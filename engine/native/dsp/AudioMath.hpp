#pragma once
#include <cmath>
#include <cstdint>
#include <limits>
namespace player2 {
inline void countAudioCorrections(std::uint64_t& counter,std::uint64_t count=1) noexcept {
    constexpr auto limit=static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max());
    counter=count>limit-counter ? limit : counter+count;
}
// Internal float representability guard, not the user's limiter/device clipper.
// Preserve every finite representable sample; expose every correction in diagnostics.
inline float audioSample(double value,std::uint64_t& counter) noexcept {
    constexpr double maximum=std::numeric_limits<float>::max();
    if(std::isfinite(value) && value>=-maximum && value<=maximum)return static_cast<float>(value);
    countAudioCorrections(counter);
    if(std::isnan(value))return 0;
    return value<0 ? -std::numeric_limits<float>::max() : std::numeric_limits<float>::max();
}
}
