#pragma once
#include "compiled/GraphTypes.hpp"
#include "compiled/MixerGraph.hpp"
#include "compiled/ClipLoop.hpp"
#include "music/TimelineEnvelope.hpp"

namespace player2 {
enum class TrackMode { Normal=0,RandomStep=1,SequenceStep=2,Switch=3 };
enum class PlaylistMode { SequenceContinuous=0,SequenceStep=1,RandomContinuous=2,RandomStep=3 };
enum class RandomMode { Standard=0,Shuffle=1 };
struct Cue { int index{-1}; std::string name; Frame frame{}; int kind{},event{-1}; };
struct Loop { Frame start{},end{}; int count{}; bool retainTail{}; };
struct Segment { int index{-1}; Frame entry{},exit{}; bool hasLoop{}; Loop loop; std::vector<Cue> cues; std::vector<PreparedTimelineEnvelope> envelopes; };
struct Clip { int index{-1},asset{-1}; Frame start{},sourceOffset{},sourceLength{}; double gainDb{}; Frame fadeIn{},fadeOut{}; std::optional<CompiledClipLoop> loop{}; std::vector<PreparedTimelineEnvelope> envelopes{}; };
struct SubTrack { int index{-1}; double weight{1}; int value{-1}; std::vector<Clip> clips; };
struct Track { int index{-1},segment{-1}; TrackMode mode{}; int group{-1},bus{-1}; std::uint64_t seed{1}; int avoidRepeat{}; bool reset{}; std::vector<Clip> clips; std::vector<SubTrack> subTracks; std::vector<Send> sends; };
struct PlaylistItem { int index{-1},target{-1}; PlaylistMode mode{}; RandomMode random{}; int avoidRepeat{}; double weight{1}; int loops{1}; std::vector<int> children; std::optional<std::uint64_t> stateSeed{}; };
struct Playlist { int index{-1},root{-1}; std::uint64_t seed{1}; bool reset{}; std::vector<PlaylistItem> items; };
struct Path { int index{-1}; std::vector<Condition> conditions; int target{-1},priority{}; };
struct Switch { int index{-1}; std::vector<int> groups; std::vector<Path> paths; int fallback{-1}; bool continueSame{true}; };
}
