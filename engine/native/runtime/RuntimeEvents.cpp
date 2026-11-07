#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
void EngineRuntime::musicOwners(const Scene& scene,std::bitset<4096>& owners) const noexcept {
    owners=scene.switchOwners; scene.playlist.musicOwners(owners);
    for(const int object: {scene.object,scene.segment ? scene.segment->index : -1}) {
        const auto* node=graph_->node(object);
        for(int depth=0;node && depth<4096;++depth,node=graph_->node(node->parent))
            if(node->kind==Kind::Segment || node->kind==Kind::Playlist || node->kind==Kind::Switch) owners.set(node->index);
    }
}
bool EngineRuntime::scheduleCues(Scene& scene) noexcept {
    if(!scene.active || !scene.segment || scene.segmentEnded) return false;
    const auto* segment=scene.segment; const auto instance=scene.instance;
    long double until=std::min(scene.position,static_cast<long double>(segment->exit));
    if(!scene.preentry && !scene.disableLoop && segment->hasLoop &&
        (segment->loop.count<0 || scene.loopCount<segment->loop.count)) until=std::min(until,static_cast<long double>(segment->loop.end));
    for(const auto& cue:segment->cues) if(!scene.cues[cue.index] && cue.frame<=until) {
        scene.cues[cue.index]=true;
        if(cue.kind==1) {
            const auto priorCue=cueContext_,priorSegment=cueSegment_; cueContext_=cue.index; cueSegment_=segment->index;
            api::EngineCommand command; command.kind=api::EngineCommandKind::Event; command.target=cue.event; execute(command);
            cueContext_=priorCue; cueSegment_=priorSegment;
        }
        if(!scene.active || !clock_.playing() || scene.segment!=segment || scene.instance!=instance) return false;
    }
    return true;
}
EngineStatus EngineRuntime::stopObject(int target,double fadeBeats,bool retainTail) noexcept {
    if(!std::isfinite(fadeBeats) || fadeBeats<0) return EngineStatus::InvalidRequest;
    if(target>=0) {
        const auto* node=graph_ ? graph_->node(target) : nullptr;
        if(!node || (node->kind!=Kind::Segment && node->kind!=Kind::Playlist && node->kind!=Kind::Switch)) return EngineStatus::InvalidRequest;
    } else if(target!=-1) return EngineStatus::InvalidRequest;
    if(queuedTransition_>=0 && graph_) {
        const auto* node=graph_->node(queuedTransition_); bool remove=target==-1;
        for(int depth=0;node && depth<4096;++depth,node=graph_->node(node->parent)) remove|=node->index==target;
        if(remove) queuedTransition_=-1;
    }
    auto matches=[&](const Scene* scene) {
        if(!scene) return false;
        if(target==-1) return true;
        std::bitset<4096> owners; musicOwners(*scene,owners); return owners.test(target);
    };
    if(queuedTransition_>=0 && matches(foreground_))queuedTransition_=-1;
    // An unstarted destination has no Scene ownership yet. Resolve its compiled
    // selected path and playlist stack without consuming selection state.
    if(transition_.active) {
        std::bitset<4096> destinationOwners=transition_.switchOwners;
        transition_.playlist.musicOwners(destinationOwners);
        for(int object: {transition_.target,transition_.segment}) {
            const auto* node=graph_->node(object);
            for(int depth=0;node && depth<4096;++depth,node=graph_->node(node->parent))
                if(node->kind==Kind::Segment || node->kind==Kind::Playlist || node->kind==Kind::Switch) destinationOwners.set(node->index);
        }
        if(target==-1 || destinationOwners.test(target)) { cancelTransition(true); queuedTransition_=-1; }
    }
    if(stinger_.active && !stinger_.started) {
        const auto* node=graph_->node(stinger_.rule->segment); bool stop=target==-1;
        for(int depth=0;node && depth<4096;++depth,node=graph_->node(node->parent)) stop|=node->index==target;
        if(stop) {
            stinger_.scene->reserved=false; stinger_.active=false;
            if(stingerPreparation_)stingerPreparation_->retire();
        }
    }
    for(auto& scene:scenes_) if((scene.active || scene.reserved || &scene==foreground_) && matches(&scene)) {
        archivePlaylist(scene); scene.active=false;
        scene.reserved=transition_.active && (&scene==transition_.source || &scene==transition_.bridge || &scene==transition_.destination);
        scene.controlled=false; scene.segmentEnded=true;
        if(foreground_==&scene) {
            if(albumActive_ && !albumInternalRequest_) {albumActive_=false;albumState_.phase=api::AlbumFlowPhase::Stopped;}
            foreground_=nullptr; current_=-1;
        }
        if(stinger_.active && stinger_.scene==&scene) {
            // Keep ownership until retained/fading audio actually ends.
            scene.reserved=true;
        }
    }
    for(auto& voice:voices_.slots()) if(voice.active && (target==-1 || voice.musicOwners.test(target))) {
        if(retainTail)voice.releaseClipLoop();
        if(fadeBeats==0) {
            if(!retainTail) voice.active=false;
            // A prior transition departure must not cut a requested natural tail.
            else { voice.exitFade=false; voice.fadeStart=-1; voice.stopFrame=-1; }
        } else {
            const auto* node=graph_ ? graph_->node(voice.segment) : nullptr;
            const auto duration=node ? fadeBeats*node->tempo.framesPerBeat()/parameters_->property(voice.segment,1) : fadeBeats*rate_;
            voice.exitFade=true; voice.fadeStart=clock_.frame(); voice.fadeEnd=addFrame(voice.fadeStart,Tempo::roundedFrame(duration));
            voice.fadeShape=Shape::Linear; voice.stopFrame=-1;
        }
    }
    if(target==-1 && fadeBeats==0 && !retainTail && mixer_) mixer_->clear();
    if(target==-1) { cancelPerformance();queuedTransition_=-1; }
    return EngineStatus::Ok;
}
}
