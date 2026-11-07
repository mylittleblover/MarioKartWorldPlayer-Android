#pragma once
#include "decoder/NintendoWaveFormat.hpp"

namespace player2::audio {
template<class Bytes> NintendoWaveLayout parseNitroWave(Bytes& input,std::size_t at,const DecodeLimits& limits) {
    input.need(at,12);
    const auto codec=input.u8(at),loop=input.u8(at+1);
    if(codec>2)fail(SourceStatus::UnsupportedCodec);
    if(loop>1)fail(SourceStatus::MalformedSource);
    const unsigned width=codec+1;const auto size=input.size()-at-12;
    const auto first=std::uint64_t(input.u16(at+6))*4,last=(std::uint64_t(input.u16(at+6))+input.u32(at+8))*4;
    if(size%4 || last!=size)fail(SourceStatus::MalformedSource);
    NintendoWaveLayout result;
    if(codec==2) {
        if(size<=4)fail(SourceStatus::MalformedSource);
        const auto header=at+12;const auto index=input.u8(header+2);
        if(index>88)fail(SourceStatus::MalformedSource);
        if(input.u8(header+3))fail(SourceStatus::UnsupportedCodec);
        if(loop && first<4)fail(SourceStatus::SourceLoopBounds);
        result.codec=3;result.ndsIma=true;result.starts[0]=at+16;result.ima[0]={dspSigned(input.u16(header)),index};
        result.metadata=nintendoWaveMetadata(input.u16(at+2),1,static_cast<std::uint32_t>((size-4)*2),3,loop,
            loop ? static_cast<std::uint32_t>((first-4)*2) : 0,limits);
        return result; // The real four-byte decoder state is not an emitted sample.
    }
    result.codec=codec;result.width=width;result.starts[0]=at+12;
    result.metadata=nintendoWaveMetadata(input.u16(at+2),1,static_cast<std::uint32_t>(size/width),codec,loop,
        static_cast<std::uint32_t>(first/width),limits);
    // Timer is retained in the original; the explicit header Hz defines linear Source time.
    return result;
}
template<class Bytes> NintendoWaveLayout parseSwav(Bytes& input,const DecodeLimits& limits) {
    input.need(0,36);
    if(!input.magic(0,"SWAV"))fail(SourceStatus::UnsupportedFormat);
    if(input.u16(4)!=0xfeff || input.u16(6)!=0x100)fail(SourceStatus::UnsupportedCodec);
    if(input.u32(8)!=input.size() || input.u16(12)!=16 || input.u16(14)!=1 || !input.magic(16,"DATA") ||
        input.u32(20)!=input.size()-16)fail(SourceStatus::MalformedSource);
    return parseNitroWave(input,24,limits);
}
}
