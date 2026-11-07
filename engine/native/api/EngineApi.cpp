#include "runtime/EngineHandleState.hpp"
namespace player2::api {
EngineHandle* createEngine() noexcept { return new (std::nothrow) EngineHandle; }
void destroyEngine(EngineHandle* engine) noexcept { delete engine; }
EngineStatus enableCueSearchMonitorV9(EngineHandle* engine) noexcept {
    if(!engine) return EngineStatus::InvalidRequest;
    if(engine->runtime || engine->outputActive.load()) return EngineStatus::RuntimeActive;
    engine->cueSearchV9.store(true);return EngineStatus::Ok;
}
bool cueSearchMonitorV9Enabled(const EngineHandle* engine) noexcept { return engine && engine->cueSearchV9.load(); }
LoadResult loadCompiledGraph(EngineHandle* engine,const std::uint8_t* bytes,std::size_t size) noexcept {
    if(!engine) return LoadResult::InvalidGraph;
    if(engine->outputActive.load()) return LoadResult::OutputActive;
    try {
        auto graph=std::make_unique<Graph>(GraphReader(bytes,size).read());
        auto next=std::make_unique<EngineRuntime>(std::move(graph));
        if(engine->cueSearchV9.load()) next->enableCueSearch();
        engine->runtime.swap(next); engine->graphSchemaDirty=true; engine->device.reset(); engine->publish(); return LoadResult::Ok;
    } catch(const std::bad_alloc&) { return LoadResult::OutOfMemory; }
      catch(const std::length_error&) { return LoadResult::ResourceLimit; }
      catch(const std::invalid_argument&) { return LoadResult::InvalidGraph; }
}
std::int32_t transportSampleRate(const EngineHandle* engine) noexcept {
    return engine && engine->runtime ? engine->runtime->sampleRate() : 48000;
}
namespace {
bool matchesSelection(const Asset& asset,const SourceOpenRequest& source) {
    if(!asset.source)return !source.entryId && !source.subsongId && !source.variantId;
    const auto same=[](const std::optional<std::string>& expected,const char* supplied){return expected ? supplied && *expected==supplied : supplied==nullptr;};
    return source.assetId && asset.source->assetId==source.assetId && same(asset.source->entryId,source.entryId) &&
        same(asset.source->subsongId,source.subsongId) && same(asset.source->variantId,source.variantId);
}
std::unique_ptr<AudioBuffer> prepareOpened(const SourceOpenRequest& request,int identity,const audio::SourceDigest& digest,AudioSourceHandle& source,bool verified=false) {
    try {
        auto buffer=audio::prepareBuffer(identity,*source.source,request.limits.maximumDecodedBytes);
        audio::setBoundSelection(buffer->sourceInfo,request);
        buffer->sourceInfo.sha256=digest;buffer->sourceInfo.hasDigest=true;buffer->sourceInfo.compiledDigestVerified=verified;return buffer;
    }
    catch(const audio::SourceFailure& failure) {
        if(failure.status==audio::SourceStatus::ResourceLimit) throw std::length_error("Prepared audio budget");
        throw std::invalid_argument("Source cannot provide complete frames");
    }
}
std::unique_ptr<AudioBuffer> prepare(const SourceOpenRequest& request,int identity,const audio::SourceDigest& digest,bool verified=false) {
    const auto opened=openAudioSource(request);
    if(opened.status==audio::SourceStatus::ResourceLimit)throw std::length_error("Source open budget");
    if(opened.status!=audio::SourceStatus::Ok)throw std::invalid_argument("Audio source open failed");
    std::unique_ptr<AudioSourceHandle,decltype(&closeAudioSource)> source(opened.handle,closeAudioSource);
    return prepareOpened(request,identity,digest,*source,verified);
}
EngineStatus sourceFailureStatus(audio::SourceStatus status)noexcept {
    if(status==audio::SourceStatus::ResourceLimit)return EngineStatus::ResourceLimit;
    if(status==audio::SourceStatus::InputChanged)return EngineStatus::SourceMismatch;
    return EngineStatus::InvalidRequest;
}
}
EngineStatus bindEngineSource(EngineHandle* engine,int index,const SourceOpenRequest& source,SourcePreparation preparation) noexcept {
    if(!engine || !engine->runtime || index<0 || index>=4096) return EngineStatus::InvalidRequest;
    if(engine->outputActive.load()) return EngineStatus::OutputActive;
    if(preparation!=SourcePreparation::Prepared && preparation!=SourcePreparation::Streamed)return EngineStatus::InvalidRequest;
    if(!engine->runtime->canBindSource())return EngineStatus::RuntimeActive;
    const auto* graph=engine->runtime->graph();const auto* asset=graph ? find(graph->assets,index) : nullptr;
    if(!asset || !source.bytes.data || source.bytes.size==0)return EngineStatus::InvalidRequest;
    if(source.bytes.size>source.limits.maximumInputBytes || source.bytes.size>64*1024*1024)return EngineStatus::ResourceLimit;
    audio::SourceDigest actual;
    if(!audio::sourceSha256(source.bytes,actual))return EngineStatus::InvalidRequest;
    if(asset->hasDigest && actual!=asset->sha256)return EngineStatus::SourceMismatch;
    if(!matchesSelection(*asset,source))return EngineStatus::SourceMismatch;
    try {
        EngineStatus result;
        if(preparation==SourcePreparation::Prepared)result=engine->runtime->bindAsset(prepare(source,index,actual,asset->hasDigest));
        else {
            const auto opened=openAudioSource(source);
            if(opened.status!=audio::SourceStatus::Ok)return opened.status==audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
            std::unique_ptr<AudioSourceHandle,decltype(&closeAudioSource)> handle(opened.handle,closeAudioSource);
            auto info=audio::boundSourceInfo(handle->source->metadata());audio::setBoundSelection(info,source);
            result=engine->runtime->bindStreamedSource(index,std::move(handle->source),source.bytes.size,false,false,&actual,asset->hasDigest,&info);
        }
        engine->publish();return result;
    }
    catch(const std::bad_alloc&) { return EngineStatus::ResourceLimit; }
    catch(const std::length_error&) { return EngineStatus::ResourceLimit; }
    catch(const std::invalid_argument&) { return EngineStatus::InvalidRequest; }
    catch(...) { return EngineStatus::ResourceLimit; }
}
EngineStatus loadLinearSource(EngineHandle* engine,const SourceOpenRequest& source,bool loop,SourcePreparation preparation) noexcept {
    if(!engine) return EngineStatus::InvalidRequest;
    if(engine->outputActive.load()) return EngineStatus::OutputActive;
    if(preparation!=SourcePreparation::Prepared && preparation!=SourcePreparation::Streamed)return EngineStatus::InvalidRequest;
    if(source.bytes.size>source.limits.maximumInputBytes || source.bytes.size>64*1024*1024)return EngineStatus::ResourceLimit;
    audio::SourceDigest actual;if(!source.bytes.data || !source.bytes.size || !audio::sourceSha256(source.bytes,actual))return EngineStatus::InvalidRequest;
    try {
        auto next=std::make_unique<EngineRuntime>(48000);
        if(preparation==SourcePreparation::Prepared)next->bindLinear(prepare(source,-1,actual),loop);
        else {
            const auto opened=openAudioSource(source);
            if(opened.status!=audio::SourceStatus::Ok)return opened.status==audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
            std::unique_ptr<AudioSourceHandle,decltype(&closeAudioSource)> handle(opened.handle,closeAudioSource);
            auto info=audio::boundSourceInfo(handle->source->metadata());audio::setBoundSelection(info,source);
            const auto status=next->bindStreamedSource(-1,std::move(handle->source),source.bytes.size,true,loop,&actual,false,&info);
            if(status!=EngineStatus::Ok)return status;
        }
        engine->runtime.swap(next); engine->graphSchemaDirty=true; engine->device.reset(); engine->publish(); return EngineStatus::Ok;
    } catch(const std::bad_alloc&) { return EngineStatus::ResourceLimit; }
      catch(const std::length_error&) { return EngineStatus::ResourceLimit; }
      catch(const std::invalid_argument&) { return EngineStatus::InvalidRequest; }
    catch(...) { return EngineStatus::ResourceLimit; }
}
EngineStatus bindEngineSourceInput(EngineHandle* engine,int index,const SourceOpenRequest& source,
    std::unique_ptr<audio::EncodedSourceInput> input,SourcePreparation preparation)noexcept {
    if(!engine || !engine->runtime || index<0 || index>=4096 || !input)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    if(preparation!=SourcePreparation::Prepared && preparation!=SourcePreparation::Streamed)return EngineStatus::InvalidRequest;
    if(!engine->runtime->canBindSource())return EngineStatus::RuntimeActive;
    const auto* graph=engine->runtime->graph();const auto* asset=graph ? find(graph->assets,index) : nullptr;
    if(!asset)return EngineStatus::InvalidRequest;
    if(!matchesSelection(*asset,source))return EngineStatus::SourceMismatch;
    const auto encodedBytes=input->byteLength();
    const auto opened=openAudioSourceInput(source,std::move(input));
    if(opened.status!=audio::SourceStatus::Ok)return sourceFailureStatus(opened.status);
    std::unique_ptr<AudioSourceHandle,decltype(&closeAudioSource)> handle(opened.handle,closeAudioSource);
    const auto& digest=handle->encodedRootSha256;
    if(asset->hasDigest && digest!=asset->sha256)return EngineStatus::SourceMismatch;
    try {
        EngineStatus result;
        if(preparation==SourcePreparation::Prepared)result=engine->runtime->bindAsset(prepareOpened(source,index,digest,*handle,asset->hasDigest));
        else {
            auto info=audio::boundSourceInfo(handle->source->metadata());audio::setBoundSelection(info,source);
            result=engine->runtime->bindStreamedSource(index,std::move(handle->source),encodedBytes,false,false,&digest,asset->hasDigest,&info);
        }
        engine->publish();return result;
    }catch(const std::length_error&){return EngineStatus::ResourceLimit;}
     catch(const std::invalid_argument&){return EngineStatus::InvalidRequest;}
     catch(...){return EngineStatus::ResourceLimit;}
}
EngineStatus loadLinearSourceInput(EngineHandle* engine,const SourceOpenRequest& source,
    std::unique_ptr<audio::EncodedSourceInput> input,bool loop,SourcePreparation preparation)noexcept {
    if(!engine || !input)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    if(preparation!=SourcePreparation::Prepared && preparation!=SourcePreparation::Streamed)return EngineStatus::InvalidRequest;
    const auto encodedBytes=input->byteLength();
    const auto opened=openAudioSourceInput(source,std::move(input));
    if(opened.status!=audio::SourceStatus::Ok)return sourceFailureStatus(opened.status);
    std::unique_ptr<AudioSourceHandle,decltype(&closeAudioSource)> handle(opened.handle,closeAudioSource);
    try {
        auto next=std::make_unique<EngineRuntime>(48000);
        if(preparation==SourcePreparation::Prepared)next->bindLinear(prepareOpened(source,-1,handle->encodedRootSha256,*handle),loop);
        else {
            auto info=audio::boundSourceInfo(handle->source->metadata());audio::setBoundSelection(info,source);
            const auto status=next->bindStreamedSource(-1,std::move(handle->source),encodedBytes,true,loop,&handle->encodedRootSha256,false,&info);
            if(status!=EngineStatus::Ok)return status;
        }
        engine->runtime.swap(next);engine->graphSchemaDirty=true;engine->device.reset();engine->publish();return EngineStatus::Ok;
    }catch(const std::length_error&){return EngineStatus::ResourceLimit;}
     catch(const std::invalid_argument&){return EngineStatus::InvalidRequest;}
     catch(...){return EngineStatus::ResourceLimit;}
}
EngineStatus submitEngineCommand(EngineHandle* engine,const EngineCommand& command) noexcept {
    if(!engine || !engine->runtime || !validEngineCommandKind(static_cast<std::int32_t>(command.kind)) || command.at.value< -1 || !std::isfinite(command.number)) return EngineStatus::InvalidRequest;
    return engine->runtime->submit(command) ? EngineStatus::Ok : EngineStatus::QueueFull;
}
void renderEngineDevice(EngineHandle* engine,float* output,std::size_t frames,int rate) noexcept {
    if(!output || frames>65536) return;
    if(engine && engine->runtime) { engine->device.render(*engine->runtime,output,frames,rate);
        float left=0,right=0;
        for(std::size_t n=0;n<frames;++n) { left=std::max(left,std::abs(output[n*2])); right=std::max(right,std::abs(output[n*2+1])); }
        engine->publish(left,right); }
    else std::fill(output,output+frames*2,0);
}
void renderEngine(EngineHandle* engine,float* output,std::size_t frames) noexcept {
    if(!output || frames>65536) return;
    if(engine && engine->runtime) { engine->runtime->render(output,frames); engine->publish(); }
    else std::fill(output,output+frames*2,0);
}
EngineSnapshot engineSnapshot(const EngineHandle* engine) noexcept {
    if(!engine) return {};
    EngineSnapshot s;
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire); if(before&1) continue;
        s.position.value=engine->frame.load(std::memory_order_relaxed); s.status=engine->status.load(std::memory_order_relaxed);
        s.transportEpoch=PlaylistMonitorSlot::loadBits(engine->transportEpoch.data());
        s.activeVoices=engine->voices.load(std::memory_order_relaxed); s.currentObject=engine->current.load(std::memory_order_relaxed);
        s.lastResult=static_cast<EngineStatus>(engine->result.load(std::memory_order_relaxed));
        s.droppedVoices=engine->dropped.load(std::memory_order_relaxed); s.rejectedCommands=engine->rejected.load(std::memory_order_relaxed);
        auto numeric=[&](std::size_t index) { return std::uint64_t(engine->numerical[index*2].load(std::memory_order_relaxed))|(std::uint64_t(engine->numerical[index*2+1].load(std::memory_order_relaxed))<<32); };
        s.voiceNumericalCorrections=numeric(0);s.mixerNumericalCorrections=numeric(1);
        s.peakLeft=engine->left.load(std::memory_order_relaxed); s.peakRight=engine->right.load(std::memory_order_relaxed);
        s.playlist=engine->playlist.load(std::memory_order_relaxed); s.playingSegment=engine->segment.load(std::memory_order_relaxed);
        s.playlistGroup=engine->group.load(std::memory_order_relaxed); s.playlistItem=engine->item.load(std::memory_order_relaxed);
        s.playlistCursor=engine->cursor.load(std::memory_order_relaxed); s.playlistHistorySize=engine->history.load(std::memory_order_relaxed);
        s.playlistRandomState=engine->randomState.load(std::memory_order_relaxed);
        s.switchContainer=engine->switchContainer.load(std::memory_order_relaxed); s.switchTarget=engine->switchTarget.load(std::memory_order_relaxed);
        s.switchPath=engine->switchPath.load(std::memory_order_relaxed); s.switchSpecificity=engine->switchSpecificity.load(std::memory_order_relaxed);
        s.switchPriority=engine->switchPriority.load(std::memory_order_relaxed); s.switchCandidates=engine->switchCandidates.load(std::memory_order_relaxed);
        s.switchReason=engine->switchReason.load(std::memory_order_relaxed);
        for(std::size_t i=0;i<s.transition.size();++i) s.transition[i]=engine->transition[i].load(std::memory_order_relaxed);
        for(std::size_t i=0;i<s.stinger.size();++i) s.stinger[i]=engine->stinger[i].load(std::memory_order_relaxed);
        for(std::size_t i=0;i<s.event.size();++i) s.event[i]=engine->event[i].load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before) return s;
    }
}
StreamingSnapshot engineStreamingSnapshot(const EngineHandle* engine) noexcept {
    if(!engine)return {};
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        std::array<std::uint64_t,21> values{};
        for(std::size_t n=0;n<values.size();++n)values[n]=std::uint64_t(engine->streaming[n*2].load(std::memory_order_relaxed))|(std::uint64_t(engine->streaming[n*2+1].load(std::memory_order_relaxed))<<32);
        StreamingSnapshot s{{engine->frame.load(std::memory_order_relaxed)},values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8],static_cast<audio::SourceStatus>(values[9]),values[10],values[11],values[12]!=0,values[13],values[14],values[15],values[16]!=0,values[17],values[18],values[19],values[20]!=0};
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before)return s;
    }
}
EngineStatus renderEngineControl(EngineHandle* engine,float* stereo,std::size_t frames) noexcept {
    if(!engine || !engine->runtime || !stereo || frames>65536)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    engine->runtime->renderControl(stereo,frames);engine->publish();return EngineStatus::Ok;
}
EngineStatus renderEngineRealtimeHarness(EngineHandle* engine,float* stereo,std::size_t frames) noexcept {
    if(!engine || !engine->runtime || !stereo || frames>65536)return EngineStatus::InvalidRequest;
    if(!claimEngineOutput(engine))return EngineStatus::OutputActive;
    renderEngine(engine,stereo,frames);releaseEngineOutput(engine);return EngineStatus::Ok;
}
EngineStatus pauseEngineDecodeWorkerHarness(EngineHandle* engine,bool pause) noexcept {
    if(!engine || !engine->runtime)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    return engine->runtime->pauseDecodeWorkerHarness(pause);
}
SourceMonitorPacket engineSourceSnapshot(const EngineHandle* engine,int slot) noexcept {
    if(!engine || slot<0 || slot>=128)return sourceMonitorPacket({});
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        auto value=engine->sources[slot].load();value[27]=engine->frame.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before)return value;
    }
}
GameParameterSnapshot engineParameterSnapshot(const EngineHandle* engine,int index) noexcept {
    if(!engine || index<0 || index>=4096) return {};
    const auto& slot=engine->parameters[index];
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire); if(before&1) continue;
        GameParameterSnapshot value; value.at.value=engine->frame.load(std::memory_order_relaxed);
        if(slot.valid.load(std::memory_order_relaxed)) {
            value.index=index; value.current=slot.load(0); value.target=slot.load(1);
            value.min=slot.load(2); value.max=slot.load(3); value.defaultValue=slot.load(4);
            value.attackFrames=slot.attack.load(std::memory_order_relaxed); value.releaseFrames=slot.release.load(std::memory_order_relaxed);
        }
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before) return value;
    }
}
BusSnapshot engineBusSnapshot(const EngineHandle* engine,int index) noexcept {
    if(!engine || index<0 || index>=4096) return {};
    const auto& slot=engine->buses[index];
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire); if(before&1) continue;
        BusSnapshot value; value.at.value=engine->frame.load(std::memory_order_relaxed);
        if(slot.valid.load(std::memory_order_relaxed)) {
            value.index=index; value.output=slot.output.load(std::memory_order_relaxed); value.auxiliary=slot.auxiliary.load(std::memory_order_relaxed);
            value.bypass=slot.bypass.load(std::memory_order_relaxed); value.localGainDb=slot.load(0);
            value.lowPassHz=slot.load(1);value.highPassHz=slot.load(2);value.reverbWet=slot.load(3);
            value.lowPassEnabled=slot.lowEnabled.load(std::memory_order_relaxed);value.highPassEnabled=slot.highEnabled.load(std::memory_order_relaxed);value.reverbWetConfigured=slot.wetConfigured.load(std::memory_order_relaxed);
            value.peakLeft=slot.left.load(std::memory_order_relaxed); value.peakRight=slot.right.load(std::memory_order_relaxed); value.tailEffects=slot.tailEffects.load(std::memory_order_relaxed);
        }
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before) return value;
    }
}
PlaylistGroupSnapshot enginePlaylistGroupSnapshot(const EngineHandle* engine,int index) noexcept {
    if(!engine || index<0 || index>=4096) return {};
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire); if(before&1) continue;
        auto value=engine->playlistGroups[index].load(index); value.at={engine->frame.load(std::memory_order_relaxed)};value.decision.at=value.at;
        value.decision.epoch=PlaylistMonitorSlot::loadBits(engine->transportEpoch.data());
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before) return value;
    }
}
PlaylistDecisionSnapshot enginePlaylistDecisionSnapshot(const EngineHandle* engine,int index) noexcept {
    if(!engine || index<0 || index>=4096)return {};
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        auto value=engine->playlistGroups[index].loadDecision(index);value.at={engine->frame.load(std::memory_order_relaxed)};
        value.epoch=PlaylistMonitorSlot::loadBits(engine->transportEpoch.data());
        std::atomic_thread_fence(std::memory_order_acquire);
        if(engine->sequence.load(std::memory_order_relaxed)==before)return value;
    }
}
EngineStatus setLinearStreamGroups(EngineHandle* engine,std::uint32_t mute,std::uint32_t solo,const std::array<std::uint8_t,32>* expectedRoot)noexcept {
    if(!engine || !engine->runtime)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    const auto result=engine->runtime->setLinearStreamGroups(mute,solo,expectedRoot);engine->device.reset();engine->publish();return result;
}
EngineStatus setLinearModuleChannels(EngineHandle* engine,std::uint64_t mute,std::uint64_t solo)noexcept {
    if(!engine || !engine->runtime)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    const auto result=engine->runtime->setLinearModuleChannels(mute,solo);engine->device.reset();engine->publish();return result;
}
EngineStatus setLinearRepeat(EngineHandle* engine,bool enabled,std::optional<audio::SourceLoopMetadata> range)noexcept {
    if(!engine || !engine->runtime)return EngineStatus::InvalidRequest;
    if(engine->outputActive.load())return EngineStatus::OutputActive;
    const auto result=engine->runtime->setLinearRepeat(enabled,range);engine->device.reset();engine->publish();return result;
}
EngineStatus seekEngine(EngineHandle* engine,audio::TransportFrame position) noexcept {
    if(!engine || !engine->runtime) return EngineStatus::InvalidRequest;
    if(engine->outputActive.load()) return EngineStatus::OutputActive;
    const auto result=engine->runtime->seek(position); engine->device.reset(); engine->publish(); return result;
}
bool claimEngineOutput(EngineHandle* engine) noexcept {
    if(!engine || !engine->runtime) return false;
    bool expected=false; return engine->outputActive.compare_exchange_strong(expected,true);
}
void releaseEngineOutput(EngineHandle* engine) noexcept { if(engine) engine->outputActive.store(false); }
}

