#pragma once
#include "decoder/SourceBytes.hpp"
#include <array>
#include <algorithm>
#include <cmath>

namespace player2::audio {
struct AdxHistory {std::int32_t previous{},older{};};
struct AdxLayout {
    SourceMetadata metadata;
    std::size_t start{};
    std::array<AdxHistory,2> initial{};
    std::int32_t firstCoefficient{},secondCoefficient{};
    bool version3{};
};
inline std::int32_t adxSigned(std::uint16_t bits)noexcept{return bits<32768 ? bits : std::int32_t(bits)-65536;}
template<class Input> AdxLayout parseAdx(Input& input,const DecodeLimits& limits) {
    if(input.u16(0,true)!=0x8000)fail(SourceStatus::UnsupportedFormat);
    input.need(0,20);
    if(input.u8(4)!=3 || input.u8(5)!=18 || input.u8(6)!=4)fail(SourceStatus::UnsupportedCodec);
    const auto version=input.u16(18,true);
    if(version!=0x0300 && version!=0x0400)fail(SourceStatus::UnsupportedCodec); // No encrypted/fixed/exponential approximation.
    const auto channels=input.u8(7);const auto rate=input.u32(8,true),frames=input.u32(12,true);
    validateFormat(static_cast<std::int32_t>(rate),channels);boundStreamDuration(frames,rate,limits);
    const std::size_t start=std::size_t(input.u16(2,true))+4;
    if(start<26)fail(SourceStatus::MalformedSource);
    input.need(0,start);
    constexpr char copyright[]="(c)CRI";
    for(unsigned n=0;n<6;++n)if(input.u8(start-6+n)!=static_cast<std::uint8_t>(copyright[n]))fail(SourceStatus::MalformedSource);
    AdxLayout result;result.start=start;result.version3=version==0x0300;
    std::size_t used=20;
    if(!result.version3) {
        if(start-6<32)fail(SourceStatus::MalformedSource);
        if(input.u32(20,true))fail(SourceStatus::UnsupportedCodec);
        for(unsigned ch=0;ch<channels;++ch)result.initial[ch]={adxSigned(input.u16(24+ch*4,true)),adxSigned(input.u16(26+ch*4,true))};
        if(channels==1 && input.u32(28,true))fail(SourceStatus::UnsupportedCodec);
        used=32;
    }
    const auto blocks=(std::uint64_t(frames)+31)/32,group=std::uint64_t(channels)*18,encoded=blocks*group;
    if(encoded>input.size()-start)fail(SourceStatus::MalformedSource);
    const auto end=start+static_cast<std::size_t>(encoded),trailer=input.size()-end;
    if(trailer) {
        if(trailer!=18 || input.u16(end,true)!=0x8001 || input.u16(end+2,true)!=14)fail(SourceStatus::MalformedSource);
        for(std::size_t p=end+4;p<input.size();++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    }
    std::optional<SourceLoopMetadata> loop;
    if(start-6-used>=24) {
        if(input.u16(used,true) || input.u16(used+2,true)>1)fail(SourceStatus::UnsupportedCodec);
        const auto enabled=input.u32(used+4,true);
        if(enabled>1)fail(SourceStatus::UnsupportedCodec);
        if(enabled) {
            const auto first=input.u32(used+8,true),last=input.u32(used+16,true);
            if(first>=last || last>frames || input.u32(used+12,true)!=start+std::uint64_t(first/32)*group ||
                input.u32(used+20,true)!=start+((std::uint64_t(last)+31)/32)*group)fail(SourceStatus::SourceLoopBounds);
            loop=SourceLoopMetadata{{first},{last}};
        }
        used+=24;
    }
    for(std::size_t p=used;p<start-6;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    const auto cutoff=input.u16(16,true);
    if(cutoff>=rate/2)fail(SourceStatus::UnsupportedCodec);
    // CRI's high-pass predictor. Coefficients are truncated toward zero;
    // negative predictor terms below use defined floor division, never shifts.
    const double root=std::sqrt(2.0),difference=root-1.0;
    const double term=root-std::cos(6.28318530717958647692*cutoff/rate);
    const double pole=(term-std::sqrt((term+difference)*(term-difference)))/difference;
    result.firstCoefficient=static_cast<std::int32_t>(pole*8192);
    result.secondCoefficient=static_cast<std::int32_t>(-pole*pole*4096);
    for(std::uint64_t block=0;block<blocks;++block)for(unsigned channel=0;channel<channels;++channel)
        if(input.u16(start+static_cast<std::size_t>(block*group+channel*18),true)&0x8000)fail(SourceStatus::UnsupportedCodec);
    result.metadata={"native.cri-adx",SourceKind::GameStream,static_cast<std::int32_t>(rate),channels,{frames},SeekSupport::Replay,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,SampleEncoding::AdxAdpcm,4};
    return result;
}
inline std::int64_t adxFloor(std::int64_t value)noexcept{return value>=0 ? value/4096 : -((-value+4095)/4096);}
inline std::int32_t adxSample(int nibble,unsigned scale,const AdxLayout& layout,AdxHistory& history)noexcept {
    if(nibble>=8)nibble-=16;
    const auto first=std::int64_t(layout.firstCoefficient)*history.previous,second=std::int64_t(layout.secondCoefficient)*history.older;
    const auto prediction=layout.version3 ? adxFloor(first)+adxFloor(second) : adxFloor(first+second);
    const auto value=std::clamp<std::int64_t>(std::int64_t(nibble)*scale+prediction,-32768,32767);
    history.older=history.previous;history.previous=static_cast<std::int32_t>(value);return history.previous;
}
}
