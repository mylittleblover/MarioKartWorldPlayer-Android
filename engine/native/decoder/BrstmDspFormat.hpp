#pragma once
#include "decoder/BrstmLayout.hpp"
#include "decoder/NintendoDsp.hpp"

namespace player2::audio {
template<class Bytes> std::array<NintendoDspChannel,MaximumNintendoStreamChannels> parseBrstmDspChannels(Bytes& input,const NintendoStreamLayout& layout) {
    const auto sections=parseBrstmSections(input);const auto head=sections.head,end=head+sections.headSize;
    const auto inside=[&](std::size_t offset,std::size_t count) {
        if(offset<head+8 || offset>end || count>end-offset)fail(SourceStatus::MalformedSource);
    };
    inside(head+24,8);if(input.u16(head+24,true)!=0x0100)fail(SourceStatus::MalformedSource);
    const auto table=input.offset(head+8,input.u32(head+28,true));inside(table,4+8*layout.metadata.channels);
    if(input.u8(table)!=layout.metadata.channels)fail(SourceStatus::MalformedSource);
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> result{};
    for(int ch=0;ch<layout.metadata.channels;++ch) {
        const auto ref=table+4+ch*8;if(input.u16(ref,true)!=0x0100)fail(SourceStatus::MalformedSource);
        const auto channel=input.offset(head+8,input.u32(ref+4,true));inside(channel,8);
        if(input.u16(channel,true)!=0x0100)fail(SourceStatus::MalformedSource);
        const auto context=input.offset(head+8,input.u32(channel+4,true));inside(context,46);
        auto& out=result[static_cast<std::size_t>(ch)];
        for(std::size_t n=0;n<16;++n)out.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(context+n*2,true)));
        if(input.u16(context+32,true)!=0)fail(SourceStatus::UnsupportedCodec); // Nonzero DSP gain is not silently ignored.
        for(std::size_t p:{context+34,context+40}) {
            const auto predictorScale=input.u16(p,true);
            if(predictorScale>255 || (predictorScale>>4)>7)fail(SourceStatus::MalformedSource);
        }
        out.initial={dspSigned(input.u16(context+36,true)),dspSigned(input.u16(context+38,true))};
        out.loop={dspSigned(input.u16(context+42,true)),dspSigned(input.u16(context+44,true))};
    }
    return result;
}
}
