#include "runtime/EngineRuntime.hpp"
#include "audio/source/BoundSourceInfo.hpp"
namespace player2 {
using api::EngineStatus;
EngineStatus EngineRuntime::bindStreamedSource(int index,std::unique_ptr<audio::AudioFrameSource> source,std::size_t encodedBytes,bool linear,bool loop,const std::array<std::uint8_t,32>* digest,bool verified,const api::BoundSourceInfo* selection) {
    if(!canBindSource())return EngineStatus::RuntimeActive;
    if(!source || (linear ? graph_!=nullptr : graph_==nullptr))return EngineStatus::InvalidRequest;
    const auto metadata=source->metadata();
    if(!(metadata.flags&audio::Streaming))return EngineStatus::UnsupportedExecution;
    const auto* asset=graph_ ? find(graph_->assets,index) : nullptr;
    if(!linear && (!asset || metadata.sampleRate!=asset->sampleRate || metadata.channels!=asset->channels || metadata.duration.value!=asset->frames))return EngineStatus::InvalidRequest;
    voices_.releaseInactive();
    if(!streamCache_)streamCache_=std::make_unique<audio::SourcePageCache>();
    if(!streamHistory_)streamHistory_=std::make_unique<StreamHistory>();
    if(!linear && !preparationHistory_)preparationHistory_=std::make_unique<PreparationHistory>();
    if(!linear && !transitionPreparation_)transitionPreparation_=std::make_unique<audio::PreparationSet>();
    if(!linear && !stingerPreparation_)stingerPreparation_=std::make_unique<audio::PreparationSet>();
    if(!linear && !futureClipPreparation_)futureClipPreparation_=std::make_unique<audio::PreparationSet>();
    auto buffer=std::make_unique<AudioBuffer>();buffer->index=index;buffer->sampleRate=metadata.sampleRate;buffer->channels=metadata.channels;
    buffer->sourceInfo=selection ? *selection : audio::boundSourceInfo(metadata);if(digest){buffer->sourceInfo.sha256=*digest;buffer->sourceInfo.hasDigest=true;buffer->sourceInfo.compiledDigestVerified=verified;}
    buffer->sourceLoop=metadata.sourceLoop;buffer->stream=streamCache_.get();buffer->streamedAsset=linear ? 0 : index;buffer->streamedFrames=metadata.duration;
    std::array<audio::SourceSamplePosition,128> warm{};std::size_t count=0;
    const auto append=[&](const Clip& clip) {
        if(clip.asset!=index)return true;
        for(std::size_t i=0;i<count;++i)if(warm[i].value==clip.sourceOffset)return true;
        if(count==warm.size())return false;
        warm[count++]={clip.sourceOffset};return true;
    };
    if(graph_)for(const auto& track:graph_->tracks) {
        for(const auto& clip:track.clips)if(!append(clip))return EngineStatus::ResourceLimit;
        for(const auto& sub:track.subTracks)for(const auto& clip:sub.clips)if(!append(clip))return EngineStatus::ResourceLimit;
    }
    const auto status=streamCache_->bind(buffer->streamedAsset,std::move(source),encodedBytes,warm.data(),count);
    if(status!=audio::SourceStatus::Ok)return status==audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
    if(linear){linear_=std::move(buffer);linearLoop_=false;return setLinearRepeat(loop,std::nullopt);}
    else {
        const auto& old=assets_[index];if(old)preparedBytes_-=old->samples.size()*sizeof(float);
        assets_[index]=std::move(buffer);
    }
    return EngineStatus::Ok;
}
EngineStatus EngineRuntime::setLinearModuleChannels(std::uint64_t mute,std::uint64_t solo)noexcept {return setLinearSourceControls(mute,solo,false);}
EngineStatus EngineRuntime::setLinearStreamGroups(std::uint32_t mute,std::uint32_t solo,const std::array<std::uint8_t,32>* expectedRoot)noexcept {
    if(expectedRoot && (!linear_ || !linear_->sourceInfo.hasDigest || linear_->sourceInfo.sha256!=*expectedRoot))return EngineStatus::SourceMismatch;
    return setLinearSourceControls(mute,solo,true);
}
EngineStatus EngineRuntime::setLinearSourceControls(std::uint64_t mute,std::uint64_t solo,bool groups)noexcept {
    if(graph_ || !linear_ || !linear_->stream || !streamCache_)return EngineStatus::UnsupportedExecution;
    const auto asset=linear_->streamedAsset;
    const auto count=groups ? streamCache_->streamGroupCount(asset) : streamCache_->moduleChannelCount(asset);
    if(count==0)return EngineStatus::UnsupportedExecution;
    if(count<64 && ((mute|solo)>>count))return EngineStatus::InvalidRequest;
    // The first Play may still be queued when stopped control claims ownership.
    drain();
    const auto at=clock_.frame();const auto phase=clock_.status();
    voices_.clear(); // Audio/output must be stopped; release every immutable page lease.
    const auto status=groups ? streamCache_->setStreamGroups(asset,static_cast<std::uint32_t>(mute),static_cast<std::uint32_t>(solo)) : streamCache_->setModuleChannels(asset,mute,solo);
    EngineStatus restored=EngineStatus::Ok;
    if(journal_->size)restored=seek({at});
    else {streamCache_->beginControlRender();const auto ready=streamCache_->prepare(asset,{0});streamCache_->endControlRender();if(ready!=audio::SourceStatus::Ok)restored=EngineStatus::MissingAsset;}
    if(phase==TransportStatus::Stopped){voices_.clear();clock_.finish();}
    if(linearLoop_ && restored==EngineStatus::Ok)restored=setLinearRepeat(true,linearRepeatRange_);
    if(status!=audio::SourceStatus::Ok)return status==audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
    return restored;
}
api::StreamingSnapshot EngineRuntime::streamingSnapshot() const noexcept {
    api::StreamingSnapshot value;value.at={clock_.frame()};
    if(streamHistory_){value.historyRanges=streamHistory_->size();value.historyDroppedRanges=streamHistory_->dropped();value.historyComplete=streamHistory_->complete();value.historyBytes=sizeof(StreamHistory);}
    if(preparationHistory_){value.preparationDecisions=preparationHistory_->size();value.droppedPreparationDecisions=preparationHistory_->dropped();value.preparationHistoryComplete=preparationHistory_->complete();value.preparationHistoryBytes=sizeof(PreparationHistory);}
    value.futureClipRequirements=futureClipPreparation_ ? futureClipPreparation_->size() : 0;
    value.futureClipReady=futureClipReady_;value.futureClipResourceLimits=futureClipResourceLimits_;
    if(!streamCache_)return value;
    const auto s=streamCache_->snapshot();value.pcmBytes=s.pcmBytes;value.encodedBytes=s.encodedBytes;value.sources=s.sources;value.cachedPages=s.cachedPages;
    value.underrunFrames=s.underrunFrames;value.requests=s.requests;value.droppedRequests=s.droppedRequests;value.decodedPages=s.decodedPages;
    value.decodeFailures=s.decodeFailures;value.lastFailure=s.lastFailure;return value;
}
void EngineRuntime::renderControl(float* output,std::size_t frames) noexcept {
    if(streamCache_)streamCache_->beginControlRender();
    render(output,frames);
    if(streamCache_)streamCache_->endControlRender();
}
}

