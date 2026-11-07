#pragma once
#include "api/AudioSourceTypes.hpp"
#include <array>
#include <cstdint>
namespace player2::api {
// Stable monitor IDs, independent of both Authoring and native enum order.
enum class PlaylistSelectionMode : std::int32_t { SequenceContinuous=0,SequenceStep=1,RandomContinuous=2,RandomStep=3 };
enum class PlaylistRandomPolicy : std::int32_t { Standard=0,Shuffle=1 };
enum class PlaylistDecisionReason : std::int32_t { None=0,Sequence=1,Weighted=2,Shuffle=3,Jump=4 };
struct PlaylistDecisionSnapshot {
    int index{-1},container{-1},cursor{-1},chosenItem{-1},configuredAvoid{},effectiveAvoid{},count{};
    PlaylistDecisionReason reason{};
    std::uint64_t randomBefore{},eligible{},repeated{},shuffleUsed{};
    double unit{},scale{1},total{};bool recycledShuffle{};
    audio::TransportFrame selectedAt{-1},at;
    std::uint64_t selectedEpoch{},epoch{};
    std::array<int,64> candidates{};std::array<double,64> weights{};
};
struct PlaylistGroupSnapshot {
    int index{-1},container{-1}; bool active{};
    PlaylistSelectionMode mode{}; PlaylistRandomPolicy randomPolicy{};
    int avoidRepeat{},cursor{-1},chosenItem{-1},historySize{};
    std::uint64_t randomState{};
    // Oldest to newest. Values are compiled stable Item IDs, never array offsets.
    std::array<int,64> history{};
    audio::TransportFrame at;
    PlaylistDecisionSnapshot decision;
};
}
