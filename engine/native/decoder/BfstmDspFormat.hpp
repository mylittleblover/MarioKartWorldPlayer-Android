#pragma once
#include "decoder/BfstmLayout.hpp"
#include "decoder/NintendoDsp.hpp"

namespace player2::audio {
template<class Bytes> std::array<NintendoDspChannel,MaximumNintendoStreamChannels> parseNintendoDspChannels(Bytes& bytes,const NintendoStreamLayout& layout) {
    const bool big=layout.big;
    std::size_t info=0,size=0;
    for(std::size_t n=0;n<bytes.u16(16,big);++n)if(bytes.u16(20+n*12,big)==0x4000) {
        info=bytes.u32(24+n*12,big);size=bytes.u32(28+n*12,big);break;
    }
    const auto inside=[&](std::size_t offset,std::size_t count) {
        if(offset<info+8 || offset>info+size || count>info+size-offset)fail(SourceStatus::MalformedSource);
    };
    inside(info+24,8);
    if(bytes.u16(info+24,big)!=0x0101)fail(SourceStatus::MalformedSource);
    const auto table=bytes.offset(info+8,bytes.u32(info+28,big));inside(table,4);
    if(bytes.u32(table,big)!=static_cast<std::uint32_t>(layout.metadata.channels))fail(SourceStatus::MalformedSource);
    inside(table,4+8*layout.metadata.channels);
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> result{};
    for(int ch=0;ch<layout.metadata.channels;++ch) {
        const auto ref=table+4+8*ch;
        if(bytes.u16(ref,big)!=0x4102)fail(SourceStatus::MalformedSource);
        const auto channel=bytes.offset(table,bytes.u32(ref+4,big));inside(channel,8);
        if(bytes.u16(channel,big)!=0x0300)fail(SourceStatus::MalformedSource);
        const auto context=bytes.offset(channel,bytes.u32(channel+4,big));inside(context,44);
        auto& out=result[static_cast<std::size_t>(ch)];
        for(std::size_t n=0;n<16;++n)out.coefficients[n]=static_cast<std::int16_t>(dspSigned(bytes.u16(context+n*2,big)));
        // BFSTM has coefficients followed by two six-byte contexts. BRSTM's gain is absent.
        for(std::size_t p:{context+32,context+38}) {
            const auto predictorScale=bytes.u16(p,big);
            if(predictorScale>255 || (predictorScale>>4)>7)fail(SourceStatus::MalformedSource);
        }
        out.initial={dspSigned(bytes.u16(context+34,big)),dspSigned(bytes.u16(context+36,big))};
        out.loop={dspSigned(bytes.u16(context+40,big)),dspSigned(bytes.u16(context+42,big))};
    }
    return result;
}
}
