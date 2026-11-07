#pragma once
#include "decoder/VgmCapabilities.hpp"
#include "decoder/VgmTags.hpp"

namespace player2::audio {
// Validate the entire accepted command stream before passing it to GME. Its
// permissive unknown-command skipping must never decide Source admission.
template<class Input> SourceMetadata parseVgmFm(Input& input,const DecodeLimits& limits) {
    if(!input.magic(0,"Vgm "))fail(SourceStatus::UnsupportedFormat);
    input.need(0,64);
    if(std::uint64_t(input.u32(4))+4!=input.size())fail(SourceStatus::MalformedSource);
    const auto version=input.u32(8);
    if(version<0x150 || version>0x171)fail(SourceStatus::UnsupportedCodec);
    const auto begin=input.u32(0x34) ? input.offset(0x34,input.u32(0x34)) : 64;
    if(begin<64)fail(SourceStatus::MalformedSource);
    validateVgmChipCapabilities(input,begin,0x07);
    const auto psg=input.u32(0x0c),opll=input.u32(0x10),opn2=input.u32(0x2c);
    if((!opll && !opn2) || (opll && opn2))fail(SourceStatus::UnsupportedCodec);
    // Clock flags select dual/variant silicon, not a frequency. GME currently
    // chooses one FM family; never silently discard an additional declaration.
    for(const auto clock:{psg,opll,opn2})
        if(clock && (clock<100000 || clock>100000000))fail(SourceStatus::UnsupportedCodec);
    if((input.u16(0x28)!=0 && input.u16(0x28)!=9) ||
        (input.u8(0x2a)!=0 && input.u8(0x2a)!=16) || input.u8(0x2b)!=0)fail(SourceStatus::UnsupportedCodec);
    if(begin>0x7e && (input.u8(0x7d) || input.u8(0x7e)))fail(SourceStatus::UnsupportedCodec);
    const auto end=vgmCommandEnd(input,begin);
    const auto loop=input.u32(0x1c) ? input.offset(0x1c,input.u32(0x1c)) : 0;
    if(loop && (loop<begin || loop>=end))fail(SourceStatus::SourceLoopBounds);
    SourceSamplePosition at,loopAt{-1};std::size_t commands=0;bool ended=false;
    std::size_t bankBytes=0,pcmCursor=0;bool pcmSeek=false,loopSeek=false;
    for(std::size_t p=begin;p<end;) {
        if(commands++>=limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        if(p==loop){loopAt=at;loopSeek=false;}
        const auto origin=p;
        const auto op=input.u8(p++);std::uint32_t wait=0;
        const auto need=[&](std::size_t count){if(count>end-p)fail(SourceStatus::MalformedSource);input.need(p,count);};
        if(op==0x4f || op==0x50) {
            if(!psg)fail(SourceStatus::UnsupportedCodec);
            need(1);++p;
        } else if(op==0x51 || op==0x52 || op==0x53) {
            if(op==0x51 ? !opll : !opn2)fail(SourceStatus::UnsupportedCodec);
            need(2);p+=2;
        } else if(op==0x67) {
            need(6);
            if(input.u8(p)!=0x66)fail(SourceStatus::MalformedSource);
            if(!opn2 || input.u8(p+1)!=0 || bankBytes || (loop && origin>=loop))fail(SourceStatus::UnsupportedCodec);
            const auto length=input.u32(p+2);p+=6;
            if(length&0x80000000u)fail(SourceStatus::UnsupportedCodec);
            if(!length)fail(SourceStatus::MalformedSource);
            need(length);p+=length;bankBytes=length;
        } else if(op==0xe0) {
            need(4);
            if(!opn2 || !bankBytes)fail(SourceStatus::UnsupportedCodec);
            pcmCursor=input.u32(p);p+=4;
            if(pcmCursor>bankBytes)fail(SourceStatus::MalformedSource);
            pcmSeek=true;if(loop && origin>=loop)loopSeek=true;
        } else if(op>=0x80 && op<=0x8f) {
            if(!opn2 || !bankBytes || !pcmSeek)fail(SourceStatus::UnsupportedCodec);
            if(pcmCursor>=bankBytes)fail(SourceStatus::MalformedSource);
            // Every internal kernel loop must reconstruct its PCM cursor before
            // reading. Otherwise lookahead/repeat can walk beyond the bank.
            if(loop && origin>=loop && !loopSeek)fail(SourceStatus::SourceLoopBounds);
            ++pcmCursor;wait=op&15;
        } else if(op==0x61){need(2);wait=input.u16(p);p+=2;}
        else if(op==0x62)wait=735;
        else if(op==0x63)wait=882;
        else if(op>=0x70 && op<=0x7f)wait=(op&15)+1;
        else if(op==0x66){ended=true;if(p!=end)fail(SourceStatus::MalformedSource);break;}
        else fail(SourceStatus::UnsupportedCodec); // Other banks/chips/stream-control commands are not silently skipped.
        if(limits.maximumGeneratedFrames<=0 || wait>limits.maximumGeneratedFrames-at.value)fail(SourceStatus::ResourceLimit);
        at.value+=wait;
    }
    if(!ended || at.value<=0 || (input.u32(0x18) && input.u32(0x18)!=at.value))fail(SourceStatus::MalformedSource);
    boundStreamDuration(at.value,44100,limits);
    SourceMetadata metadata{"native.vgm-fm",SourceKind::ChipCommand,44100,2,at,SeekSupport::Replay,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,std::nullopt};
    if(loop) {
        if(loopAt.value<0 || loopAt.value>=at.value ||
            (input.u32(0x20) && input.u32(0x20)!=at.value-loopAt.value))fail(SourceStatus::SourceLoopBounds);
        metadata.sourceLoop=SourceLoopMetadata{loopAt,at};
    } else if(input.u32(0x20))fail(SourceStatus::SourceLoopBounds);
    return metadata;
}
}
