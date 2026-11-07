#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/AiffMarkers.hpp"

namespace player2::audio {
struct AiffLayout {SourceMetadata metadata;std::size_t start{};unsigned width{};bool little{},floating{},ima4{};};
std::unique_ptr<AudioFrameSource> openAiffIma4(std::unique_ptr<EncodedSourceInput>,AiffLayout,const DecodeLimits&);
template<class Bytes> unsigned aiffRate(Bytes& input,std::size_t p) {
    const auto exponent=input.u16(p,true);const int power=int(exponent)-16383;
    const auto mantissa=(std::uint64_t(input.u32(p+2,true))<<32)|input.u32(p+6,true);
    if(power<12 || power>17 || !(mantissa&(std::uint64_t{1}<<63)))fail(SourceStatus::UnsupportedSampleRate);
    const auto shift=63-power;
    if(mantissa&((std::uint64_t{1}<<shift)-1))fail(SourceStatus::UnsupportedSampleRate);
    return static_cast<unsigned>(mantissa>>shift);
}
template<class Bytes> AiffLayout parseAiff(Bytes& input,const DecodeLimits& limits) {
    input.need(0,12);
    if(!input.magic(0,"FORM") || (!input.magic(8,"AIFF") && !input.magic(8,"AIFC")))fail(SourceStatus::UnsupportedFormat);
    if(std::uint64_t(input.u32(4,true))+8!=input.size())fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384)fail(SourceStatus::ResourceLimit);
    const bool compressed=input.magic(8,"AIFC");std::size_t comm=0,sound=0,commSize=0,soundSize=0;bool version=false;
    std::size_t mark=0,markSize=0,inst=0,instSize=0;
    std::size_t p=12;unsigned chunks=0;
    while(p<input.size()) {
        if(++chunks>65536 || chunks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        input.need(p,8);const auto size=input.u32(p+4,true);const auto body=p+8;input.need(body,size);
        if(input.magic(p,"COMM")){if(comm)fail(SourceStatus::MalformedSource);comm=body;commSize=size;}
        else if(input.magic(p,"SSND")){if(sound)fail(SourceStatus::MalformedSource);sound=body;soundSize=size;}
        else if(input.magic(p,"MARK")){if(mark)fail(SourceStatus::MalformedSource);mark=body;markSize=size;}
        else if(input.magic(p,"INST")){if(inst)fail(SourceStatus::MalformedSource);inst=body;instSize=size;}
        else if(input.magic(p,"FVER")) {
            if(!compressed || version || size!=4 || input.u32(body,true)!=0xa2805140)fail(SourceStatus::UnsupportedCodec);
            version=true;
        } else if(!input.magic(p,"NAME") && !input.magic(p,"AUTH") && !input.magic(p,"ANNO") && !input.magic(p,"(c) "))
            fail(SourceStatus::UnsupportedCodec);
        p=body+size;
        if(size&1){input.need(p,1);++p;}
    }
    if(!comm || !sound || commSize<18 || soundSize<8 || (compressed && !version))fail(SourceStatus::MalformedSource);
    if(!compressed && commSize!=18)fail(SourceStatus::UnsupportedCodec);
    const auto channels=input.u16(comm,true),bits=input.u16(comm+6,true);const auto declaredFrames=input.u32(comm+2,true),rate=aiffRate(input,comm+8);
    validateFormat(rate,channels);std::uint64_t frames=declaredFrames;
    bool little=false,floating=false,ima4=false;SampleEncoding companded=SampleEncoding::Unknown;
    if(compressed) {
        if(commSize<23)fail(SourceStatus::MalformedSource);
        if(input.magic(comm+18,"sowt")){if(bits!=16)fail(SourceStatus::UnsupportedCodec);little=true;}
        else if(input.magic(comm+18,"fl32") || input.magic(comm+18,"FL32")){floating=true;if(bits!=32)fail(SourceStatus::UnsupportedCodec);}
        else if(input.magic(comm+18,"fl64") || input.magic(comm+18,"FL64")){floating=true;if(bits!=64)fail(SourceStatus::UnsupportedCodec);}
        else if(input.magic(comm+18,"alaw") || input.magic(comm+18,"ulaw")) {
            if(bits!=16)fail(SourceStatus::UnsupportedCodec); // COMM declares the logical decoded sample size.
            companded=input.magic(comm+18,"alaw") ? SampleEncoding::G711ALaw : SampleEncoding::G711MuLaw;
        }
        else if(input.magic(comm+18,"ima4")) {
            if(bits!=16)fail(SourceStatus::UnsupportedCodec);
            ima4=true;frames*=64; // COMM counts compressed packets, each emits 64 frames.
        }
        else if(!input.magic(comm+18,"NONE") && !input.magic(comm+18,"twos"))fail(SourceStatus::UnsupportedCodec);
        const auto name=input.u8(comm+22);const std::size_t used=23+name;
        if(used>commSize || commSize-used>1 || (commSize!=used && input.u8(comm+used)))fail(SourceStatus::MalformedSource);
    }
    boundStreamDuration(frames,rate,limits);
    if((!floating && bits!=8 && bits!=16 && bits!=24 && bits!=32) || (floating && bits!=32 && bits!=64))fail(SourceStatus::UnsupportedCodec);
    const auto offset=input.u32(sound,true),block=input.u32(sound+4,true);
    if(block)fail(SourceStatus::UnsupportedCodec);
    if(offset>soundSize-8)fail(SourceStatus::MalformedSource);
    const unsigned width=companded==SampleEncoding::Unknown ? bits/8 : 1;
    const std::uint64_t encoded=ima4 ? std::uint64_t(declaredFrames)*channels*34 : frames*channels*width;
    if(encoded!=soundSize-8-offset)fail(SourceStatus::MalformedSource);
    for(std::size_t n=0;n<offset;++n)if(input.u8(sound+8+n))fail(SourceStatus::UnsupportedCodec);
    const auto loop=aiffMarkers(input,mark,markSize,inst,instSize,frames,limits,chunks);
    return {{"native.aiff",SourceKind::StandardMedia,static_cast<std::int32_t>(rate),channels,{static_cast<std::int64_t>(frames)},SeekSupport::Fast,
        KnownDuration|DeterministicRender|Streaming|(loop ? LoopMetadata : 0u),loop,ima4 ? SampleEncoding::ImaAdpcm : companded!=SampleEncoding::Unknown ? companded : floating ? SampleEncoding::IeeeFloat : SampleEncoding::PcmSigned,ima4 ? 4 : companded!=SampleEncoding::Unknown ? 8 : bits},sound+8+offset,width,little,floating,ima4};
}
}
