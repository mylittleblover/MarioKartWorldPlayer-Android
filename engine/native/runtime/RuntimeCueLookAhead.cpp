#include "runtime/EngineRuntime.hpp"
namespace player2 {
CueForecast EngineRuntime::forecastSourceCue(const Scene& source,const Transition& rule,bool includeCurrent) noexcept {
    if(!source.segment || !source.playlist.active())return {};
    return forecastCue(*graph_,source.playlist,*sourcePreview_,parameters_->values(),*source.segment,
        source.position,source.musicalPosition,
        source.segment->hasLoop && source.segment->loop.count>=0 ? source.segment->loop.count-source.loopCount : -1,
        !source.disableLoop,clock_.frame(),rule.sync,rule.sourceCue,
        [&](int index){return parameters_->property(index,1);},includeCurrent);
}
void EngineRuntime::departForecastScene(TransitionRun& run) noexcept {
    auto& source=*run.source;const auto& rule=*run.rule;
    const auto* segment=find(graph_->segments,run.forecastPlan.segment);
    const auto& tempo=graph_->node(segment->index)->tempo;
    const auto speed=parameters_->property(segment->index,1);
    source.departure=rule.fadeOut.beats>0 || rule.fadeOut.offset!=0;
    source.departureStart=addFrame(run.sourceSync,fadeOffset(rule.fadeOut,tempo,speed));
    source.departureEnd=addFrame(source.departureStart,fadeDuration(rule.fadeOut,tempo,speed));
    source.departureShape=rule.fadeOut.shape;
    // Earlier source segments still reach their own natural exit before the
    // forecast cue. Keep their already-authored post-exit tails when requested;
    // the actual cue-bearing segment is handled again after it is entered.
    source.stopFrame=rule.postExit && (source.departure || run.forecastPlan.hops>0 || run.forecastPlan.position>=segment->exit) ? -1 : run.sourceSync;
    for(auto& voice:voices_.slots())if(voice.active && voice.instance==source.instance) {
        voice.exitFade=source.departure;voice.fadeStart=source.departure ? source.departureStart : -1;
        voice.fadeEnd=source.departureEnd;voice.fadeShape=source.departureShape;voice.stopFrame=source.stopFrame;
    }
}
bool EngineRuntime::refreshCueForecast() noexcept {
    auto& run=transition_;auto& source=*run.source;
    if(run.sourceLeft)return true;
    const bool entered=source.instance!=run.sourceInstance;
    bool changed=false;
    for(std::size_t n=0;n<=run.forecastPlan.hops;++n)
        changed|=parameters_->property(run.forecastPlan.path[n],1)!=run.forecastPlan.rates[n];
    const bool destinationChanged=parameters_->property(run.segment,1)!=run.destinationSpeed ||
        (run.rule->bridge>=0 && parameters_->property(run.rule->bridge,1)!=run.bridgeSpeed);
    if(!entered && !changed && !destinationChanged) {
        // Let the frame dispatcher enter a cue-bearing future instance before
        // committing departure at its entry frame.
        return run.forecastPlan.hops==0 || clock_.frame()<run.sourceSync;
    }
    const bool begun=run.destinationStarted || run.bridgeStarted || (source.departure && clock_.frame()>=source.departureStart);
    // Destination phases already producing audio keep the existing speed policy.
    if(!entered && !changed && begun)return true;
    auto next=forecastSourceCue(source,*run.rule,true);
    const bool same=matchesForecastSuffix(run.forecastPlan,next,entered);
    if(!same || (destinationChanged && !begun)) {
        if(begun) {cancelTransition();last_=api::EngineStatus::DeadlineMiss;++rejected_;return false;}
        replanCueTransition(entered);return false;
    }
    run.forecastPlan=next;run.sourceInstance=source.instance;run.sourceSegment=source.segment->index;
    run.sourceSpeed=parameters_->property(run.sourceSegment,1);run.sourceLoopDisabled=source.disableLoop;
    // enterSegment resets local scheduling, so reapply the same absolute fade
    // before its new Voices are created. The source session/RNG is never replaced.
    departForecastScene(run);return true;
}
bool EngineRuntime::yieldCueRequest(Scene& source) noexcept {
    if(!transition_.active || transition_.switchChange || transition_.source!=&source || (!transition_.cueSearch && !transition_.forecast))return false;
    if(transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
        (source.departure && clock_.frame()>=source.departureStart))return false;
    const int target=transition_.target;cancelTransition();
    queuedTransition_=target;queuedSwitch_=false;return true;
}
void EngineRuntime::resumeCueRequest() noexcept {
    // Explicit Nothing retains a real queued intent until the source Switch
    // becomes audible again. Stop/new Play clears or replaces that same queue.
    if(transition_.active || queuedTransition_<0 || !foreground_)return;
    if(!foreground_->active) {
        queuedTransition_=-1;if(last_==api::EngineStatus::Ok)last_=api::EngineStatus::MissingCue;++rejected_;return;
    }
    if(!foreground_->segment)return;
    const int target=queuedTransition_;const bool switchChange=queuedSwitch_;queuedTransition_=-1;
    if(switchChange && foreground_->switchObject>=0) {
        foreground_->switchRevision=parameters_->syncRevision()-1;updateSwitch(*foreground_);
    } else {
        cueSearchReplan_=true;last_=requestPlay(target);cueSearchReplan_=false;
        if(last_!=api::EngineStatus::Ok)++rejected_;
    }
}

void EngineRuntime::replanCueTransition(bool entered) noexcept {
    auto* source=transition_.source;const int target=transition_.target;
    const bool root=transition_.switchChange,nested=transition_.playlistSwitchChange;
    const int previousTarget=transition_.sourceSwitchTarget;cancelTransition();
    cueSearchReplan_=entered;
    if(nested) {
        source->playlistSwitchRevision=parameters_->syncRevision()-1;updatePlaylistSwitch(*source);
    } else if(root) {
        source->switchTarget=previousTarget;source->switchRevision=parameters_->syncRevision()-1;updateRootSwitch(*source);
    } else {
        last_=requestPlay(target);if(last_!=api::EngineStatus::Ok)++rejected_;
    }
    cueSearchReplan_=false;
}

}
