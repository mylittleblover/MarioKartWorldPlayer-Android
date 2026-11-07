#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/ItPackedSample.hpp"
#include "decoder/ItInstrument.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include <array>
#include <algorithm>

namespace player2::audio {
enum class TrackerKind { It,S3m,Ptm };
struct TrackerFormat {
    TrackerKind kind;std::size_t orderOrigin{};
    std::vector<std::uint8_t> orders;std::vector<unsigned> rows;
};
inline bool probeIt(ByteView bytes)noexcept {
    return bytes.data && bytes.size>=4 && std::memcmp(bytes.data,"IMPM",4)==0;
}
inline bool probeS3m(ByteView bytes)noexcept {
    return bytes.data && bytes.size>=48 && bytes.data[29]==16 && std::memcmp(bytes.data+44,"SCRM",4)==0;
}
inline bool probePtm(ByteView bytes)noexcept {
    return bytes.data && bytes.size>=48 && bytes.data[28]==26 && std::memcmp(bytes.data+44,"PTMF",4)==0;
}
// Validate declarations and encoded spans before libopenmpt allocates samples
// or expands packed patterns. External/plugin/FM instruments, old IT1.x
// instruments and MPTM extensions remain excluded.
// Bounded PCM conversion and mono8 MODPlugin table ADPCM reuse the same renderer.
template<class Input> class TrackerAdmission {
    Input& input_;const DecodeLimits& limits_;
    std::uint64_t heap_{},events_{},encodedOperations_{};
    std::vector<std::pair<std::size_t,std::size_t>> regions_;
public:
    TrackerAdmission(Input& input,const DecodeLimits& limits):input_(input),limits_(limits) {}
    void region(std::size_t at,std::size_t bytes) {
        input_.need(at,bytes);if(bytes)regions_.emplace_back(at,at+bytes);
    }
    void memory(std::uint64_t bytes) {
        if(bytes>limits_.maximumDecodedBytes-heap_)fail(SourceStatus::ResourceLimit);
        heap_+=bytes;
    }
    void pattern(unsigned rows,unsigned channels) {
        const auto cells=std::uint64_t(rows)*channels;
        if(cells>limits_.maximumEvents-events_)fail(SourceStatus::ResourceLimit);
        events_+=cells;memory(cells*32); // covers expansion and overlapping renderer recreation
    }
    void operation() {
        if(encodedOperations_>=limits_.maximumEvents)fail(SourceStatus::ResourceLimit);
        ++encodedOperations_;
    }
    void sample(std::size_t at,std::uint32_t frames,unsigned channels,unsigned depth,bool adpcm=false) {
        memory(std::uint64_t(frames)*channels*8+256);
        const auto bytes=adpcm ? (frames ? 16+(std::uint64_t(frames)+1)/2 : 0) : std::uint64_t(frames)*channels*(depth/8);
        if(bytes>input_.size())fail(SourceStatus::MalformedSource);
        region(at,static_cast<std::size_t>(bytes));
    }
    void packedSample(std::size_t at,std::uint32_t frames,unsigned channels,unsigned depth) {
        memory(std::uint64_t(frames)*channels*8+256);
        const auto stored=itPackedSampleBytes(input_,at,frames,channels,depth,[&]{operation();});
        region(at,stored);
    }
    void finish() {
        std::sort(regions_.begin(),regions_.end());std::size_t end=0;
        std::pair<std::size_t,std::size_t> previous{};
        for(const auto& span:regions_) {
            if(span.first<end && span!=previous)fail(SourceStatus::MalformedSource);
            for(auto n=end;n<span.first;++n)if(input_.u8(n))fail(SourceStatus::UnsupportedCodec);
            end=std::max(end,span.second);previous=span;
        }
        for(auto n=end;n<input_.size();++n)if(input_.u8(n))fail(SourceStatus::UnsupportedCodec);
    }
};
inline void trackerLoop(std::uint32_t begin,std::uint32_t end,std::uint32_t frames) {
    if(begin>=end || end>frames)fail(SourceStatus::SourceLoopBounds);
}
// PTM2.03 admission bounds every packed pattern and sample before the shared renderer allocates.
template<class Input> inline TrackerFormat parsePtm(Input& in,const DecodeLimits& limits) {
    in.need(0,608);if(!in.magic(44,"PTMF") || in.u8(28)!=26)fail(SourceStatus::UnsupportedFormat);
    if(in.u8(29)!=3 || in.u8(30)!=2 || in.u8(31) || in.u16(40) || in.u16(42))fail(SourceStatus::UnsupportedCodec);
    for(std::size_t p=48;p<64;++p)if(in.u8(p))fail(SourceStatus::UnsupportedCodec);
    const unsigned orders=in.u16(32),samples=in.u16(34),patterns=in.u16(36),channels=in.u16(38);
    if(!orders || !samples || !patterns || !channels)fail(SourceStatus::MalformedSource);
    if(orders>256 || samples>255 || patterns>128 || channels>32)fail(SourceStatus::ResourceLimit);
    for(unsigned ch=0;ch<32;++ch)if(in.u8(64+ch)>15)fail(SourceStatus::MalformedSource);
    TrackerFormat result{TrackerKind::Ptm,96,{},{}};TrackerAdmission<Input> bounds(in,limits);
    const auto headerEnd=608+samples*80;bounds.region(0,headerEnd);bounds.memory(65536+samples*1024);
    bool playable=false;
    for(unsigned n=0;n<orders;++n) {
        const auto order=in.u8(96+n);if(order<254 && order>=patterns)fail(SourceStatus::MalformedSource);
        playable=playable || order<254;result.orders.push_back(order);
    }
    if(!playable)fail(SourceStatus::MalformedSource);
    for(unsigned n=0;n<samples;++n) {
        const auto at=608+n*80;const auto flags=in.u8(at);const unsigned type=flags&3;
        if(!in.magic(at+76,"PTMS"))fail(SourceStatus::MalformedSource);
        if(flags&~31u || (type!=0 && type!=1))fail(SourceStatus::UnsupportedCodec);
        if(in.u8(at+13)>64 || (type==1 && !in.u16(at+14)))fail(SourceStatus::MalformedSource);
        if(in.u16(at+16))fail(SourceStatus::UnsupportedCodec);
        for(unsigned k=34;k<48;++k)if(in.u8(at+k))fail(SourceStatus::UnsupportedCodec);
        const auto bytes=in.u32(at+22),first=in.u32(at+26),last=in.u32(at+30);
        const auto width=(flags&16) ? 2u : 1u;
        if(!type) {if(flags || bytes || first || last)fail(SourceStatus::UnsupportedCodec);continue;}
        if(bytes%width)fail(SourceStatus::MalformedSource);
        if((flags&8) && !(flags&4))fail(SourceStatus::UnsupportedCodec);
        if(flags&4) {
            if(first%width || last%width || first>=last || last>bytes || (last-1)/width<=first/width)fail(SourceStatus::SourceLoopBounds);
        } else if(first || last)fail(SourceStatus::UnsupportedCodec);
        bounds.sample(in.u32(at+18),bytes/width,1,width*8);
    }
    for(unsigned n=0;n<patterns;++n) {
        bounds.pattern(64,channels);result.rows.push_back(64);const auto start=std::size_t(in.u16(352+n*2))*16;
        if(!start)continue;
        auto cursor=start;unsigned row=0;std::uint32_t seen=0;
        while(row<64) {
            bounds.operation();const auto token=in.u8(cursor++);
            if(!token){++row;seen=0;continue;}
            const unsigned ch=token&31;if(ch>=channels || !(token&0xe0) || (seen&(1u<<ch)))fail(SourceStatus::MalformedSource);
            seen|=1u<<ch;
            if(token&32) {
                const auto note=in.u8(cursor++);if(note>120 && note!=254 && note!=255)fail(SourceStatus::MalformedSource);
                if(in.u8(cursor++)>samples)fail(SourceStatus::MalformedSource);
            }
            if(token&64){if(in.u8(cursor++)>0x17)fail(SourceStatus::UnsupportedCodec);static_cast<void>(in.u8(cursor++));}
            if((token&128) && in.u8(cursor++)>64)fail(SourceStatus::MalformedSource);
        }
        bounds.region(start,cursor-start);
    }
    bounds.finish();return result;
}
template<class Input> inline TrackerFormat parseTracker(Input& in,const DecodeLimits& limits,TrackerKind kind) {
    if(kind==TrackerKind::Ptm)return parsePtm(in,limits);
    TrackerFormat result{kind,kind==TrackerKind::It ? 192u : 96u,{},{}};
    TrackerAdmission<Input> bounds(in,limits);
    const bool it=kind==TrackerKind::It;
    in.need(0,result.orderOrigin);
    if(it ? !in.magic(0,"IMPM") : (!in.magic(44,"SCRM") || in.u8(29)!=16))fail(SourceStatus::UnsupportedFormat);
    const unsigned orders=in.u16(32),instruments=it ? in.u16(34) : 0;
    const unsigned samples=in.u16(it ? 36 : 34),patterns=in.u16(it ? 38 : 36);
    if(!orders || !patterns)fail(SourceStatus::MalformedSource);
    if(orders>256 || samples>256 || patterns>256 || instruments>256)fail(SourceStatus::ResourceLimit);
    const unsigned flags=in.u16(it ? 44 : 38);
    if(it) {
        const auto maker=in.u16(40);
        if((flags&~0x3fu) || (in.u16(46)&~7u) || in.u16(42)>0x217 ||
            (instruments && in.u16(42)<0x200) || (maker>=0x888 && maker<=0xfff))fail(SourceStatus::UnsupportedCodec);
        if(in.u8(48)>128 || in.u8(49)>128 || !in.u8(50) || in.u8(51)<31)fail(SourceStatus::MalformedSource);
        for(unsigned n=0;n<64;++n) {
            const auto pan=in.u8(64+n);
            if(in.u8(128+n)>64 || (pan!=255 && (pan&127)>64 && (pan&127)!=100))fail(SourceStatus::MalformedSource);
        }
    } else {
        if(flags&~0x59u || (in.u16(42)!=1 && in.u16(42)!=2) || in.u16(62))fail(SourceStatus::UnsupportedCodec);
        if(in.u8(48)>64 || !in.u8(49) || in.u8(49)==255 || in.u8(50)<33)fail(SourceStatus::MalformedSource);
        for(unsigned n=0;n<32;++n)if(in.u8(64+n)!=255 && (in.u8(64+n)&127)>15)fail(SourceStatus::UnsupportedCodec);
    }
    const auto pointers=result.orderOrigin+orders;
    const auto samplePointers=pointers+instruments*4;
    auto headerEnd=samplePointers+(samples+patterns)*(it ? 4u : 2u);
    in.need(0,headerEnd);
    if(it && (in.u16(46)&2)) {
        const auto count=in.u16(headerEnd);if(count>256)fail(SourceStatus::ResourceLimit);
        headerEnd+=2+count*8;
    }
    if(!it && in.u8(53)==252)headerEnd+=32;
    bounds.region(0,headerEnd);
    if(it && (in.u16(46)&1))bounds.region(in.u32(56),in.u16(54));
    bool playable=false;
    for(unsigned n=0;n<orders;++n) {
        const auto order=in.u8(result.orderOrigin+n);
        if(order<254 && order>=patterns)fail(SourceStatus::MalformedSource);
        playable=playable || order<254;result.orders.push_back(order);
    }
    if(!playable)fail(SourceStatus::MalformedSource);
    for(unsigned n=0;n<instruments;++n) {
        const auto p=in.u32(pointers+n*4);if(!p)continue;
        bounds.region(p,554);bounds.memory(8192); // includes overlapping renderer recreation
        validateItInstrument(in,p,samples);
    }
    for(unsigned n=0;n<samples;++n) {
        const auto p=it ? std::size_t(in.u32(samplePointers+n*4)) : std::size_t(in.u16(samplePointers+n*2))*16;
        if(!p)continue;
        bounds.region(p,80);
        if(it) {
            if(!in.magic(p,"IMPS"))fail(SourceStatus::MalformedSource);
            const auto sf=in.u8(p+18),convert=in.u8(p+46);
            const bool adpcm=convert==255,compressed=(sf&8)!=0;
            const bool ptm=convert==8 || convert==9;
            if(compressed ? (convert!=1 && convert!=5) : ((!adpcm && !ptm && (convert&~7u)) ||
                (adpcm && (sf&6)) || (ptm && (sf&6)!=2)))
                fail(SourceStatus::UnsupportedCodec);
            const auto length=in.u32(p+48);
            if(in.u8(p+17)>64 || in.u8(p+19)>64)fail(SourceStatus::MalformedSource);
            if(sf&16)trackerLoop(in.u32(p+52),in.u32(p+56),length);
            if(sf&32)trackerLoop(in.u32(p+64),in.u32(p+68),length);
            if((sf&64) && !(sf&16))fail(SourceStatus::SourceLoopBounds);
            if((sf&128) && !(sf&32))fail(SourceStatus::SourceLoopBounds);
            if(length && (!(sf&1) || !in.u32(p+60)))fail(SourceStatus::MalformedSource);
            if(compressed)bounds.packedSample(in.u32(p+72),length,(sf&4) ? 2 : 1,(sf&2) ? 16 : 8);
            else bounds.sample(in.u32(p+72),length,(sf&4) ? 2 : 1,(sf&2) ? 16 : 8,adpcm);
        } else {
            const auto type=in.u8(p),sf=in.u8(p+31),pack=in.u8(p+30);
            const bool adpcm=pack==4;
            if(type>1 || (pack && !adpcm) || sf>7 || (adpcm && (type!=1 || (sf&6))))fail(SourceStatus::UnsupportedCodec);
            if(type==0)continue;
            if(!in.magic(p+76,"SCRS"))fail(SourceStatus::MalformedSource);
            const auto length=in.u32(p+16);
            if(in.u8(p+28)>64 || (length && !in.u32(p+32)))fail(SourceStatus::MalformedSource);
            if(sf&1)trackerLoop(in.u32(p+20),in.u32(p+24),length);
            const auto data=(std::size_t(in.u8(p+13))<<20)|(std::size_t(in.u16(p+14))<<4);
            bounds.sample(data,length,(sf&2) ? 2 : 1,(sf&4) ? 16 : 8,adpcm);
        }
    }
    for(unsigned n=0;n<patterns;++n) {
        const auto p=it ? std::size_t(in.u32(samplePointers+(samples+n)*4)) : std::size_t(in.u16(samplePointers+(samples+n)*2))*16;
        unsigned rows=64;
        if(!p){bounds.pattern(rows,it ? 64 : 32);result.rows.push_back(rows);continue;}
        in.need(p,it ? 8 : 2);const auto packed=in.u16(p);
        if(it)rows=in.u16(p+2);
        if(!rows || rows>1024)fail(SourceStatus::ResourceLimit);
        bounds.pattern(rows,it ? 64 : 32);result.rows.push_back(rows);
        if(!it && packed<2)fail(SourceStatus::MalformedSource);
        const auto end=p+packed+(it ? 8 : 0);bounds.region(p,packed+(it ? 8 : 0));
        auto cursor=p+(it ? 8 : 2);unsigned row=0;std::array<std::uint8_t,64> masks{};
        auto take=[&]() {if(cursor>=end)fail(SourceStatus::MalformedSource);return in.u8(cursor++);};
        while(row<rows) {
            bounds.operation(); // includes empty rows and repeated packed cell updates
            const auto token=take();if(!token){++row;continue;}
            if(it) {
                const auto channel=token&127;if(channel<1 || channel>64)fail(SourceStatus::MalformedSource);
                auto& mask=masks[channel-1];if(token&128)mask=take();
                if(mask&1){const auto note=take();if(note>119 && note<253)fail(SourceStatus::MalformedSource);}
                if((mask&2) && take()>((flags&4) ? instruments : samples))fail(SourceStatus::MalformedSource);
                if((mask&4) && take()>212)fail(SourceStatus::UnsupportedCodec);
                if(mask&8){if(take()>25)fail(SourceStatus::UnsupportedCodec);static_cast<void>(take());}
            } else {
                if(token&32){const auto note=take();if(note<254 && ((note&15)>11 || (note>>4)>9))fail(SourceStatus::MalformedSource);if(take()>samples)fail(SourceStatus::MalformedSource);}
                if((token&64) && take()>64)fail(SourceStatus::MalformedSource);
                if(token&128){if(take()>25)fail(SourceStatus::UnsupportedCodec);static_cast<void>(take());}
            }
        }
        if(cursor!=end)fail(SourceStatus::MalformedSource);
    }
    bounds.finish();return result;
}
inline TrackerFormat readTracker(EncodedSourceInput& source,const DecodeLimits& limits,TrackerKind kind) {
    EncodedSourceBytes input(source,limits);return parseTracker(input,limits,kind);
}
}
