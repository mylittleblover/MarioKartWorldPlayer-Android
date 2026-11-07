#pragma once
#include "decoder/NintendoDsp.hpp"
#include "decoder/ImaAdpcm.hpp"
#include <algorithm>

namespace player2::audio {
struct NintendoWaveLayout {
    SourceMetadata metadata;
    std::array<std::size_t,2> starts{};
    std::array<NintendoDspChannel,2> channels{};
    std::array<ImaHistory,2> ima{};
    unsigned codec{},width{};bool big{},ndsIma{};
};
// For actual headerless SWAR wave records, not a fabricated standalone SWAV.
std::unique_ptr<AudioFrameSource> makeNintendoWaveSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,NintendoWaveLayout layout);
// Small fixed set: reject partial aliases and nonzero unclaimed extension bytes.
template<class Bytes> class NintendoWaveRegions {
    Bytes& input_;std::size_t begin_,end_,count_{};
    std::array<std::pair<std::size_t,std::size_t>,16> spans_{};
public:
    NintendoWaveRegions(Bytes& input,std::size_t begin,std::size_t size):input_(input),begin_(begin),end_(input.offset(begin,size)) {}
    void add(std::size_t at,std::size_t bytes) {
        if(at<begin_ || at>end_ || bytes>end_-at)fail(SourceStatus::MalformedSource);
        if(count_==spans_.size())fail(SourceStatus::ResourceLimit);
        const auto span=std::make_pair(at,at+bytes);
        for(std::size_t n=0;n<count_;++n)
            if(span.first<spans_[n].second && spans_[n].first<span.second && span!=spans_[n])fail(SourceStatus::MalformedSource);
        spans_[count_++]=span;
    }
    void finish() {
        std::sort(spans_.begin(),spans_.begin()+count_);auto end=begin_;
        for(std::size_t n=0;n<count_;++n) {
            for(auto p=end;p<spans_[n].first;++p)if(input_.u8(p))fail(SourceStatus::UnsupportedCodec);
            end=std::max(end,spans_[n].second);
        }
        for(auto p=end;p<end_;++p)if(input_.u8(p))fail(SourceStatus::UnsupportedCodec);
    }
};
inline SourceMetadata nintendoWaveMetadata(unsigned rate,unsigned channels,std::uint32_t frames,
    unsigned codec,bool loop,std::uint32_t begin,const DecodeLimits& limits) {
    validateFormat(static_cast<std::int32_t>(rate),static_cast<std::int32_t>(channels));
    boundStreamDuration(frames,rate,limits);
    if(loop && begin>=frames)fail(SourceStatus::SourceLoopBounds);
    return {"native.nintendo-wave",SourceKind::GameStream,static_cast<std::int32_t>(rate),static_cast<std::int32_t>(channels),{frames},SeekSupport::Fast,
        KnownDuration|DeterministicRender|Streaming|(loop ? LoopMetadata : 0u),
        loop ? std::optional<SourceLoopMetadata>{{{begin},{frames}}} : std::nullopt,
        codec==3 ? SampleEncoding::ImaAdpcm : codec==2 ? SampleEncoding::DspAdpcm : SampleEncoding::PcmSigned,codec>=2 ? 4 : codec==1 ? 16 : 8};
}
template<class Bytes> NintendoWaveLayout parseNintendoWave(Bytes& input,const DecodeLimits& limits) {
    input.need(0,64);
    const bool ctr=input.magic(0,"CWAV");
    if(!ctr && !input.magic(0,"FWAV"))fail(SourceStatus::UnsupportedFormat);
    const auto bom=input.u16(4,true);
    if(bom!=0xfeff && bom!=0xfffe)fail(SourceStatus::MalformedSource);
    const bool big=bom==0xfeff;const auto version=input.u32(8,big);
    if(ctr ? (version!=0x02000000 && version!=0x02010000 && version!=0x02010100) : version!=0x00010200)
        fail(SourceStatus::UnsupportedCodec);
    if(input.u16(6,big)!=64 || input.u32(12,big)!=input.size() || input.u16(16,big)!=2 || input.u16(18,big))
        fail(SourceStatus::MalformedSource);
    std::size_t info=0,infoSize=0,data=0,dataSize=0;
    NintendoWaveRegions<Bytes> root(input,0,input.size());root.add(0,44);
    for(unsigned n=0;n<2;++n) {
        const auto ref=20+n*12;const auto tag=input.u16(ref,big);
        const auto at=input.u32(ref+4,big),size=input.u32(ref+8,big);
        if(input.u16(ref+2,big) || at<64 || size<8)fail(SourceStatus::MalformedSource);
        root.add(at,size);input.need(at,size);
        if(input.u32(at+4,big)!=size)fail(SourceStatus::MalformedSource);
        if(tag==0x7000 && !info && input.magic(at,"INFO")){info=at;infoSize=size;}
        else if(tag==0x7001 && !data && input.magic(at,"DATA")){data=at;dataSize=size;}
        else fail(SourceStatus::UnsupportedCodec);
    }
    if(!info || !data || infoSize<32)fail(SourceStatus::MalformedSource);
    const auto codec=input.u8(info+8),loop=input.u8(info+9);
    if(codec>3)fail(SourceStatus::UnsupportedCodec);
    if(loop>1 || input.u16(info+10,big))fail(SourceStatus::MalformedSource);
    const auto rate=input.u32(info+12,big),first=input.u32(info+16,big),frames=input.u32(info+20,big);
    const auto table=info+28;const auto count=input.u32(table,big);
    NintendoWaveLayout result;result.metadata=nintendoWaveMetadata(rate,count,frames,codec,loop,first,limits);
    if(!loop && first)fail(SourceStatus::SourceLoopBounds);
    if(input.u32(info+24,big)>first)fail(SourceStatus::SourceLoopBounds); // informational original loop boundary
    result.codec=codec;result.width=codec>=2 ? 0 : codec+1;result.big=big;
    NintendoWaveRegions<Bytes> fields(input,info,infoSize),audio(input,data,dataSize);
    fields.add(info,32+count*8);audio.add(data,8);
    const std::uint64_t stored=codec==3 ? (std::uint64_t(frames)+1)/2 : codec==2 ? ((std::uint64_t(frames)+13)/14)*8 : std::uint64_t(frames)*result.width;
    if(stored>input.size())fail(SourceStatus::MalformedSource);
    for(unsigned ch=0;ch<count;++ch) {
        const auto ref=table+4+ch*8;
        if(input.u16(ref,big)!=0x7100 || input.u16(ref+2,big))fail(SourceStatus::MalformedSource);
        const auto channel=input.offset(table,input.u32(ref+4,big));fields.add(channel,20);
        if(input.u16(channel,big)!=0x1f00 || input.u16(channel+2,big) || input.u16(channel+10,big) || input.u32(channel+16,big))
            fail(SourceStatus::UnsupportedCodec);
        const auto start=input.offset(data+8,input.u32(channel+4,big));audio.add(start,static_cast<std::size_t>(stored));result.starts[ch]=start;
        if(codec==2) {
            if(input.u16(channel+8,big)!=0x0300)fail(SourceStatus::MalformedSource);
            const auto context=input.offset(channel,input.u32(channel+12,big));fields.add(context,44);
            auto& out=result.channels[ch];
            for(unsigned n=0;n<16;++n)out.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(context+n*2,big)));
            const auto ps=input.u16(context+32,big),loopPs=input.u16(context+38,big);
            if(ps>127 || loopPs>127 || ps!=input.u8(start) || (loop && loopPs!=input.u8(start+first/14*8)))fail(SourceStatus::MalformedSource);
            out.initial={dspSigned(input.u16(context+34,big)),dspSigned(input.u16(context+36,big))};
            out.loop={dspSigned(input.u16(context+40,big)),dspSigned(input.u16(context+42,big))};
        } else if(codec==3) {
            if(input.u16(channel+8,big)!=0x0301)fail(SourceStatus::MalformedSource);
            const auto context=input.offset(channel,input.u32(channel+12,big));fields.add(context,8);
            if(input.u8(context+2)>88 || input.u8(context+6)>88)fail(SourceStatus::MalformedSource);
            if(input.u8(context+3) || input.u8(context+7))fail(SourceStatus::UnsupportedCodec);
            result.ima[ch]={dspSigned(input.u16(context,big)),input.u8(context+2)};
            // Loop context remains original metadata; seek/loop reconstruction replays real PCM history.
        } else if(input.u16(channel+8,big) || (input.u32(channel+12,big)!=0xffffffffu && input.u32(channel+12,big)))
            fail(SourceStatus::UnsupportedCodec);
    }
    fields.finish();audio.finish();root.finish();return result;
}
}
