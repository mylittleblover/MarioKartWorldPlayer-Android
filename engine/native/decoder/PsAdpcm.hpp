#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <array>
#include <cmath>

namespace player2::audio {
struct PsAdpcmHistory {std::int32_t recent{},previous{};};
// Standard PS-ADPCM float/q8 profile as used by Sony PC tools/vgmstream.
// Clipping applies to emitted PCM, not predictor histories. Explicit float32
// stores prevent ARM fused multiply-add from changing this selected profile.
inline std::array<float,28> decodePsAdpcm(const std::array<std::uint8_t,16>& block,PsAdpcmHistory& state) {
    constexpr int coefficients[5][2]{{0,0},{60,0},{115,-52},{98,-55},{122,-60}};
    const auto filter=block[0]>>4,shift=block[0]&15;
    if(filter>4 || shift>12 || block[1]>7)fail(SourceStatus::UnsupportedCodec);
    auto history=state;std::array<float,28> pcm{};
    for(unsigned n=0;n<28;++n) {
        std::int64_t sample=0;
        if(block[1]<7) {
            const auto nibble=(block[2+n/2]>>(n%2*4))&15;
            const auto signedNibble=nibble<8 ? int(nibble) : int(nibble)-16;
            const volatile float a=(coefficients[filter][0]/64.0f)*history.recent;
            const volatile float b=(coefficients[filter][1]/64.0f)*history.previous;
            const volatile float prediction=(a+b)*256.0f;
            if(!std::isfinite(prediction) || double(prediction)<INT32_MIN || double(prediction)>INT32_MAX)fail(SourceStatus::UnsupportedCodec);
            const auto q8=std::int64_t(signedNibble)*(std::int64_t{1}<<(20-shift))+static_cast<std::int32_t>(prediction);
            sample=q8>=0 ? q8/256 : -((-q8+255)/256); // Defined arithmetic shift, including negative values.
        }
        if(sample<INT32_MIN || sample>INT32_MAX)fail(SourceStatus::UnsupportedCodec);
        pcm[n]=static_cast<float>(std::clamp<std::int64_t>(sample,-32768,32767))/32768.0f;
        history.previous=history.recent;history.recent=static_cast<std::int32_t>(sample);
    }
    state=history;return pcm; // Atomic block publication after all validation.
}
}
