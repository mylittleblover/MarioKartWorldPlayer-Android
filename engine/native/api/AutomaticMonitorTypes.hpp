#pragma once
#include "api/EngineTypes.hpp"
#include <cstring>
namespace player2::api {
using AutomaticMonitorPacket=std::array<std::int64_t,21>;
inline AutomaticMonitorPacket automaticPacket(const AutomaticSnapshot& s)noexcept {
    AutomaticMonitorPacket p{1,s.parameter,static_cast<int>(s.phase),s.active,s.paused,s.requestedAt.value,s.at.value,s.cycles,0,0,0,0,0,0,0,0,0,s.clockSegment,s.returnStart.value,s.returnEnd.value,static_cast<int>(s.result)};
    std::memcpy(&p[8],&s.random,8);const double values[8]{s.start,s.goal,s.progress,s.cycleBeats,s.low,s.high,s.current,s.target};
    for(int n=0;n<8;++n)std::memcpy(&p[9+n],&values[n],8);
    return p;
}
}
