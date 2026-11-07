#pragma once
#include <cstdint>
#include <array>
#include "api/AudioSourceTypes.hpp"
namespace player2::api {
// Public command IDs are stable; no C++ or Kotlin declaration-order ABI.
enum class EngineCommandKind : std::int32_t {
    Play=0,Stop=1,Pause=2,Resume=3,Event=4,SetSync=5,SetParameter=6,Trigger=7,Performance=8,EntryState=9,LayerGain=10,Automatic=11,StopAutomatic=12,MusicalLoop=13,StopMusicalLoop=14,AlbumFlow=15,AlbumNext=16
};
inline bool validEngineCommandKind(std::int32_t id)noexcept {
    switch(static_cast<EngineCommandKind>(id)) {
        case EngineCommandKind::Play:case EngineCommandKind::Stop:case EngineCommandKind::Pause:case EngineCommandKind::Resume:
        case EngineCommandKind::Event:case EngineCommandKind::SetSync:case EngineCommandKind::SetParameter:case EngineCommandKind::Trigger:
        case EngineCommandKind::Performance:case EngineCommandKind::EntryState:case EngineCommandKind::LayerGain:
        case EngineCommandKind::Automatic:case EngineCommandKind::StopAutomatic:case EngineCommandKind::MusicalLoop:case EngineCommandKind::StopMusicalLoop:
        case EngineCommandKind::AlbumFlow:case EngineCommandKind::AlbumNext:return true;
    }
    return false;
}
enum class EngineStatus : std::int32_t {
    Ok=0,InvalidRequest=1,MissingAsset=2,QueueFull=3,UnsupportedExecution=4,VoiceLimit=5,ResourceLimit=6,OutputActive=7,RuntimeActive=8,MissingCue=9,DeadlineMiss=10,SourceMismatch=11
};
enum class StingerDecisionReason : std::int32_t {
    None=0,Scheduled=1,Replaced=2,Cooldown=3,LowerPriority=4,Occupied=5,NoCandidate=6,Playing=7,Finished=8,Failed=9
};
enum class SourcePreparation : std::int32_t { Prepared=0,Streamed=1 };
enum class AutomaticPhase : std::int32_t {None=0,Running=1,Returning=2,Stopped=3,Finished=4,Overridden=5,StartFailed=6};
enum class MusicalLoopPhase : std::int32_t {None=0,Playing=1,Ending=2,Stopping=3,Finished=4,Stopped=5,Overridden=6,StartFailed=7};
struct MusicalLoopSnapshot {
    int playlist{-1},segment{-1},item{-1};MusicalLoopPhase phase{MusicalLoopPhase::None};bool active{},paused{};
    audio::TransportFrame requestedAt{-1},at;EngineStatus result{EngineStatus::Ok};
};
struct AutomaticSnapshot {
    int parameter{-1};AutomaticPhase phase{AutomaticPhase::None};bool active{},paused{};
    audio::TransportFrame requestedAt{-1},at;std::int64_t cycles{};std::uint64_t random{};
    double start{},goal{},progress{},cycleBeats{},low{},high{},current{},target{};int clockSegment{-1};
    audio::TransportFrame returnStart{-1},returnEnd{-1};EngineStatus result{EngineStatus::Ok};
};
enum class EntryStateDecision : std::int32_t {None=0,Started=1,ActionFailed=2,StartFailed=3};
struct EntryStateSnapshot {
    int index{-1},start{-1},appliedActions{},failedAction{-1};
    EntryStateDecision decision{EntryStateDecision::None};EngineStatus result{EngineStatus::Ok};
    audio::TransportFrame requestedAt{-1},at;
};
enum class PerformancePhase : std::int32_t {None=0,Running=1,Finished=2,ActionFailed=3,StartFailed=4,Stopped=5,EndFailed=6};
struct PerformanceSnapshot {
    int index{-1},start{-1},steps{},executedSteps{},lastStep{-1};
    PerformancePhase phase{PerformancePhase::None};EngineStatus result{EngineStatus::Ok};
    audio::TransportFrame actionAt{-1},requestedAt{-1},at;bool active{},paused{};
    audio::TransportFrame endAt{-1};
};
struct StreamingSnapshot {
    audio::TransportFrame at;
    std::uint64_t pcmBytes{},encodedBytes{},sources{},cachedPages{},underrunFrames{},requests{},droppedRequests{},decodedPages{},decodeFailures{};
    audio::SourceStatus lastFailure{audio::SourceStatus::Ok};
    std::uint64_t historyRanges{},historyDroppedRanges{};bool historyComplete{true};std::uint64_t historyBytes{};
    std::uint64_t preparationDecisions{},droppedPreparationDecisions{};bool preparationHistoryComplete{true};std::uint64_t preparationHistoryBytes{};
    std::uint64_t futureClipRequirements{},futureClipResourceLimits{};bool futureClipReady{true};
};
struct EngineCommand {
    EngineCommandKind kind{}; int target{-1},value{-1}; double number{};
    audio::TransportFrame at{-1}; // -1 means next audio frame; nonnegative is transport time.
};
struct BusSnapshot {
    int index{-1},output{-1}; bool auxiliary{},bypass{}; double localGainDb{};
    float peakLeft{},peakRight{}; int tailEffects{}; audio::TransportFrame at;
    double lowPassHz{},highPassHz{},reverbWet{}; bool lowPassEnabled{},highPassEnabled{},reverbWetConfigured{};
};
struct GameParameterSnapshot {
    int index{-1}; double current{},target{},min{},max{},defaultValue{};
    std::int64_t attackFrames{},releaseFrames{}; audio::TransportFrame at;
};
struct LayerGainSnapshot {
    int index{-1};double trimDb{},baseDb{},rtpcDb{},finalDb{};audio::TransportFrame at;
};
struct EngineSnapshot {
    // Diagnostic counts are scoped to the current Transport epoch and rebuilt by seek.
    audio::TransportFrame position; int status{},activeVoices{},currentObject{-1};
    std::uint64_t droppedVoices{},rejectedCommands{}; EngineStatus lastResult{EngineStatus::Ok};
    float peakLeft{},peakRight{};
    int playlist{-1},playingSegment{-1},playlistGroup{-1},playlistItem{-1},playlistCursor{-1},playlistHistorySize{};
    std::uint64_t playlistRandomState{};
    int switchContainer{-1},switchTarget{-1},switchPath{-1},switchSpecificity{-1},switchPriority{},switchCandidates{},switchReason{};
    // active, rule, source, destination, bridge, request, source sync, destination entry,
    // preparation start, candidates, specificity, priority, queued target, phase.
    // active, trigger, selected candidate, segment, priority, candidates, request,
    // scheduled entry, decision, occupied stinger, occupied priority, cooldown end, clock segment, occupied entry frame.
    // Last executed Event/Action: event, event sequence, action index, stable ActionKind,
    // target, Transport frame, action result, Cue, Segment, action sequence, Event result.
    std::array<std::int64_t,11> event{-1,0,-1,-1,-1,0,0,-1,-1,0,0};
    std::array<std::int64_t,14> stinger{0,-1,-1,-1,0,0,0,0,0,-1,0,-1,-1,-1};
    std::array<std::int64_t,14> transition{0,-1,-1,-1,-1,0,0,0,0,0,-1,0,-1,0};
    std::uint64_t voiceNumericalCorrections{},mixerNumericalCorrections{};
    std::uint64_t transportEpoch{};
};
}
