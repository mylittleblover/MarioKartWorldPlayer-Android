#pragma once
#include "decoder/NintendoDsp.hpp"

namespace player2::audio {
// Canonical mono Nintendo .dsp: 0x60-byte BE header and whole 8-byte ADPCM
// frames. Headerless/interleaved/vendor extensions require separate admission.
template<class Bytes> NintendoStreamLayout parseStandaloneDspLayout(Bytes& input,const DecodeLimits& limits,bool headerOnly=false) {
    input.need(0,96);
    const auto frames=input.u32(0,true),nibbles=input.u32(4,true),rate=input.u32(8,true);
    if(input.u16(14,true)!=0 || input.u16(60,true)!=0)fail(SourceStatus::UnsupportedCodec);
    validateFormat(static_cast<std::int32_t>(rate),1);
    boundStreamDuration(frames,rate,limits);
    boundPcm(frames,1,limits);
    const auto expected=std::uint64_t(frames/14)*16+(frames%14 ? frames%14+2 : 0);
    if(nibbles!=expected)fail(SourceStatus::MalformedSource);
    const auto blocks=(std::uint64_t(frames)+13)/14,encoded=blocks*8;
    if((!headerOnly && encoded!=input.size()-96) || (headerOnly && encoded<input.size()-96))fail(SourceStatus::MalformedSource);
    const auto loop=input.u16(12,true);
    if(loop>1 || input.u32(24,true)!=2)fail(SourceStatus::MalformedSource);
    for(std::size_t p=74;p<96;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    const auto addressToSample=[](std::uint32_t address) {
        const auto within=address%16;
        if(within<2)fail(SourceStatus::SourceLoopBounds);
        return std::uint64_t(address/16)*14+within-2;
    };
    std::optional<SourceLoopMetadata> sourceLoop;
    if(loop) {
        const auto start=addressToSample(input.u32(16,true)),end=addressToSample(input.u32(20,true));
        if(start>end || end>=frames)fail(SourceStatus::SourceLoopBounds);
        sourceLoop=SourceLoopMetadata{{static_cast<std::int64_t>(start)},{static_cast<std::int64_t>(end+1)}};
        const auto loopBlock=96+start/14*8;
        if(input.u16(68,true)>0x7f || ((!headerOnly || loopBlock<input.size()) && input.u16(68,true)!=input.u8(loopBlock)))fail(SourceStatus::MalformedSource);
    }
    if(input.u16(62,true)!=input.u8(96))fail(SourceStatus::MalformedSource);
    SourceMetadata metadata{"native.nintendo-dsp",SourceKind::GameStream,static_cast<std::int32_t>(rate),1,
        {frames},SeekSupport::Fast,KnownDuration|LoopMetadata|DeterministicRender|Streaming,sourceLoop,SampleEncoding::DspAdpcm,4};
    return {metadata,96,static_cast<std::uint32_t>(blocks),8,14,8,0,true};
}
template<class Bytes> std::array<NintendoDspChannel,MaximumNintendoStreamChannels> parseStandaloneDspChannels(Bytes& input) {
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> result{};
    auto& ch=result[0];
    for(std::size_t n=0;n<16;++n)ch.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(28+n*2,true)));
    ch.initial={dspSigned(input.u16(64,true)),dspSigned(input.u16(66,true))};
    ch.loop={dspSigned(input.u16(70,true)),dspSigned(input.u16(72,true))};
    return result;
}
// No magic exists: match the canonical header even when probing receives only
// the bounded prefix of a large file. Open validates the complete payload extent.
inline bool probeStandaloneDsp(ByteView bytes)noexcept {
    if(!bytes.data || bytes.size<104)return false;
    try {SourceBytes input(bytes,{});static_cast<void>(parseStandaloneDspLayout(input,{},true));return true;}
    catch(...){return false;}
}
}
