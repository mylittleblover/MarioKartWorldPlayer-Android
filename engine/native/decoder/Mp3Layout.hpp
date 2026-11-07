#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
struct Mp3FrameHeader {int rate{},channels{},samples{},bytes{};};
inline Mp3FrameHeader mp3Header(std::uint32_t header) {
    if((header&0xffe00000u)!=0xffe00000u)fail(SourceStatus::MalformedSource);
    const auto version=(header>>19)&3,layer=(header>>17)&3,bitrate=(header>>12)&15,frequency=(header>>10)&3;
    if(version==1 || layer==0 || bitrate==15 || frequency==3 || (header&3)==2)fail(SourceStatus::MalformedSource);
    if(layer!=1 || bitrate==0 || !(header&(1u<<16)))fail(SourceStatus::UnsupportedCodec); // Free-format / Layer I-II / CRC-protected subset pending.
    static constexpr int rates[]{44100,48000,32000},rates1[]{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320};
    static constexpr int rates2[]{0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
    const auto rate=rates[frequency]/(version==3 ? 1 : version==2 ? 2 : 4);
    return {rate,((header>>6)&3)==3 ? 1 : 2,version==3 ? 1152 : 576,
        (version==3 ? 144000*rates1[bitrate] : 72000*rates2[bitrate])/rate+int((header>>9)&1)};
}
inline bool probeMp3(ByteView input)noexcept {
    if(!input.data || input.size<4)return false;
    if(input.data[0]=='I' && input.data[1]=='D' && input.data[2]=='3')return true;
    const auto header=(std::uint32_t(input.data[0])<<24)|(std::uint32_t(input.data[1])<<16)|(std::uint32_t(input.data[2])<<8)|input.data[3];
    return (header&0xffe00000u)==0xffe00000u && ((header>>17)&3)==1 && ((header>>19)&3)!=1;
}
struct Mp3Layout {
    SourceMetadata metadata;
    std::size_t start{},end{},frameCount{};
    int frameSamples{};std::uint32_t delay{},padding{};
};
template<class Bytes> Mp3Layout parseMp3Layout(Bytes& input,const DecodeLimits& limits) {
    std::size_t start=0,end=input.size();
    if(input.u8(0)=='I' && input.u8(1)=='D' && input.u8(2)=='3') {
        input.need(0,10);const auto version=input.u8(3),flags=input.u8(5);
        if(version<2 || version>4)fail(SourceStatus::UnsupportedCodec);
        if(input.u8(4)==255 || (flags & (version==2 ? 0x3f : version==3 ? 0x1f : 0x0f)))fail(SourceStatus::MalformedSource);
        std::size_t size=0;
        for(int n=6;n<10;++n) {const auto byte=input.u8(n);if(byte&128)fail(SourceStatus::MalformedSource);size=(size<<7)|byte;}
        if(size>1024*1024)fail(SourceStatus::ResourceLimit);
        const auto footer=version==4 && (flags&16) ? 10u : 0u;
        input.need(10,size+footer);start=10+size+footer;
        if(footer) {
            const auto at=10+size;
            if(input.u8(at)!='3' || input.u8(at+1)!='D' || input.u8(at+2)!='I')fail(SourceStatus::MalformedSource);
            for(int n=3;n<10;++n)if(input.u8(at+n)!=input.u8(n))fail(SourceStatus::MalformedSource);
        }
    }
    if(end>=128 && input.u8(end-128)=='T' && input.u8(end-127)=='A' && input.u8(end-126)=='G')end-=128;
    if(end>=32 && input.magic(end-32,"APET"))fail(SourceStatus::UnsupportedCodec);
    input.need(start,4);const auto first=mp3Header(input.u32(start,true));validateFormat(first.rate,first.channels);
    if(first.bytes<4 || first.bytes>2048)fail(SourceStatus::MalformedSource);
    Mp3Layout result;result.end=end;result.frameSamples=first.samples;
    std::size_t cursor=start;std::uint32_t declaredFrames=0,declaredBytes=0;bool tag=false;
    while(cursor<end) {
        input.need(cursor,4);const auto frame=mp3Header(input.u32(cursor,true));
        if(frame.rate!=first.rate || frame.channels!=first.channels || frame.samples!=first.samples)fail(SourceStatus::UnsupportedCodec);
        if(frame.bytes<4 || frame.bytes>2048 || static_cast<std::size_t>(frame.bytes)>end-cursor)fail(SourceStatus::MalformedSource);
        if(cursor==start) {
            const auto side=first.samples==1152 ? (first.channels==1 ? 17u : 32u) : (first.channels==1 ? 9u : 17u);
            const auto at=cursor+4+side,frameEnd=cursor+frame.bytes;
            if(at+8<=frameEnd && (input.magic(at,"Xing") || input.magic(at,"Info"))) {
                tag=true;const auto flags=input.u32(at+4,true);if(flags&~15u)fail(SourceStatus::MalformedSource);
                auto field=at+8;
                auto need=[&](std::size_t bytes){if(bytes>frameEnd-field)fail(SourceStatus::MalformedSource);};
                if(flags&1) {need(4);declaredFrames=input.u32(field,true);if(!declaredFrames)fail(SourceStatus::MalformedSource);field+=4;}
                if(flags&2) {need(4);declaredBytes=input.u32(field,true);field+=4;}
                if(flags&4) {need(100);field+=100;}
                if(flags&8) {need(4);field+=4;}
                if(field<frameEnd && input.u8(field)!=0) {
                    need(36); // LAME/Lavc-compatible gapless fields; retain original tag bytes.
                    if(!input.magic(field,"LAME") && !input.magic(field,"Lavc") && !input.magic(field,"Lavf"))fail(SourceStatus::UnsupportedCodec);
                    result.delay=((std::uint32_t(input.u8(field+21))<<4)|(input.u8(field+22)>>4))+529;
                    const auto padding=((std::uint32_t(input.u8(field+22))&15)<<8)|input.u8(field+23);
                    if(padding<529)fail(SourceStatus::MalformedSource);
                    result.padding=padding-529;
                }
                result.start=frameEnd;
            } else {
                if(cursor+40<=frameEnd && input.magic(cursor+36,"VBRI"))fail(SourceStatus::UnsupportedCodec);
                result.start=cursor;
            }
        }
        ++result.frameCount;cursor+=frame.bytes;
        if(result.frameCount>1048576)fail(SourceStatus::ResourceLimit);
    }
    if(cursor!=end || result.frameCount<=static_cast<std::size_t>(tag))fail(SourceStatus::MalformedSource);
    result.frameCount-=static_cast<std::size_t>(tag);
    if((declaredFrames && declaredFrames!=result.frameCount) || (declaredBytes && declaredBytes!=end-start))fail(SourceStatus::MalformedSource);
    const auto raw=std::uint64_t(result.frameCount)*first.samples,trim=std::uint64_t(result.delay)+result.padding;
    if(trim>=raw)fail(SourceStatus::MalformedSource);
    boundStreamDuration(raw-trim,first.rate,limits);
    result.metadata={"native.mp3",SourceKind::StandardMedia,first.rate,first.channels,{static_cast<std::int64_t>(raw-trim)},SeekSupport::Fast,
        KnownDuration|DeterministicRender|Streaming,std::nullopt,SampleEncoding::MpegLayer3,0};
    return result;
}
}
