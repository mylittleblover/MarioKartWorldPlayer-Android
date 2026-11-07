#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
using Reason=api::StingerDecisionReason;
constexpr std::int64_t reasonId(Reason value) noexcept { return static_cast<std::int64_t>(value); }
void EngineRuntime::clearStinger() noexcept {
    if(stinger_.scene) { stinger_.scene->active=false; stinger_.scene->reserved=false; }
    if(stingerPreparation_)stingerPreparation_->retire();
    stinger_={}; stingerCooldown_.fill(-1); stingerDecision_={0,-1,-1,-1,0,0,0,0,0,-1,0,-1,-1,-1};
}
void EngineRuntime::finishStinger() noexcept {
    if(!stinger_.active || !stinger_.started) return;
    const auto& scene=*stinger_.scene;
    if(scene.active) return;
    for(const auto& voice:voices_.slots()) if(voice.active && voice.owner==scene.owner) return;
    stinger_.scene->active=false; stinger_.scene->reserved=false; stinger_.active=false;
    if(stingerDecision_[2]==stinger_.rule->index && (stingerDecision_[8]==reasonId(Reason::Scheduled) || stingerDecision_[8]==reasonId(Reason::Replaced) || stingerDecision_[8]==reasonId(Reason::Playing))) stingerDecision_[8]=reasonId(Reason::Finished);
}
EngineStatus EngineRuntime::fireTrigger(int trigger) noexcept {
    if(!graph_ || !graph_->node(trigger) || graph_->node(trigger)->kind!=Kind::Trigger) return EngineStatus::InvalidRequest;
    finishStinger();
    stingerDecision_={stinger_.active,trigger,-1,-1,0,0,clock_.frame(),-1,reasonId(Reason::NoCandidate),
        stinger_.active ? stinger_.rule->index : -1,stinger_.active ? stinger_.rule->priority : 0,-1,-1,-1};
    const Stinger* selected=nullptr;
    bool cooling=false;
    for(const auto& rule:graph_->stingers) if(rule.trigger==trigger) {
        ++stingerDecision_[5];
        if(stingerCooldown_[rule.index]>clock_.frame()) {
            cooling=true; stingerDecision_[11]=stingerDecision_[11]<0 ? stingerCooldown_[rule.index] : std::min(stingerDecision_[11],stingerCooldown_[rule.index]); continue;
        }
        if(!selected || rule.priority>selected->priority || (rule.priority==selected->priority && rule.index<selected->index)) selected=&rule;
    }
    if(!selected) { stingerDecision_[8]=cooling ? reasonId(Reason::Cooldown) : reasonId(Reason::NoCandidate); return EngineStatus::Ok; }
    stingerDecision_[2]=selected->index; stingerDecision_[3]=selected->segment; stingerDecision_[4]=selected->priority;
    if(stinger_.active) {
        if(selected->priority<stinger_.rule->priority) { stingerDecision_[8]=reasonId(Reason::LowerPriority); return EngineStatus::Ok; }
        if(selected->priority==stinger_.rule->priority && !selected->replace) { stingerDecision_[8]=reasonId(Reason::Occupied); return EngineStatus::Ok; }
    }
    std::array<bool,4096> visited{}; auto result=targetReady(selected->segment,visited);
    if(result!=EngineStatus::Ok) { stingerDecision_[8]=reasonId(Reason::Failed); return result; }
    Scene* source=foreground_;
    if(transition_.active && transition_.sourceLeft && transition_.bridgeStarted) source=transition_.bridge;
    std::optional<Frame> scheduled;long double distance=0;
    if(selected->sync==SyncPoint::Immediate) scheduled=clock_.frame();
    else if(source && source->segment && source->active) {
        scheduled=sourceSyncFrame(*source->segment,graph_->node(source->segment->index)->tempo,source->position,
            parameters_->property(source->segment->index,1),clock_.frame(),selected->sync,{},false,
            !source->disableLoop && (!source->segment->hasLoop || source->segment->loop.count<0 || source->loopCount<source->segment->loop.count),source->musicalPosition,
            source->segment->hasLoop && source->segment->loop.count>=0 ? source->segment->loop.count-source->loopCount : -1,false,&distance);
    }
    if(!scheduled) { stingerDecision_[8]=reasonId(Reason::Failed); return EngineStatus::MissingCue; }
    const auto* segment=find(graph_->segments,selected->segment); const auto speed=parameters_->property(selected->segment,1);
    const Frame preparation=addFrame(*scheduled,-Tempo::roundedFrame(segment->entry/speed));
    stingerDecision_[7]=*scheduled; stingerDecision_[12]=source && source->segment ? source->segment->index : -1;
    if(preparation<clock_.frame()) { stingerDecision_[8]=reasonId(Reason::Failed); return EngineStatus::DeadlineMiss; }
    Scene* slot=nullptr;
    if(stinger_.active) slot=stinger_.scene;
    else for(auto& scene:scenes_) if(!scene.active && !scene.reserved) { slot=&scene; break; }
    if(!slot) { stingerDecision_[8]=reasonId(Reason::Failed); return EngineStatus::VoiceLimit; }
    if(preparationGeneration_==std::numeric_limits<std::uint64_t>::max())return EngineStatus::ResourceLimit;
    StingerRun pending{true,false,false,selected,slot,*scheduled,preparation,0};
    result=prepareStingerPages(pending);if(result!=EngineStatus::Ok){stingerDecision_[8]=reasonId(Reason::Failed);return result;}
    if(stingerPreparation_)stingerPreparation_->ready();
    const bool replaced=stinger_.active;
    if(replaced) for(auto& voice:voices_.slots()) if(voice.active && voice.owner==slot->owner) voice.active=false;
    slot->active=false; slot->reserved=true;
    const auto& tempo=source && source->segment ? graph_->node(source->segment->index)->tempo : graph_->node(selected->segment)->tempo;
    const auto sourceSpeed=source && source->segment ? parameters_->property(source->segment->index,1) : speed;
    stinger_={true,false,false,selected,slot,*scheduled,preparation,
        Tempo::roundedFrame(tempo.atBeat(selected->cooldownBeats)/sourceSpeed)};
    stinger_.preparationGeneration=++preparationGeneration_;
    stinger_.source=source;stinger_.sourceInstance=source ? source->instance : 0;
    stinger_.sourceBoundary=source ? source->musicalPosition+distance : 0;stinger_.sourceSpeed=sourceSpeed;stinger_.destinationSpeed=speed;
    stingerDecision_[8]=replaced ? reasonId(Reason::Replaced) : reasonId(Reason::Scheduled);
    if(clock_.status()==TransportStatus::Stopped) clock_.play();
    return EngineStatus::Ok;
}
bool EngineRuntime::retimeWaitingStinger() noexcept {
    auto& run=stinger_;
    // After audible preroll starts, preserve that execution phase. If the
    // original source departs/reuses its slot, its projected boundary stays fixed.
    if(run.started || run.rule->sync==SyncPoint::Immediate)return true;
    const bool sourceAlive=run.source && run.source->active && run.source->segment && run.source->instance==run.sourceInstance;
    const auto sourceSpeed=sourceAlive ? parameters_->property(run.source->segment->index,1) : run.sourceSpeed;
    const auto destinationSpeed=parameters_->property(run.rule->segment,1);
    if(sourceSpeed==run.sourceSpeed && destinationSpeed==run.destinationSpeed)return true;
    const auto now=clock_.frame();
    const auto scheduled=sourceAlive ? addFrame(now,Tempo::roundedFrame(std::ceil(std::max(0.0L,run.sourceBoundary-run.source->musicalPosition)/sourceSpeed))) : run.scheduled;
    const auto* segment=find(graph_->segments,run.rule->segment);
    const auto preparation=addFrame(scheduled,-Tempo::roundedFrame(segment->entry/destinationSpeed));
    if(preparation<now) {
        bool historical=false;last_=recordPreparationDecision(run.preparationGeneration,EngineStatus::DeadlineMiss,true,historical);
        if(!historical)++rejected_;
        run.active=false;run.scene->reserved=false;run.preparationDecided=true;
        if(stingerPreparation_)stingerPreparation_->retire();
        stingerDecision_[8]=reasonId(Reason::Failed);return false;
    }
    run.scheduled=scheduled;run.preparation=preparation;run.sourceSpeed=sourceSpeed;run.destinationSpeed=destinationSpeed;
    if(sourceAlive)run.cooldown=Tempo::roundedFrame(graph_->node(run.source->segment->index)->tempo.atBeat(run.rule->cooldownBeats)/sourceSpeed);
    if(stingerDecision_[2]==run.rule->index)stingerDecision_[7]=scheduled;
    return true;
}
void EngineRuntime::tickStinger() noexcept {
    finishStinger(); if(!stinger_.active) return;
    auto& run=stinger_;if(!retimeWaitingStinger() || !stingerPagesReady())return;
    if(!run.started && clock_.frame()>=run.preparation) {
        auto& scene=*run.scene; scene.object=run.rule->segment; scene.owner=static_cast<int>(++instance_ & 0x7fffffff);
        scene.switchObject=-1; scene.switchOwners.reset(); scene.playlistObject=-1; scene.playlist.clear();
        last_=enterSegment(scene,run.rule->segment);
        if(last_!=EngineStatus::Ok) { ++rejected_; scene.reserved=false; run.active=false; stingerDecision_[8]=reasonId(Reason::Failed); return; }
        scene.position=scene.musicalPosition=0; scene.preentry=true; scene.controlled=false; scene.reserved=true;
        run.started=true;if(stingerPreparation_)stingerPreparation_->retire();
    }
    if(!run.entered && clock_.frame()>=run.scheduled) {
        run.entered=true; run.scene->preentry=false;
        stingerCooldown_[run.rule->index]=addFrame(run.scheduled,run.cooldown);
        if(stingerDecision_[2]==run.rule->index && (stingerDecision_[8]==reasonId(Reason::Scheduled) || stingerDecision_[8]==reasonId(Reason::Replaced))) stingerDecision_[8]=reasonId(Reason::Playing);
    }
}
}
