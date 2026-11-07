#include "runtime/EngineRuntime.hpp"
#include <limits>
namespace player2 {
using api::EngineStatus;
void EngineRuntime::archivePlaylist(const Scene& scene) noexcept {
    if(scene.playlistObject<0) return;
    for(auto& archive:playlistArchives_) if(archive.index==scene.playlistObject) { archive.session=scene.playlist; return; }
}
void EngineRuntime::cancelTransition(bool retainVoices) noexcept {
    if(!transition_.active) return;
    for(auto* scene: {transition_.destination,transition_.bridge}) if(scene) {
        for(auto& voice:voices_.slots()) if(!retainVoices && voice.active && voice.owner==scene->owner) voice.active=false;
        scene->active=false; scene->reserved=false;
    }
    if(transition_.source && !transition_.sourceLeft) {
        auto& source=*transition_.source; source.departure=false; source.stopFrame=-1;
        if(source.instance==transition_.sourceInstance) source.disableLoop=transition_.sourceLoopDisabled;
        for(auto& voice:voices_.slots()) if(voice.active && (voice.instance==source.instance || (transition_.forecast && voice.owner==source.owner && (voice.exitFade || voice.stopFrame==transition_.sourceSync)))) {
            voice.exitFade=false; voice.fadeStart=-1; voice.stopFrame=-1;
        }
    }
    if(transition_.source) transition_.source->reserved=false;
    transition_.active=false;
    if(transitionPreparation_)transitionPreparation_->retire();
}
TransitionChoice EngineRuntime::transitionChoice(const Scene& source,int target,const TransitionRun& destination,const std::bitset<4096>* sourceSwitchOwners) const noexcept {
    auto sourceOwners=sourceSwitchOwners ? *sourceSwitchOwners : source.switchOwners;
    source.playlist.musicOwners(sourceOwners);
    auto destinationOwners=destination.switchOwners;destination.playlist.musicOwners(destinationOwners);
    return chooseTransition(*graph_,source.object,source.segment ? source.segment->index : -1,target,destination.segment,
        parameters_->values(),&sourceOwners,&destinationOwners);
}
EngineStatus EngineRuntime::requestPlay(int target) noexcept {
    queuedTransition_=-1;queuedSwitch_=false;
    if(transition_.active) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (transition_.source->departure && clock_.frame()>=transition_.source->departureStart);
        if(begun) { queuedTransition_=target; queuedSwitch_=false; return EngineStatus::Ok; }
        cancelTransition();
    }
    if(!graph_ || !foreground_ || !foreground_->segment || !clock_.playing()) return startSegment(target);
    Transition probe; TransitionRun peek; preview_->reset();
    const auto ready=prepareDestination(*foreground_,target,probe,peek);
    if(ready!=EngineStatus::Ok) return ready;
    const auto choice=transitionChoice(*foreground_,target,peek);
    if(!choice.rule) return startSegment(target);
    return planTransition(*foreground_,target,choice,false);
}
EngineStatus EngineRuntime::prepareDestination(Scene& source,int target,const Transition& rule,TransitionRun& run,bool allowSilent) noexcept {
    const int object=target;
    run.switchObject=find(graph_->switches,target) ? target : -1; run.switchOwners.reset();
    run.switchValues=parameters_->values(); run.switchRevision=parameters_->syncRevision();
    for(int depth=0;depth<33;++depth) {
        const auto* branch=find(graph_->switches,target); if(!branch) break;
        run.switchOwners.set(target);
        const auto selection=resolveSwitch(*branch,parameters_->values());
        if(depth==0) run.switchChoice=selection;
        target=selection.target;
        if(depth==32) return EngineStatus::ResourceLimit;
    }
    run.switchTarget=target; run.target=object; run.playlistObject=-1;
    if(const auto* playlist=find(graph_->playlists,target)) {
        run.playlistObject=target;
        if(playlist->reset) for(const auto& item:playlist->items)
            (*preview_)[item.index].reset(playlistItemSeed(*playlist,item));
        bool restored=false;
        if(rule.entry==EntryMode::PlaylistLast) for(const auto& archive:playlistArchives_) if(archive.index==target && archive.session.lastSegment()>=0) {
            run.playlist=archive.session; restored=run.playlist.restoreLast();
            if(restored) target=run.playlist.lastSegment();
        }
        if(!restored) {
            if(rule.entry==EntryMode::PlaylistJump) {
                if(!run.playlist.jump(*playlist,rule.jumpItem,*preview_,clock_.frame(),journal_->epoch)) return EngineStatus::InvalidRequest;
            } else if(rule.entry==EntryMode::PlaylistNext) {
                bool resumed=false;
                if(source.playlistObject==playlist->index) { run.playlist=source.playlist; resumed=true; }
                else for(const auto& archive:playlistArchives_) if(archive.index==playlist->index && archive.session.lastSegment()>=0) {
                    run.playlist=archive.session; resumed=true; break;
                }
                if(!resumed && !run.playlist.begin(*playlist)) return EngineStatus::ResourceLimit;
            } else if(!run.playlist.begin(*playlist)) return EngineStatus::ResourceLimit;
            target=run.playlist.next(*graph_,*preview_,parameters_->values(),clock_.frame(),journal_->epoch);
            if(target<0) {
                if(allowSilent && run.playlist.waitingSwitch()) {
                    run.segment=-1;run.playlistSwitch=run.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
                    run.playlistSwitchValues=parameters_->values();run.playlistSwitchRevision=parameters_->syncRevision();return EngineStatus::Ok;
                }
                return run.playlist.limited() ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
            }
        }
    } else if(rule.entry==EntryMode::PlaylistLast || rule.entry==EntryMode::PlaylistNext || rule.entry==EntryMode::PlaylistJump) return EngineStatus::InvalidRequest;
    const auto* destination=find(graph_->segments,target); if(!destination) return EngineStatus::InvalidRequest;
    run.playlistSwitch=run.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
    run.playlistSwitchValues=parameters_->values();run.playlistSwitchRevision=parameters_->syncRevision();
    run.segment=target;
    const auto mode=static_cast<int>(rule.entry)>=static_cast<int>(EntryMode::PlaylistLast) ? EntryMode::EntryCue : rule.entry;
    const auto position=destinationEntryFrame(*destination,mode,source.segment ? Tempo::roundedFrame(source.position) : 0,
        lastPositions_[target],rule.destinationCue,(*preview_)[target]);
    if(!position) return EngineStatus::MissingCue;
    run.position=*position; return EngineStatus::Ok;
}
void EngineRuntime::departScene(Scene& source,const Transition& rule,Frame at,bool naturalSource) noexcept {
    const auto& tempo=graph_->node(source.segment->index)->tempo;
    const auto speed=parameters_->property(source.segment->index,1);
    source.departure=rule.fadeOut.beats>0 || rule.fadeOut.offset!=0;
    source.departureStart=addFrame(at,fadeOffset(rule.fadeOut,tempo,speed));
    source.departureEnd=addFrame(source.departureStart,fadeDuration(rule.fadeOut,tempo,speed));
    source.departureShape=rule.fadeOut.shape;
    const auto exit=sourceSyncFrame(*source.segment,tempo,source.position,speed,clock_.frame(),
        SyncPoint::ExitCue,std::nullopt,false,!source.disableLoop,
        source.musicalPosition,source.segment->hasLoop && source.segment->loop.count>=0 ? source.segment->loop.count-source.loopCount : -1,
        rule.sync==SyncPoint::ExitCue && !naturalSource);
    // An early cue does not make the rest of the source a post-exit region.
    // A configured fade may continue to its end; Voice enforces that endpoint.
    const bool reachesExit=exit && at>=*exit;
    source.stopFrame=rule.postExit && (source.departure || reachesExit) ? -1 : at;
    if(rule.sync==SyncPoint::ExitCue && !naturalSource) source.disableLoop=true;
    for(auto& voice:voices_.slots()) if(voice.active && voice.instance==source.instance) {
        voice.exitFade=source.departure; voice.fadeStart=source.departure ? source.departureStart : -1;
        voice.fadeEnd=source.departureEnd; voice.fadeShape=source.departureShape; voice.stopFrame=source.stopFrame;
    }
}
EngineStatus EngineRuntime::planTransition(Scene& source,int target,const TransitionChoice& choice,bool switchChange,const PlaylistSession::SwitchBranch* leaf,bool naturalSource,Frame destinationLead) noexcept {
    if(!choice.rule || !source.segment) return EngineStatus::InvalidRequest;
    if(transition_.active) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (transition_.source->departure && clock_.frame()>=transition_.source->departureStart);
        if(begun) { queuedTransition_=target; queuedSwitch_=switchChange; return EngineStatus::Ok; }
        cancelTransition();
    }
    std::array<bool,4096> visited{}; auto ready=targetReady(leaf ? leaf->target : target,visited);
    if(ready!=EngineStatus::Ok) return ready;
    if(choice.rule->bridge>=0) { ready=targetReady(choice.rule->bridge,visited); if(ready!=EngineStatus::Ok) return ready; }
    TransitionRun run; run.rule=choice.rule; run.source=&source; run.switchChange=switchChange;run.naturalSource=naturalSource;run.destinationLead=destinationLead;
    run.sourceSegment=source.segment->index;run.sourceSwitchTarget=source.switchTarget;
    run.candidates=choice.candidates; run.specificity=choice.specificity; run.priority=choice.priority;
    preview_->reset(); ready=leaf ? preparePlaylistSwitch(source,*leaf,*run.rule,run) : prepareDestination(source,target,*run.rule,run); if(ready!=EngineStatus::Ok) return ready;
    if(switchChange && !leaf) {
        run.switchObject=source.switchObject; run.switchTarget=target; run.switchOwners=source.switchOwners;
        run.switchChoice=source.switchChoice; run.switchValues=source.switchValues; run.switchRevision=source.switchRevision;
    }
    run.requested=clock_.frame();
    run.revision=parameters_->syncRevision();
    const auto speed=parameters_->property(source.segment->index,1);
    long double musicalDistance=0;
    auto sync=sourceSyncFrame(*source.segment,graph_->node(source.segment->index)->tempo,source.position,speed,
        clock_.frame(),run.rule->sync,run.rule->sourceCue,cueSearchReplan_,
        !source.disableLoop && (!source.segment->hasLoop || source.segment->loop.count<0 || source.loopCount<source.segment->loop.count),source.musicalPosition,
        source.segment->hasLoop && source.segment->loop.count>=0 ? source.segment->loop.count-source.loopCount : -1,!naturalSource,&musicalDistance);
    if(!sync) {
        const bool cueRule=run.rule->sync==SyncPoint::NextCue || run.rule->sync==SyncPoint::NextCustomCue;
        if(!cueRule || !source.playlist.active() || (source.segment->hasLoop && !source.disableLoop && source.segment->loop.count<0)) return EngineStatus::MissingCue;
        if(!cueSearchEnabled_) return EngineStatus::UnsupportedExecution;
        // Negative-offset fades need musical lookahead before the unknown cue.
        // Pre-entry can wait and use the normal deadline once a cue is known.
        if(naturalSource)
            return EngineStatus::UnsupportedExecution;
        // Source Switch changes yield this external intent to the existing
        // source transition, then resume it on the real selected source path.
        const bool needsAdvance=run.rule->preEntry || run.rule->fadeOut.offset<0 || run.rule->fadeIn.offset<0;
        if(needsAdvance) {
            run.forecastPlan=forecastSourceCue(source,*run.rule);
            if(run.forecastPlan.outcome==CueForecast::Outcome::Found) {
                run.forecast=true;sync=run.forecastPlan.sync;
            } else if(run.rule->fadeOut.offset<0 || run.rule->fadeIn.offset<0) {
                return run.forecastPlan.outcome==CueForecast::Outcome::Limited ? EngineStatus::ResourceLimit : EngineStatus::UnsupportedExecution;
            }
        }
        if(!sync) {
            // Keep the selected destination/bridge start pages reserved while the
            // source follows its real playlist. No deadline exists until a cue is
            // selected, so ready() requests work without promising timely output.
            ready=prepareTransitionPages(run);if(ready!=EngineStatus::Ok)return ready;
            if(transitionPreparation_)transitionPreparation_->ready();
            run.active=true;run.cueSearch=true;run.sourceSync=run.destinationEntry=run.preStart=run.bridgeStart=-1;run.sourceInstance=source.instance;run.sourceLoopDisabled=source.disableLoop;
            transition_=run;source.reserved=true;return EngineStatus::Ok;
        }
    }
    run.sourceBoundary=source.musicalPosition+musicalDistance;
    run.sourceInstance=source.instance;run.sourceLoopDisabled=source.disableLoop;
    run.sourceSpeed=speed;run.destinationSpeed=parameters_->property(run.segment,1);
    run.bridgeSpeed=run.rule->bridge>=0 ? parameters_->property(run.rule->bridge,1) : 1;
    run.sourceSync=*sync; run.destinationEntry=addFrame(*sync,-destinationLead); run.bridgeStart=*sync;
    run.position=projectSameTime(run,run.requested,speed,run.sourceSync);
    const auto destinationSpeed=parameters_->property(run.segment,1);
    if(run.rule->bridge>=0) {
        const auto* bridge=find(graph_->segments,run.rule->bridge);
        if(bridge->hasLoop && bridge->loop.count<0) return EngineStatus::InvalidRequest;
        long double duration=static_cast<long double>(bridge->exit)-bridge->entry;
        if(bridge->hasLoop) duration+=(static_cast<long double>(bridge->loop.end)-bridge->loop.start)*bridge->loop.count;
        run.destinationEntry=addFrame(*sync,Tempo::roundedFrame(std::ceil(duration/parameters_->property(bridge->index,1))));
        if(run.rule->preEntry) run.bridgeStart=addFrame(*sync,-Tempo::roundedFrame(bridge->entry/parameters_->property(bridge->index,1)));
    }
    run.preStart=run.rule->preEntry ? addFrame(run.destinationEntry,-Tempo::roundedFrame(run.position/destinationSpeed)) : run.destinationEntry;
    if(run.preStart<clock_.frame() || run.bridgeStart<clock_.frame()) return EngineStatus::DeadlineMiss;
    if(run.forecast) {
        const auto* cueNode=graph_->node(run.forecastPlan.segment);
        if(addFrame(run.sourceSync,fadeOffset(run.rule->fadeOut,cueNode->tempo,parameters_->property(cueNode->index,1)))<clock_.frame())return EngineStatus::DeadlineMiss;
    }
    for(auto& scene:scenes_) if(!scene.active && !scene.reserved && &scene!=&source) {
        if(!run.destination) run.destination=&scene;
        else if(run.rule->bridge>=0) { run.bridge=&scene; break; }
    }
    if(!run.destination || (run.rule->bridge>=0 && !run.bridge)) return EngineStatus::VoiceLimit;
    ready=prepareTransitionPages(run);if(ready!=EngineStatus::Ok)return ready;
    if(preparationGeneration_==std::numeric_limits<std::uint64_t>::max())return EngineStatus::ResourceLimit;
    run.preparationGeneration=++preparationGeneration_;
    if(transitionPreparation_)transitionPreparation_->ready();
    run.active=true; transition_=run;
    source.reserved=true;
    run.destination->reserved=true; if(run.bridge) run.bridge->reserved=true;
    if(run.forecast)departForecastScene(transition_);else departScene(source,*run.rule,run.sourceSync,run.naturalSource); return EngineStatus::Ok;
}
Frame EngineRuntime::projectSameTime(const TransitionRun& run,Frame now,double speed,Frame sync) const noexcept {
    if(run.rule->entry!=EntryMode::SameTime) return run.position;
    const auto& source=*run.source;
    if(run.forecast) {
        const auto* destination=find(graph_->segments,run.segment);
        return std::clamp(run.forecastPlan.position,destination->entry,destination->exit-1);
    }
    long double local=source.position+(static_cast<long double>(sync)-now)*speed;
    if((run.rule->sync!=SyncPoint::ExitCue || run.naturalSource) && source.segment->hasLoop && !source.disableLoop) {
        const auto& loop=source.segment->loop;
        const long double span=static_cast<long double>(loop.end)-loop.start;
        if(local>=loop.end && (loop.count<0 || source.loopCount<loop.count)) {
            auto repeats=std::floor((local-loop.end)/span)+1;
            if(loop.count>=0) repeats=std::min(repeats,static_cast<long double>(loop.count-source.loopCount));
            local-=repeats*span;
        }
    }
    const auto* destination=find(graph_->segments,run.segment);
    return std::clamp(Tempo::roundedFrame(local),destination->entry,destination->exit-1);
}
bool EngineRuntime::retimeWaitingTransition() noexcept {
    auto& run=transition_; const auto now=clock_.frame();
    if(!run.active) return false;
    if(run.forecast)return refreshCueForecast();
    // Once audio has started, that phase needs its own remaining-music-time policy.
    if(run.sourceLeft || run.destinationStarted || run.bridgeStarted ||
        (run.source->departure && now>run.source->departureStart)) return true;
    if(run.source->instance!=run.sourceInstance) {
        if(run.naturalSource && albumActive_) {cancelTransition();albumProbeInstance_=-1;albumState_.phase=api::AlbumFlowPhase::Playing;return false;}
        if(run.playlistSwitchChange) {
            auto* source=run.source;cancelTransition();source->playlistSwitchRevision=parameters_->syncRevision()-1;
            updatePlaylistSwitch(*source);return transition_.active;
        }
        const auto target=run.target;
        last_=requestPlay(target);
        if(last_!=EngineStatus::Ok) ++rejected_;
        return transition_.active;
    }
    const double sourceSpeed=parameters_->property(run.sourceSegment,1),destinationSpeed=parameters_->property(run.segment,1);
    const double bridgeSpeed=run.rule->bridge>=0 ? parameters_->property(run.rule->bridge,1) : 1;
    if(sourceSpeed==run.sourceSpeed && destinationSpeed==run.destinationSpeed && bridgeSpeed==run.bridgeSpeed) return true;
    // Album duration is authored in seconds/Transport frames. Pending speed
    // changes must not shrink it merely because standard Fade uses beats.
    if(run.albumEdge && run.rule==&albumRule_ && albumState_.mode==static_cast<int>(AlbumFlowMode::Crossfade)) {
        const auto* flow=albumFlowsByFrom_[albumState_.from];
        albumRule_.fadeOut.beats=flow->duration.value*sourceSpeed/graph_->node(run.sourceSegment)->tempo.framesPerBeat();
        albumRule_.fadeIn.beats=flow->duration.value*destinationSpeed/graph_->node(run.segment)->tempo.framesPerBeat();
        if(run.naturalSource)albumRule_.fadeOut.offset=-albumRule_.fadeOut.beats;
    }
    const auto sync=addFrame(now,Tempo::roundedFrame(std::ceil(std::max(0.0L,run.sourceBoundary-run.source->musicalPosition)/sourceSpeed)));
    Frame destinationEntry=addFrame(sync,-run.destinationLead),bridgeStart=sync;
    const auto position=projectSameTime(run,now,sourceSpeed,sync);
    if(run.rule->bridge>=0) {
        const auto* bridge=find(graph_->segments,run.rule->bridge);
        long double duration=static_cast<long double>(bridge->exit)-bridge->entry;
        if(bridge->hasLoop) duration+=(static_cast<long double>(bridge->loop.end)-bridge->loop.start)*bridge->loop.count;
        destinationEntry=addFrame(sync,Tempo::roundedFrame(std::ceil(duration/bridgeSpeed)));
        if(run.rule->preEntry) bridgeStart=addFrame(sync,-Tempo::roundedFrame(bridge->entry/bridgeSpeed));
    }
    const auto preStart=run.rule->preEntry ? addFrame(destinationEntry,-Tempo::roundedFrame(position/destinationSpeed)) : destinationEntry;
    if(preStart<now || bridgeStart<now) {
        last_=EngineStatus::DeadlineMiss; ++rejected_; cancelTransition(); return false;
    }
    run.sourceSync=sync;run.destinationEntry=destinationEntry;run.bridgeStart=bridgeStart;run.preStart=preStart;run.position=position;
    if(run.albumEdge)albumState_.scheduledAt={destinationEntry};
    run.sourceSpeed=sourceSpeed;run.destinationSpeed=destinationSpeed;run.bridgeSpeed=bridgeSpeed;
    departScene(*run.source,*run.rule,sync,run.naturalSource);
    return true;
}
void EngineRuntime::tickTransition() noexcept {
    if(!transition_.active) return;
    if(transition_.cueSearch) {
        auto* source=transition_.source;
        if(!source->active || !source->segment) {
            cancelTransition();last_=EngineStatus::MissingCue;++rejected_;return;
        }
        if(source->instance!=transition_.sourceInstance || transition_.revision!=parameters_->syncRevision()) {
            replanCueTransition(source->instance!=transition_.sourceInstance);
        } else {
            const auto ready=prepareTransitionPages(transition_);
            if(ready!=EngineStatus::Ok) {cancelTransition();last_=ready;++rejected_;return;}
            if(transitionPreparation_)transitionPreparation_->ready();
        }
        return;
    }
    if(!transition_.switchChange && transition_.revision!=parameters_->syncRevision()) {
        const bool begun=transition_.sourceLeft || transition_.destinationStarted || transition_.bridgeStarted ||
            (transition_.source->departure && clock_.frame()>=transition_.source->departureStart);
        if(!begun) {
            if(transition_.naturalSource && albumActive_) {cancelTransition();albumProbeInstance_=-1;return;}
            const int target=transition_.target; last_=requestPlay(target); if(!transition_.active) return;
        }
        else transition_.revision=parameters_->syncRevision();
    }
    if(!retimeWaitingTransition() || !transitionPagesReady()) return;
    auto& run=transition_; const auto now=clock_.frame();
    auto start=[&](Scene& scene,int object,int segment,Frame position,bool preentry) {
        scene.object=object; scene.owner=static_cast<int>(++instance_ & 0x7fffffff); scene.switchObject=-1; scene.switchOwners.reset();
        scene.playlistObject=-1; scene.playlist.clear(); scene.reserved=false;
        const auto result=enterSegment(scene,segment); if(result!=EngineStatus::Ok) return result;
        scene.position=position; scene.musicalPosition=position; scene.preentry=preentry; scene.controlled=false; scene.reserved=true; return EngineStatus::Ok;
    };
    if(!run.bridgeStarted && run.bridge && now>=run.bridgeStart) {
        const auto* segment=find(graph_->segments,run.rule->bridge);
        last_=start(*run.bridge,run.switchChange ? run.source->object : run.target,run.rule->bridge,run.rule->preEntry ? 0 : segment->entry,run.rule->preEntry);
        if(last_!=EngineStatus::Ok) { ++rejected_; cancelTransition(); return; }
        run.bridge->switchOwners=run.switchOwners; run.bridge->playlist=run.playlist;
        run.bridge->stopFrame=run.rule->postExit ? -1 : run.destinationEntry;
        run.bridgeStarted=true;
    }
    if(!run.destinationStarted && now>=run.preStart) {
        auto& destination=*run.destination;
        last_=start(destination,run.switchChange ? run.source->object : run.target,run.segment,run.rule->preEntry ? 0 : run.position,run.rule->preEntry);
        if(last_!=EngineStatus::Ok) { ++rejected_; cancelTransition(); return; }
        destination.playlistObject=run.playlistObject; destination.playlist=run.playlist;
        destination.playlistSwitch=run.playlistSwitch;destination.playlistSwitchValues=run.playlistSwitchValues;destination.playlistSwitchRevision=run.playlistSwitchRevision;
        if(destination.segment->hasLoop && run.position>=destination.segment->loop.end) destination.disableLoop=true;
        // Keep the choice used to prepare this destination, rather than relabeling
        // an already-started segment with later Game Sync values.
        destination.switchObject=run.switchObject; destination.switchTarget=run.switchTarget;
        destination.switchChoice=run.switchChoice; destination.switchOwners=run.switchOwners;
        destination.switchValues=run.switchValues; destination.switchRevision=run.switchRevision;
        const auto& tempo=graph_->node(run.segment)->tempo; const auto speed=parameters_->property(run.segment,1);
        destination.entrance=run.rule->fadeIn.beats>0 || run.rule->fadeIn.offset!=0;
        destination.entranceStart=addFrame(run.destinationEntry,fadeOffset(run.rule->fadeIn,tempo,speed));
        destination.entranceEnd=addFrame(destination.entranceStart,fadeDuration(run.rule->fadeIn,tempo,speed)); destination.entranceShape=run.rule->fadeIn.shape;
        run.destinationStarted=true;
    }
    if(!run.sourceLeft && now>=run.sourceSync) {
        lastPositions_[run.source->segment->index]=Tempo::roundedFrame(run.source->position);
        for(auto& voice:voices_.slots())if(voice.active && voice.instance==run.source->instance)voice.releaseClipLoop();
        run.source->active=false; run.source->controlled=false; run.sourceLeft=true;
        if(run.bridge) run.bridge->preentry=false;
    }
    if(now>=run.destinationEntry && !run.destinationCommitted) {
        if(run.bridge) { for(auto& voice:voices_.slots())if(voice.active && voice.instance==run.bridge->instance)voice.releaseClipLoop(); run.bridge->active=false; lastPositions_[run.bridge->segment->index]=Tempo::roundedFrame(run.bridge->position); }
        run.destination->preentry=false; run.destination->controlled=true;
        foreground_=run.destination; current_=run.destination->object; preview_->commit(selections_,clock_.frame(),journal_->epoch); archivePlaylist(*run.destination);
        if(run.albumEdge && albumActive_)updateAlbumCurrent(true);
        run.destination->reserved=false; if(run.bridge) run.bridge->reserved=false;
        run.destinationCommitted=true;
    }
    // An Album crossfade can enter before natural source exit. Keep the
    // source and rule alive, and commit destination selection only once.
    if(run.destinationCommitted && run.sourceLeft) {
        run.source->reserved=false;
        run.active=false;
        if(transitionPreparation_)transitionPreparation_->retire();
        if(queuedTransition_>=0) {
            const auto target=queuedTransition_; const bool switchChange=queuedSwitch_; queuedTransition_=-1;
            if(switchChange && foreground_->switchObject>=0) { foreground_->switchRevision=parameters_->syncRevision()-1; updateSwitch(*foreground_); }
            else last_=requestPlay(target);
        }
    }
}
}
