#include "runtime/EngineRuntime.hpp"
#include <limits>
namespace player2 {
using api::EngineStatus;
using api::EngineCommandKind;
EngineRuntime::EngineRuntime(std::unique_ptr<Graph> graph):graph_(std::move(graph)) {
    stingerCooldown_.fill(-1);
    journal_=std::make_unique<CommandJournal>();
    if(!graph_->entries.empty() || !graph_->events.empty())actionDecisions_=std::make_unique<ActionDecisionJournal>();
    if(!graph_->stingers.empty())preparationHistory_=std::make_unique<PreparationHistory>();
    graph_->indexNodes(); rate_=graph_->sampleRate;
    for(const auto& flow:graph_->albumFlows)albumFlowsByFrom_[flow.from]=&flow;
    parameters_=std::make_unique<Parameters>(*graph_); mixer_=std::make_unique<BusMixer>(*graph_); updateMixerProperties();
    for(const auto& track:graph_->tracks) {
        selections_[track.index].reset(track.seed); tracksBySegment_[track.segment].push_back(&track); tracksByIndex_[track.index]=&track;
    }
    for(const auto& playlist:graph_->playlists) for(const auto& item:playlist.items) {
        selections_[item.index].reset(playlistItemSeed(playlist,item));
        if(item.target==-1) { playlistGroups_[item.index]=&item; playlistGroupOwners_[item.index]=playlist.index; }
    }
    preview_=std::make_unique<SelectionPreview>(selections_); sourcePreview_=std::make_unique<SelectionPreview>(selections_); lastPositions_.fill(-1);
    for(const auto& playlist:graph_->playlists) playlistArchives_.push_back({playlist.index,{}});
}
EngineRuntime::EngineRuntime(int rate):journal_(std::make_unique<CommandJournal>()),rate_(rate) {}
bool EngineRuntime::canBindSource() const noexcept {
    if(voices_.count()!=0 || transition_.active || stinger_.active)return false;
    for(const auto& scene:scenes_)if(scene.active || scene.reserved)return false;
    return true;
}
EngineStatus EngineRuntime::bindAsset(std::unique_ptr<AudioBuffer> buffer) {
    if(!canBindSource()) return EngineStatus::RuntimeActive;
    voices_.releaseInactive();
    const auto* asset=graph_ ? find(graph_->assets,buffer->index) : nullptr;
    if(!asset || !buffer->valid() || buffer->sampleRate!=asset->sampleRate || buffer->channels!=asset->channels || buffer->frames()!=asset->frames) return EngineStatus::InvalidRequest;
    auto& current=assets_[buffer->index];
    const auto old=current ? current->samples.size()*sizeof(float) : 0,bytes=buffer->samples.size()*sizeof(float);
    if(bytes>128*1024*1024-(preparedBytes_-old)) return EngineStatus::ResourceLimit;
    if(streamCache_ && current && current->stream)streamCache_->unbind(buffer->index);
    preparedBytes_=preparedBytes_-old+bytes; current=std::move(buffer); return EngineStatus::Ok;
}
EngineStatus EngineRuntime::setLinearRepeat(bool enabled,std::optional<audio::SourceLoopMetadata> range)noexcept {
    if(graph_ || !linear_)return EngineStatus::InvalidRequest;
    if(range && (range->start.value<0 || range->end.value<=range->start.value || range->end.value>linear_->frames()))return EngineStatus::InvalidRequest;
    if(linear_->stream) {
        const auto window=enabled ? (range ? range : std::optional<audio::SourceLoopMetadata>{linear_->sourceLoop.value_or(audio::SourceLoopMetadata{{0},{linear_->frames()}})}) : std::nullopt;
        const auto status=streamCache_->prepareLinearRepeat(linear_->streamedAsset,window);
        if(status!=audio::SourceStatus::Ok)return status==audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::MissingAsset;
    }
    linearLoop_=enabled;linearRepeatRange_=range;
    for(auto& voice:voices_.slots())if(voice.active)voice.repeatRange(enabled,range);
    return EngineStatus::Ok;
}
void EngineRuntime::bindLinear(std::unique_ptr<AudioBuffer> buffer,bool loop) noexcept { linear_=std::move(buffer); linearLoop_=loop; }
bool EngineRuntime::submit(const api::EngineCommand& command) noexcept {
    const int kind=static_cast<int>(command.kind);
    if(!api::validEngineCommandKind(kind) || command.at.value< -1 || !std::isfinite(command.number)) return false;
    return queue_.push(command);
}
void EngineRuntime::resetStopped() noexcept {
    albumActive_=albumEnding_=albumWaitingTail_=false;albumCurrent_=albumOwner_=-1;albumProbeInstance_=-1;
    if(albumState_.phase!=api::AlbumFlowPhase::None)albumState_.phase=api::AlbumFlowPhase::Stopped;
    musicalLoopActive_=false;if(musicalLoopState_.phase!=api::MusicalLoopPhase::None)musicalLoopState_.phase=api::MusicalLoopPhase::Stopped;
    stopAutomatic(true);
    journal_->stopped(clock_.frame(),rebuilding_);
    clearStinger(); cancelTransition(); foreground_=nullptr; queuedTransition_=-1;
    transition_=TransitionRun{};preparationGeneration_=0;
    if(futureClipPreparation_){futureClipPreparation_->retire();futureClipPreparation_->trim();}
    futureClipCount_=0;futureClipOverflow_=futureClipLimited_=false;futureClipReady_=true;futureClipResourceLimits_=0;
    voices_.clear();if(transitionPreparation_)transitionPreparation_->trim();if(stingerPreparation_)stingerPreparation_->trim(); voiceNumericalCorrections_=0; if(mixer_)mixer_->resetDiagnostics(); clock_.stop(); for(auto& scene:scenes_) { scene.active=false; scene.playlist.clear(); }
    cancelPerformance();current_=-1;
    if(mixer_) mixer_->clear();
    if(parameters_) parameters_->resetFrame();
}
EngineStatus EngineRuntime::startSegment(int target) noexcept {
    if(!graph_) {
        if(target!=-1 || !linear_) return EngineStatus::InvalidRequest;
        // Restarting linear playback resets Transport and therefore begins a new
        // command-history epoch, including replay of a restart while paused.
        if(clock_.frame()!=0 || clock_.status()!=TransportStatus::Stopped)journal_->stopped(clock_.frame(),rebuilding_);
        voices_.clear(); clock_.seek(0); Voice voice; voice.sourceLength=linear_->frames();
        voice.begin(linear_.get(),rate_); voice.repeatRange(linearLoop_,linearRepeatRange_); voices_.add(voice); clock_.play(); return EngineStatus::Ok;
    }
    const auto* object=graph_->node(target);
    if(!object || (object->kind!=Kind::Segment && object->kind!=Kind::Playlist && object->kind!=Kind::Switch)) return EngineStatus::InvalidRequest;
    Scene* scene=nullptr; for(auto& s:scenes_) if(!s.active && !s.reserved) { scene=&s; break; }
    if(!scene) return EngineStatus::VoiceLimit;
    scene->playlist.clear(); scene->object=target; scene->playlistObject=-1;
    scene->owner=static_cast<int>(++instance_ & 0x7fffffff);
    scene->switchOwners.reset(); scene->switchObject=find(graph_->switches,target) ? target : -1; scene->switchTarget=-1;
    scene->segment=nullptr; scene->segmentEnded=true; scene->active=false;
    scene->controlled=true; scene->preentry=false; scene->reserved=false;
    if(scene->switchObject>=0) {
        scene->switchValues.fill(-1); bool changed=false;
        target=resolveSceneSwitch(*scene,changed); scene->switchTarget=target;
    }
    std::array<bool,4096> visited{}; const auto ready=targetReady(target,visited);
    if(ready!=EngineStatus::Ok) return ready;
    const auto result=enterTarget(*scene,target);
    if(result==EngineStatus::Ok) { current_=scene->object; foreground_=scene; clock_.play(); }
    else { scene->playlist.clear(); scene->active=false; }
    return result;
}
EngineStatus EngineRuntime::enterTarget(Scene& scene,int target) noexcept {
    scene.playlist.clear(); scene.playlistObject=-1;scene.playlistSwitch={};
    if(target==-1) {
        scene.segment=nullptr; scene.segmentEnded=true; scene.active=scene.switchObject>=0;
        return EngineStatus::Ok;
    }
    if(const auto* playlist=find(graph_->playlists,target)) {
        scene.playlistObject=target;
        if(playlist->reset) for(const auto& item:playlist->items)
            selections_[item.index].reset(playlistItemSeed(*playlist,item));
        if(!scene.playlist.begin(*playlist)) return EngineStatus::ResourceLimit;
        target=scene.playlist.next(*graph_,selections_,parameters_->values(),clock_.frame(),journal_->epoch);
        if(target==-1) {
            if(scene.playlist.limited())return EngineStatus::ResourceLimit;
            scene.segment=nullptr;scene.segmentEnded=true;scene.active=scene.playlist.waitingSwitch();scene.controlled=true;
            scene.playlistSwitch=scene.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
            scene.playlistSwitchValues=parameters_->values();scene.playlistSwitchRevision=parameters_->syncRevision();
            return EngineStatus::Ok;
        }
    }
    return enterSegment(scene,target);
}
EngineStatus EngineRuntime::targetReady(int target,std::array<bool,4096>& visited,int depth) const noexcept {
    if(target==-1) return EngineStatus::Ok;
    if(target<0 || target>=4096 || depth>32) return EngineStatus::ResourceLimit;
    if(visited[target]) return EngineStatus::Ok;
    visited[target]=true;
    if(find(graph_->segments,target)) {
        for(const auto* track:tracksBySegment_[target]) {
            for(const auto& c:track->clips) if(!assets_[c.asset]) return EngineStatus::MissingAsset;
            for(const auto& sub:track->subTracks) for(const auto& c:sub.clips) if(!assets_[c.asset]) return EngineStatus::MissingAsset;
        }
        return EngineStatus::Ok;
    }
    if(const auto* playlist=find(graph_->playlists,target)) {
        for(const auto& item:playlist->items) if(item.target>=0) {
            const auto result=targetReady(item.target,visited,depth+1); if(result!=EngineStatus::Ok) return result;
        }
        return EngineStatus::Ok;
    }
    if(const auto* branch=find(graph_->switches,target)) return targetReady(resolveSwitch(*branch,parameters_->values()).target,visited,depth+1);
    return EngineStatus::InvalidRequest;
}

EngineStatus EngineRuntime::enterSegment(Scene& scene,int target) noexcept {
    const auto* segment=find(graph_->segments,target);
    if(!segment) return graph_->node(target) ? EngineStatus::UnsupportedExecution : EngineStatus::InvalidRequest;
    for(const auto* trackPointer:tracksBySegment_[target]) {
        const auto& track=*trackPointer;
        if(track.subTracks.size()>64 && (track.mode==TrackMode::RandomStep || track.mode==TrackMode::SequenceStep)) return EngineStatus::ResourceLimit;
        for(const auto& c:track.clips) if(!assets_[c.asset]) return EngineStatus::MissingAsset;
        for(const auto& sub:track.subTracks) for(const auto& c:sub.clips) if(!assets_[c.asset]) return EngineStatus::MissingAsset;
    }
    scene.active=true; scene.segmentEnded=false; scene.segment=segment; scene.instance=static_cast<int>(++instance_ & 0x7fffffff); scene.position=segment->entry; scene.musicalPosition=segment->entry;
    scene.preentry=false; scene.controlled=true; scene.disableLoop=false; scene.entrance=scene.departure=false; scene.stopFrame=-1;
    scene.loopCount=0; scene.clips.fill(false); scene.cues.fill(false); scene.selections.fill(-1);
    for(const auto* trackPointer:tracksBySegment_[target]) {
        const auto& track=*trackPointer;
        auto& state=selections_[track.index]; if(track.reset) state.reset(track.seed);
        if(track.mode==TrackMode::SequenceStep) scene.selections[track.index]=state.sequence(static_cast<int>(track.subTracks.size()),clock_.frame(),journal_->epoch);
        if(track.mode==TrackMode::RandomStep) {
            std::array<double,64> weights{};
            for(std::size_t i=0;i<track.subTracks.size();++i) weights[i]=track.subTracks[i].weight;
            scene.selections[track.index]=state.weighted(weights.data(),static_cast<int>(track.subTracks.size()),track.avoidRepeat,RandomMode::Standard,clock_.frame(),journal_->epoch);
        }
    }
    scene.playlistSwitch=scene.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
    scene.playlistSwitchValues=parameters_->values();scene.playlistSwitchRevision=parameters_->syncRevision();
    archivePlaylist(scene); return EngineStatus::Ok;
}
bool EngineRuntime::consumeActionWork() noexcept {
    if(actionWorkFrame_!=clock_.frame() || actionWorkEpoch_!=journal_->epoch) {
        actionWorkFrame_=clock_.frame();actionWorkEpoch_=journal_->epoch;actionWorkRemaining_=MaxActionsPerFrame;
    }
    if(actionWorkRemaining_==0) {last_=EngineStatus::ResourceLimit;++rejected_;return false;}
    --actionWorkRemaining_;return true;
}
void EngineRuntime::action(const Action& value,int depth) noexcept {
    const auto token=actionDecisions_ ? actionDecisions_->enter(value.kind,value.target) : ActionDecisionJournal::Token{};
    if(token.invalid){last_=EngineStatus::ResourceLimit;return;}
    auto perform=[&] {
        if(!consumeActionWork())return;
        if(actionDecisions_ && actionDecisions_->rejectedLeaf(token)) {
            last_=actionDecisions_->result(token);++rejected_;return;
        }
        if(value.kind==ActionKind::Stop) {
            last_=stopObject(value.target,value.number,value.retainTail);
            if(last_!=EngineStatus::Ok) ++rejected_;
            return;
        }
        api::EngineCommand command; command.kind=static_cast<EngineCommandKind>(static_cast<int>(value.kind));
        // Action IDs have a different order from public command IDs after Resume.
        if(value.kind==ActionKind::SetSync) command.kind=EngineCommandKind::SetSync;
        if(value.kind==ActionKind::SetParameter) command.kind=EngineCommandKind::SetParameter;
        if(value.kind==ActionKind::Trigger) command.kind=EngineCommandKind::Trigger;
        if(value.kind==ActionKind::Event) command.kind=EngineCommandKind::Event;
        if(value.kind==ActionKind::LayerGain) command.kind=EngineCommandKind::LayerGain;
        command.target=value.target; command.value=value.value; command.number=value.number; execute(command,depth+1);
    };
    perform();
    if(actionDecisions_)actionDecisions_->finish(token,last_);
}
void EngineRuntime::execute(const api::EngineCommand& command,int depth) noexcept {
    if(depth>32) { last_=EngineStatus::ResourceLimit; ++rejected_; return; }
    last_=EngineStatus::Ok;
    switch(command.kind) {
        case EngineCommandKind::Play:
            last_=requestPlay(command.target);
            if(last_==EngineStatus::Ok && albumActive_){albumActive_=false;albumState_.phase=api::AlbumFlowPhase::Overridden;}
            if(last_==EngineStatus::Ok && musicalLoopActive_){musicalLoopActive_=false;musicalLoopState_.phase=api::MusicalLoopPhase::Overridden;}
            break;
        case EngineCommandKind::Stop:
            if(command.target==-1) resetStopped();
            else last_=stopObject(command.target,command.number,command.value==1);
            break;
        case EngineCommandKind::Pause: clock_.pause(); break;
        case EngineCommandKind::Resume: if(clock_.status()==TransportStatus::Paused) clock_.play(); break;
        case EngineCommandKind::SetSync:
            if(!parameters_ || !parameters_->setSync(command.target,command.value)) last_=EngineStatus::InvalidRequest;
            break;
        case EngineCommandKind::SetParameter:
            if(!parameters_ || !parameters_->setParameter(command.target,command.number)) last_=EngineStatus::InvalidRequest;
            else if(automatic_ && command.target==automatic_->parameter)stopAutomatic(false,api::AutomaticPhase::Overridden);
            break;
        case EngineCommandKind::LayerGain:
            if(!parameters_ || !parameters_->setLayerGain(command.target,command.number))last_=EngineStatus::InvalidRequest;
            break;
        case EngineCommandKind::Event: {
            const auto* event=graph_ ? find(graph_->events,command.target) : nullptr;
            if(!event) last_=EngineStatus::InvalidRequest;
            else {
                auto failure=EngineStatus::Ok;
                const auto sequence=eventDecision_[1]==std::numeric_limits<std::int64_t>::max() ? eventDecision_[1] : eventDecision_[1]+1;
                eventDecision_[0]=event->index; eventDecision_[1]=sequence;
                eventDecision_[2]=-1; eventDecision_[3]=-1; eventDecision_[4]=-1; eventDecision_[5]=clock_.frame();
                eventDecision_[6]=0; eventDecision_[7]=cueContext_; eventDecision_[8]=cueSegment_;
                for(std::size_t n=0;n<event->actions.size();++n) {
                    const auto& a=event->actions[n]; action(a,depth);
                    if(last_!=EngineStatus::Ok && failure==EngineStatus::Ok) failure=last_;
                    eventDecision_[0]=event->index; eventDecision_[1]=sequence; eventDecision_[2]=n;
                    eventDecision_[3]=static_cast<int>(a.kind); eventDecision_[4]=a.target; eventDecision_[5]=clock_.frame();
                    eventDecision_[6]=static_cast<int>(last_); eventDecision_[7]=cueContext_; eventDecision_[8]=cueSegment_;
                    if(eventDecision_[9]<std::numeric_limits<std::int64_t>::max()) ++eventDecision_[9];
                    // Depth alone cannot bound a branching posted-Event tree.
                    // Keep the failed attempt in the existing trace, then stop.
                    if(last_==EngineStatus::ResourceLimit && actionWorkRemaining_==0)break;
                }
                eventDecision_[10]=static_cast<int>(failure); last_=failure;
            }
            break;
        }
        case EngineCommandKind::Performance: {
            last_=startPerformance(command.target);
            break;
        }
        case EngineCommandKind::EntryState:last_=applyEntryState(command.target,depth);break;
        case EngineCommandKind::Automatic:last_=startAutomatic(command.target);break;
        case EngineCommandKind::StopAutomatic:
            if(!automatic_ || (command.target!=-1 && command.target!=automatic_->parameter))last_=EngineStatus::InvalidRequest;
            else stopAutomatic(true);
            break;
        case EngineCommandKind::Trigger: last_=fireTrigger(command.target); break;
        case EngineCommandKind::MusicalLoop:last_=startMusicalLoop(command.target);break;
        case EngineCommandKind::AlbumFlow:last_=startAlbumFlow(command.target);break;
        case EngineCommandKind::AlbumNext:last_=nextAlbumFlow();break;
        case EngineCommandKind::StopMusicalLoop:last_=stopMusicalLoop(command.target,command.number);break;
    }
    if(last_!=EngineStatus::Ok) ++rejected_;
}
void EngineRuntime::drain() noexcept {
    while(journal_->cursor<journal_->size) {
        const auto record=journal_->records[journal_->cursor];
        if(record.epoch!=journal_->epoch || record.frame>clock_.frame()) break;
        ++journal_->cursor;
        // Rebinding after an earlier rejected Play must not turn that historical
        // failure into an additional voice. Compound inputs retain successful
        // prefixes and rejected leaf outcomes through their bounded Action tape.
        const bool compound=record.command.kind==EngineCommandKind::Event || record.command.kind==EngineCommandKind::EntryState;
        const bool valid=!actionDecisions_ || actionDecisions_->replay(record.actionsBegin,record.actionsEnd,compound);
        if(!valid)last_=EngineStatus::ResourceLimit;
        else if(record.result!=EngineStatus::Ok && !compound) {
            last_=record.result;
        } else {
            entryReplayFailure_=record.command.kind==EngineCommandKind::EntryState ? record.result : EngineStatus::Ok;
            const auto saved=rejected_; execute(record.command); rejected_=saved;
            entryReplayFailure_=EngineStatus::Ok;
        }
        if(actionDecisions_)actionDecisions_->endScope();
    }
    if(rebuilding_ && journal_->epoch!=seekEpoch_) return;
    api::EngineCommand command;
    // Reserve scheduling space before popping; bounded backlog never drops silently.
    while(pendingCount_<pending_.size() && queue_.pop(command)) {
        if(command.at.value==-1) command.at.value=clock_.frame();
        std::size_t i=pendingCount_++;
        while(i>0 && pending_[i-1].at.value>command.at.value) { pending_[i]=pending_[i-1]; --i; }
        pending_[i]=command;
    }
    const auto dueFrame=clock_.frame();
    while(pendingCount_ && pending_[0].at.value<=dueFrame) {
        command=pending_[0]; --pendingCount_;
        for(std::size_t i=0;i<pendingCount_;++i) pending_[i]=pending_[i+1];
        const auto applied=clock_.frame(); const auto epoch=journal_->epoch;
        // Pause/Resume change wall-clock execution only. At a seek boundary they
        // must not turn historical cold-page/deadline failures into warm success.
        const bool audioBranch=command.kind!=EngineCommandKind::Pause && command.kind!=EngineCommandKind::Resume;
        if(audioBranch && streamHistory_)streamHistory_->branch({epoch,{applied}});
        if(audioBranch && preparationHistory_)preparationHistory_->branch(epoch,{applied});
        const bool compound=command.kind==EngineCommandKind::Event || command.kind==EngineCommandKind::EntryState;
        if(actionDecisions_)actionDecisions_->branch(compound);
        const auto begin=actionDecisions_ ? actionDecisions_->cursor() : 0;
        execute(command);
        const auto end=actionDecisions_ ? actionDecisions_->cursor() : 0;
        if(actionDecisions_)actionDecisions_->endScope();
        journal_->append(command,last_,applied,epoch,begin,end);
    }
}
void EngineRuntime::clip(Scene& scene,const Track& track,const Clip& c) noexcept {
    if(scene.clips[c.index])return;
    if(scene.position<c.start){collectFutureClip(scene,track,c);return;}
    scene.clips[c.index]=true;
    Voice voice; voice.segment=scene.segment->index; voice.track=track.index; voice.clip=c.index; voice.bus=track.bus; voice.instance=scene.instance; voice.owner=scene.owner;
    voice.envelopeSegment=scene.segment;voice.envelopeClip=&c;voice.segmentClipStart=c.start;
    musicOwners(scene,voice.musicOwners);
    voice.startFrame=clock_.frame(); voice.sourceOffset=c.sourceOffset; voice.sourceLength=c.sourceLength;
    voice.clipLoop=c.loop;voice.gain=dbGain(c.gainDb); voice.fadeIn=c.fadeIn; voice.fadeOut=c.fadeOut;
    voice.entryFade=scene.entrance; voice.entranceStart=scene.entrance ? scene.entranceStart : -1;
    voice.entranceEnd=scene.entranceEnd; voice.entranceShape=scene.entranceShape;
    voice.exitFade=scene.departure; voice.fadeStart=scene.departure ? scene.departureStart : -1;
    voice.fadeEnd=scene.departureEnd; voice.fadeShape=scene.departureShape; voice.stopFrame=scene.stopFrame;
    voice.speed=parameters_->property(track.index,1); voice.timelineRate=parameters_->property(scene.segment->index,1);
    voice.begin(assets_[c.asset].get(),rate_,scene.position-c.start);
    if(voice.buffer && voice.buffer->stream){voice.streamCursor.bind(voice.buffer->stream,voice.buffer->streamedAsset);std::optional<audio::SourceSamplePosition> neighbor;
        if(c.loop)neighbor=audio::SourceSamplePosition{std::min(voice.buffer->frames()-1,static_cast<std::int64_t>(ClipPlayback::position(*c.loop,c.sourceOffset,std::floor(voice.sourceProgress)+1)))};
        voice.streamCursor.prepare(voice.sourcePosition,neighbor);}
    if(!voices_.add(std::move(voice))) {last_=EngineStatus::VoiceLimit;++rejected_;}
}
void EngineRuntime::schedule(Scene& scene) noexcept {
    if(scene.controlled) updateSwitch(scene);
    if(!scene.segment || scene.segmentEnded || !scene.active) return;
    const auto& segment=*scene.segment;
    if(!scheduleCues(scene)) return;
    while(!scene.preentry && !scene.disableLoop && segment.hasLoop && scene.position>=segment.loop.end && (segment.loop.count==-1 || scene.loopCount<segment.loop.count)) {
        scene.position=segment.loop.start+(scene.position-segment.loop.end); if(segment.loop.count>0) ++scene.loopCount;
        for(auto& voice:voices_.slots())if(voice.active && voice.instance==scene.instance)voice.releaseClipLoop();
        if(!segment.loop.retainTail) voices_.fadeInstance(scene.instance,clock_.frame(),clock_.frame(),Shape::Linear);
        scene.clips.fill(false);
        for(const auto& cue:segment.cues) if(cue.frame>=segment.loop.start) scene.cues[cue.index]=false;
        if(!scheduleCues(scene)) return;
    }
    if(scene.position>=segment.exit) {
        if(graph_->hasMusicalLoop && scene.object==graph_->musicalLoop.playlist && !graph_->musicalLoop.retainIntroTail && graph_->musicalLoop.intro>=0) {
            const auto* p=find(graph_->playlists,scene.object);const auto* root=p ? find(p->items,p->root) : nullptr;
            if(root && !root->children.empty() && scene.playlist.item()==root->children.front())
                voices_.fadeInstance(scene.instance,clock_.frame(),clock_.frame(),Shape::Linear);
        }
        for(auto& voice:voices_.slots())if(voice.active && voice.instance==scene.instance)voice.releaseClipLoop();
        scene.active=false; scene.segmentEnded=true;
        lastPositions_[segment.index]=Tempo::roundedFrame(scene.position);
        if(scene.playlist.active()) {
            const auto next=scene.playlist.next(*graph_,selections_,parameters_->values(),clock_.frame(),journal_->epoch);
            if(next>=0) {
                last_=enterSegment(scene,next);
                if(last_!=EngineStatus::Ok) { ++rejected_; scene.playlist.clear(); }
                // The frame dispatcher schedules this new instance exactly once.
            } else if(scene.playlist.limited()) { last_=EngineStatus::ResourceLimit; ++rejected_; scene.playlist.clear(); }
            else if(scene.playlist.waitingSwitch()) {
                scene.segment=nullptr;scene.active=true;
                scene.playlistSwitch=scene.playlist.probeSwitch(*graph_,parameters_->values(),parameters_->values());
                scene.playlistSwitchValues=parameters_->values();scene.playlistSwitchRevision=parameters_->syncRevision();
            }
        }
        if(scene.switchObject>=0 && scene.segmentEnded) scene.active=true;
        return;
    }
    for(const auto* trackPointer:tracksBySegment_[segment.index]) {
        const auto& track=*trackPointer;
        if(track.mode==TrackMode::Normal) for(const auto& c:track.clips) clip(scene,track,c);
        else {
            int selected=scene.selections[track.index];
            if(track.mode==TrackMode::Switch) {
                selected=selectSwitchSubTrack(track,parameters_->values()[track.group]);
                if(selected!=scene.selections[track.index]) {
                    for(auto& voice:voices_.slots()) if(voice.active && voice.instance==scene.instance && voice.track==track.index) voice.retireSource();
                    for(const auto& sub:track.subTracks) for(const auto& c:sub.clips) scene.clips[c.index]=false;
                    scene.selections[track.index]=selected;
                }
            }
            if(selected>=0) for(const auto& c:track.subTracks[selected].clips) clip(scene,track,c);
        }
    }
    advanceAutomaticClock(scene);
    scene.position+=parameters_->property(segment.index,1);
    scene.musicalPosition+=parameters_->property(segment.index,1);
}
void EngineRuntime::updateMixerProperties() noexcept {
    if(!mixer_ || !parameters_) return;
    for(const auto& bus:graph_->buses) {
        for(const auto& effect:bus.effects)mixer_->effectParameters(effect.index,parameters_->effectParameters(effect));
        double gain=parameters_->property(bus.index,0);
        for(const auto* ancestor=graph_->node(graph_->node(bus.index)->parent);ancestor;ancestor=graph_->node(ancestor->parent)) {
            if(ancestor->index==bus.output) { gain-=parameters_->property(ancestor->index,0); break; }
        }
        mixer_->properties(bus.index,gain,parameters_->property(bus.index,6)>=0.5,
            parameters_->propertyEnabled(bus.index,2),parameters_->property(bus.index,2),
            parameters_->propertyEnabled(bus.index,3),parameters_->property(bus.index,3),
            parameters_->propertyEnabled(bus.index,4),parameters_->property(bus.index,4));
    }
}
void EngineRuntime::render(float* output,std::size_t frames) noexcept {
    voices_.releaseInactive();
    peakLeft_=peakRight_=0; if(mixer_) mixer_->beginBlock();
    for(std::size_t frame=0;frame<frames;++frame) {
        audio::PreparationSet::FrameEnd preparationFrameEnd{transitionPreparation_.get()};
        audio::PreparationSet::FrameEnd stingerFrameEnd{stingerPreparation_.get()};
        audio::PreparationSet::FrameEnd futureClipFrameEnd{futureClipPreparation_.get()};
        futureClipCount_=0;futureClipOverflow_=false;
        if(parameters_) parameters_->beginFrame(clock_.frame());
        drain(); updateMixerProperties(); Stereo mixed;
        if(clock_.playing()) {
            advancePerformance();
            if(!clock_.playing()) { output[frame*2]=output[frame*2+1]=0; continue; }
            advanceAutomatic();
            if(parameters_) parameters_->advance(clock_.frame());
            // Game Sync commands due at a boundary must be considered before that boundary commits.
            for(auto& scene:scenes_) if(scene.active && scene.controlled) updateSwitch(scene);
            // Outgoing boundary Events execute before Transition/Stinger departure.
            // Then schedule each newly created Scene instance once at this frame,
            // including instances allocated into an earlier slot by a typed Event.
            std::array<int,16> cued; cued.fill(-1);
            auto cues=[&](Scene* scene) {
                if(!scene || !scene->active) return;
                const auto slot=static_cast<std::size_t>(scene-scenes_.data());
                if(cued[slot]==scene->instance) return;
                cued[slot]=scene->instance; scheduleCues(*scene);
            };
            cues(transition_.active ? transition_.source : nullptr);
            cues(transition_.active ? transition_.bridge : nullptr);
            cues(transition_.active ? transition_.destination : nullptr);
            for(auto& scene:scenes_) cues(&scene);
            std::array<int,16> scheduled; scheduled.fill(-1);
            for(int work=0;work<=64 && clock_.playing();++work) {
                for(auto& scene:scenes_) if(scene.active && scene.controlled) updateSwitch(scene);
                resumeCueRequest();tickTransition(); tickStinger();
                advanceAlbumFlow();
                Scene* next=nullptr; std::size_t slot=0;
                for(;slot<scenes_.size();++slot) if(scenes_[slot].active && scheduled[slot]!=scenes_[slot].instance) { next=&scenes_[slot]; break; }
                if(!next) break;
                if(work==64) {
                    last_=EngineStatus::ResourceLimit; ++rejected_;
                    for(std::size_t n=0;n<scenes_.size();++n) if(scenes_[n].active && scheduled[n]!=scenes_[n].instance) scenes_[n].active=false;
                    break;
                }
                scheduled[slot]=next->instance; schedule(*next);
            }
            if(clock_.playing())prepareFutureClips();
            advanceMusicalLoop();
            updateMixerProperties();
            if(rebuilding_ && journal_->epoch==seekEpoch_ && clock_.frame()>=seekFrame_) {
                output[frame*2]=output[frame*2+1]=0; continue;
            }
            if(graph_ && !performance_ && voices_.count()==0) {
                bool active=false; for(const auto& scene:scenes_) active|=scene.active;
                if(!active && !albumActive_ && !transition_.active && !stinger_.active && (!mixer_ || !mixer_->hasTail())) { stopAutomatic(true,api::AutomaticPhase::Finished);clock_.finish(); if(mixer_) mixer_->clear(false); }
            }
            if(!clock_.playing()) { output[frame*2]=output[frame*2+1]=0; continue; }
            if(mixer_) {
                mixer_->beginFrame();
            }
            const StreamHistory::Point availabilityAt{journal_->epoch,{clock_.frame()}};
            const auto historicalMissing=streamHistory_ && streamHistory_->known(availabilityAt) ? streamHistory_->expected(availabilityAt) : StreamHistory::Mask{};
            StreamHistory::Mask actualMissing;
            for(std::size_t slot=0;slot<voices_.slots().size();++slot) {
                auto& voice=voices_.slots()[slot];if(!voice.active)continue;
                const double gain=parameters_ ? dbGain(parameters_->property(voice.track,0)) : 1;
                const double speed=parameters_ ? parameters_->property(voice.track,1)/voice.speed : 1;
                const double pan=parameters_ ? parameters_->property(voice.track,5) : 0;
                const double timelineRate=parameters_ ? parameters_->property(voice.segment,1) : voice.timelineRate;
                const auto before=voice.numericalCorrections;
                const auto sample=voice.next(clock_.frame(),rate_,gain,speed,pan,timelineRate,
                    parameters_ && parameters_->propertyEnabled(voice.track,2),parameters_ ? parameters_->property(voice.track,2) : 20000,
                    parameters_ && parameters_->propertyEnabled(voice.track,3),parameters_ ? parameters_->property(voice.track,3) : 20,historicalMissing[slot]);
                if(voice.lastUnderrun)actualMissing.set(slot);
                countAudioCorrections(voiceNumericalCorrections_,voice.numericalCorrections-before);
                if(mixer_) {
                    mixer_->add(voice.bus,sample);
                    const auto* track=tracksByIndex_[voice.track];
                    if(track) for(const auto& send:track->sends) mixer_->add(send.bus,sample,dbGain(send.gainDb));
                } else { mixed.left=audioSample(static_cast<double>(mixed.left)+sample.left,voiceNumericalCorrections_); mixed.right=audioSample(static_cast<double>(mixed.right)+sample.right,voiceNumericalCorrections_); }
            }
            if(streamHistory_)streamHistory_->record(availabilityAt,actualMissing);
            if(mixer_) mixed=mixer_->finishFrame();
            const auto envelope=performanceEnvelope();mixed.left*=envelope;mixed.right*=envelope;
            clock_.advance(1);
            completeAlbumFlow();
            if(linear_ && voices_.count()==0) {
                clock_.finish();
            } else if(graph_ && !performance_ && voices_.count()==0) {
                bool active=false; for(const auto& scene:scenes_) active|=scene.active;
                if(!active && !albumActive_ && !transition_.active && !stinger_.active && (!mixer_ || !mixer_->hasTail())) { stopAutomatic(true,api::AutomaticPhase::Finished);clock_.finish(); if(mixer_) mixer_->clear(false); }
            }
        }
        advanceMusicalLoop();
        output[frame*2]=mixed.left; output[frame*2+1]=mixed.right;
        peakLeft_=std::max(peakLeft_,std::abs(mixed.left)); peakRight_=std::max(peakRight_,std::abs(mixed.right));
    }
}
api::EngineSnapshot EngineRuntime::snapshot() const noexcept {
    api::EngineSnapshot result{{clock_.frame()},static_cast<int>(clock_.status()),static_cast<int>(voices_.count()),current_,voices_.dropped(),rejected_,last_,peakLeft_,peakRight_};
    result.transportEpoch=journal_->epoch;result.voiceNumericalCorrections=voiceNumericalCorrections_;result.mixerNumericalCorrections=mixer_ ? mixer_->numericalCorrections() : 0;
    result.event=eventDecision_;
    result.stinger=stingerDecision_; result.stinger[0]=stinger_.active;
    result.stinger[9]=stinger_.active ? stinger_.rule->index : -1; result.stinger[10]=stinger_.active ? stinger_.rule->priority : 0;
    result.stinger[13]=stinger_.active ? stinger_.scheduled : -1;
    if(stingerDecision_[2]>=0) result.stinger[11]=stingerCooldown_[stingerDecision_[2]];
    if(transition_.rule) {
        const auto& run=transition_;
        result.transition={run.active,run.rule->index,run.sourceSegment,
            run.segment,run.rule->bridge,run.requested,run.sourceSync,run.destinationEntry,run.preStart,
            run.candidates,run.specificity,run.priority,queuedTransition_,!run.active ? 3 : run.sourceLeft ? 2 : run.destinationStarted || run.bridgeStarted ? 1 : 0};
    }
    if(foreground_) { const auto& scene=*foreground_;
        result.playingSegment=scene.segment ? scene.segment->index : -1;
        if(scene.switchObject>=0) {
            result.switchContainer=scene.switchObject; result.switchTarget=scene.switchTarget; result.switchPath=scene.switchChoice.path;
            result.switchSpecificity=scene.switchChoice.specificity; result.switchPriority=scene.switchChoice.priority;
            result.switchCandidates=scene.switchChoice.matched;
            // 0 matched, 1 fallback, 2 explicit nothing, 3 silent fallback.
            result.switchReason=scene.switchChoice.path<0 ? (scene.switchTarget<0 ? 3 : 1) : (scene.switchTarget<0 ? 2 : 0);
        }
        if(scene.switchObject<0 && scene.playlistSwitch.root>=0) {
            const auto& leaf=scene.playlistSwitch;result.switchContainer=leaf.root;result.switchTarget=leaf.target;result.switchPath=leaf.choice.path;
            result.switchSpecificity=leaf.choice.specificity;result.switchPriority=leaf.choice.priority;result.switchCandidates=leaf.choice.matched;
            result.switchReason=leaf.choice.path<0 ? (leaf.target<0 ? 3 : 1) : (leaf.target<0 ? 2 : 0);
        }
        if(scene.playlistObject>=0) {
            result.playlist=scene.playlistObject; result.playlistGroup=scene.playlist.group(); result.playlistItem=scene.playlist.item();
            if(result.playlistGroup>=0) {
                const auto& state=selections_[result.playlistGroup]; result.playlistCursor=state.cursor;
                result.playlistHistorySize=state.historySize; result.playlistRandomState=state.random;
            }
        }
    }
    return result;
}
}
