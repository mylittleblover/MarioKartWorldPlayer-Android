#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
EngineStatus EngineRuntime::seek(audio::TransportFrame target,std::uint64_t maximumReplayFrames) noexcept {
    if(target.value<0 || static_cast<std::uint64_t>(target.value)>maximumReplayFrames) return EngineStatus::ResourceLimit;
    if(streamHistory_ && !streamHistory_->complete())return EngineStatus::ResourceLimit;
    if(preparationHistory_ && !preparationHistory_->complete())return EngineStatus::ResourceLimit;
    // Apply next-frame inputs at the old position before capturing its epoch.
    // Future scheduled inputs remain pending and participate in forward replay.
    if(parameters_) parameters_->beginFrame(clock_.frame());
    drain();
    if(!journal_->withinBudget(static_cast<std::uint64_t>(target.value),maximumReplayFrames) ||
        (actionDecisions_ && !actionDecisions_->complete())) return EngineStatus::ResourceLimit;
    if(journal_->size==0) return EngineStatus::InvalidRequest;
    // A decoded-linear source has no scheduled musical/DSP state. Reposition
    // its one Voice analytically; the decoder still performs exact source replay
    // on the stopped worker. Graphs retain their full deterministic rebuild.
    bool linearHistory=!graph_ && linear_ && !pendingCount_;
    for(std::size_t n=0;n<journal_->size && linearHistory;++n) {
        const auto& record=journal_->records[n];
        if(record.epoch!=journal_->epoch || record.result!=EngineStatus::Ok)continue;
        const auto kind=record.command.kind;
        linearHistory=kind==api::EngineCommandKind::Play || kind==api::EngineCommandKind::Pause || kind==api::EngineCommandKind::Resume;
    }
    if(linearHistory) {
        const auto source=static_cast<long double>(target.value)*linear_->sampleRate/rate_;
        if(!linearLoop_ && source>linear_->frames())return EngineStatus::InvalidRequest;
        Voice voice;voice.sourceLength=linear_->frames();voice.begin(linear_.get(),rate_,static_cast<double>(target.value));
        voice.repeatRange(linearLoop_,linearRepeatRange_);voice.wrapSource();
        if(linear_->stream && voice.sourcePosition<linear_->frames()) {
            streamCache_->beginControlRender();
            voice.streamCursor.bind(linear_->stream,linear_->streamedAsset);
            const auto ready=voice.streamCursor.prepare(voice.sourcePosition);
            streamCache_->endControlRender();
            if(!ready)return EngineStatus::MissingAsset;
        }
        const auto paused=clock_.status()==TransportStatus::Paused;
        voices_.clear();voices_.add(std::move(voice));clock_.seek(target.value);clock_.play();if(paused)clock_.pause();
        journal_->cursor=journal_->size;
        if(streamHistory_)streamHistory_->branch({journal_->epoch,target});
        return EngineStatus::Ok;
    }
    const auto originalFrame=clock_.frame();
    const auto dropped=voices_.dropped(); const auto rejected=rejected_; const bool paused=clock_.status()==TransportStatus::Paused;
    if(streamCache_)streamCache_->beginControlRender();
    seekEpoch_=journal_->epoch; seekFrame_=target.value; rebuilding_=true;
    resetStopped(); journal_->rewind();if(streamHistory_)streamHistory_->rewind();if(preparationHistory_)preparationHistory_->rewind();
    if(actionDecisions_)actionDecisions_->rewind();
    entryReplayFailure_=EngineStatus::Ok;
    eventDecision_={-1,0,-1,-1,-1,0,0,-1,-1,0,0}; cueContext_=cueSegment_=-1;
    actionWorkFrame_=-1;actionWorkRemaining_=0;
    entryState_={};performanceState_={};automaticState_={};automaticRandom_.reset(1);musicalLoopState_={};albumState_={};
    if(parameters_) parameters_->reset();
    if(graph_) {
        for(auto& selection:selections_) selection.reset(1);
        for(const auto& track:graph_->tracks) selections_[track.index].reset(track.seed);
        for(const auto& playlist:graph_->playlists) for(const auto& item:playlist.items)
            selections_[item.index].reset(playlistItemSeed(playlist,item));
        for(auto& archive:playlistArchives_) archive.session.clear();
        lastPositions_.fill(-1); preview_->reset();
    }
    EngineStatus result=EngineStatus::Ok; std::uint64_t replayed=0,newRejected=0,newDropped=0;
    float discard[2]{};
    auto account=[&](std::uint64_t priorRejected,std::uint64_t priorDropped,bool future) {
        if(future) { newRejected+=rejected_-priorRejected; newDropped+=voices_.dropped()-priorDropped; }
    };
    for(;;) {
        const auto beforeDrainRejected=rejected_,beforeDrainDropped=voices_.dropped();
        if(parameters_) parameters_->beginFrame(clock_.frame());
        drain();
        if(actionDecisions_ && !actionDecisions_->complete()){result=EngineStatus::ResourceLimit;break;}
        account(beforeDrainRejected,beforeDrainDropped,journal_->epoch==seekEpoch_ && clock_.frame()>=originalFrame);
        if(journal_->epoch==seekEpoch_ && clock_.frame()==target.value) break;
        if(clock_.status()==TransportStatus::Paused) clock_.play(); // Worker replay ignores wall-clock pauses.
        if(journal_->epoch>seekEpoch_ || !clock_.playing()) { result=EngineStatus::InvalidRequest; break; }
        if(replayed==maximumReplayFrames) { result=EngineStatus::ResourceLimit; break; }
        const auto before=clock_.frame(); const auto beforeEpoch=journal_->epoch;
        const auto beforeRenderRejected=rejected_,beforeRenderDropped=voices_.dropped();
        render(discard,1);
        account(beforeRenderRejected,beforeRenderDropped,beforeEpoch==seekEpoch_ && before>=originalFrame);
        if(clock_.frame()!=before || journal_->epoch!=beforeEpoch) ++replayed;
    }
    rebuilding_=false; rejected_=rejected+newRejected; voices_.restoreDropped(dropped+newDropped);
    if(paused && clock_.playing()) clock_.pause();
    else if(!paused && clock_.status()==TransportStatus::Paused) clock_.play();
    updateMixerProperties();
    if(streamCache_)streamCache_->endControlRender();
    return result;
}
}
