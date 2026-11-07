#pragma once
#include "decoder/NintendoStreamLayout.hpp"

namespace player2::audio {
struct BrstmSections {std::size_t head,headSize,stream,data,dataSize;};
template<class Bytes> BrstmSections parseBrstmSections(Bytes& input) {
    if(!input.magic(0,"RSTM"))fail(SourceStatus::UnsupportedFormat);
    if(input.u16(4,true)!=0xfeff)fail(SourceStatus::MalformedSource);
    if(input.u16(6,true)!=0x0100)fail(SourceStatus::UnsupportedCodec); // Historical 0.1 layout is not 1.0.
    const auto header=input.u16(12,true),count=input.u16(14,true);
    if(input.u32(8,true)!=input.size() || header<40 || count<2 || count>3)fail(SourceStatus::MalformedSource);
    input.need(0,header);std::array<std::pair<std::size_t,std::size_t>,3> ranges{};
    for(std::size_t n=0;n<3;++n) {
        const auto start=std::size_t(input.u32(16+n*8,true)),size=std::size_t(input.u32(20+n*8,true));
        if(n==1 && start==0 && size==0)continue;
        if(start<header || size<8)fail(SourceStatus::MalformedSource);
        input.need(start,size);
        if(!input.magic(start,n==0 ? "HEAD" : n==1 ? "ADPC" : "DATA") || input.u32(start+4,true)!=size)fail(SourceStatus::MalformedSource);
        for(std::size_t previous=0;previous<n;++previous)
            if(start<ranges[previous].second && ranges[previous].first<start+size)fail(SourceStatus::MalformedSource);
        ranges[n]={start,start+size};
    }
    const auto head=ranges[0].first,headSize=ranges[0].second-head;
    if(headSize<32 || input.u16(head+8,true)!=0x0100)fail(SourceStatus::MalformedSource);
    const auto stream=input.offset(head+8,input.u32(head+12,true));
    if(stream<head+32 || stream>head+headSize || 52>head+headSize-stream)fail(SourceStatus::MalformedSource);
    return {head,headSize,stream,ranges[2].first,ranges[2].second-ranges[2].first};
}
template<class Bytes> NintendoStreamLayout parseBrstmLayout(Bytes& input,const DecodeLimits& limits,bool dsp=false) {
    const auto sections=parseBrstmSections(input);const auto stream=sections.stream;
    const auto codec=input.u8(stream),loop=input.u8(stream+1),channels=input.u8(stream+2);
    const auto rate=input.u16(stream+4,true);validateFormat(rate,1);
    if(channels<1 || channels>MaximumNintendoStreamChannels)fail(SourceStatus::UnsupportedChannels);
    if(dsp ? codec!=2 : codec>1)fail(SourceStatus::UnsupportedCodec);
    const auto loopStart=input.u32(stream+8,true),frames=input.u32(stream+12,true),start=input.u32(stream+16,true);
    const auto blocks=input.u32(stream+20,true),blockBytes=input.u32(stream+24,true),blockFrames=input.u32(stream+28,true);
    const auto lastBytes=input.u32(stream+32,true),lastFrames=input.u32(stream+36,true),lastPadded=input.u32(stream+40,true);
    const std::uint32_t width=dsp ? 0 : codec==1 ? 2 : 1;
    validateNintendoBlocks(frames,channels,blocks,blockBytes,blockFrames,lastBytes,lastFrames,lastPadded,width,dsp,input.size(),MaximumNintendoStreamChannels);
    const auto metadata=nintendoStreamMetadata(NintendoStreamContainer::Brstm,dsp,rate,channels,frames,loop,loopStart,width,limits);
    if(start<sections.data+8 || start>sections.data+sections.dataSize)fail(SourceStatus::MalformedSource);
    const auto required=std::uint64_t(blocks-1)*blockBytes*channels+std::uint64_t(lastPadded)*channels;
    if(required>sections.data+sections.dataSize-start)fail(SourceStatus::MalformedSource);
    return {metadata,start,blocks,blockBytes,blockFrames,lastPadded,width,true};
}
}
