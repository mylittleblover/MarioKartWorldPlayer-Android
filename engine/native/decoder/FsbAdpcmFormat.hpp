#pragma once
#include "decoder/NintendoDsp.hpp"

namespace player2::audio {
template<class Bytes> std::array<NintendoDspChannel,2> parseFsbDspChannels(Bytes& bytes,std::size_t at,unsigned channels) {
    if(channels<1 || channels>2)fail(SourceStatus::UnsupportedChannels);
    bytes.need(at,46*channels);std::array<NintendoDspChannel,2> result{};
    for(unsigned ch=0;ch<channels;++ch) {
        const auto context=at+46*ch;auto& channel=result[ch];
        for(unsigned n=0;n<16;++n)channel.coefficients[n]=static_cast<std::int16_t>(dspSigned(bytes.u16(context+n*2,true)));
        if(bytes.u16(context+32,true))fail(SourceStatus::UnsupportedCodec);
        for(auto offset:{34u,40u}) {
            const auto header=bytes.u16(context+offset,true);
            if(header>127)fail(SourceStatus::MalformedSource);
        }
        channel.initial={dspSigned(bytes.u16(context+36,true)),dspSigned(bytes.u16(context+38,true))};
        channel.loop={dspSigned(bytes.u16(context+42,true)),dspSigned(bytes.u16(context+44,true))};
    }
    return result;
}
std::unique_ptr<AudioFrameSource> openFsbAdpcm(std::unique_ptr<EncodedSourceInput> input,
    std::size_t start,std::size_t end,const SourceMetadata& metadata,bool planar,bool outerAlignment,
    const std::array<NintendoDspChannel,2>& channels,const DecodeLimits& limits);
}
