#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/XboxImaBlock.hpp"
#include <cstdio>
#include <vector>

namespace player2::audio {
struct LegacyFsbSample {
    std::size_t header{},start{},size{};std::uint32_t frames{},mode{},rate{},loopStart{},loopEnd{};
    std::uint16_t channels{},headerSize{};
};
struct LegacyFsbIndex {std::vector<LegacyFsbSample> samples;std::vector<SourceEntry> entries;bool big{},planar{};unsigned minimumHeader{},mpegPadding{};};
template<class Bytes> LegacyFsbIndex parseLegacyFsb(Bytes& input,const DecodeLimits& limits) {
    const bool v1=input.magic(0,"FSB1"),v2=input.magic(0,"FSB2"),v3=input.magic(0,"FSB3"),v4=input.magic(0,"FSB4");
    if(!v1 && !v2 && !v3 && !v4)fail(SourceStatus::UnsupportedFormat);
    const unsigned base=v1 || v2 ? 16 : v3 ? 24 : 48;input.need(0,base);
    const auto version=v1 || v2 ? 0u : input.u32(16),flags=v1 || v2 ? 0u : input.u32(20);
    if((v3 && version!=0x30000 && version!=0x30001) || (v4 && version!=0x40000) || (flags&~0x79u))
        fail(SourceStatus::UnsupportedCodec); // No basic headers, encryption or special interleave/padding.
    const unsigned minimum=v4 || version==0x30001 ? 80 : 64;
    const auto count=input.u32(4),headers=v1 ? 64u : input.u32(8),data=v1 ? input.u32(8) : input.u32(12);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>(limits.maximumDecodedBytes-16384)/512)
        fail(SourceStatus::ResourceLimit);
    if(v1 && (count!=1 || input.u32(12)))fail(SourceStatus::UnsupportedCodec);
    const auto headerEnd=std::uint64_t(base)+headers;
    if(headerEnd+data!=input.size() || headers<std::uint64_t(count)*minimum)fail(SourceStatus::MalformedSource);
    LegacyFsbIndex result;result.big=flags&8;result.minimumHeader=minimum;result.samples.reserve(count);result.entries.resize(count);
    result.mpegPadding=(flags&0x40) ? 4 : (flags&0x20) ? 2 : 0;
    result.planar=flags&16;
    std::size_t cursor=base,encoded=static_cast<std::size_t>(headerEnd);
    for(unsigned n=0;n<count;++n) {
        if(cursor>headerEnd || minimum>headerEnd-cursor)fail(SourceStatus::MalformedSource);
        // FSB1 has a fixed 32-byte name and no per-sample size prefix.
        const auto size=v1 ? 64u : input.u16(cursor);if(size<minimum || size>headerEnd-cursor)fail(SourceStatus::MalformedSource);
        const auto frames=input.u32(cursor+32),bytes=input.u32(cursor+36),mode=input.u32(cursor+(v1 ? 52 : 48));
        if(!frames || !bytes || bytes>input.size()-encoded)fail(SourceStatus::MalformedSource);
        if(mode&0x8000)fail(SourceStatus::UnsupportedCodec); // Duplicates change subsequent stream addressing.
        const auto channels=v1 ? std::uint16_t((mode&0x40u) ? 2 : 1) : input.u16(cursor+62);
        result.samples.push_back({cursor,encoded,bytes,frames,mode,input.u32(cursor+(v1 ? 40 : 52)),
            input.u32(cursor+(v1 ? 56 : 40)),input.u32(cursor+(v1 ? 60 : 44)),channels,static_cast<std::uint16_t>(size)});
        std::snprintf(result.entries[n].id.data(),result.entries[n].id.size(),"fsb:header:%zu",cursor);
        cursor+=size;encoded+=bytes;
    }
    if(cursor!=headerEnd || encoded!=input.size())fail(SourceStatus::MalformedSource);
    return result;
}
inline SourceMetadata legacyFsbMetadata(const LegacyFsbIndex& index,const LegacyFsbSample& sample,const DecodeLimits& limits) {
    const bool ima=sample.mode&0x400000u;
    const bool mpeg=sample.mode&0x200u;
    const bool psx=sample.mode&0x800000u,dsp=sample.mode&0x2000000u;
    if(unsigned(ima)+unsigned(mpeg)+unsigned(psx)+unsigned(dsp)>1)fail(SourceStatus::UnsupportedCodec);
    const auto allowed=dsp ? 0x020001ffu : psx ? 0x008001ffu : mpeg ? 0x500203ffu : ima ? 0x204001ffu : 0x1ffu;
    if(sample.headerSize!=index.minimumHeader+(dsp ? 46*sample.channels : 0) || (sample.mode&~allowed) || ((sample.mode&7)!=1u && (sample.mode&7)!=2u))
        fail(SourceStatus::UnsupportedCodec);
    const auto depth=sample.mode&0x18u,sign=sample.mode&0x180u,channels=sample.mode&0x60u;
    if(mpeg && (depth!=16 || sign!=0x100))fail(SourceStatus::UnsupportedCodec);
    if((psx || dsp) && (depth!=16 || sign!=0x100))fail(SourceStatus::UnsupportedCodec);
    if(!mpeg && index.mpegPadding)fail(SourceStatus::UnsupportedCodec);
    if(index.planar && !psx && !dsp)fail(SourceStatus::UnsupportedCodec);
    if(ima && (depth!=16 || sign!=0x100 || ((sample.mode&0x20000000u) && sample.channels!=2)))fail(SourceStatus::UnsupportedCodec);
    if((depth!=8 && depth!=16) || (sign!=0x80 && sign!=0x100) || (depth==16 && sign!=0x100))fail(SourceStatus::UnsupportedCodec);
    validateFormat(static_cast<std::int32_t>(sample.rate),sample.channels);
    if(channels!=(sample.channels==1 ? 0x20u : 0x40u))fail(SourceStatus::MalformedSource);
    const unsigned width=depth==8 ? 1 : 2;
    if(ima && limits.maximumDecodedBytes<16384+XboxImaDecodedBudget+std::uint64_t(index.entries.size())*512)fail(SourceStatus::ResourceLimit);
    const auto adpcmBytes=(std::uint64_t(sample.frames)+(dsp ? 13 : 27))/(dsp ? 14 : 28)*(dsp ? 8 : 16)*sample.channels;
    if(!mpeg && ((psx || dsp) ? adpcmBytes : ima ? xboxImaBytes(sample.frames,sample.channels) : std::uint64_t(sample.frames)*sample.channels*width)!=sample.size)fail(SourceStatus::MalformedSource);
    boundStreamDuration(sample.frames,sample.rate,limits);
    std::optional<SourceLoopMetadata> loop;
    if((sample.mode&7)==2) {
        const auto end=std::uint64_t(sample.loopEnd)+1;
        if(sample.loopStart>=end || end>sample.frames)fail(SourceStatus::SourceLoopBounds);
        loop=SourceLoopMetadata{{sample.loopStart},{static_cast<std::int64_t>(end)}};
    }
    return {"native.fmod-fsb",SourceKind::GameBank,static_cast<std::int32_t>(sample.rate),sample.channels,{sample.frames},SeekSupport::Fast,
        ContainerEntry|KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,
        mpeg ? SampleEncoding::MpegLayer3 : dsp ? SampleEncoding::DspAdpcm : psx ? SampleEncoding::PsAdpcm : ima ? SampleEncoding::ImaAdpcm : sign==0x80 ? SampleEncoding::PcmUnsigned : SampleEncoding::PcmSigned,static_cast<std::int32_t>(mpeg ? 0 : (ima || psx || dsp) ? 4 : width*8)};
}
}
