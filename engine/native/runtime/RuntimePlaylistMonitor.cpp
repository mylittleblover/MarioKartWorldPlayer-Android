#include "runtime/EngineRuntime.hpp"
namespace player2 {
bool EngineRuntime::playlistGroupActive(int index) const noexcept {
    for(const auto& scene:scenes_) if((scene.active || (transition_.active && transition_.source==&scene && foreground_==&scene)) && scene.playlist.containsGroup(index)) return true;
    return false;
}
std::bitset<4096> EngineRuntime::activePlaylistGroups() const noexcept {
    std::bitset<4096> groups;
    for(const auto& scene:scenes_)if(scene.active || (transition_.active && transition_.source==&scene && foreground_==&scene))scene.playlist.activeGroups(groups);
    return groups;
}
api::PlaylistGroupSnapshot EngineRuntime::playlistGroupSnapshot(int index,bool includeCandidates) const noexcept {
    api::PlaylistGroupSnapshot value; value.at={clock_.frame()};
    if(index<0 || index>=4096 || !playlistGroups_[index]) return value;
    const auto& group=*playlistGroups_[index]; const auto& state=selections_[index];
    value.index=index; value.container=playlistGroupOwners_[index]; value.active=playlistGroupActive(index);
    switch(group.mode) {
        case PlaylistMode::SequenceContinuous: value.mode=api::PlaylistSelectionMode::SequenceContinuous; break;
        case PlaylistMode::SequenceStep: value.mode=api::PlaylistSelectionMode::SequenceStep; break;
        case PlaylistMode::RandomContinuous: value.mode=api::PlaylistSelectionMode::RandomContinuous; break;
        case PlaylistMode::RandomStep: value.mode=api::PlaylistSelectionMode::RandomStep; break;
    }
    value.randomPolicy=group.random==RandomMode::Shuffle ? api::PlaylistRandomPolicy::Shuffle : api::PlaylistRandomPolicy::Standard;
    value.avoidRepeat=group.avoidRepeat; value.cursor=state.cursor; value.randomState=state.random;
    const auto identity=[&](int offset) { return offset>=0 && static_cast<std::size_t>(offset)<group.children.size() ? group.children[offset] : -1; };
    value.chosenItem=identity(state.cursor); value.historySize=state.historySize; value.history.fill(-1);
    for(int n=0;n<state.historySize;++n) value.history[n]=identity(state.history[(state.historyHead+64-state.historySize+n)%64]);
    auto& trace=value.decision;const auto& decision=state.decision;
    trace.index=index;trace.container=value.container;trace.cursor=value.cursor;trace.chosenItem=value.chosenItem;
    trace.configuredAvoid=group.avoidRepeat;trace.effectiveAvoid=decision.effectiveAvoid;trace.count=static_cast<int>(group.children.size());
    switch(decision.kind) {
        case SelectionDecisionKind::None: trace.reason=api::PlaylistDecisionReason::None;break;
        case SelectionDecisionKind::Sequence: trace.reason=api::PlaylistDecisionReason::Sequence;break;
        case SelectionDecisionKind::Weighted: trace.reason=api::PlaylistDecisionReason::Weighted;break;
        case SelectionDecisionKind::Shuffle: trace.reason=api::PlaylistDecisionReason::Shuffle;break;
        case SelectionDecisionKind::Jump: trace.reason=api::PlaylistDecisionReason::Jump;break;
    }
    trace.randomBefore=decision.randomBefore;trace.eligible=decision.eligible;trace.repeated=decision.repeated;trace.shuffleUsed=decision.shuffleUsed;
    trace.unit=decision.unit;trace.scale=decision.scale;trace.total=decision.total;trace.recycledShuffle=decision.recycledShuffle;
    trace.selectedAt={decision.at};trace.selectedEpoch=decision.epoch;trace.epoch=journal_->epoch;trace.at=value.at;trace.candidates.fill(-1);
    if(includeCandidates) {
        const auto* playlist=find(graph_->playlists,value.container);
        for(std::size_t n=0;n<group.children.size();++n) {
            trace.candidates[n]=group.children[n];trace.weights[n]=find(playlist->items,group.children[n])->weight;
        }
    }
    return value;
}
}
