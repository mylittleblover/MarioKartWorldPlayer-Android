#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/MsAdpcm.hpp"

namespace player2::audio {
template<class Bytes> bool waveMsAdpcmFormat(Bytes& input) {
    if(input.size()<12 || (!input.magic(0,"RIFF") && !input.magic(0,"RIFX")) || !input.magic(8,"WAVE"))return false;
    const bool big=input.magic(0,"RIFX");
    for(std::size_t at=12,chunks=0;at<input.size() && chunks<4096;++chunks) {
        input.need(at,8);const auto size=input.u32(at+4,big);const auto begin=at+8;input.need(begin,size);
        if(input.magic(at,"fmt "))return size>=2 && input.u16(begin,big)==2;
        at=input.offset(begin,std::size_t(size)+(size&1u));
    }
    return false;
}
template<class Bytes> MsAdpcmLayout parseMsAdpcmWave(Bytes& input,const DecodeLimits& limits) {
    if(input.magic(0,"RIFX"))fail(SourceStatus::UnsupportedCodec);
    if(!input.magic(0,"RIFF") || !input.magic(8,"WAVE"))fail(SourceStatus::UnsupportedFormat);
    if(std::uint64_t(input.u32(4))+8!=input.size())fail(SourceStatus::MalformedSource);
    MsAdpcmLayout result;unsigned rate=0,channels=0;std::uint32_t frames=0;bool fmt=false,fact=false,sawLoop=false;
    std::optional<SourceLoopMetadata> loop;
    for(std::size_t at=12,chunks=0;at<input.size();) {
        input.need(at,8);const auto size=input.u32(at+4);const auto begin=at+8;input.need(begin,size);
        if(++chunks>4096 || chunks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        if(input.magic(at,"fmt ")) {
            if(fmt || size<50)fail(SourceStatus::MalformedSource);
            if(size!=50 || input.u16(begin)!=2 || input.u16(begin+14)!=4 || input.u16(begin+16)!=32 || input.u16(begin+20)!=7)fail(SourceStatus::UnsupportedCodec);
            channels=input.u16(begin+2);rate=input.u32(begin+4);validateFormat(rate,channels);
            result.align=input.u16(begin+12);result.blockFrames=input.u16(begin+18);
            if(result.align<7*channels || result.blockFrames!=2+(result.align-7*channels)*2/channels || !input.u32(begin+8))
                fail(SourceStatus::MalformedSource);
            if(result.align>32768)fail(SourceStatus::ResourceLimit);
            for(unsigned n=0;n<7;++n)for(unsigned k=0;k<2;++k)
                if(msAdpcmSigned(input.u16(begin+22+n*4+k*2))!=MsAdpcmCoefficients[n][k])fail(SourceStatus::UnsupportedCodec);
            fmt=true;
        }else if(input.magic(at,"data")) {
            if(result.data)fail(SourceStatus::MalformedSource);
            result.data=begin;result.bytes=size;
        }else if(input.magic(at,"fact")) {
            if(fact || size!=4)fail(SourceStatus::MalformedSource);
            frames=input.u32(begin);fact=true;
        }else if(input.magic(at,"smpl")) {
            if(sawLoop || size<36)fail(SourceStatus::MalformedSource);
            sawLoop=true;const auto count=input.u32(begin+28);
            if(count>1)fail(SourceStatus::UnsupportedCodec);
            if(count) {
                if(size<60)fail(SourceStatus::MalformedSource);
                if(input.u32(begin+40) || input.u32(begin+52))fail(SourceStatus::UnsupportedCodec);
                loop=SourceLoopMetadata{{input.u32(begin+44)},{std::int64_t(input.u32(begin+48))+1}};
            }
        }
        at=input.offset(begin,std::size_t(size)+(size&1u));
    }
    if(!fmt || !fact || !result.data || !result.bytes || result.bytes%result.align)fail(SourceStatus::MalformedSource);
    const auto blocks=result.bytes/result.align;
    if(blocks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
    const auto capacity=std::uint64_t(blocks)*result.blockFrames;
    if(!frames || frames>capacity || frames<=capacity-result.blockFrames)fail(SourceStatus::MalformedSource);
    boundStreamDuration(frames,rate,limits);
    if(loop && (loop->start.value>=loop->end.value || loop->end.value>frames))fail(SourceStatus::SourceLoopBounds);
    const auto heap=16384+std::uint64_t(result.blockFrames)*channels*sizeof(float);
    if(heap>limits.maximumDecodedBytes)fail(SourceStatus::ResourceLimit);
    result.metadata={"native.wave",SourceKind::StandardMedia,static_cast<std::int32_t>(rate),static_cast<std::int32_t>(channels),{frames},SeekSupport::Fast,
        KnownDuration|DeterministicRender|Streaming|(loop ? LoopMetadata : 0u),loop,SampleEncoding::MsAdpcm,4};
    return result;
}

}
