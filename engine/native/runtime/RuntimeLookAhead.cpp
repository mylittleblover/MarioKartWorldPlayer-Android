#include "runtime/EngineRuntime.hpp"
#include <limits>
namespace player2 {
void EngineRuntime::collectFutureClip(const Scene& scene,const Track& track,const Clip& clip) noexcept {
    if(!futureClipPreparation_)return;
    const auto* buffer=assets_[clip.asset].get();const auto* asset=find(graph_->assets,clip.asset);
    if(!buffer || !buffer->stream || !asset || asset->sourceLookAhead().value<=0)return;
    // lookAhead is an original Source-sample count. Scene positions are musical
    // Transport-rate frames; resolved Track speed determines Source consumption.
    const long double transportLeadFrames=(static_cast<long double>(clip.start)-scene.position)/parameters_->property(scene.segment->index,1);
    const long double sourceLeadSamples=transportLeadFrames*parameters_->property(track.index,1)*buffer->sampleRate/rate_;
    if(sourceLeadSamples>asset->sourceLookAhead().value)return;
    const auto page=clip.sourceOffset/static_cast<std::int64_t>(audio::SourcePageCache::FramesPerPage);
    for(std::size_t n=0;n<futureClipCount_;++n) {
        const auto& old=futureClipRequirements_[n];
        if(old.cache==buffer->stream && old.asset==buffer->streamedAsset && old.sample.value/static_cast<std::int64_t>(audio::SourcePageCache::FramesPerPage)==page)return;
    }
    if(futureClipCount_==futureClipRequirements_.size()){futureClipOverflow_=true;return;}
    futureClipRequirements_[futureClipCount_++]={buffer->stream,buffer->streamedAsset,{clip.sourceOffset},0,std::nullopt};
}
void EngineRuntime::prepareFutureClips() noexcept {
    if(!futureClipPreparation_)return;
    if(!futureClipOverflow_ && futureClipCount_==0 && futureClipPreparation_->size()==0) {
        futureClipLimited_=false;futureClipReady_=true;return;
    }
    const bool configured=!futureClipOverflow_ && futureClipPreparation_->configure(futureClipRequirements_.data(),futureClipCount_);
    if(!configured) {
        // Drop stale reservations at frame end; never steal leases from playing
        // Voices or the independent Transition/Stinger plans. Retry next frame.
        futureClipPreparation_->retire();futureClipReady_=false;last_=api::EngineStatus::ResourceLimit;
        if(!futureClipLimited_ && futureClipResourceLimits_<static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))++futureClipResourceLimits_;
        futureClipLimited_=true;return;
    }
    futureClipLimited_=false;futureClipReady_=futureClipCount_==0 || futureClipPreparation_->ready();
}
}
