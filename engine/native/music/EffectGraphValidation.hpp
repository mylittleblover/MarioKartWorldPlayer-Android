#pragma once
#include "compiled/Graph.hpp"
#include "compiled/GraphProtocolIds.hpp"
#include "dsp/ParameterDescriptors.hpp"

namespace player2 {
inline bool validEffectCurves(const Graph& g) noexcept {
    if(!g.effectCurves.empty() && g.version<protocol::EffectRtpcVersion)return false;
    for(const auto& r:g.effectCurves) {
        const auto* bus=find(g.buses,r.bus);const auto* effect=bus ? find(bus->effects,r.effect) : nullptr;
        const auto* parameter=find(g.parameters,r.parameter);const auto* descriptor=effectParameterDescriptor(r.property);
        if(!effect || !parameter || !descriptor || !effectParameterSupported(effect->kind,r.property) ||
            !effectModifierSupported(r.property,r.policy) || r.points.size()<2)return false;
        for(std::size_t n=0;n<r.points.size();++n) {
            const auto& p=r.points[n];
            if(!std::isfinite(p.x) || !std::isfinite(p.y) || p.x<parameter->min || p.x>parameter->max ||
                p.y<descriptor->min || p.y>descriptor->max || (n && p.x<=r.points[n-1].x) ||
                !protocol::enumFromWire<Shape>(static_cast<int>(p.shape)))return false;
        }
        for(const auto& other:g.effectCurves)if(other.index!=r.index && other.effect==r.effect && other.property==r.property &&
            (other.policy!=r.policy || r.policy==Modifier::Absolute))return false;
    }
    return true;
}
}