namespace player2::api {
EntryStateSnapshot engineEntryStateSnapshot(const EngineHandle* engine) noexcept {
    if(!engine)return {};
    for(;;) {
        const auto sequence=engine->sequence.load(std::memory_order_acquire);if(sequence&1)continue;
        std::array<std::int64_t,8> values{};
        for(std::size_t n=0;n<values.size();++n){const auto bits=std::uint64_t(engine->entryState[n*2].load(std::memory_order_relaxed))|(std::uint64_t(engine->entryState[n*2+1].load(std::memory_order_relaxed))<<32);std::memcpy(&values[n],&bits,8);}
        std::atomic_thread_fence(std::memory_order_acquire);if(sequence!=engine->sequence.load(std::memory_order_relaxed))continue;
        return {static_cast<int>(values[0]),static_cast<int>(values[1]),static_cast<int>(values[2]),static_cast<int>(values[3]),static_cast<EntryStateDecision>(values[4]),static_cast<EngineStatus>(values[5]),{values[6]},{values[7]}};
    }
}
PerformanceSnapshot enginePerformanceSnapshot(const EngineHandle* engine) noexcept {
    if(!engine)return {};
    for(;;) {
        const auto sequence=engine->sequence.load(std::memory_order_acquire);if(sequence&1)continue;
        std::array<std::int64_t,13> values{};
        for(std::size_t n=0;n<values.size();++n){const auto bits=std::uint64_t(engine->performance[n*2].load(std::memory_order_relaxed))|(std::uint64_t(engine->performance[n*2+1].load(std::memory_order_relaxed))<<32);std::memcpy(&values[n],&bits,8);}
        std::atomic_thread_fence(std::memory_order_acquire);if(sequence!=engine->sequence.load(std::memory_order_relaxed))continue;
        return {static_cast<int>(values[0]),static_cast<int>(values[1]),static_cast<int>(values[2]),static_cast<int>(values[3]),static_cast<int>(values[4]),static_cast<PerformancePhase>(values[5]),static_cast<EngineStatus>(values[6]),{values[7]},{values[8]},{values[9]},values[10]!=0,values[11]!=0,{values[12]}};
    }
}
LayerGainSnapshot engineLayerGainSnapshot(const EngineHandle* engine,int index)noexcept {
    if(!engine || index<0 || index>=4096)return {};
    for(;;){const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        const auto& slot=engine->layers[index];LayerGainSnapshot s;
        if(slot.valid.load(std::memory_order_relaxed))s={index,slot.load(0),slot.load(1),slot.load(2),slot.load(3),{engine->frame.load(std::memory_order_relaxed)}};
        std::atomic_thread_fence(std::memory_order_acquire);if(before==engine->sequence.load(std::memory_order_relaxed))return s;
    }
}
GameSyncMonitorPacket engineGameSyncSnapshot(const EngineHandle* engine)noexcept {
    if(!engine)return {};
    try {
        GameSyncMonitorPacket packet;packet.reserve(maxGameSyncMonitorWords);
        for(int attempt=0;attempt<64;++attempt) {
            const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
            packet.assign({2,engine->frame.load(std::memory_order_relaxed),0,0,0,
                engine->status.load(std::memory_order_relaxed),engine->transition[0].load(std::memory_order_relaxed)});
            const auto appendDouble=[&packet](double value){std::int64_t bits;std::memcpy(&bits,&value,8);packet.push_back(bits);};
            for(int index=0;index<4096;++index){const auto value=engine->syncValues[index].load(std::memory_order_relaxed);if(value>=0){packet.push_back(index);packet.push_back(value);++packet[2];}}
            for(int index=0;index<4096;++index){const auto& slot=engine->parameters[index];if(slot.valid.load(std::memory_order_relaxed)){packet.push_back(index);appendDouble(slot.load(0));appendDouble(slot.load(1));++packet[3];}}
            for(int index=0;index<4096;++index){const auto& slot=engine->layers[index];if(slot.valid.load(std::memory_order_relaxed)){packet.push_back(index);appendDouble(slot.load(0));++packet[4];}}
            std::atomic_thread_fence(std::memory_order_acquire);
            if(before==engine->sequence.load(std::memory_order_relaxed))return packet;
        }
    }catch(const std::bad_alloc&) {}
    return {}; // Busy publication / allocation failure never returns a torn preset.
}
AutomaticMonitorPacket engineAutomaticSnapshot(const EngineHandle* engine)noexcept {
    if(!engine)return automaticPacket({});
    for(;;){const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        auto packet=engine->automatic.load();packet[6]=engine->frame.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);if(before==engine->sequence.load(std::memory_order_relaxed))return packet;
    }
}
MusicalLoopMonitorPacket engineMusicalLoopSnapshot(const EngineHandle* engine)noexcept {
    if(!engine)return musicalLoopPacket({});
    for(;;){const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        auto packet=engine->musicalLoop.load();packet[8]=engine->frame.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);if(before==engine->sequence.load(std::memory_order_relaxed))return packet;
    }
}

}
