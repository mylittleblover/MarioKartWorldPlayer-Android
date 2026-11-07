#pragma once
#include "decoder/NintendoStreamLayout.hpp"
#include <array>

namespace player2::audio {
// One container/block parser shared by both codec providers and source leases.
template<class Bytes> NintendoStreamLayout parseBfstmLayout(Bytes& input,const DecodeLimits& limits,bool dsp=false,
    NintendoStreamContainer container=NintendoStreamContainer::Bfstm) {
    const bool bcstm=container==NintendoStreamContainer::Bcstm;
    if(!input.magic(0,bcstm ? "CSTM" : "FSTM")) fail(SourceStatus::UnsupportedFormat);
    const auto bom=input.u16(4,true);
    if(bom!=0xfeff && bom!=0xfffe) fail(SourceStatus::MalformedSource);
    const bool big=bom==0xfeff;
    const auto header=input.u16(6,big),count=input.u16(0x10,big);
    if(input.u32(12,big)!=input.size() || header<0x14 || count<2 || count>16 || 0x14+std::size_t(count)*12>header) fail(SourceStatus::MalformedSource);
    input.need(0,header);
    std::size_t info=0,infoSize=0,data=0,dataSize=0;
    std::array<std::pair<std::size_t,std::size_t>,16> ranges{};
    for(std::size_t n=0;n<count;++n) {
        const auto p=0x14+n*12; const auto type=input.u16(p,big);
        const auto start=std::size_t(input.u32(p+4,big)),size=std::size_t(input.u32(p+8,big));
        if(start<header || size<8) fail(SourceStatus::MalformedSource);
        input.need(start,size);
        for(std::size_t previous=0;previous<n;++previous)
            if(start<ranges[previous].second && ranges[previous].first<start+size) fail(SourceStatus::MalformedSource);
        ranges[n]={start,start+size};
        if(input.u32(start+4,big)!=size) fail(SourceStatus::MalformedSource);
        if(type==0x4001 && !input.magic(start,"SEEK"))fail(bcstm ? SourceStatus::UnsupportedCodec : SourceStatus::MalformedSource);
        if(type==0x4000) { if(info) fail(SourceStatus::MalformedSource); info=start; infoSize=size; }
        if(type==0x4002) { if(data) fail(SourceStatus::MalformedSource); data=start; dataSize=size; }
    }
    if(!info || !data || infoSize<0x58 || !input.magic(info,"INFO") || !input.magic(data,"DATA")) fail(SourceStatus::MalformedSource);
    if(input.u16(info+8,big)!=0x4100) fail(SourceStatus::MalformedSource);
    const auto stream=input.offset(info+8,input.u32(info+12,big));
    if(stream<info+8 || stream>info+infoSize || 0x38>info+infoSize-stream) fail(SourceStatus::MalformedSource);
    const auto codec=input.u8(stream),loop=input.u8(stream+1);
    const auto channels=input.u8(stream+2),regions=input.u8(stream+3);
    const auto rate=static_cast<std::int32_t>(input.u32(stream+4,big));
    validateFormat(rate,1);
    const auto maximumChannels=MaximumNintendoStreamChannels;
    if(channels<1 || channels>maximumChannels)fail(SourceStatus::UnsupportedChannels);
    if((dsp ? codec!=2 : codec>1) || regions!=0) fail(SourceStatus::UnsupportedCodec);
    if(loop>1) fail(SourceStatus::MalformedSource);
    const auto loopStart=input.u32(stream+8,big),frames=input.u32(stream+12,big);
    const auto blocks=input.u32(stream+16,big),blockBytes=input.u32(stream+20,big),blockFrames=input.u32(stream+24,big);
    const auto lastBytes=input.u32(stream+28,big),lastFrames=input.u32(stream+32,big),lastPadded=input.u32(stream+36,big);
    const std::uint32_t width=dsp ? 0 : codec==1 ? 2 : 1;
    validateNintendoBlocks(frames,channels,blocks,blockBytes,blockFrames,lastBytes,lastFrames,lastPadded,width,dsp,input.size(),maximumChannels);
    const auto metadata=nintendoStreamMetadata(container,dsp,rate,channels,frames,loop,loopStart,width,limits);
    if(input.u16(stream+48,big)!=0x1f00) fail(SourceStatus::MalformedSource);
    const auto start=input.offset(data+8,input.u32(stream+52,big));
    if(start<data+8 || start>data+dataSize) fail(SourceStatus::MalformedSource);
    const auto required=std::uint64_t(blocks-1)*blockBytes*channels+std::uint64_t(lastPadded)*channels;
    if(required>data+dataSize-start) fail(SourceStatus::MalformedSource);
    return {metadata,start,blocks,blockBytes,blockFrames,lastPadded,width,big};
}
}
