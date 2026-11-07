#pragma once
#include "decoder/NintendoStreamLayout.hpp"
#include <algorithm>

namespace player2::audio {
struct DspHistory {std::int32_t previous{},older{};};
struct NintendoDspChannel {
    std::array<std::int16_t,16> coefficients{};
    DspHistory initial,loop;
};
inline std::int32_t dspSigned(std::uint16_t value)noexcept {return value<32768 ? value : std::int32_t(value)-65536;}
inline std::size_t nintendoDspOffset(const NintendoStreamLayout& layout,std::size_t frame,std::size_t channel)noexcept {
    const auto block=frame/layout.blockFrames;
    const auto stride=block==layout.blocks-1 ? layout.lastPadded : layout.blockBytes;
    return layout.start+block*layout.blockBytes*layout.metadata.channels+channel*stride+(frame%layout.blockFrames)/14*8;
}
inline std::int32_t dspSample(std::uint8_t header,int nibble,const NintendoDspChannel& channel,DspHistory& history) {
    const auto predictor=header>>4;if(predictor>=8)fail(SourceStatus::MalformedSource);
    if(nibble>=8)nibble-=16;
    const std::int64_t sum=std::int64_t(nibble)*(1<<int(header&15))*2048+
        std::int64_t(channel.coefficients[predictor*2])*history.previous+
        std::int64_t(channel.coefficients[predictor*2+1])*history.older+1024;
    // Defined floor division, including negative values; no signed shift or overflow.
    const auto rounded=sum>=0 ? sum/2048 : -((-sum+2047)/2048);
    const auto sample=static_cast<std::int32_t>(std::clamp<std::int64_t>(rounded,-32768,32767));
    history.older=history.previous;history.previous=sample;return sample;
}
}
