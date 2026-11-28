#include "runtime/EngineRuntime.hpp"
namespace player2 {
using api::EngineStatus;
bool EngineRuntime::collectPreparationPages(int index,Frame position,std::array<audio::PreparationSet::Requirement,128>& required,std::size_t& count) noexcept {
        const double timelineRate=parameters_->property(index,1);
        for(const auto* pointer:tracksBySegment_[index]) {
            const auto& track=*pointer;
            const auto append=[&](const Clip& clip) {
                const auto* buffer=assets_[clip.asset].get();if(!buffer || !buffer->stream || clip.start>position)return true;
                const long double catchup=static_cast<long double>(position)-clip.start;
                const long double progress=static_cast<long double>(catchup)*parameters_->property(track.index,1)/timelineRate*buffer->sampleRate/rate_;
                if(clip.loop && ClipPlayback::remaining(*clip.loop,clip.sourceLength,progress)<=0)return true;
                const long double sample=clip.loop ? ClipPlayback::position(*clip.loop,clip.sourceOffset,progress) : clip.sourceOffset+catchup*parameters_->property(track.index,1)/timelineRate*buffer->sampleRate/rate_;
                if(sample>=static_cast<double>(clip.sourceOffset)+clip.sourceLength || sample>=buffer->frames())return true;
                if(!std::isfinite(sample) || sample<0 || count==required.size())return false;
                const auto whole=static_cast<std::int64_t>(sample);
                std::optional<audio::SourceSamplePosition> neighbor;
                if(clip.loop)neighbor=audio::SourceSamplePosition{std::min(buffer->frames()-1,static_cast<std::int64_t>(ClipPlayback::position(*clip.loop,clip.sourceOffset,std::floor(progress)+1)))};
                required[count++]={buffer->stream,buffer->streamedAsset,{whole},static_cast<double>(sample-whole),neighbor};
                // Prefetch up to 7 additional ahead pages (~682ms total at 48kHz) so streamed openings do not underrun on entry.
                const auto framesPerPage=static_cast<std::int64_t>(audio::SourcePageCache::FramesPerPage);
                const auto basePage=whole/framesPerPage;
                const auto maxPos=std::min(buffer->frames()-1,clip.sourceOffset+clip.sourceLength-1);
                bool wrappedToLoopStart=false;
                std::int64_t wrapOffset=0;
                for(int ahead=1;ahead<8 && count+4<required.size();++ahead) {
                    if(!wrappedToLoopStart) {
                        const auto nextFrame=(basePage+ahead)*framesPerPage;
                        if(clip.loop && nextFrame>=clip.loop->end.value) {
                            wrappedToLoopStart=true;
                            wrapOffset=0;
                        } else if(nextFrame>maxPos) {
                            break;
                        } else {
                            required[count++]={buffer->stream,buffer->streamedAsset,{nextFrame},0.0,std::nullopt};
                            continue;
                        }
                    }
                    if(wrappedToLoopStart && clip.loop) {
                        const auto loopStartPage=clip.loop->start.value/framesPerPage;
                        const auto loopFrame=(loopStartPage+wrapOffset)*framesPerPage;
                        ++wrapOffset;
                        if(loopFrame>=clip.loop->end.value || loopFrame>maxPos)break;
                        required[count++]={buffer->stream,buffer->streamedAsset,{loopFrame},0.0,std::nullopt};
                    }
                }
                return true;
            };
            const int selected=track.mode==TrackMode::Switch ? selectSwitchSubTrack(track,parameters_->values()[track.group]) : -1;
            if(track.mode==TrackMode::Normal){for(const auto& clip:track.clips)if(!append(clip))return false;}
            else for(std::size_t n=0;n<track.subTracks.size();++n) {
                if(track.mode==TrackMode::Switch && static_cast<int>(n)!=selected)continue;
                const auto& sub=track.subTracks[n];
                // Preparing random/sequence candidates must not consume selection state.
                for(const auto& clip:sub.clips)if(!append(clip))return false;
            }
        }
        return true;
    }
EngineStatus EngineRuntime::prepareTransitionPages(TransitionRun& run) noexcept {
    if(!transitionPreparation_)return EngineStatus::Ok;
    const auto syncRev=parameters_ ? parameters_->syncRevision() : 0;
    const auto paramRev=parameters_ ? parameters_->parameterRevision() : 0;
    if(run.preparedPagesValid && run.preparedRule==run.rule && run.preparedRevision==run.revision && run.preparedSegment==run.segment && run.preparedPosition==run.position && run.preparedBridgeStarted==run.bridgeStarted && run.preparedDestStarted==run.destinationStarted && run.preparedSyncRev==syncRev && run.preparedParamRev==paramRev) return EngineStatus::Ok;
    std::array<audio::PreparationSet::Requirement,128> required{};std::size_t count=0;
    std::size_t bridgeInputs=0;
    if(run.rule->bridge>=0 && !run.bridgeStarted) {
        const auto* bridge=find(graph_->segments,run.rule->bridge);
        const Frame bridgePosition=run.rule->timeline ? 0 : (run.rule->preEntry ? 0 : bridge->entry);
        if(!collectPreparationPages(bridge->index,bridgePosition,required,count))return EngineStatus::ResourceLimit;
        bridgeInputs=count;
    }
    if(!run.destinationStarted) {
        const Frame destinationPosition=run.rule->timeline ? run.rule->timeline->destinationSource : (run.rule->preEntry ? 0 : run.position);
        if(!collectPreparationPages(run.segment,destinationPosition,required,count))return EngineStatus::ResourceLimit;
    }
    run.bridgePreparationInputCount=bridgeInputs;
    if(!transitionPreparation_->configure(required.data(),count)) return EngineStatus::ResourceLimit;
    run.preparedPagesValid=true; run.preparedRule=run.rule; run.preparedRevision=run.revision; run.preparedSegment=run.segment; run.preparedPosition=run.position; run.preparedBridgeStarted=run.bridgeStarted; run.preparedDestStarted=run.destinationStarted; run.preparedSyncRev=syncRev; run.preparedParamRev=paramRev;
    return EngineStatus::Ok;
}
bool EngineRuntime::transitionPagesReady() noexcept {
    auto& run=transition_;
    if(!transitionPreparation_ || run.preparationDecided)return true;
    const bool staggered=run.rule->bridge>=0 && run.preStart>run.bridgeStart && run.bridgePreparationGeneration!=0;
    if(staggered && !run.bridgeStarted && !run.bridgePreparationDecided) {
        auto result=prepareTransitionPages(run);
        const auto now=clock_.frame();
        const auto deadline=std::max(run.requested,std::min({run.sourceSync,run.bridgeStart,
            run.source->departure ? run.source->departureStart : run.sourceSync}));
        const bool allReady=result==EngineStatus::Ok && transitionPreparation_->ready();
        const bool bridgeReady=result==EngineStatus::Ok && (allReady || transitionPreparation_->readyRange(0,run.bridgePreparationInputCount));
        if(now<deadline && result==EngineStatus::Ok)return true;
        const bool needsHistory=run.bridgePreparationInputCount!=0 || result!=EngineStatus::Ok;
        if(result==EngineStatus::Ok && !bridgeReady)result=EngineStatus::DeadlineMiss;
        bool historicalDecision=false;
        result=recordPreparationDecision(run.bridgePreparationGeneration,result,needsHistory,historicalDecision);
        run.bridgePreparationDecided=true;
        if(result==EngineStatus::Ok)return true;
        last_=result;if(!historicalDecision)++rejected_;cancelTransition();return false;
    }
    auto result=prepareTransitionPages(run);
    const auto now=clock_.frame();
    const auto deadline=staggered
        ? std::max(run.requested,run.preStart)
        : std::max(run.requested,std::min({run.sourceSync,run.preStart,run.bridge ? run.bridgeStart : run.preStart,
            run.source->departure ? run.source->departureStart : run.sourceSync}));
    const bool ready=result==EngineStatus::Ok && transitionPreparation_->ready();
    if(now<deadline && result==EngineStatus::Ok)return true;
    const bool needsHistory=transitionPreparation_->size()!=0 || result!=EngineStatus::Ok;
    if(result==EngineStatus::Ok && !ready)result=EngineStatus::DeadlineMiss;
    bool historicalDecision=false;
    result=recordPreparationDecision(run.preparationGeneration,result,needsHistory,historicalDecision);
    run.preparationDecided=true;
    if(result==EngineStatus::Ok)return true;
    last_=result;if(!historicalDecision)++rejected_;cancelTransition();return false;
}
EngineStatus EngineRuntime::recordPreparationDecision(std::uint64_t generation,EngineStatus result,bool needsHistory,bool& historical) noexcept {
    if(needsHistory && preparationHistory_) {
        const PreparationHistory::Key key{journal_->epoch,generation,{clock_.frame()}};
        if(const auto expected=preparationHistory_->expected(key)){result=*expected;historical=preparationHistory_->complete();}
        if(!preparationHistory_->complete() || !preparationHistory_->record(key,result))result=EngineStatus::ResourceLimit;
    }
    return result;
}
EngineStatus EngineRuntime::prepareStingerPages(const StingerRun& run) noexcept {
    if(!stingerPreparation_)return EngineStatus::Ok;
    std::array<audio::PreparationSet::Requirement,128> required{};std::size_t count=0;
    if(!collectPreparationPages(run.rule->segment,0,required,count))return EngineStatus::ResourceLimit;
    return stingerPreparation_->configure(required.data(),count) ? EngineStatus::Ok : EngineStatus::ResourceLimit;
}
bool EngineRuntime::stingerPagesReady() noexcept {
    auto& run=stinger_;if(!stingerPreparation_ || run.preparationDecided)return true;
    auto result=prepareStingerPages(run);const bool ready=result==EngineStatus::Ok && stingerPreparation_->ready();
    if(clock_.frame()<run.preparation && result==EngineStatus::Ok)return true;
    const bool needsHistory=stingerPreparation_->size()!=0 || result!=EngineStatus::Ok;
    if(result==EngineStatus::Ok && !ready)result=EngineStatus::DeadlineMiss;
    bool historical=false;result=recordPreparationDecision(run.preparationGeneration,result,needsHistory,historical);run.preparationDecided=true;
    if(result==EngineStatus::Ok)return true;
    last_=result;if(!historical)++rejected_;run.scene->reserved=false;run.active=false;
    stingerDecision_[8]=static_cast<std::int64_t>(api::StingerDecisionReason::Failed);stingerPreparation_->retire();return false;
}
api::EngineStatus EngineRuntime::pauseDecodeWorkerHarness(bool pause) noexcept {
    if(!streamCache_)return EngineStatus::InvalidRequest;
    if(pause)streamCache_->pauseWorker();else streamCache_->resumeWorker();return EngineStatus::Ok;
}
}
