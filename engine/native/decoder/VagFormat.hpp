#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
template<class Input> SourceMetadata parseVag(Input& input,const DecodeLimits& limits) {
    if(!input.magic(0,"VAGp"))fail(SourceStatus::UnsupportedFormat);
    input.need(0,48);
    if(input.u32(4,true)!=0x20 || input.u32(8,true))fail(SourceStatus::UnsupportedCodec);
    for(std::size_t n=0x14;n<0x20;++n)if(input.u8(n))fail(SourceStatus::UnsupportedCodec);
    const auto size=input.u32(12,true),rate=input.u32(16,true);
    if(!size || size%16 || size!=input.size()-48)fail(SourceStatus::MalformedSource);
    if(rate<8000 || rate>192000)fail(SourceStatus::UnsupportedSampleRate);
    const auto blocks=size/16;const auto frames=std::uint64_t(blocks)*28;
    boundStreamDuration(frames,rate,limits);
    if(blocks>limits.maximumEvents || limits.maximumDecodedBytes<16384)fail(SourceStatus::ResourceLimit);
    SourceMetadata metadata{"native.sony-vag",SourceKind::GameStream,static_cast<std::int32_t>(rate),1,
        {static_cast<std::int64_t>(frames)},SeekSupport::Replay,KnownDuration|LoopMetadata|DeterministicRender|Streaming,
        std::nullopt,SampleEncoding::PsAdpcm,4};
    std::int64_t loopStart=-1,loopEnd=-1;bool ended=false;
    for(std::uint32_t n=0;n<blocks;++n) {
        const auto at=48+std::size_t(n)*16;const auto header=input.u8(at),flag=input.u8(at+1);
        if((header>>4)>4 || (header&15)>12)fail(SourceStatus::UnsupportedCodec);
        if(flag!=0 && flag!=1 && flag!=2 && flag!=3 && flag!=6 && flag!=7)fail(SourceStatus::UnsupportedCodec);
        if(ended && flag!=7)fail(SourceStatus::MalformedSource);
        if(flag==6){if(loopStart>=0 || ended)fail(SourceStatus::SourceLoopBounds);loopStart=std::int64_t(n)*28;}
        if(flag==2 && (loopStart<0 || ended))fail(SourceStatus::SourceLoopBounds);
        if(flag==3){if(loopStart<0 || loopEnd>=0)fail(SourceStatus::SourceLoopBounds);loopEnd=std::int64_t(n+1)*28;ended=true;}
        if(flag==1){if(loopStart>=0)fail(SourceStatus::SourceLoopBounds);ended=true;}
        if(flag==7){if(n+1!=blocks)fail(SourceStatus::UnsupportedCodec);ended=true;}
    }
    if(!ended)fail(SourceStatus::MalformedSource);
    if(loopStart>=0) {
        if(loopEnd<=loopStart)fail(SourceStatus::SourceLoopBounds);
        metadata.sourceLoop=SourceLoopMetadata{{loopStart},{loopEnd}};
    }
    return metadata;
}
}
