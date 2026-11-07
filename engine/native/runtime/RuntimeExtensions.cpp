#include "runtime/EngineRuntime.hpp"
namespace player2 {
void EngineRuntime::cancelPerformance() noexcept {
    if(performance_)performanceState_.phase=api::PerformancePhase::Stopped;
    performance_=nullptr;performanceStep_=0;
}
void EngineRuntime::finishPerformancePlayback() noexcept {
    stopAutomatic(true,api::AutomaticPhase::Finished);
    // An automatic end belongs to this timeline. Preserve its final frame/epoch
    // so seek can rebuild it; explicit user Stop still resets Transport to zero.
    stopObject(-1,0,false);clearStinger();cancelTransition();voices_.clear();clock_.finish();
    if(futureClipPreparation_){futureClipPreparation_->retire();futureClipPreparation_->trim();}
    futureClipCount_=0;futureClipOverflow_=futureClipLimited_=false;futureClipReady_=true;
}
api::EngineStatus EngineRuntime::startPerformance(int index) noexcept {
    const auto requested=clock_.frame();
    const auto* p=graph_ ? find(graph_->performances,index) : nullptr;
    auto failure=[&](api::EngineStatus result) {
        // A rejected request cannot replace diagnostics for a running timeline.
        if(!performance_){performanceState_={};performanceState_.index=index;performanceState_.start=p ? p->start : -1;performanceState_.requestedAt={requested};performanceState_.phase=api::PerformancePhase::StartFailed;performanceState_.result=result;}
        return result;
    };
    if(!p)return failure(api::EngineStatus::InvalidRequest);
    if((p->end==EndBehavior::Fade && !p->endFade) || (p->end==EndBehavior::Ending && p->ending==-1)) {
        return failure(api::EngineStatus::UnsupportedExecution);
    }
    resetStopped();parameters_->reset();const auto result=startSegment(p->start);
    performanceState_={};performanceState_.index=index;performanceState_.start=p->start;performanceState_.steps=static_cast<int>(p->steps.size());
    performanceState_.requestedAt={requested};performanceState_.endAt={p->endFrame};performanceState_.result=result;
    performanceState_.phase=result==api::EngineStatus::Ok ? api::PerformancePhase::Running : api::PerformancePhase::StartFailed;
    if(result==api::EngineStatus::Ok)performance_=p;
    return result;
}
void EngineRuntime::advancePerformance() noexcept {
    if(!performance_)return;
    while(performanceStep_<performance_->steps.size() && performance_->steps[performanceStep_].frame<=clock_.frame()) {
        const auto step=performance_->steps[performanceStep_++];
        performanceState_.lastStep=step.index;performanceState_.actionAt={clock_.frame()};
        action(step.action,0);performanceState_.result=last_;
        if(last_!=api::EngineStatus::Ok){performanceState_.phase=api::PerformancePhase::ActionFailed;performance_=nullptr;return;}
        ++performanceState_.executedSteps;
        // Pause holds later Actions, including those authored at this same frame.
        if(!performance_ || !clock_.playing())return;
    }
    const auto* p=performance_;
    Frame end=p->endFrame;
    if(p->end==EndBehavior::Fade)end=addFrame(addFrame(end,p->endFade->offset.value),p->endFade->duration.value);
    if(p->endFrame>=0 && clock_.frame()>=end) {
        stopAutomatic(true,api::AutomaticPhase::Finished);
        performance_=nullptr;performanceState_.phase=api::PerformancePhase::Finished;
        if(p->end==EndBehavior::Immediate || p->end==EndBehavior::Fade)finishPerformancePlayback();
        else if(p->end==EndBehavior::Ending) {
            last_=requestPlay(p->ending);performanceState_.result=last_;
            if(last_!=api::EngineStatus::Ok){performanceState_.phase=api::PerformancePhase::EndFailed;++rejected_;}
        }
    } else if(performance_->endFrame==-1 && performanceStep_==performance_->steps.size() && (!foreground_ || !foreground_->active) &&
        !transition_.active && !stinger_.active && voices_.count()==0) {
        stopAutomatic(true,api::AutomaticPhase::Finished);
        performance_=nullptr;performanceState_.phase=api::PerformancePhase::Finished;
    }
}
double EngineRuntime::performanceEnvelope()const noexcept {
    if(!performance_ || performance_->end!=EndBehavior::Fade || !performance_->endFade)return 1;
    const auto& p=*performance_;const auto f=*p.endFade;const auto start=addFrame(p.endFrame,f.offset.value);
    const auto elapsed=static_cast<long double>(clock_.frame())-start;
    if(elapsed<=0)return 1;
    return 1-shapeValue(elapsed/f.duration.value,f.shape);
}
api::EngineStatus EngineRuntime::applyEntryState(int index,int depth) noexcept {
    entryState_={};entryState_.index=index;entryState_.requestedAt={clock_.frame()};
    const auto* entry=graph_ ? find(graph_->entries,index) : nullptr;
    if(!entry){entryState_.result=api::EngineStatus::InvalidRequest;entryState_.decision=api::EntryStateDecision::StartFailed;return entryState_.result;}
    entryState_.start=entry->start;
    // Same typed Action executor as Events/Performance, in authored order.
    // Initial Game Sync is applied before the standard target is resolved.
    // A failure keeps prior applied actions visible and prevents target start.
    for(std::size_t n=0;n<entry->actions.size();++n) {
        action(entry->actions[n],depth+1);
        if(last_!=api::EngineStatus::Ok) {
            entryState_.failedAction=static_cast<int>(n);entryState_.result=last_;entryState_.decision=api::EntryStateDecision::ActionFailed;
            return last_;
        }
        ++entryState_.appliedActions;
    }
    // Historical start failure must survive newly bound/warm sources. The
    // successful initial Actions above still rebuild the same Game Sync state.
    const auto result=entryReplayFailure_==api::EngineStatus::Ok ? requestPlay(entry->start) : entryReplayFailure_;
    entryState_.result=result;
    if(result==api::EngineStatus::Ok && albumActive_){albumActive_=false;albumState_.phase=api::AlbumFlowPhase::Overridden;}
    entryState_.decision=result==api::EngineStatus::Ok ? api::EntryStateDecision::Started : api::EntryStateDecision::StartFailed;
    return result;
}
}
