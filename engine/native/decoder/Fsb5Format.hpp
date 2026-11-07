#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/XboxImaBlock.hpp"
#include "decoder/FsbAdpcmFormat.hpp"
#include <vector>
#include <algorithm>
#include <cstdio>

namespace player2::audio {
struct Fsb5Sample {SourceMetadata metadata;std::size_t header{},start{},end{};unsigned width{};bool floating{},ima{},mpeg{},dsp{},psx{};std::array<NintendoDspChannel,2> dspChannels{};};
struct Fsb5Index {std::vector<Fsb5Sample> samples;std::vector<SourceEntry> entries;bool planar{};};
template<class Bytes> Fsb5Index parseFsb5(Bytes& input,const DecodeLimits& limits) {
    if(!input.magic(0,"FSB5"))fail(SourceStatus::UnsupportedFormat);
    input.need(0,60);
    const auto version=input.u32(4);
    if(version>1 || (input.u32(28)&~2u) || input.u32(32) || (!version && input.u32(36)))fail(SourceStatus::UnsupportedCodec);
    const std::size_t base=version ? 60 : 64;input.need(0,base);
    const auto count=input.u32(8),headers=input.u32(12),names=input.u32(16),data=input.u32(20),codec=input.u32(24);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>(limits.maximumDecodedBytes-16384)/512)fail(SourceStatus::ResourceLimit);
    const std::uint64_t headerEnd=base+std::uint64_t(headers),dataStart=headerEnd+names;
    if(dataStart+data!=input.size() || headers<std::uint64_t(count)*8)fail(SourceStatus::MalformedSource);
    // Mono/stereo codec7 uses Xbox blocks; multichannel FSB IMA remains refused.
    if(codec<1 || (codec>8 && codec!=11))fail(SourceStatus::UnsupportedCodec);
    if((input.u32(28)&2) && codec!=6 && codec!=8)fail(SourceStatus::UnsupportedCodec);
    if(codec==7 && limits.maximumDecodedBytes<16384+XboxImaDecodedBudget+std::uint64_t(count)*512)fail(SourceStatus::ResourceLimit);
    Fsb5Index result;result.planar=input.u32(28)&2;result.samples.reserve(count);result.entries.resize(count);std::size_t cursor=base;
    constexpr unsigned rates[]{4000,8000,11000,11025,16000,22050,24000,32000,44100,48000,96000};
    for(unsigned n=0;n<count;++n) {
        if(cursor>headerEnd || headerEnd-cursor<8)fail(SourceStatus::MalformedSource);
        const auto header=cursor;const auto mode=std::uint64_t(input.u32(cursor))|(std::uint64_t(input.u32(cursor+4))<<32);cursor+=8;
        const auto frames=(mode>>34)&0x3fffffffu,offset=((mode>>7)&0x7ffffffu)*32;
        const auto rateCode=(mode>>1)&15,channelCode=(mode>>5)&3;
        if(rateCode>=11)fail(SourceStatus::UnsupportedSampleRate);
        unsigned rate=rates[rateCode],channels=channelCode<2 ? unsigned(channelCode+1) : channelCode==2 ? 6 : 8;
        std::optional<SourceLoopMetadata> loop;unsigned seen=0,chunks=0;bool more=mode&1;std::size_t dspAt=0,dspSize=0;
        while(more) {
            if(++chunks>64 || chunks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
            if(cursor>headerEnd || headerEnd-cursor<4)fail(SourceStatus::MalformedSource);
            const auto tag=input.u32(cursor);cursor+=4;
            const auto type=tag>>25,size=(tag>>1)&0xffffff;more=tag&1;
            if(size>headerEnd-cursor)fail(SourceStatus::MalformedSource);
            if(type!=1 && type!=2 && type!=3 && type!=4 && !(type==7 && codec==6))fail(SourceStatus::UnsupportedCodec);
            if(type!=4) {if(seen&(1u<<type))fail(SourceStatus::MalformedSource);seen|=1u<<type;}
            if(type==1){if(size!=1)fail(SourceStatus::MalformedSource);channels=input.u8(cursor);}
            if(type==2){if(size!=4)fail(SourceStatus::MalformedSource);rate=input.u32(cursor);}
            if(type==7){dspAt=cursor;dspSize=size;}
            if(type==3) {
                if(size!=8)fail(SourceStatus::UnsupportedCodec);
                const auto first=input.u32(cursor);const std::uint64_t last=std::uint64_t(input.u32(cursor+4))+1;
                if(first>=last || last>frames)fail(SourceStatus::SourceLoopBounds);
                loop=SourceLoopMetadata{{first},{static_cast<std::int64_t>(last)}};
            }
            cursor+=size;
        }
        validateFormat(static_cast<std::int32_t>(rate),channels);boundStreamDuration(frames,rate,limits);
        std::array<NintendoDspChannel,2> dspChannels{};
        if(codec==6){if(!dspAt || dspSize!=46*channels)fail(SourceStatus::MalformedSource);dspChannels=parseFsbDspChannels(input,dspAt,channels);}
        if(offset>=data || (n==0 && offset!=0))fail(SourceStatus::MalformedSource);
        if(n && result.samples.back().start>=dataStart+offset)fail(SourceStatus::MalformedSource);
        const unsigned width=codec<=4 ? codec : 4;
        SourceMetadata metadata{"native.fmod-fsb5",SourceKind::GameBank,static_cast<std::int32_t>(rate),static_cast<std::int32_t>(channels),
            {static_cast<std::int64_t>(frames)},SeekSupport::Fast,ContainerEntry|KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,
            codec==11 ? SampleEncoding::MpegLayer3 : codec==6 ? SampleEncoding::DspAdpcm : codec==8 ? SampleEncoding::PsAdpcm : codec==7 ? SampleEncoding::ImaAdpcm : codec==1 ? SampleEncoding::PcmUnsigned : codec==5 ? SampleEncoding::IeeeFloat : SampleEncoding::PcmSigned,static_cast<std::int32_t>(codec==11 ? 0 : codec>=6 ? 4 : width*8)};
        result.samples.push_back({metadata,header,static_cast<std::size_t>(dataStart+offset),0,width,codec==5,codec==7,codec==11,codec==6,codec==8,dspChannels});
        std::snprintf(result.entries[n].id.data(),result.entries[n].id.size(),"fsb5:header:%zu",header);
    }
    if(cursor!=headerEnd)fail(SourceStatus::UnsupportedCodec);
    if(names) {
        if(names<std::uint64_t(count)*4)fail(SourceStatus::MalformedSource);
        for(unsigned n=0;n<count;++n) {
            const auto relative=input.u32(static_cast<std::size_t>(headerEnd)+n*4);
            if(relative<std::uint64_t(count)*4 || relative>=names)fail(SourceStatus::MalformedSource);
            const auto remaining=std::min<std::uint64_t>(names-relative,8192);std::size_t size=0;
            while(size<remaining && input.u8(static_cast<std::size_t>(headerEnd)+relative+size))++size;
            if(size==remaining)fail(SourceStatus::MalformedSource);
        }
    }
    for(unsigned n=0;n<count;++n) {
        auto& sample=result.samples[n];sample.end=n+1<count ? result.samples[n+1].start : input.size();
        if(sample.mpeg){if(sample.end-sample.start<4)fail(SourceStatus::MalformedSource);continue;} // Selected child validates actual MPEG frames.
        if(sample.dsp || sample.psx) {
            const auto frames=sample.dsp ? 14u : 28u,bytes=sample.dsp ? 8u : 16u;
            const auto needed=(static_cast<std::uint64_t>(sample.metadata.duration.value)+frames-1)/frames*bytes*sample.metadata.channels;
            if(needed>sample.end-sample.start)fail(SourceStatus::MalformedSource);
            continue; // Selected child validates interleave/planar padding and codec history.
        }
        const auto encoded=sample.ima ? xboxImaBytes(sample.metadata.duration.value,sample.metadata.channels) :
            std::uint64_t(sample.metadata.duration.value)*sample.metadata.channels*sample.width;
        if(encoded>sample.end-sample.start)fail(SourceStatus::MalformedSource);
        if(sample.ima)validateXboxIma(input,sample.start,static_cast<std::size_t>(encoded),sample.metadata.duration.value,sample.metadata.channels);
        if(sample.end-sample.start-encoded>=32)fail(SourceStatus::UnsupportedCodec);
        for(auto p=sample.start+static_cast<std::size_t>(encoded);p<sample.end;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    }
    return result;
}
}
