#pragma once
#include "decoder/SourceComments.hpp"

namespace player2::audio {
template<class Bytes> SourceMetadata parseFlacLayout(Bytes& input,const DecodeLimits& limits) {
    if(!input.magic(0,"fLaC"))fail(SourceStatus::UnsupportedFormat);
    if((input.u8(4)&127)!=0 || input.u8(5)!=0 || input.u8(6)!=0 || input.u8(7)!=34)fail(SourceStatus::MalformedSource);
    input.need(8,34);std::uint64_t packed=0;
    for(std::size_t n=0;n<8;++n)packed=(packed<<8)|input.u8(18+n);
    const auto rate=static_cast<std::int32_t>(packed>>44),channels=static_cast<std::int32_t>((packed>>41&7)+1),depth=static_cast<std::int32_t>((packed>>36&31)+1);
    const auto frames=packed&((std::uint64_t(1)<<36)-1);validateFormat(rate,channels);
    if(depth<4 || depth>32)fail(SourceStatus::UnsupportedCodec);
    if(frames==0)fail(SourceStatus::UnsupportedCodec); // Unknown-duration live streams need a different admission contract.
    boundStreamDuration(frames,rate,limits);
    const auto minimum=input.u16(8,true),maximum=input.u16(10,true);
    if(minimum<16 || maximum<minimum)fail(SourceStatus::MalformedSource);
    SourceMetadata metadata{"native.flac",SourceKind::StandardMedia,rate,channels,{static_cast<std::int64_t>(frames)},SeekSupport::Fast,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,std::nullopt,SampleEncoding::Flac,depth};
    std::size_t cursor=4,count=0;bool comments=false,last=false;
    do {
        if(++count>1024)fail(SourceStatus::ResourceLimit);
        input.need(cursor,4);const auto type=input.u8(cursor)&127;last=(input.u8(cursor)&128)!=0;
        const auto size=(std::size_t(input.u8(cursor+1))<<16)|(std::size_t(input.u8(cursor+2))<<8)|input.u8(cursor+3);cursor+=4;input.need(cursor,size);
        if(type==127 || (type==0 && count!=1) || (count==1 && type!=0))fail(SourceStatus::MalformedSource);
        if(type==4) {
            if(comments)fail(SourceStatus::MalformedSource);
            comments=true;metadata.sourceLoop=sourceCommentLoop(input,cursor,size,metadata.duration);
        }
        cursor+=size;
    }while(!last);
    if(cursor==input.size())fail(SourceStatus::MalformedSource);
    return metadata;
}
}
