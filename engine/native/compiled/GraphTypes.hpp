#pragma once
#include "clock/MusicTime.hpp"
#include "rtpc/Curve.hpp"
#include <array>
#include <string>
#include <vector>

namespace player2 {
enum class Kind { Folder=0,Segment=1,Track=2,Playlist=3,Switch=4,SyncGroup=5,Parameter=6,Trigger=7,Event=8,Transition=9,Rtpc=10,Stinger=11,Bus=12,Media=13 };
struct Node { int index{-1}; Kind kind{}; int parent{-1}; Tempo tempo; std::array<double,7> properties{}; std::uint32_t configuredProperties{}; };
struct Condition { int group{-1},value{-1}; };
template<class T> const T* find(const std::vector<T>& values,int index) noexcept { for(const auto& value:values) if(value.index==index) return &value; return nullptr; }
}
