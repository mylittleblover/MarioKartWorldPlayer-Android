#pragma once
#include "api/Mo3Types.hpp"
#include "decoder/Mo3Compression.hpp"
#include "decoder/Mo3Envelope.hpp"
#include <string>

namespace player2::audio {
struct Mo3Format {
    Mo3Summary summary;std::uint64_t workingBytes{};std::vector<Mo3Order> orders;std::vector<Mo3Pattern> patterns;
    std::vector<std::vector<Mo3Cell>> cells;std::vector<Mo3Instrument> instruments;std::vector<Mo3Sample> samples;std::vector<Mo3Subsong> songs;
};
inline bool probeMo3(ByteView bytes)noexcept{return bytes.data && bytes.size>=4 && std::memcmp(bytes.data,"MO3",3)==0;}
inline void mo3SkipString(SourceBytes& bytes,std::size_t& cursor) {
    const auto begin=cursor;while(bytes.u8(cursor++))if(cursor-begin>4096)fail(SourceStatus::ResourceLimit);
}
template<class Input> Mo3Format parseMo3Format(Input& encoded,const DecodeLimits& limits) {
    encoded.need(0,8);if(encoded.u8(0)!='M' || encoded.u8(1)!='O' || encoded.u8(2)!='3')fail(SourceStatus::UnsupportedFormat);
    const auto version=encoded.u8(3);const auto size=encoded.u32(4);
    if(version>5)fail(SourceStatus::UnsupportedCodec);
    if(size<424)fail(SourceStatus::MalformedSource);
    // Charge the aggregate working-set estimate instead of forcing all DTOs
    // and sample waveforms into the same quarter of the decode budget.
    std::uint64_t remaining=limits.maximumDecodedBytes;
    const auto charge=[&](std::uint64_t count,std::uint64_t width=1) {
        if(width && count>remaining/width)fail(SourceStatus::ResourceLimit);
        remaining-=count*width;
    };
    if(size>16*1024*1024)fail(SourceStatus::ResourceLimit);
    charge(encoded.size(),2);charge(size,2);charge(2*1024*1024);
    std::size_t sampleStart=0;const auto music=unpackMo3Music(encoded,size,sampleStart,version);
    DecodeLimits structureLimits=limits;structureLimits.maximumInputBytes=16*1024*1024;
    SourceBytes input({music.data(),music.size()},structureLimits);std::size_t cursor=0;
    mo3SkipString(input,cursor);mo3SkipString(input,cursor);const auto header=cursor;input.need(header,422);cursor+=422;
    Mo3Format result;auto& summary=result.summary;summary.version=version;summary.structureBytes=size;summary.channels=input.u8(header);
    summary.orders=input.u16(header+1);summary.patterns=input.u16(header+5);const auto tracks=input.u16(header+7);
    summary.instruments=input.u16(header+9);summary.samples=input.u16(header+11);summary.speed=input.u8(header+13);summary.tempo=input.u8(header+14);
    const auto flags=input.u32(header+15);
    summary.kind=(flags&0x100) ? 1 : (flags&2) ? 2 : (flags&128) ? 3 : (flags&8) ? 4 : 0;
    if(flags&0x100000)fail(SourceStatus::UnsupportedCodec); // No silent plugin dependence.
    if(!summary.channels || summary.channels>64)fail(SourceStatus::UnsupportedChannels);
    if(!summary.orders || summary.orders>256 || !summary.patterns || summary.patterns>256 || !tracks || tracks>4096 || summary.instruments>128 || summary.samples>2048)fail(SourceStatus::ResourceLimit);
    charge(summary.instruments,sizeof(Mo3Instrument)*2);
    charge(summary.samples,sizeof(Mo3Sample)*2+2048);
    charge(summary.orders,sizeof(Mo3Order)*2);
    charge(summary.patterns,sizeof(Mo3Pattern)*2+sizeof(std::vector<Mo3Cell>));
    if(input.u16(header+3)>=summary.orders)fail(SourceStatus::MalformedSource);
    if(!summary.speed)summary.speed=6;
    if(!summary.tempo)summary.tempo=125;
    for(std::uint32_t n=0;n<summary.orders;++n) {
        const auto at=cursor++;const auto pattern=input.u8(at);
        if(pattern>=summary.patterns && !(summary.kind==1 || summary.kind==2) )fail(SourceStatus::MalformedSource);
        if(pattern>=summary.patterns && pattern!=254 && pattern!=255)fail(SourceStatus::MalformedSource);
        result.orders.push_back({n,pattern,{at}});
    }
    const auto assignments=cursor;input.need(cursor,summary.patterns*summary.channels*2);cursor+=summary.patterns*summary.channels*2;
    const auto lengths=cursor;input.need(cursor,summary.patterns*2);cursor+=summary.patterns*2;
    std::vector<std::pair<std::size_t,std::size_t>> trackRanges;
    for(std::uint32_t n=0;n<tracks;++n) {const auto length=input.u32(cursor);cursor+=4;if(length>2*1024*1024)fail(SourceStatus::ResourceLimit);input.need(cursor,length);trackRanges.push_back({cursor,length});cursor+=length;}
    for(std::uint32_t p=0;p<summary.patterns;++p) {
        const auto rows=input.u16(lengths+p*2);if(!rows || rows>256)fail(SourceStatus::ResourceLimit);
        const auto count=std::uint64_t(rows)*summary.channels;
        if(count>limits.maximumEvents-summary.cells)fail(SourceStatus::ResourceLimit);
        // Inspection DTO plus two renderer pattern sets during replay rebuild.
        charge(count,sizeof(Mo3Cell)+16);summary.cells+=count;result.patterns.push_back({p,rows,summary.channels,{lengths+p*2}});
        std::vector<Mo3Cell> cells(count);
        for(std::uint32_t ch=0;ch<summary.channels;++ch) {
            const auto track=input.u16(assignments+(p*summary.channels+ch)*2);if(track>=tracks)fail(SourceStatus::MalformedSource);
            auto at=trackRanges[track].first;const auto end=at+trackRanges[track].second;std::uint32_t row=0;
            for(;row<rows;) {
                if(at==end)break;
                const auto origin=at;const auto status=input.u8(at++);if(!status)break;
                const unsigned repetitions=status>>4,commands=status&15;if(!repetitions || repetitions>rows-row || commands*2>end-at)fail(SourceStatus::MalformedSource);
                Mo3Cell cell;cell.origin={origin};cell.channel=ch;cell.count=commands;
                for(unsigned n=0;n<commands;++n) {
                    const auto opcode=input.u8(at++),value=input.u8(at++);if(!opcode || opcode>0x34)fail(SourceStatus::UnsupportedCodec);
                    const auto references=(summary.kind==0 || (flags&0x200)) ? summary.instruments : summary.samples;
                    if(opcode==2 && value>=references)fail(SourceStatus::MalformedSource);
                    cell.commands[n]={opcode,value};
                }
                for(unsigned repeat=0;repeat<repetitions;++repeat){cell.row=row;cells[row++*summary.channels+ch]=cell;}
            }
            for(;row<rows;++row)cells[row*summary.channels+ch]={{trackRanges[track].first},row,ch,0,{}};
        }
        result.cells.push_back(std::move(cells));
    }
    for(std::uint32_t n=0;n<summary.instruments;++n) {
        mo3SkipString(input,cursor);if(version>=5)mo3SkipString(input,cursor);input.need(cursor,826);
        Mo3Instrument instrument;instrument.origin={cursor};for(unsigned note=0;note<120;++note) {const auto sample=input.u16(cursor+4+note*4+2);if(sample!=65535 && sample>=summary.samples)fail(SourceStatus::MalformedSource);instrument.samples[note]=sample;}
        for(unsigned kind=0;kind<3;++kind)instrument.envelopes[kind]=readMo3Envelope(input,cursor+484+kind*106,summary.kind==0);
        result.instruments.push_back(instrument);cursor+=826;
    }
    for(std::uint32_t n=0;n<summary.samples;++n) {
        mo3SkipString(input,cursor);if(version>=5)mo3SkipString(input,cursor);input.need(cursor,41);
        Mo3Sample sample;sample.origin={cursor};sample.frames=input.u32(cursor+8);sample.loopStart=input.u32(cursor+12);sample.loopEnd=input.u32(cursor+16);sample.flags=input.u16(cursor+20);
        const auto packed=input.u32(cursor+35);const auto compression=sample.flags&0xf000;
        if(compression!=0 && compression!=0x1000 && compression!=0x2000 && compression!=0x3000 && compression!=0x4000 && compression!=0x7000)fail(SourceStatus::UnsupportedCodec);
        if((sample.flags&0x20) && !(sample.flags&0x10))fail(SourceStatus::SourceLoopBounds);
        if(sample.frames && (sample.flags&0x30) && (sample.loopStart>=sample.loopEnd || sample.loopEnd>sample.frames))fail(SourceStatus::SourceLoopBounds);
        const auto sustainStart=input.u32(cursor+27),sustainEnd=input.u32(cursor+31);
        if((sample.flags&0x200) && !(sample.flags&0x100))fail(SourceStatus::SourceLoopBounds);
        if(sample.frames && (sample.flags&0x300) && (sustainStart>=sustainEnd || sustainEnd>sample.frames))fail(SourceStatus::SourceLoopBounds);
        // Empty tracker sample slots may retain inactive loop endpoints. Keep
        // the original inspection facts; bounds still apply to nonempty audio.
        const auto bytes=std::uint64_t(sample.frames)*(sample.flags&1 ? 2 : 1)*(sample.flags&0x400 ? 2 : 1);
        // Old/new renderer waveforms and transient compressed-sample decode.
        charge(bytes,3);
        if(packed>=0x80000000u) {const auto back=std::uint64_t(0x100000000ULL-packed);if(!back || back>n)fail(SourceStatus::MalformedSource);sample.sharedSample=static_cast<int>(n-back);
            const auto& shared=result.samples[sample.sharedSample];if(shared.frames!=sample.frames || (shared.flags&0x401)!=(sample.flags&0x401))fail(SourceStatus::MalformedSource);}
        else {sample.compressedBytes=packed;if(!compression && packed)fail(SourceStatus::MalformedSource);if(compression && sample.frames && !packed)fail(SourceStatus::MalformedSource);}
        cursor+=41;
        if(version>=5 && compression==0x7000){input.need(cursor,2);const auto shared=input.u16(cursor);const auto signedShared=shared<32768 ? int(shared) : int(shared)-65536;if(int(n)+signedShared<0 || int(n)+signedShared>=int(summary.samples))fail(SourceStatus::MalformedSource);cursor+=2;}
        sample.data={sampleStart};
        if(sample.sharedSample<0){const auto encodedBytes=compression ? sample.compressedBytes : bytes;encoded.need(sampleStart,encodedBytes);sampleStart+=encodedBytes;}
        result.samples.push_back(sample);
    }
    // Optional tagged metadata is bounded by the decompressed structure length.
    while(cursor<input.size()){input.need(cursor,8);const auto length=input.u32(cursor+4);cursor+=8;input.need(cursor,length);cursor+=length;}
    if(sampleStart!=encoded.size())fail(SourceStatus::MalformedSource);
    result.workingBytes=limits.maximumDecodedBytes-remaining;
    return result;
}
}
