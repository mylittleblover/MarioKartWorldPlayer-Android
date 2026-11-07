#pragma once
#include "decoder/SourceBytes.hpp"
#include <opus.h>
#include <algorithm>
#include <array>
#include <vector>

namespace player2::audio {
struct OpusSpan {std::size_t offset{},size{};};
struct OpusPacket {std::vector<OpusSpan> spans;std::size_t bytes{};int frames{};};
struct OpusLayout {SourceMetadata metadata;std::vector<OpusPacket> packets;unsigned preSkip{};int gain{};};
inline constexpr std::size_t OpusWorkingBudget=1024*1024,MaximumOpusPacket=65536;
inline bool probeOpus(ByteView bytes)noexcept {
    return bytes.data && bytes.size>=47 && std::memcmp(bytes.data,"OggS",4)==0 && bytes.data[26]==1 &&
        bytes.data[27]==19 && std::memcmp(bytes.data+28,"OpusHead",8)==0;
}
inline std::uint32_t oggCrc(const std::uint8_t* bytes,std::size_t size)noexcept {
    std::uint32_t crc=0;
    for(std::size_t n=0;n<size;++n) {
        crc^=std::uint32_t(n>=22 && n<26 ? 0 : bytes[n])<<24;
        for(unsigned bit=0;bit<8;++bit)crc=(crc<<1)^((crc&0x80000000u) ? 0x04c11db7u : 0);
    }
    return crc;
}
// Canonical, complete single logical Opus stream, mapping family0. Complete Ogg
// pages are CRC-checked; no holes, chaining, guessed granules or synthetic PLC.
template<class Bytes> OpusLayout parseOpus(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"OggS"))fail(SourceStatus::UnsupportedFormat);
    if(limits.maximumDecodedBytes<OpusWorkingBudget)fail(SourceStatus::ResourceLimit);
    OpusLayout result;std::array<std::uint8_t,65307> page{};
    std::vector<std::uint8_t> packet;packet.reserve(MaximumOpusPacket);std::vector<OpusSpan> spans;
    std::uint32_t serial=0,sequence=0;std::uint64_t decoded=0,previousGranule=0,finalGranule=0;
    std::size_t cursor=0,packetCount=0,spanCount=0,pages=0;bool eos=false,continued=false;
    int channels=0;
    while(cursor<bytes.size()) {
        bytes.need(cursor,27);
        if(!bytes.magic(cursor,"OggS") || bytes.u8(cursor+4)!=0)fail(SourceStatus::MalformedSource);
        const auto flags=bytes.u8(cursor+5),segments=bytes.u8(cursor+26);
        if(flags&~7u || bool(flags&1)!=continued || eos)fail(SourceStatus::MalformedSource);
        if(pages==0) {if(flags!=2)fail(SourceStatus::MalformedSource);serial=bytes.u32(cursor+14);}
        else if(flags&2)fail(SourceStatus::UnsupportedCodec); // chained/multiplexed streams need explicit policy
        if(bytes.u32(cursor+14)!=serial)fail(SourceStatus::UnsupportedCodec);
        if(bytes.u32(cursor+18)!=sequence++)fail(SourceStatus::MalformedSource);
        if(++pages>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        bytes.need(cursor+27,segments);std::size_t bodySize=0;
        for(unsigned n=0;n<segments;++n)bodySize+=bytes.u8(cursor+27+n);
        const auto headerSize=std::size_t(27)+segments,pageSize=headerSize+bodySize;bytes.need(cursor,pageSize);
        for(std::size_t n=0;n<pageSize;++n)page[n]=bytes.u8(cursor+n);
        if(oggCrc(page.data(),pageSize)!=bytes.u32(cursor+22))fail(SourceStatus::MalformedSource);
        const auto granule=std::uint64_t(bytes.u32(cursor+6))|(std::uint64_t(bytes.u32(cursor+10))<<32);
        const auto before=packetCount;std::size_t body=cursor+headerSize;
        for(unsigned segment=0;segment<segments;++segment) {
            const auto count=page[27+segment];
            if(count>MaximumOpusPacket-packet.size())fail(SourceStatus::ResourceLimit);
            if(count) {
                spans.push_back({body,count});++spanCount;
                for(unsigned n=0;n<count;++n)packet.push_back(page[body-cursor+n]);
            }
            body+=count;continued=count==255;
            const auto retained=result.packets.size()*256ull+spanCount*64ull;
            if(spanCount>limits.maximumEvents || retained>limits.maximumDecodedBytes-OpusWorkingBudget)fail(SourceStatus::ResourceLimit);
            if(continued)continue;
            if(++packetCount>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
            if(packetCount==1) {
                if(packet.size()!=19 || std::memcmp(packet.data(),"OpusHead",8)!=0)fail(SourceStatus::UnsupportedFormat);
                if(packet[8]!=1 || packet[18]!=0)fail(SourceStatus::UnsupportedCodec);
                channels=packet[9];validateFormat(48000,channels);
                result.preSkip=packet[10]|(unsigned(packet[11])<<8);
                const auto gain=unsigned(packet[16])|(unsigned(packet[17])<<8);
                result.gain=gain<32768 ? int(gain) : int(gain)-65536;
                if(pages!=1 || segment+1!=segments || granule!=0)fail(SourceStatus::MalformedSource);
            } else if(packetCount==2) {
                if(packet.size()<16 || std::memcmp(packet.data(),"OpusTags",8)!=0)fail(SourceStatus::MalformedSource);
                SourceBytes tags({packet.data(),packet.size()},DecodeLimits{MaximumOpusPacket});
                const auto vendor=tags.u32(8);tags.need(12,vendor);const auto comments=tags.u32(12+vendor);
                if(comments>4096 || comments>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
                std::size_t at=16+std::size_t(vendor);
                for(std::size_t n=0;n<comments;++n) {const auto length=tags.u32(at);at+=4;tags.need(at,length);at+=length;}
                if(segment+1!=segments || granule!=0)fail(SourceStatus::MalformedSource);
            } else {
                if(packet.empty())fail(SourceStatus::MalformedSource);
                const auto frames=opus_packet_get_nb_samples(packet.data(),static_cast<opus_int32>(packet.size()),48000);
                if(frames<=0 || frames>5760)fail(SourceStatus::MalformedSource);
                decoded+=static_cast<unsigned>(frames);
                if(decoded>INT64_MAX)fail(SourceStatus::ResourceLimit);
                result.packets.push_back({std::move(spans),packet.size(),frames});spans={};
            }
            packet.clear();spans.clear();
        }
        eos=flags&4;
        if(packetCount==before) {
            if(granule!=UINT64_MAX || eos)fail(SourceStatus::MalformedSource);
        } else if(packetCount<=2) {
            if(eos || granule!=0)fail(SourceStatus::MalformedSource);
        } else {
            if(granule==UINT64_MAX || granule<previousGranule || granule>decoded || (!eos && granule!=decoded))
                fail(SourceStatus::MalformedSource);
            previousGranule=granule;
        }
        if(eos) {if(continued || cursor+pageSize!=bytes.size())fail(SourceStatus::MalformedSource);finalGranule=granule;}
        cursor+=pageSize;
    }
    if(!eos || continued || result.packets.empty() || finalGranule<=result.preSkip)fail(SourceStatus::MalformedSource);
    const auto frames=finalGranule-result.preSkip;boundStreamDuration(frames,48000,limits);
    result.metadata={"native.opus",SourceKind::StandardMedia,48000,channels,{static_cast<std::int64_t>(frames)},SeekSupport::Replay,
        KnownDuration|DeterministicRender|Streaming,std::nullopt,SampleEncoding::Opus,0};
    return result;
}
std::unique_ptr<AudioFrameSource> makePacketOpusSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,OpusLayout layout);

}
