#pragma once
#include "compiled/MixerGraph.hpp"
namespace player2 {
struct EffectParameterDescriptor { double min,max,defaultValue; };
inline bool effectParameterSupported(int kind,int property) noexcept {
    switch(kind) {
        case 0:case 1:return property==0 || property==1;
        case 2:return property>=0 && property<=2;
        case 3:return property>=3 && property<=6;
        case 4:return property==3 || property==5 || property==6;
        case 5:return property>=7 && property<=9;
        case 6:return property==0 || (property>=7 && property<=9);
        default:return false;
    }
}
inline bool effectModifierSupported(int property,Modifier policy) noexcept {
    return policy==Modifier::Absolute || ((property==2 || property==3 || property==9) && (policy==Modifier::Sum || policy==Modifier::Highest));
}
inline const EffectParameterDescriptor* effectParameterDescriptor(int id) noexcept {
    static constexpr std::array<EffectParameterDescriptor,10> values{{
        {10,24000,1000},{0.1,20,0.707},{-60,60,0},{-96,0,-6},{1,1000,4},
        {0.01,10000,10},{0.01,10000,100},{0.01,2000,250},{0,0.99,0.25},{0,1,0.25}
    }};
    return id>=0 && id<static_cast<int>(values.size()) ? &values[id] : nullptr;
}
inline bool validEffectParameters(const Effect& effect) noexcept {
    if(effect.kind<0 || effect.kind>6) return false;
    for(int id=0;id<10;++id) { const auto* d=effectParameterDescriptor(id); const auto value=effect.parameters[id];
        if(!std::isfinite(value) || value<d->min || value>d->max) return false;
    }
    return true;
}
}
