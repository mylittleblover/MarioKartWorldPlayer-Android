#pragma once
#include "compiled/GraphTypes.hpp"

namespace player2 {
struct Send { int bus{-1}; double gainDb{}; };
struct Effect { int index{-1},kind{}; bool bypass{}; std::array<double,10> parameters{}; };
struct EffectRtpc { int index{-1},parameter{-1},bus{-1},effect{-1},property{}; Modifier policy{}; std::vector<Point> points; };
struct Bus { int index{-1},output{-1}; bool auxiliary{}; std::vector<Effect> effects; std::vector<Send> sends; };
}
