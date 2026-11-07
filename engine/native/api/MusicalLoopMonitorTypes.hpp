#pragma once
#include "api/EngineTypes.hpp"
namespace player2::api {
using MusicalLoopMonitorPacket=std::array<std::int64_t,10>;
inline MusicalLoopMonitorPacket musicalLoopPacket(const MusicalLoopSnapshot& s)noexcept {
    return {1,s.playlist,s.segment,s.item,static_cast<int>(s.phase),s.active,s.paused,s.requestedAt.value,s.at.value,static_cast<int>(s.result)};
}
}
