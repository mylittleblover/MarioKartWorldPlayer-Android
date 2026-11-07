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
                required[count++]={buffer->stream,buffer->streamedAsset,{whole},static_cast<double>(sample-whole),neighbor};return true;
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
EngineStatus EngineRuntime::prepareTransitionPages(const TransitionRun& run) noexcept {
    if(!transitionPreparation_)return EngineStatus::Ok;
    std::array<audio::PreparationSet::Requirement,128> required{};std::size_t count=0;
    if(!collectPreparationPages(run.segment,run.rule->preEntry ? 0 : run.position,required,count))return EngineStatus::ResourceLimit;
    if(run.rule->bridge>=0) {
        const auto* bridge=find(graph_->segments,run.rule->bridge);
        if(!collectPreparationPages(bridge->index,run.rule->preEntry ? 0 : bridge->entry,required,count))return EngineStatus::ResourceLimit;
    }
    return transitionPreparation_->configure(required.data(),count) ? EngineStatus::Ok : EngineStatus::ResourceLimit;
}
bool EngineRuntime::transitionPagesReady() noexcept {
    auto& run=transition_;
    if(!transitionPreparation_ || run.preparationDecided)return true;
    auto result=prepareTransitionPages(run);
    const auto now=clock_.frame();
    const auto deadline=std::max(run.requested,std::min({run.sourceSync,run.preStart,run.bridge ? run.bridgeStart : run.preStart,
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
