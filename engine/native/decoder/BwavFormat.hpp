#pragma once
#include "decoder/NintendoDsp.hpp"

namespace player2::audio {
struct BwavLayout {
    SourceMetadata metadata;
    std::array<std::size_t,MaximumNintendoStreamChannels> starts{};
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> channels{};
    std::array<std::uint8_t,MaximumNintendoStreamChannels> speakers{};
    bool dsp{};
};
// BWAV v1, complete little-endian planar PCM16 or Nintendo DSP channels.
// Prefetch fragments and NXOpus require their own admission, never guessing.
template<class Bytes> BwavLayout parseBwav(Bytes& input,const DecodeLimits& limits) {
    if(!input.magic(0,"BWAV"))fail(SourceStatus::UnsupportedFormat);
    input.need(0,16);
    if(input.u16(4)!=0xfeff || input.u16(6)!=1 || input.u16(12))fail(SourceStatus::UnsupportedCodec);
    const auto count=input.u16(14);
    if(count<1 || count>MaximumNintendoStreamChannels)fail(SourceStatus::UnsupportedChannels);
    if(count>2 && limits.maximumDecodedBytes<65536)fail(SourceStatus::ResourceLimit);
    const std::size_t headerEnd=16+count*76;input.need(0,headerEnd);
    BwavLayout result;std::uint32_t rate=0,frames=0;unsigned codec=0;
    std::optional<SourceLoopMetadata> loop;
    std::size_t previousEnd=headerEnd;
    for(unsigned ch=0;ch<count;++ch) {
        const auto h=16+ch*76;
        const auto kind=input.u16(h),speaker=input.u16(h+2);
        const auto hz=input.u32(h+4),full=input.u32(h+8),samples=input.u32(h+12);
        if(kind>1)fail(SourceStatus::UnsupportedCodec);
        validateFormat(static_cast<std::int32_t>(hz),1);boundStreamDuration(samples,hz,limits);
        if(full!=samples || input.u32(h+48)!=input.u32(h+52) || input.u32(h+56)!=1 || input.u16(h+74))fail(SourceStatus::UnsupportedCodec);
        if((count==1 && speaker!=0 && speaker!=2) || (count==2 && speaker!=ch))fail(SourceStatus::UnsupportedChannels);
        if(speaker>2)fail(SourceStatus::UnsupportedChannels);
        result.speakers[ch]=static_cast<std::uint8_t>(speaker);
        const auto last=input.u32(h+60),first=input.u32(h+64);
        std::optional<SourceLoopMetadata> channelLoop;
        if(last!=0xffffffffu) {
            if(first>=last || last>samples)fail(SourceStatus::SourceLoopBounds);
            channelLoop=SourceLoopMetadata{{first},{last}};
        } else if(first)fail(SourceStatus::UnsupportedCodec);
        if(ch==0){codec=kind;rate=hz;frames=samples;loop=channelLoop;}
        else if(codec!=kind || rate!=hz || frames!=samples || bool(loop)!=bool(channelLoop) ||
            (loop && (loop->start.value!=channelLoop->start.value || loop->end.value!=channelLoop->end.value)))fail(SourceStatus::UnsupportedCodec);
        const auto encoded=kind ? ((std::uint64_t(samples)+13)/14)*8 : std::uint64_t(samples)*2;
        const auto start=static_cast<std::size_t>(input.u32(h+52));
        input.need(start,static_cast<std::size_t>(encoded));
        if(start<previousEnd || start-previousEnd>63 || start%4)fail(SourceStatus::MalformedSource);
        for(auto p=previousEnd;p<start;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
        result.starts[ch]=start;previousEnd=start+static_cast<std::size_t>(encoded);
        auto& channel=result.channels[ch];
        if(kind) {
            for(unsigned n=0;n<16;++n)channel.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(h+16+n*2)));
            channel.initial={dspSigned(input.u16(h+70)),dspSigned(input.u16(h+72))};
            if(input.u16(h+68)!=input.u8(start) || input.u16(h+68)>0x7f)fail(SourceStatus::MalformedSource);
        } else {
            for(unsigned n=16;n<48;++n)if(input.u8(h+n))fail(SourceStatus::UnsupportedCodec);
            for(unsigned n=68;n<74;++n)if(input.u8(h+n))fail(SourceStatus::UnsupportedCodec);
        }
    }
    if(input.size()-previousEnd>63)fail(SourceStatus::UnsupportedCodec);
    for(auto p=previousEnd;p<input.size();++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    // The opaque container checksum is not advertised as content authentication;
    // the shared Source root SHA-256 identity remains authoritative.
    result.dsp=codec==1;
    result.metadata={"native.bwav",SourceKind::GameStream,static_cast<std::int32_t>(rate),count,{frames},SeekSupport::Fast,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,codec ? SampleEncoding::DspAdpcm : SampleEncoding::PcmSigned,codec ? 4 : 16};
    return result;
}
}
