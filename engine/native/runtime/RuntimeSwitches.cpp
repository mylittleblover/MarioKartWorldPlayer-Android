#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
int EngineRuntime::resolveSceneSwitch(Scene& scene,bool& restart) noexcept {
    const auto& values=parameters_->values(); int target=scene.switchObject; scene.switchOwners.reset();
    for(int depth=0;depth<33;++depth) {
        const auto* branch=find(graph_->switches,target);
        if(!branch) {
            scene.switchValues=values; scene.switchRevision=parameters_->syncRevision(); return target;
        }
        scene.switchOwners.set(target);
        bool changed=false; for(const auto group:branch->groups) changed|=scene.switchValues[group]!=values[group];
        restart|=changed && !branch->continueSame;
        const auto choice=resolveSwitch(*branch,values);
        if(depth==0) scene.switchChoice=choice;
        target=choice.target;
    }
    last_=EngineStatus::ResourceLimit; ++rejected_; return -1;
}
void EngineRuntime::updateRootSwitch(Scene& scene) noexcept {
    if(scene.switchObject<0 || scene.switchRevision==parameters_->syncRevision()) return;
    const auto sourceOwners=scene.switchOwners;
    bool restart=false; const auto target=resolveSceneSwitch(scene,restart);
    bool waiting=transition_.active && transition_.source==&scene;
    if(target==scene.switchTarget && !restart && (!waiting || transition_.cueSearch || transition_.forecast || transition_.playlistSwitchChange || transition_.revision==parameters_->syncRevision())) return;
    if(transition_.active && transition_.source==&scene && scene.segment && target==scene.segment->index && !restart) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (scene.departure && clock_.frame()>=scene.departureStart);
        if(!begun) { cancelTransition(); scene.switchTarget=target; return; }
    }
    std::array<bool,4096> visited{}; last_=targetReady(target,visited);
    if(last_!=EngineStatus::Ok) { ++rejected_; return; }
    if(waiting && (target!=scene.switchTarget || restart) && yieldCueRequest(scene))waiting=false;
    if(waiting && target>=0) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (scene.departure && clock_.frame()>=scene.departureStart);
        if(begun) {
            transition_.revision=parameters_->syncRevision();
            if(target!=scene.switchTarget || restart) { queuedTransition_=target; queuedSwitch_=true; scene.switchTarget=target; }
            return;
        }
    }
    if(target>=0 && scene.segment && !scene.segmentEnded) {
        Transition probe; TransitionRun peek; preview_->reset();
        last_=prepareDestination(scene,target,probe,peek);
        if(last_!=EngineStatus::Ok) { ++rejected_; return; }
        const auto choice=transitionChoice(scene,target,peek,&sourceOwners);
        if(choice.rule) {
            last_=planTransition(scene,target,choice,true);
            if(last_==EngineStatus::Ok) scene.switchTarget=target; else ++rejected_;
            return;
        }
    }
    if(transition_.active && transition_.source==&scene) cancelTransition();
    for(auto& voice:voices_.slots()) if(voice.active && voice.owner==scene.owner) {
        voice.fadeStart=clock_.frame(); voice.fadeEnd=clock_.frame(); voice.fadeShape=Shape::Linear;
    }
    scene.switchTarget=target; scene.active=false; last_=enterTarget(scene,target);
    if(last_!=EngineStatus::Ok) { ++rejected_; scene.playlist.clear(); }
}
void EngineRuntime::updateSwitch(Scene& scene) noexcept {
    updateRootSwitch(scene);
    if(scene.active && scene.controlled)updatePlaylistSwitch(scene);
}
EngineStatus EngineRuntime::preparePlaylistSwitch(Scene& source,const PlaylistSession::SwitchBranch& leaf,const Transition& rule,TransitionRun& run) noexcept {
    auto status=prepareDestination(source,leaf.root,rule,run,true);if(status!=EngineStatus::Ok)return status;
    const auto inner=run.playlist;run.playlist=source.playlist;
    if(!run.playlist.replaceSwitchBranch(leaf,inner,run.segment))return EngineStatus::ResourceLimit;
    run.target=source.object;run.playlistObject=source.playlistObject;run.playlistSwitchChange=true;
    run.playlistSwitch=run.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
    run.playlistSwitchValues=parameters_->values();run.playlistSwitchRevision=parameters_->syncRevision();
    run.switchObject=source.switchObject;run.switchTarget=source.switchTarget;run.switchChoice=source.switchChoice;
    run.switchOwners=source.switchOwners;run.switchValues=source.switchValues;run.switchRevision=source.switchRevision;
    return EngineStatus::Ok;
}
void EngineRuntime::updatePlaylistSwitch(Scene& scene) noexcept {
    if(scene.playlistObject<0 || !scene.playlist.active() || scene.playlistSwitchRevision==parameters_->syncRevision())return;
    bool waiting=transition_.active && transition_.source==&scene;
    if(waiting && (transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted || (scene.departure && clock_.frame()>=scene.departureStart)))return;
    const auto leaf=scene.playlist.probeSwitch(*graph_,scene.playlistSwitchValues,parameters_->values());
    scene.playlistSwitchRevision=parameters_->syncRevision();
    if(leaf.limited){last_=EngineStatus::ResourceLimit;++rejected_;return;}
    if(waiting && transition_.playlistSwitchChange)cancelTransition();
    if(!leaf.changed) {
        scene.playlist.acceptSwitch(leaf);scene.playlistSwitch=leaf;scene.playlistSwitchValues=parameters_->values();return;
    }
    std::array<bool,4096> visited{};last_=targetReady(leaf.target,visited);
    if(last_!=EngineStatus::Ok){++rejected_;return;}
    if(waiting && yieldCueRequest(scene))waiting=false;
    Transition probe;TransitionRun destination;preview_->reset();
    if(leaf.target>=0) {
        last_=preparePlaylistSwitch(scene,leaf,probe,destination);
        if(last_!=EngineStatus::Ok){++rejected_;return;}
        if(destination.segment>=0 && scene.segment && !scene.segmentEnded) {
            const auto choice=transitionChoice(scene,leaf.root,destination);
            if(choice.rule){last_=planTransition(scene,leaf.root,choice,true,&leaf);if(last_!=EngineStatus::Ok)++rejected_;return;}
        }
    } else {
        destination.playlist=scene.playlist;PlaylistSession empty;
        if(!destination.playlist.replaceSwitchBranch(leaf,empty,-1)){last_=EngineStatus::ResourceLimit;++rejected_;return;}
        destination.segment=-1;
    }
    if(waiting)cancelTransition();
    for(auto& voice:voices_.slots())if(voice.active && voice.owner==scene.owner){voice.fadeStart=clock_.frame();voice.fadeEnd=clock_.frame();}
    scene.playlist=destination.playlist;
    if(destination.segment>=0) {
        last_=enterSegment(scene,destination.segment);
        if(last_!=EngineStatus::Ok){++rejected_;return;}
    } else {scene.segment=nullptr;scene.segmentEnded=true;scene.active=true;scene.controlled=true;}
    preview_->commit(selections_,clock_.frame(),journal_->epoch);
    scene.playlistSwitch=scene.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
    scene.playlistSwitchValues=parameters_->values();archivePlaylist(scene);
}
}
