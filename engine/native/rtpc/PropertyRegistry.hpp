#pragma once
#include "compiled/GraphTypes.hpp"
namespace player2 {
// Runtime property descriptors mirror the Authoring registry, with stable v1 IDs.
constexpr std::uint32_t kindMask(Kind kind) noexcept { return std::uint32_t{1}<<static_cast<int>(kind); }
constexpr std::uint32_t modifierMask(Modifier modifier) noexcept { return std::uint32_t{1}<<static_cast<int>(modifier); }
struct PropertyDescriptor {
    double min,max,defaultValue; Modifier defaultPolicy; std::uint32_t kinds,policies;
    bool supports(Kind kind,Modifier policy) const noexcept {
        const auto k=static_cast<int>(kind),p=static_cast<int>(policy);
        return k>=0 && k<=13 && p>=0 && p<=3 && (kinds&kindMask(kind)) && (policies&modifierMask(policy));
    }
};
inline const PropertyDescriptor* propertyDescriptor(int id) noexcept {
    constexpr auto music=kindMask(Kind::Segment)|kindMask(Kind::Playlist)|kindMask(Kind::Switch)|kindMask(Kind::Track)|kindMask(Kind::Bus);
    constexpr auto absolute=modifierMask(Modifier::Absolute),sum=modifierMask(Modifier::Sum),highest=modifierMask(Modifier::Highest);
    static constexpr std::array<PropertyDescriptor,7> values{{
        {-96,24,0,Modifier::Sum,music,absolute|sum|highest},
        {0.25,4,1,Modifier::Absolute,music&~kindMask(Kind::Bus),absolute},
        {20,24000,20000,Modifier::Absolute,music,absolute},
        {20,24000,20,Modifier::Highest,music,absolute|highest},
        {0,1,0,Modifier::Absolute,kindMask(Kind::Bus),absolute},
        {-1,1,0,Modifier::Absolute,music,absolute|sum},
        {0,1,0,Modifier::Boolean,kindMask(Kind::Bus),absolute|modifierMask(Modifier::Boolean)}
    }};
    return id>=0 && id<static_cast<int>(values.size()) ? &values[id] : nullptr;
}
}
