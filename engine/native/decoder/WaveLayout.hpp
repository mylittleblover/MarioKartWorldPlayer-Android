#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/G711.hpp"
#include <cmath>
#include <limits>

namespace player2::audio {
template<class Bytes> inline float wavePcmSample(Bytes& input,std::size_t offset,std::uint16_t bits,bool floating,bool big=false) {
    std::uint64_t raw=0;
    for(std::size_t n=0;n<bits/8u;++n) raw|=std::uint64_t(input.u8(offset+(big ? bits/8u-1-n : n)))<<(8*n);
    if(floating) {
        double value;
        if(bits==32) { const auto word=static_cast<std::uint32_t>(raw); float f; std::memcpy(&f,&word,4); value=f; }
        else std::memcpy(&value,&raw,8);
        if(!std::isfinite(value) || std::abs(value)>std::numeric_limits<float>::max()) fail(SourceStatus::MalformedSource);
        return static_cast<float>(value);
    }
    if(bits==8) return (static_cast<float>(raw)-128)/128;
    const auto sign=std::uint64_t{1}<<(bits-1);
    const auto signedValue=raw<sign ? static_cast<std::int64_t>(raw) : static_cast<std::int64_t>(raw)-static_cast<std::int64_t>(sign*2);
    return static_cast<float>(static_cast<double>(signedValue)/static_cast<double>(sign));
}
template<class Bytes> inline float waveLinearSample(Bytes& input,std::size_t offset,std::uint16_t bits,bool floating,bool big,SampleEncoding encoding) {
    if(encoding==SampleEncoding::G711ALaw)return g711ALaw(input.u8(offset))/32768.0f;
    if(encoding==SampleEncoding::G711MuLaw)return g711MuLaw(input.u8(offset))/32768.0f;
    return wavePcmSample(input,offset,bits,floating,big);
}
struct WaveLayout {SourceMetadata metadata;std::size_t dataOffset{};bool floating{},big{};};
// One bounded format/parser source for memory and native encoded-input leases.
template<class Bytes> WaveLayout parseWaveLayout(Bytes& input,const DecodeLimits& limits) {
    const bool big=input.magic(0,"RIFX");
    if((!big && !input.magic(0,"RIFF")) || !input.magic(8,"WAVE")) fail(SourceStatus::UnsupportedFormat);
    if(std::uint64_t(input.u32(4,big))+8!=input.size()) fail(SourceStatus::MalformedSource);
    std::size_t data=0,dataSize=0;
    std::int32_t rate=0,channels=0;
    std::uint16_t bits=0,align=0,codec=0;
    bool format=false,badFact=false; std::size_t chunks=0;std::optional<std::uint32_t> fact;
    std::optional<SourceLoopMetadata> loop;
    for(std::size_t p=12;p<input.size();) {
        input.need(p,8);
        const auto size=input.u32(p+4,big); const auto begin=p+8; input.need(begin,size);
        if(++chunks>4096) fail(SourceStatus::ResourceLimit);
        if(input.magic(p,"fmt ")) {
            if(format || size<16) fail(SourceStatus::MalformedSource);
            codec=input.u16(begin,big); channels=input.u16(begin+2,big); rate=static_cast<std::int32_t>(input.u32(begin+4,big));
            align=input.u16(begin+12,big); bits=input.u16(begin+14,big);
            validateFormat(rate,channels);
            if(codec==0xfffe) {
                if(big)fail(SourceStatus::UnsupportedCodec); // RIFX extensible GUID/channel layouts need separate admission.
                if(size<40 || input.u16(begin+16,big)<22 || input.u16(begin+16,big)>size-18) fail(SourceStatus::MalformedSource);
                if(input.u16(begin+18,big)!=bits) fail(SourceStatus::UnsupportedCodec);
                codec=input.u16(begin+24,big);
                if(codec!=1 && codec!=3)fail(SourceStatus::UnsupportedCodec);
                const std::uint8_t guidTail[]{0,0,0,0,0x10,0,0x80,0,0,0xaa,0,0x38,0x9b,0x71};
                for(std::size_t n=0;n<14;++n) if(input.u8(begin+26+n)!=guidTail[n]) fail(SourceStatus::UnsupportedCodec);
                const auto mask=input.u32(begin+20,big);
                if(mask!=0 && mask!=(channels==1 ? 4u : 3u)) fail(SourceStatus::UnsupportedChannels);
            }
            if((codec==1 && bits!=8 && bits!=16 && bits!=24 && bits!=32) ||
                (codec==3 && bits!=32 && bits!=64) || ((codec==6 || codec==7) && bits!=8) || (codec!=1 && codec!=3 && codec!=6 && codec!=7)) fail(SourceStatus::UnsupportedCodec);
            if((codec==6 || codec==7) && (size!=16 && (size!=18 || input.u16(begin+16,big))))fail(SourceStatus::UnsupportedCodec);
            if(align!=channels*(bits/8) || input.u32(begin+8,big)!=static_cast<std::uint32_t>(rate)*align) fail(SourceStatus::MalformedSource);
            format=true;
        } else if(input.magic(p,"data")) {
            if(data) fail(SourceStatus::MalformedSource);
            data=begin; dataSize=size;
        } else if(input.magic(p,"fact")) {
            if(fact || size!=4)badFact=true;else fact=input.u32(begin,big);
        } else if(input.magic(p,"smpl")) {
            if(size<36 || loop) fail(SourceStatus::MalformedSource);
            const auto count=input.u32(begin+28,big);
            if(count>1) fail(SourceStatus::UnsupportedCodec);
            if(count==1) {
                if(size<60) fail(SourceStatus::MalformedSource);
                if(input.u32(begin+40,big)!=0 || input.u32(begin+52,big)!=0) fail(SourceStatus::UnsupportedCodec);
                loop=SourceLoopMetadata{{input.u32(begin+44,big)},{std::int64_t(input.u32(begin+48,big))+1}};
            }
        }
        const auto padded=std::size_t(size)+(size&1u); input.need(begin,padded); p=begin+padded;
    }
    if(!format || !data || dataSize%align!=0) fail(SourceStatus::MalformedSource);
    const auto frames=dataSize/align; boundPcm(frames,channels,limits);
    if((codec==6 || codec==7) && (badFact || (fact && *fact!=frames)))fail(SourceStatus::MalformedSource);
    if(loop && (loop->start.value>=loop->end.value || static_cast<std::uint64_t>(loop->end.value)>frames)) fail(SourceStatus::SourceLoopBounds);
    SourceMetadata metadata{"native.wave",SourceKind::StandardMedia,rate,channels,{static_cast<std::int64_t>(frames)},SeekSupport::Fast,
        KnownDuration|RealtimeRender|DeterministicRender|Streaming|(loop ? LoopMetadata : 0u),loop};
    metadata.encoding=codec==6 ? SampleEncoding::G711ALaw : codec==7 ? SampleEncoding::G711MuLaw : codec==3 ? SampleEncoding::IeeeFloat : (bits==8 ? SampleEncoding::PcmUnsigned : SampleEncoding::PcmSigned);
    metadata.bitDepth=bits;
    return {metadata,data,codec==3,big};
}
}
