#pragma once
#include "decoder/NintendoWaveFormat.hpp"

namespace player2::audio {
// RWAV stores DSP counts in nibbles; PCM counts already name Source frames.
inline std::uint32_t rwavFrames(std::uint32_t nibbles) {
    if(nibbles%16==1)fail(SourceStatus::MalformedSource);
    return nibbles/16*14+(nibbles%16>2 ? nibbles%16-2 : 0);
}
template<class Bytes> NintendoWaveLayout parseRwav(Bytes& input,const DecodeLimits& limits) {
    input.need(0,32);
    if(!input.magic(0,"RWAV"))fail(SourceStatus::UnsupportedFormat);
    const auto bom=input.u16(4,true);
    if(bom!=0xfeff && bom!=0xfffe)fail(SourceStatus::MalformedSource);
    const bool big=bom==0xfeff;const auto version=input.u16(6,big);
    if(version!=0x102 && version!=0x103)fail(SourceStatus::UnsupportedCodec);
    if(input.u32(8,big)!=input.size() || input.u16(12,big)!=32 || input.u16(14,big)!=2)fail(SourceStatus::MalformedSource);
    const auto info=input.u32(16,big),infoSize=input.u32(20,big),data=input.u32(24,big),dataSize=input.u32(28,big);
    if(info<32 || data<32 || infoSize<36 || dataSize<8)fail(SourceStatus::MalformedSource);
    NintendoWaveRegions<Bytes> root(input,0,input.size());root.add(0,32);root.add(info,infoSize);root.add(data,dataSize);
    if(!input.magic(info,"INFO") || !input.magic(data,"DATA") || input.u32(info+4,big)!=infoSize || input.u32(data+4,big)!=dataSize)
        fail(SourceStatus::MalformedSource);
    const auto base=info+8;const auto codec=input.u8(base),loop=input.u8(base+1),count=input.u8(base+2);
    if(codec>2)fail(SourceStatus::UnsupportedCodec);
    if(loop>1)fail(SourceStatus::MalformedSource);
    // Address-based data, 24-bit rates and nonzero extensions need separate admission.
    if(input.u8(base+3) || input.u16(base+6,big) || input.u32(base+24,big))fail(SourceStatus::UnsupportedCodec);
    const auto location=input.u32(base+20,big);
    if(location && location!=data+8)fail(SourceStatus::UnsupportedCodec);
    const auto rawFirst=input.u32(base+8,big),rawFrames=input.u32(base+12,big);
    if(codec==2 && loop && rawFirst && rawFirst%16<2)fail(SourceStatus::SourceLoopBounds);
    const auto first=codec==2 ? rwavFrames(rawFirst) : rawFirst,frames=codec==2 ? rwavFrames(rawFrames) : rawFrames;
    NintendoWaveLayout result;result.metadata=nintendoWaveMetadata(input.u16(base+4,big),count,frames,codec,loop,first,limits);
    if(!loop && rawFirst)fail(SourceStatus::SourceLoopBounds);
    result.codec=codec;result.width=codec==2 ? 0 : codec+1;result.big=big;
    NintendoWaveRegions<Bytes> fields(input,info,infoSize),audio(input,data,dataSize);fields.add(info,36);audio.add(data,8);
    const auto table=input.offset(base,input.u32(base+16,big));fields.add(table,count*4);
    const std::uint64_t stored=codec==2 ? ((std::uint64_t(frames)+13)/14)*8 : std::uint64_t(frames)*result.width;
    if(stored>input.size())fail(SourceStatus::MalformedSource);
    for(unsigned ch=0;ch<count;++ch) {
        const auto channel=input.offset(base,input.u32(table+ch*4,big));fields.add(channel,28);
        const auto start=input.offset(data+8,input.u32(channel,big));audio.add(start,static_cast<std::size_t>(stored));result.starts[ch]=start;
        // These are original channel routing hints, not an authored Mixer gain.
        for(unsigned n=0;n<4;++n) {
            const auto routing=input.u32(channel+8+n*4,big);
            if(routing!=0 && routing!=1 && routing!=0x01000000)fail(SourceStatus::UnsupportedCodec);
        }
        if(input.u32(channel+24,big))fail(SourceStatus::UnsupportedCodec);
        const auto relative=input.u32(channel+4,big);
        if(codec==2) {
            const auto context=input.offset(base,relative);fields.add(context,48);auto& out=result.channels[ch];
            for(unsigned n=0;n<16;++n)out.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(context+n*2,big)));
            // Revolution DSP context includes gain before the predictor/history fields.
            if(input.u16(context+32,big) || input.u16(context+46,big))fail(SourceStatus::UnsupportedCodec);
            const auto ps=input.u16(context+34,big),loopPs=input.u16(context+40,big);
            if(ps>127 || loopPs>127 || ps!=input.u8(start) || (loop && loopPs!=input.u8(start+first/14*8)))fail(SourceStatus::MalformedSource);
            out.initial={dspSigned(input.u16(context+36,big)),dspSigned(input.u16(context+38,big))};
            out.loop={dspSigned(input.u16(context+42,big)),dspSigned(input.u16(context+44,big))};
        }else if(relative && relative!=0xffffffffu)fail(SourceStatus::UnsupportedCodec);
    }
    fields.finish();audio.finish();root.finish();return result;
}
}
