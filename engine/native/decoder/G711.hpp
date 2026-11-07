#pragma once
#include <cstdint>
namespace player2::audio {
// ITU-T G.711 code words to signed PCM16; containers retain their own clocks.
inline std::int32_t g711ALaw(std::uint8_t byte)noexcept {
    const unsigned code=byte^0x55u,segment=(code>>4)&7;auto value=std::int32_t((code&15)<<4);
    if(!segment)value+=8;else value=(value+264)<<(segment-1);
    return (code&128) ? value : -value;
}
inline std::int32_t g711MuLaw(std::uint8_t byte)noexcept {
    const unsigned code=(~byte)&255u;const auto value=std::int32_t(((code&15)*8+132)<<((code>>4)&7));
    return (code&128) ? 132-value : value-132;
}
}
