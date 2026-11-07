#pragma once
#include "decoder/SourceComments.hpp"
#include <array>
#include <vector>

namespace player2::audio {
inline constexpr std::size_t MaximumVorbisWindow=1024*1024;
inline constexpr auto oggCrcTable() {
    std::array<std::uint32_t,256> table{};
    for(std::size_t n=0;n<table.size();++n) {
        auto value=static_cast<std::uint32_t>(n)<<24;
        for(int b=0;b<8;++b)value=(value<<1)^((value&0x80000000u) ? 0x04c11db7u : 0);
        table[n]=value;
    }
    return table;
}
struct VorbisLayout {SourceMetadata metadata;std::size_t headerPrefix{};};
inline bool probeVorbis(ByteView input)noexcept {
    if(!input.data || input.size<27 || std::memcmp(input.data,"OggS",4)!=0)return false;
    const auto start=std::size_t(27)+input.data[26];
    return start<=input.size && input.size-start>=7 && input.data[start]==1 && std::memcmp(input.data+start+1,"vorbis",6)==0;
}
inline bool vorbisPacket(const std::vector<std::uint8_t>& bytes,int kind)noexcept {
    return bytes.size()>=7 && bytes[0]==kind && std::equal(bytes.begin()+1,bytes.begin()+7,"vorbis");
}
template<class Bytes> VorbisLayout parseVorbisLayout(Bytes& input,const DecodeLimits& limits) {
    if(!input.magic(0,"OggS"))fail(SourceStatus::UnsupportedFormat);
    constexpr auto crcTable=oggCrcTable();
    std::size_t cursor=0,page=0,packetSize=0,packets=0;std::uint32_t serial=0;
    std::uint64_t previousGranule=0,lastGranule=0;bool continued=false,eos=false;
    std::vector<std::uint8_t> packet,comments;VorbisLayout result;
    while(cursor<input.size()) {
        if(++page>1048576)fail(SourceStatus::ResourceLimit);
        if(eos)fail(SourceStatus::UnsupportedCodec); // Chained logical streams require explicit source selection.
        input.need(cursor,27);if(!input.magic(cursor,"OggS") || input.u8(cursor+4)!=0)fail(SourceStatus::MalformedSource);
        const auto flags=input.u8(cursor+5),laces=input.u8(cursor+26);
        if(flags&~7u || bool(flags&1)!=continued)fail(SourceStatus::MalformedSource);
        if(page==1) {if(!(flags&2) || flags&1)fail(SourceStatus::MalformedSource);serial=input.u32(cursor+14);}
        else if(flags&2 || input.u32(cursor+14)!=serial)fail(SourceStatus::UnsupportedCodec);
        if(input.u32(cursor+18)!=page-1)fail(SourceStatus::MalformedSource);
        input.need(cursor+27,laces);std::size_t payload=0;
        for(std::size_t n=0;n<laces;++n)payload+=input.u8(cursor+27+n);
        const auto begin=cursor+27+laces;input.need(begin,payload);const auto end=begin+payload;
        std::uint32_t crc=0;
        for(auto n=cursor;n<end;++n) {const auto byte=n>=cursor+22 && n<cursor+26 ? 0 : input.u8(n);crc=(crc<<8)^crcTable[(crc>>24)^byte];}
        if(crc!=input.u32(cursor+22))fail(SourceStatus::MalformedSource);
        std::uint64_t granule=0;for(int n=7;n>=0;--n)granule=(granule<<8)|input.u8(cursor+6+n);
        if(granule!=UINT64_MAX) {if(granule>INT64_MAX || granule<previousGranule)fail(SourceStatus::MalformedSource);previousGranule=granule;}
        auto at=begin;
        for(std::size_t n=0;n<laces;++n) {
            const auto length=input.u8(cursor+27+n);
            if(packets>=3 && packetSize==0 && length && (input.u8(at)&1))fail(SourceStatus::MalformedSource);
            if(length>MaximumVorbisWindow-packetSize)fail(SourceStatus::ResourceLimit);
            packetSize+=length;
            if(packets<3) {
                const auto old=packet.size();packet.resize(old+length);
                for(std::size_t k=0;k<length;++k)packet[old+k]=input.u8(at+k);
            }
            at+=length;continued=length==255;
            if(continued)continue;
            if(packets==0) {
                if(!vorbisPacket(packet,1))fail(SourceStatus::UnsupportedCodec);
                if(packet.size()!=30)fail(SourceStatus::MalformedSource);
                SourceBytes header({packet.data(),packet.size()},limits);
                if(header.u32(7)!=0 || header.u8(29)!=1)fail(SourceStatus::MalformedSource);
                const auto rate=header.u32(12);if(rate>INT32_MAX)fail(SourceStatus::UnsupportedSampleRate);
                const auto channels=header.u8(11),blocks=header.u8(28);
                const auto small=blocks&15,large=blocks>>4;
                validateFormat(rate,channels);if(small<6 || large>13 || small>large)fail(SourceStatus::MalformedSource);
                result.metadata={"native.vorbis",SourceKind::StandardMedia,static_cast<std::int32_t>(rate),channels,{},SeekSupport::Replay,
                    KnownDuration|LoopMetadata|DeterministicRender|Streaming,std::nullopt,SampleEncoding::Vorbis,0};
            } else if(packets==1) {
                if(!vorbisPacket(packet,3) || packet.size()<8 || packet.back()!=1)fail(SourceStatus::MalformedSource);
                comments=std::move(packet);
            } else if(packets==2) {
                if(!vorbisPacket(packet,5))fail(SourceStatus::MalformedSource);
                if(end>MaximumVorbisWindow)fail(SourceStatus::ResourceLimit);
                result.headerPrefix=end;
            } else if(!packetSize)fail(SourceStatus::MalformedSource);
            ++packets;packet.clear();packetSize=0;
        }
        if(flags&4) {if(continued || granule==UINT64_MAX || packets<4)fail(SourceStatus::MalformedSource);eos=true;lastGranule=granule;}
        cursor=end;
    }
    if(!eos || !lastGranule || packets<4)fail(SourceStatus::MalformedSource);
    if(lastGranule>UINT32_MAX)fail(SourceStatus::ResourceLimit); // Pinned decoder position range, despite 64-bit Ogg/API coordinates.
    boundStreamDuration(lastGranule,result.metadata.sampleRate,limits);result.metadata.duration.value=static_cast<std::int64_t>(lastGranule);
    SourceBytes comment({comments.data(),comments.size()},limits);
    result.metadata.sourceLoop=sourceCommentLoop(comment,7,comments.size()-8,result.metadata.duration);
    return result;
}
}
