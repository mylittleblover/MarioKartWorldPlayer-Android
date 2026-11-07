#pragma once
#include <algorithm>
#include <array>
#include <cstdint>

namespace player2::audio {
struct ImaHistory {std::int32_t sample{},index{};};
// Standard IMA/DVI shift-and-add expansion. Container headers and nibble order
// are deliberately separate; only the explicitly selected NDS profile changes saturation.
inline std::int32_t imaSample(unsigned code,ImaHistory& history,bool nds=false)noexcept {
    static constexpr std::array<int,89> steps{{
        7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,
        157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,
        1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,
        10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767}};
    static constexpr std::array<int,8> indexes{{-1,-1,-1,-1,2,4,6,8}};
    const auto step=steps[static_cast<unsigned>(history.index)];
    auto delta=step/8;
    if(code&1)delta+=step/4;
    if(code&2)delta+=step/2;
    if(code&4)delta+=step;
    const auto sample=history.sample+((code&8) ? -delta : delta);
    // NDS clamps only in the direction of this nibble. A positive zero delta
    // can retain a signed header's -32768; a negative delta clamps to -32767.
    history.sample=nds ? ((code&8) ? std::max(sample,-32767) : std::min(sample,32767)) : std::clamp(sample,-32768,32767);
    history.index=std::clamp(history.index+indexes[code&7],0,88);return history.sample;
}
}