namespace player2 {
api::SourceVoiceSnapshot EngineRuntime::sourceSnapshot(int slot) const noexcept {
    api::SourceVoiceSnapshot value;value.at={clock_.frame()};
    if(slot<0 || slot>=static_cast<int>(voices_.slots().size()))return value;
    const auto& voice=voices_.slots()[slot];if(!voice.active || !voice.buffer)return value;
    const auto& buffer=*voice.buffer;value.slot=slot;value.asset=buffer.index;value.segment=voice.segment;value.track=voice.track;value.clip=voice.clip;
    value.sourceEnded=voice.sourceEnded;value.streamed=buffer.stream!=nullptr;value.repeating=voice.sourceRepeats;
    value.duration={buffer.frames()};value.sampleRate=buffer.sampleRate;value.channels=buffer.channels;value.sourceLoop=buffer.sourceLoop;value.info=buffer.sourceInfo;
    const auto position=std::clamp(voice.sourcePosition,0.0,static_cast<double>(buffer.frames()));
    value.position={static_cast<std::int64_t>(position)};value.fraction=position-value.position.value;
    if(voice.clipLoop)value.clipLoop=api::ClipLoopExecution{voice.clipLoop->start,voice.clipLoop->end,voice.clipLoop->count,voice.clipLoopReleased ? voice.releasedWraps : ClipPlayback::wraps(*voice.clipLoop,voice.sourceOffset,voice.sourceProgress),voice.clipLoopReleased};
    if(voice.sourceRepeats){value.repeatBegin=voice.sourceRepeatBegin;value.repeatEnd=voice.sourceRepeatEnd;}
    return value;
}
}
