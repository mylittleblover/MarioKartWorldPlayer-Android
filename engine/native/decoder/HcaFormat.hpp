#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/HcaProbe.hpp"
#include <array>
#include <vector>
extern "C" {
#include <clhca.h>
}

namespace player2::audio {
struct HcaLayout {
    SourceMetadata metadata;
    std::vector<std::uint8_t> header;
    std::size_t blockSize{},blockCount{},delay{},padding{};
};
inline std::uint16_t hcaCrc(const std::uint8_t* data,std::size_t size)noexcept {
    std::uint16_t crc=0;
    for(std::size_t n=0;n<size;++n) {
        crc^=std::uint16_t(data[n])<<8;
        for(unsigned bit=0;bit<8;++bit)crc=std::uint16_t((unsigned(crc)<<1)^((crc&0x8000) ? 0x8005u : 0u));
    }
    return crc;
}
template<class Bytes> HcaLayout parseHca(Bytes& input,const DecodeLimits& limits) {
    if(!hcaTag(input,0,"HCA\0"))fail(SourceStatus::UnsupportedFormat);
    input.need(0,40);
    if(input.u16(4,true)!=0x200)fail(SourceStatus::UnsupportedCodec);
    const auto headerSize=input.u16(6,true),blockSize=input.u16(28,true);
    if(headerSize<42 || blockSize<8)fail(SourceStatus::MalformedSource);
    input.need(0,headerSize);
    const std::uint64_t memory=2*std::uint64_t(clHCA_sizeof())+2*std::uint64_t(blockSize)+headerSize+32768;
    if(limits.maximumDecodedBytes<memory)fail(SourceStatus::ResourceLimit);
    if(!hcaTag(input,8,"fmt\0") || !hcaTag(input,24,"comp"))fail(SourceStatus::UnsupportedCodec);
    const auto channels=input.u8(12);
    const auto rate=(std::uint32_t(input.u8(13))<<16)|input.u16(14,true),blocks=input.u32(16,true);
    const auto delay=input.u16(20,true),padding=input.u16(22,true);
    validateFormat(static_cast<std::int32_t>(rate),channels);
    const auto rawFrames=std::uint64_t(blocks)*1024;
    if(!blocks || rawFrames<=std::uint64_t(delay)+padding)fail(SourceStatus::MalformedSource);
    if(rawFrames>UINT32_MAX)fail(SourceStatus::ResourceLimit); // Upstream derived counters are uint32.
    const auto frames=rawFrames-delay-padding;boundStreamDuration(frames,rate,limits);
    if(std::uint64_t(headerSize)+std::uint64_t(blocks)*blockSize!=input.size())fail(SourceStatus::MalformedSource);
    if(input.u8(30)!=1 || input.u8(31)!=15 || input.u8(32)>1 || input.u8(33) || input.u8(38) || input.u8(39))
        fail(SourceStatus::UnsupportedCodec);
    const auto total=input.u8(34),base=input.u8(35),stereo=input.u8(36),hfr=input.u8(37);
    if(!base || !total || total>128 || base+stereo>total || stereo>base || hfr>128 || (base+stereo<total && !hfr))
        fail(SourceStatus::MalformedSource);
    const std::size_t end=headerSize-2;std::size_t cursor=40;
    auto tag=[&](const char* text){return cursor<=end && end-cursor>=4 && hcaTag(input,cursor,text);};
    auto need=[&](std::size_t size){if(cursor>end || size>end-cursor)fail(SourceStatus::MalformedSource);};
    if(tag("ath\0")){need(6);if(input.u16(cursor+4,true))fail(SourceStatus::UnsupportedCodec);cursor+=6;}
    std::optional<SourceLoopMetadata> loop;
    if(tag("loop")) {
        need(16);const auto first=input.u32(cursor+4,true),last=input.u32(cursor+8,true);
        const auto startDelay=input.u16(cursor+12,true),endPadding=input.u16(cursor+14,true);
        if(first>last || last>=blocks || startDelay>=1024 || endPadding>=1024)fail(SourceStatus::SourceLoopBounds);
        const auto start=std::int64_t(first)*1024+startDelay-delay,finish=(std::int64_t(last)+1)*1024-endPadding-delay;
        if(start<0 || start>=finish || std::uint64_t(finish)>frames)fail(SourceStatus::SourceLoopBounds);
        loop=SourceLoopMetadata{{start},{finish}};cursor+=16;
    }
    if(tag("ciph")){need(6);if(input.u16(cursor+4,true)>1)fail(SourceStatus::UnsupportedCodec);cursor+=6;}
    if(tag("rva\0")){need(8);if(input.u32(cursor+4,true)!=0x3f800000)fail(SourceStatus::UnsupportedCodec);cursor+=8;}
    if(tag("comm")){need(5);const auto size=input.u8(cursor+4);need(5+size);cursor+=5+size;}
    if(tag("pad\0"))cursor+=4;
    for(;cursor<end;++cursor)if(input.u8(cursor))fail(SourceStatus::UnsupportedCodec);
    HcaLayout layout;layout.header.resize(headerSize);
    for(std::size_t n=0;n<headerSize;++n)layout.header[n]=input.u8(n);
    if(hcaCrc(layout.header.data(),layout.header.size()))fail(SourceStatus::MalformedSource);
    std::vector<std::uint8_t> block(blockSize);
    for(std::size_t n=0;n<blocks;++n) {
        const auto p=headerSize+n*blockSize;
        for(std::size_t i=0;i<block.size();++i)block[i]=input.u8(p+i);
        if(block[0]!=0xff || block[1]!=0xff || hcaCrc(block.data(),block.size()))fail(SourceStatus::MalformedSource);
    }
    layout.blockSize=blockSize;layout.blockCount=blocks;layout.delay=delay;layout.padding=padding;
    layout.metadata={"native.cri-hca",SourceKind::GameStream,static_cast<std::int32_t>(rate),channels,{static_cast<std::int64_t>(frames)},
        SeekSupport::Replay,KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,SampleEncoding::Hca,0};
    return layout;
}
}
