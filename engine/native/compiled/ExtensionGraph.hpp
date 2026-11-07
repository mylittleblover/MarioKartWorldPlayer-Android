#pragma once
#include "compiled/ControlGraph.hpp"
#include "api/AudioSourceTypes.hpp"

namespace player2 {
enum class AlbumFlowMode { Gapless=0,Crossfade=1,ExitCue=2,Bridge=3,Tail=4,Transition=5,Ending=6 };
struct AlbumFlow { int from{-1},to{-1}; AlbumFlowMode mode{}; audio::TransportFrame duration; int bridge{-1}; bool retainTail{true}; };
enum class EndBehavior { Natural=0,Immediate=1,Fade=2,Ending=3 };
struct Entry { int index{-1},start{-1}; std::vector<Action> actions; };
struct PerformanceStep { int index{-1}; Frame frame{}; Action action; };
struct PerformanceFade {audio::TransportFrame duration;audio::TransportFrameOffset offset;Shape shape{};};
struct Performance { int index{-1},start{-1}; std::vector<PerformanceStep> steps; Frame endFrame{-1}; EndBehavior end{};
    std::optional<PerformanceFade> endFade;int ending{-1}; };
struct MusicalLoop { int intro{-1},loop{-1},ending{-1},repeats{}; bool retainIntroTail{}; EndBehavior stop{}; int playlist{-1}; };
struct Automatic { int parameter{-1}; double low{},high{},cycleBeats{}; bool returnDefault{}; std::uint64_t seed{}; };
}
