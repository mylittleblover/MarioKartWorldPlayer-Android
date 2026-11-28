#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
namespace {
void warmSeekAheadPages(
    audio::SourcePageCache* cache,
    int asset,
    std::int64_t startFrame,
    std::int64_t maxFrame,
    std::optional<audio::SourceLoopMetadata> loopRange
) noexcept {
    if(!cache || asset<0 || maxFrame<=0 || startFrame<0)return;
    constexpr std::int64_t framesPerPage=static_cast<std::int64_t>(audio::SourcePageCache::FramesPerPage);
    const std::int64_t basePage=startFrame/framesPerPage;
    bool wrapped=false;
    std::int64_t wrapOffset=0;
    // Warm 3 additional pages after the primary page (4 pages = 16,384 frames = 341ms at 48kHz)
    // to match AudioTrack's initial burst buffer and eliminate post-seek silence underruns.
    for(int ahead=1;ahead<=3;++ahead) {
        std::int64_t nextFrame=0;
        if(!wrapped) {
            nextFrame=(basePage+ahead)*framesPerPage;
            if(loopRange && nextFrame>=loopRange->end.value) {
                wrapped=true;
                wrapOffset=0;
            } else if(nextFrame>=maxFrame) {
                break;
            }
        }
        if(wrapped && loopRange) {
            const std::int64_t loopStartPage=loopRange->start.value/framesPerPage;
            nextFrame=(loopStartPage+wrapOffset)*framesPerPage;
            ++wrapOffset;
            if(nextFrame>=loopRange->end.value || nextFrame>=maxFrame)break;
        }
        static_cast<void>(cache->prepare(asset,{nextFrame}));
    }
}
}
EngineStatus EngineRuntime::seek(audio::TransportFrame target,std::uint64_t maximumReplayFrames) noexcept {
    if(target.value<0) return EngineStatus::ResourceLimit;
    // Apply next-frame inputs at the old position before capturing its epoch.
    // Future scheduled inputs remain pending and participate in forward replay.
    if(parameters_) parameters_->beginFrame(clock_.frame());
    drain();
    if(journal_->size==0) return EngineStatus::InvalidRequest;
    // A decoded-linear source has no scheduled musical/DSP state. Reposition
    // its one Voice analytically and prepare the target pages on the stopped worker.
    // Master output gain and linear fade window are independent of replay history.
    bool linearHistory=!graph_ && linear_ && !pendingCount_;
    for(std::size_t n=0;n<journal_->size && linearHistory;++n) {
        const auto& record=journal_->records[n];
        if(record.epoch!=journal_->epoch || record.result!=EngineStatus::Ok)continue;
        const auto kind=record.command.kind;
        linearHistory=kind==api::EngineCommandKind::Play || kind==api::EngineCommandKind::Pause ||
            kind==api::EngineCommandKind::Resume || kind==api::EngineCommandKind::OutputGain ||
            kind==api::EngineCommandKind::LinearFadeWindow;
    }
    if(linearHistory) {
        const auto source=static_cast<long double>(target.value)*linear_->sampleRate/rate_;
        if(linearFadeEnd_>0 && target.value>linearFadeEnd_)return EngineStatus::InvalidRequest;
        if(!linearLoop_ && source>linear_->frames())return EngineStatus::InvalidRequest;
        Voice voice;voice.sourceLength=linear_->frames();voice.begin(linear_.get(),rate_,static_cast<double>(target.value));
        voice.repeatRange(linearLoop_,linearRepeatRange_);voice.wrapSource();
        applyLinearFadeWindow(voice);
        if(linear_->stream && voice.sourcePosition<linear_->frames()) {
            streamCache_->beginControlRender();
            streamCache_->clearPendingRequests();
            voice.streamCursor.bind(linear_->stream,linear_->streamedAsset);
            const auto ready=voice.streamCursor.prepare(voice.sourcePosition);
            if(ready) {
                std::optional<audio::SourceLoopMetadata> loopMeta;
                if(voice.sourceRepeats)loopMeta=audio::SourceLoopMetadata{voice.sourceRepeatBegin,voice.sourceRepeatEnd};
                warmSeekAheadPages(linear_->stream,linear_->streamedAsset,static_cast<std::int64_t>(voice.sourcePosition),linear_->frames(),loopMeta);
            }
            streamCache_->endControlRender();
            if(!ready)return EngineStatus::MissingAsset;
        }
        const auto paused=clock_.status()==TransportStatus::Paused;
        voices_.clear();voices_.add(std::move(voice));clock_.seek(target.value);clock_.play();if(paused)clock_.pause();
        journal_->cursor=journal_->size;
        if(streamHistory_)streamHistory_->branch({journal_->epoch,target});
        return EngineStatus::Ok;
    }
    // Single-segment static clip graphs (such as OverlaidOpening, ChainedSequence, and Medleys) and
    // static linear exit-only chains (such as CourseMedleyChain across all stops 1..N) have no segment
    // loops, cues, or stateful filters, so seek can reconstruct active Voices in O(clips) instead of
    // replaying millions of 48 kHz frames through render().
    const bool singleSegmentStatic=!transition_.active && graph_ && graph_->segments.size()==1 && graph_->transitions.empty();
    bool linearExitChain=graph_ && graph_->segments.size()>=2 && graph_->segments.size()<=64 && !graph_->transitions.empty();
    std::array<bool,64> chainPostExit{};
    if(linearExitChain) {
        for(std::size_t i=0;i+1<graph_->segments.size();++i) {
            const int srcId=graph_->segments[i].index;
            const int dstId=graph_->segments[i+1].index;
            int matches=0;
            for(const auto& t:graph_->transitions) {
                const bool matchesSrc=std::find(t.sources.begin(),t.sources.end(),srcId)!=t.sources.end();
                if(!matchesSrc)continue;
                const bool matchesDst=t.destinations.size()==1 && t.destinations.front()==dstId;
                const bool zeroTimeline=!t.timeline ||
                    (t.timeline->bridgeStart==0 && t.timeline->destinationStart==0 &&
                     t.timeline->destinationCommit==0 && t.timeline->destinationSource==0);
                if(!matchesDst || t.bridge>=0 || t.preEntry || t.sync!=SyncPoint::ExitCue ||
                    t.sourceCue.has_value() || t.fadeOut.beats>0 || t.fadeIn.beats>0 || !zeroTimeline) {
                    linearExitChain=false;
                    break;
                }
                chainPostExit[i]=t.postExit;
                ++matches;
            }
            if(!linearExitChain || matches!=1){linearExitChain=false;break;}
        }
    }
    bool staticLinearGraph=graph_ && !linear_ && !pendingCount_ &&
        !performance_ && !automatic_ && !albumActive_ && !musicalLoopActive_ && !stinger_.active &&
        (singleSegmentStatic || linearExitChain) &&
        graph_->entries.empty() && !graph_->hasMusicalLoop && !graph_->hasAutomatic &&
        graph_->playlists.empty() && graph_->switches.empty() && graph_->groups.empty() &&
        graph_->stingers.empty() && graph_->performances.empty() && graph_->albumFlows.empty() &&
        graph_->events.empty() && graph_->curves.empty() && graph_->effectCurves.empty() && parameters_!=nullptr;
    std::size_t targetSegIdx=0;
    Frame targetSegOrigin=0;
    if(staticLinearGraph) {
        auto volumeOnly=[](const auto& envelopes) noexcept {
            for(const auto& e:envelopes)if(e.target()!=EnvelopeTarget::VolumeDb)return false;
            return true;
        };
        for(const auto& bus:graph_->buses) {
            if(!bus.effects.empty() || parameters_->propertyEnabled(bus.index,2) ||
               parameters_->propertyEnabled(bus.index,3) || parameters_->propertyEnabled(bus.index,4)) {
                staticLinearGraph=false;
                break;
            }
        }
        Frame cursor=0;
        bool foundSeg=false;
        for(std::size_t i=0;i<graph_->segments.size() && staticLinearGraph;++i) {
            const auto& seg=graph_->segments[i];
            if(seg.hasLoop || seg.entry!=0 || seg.exit<=0 || !seg.cues.empty() || !volumeOnly(seg.envelopes) ||
               parameters_->property(seg.index,1)!=1.0) {
                staticLinearGraph=false;
                break;
            }
            for(const auto* track:tracksBySegment_[seg.index]) {
                if(!track || track->mode!=TrackMode::Normal || !track->subTracks.empty() ||
                   parameters_->property(track->index,1)!=1.0 ||
                   parameters_->propertyEnabled(track->index,2) || parameters_->propertyEnabled(track->index,3)) {
                    staticLinearGraph=false;
                    break;
                }
                for(const auto& c:track->clips) {
                    if(c.start<0 || !volumeOnly(c.envelopes) || !assets_[c.asset] || !assets_[c.asset]->valid()) {
                        staticLinearGraph=false;
                        break;
                    }
                }
                if(!staticLinearGraph)break;
            }
            const bool isLast=i+1==graph_->segments.size();
            if(!foundSeg && (target.value<cursor+seg.exit || (isLast && target.value<=cursor+seg.exit))) {
                targetSegIdx=i;
                targetSegOrigin=cursor;
                foundSeg=true;
            }
            cursor+=seg.exit;
        }
        if(staticLinearGraph && !foundSeg)return EngineStatus::InvalidRequest;
        int playCount=0;
        const int seg0Id=graph_->segments.front().index;
        for(std::size_t n=0;n<journal_->size && staticLinearGraph;++n) {
            const auto& record=journal_->records[n];
            if(record.epoch!=journal_->epoch || record.result!=EngineStatus::Ok)continue;
            const auto kind=record.command.kind;
            if(kind==api::EngineCommandKind::Play && record.frame==0 && record.command.target==seg0Id) {
                ++playCount;
            } else if(!singleSegmentStatic &&
                (kind==api::EngineCommandKind::Play || kind==api::EngineCommandKind::CancelPendingTransition)) {
                // Prequeued/cancelled handover transitions within a linear exit-only chain keep timeline analytical.
            } else {
                staticLinearGraph=kind==api::EngineCommandKind::Pause ||
                    kind==api::EngineCommandKind::Resume || kind==api::EngineCommandKind::OutputGain;
            }
        }
        staticLinearGraph=staticLinearGraph && playCount==1;
    }
    if(staticLinearGraph) {
        const auto& seg=graph_->segments[targetSegIdx];
        const Frame localTarget=target.value-targetSegOrigin;
        if(localTarget<0 || localTarget>seg.exit)return EngineStatus::InvalidRequest;
        const bool paused=clock_.status()==TransportStatus::Paused;
        lastPositions_.fill(-1);
        for(std::size_t i=0;i<targetSegIdx;++i)lastPositions_[graph_->segments[i].index]=graph_->segments[i].exit;
        cancelTransition(false);
        transition_=TransitionRun{};queuedTransition_=-1;queuedSwitch_=false;clearStinger();
        if(transitionPreparation_){transitionPreparation_->retire();transitionPreparation_->trim();}
        if(stingerPreparation_){stingerPreparation_->retire();stingerPreparation_->trim();}
        if(futureClipPreparation_){futureClipPreparation_->retire();futureClipPreparation_->trim();}
        voices_.clear();
        for(auto& s:scenes_) { s.active=false; s.playlist.clear(); s.segment=nullptr; s.segmentEnded=true; }
        Scene& scene=scenes_[0];
        scene=Scene{};
        scene.active=true;scene.segmentEnded=false;scene.segment=&seg;
        scene.instance=static_cast<int>(++instance_&0x7fffffff);
        scene.owner=static_cast<int>(++instance_&0x7fffffff);
        scene.object=seg.index;scene.controlled=true;
        scene.position=static_cast<long double>(localTarget);
        scene.musicalPosition=static_cast<long double>(localTarget);
        foreground_=&scene;current_=seg.index;
        if(streamCache_){streamCache_->beginControlRender();streamCache_->clearPendingRequests();}
        auto reconstructClip=[&](const Segment& clipSeg,int clipInstance,int clipOwner,const Track* track,const Clip& c,Frame clipOrigin,bool markSceneClip) -> EngineStatus {
            const Frame absStart=clipOrigin+c.start;
            if(absStart>=target.value) {
                if(absStart==target.value && c.sourceLength>0 && assets_[c.asset]->stream && c.sourceOffset<assets_[c.asset]->frames()) {
                    audio::StreamCursor warm;
                    warm.bind(assets_[c.asset]->stream,assets_[c.asset]->streamedAsset);
                    if(!warm.prepare(static_cast<double>(c.sourceOffset)))return EngineStatus::MissingAsset;
                    std::optional<audio::SourceLoopMetadata> loopMeta;
                    if(c.loop)loopMeta=audio::SourceLoopMetadata{c.loop->start,c.loop->end};
                    const auto maxF=std::min(assets_[c.asset]->frames(),c.sourceOffset+c.sourceLength);
                    warmSeekAheadPages(assets_[c.asset]->stream,assets_[c.asset]->streamedAsset,c.sourceOffset,maxF,loopMeta);
                }
                return EngineStatus::Ok;
            }
            if(markSceneClip)scene.clips[c.index]=true;
            Voice voice;
            voice.segment=clipSeg.index;voice.track=track->index;voice.clip=c.index;voice.bus=track->bus;
            voice.instance=clipInstance;voice.owner=clipOwner;
            voice.envelopeSegment=&clipSeg;voice.envelopeClip=&c;voice.segmentClipStart=c.start;
            musicOwners(scene,voice.musicOwners);
            voice.startFrame=absStart;voice.sourceOffset=c.sourceOffset;voice.sourceLength=c.sourceLength;
            voice.clipLoop=c.loop;voice.gain=dbGain(c.gainDb);voice.fadeIn=c.fadeIn;voice.fadeOut=c.fadeOut;
            voice.speed=1.0;voice.timelineRate=1.0;
            voice.begin(assets_[c.asset].get(),rate_,static_cast<double>(target.value-absStart));
            const bool alive=c.loop
                ? (ClipPlayback::remaining(*c.loop,c.sourceLength,voice.sourceProgress)>0)
                : (voice.sourcePosition<static_cast<double>(c.sourceOffset+c.sourceLength));
            if(voice.active && alive && voice.sourcePosition<voice.buffer->frames()) {
                if(voice.buffer->stream) {
                    voice.streamCursor.bind(voice.buffer->stream,voice.buffer->streamedAsset);
                    std::optional<audio::SourceSamplePosition> neighbor;
                    if(c.loop)neighbor=audio::SourceSamplePosition{std::min(voice.buffer->frames()-1,static_cast<std::int64_t>(ClipPlayback::position(*c.loop,c.sourceOffset,std::floor(voice.sourceProgress)+1)))};
                    if(!voice.streamCursor.prepare(voice.sourcePosition,neighbor))return EngineStatus::MissingAsset;
                    std::optional<audio::SourceLoopMetadata> loopMeta;
                    if(c.loop)loopMeta=audio::SourceLoopMetadata{c.loop->start,c.loop->end};
                    const auto maxF=std::min(voice.buffer->frames(),c.sourceOffset+c.sourceLength);
                    warmSeekAheadPages(voice.buffer->stream,voice.buffer->streamedAsset,static_cast<std::int64_t>(voice.sourcePosition),maxF,loopMeta);
                }
                if(!voices_.add(std::move(voice)))return EngineStatus::VoiceLimit;
            }
            return EngineStatus::Ok;
        };
        // Reconstruct any post-exit tail voices still ringing from the preceding chain segment.
        if(targetSegIdx>0 && chainPostExit[targetSegIdx-1]) {
            const auto& prevSeg=graph_->segments[targetSegIdx-1];
            const Frame prevOrigin=targetSegOrigin-prevSeg.exit;
            const int prevInstance=static_cast<int>(++instance_&0x7fffffff);
            const int prevOwner=static_cast<int>(++instance_&0x7fffffff);
            for(const auto* track:tracksBySegment_[prevSeg.index]) {
                for(const auto& c:track->clips) {
                    const auto status=reconstructClip(prevSeg,prevInstance,prevOwner,track,c,prevOrigin,false);
                    if(status!=EngineStatus::Ok) {
                        if(streamCache_)streamCache_->endControlRender();
                        return status;
                    }
                }
            }
        }
        for(const auto* track:tracksBySegment_[seg.index]) {
            for(const auto& c:track->clips) {
                const auto status=reconstructClip(seg,scene.instance,scene.owner,track,c,targetSegOrigin,true);
                if(status!=EngineStatus::Ok) {
                    if(streamCache_)streamCache_->endControlRender();
                    return status;
                }
            }
        }
        if(streamCache_)streamCache_->endControlRender();
        if(mixer_)mixer_->clear();
        updateMixerProperties();
        clock_.seek(target.value);clock_.play();if(paused)clock_.pause();
        journal_->cursor=journal_->size;
        if(streamHistory_)streamHistory_->branch({journal_->epoch,target});
        if(preparationHistory_)preparationHistory_->branch(journal_->epoch,target);
        return EngineStatus::Ok;
    }
    if(static_cast<std::uint64_t>(target.value)>maximumReplayFrames) return EngineStatus::ResourceLimit;
    if(streamHistory_ && !streamHistory_->complete())return EngineStatus::ResourceLimit;
    if(preparationHistory_ && !preparationHistory_->complete())return EngineStatus::ResourceLimit;
    if(!journal_->withinBudget(static_cast<std::uint64_t>(target.value),maximumReplayFrames) ||
        (actionDecisions_ && !actionDecisions_->complete())) return EngineStatus::ResourceLimit;
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
