#include "runtime/EngineRuntime.hpp"
#include <limits>
namespace player2 {
using api::EngineStatus;
using api::AlbumFlowPhase;
EngineStatus EngineRuntime::startAlbumFlow(int target) noexcept {
    if(!graph_ || target<0 || target>=4096 || !albumFlowsByFrom_[target])return EngineStatus::InvalidRequest;
    std::array<bool,4096> visited{};const auto ready=targetReady(target,visited);
    if(ready!=EngineStatus::Ok)return ready;
    const auto requested=clock_.frame();resetStopped();const auto result=startSegment(target);
    albumState_={};albumState_.requestedAt={requested};albumState_.result=result;
    albumActive_=result==EngineStatus::Ok;albumCurrent_=target;albumOwner_=foreground_ ? foreground_->owner : -1;
    albumState_.phase=albumActive_ ? AlbumFlowPhase::Playing : AlbumFlowPhase::Failed;return result;
}
EngineStatus EngineRuntime::nextAlbumFlow(bool automatic) noexcept {
    if(!albumActive_ || albumEnding_ || albumWaitingTail_ || !foreground_ || albumCurrent_<0)return EngineStatus::InvalidRequest;
    if(transition_.active) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (transition_.source->departure && clock_.frame()>=transition_.source->departureStart);
        if(automatic || !transition_.albumEdge || !transition_.naturalSource || begun)return EngineStatus::RuntimeActive;
    }
    const auto* flow=albumFlowsByFrom_[albumCurrent_];if(!flow)return EngineStatus::InvalidRequest;
    std::array<bool,4096> visited{};auto result=targetReady(flow->to,visited);
    if(result==EngineStatus::Ok && flow->bridge>=0)result=targetReady(flow->bridge,visited);
    albumState_.from=flow->from;albumState_.to=flow->to;albumState_.mode=static_cast<int>(flow->mode);albumState_.bridge=flow->bridge;
    albumState_.requestedAt={clock_.frame()};albumState_.result=result;
    if(result!=EngineStatus::Ok)return result;
    // An explicit Next can replace this Album's unstarted automatic look-ahead
    // edge, after readiness succeeds. Already audible transitions remain intact.
    if(transition_.active)cancelTransition();
    const bool paused=clock_.status()==TransportStatus::Paused;
    if(flow->mode==AlbumFlowMode::Tail) {
        albumInternalRequest_=true;result=stopObject(albumCurrent_,0,flow->retainTail);albumInternalRequest_=false;
        if(result==EngineStatus::Ok) {albumWaitingTail_=true;albumState_.phase=AlbumFlowPhase::WaitingTail;}
    } else if((flow->mode==AlbumFlowMode::Gapless && !automatic) || !foreground_->segment) {
        // Natural item completion and Explicit Nothing use the same standard
        // target entry. Retained old voices remain on the single SampleClock.
        albumInternalRequest_=true;result=stopObject(albumCurrent_,0,flow->retainTail);albumInternalRequest_=false;
        if(result==EngineStatus::Ok)result=startSegment(flow->to);
        if(result==EngineStatus::Ok)albumState_.scheduledAt={clock_.frame()};
    } else {
        // A bounded synthetic standard Transition rule has Engine lifetime.
        // No template/album-specific transport, mixer or scheduling path exists.
        albumRule_={};albumRule_.sync=flow->mode==AlbumFlowMode::Crossfade && !automatic ? SyncPoint::Immediate : SyncPoint::ExitCue;
        albumRule_.entry=EntryMode::EntryCue;albumRule_.bridge=flow->bridge;albumRule_.postExit=flow->retainTail;albumRule_.preEntry=true;
        preview_->reset();TransitionRun destination;
        result=prepareDestination(*foreground_,flow->to,albumRule_,destination);
        if(result==EngineStatus::Ok) {
            if(flow->mode==AlbumFlowMode::Crossfade) {
                const auto source=foreground_->segment->index;
                albumRule_.fadeOut.beats=flow->duration.value*parameters_->property(source,1)/graph_->node(source)->tempo.framesPerBeat();
                if(automatic)albumRule_.fadeOut.offset=-albumRule_.fadeOut.beats;
                albumRule_.fadeIn.beats=flow->duration.value*parameters_->property(destination.segment,1)/graph_->node(destination.segment)->tempo.framesPerBeat();
            }
            auto choice=TransitionChoice{&albumRule_,1,0,0};
            if(flow->mode==AlbumFlowMode::Transition || flow->mode==AlbumFlowMode::Ending) {
                const auto authored=transitionChoice(*foreground_,flow->to,destination);
                if(authored.rule)choice=authored;
            }
            result=planTransition(*foreground_,flow->to,choice,false,nullptr,automatic,
                automatic && flow->mode==AlbumFlowMode::Crossfade ? flow->duration.value : 0);
            if(result==EngineStatus::Ok)albumState_.scheduledAt={transition_.destinationEntry};
        }
    }
    albumState_.result=result;
    if(result==EngineStatus::Ok) {
        albumEnding_=flow->mode==AlbumFlowMode::Ending;
        if(transition_.active)transition_.albumEdge=true;
        else if(!albumWaitingTail_)updateAlbumCurrent(true);
        if(!albumWaitingTail_)albumState_.phase=transition_.active ? AlbumFlowPhase::Transitioning : AlbumFlowPhase::Playing;
        if(paused)clock_.pause();
    }
    return result;
}
void EngineRuntime::advanceAlbumFlow() noexcept {
    if(!albumActive_)return;
    updateAlbumCurrent();
    if(transition_.active) {albumState_.phase=AlbumFlowPhase::Transitioning;return;}
    if(albumWaitingTail_) {
        for(const auto& voice:voices_.slots())if(voice.active && voice.owner==albumOwner_)return;
        if(mixer_ && mixer_->hasTail())return;
        const auto* flow=albumFlowsByFrom_[albumCurrent_];
        const auto result=flow ? startSegment(flow->to) : EngineStatus::InvalidRequest;
        albumWaitingTail_=false;albumState_.result=result;albumState_.scheduledAt={clock_.frame()};
        if(result!=EngineStatus::Ok) {albumActive_=false;albumState_.phase=AlbumFlowPhase::Failed;last_=result;++rejected_;return;}
        updateAlbumCurrent(true);
    }
    updateAlbumCurrent();
    // Explicit Nothing and step sessions wait for standard Game Sync/commands.
    // Finite selected leaves can leave via an authored Album edge; changes
    // before departure invalidate/rebuild the existing Transition preparation.
    if(!foreground_ || foreground_->reserved)return;
    if(foreground_->active && !albumEnding_ && foreground_->segment &&
        albumCurrent_>=0 && albumFlowsByFrom_[albumCurrent_] && albumFlowsByFrom_[albumCurrent_]->mode!=AlbumFlowMode::Tail) {
        if(albumProbeInstance_!=foreground_->instance || albumProbeRevision_!=parameters_->syncRevision()) {
            albumProbeInstance_=foreground_->instance;albumProbeRevision_=parameters_->syncRevision();albumTerminal_=true;
            if(foreground_->playlist.active()) {
                auto session=foreground_->playlist;preview_->reset();
                const auto next=session.next(*graph_,*preview_,parameters_->values(),clock_.frame(),journal_->epoch);
                albumTerminal_=next<0 && !session.waitingSwitch() && !session.limited();
            }
        }
        const auto* segment=foreground_->segment;
        if(albumTerminal_ && !(segment->hasLoop && segment->loop.count<0 && !foreground_->disableLoop)) {
            const auto result=nextAlbumFlow(true);
            if(result!=EngineStatus::Ok) {albumState_.result=result;albumState_.phase=AlbumFlowPhase::Failed;albumActive_=false;last_=result;++rejected_;}
        }
        return;
    }
    if(foreground_->playlist.active() || (foreground_->active && !foreground_->segmentEnded))return;
    if(!albumEnding_ && albumCurrent_>=0 && albumFlowsByFrom_[albumCurrent_]) {
        const auto result=nextAlbumFlow(true);
        if(result!=EngineStatus::Ok) {albumState_.result=result;albumState_.phase=AlbumFlowPhase::Failed;albumActive_=false;last_=result;++rejected_;}
        return;
    }
    completeAlbumFlow();
}
void EngineRuntime::completeAlbumFlow() noexcept {
    // Completion may become observable only after the final Voice/DSP frame.
    // This path publishes a terminal state; it never starts an Album edge.
    if(!albumActive_ || transition_.active || albumWaitingTail_ || !foreground_ || foreground_->reserved)return;
    if(foreground_->playlist.active() || (foreground_->active && !foreground_->segmentEnded))return;
    if(!albumEnding_ && albumCurrent_>=0 && albumFlowsByFrom_[albumCurrent_])return;
    for(const auto& voice:voices_.slots())if(voice.active && voice.owner==albumOwner_)return;
    if(mixer_ && mixer_->hasTail())return;
    foreground_->active=false;foreground_->controlled=false;
    albumActive_=false;albumState_.phase=AlbumFlowPhase::Finished;
}
void EngineRuntime::updateAlbumCurrent(bool committedEdge) noexcept {
    if(foreground_ && foreground_->owner!=albumOwner_) {
        albumCurrent_=foreground_->object;albumOwner_=foreground_->owner;
        if(committedEdge && albumState_.completedEdges<std::numeric_limits<std::int64_t>::max())++albumState_.completedEdges;
        albumState_.phase=albumEnding_ ? AlbumFlowPhase::Ending : AlbumFlowPhase::Playing;
    }
}
}
