#pragma once
#include "api/EngineTypes.hpp"
#include <cstring>
namespace player2::api {
struct EffectSnapshot {
    int index{-1},bus{-1},kind{},order{},bindings{};
    bool bypass{},busBypass{},tail{};audio::TransportFrame at;
    std::array<double,10> base{},rtpc{},effective{};
};
// v1: version, valid, effect, bus, kind, order, bypass, busBypass, tail,
// Transport frame, binding count, then 10 base/10 RTPC/10 effective double bits.
using EffectMonitorPacket=std::array<std::int64_t,41>;
inline EffectMonitorPacket effectPacket(const EffectSnapshot& s)noexcept {
    EffectMonitorPacket p{1,s.index>=0,s.index,s.bus,s.kind,s.order,s.bypass,s.busBypass,s.tail,s.at.value,s.bindings};
    for(int n=0;n<10;++n) {std::memcpy(&p[11+n],&s.base[n],8);std::memcpy(&p[21+n],&s.rtpc[n],8);std::memcpy(&p[31+n],&s.effective[n],8);}
    return p;
}
}
