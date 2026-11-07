#include <jni.h>
#include <cstring>
#include "api/EngineApi.hpp"
#include "NativeSourceInputArguments.hpp"
#include "../decode/NativeFileSourceInput.hpp"

namespace {
player2::api::EngineHandle* handle(jlong value) noexcept {
    return reinterpret_cast<player2::api::EngineHandle*>(static_cast<std::intptr_t>(value));
}
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_transportSampleRate(JNIEnv*, jobject) {
    return player2::api::transportSampleRate();
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_effectSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto packet=player2::api::engineEffectSnapshot(handle(engine),index);
    static_assert(sizeof(jlong)==sizeof(std::int64_t));
    std::array<jlong,41> values{};std::copy(packet.begin(),packet.end(),values.begin());
    auto result=env->NewLongArray(static_cast<jsize>(packet.size()));
    if(result)env->SetLongArrayRegion(result,0,static_cast<jsize>(packet.size()),values.data());
    return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_albumFlowSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto packet=player2::api::engineAlbumFlowSnapshot(handle(engine));std::array<jlong,14> values{};
    std::copy(packet.begin(),packet.end(),values.begin());auto result=env->NewLongArray(static_cast<jsize>(values.size()));
    if(result)env->SetLongArrayRegion(result,0,static_cast<jsize>(values.size()),values.data());
    return result;
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_createEngine(JNIEnv*, jobject) {
    return static_cast<jlong>(reinterpret_cast<std::intptr_t>(player2::api::createEngine()));
}
extern "C" JNIEXPORT void JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_destroyEngine(JNIEnv*, jobject, jlong engine) {
    player2::api::destroyEngine(handle(engine));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_loadCompiledGraph(JNIEnv* env, jobject, jlong engine, jbyteArray packet) {
    using player2::api::LoadResult;
    if (!packet || !engine) return static_cast<jint>(LoadResult::InvalidGraph);
    const jsize size = env->GetArrayLength(packet);
    if (size < 16 || size > 4 * 1024 * 1024 + 16) return static_cast<jint>(LoadResult::InvalidGraph);
    auto* bytes = env->GetByteArrayElements(packet, nullptr);
    if (!bytes) return static_cast<jint>(LoadResult::OutOfMemory);
    const auto result = player2::api::loadCompiledGraph(handle(engine), reinterpret_cast<const std::uint8_t*>(bytes), static_cast<std::size_t>(size));
    env->ReleaseByteArrayElements(packet, bytes, JNI_ABORT);
    return static_cast<jint>(result);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_engineSampleRate(JNIEnv*, jobject, jlong engine) {
    return player2::api::transportSampleRate(handle(engine));
}
namespace {
class SourceText {
    JNIEnv* env_;jstring string_;const char* text_{};
public:
    SourceText(JNIEnv* env,jstring value):env_(env),string_(value){if(value && !env->ExceptionCheck())text_=env->GetStringUTFChars(value,nullptr);}
    ~SourceText(){if(text_)env_->ReleaseStringUTFChars(string_,text_);}
    const char* text()const noexcept{return text_;}
};
player2::api::EngineStatus prepareSource(JNIEnv* env,jlong engine,jint asset,jstring provider,jbyteArray input,bool linear,bool loop,jint preparation,
    jstring root,jstring entry,jstring subsong,jstring variant) {
    using player2::api::EngineStatus;
    if(!engine || !provider || !input) return EngineStatus::InvalidRequest;
    const auto size=env->GetArrayLength(input);
    if(size<=0 || size>64*1024*1024) return EngineStatus::ResourceLimit;
    const char* id=env->GetStringUTFChars(provider,nullptr); if(!id) return EngineStatus::ResourceLimit;
    auto* bytes=env->GetByteArrayElements(input,nullptr);
    if(!bytes) { env->ReleaseStringUTFChars(provider,id); return EngineStatus::ResourceLimit; }
    player2::api::SourceOpenRequest request; request.providerId=id;
    SourceText rootPin(env,root),entryPin(env,entry),subsongPin(env,subsong),variantPin(env,variant);
    if(env->ExceptionCheck()){env->ReleaseByteArrayElements(input,bytes,JNI_ABORT);env->ReleaseStringUTFChars(provider,id);return EngineStatus::ResourceLimit;}
    request.assetId=rootPin.text();request.entryId=entryPin.text();request.subsongId=subsongPin.text();request.variantId=variantPin.text();
    request.bytes={reinterpret_cast<const std::uint8_t*>(bytes),static_cast<std::size_t>(size)};
    const auto result=linear ? player2::api::loadLinearSource(handle(engine),request,loop,static_cast<player2::api::SourcePreparation>(preparation)) : player2::api::bindEngineSource(handle(engine),asset,request,static_cast<player2::api::SourcePreparation>(preparation));
    env->ReleaseByteArrayElements(input,bytes,JNI_ABORT); env->ReleaseStringUTFChars(provider,id); return result;
}
player2::api::EngineStatus prepareInput(JNIEnv* env,jlong engine,jint asset,jstring provider,jobject descriptor,bool linear,bool loop,jint preparation,
    jstring root,jstring entry,jstring subsong,jstring variant,jbyteArray expectedDigest) {
    using player2::api::EngineStatus;
    if(!engine || !provider || !descriptor || !root)return EngineStatus::InvalidRequest;
    SourceText id(env,provider),rootPin(env,root),entryPin(env,entry),subsongPin(env,subsong),variantPin(env,variant);
    if(env->ExceptionCheck())return EngineStatus::ResourceLimit;
    const auto fd=player2::platform::fileDescriptor(env,descriptor);if(env->ExceptionCheck())return EngineStatus::InvalidRequest;
    auto lease=player2::platform::openFileSourceInput(fd);
    if(!lease.input)return lease.status==player2::audio::SourceStatus::ResourceLimit ? EngineStatus::ResourceLimit : EngineStatus::InvalidRequest;
    player2::api::SourceOpenRequest request;request.providerId=id.text();request.assetId=rootPin.text();
    request.entryId=entryPin.text();request.subsongId=subsongPin.text();request.variantId=variantPin.text();
    std::array<std::uint8_t,32> digest{};
    if(!player2::platform::sourceDigest(env,expectedDigest,digest,request.expectedRootSha256))return EngineStatus::InvalidRequest;
    return linear ? player2::api::loadLinearSourceInput(handle(engine),request,std::move(lease.input),loop,static_cast<player2::api::SourcePreparation>(preparation)) :
        player2::api::bindEngineSourceInput(handle(engine),asset,request,std::move(lease.input),static_cast<player2::api::SourcePreparation>(preparation));
}
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_loadLinearInput(JNIEnv* env,jobject,jlong engine,jstring provider,jobject input,jboolean loop,jint preparation,jstring root,jstring entry,jstring subsong,jstring variant,jbyteArray expectedDigest) {
    return static_cast<jint>(prepareInput(env,engine,-1,provider,input,true,loop,preparation,root,entry,subsong,variant,expectedDigest));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_bindSourceInput(JNIEnv* env,jobject,jlong engine,jint asset,jstring provider,jobject input,jint preparation,jstring root,jstring entry,jstring subsong,jstring variant,jbyteArray expectedDigest) {
    return static_cast<jint>(prepareInput(env,engine,asset,provider,input,false,false,preparation,root,entry,subsong,variant,expectedDigest));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_loadLinear(JNIEnv* env,jobject,jlong engine,jstring provider,jbyteArray input,jboolean loop,jint preparation,jstring root,jstring entry,jstring subsong,jstring variant) {
    return static_cast<jint>(prepareSource(env,engine,-1,provider,input,true,loop,preparation,root,entry,subsong,variant));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_bindSource(JNIEnv* env,jobject,jlong engine,jint asset,jstring provider,jbyteArray input,jint preparation,jstring root,jstring entry,jstring subsong,jstring variant) {
    return static_cast<jint>(prepareSource(env,engine,asset,provider,input,false,false,preparation,root,entry,subsong,variant));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_command(JNIEnv*,jobject,jlong engine,jint kind,jint target,jint value,jdouble number,jlong frame) {
    const player2::api::EngineCommand command{static_cast<player2::api::EngineCommandKind>(kind),target,value,number,{frame}};
    return static_cast<jint>(player2::api::submitEngineCommand(handle(engine),command));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_render(JNIEnv* env,jobject,jlong engine,jfloatArray array,jint frames) {
    using player2::api::EngineStatus;
    if(!engine || !array || frames<0 || frames>65536 || env->GetArrayLength(array)<frames*2) return static_cast<jint>(EngineStatus::InvalidRequest);
    auto* output=env->GetFloatArrayElements(array,nullptr); if(!output) return static_cast<jint>(EngineStatus::ResourceLimit);
    const auto result=player2::api::renderEngineControl(handle(engine),output,frames); env->ReleaseFloatArrayElements(array,output,0); return static_cast<jint>(result);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_renderRealtime(JNIEnv* env,jobject,jlong engine,jfloatArray array,jint frames) {
    using player2::api::EngineStatus;
    if(!engine || !array || frames<0 || frames>65536 || env->GetArrayLength(array)<frames*2)return static_cast<jint>(EngineStatus::InvalidRequest);
    auto* output=env->GetFloatArrayElements(array,nullptr);if(!output)return static_cast<jint>(EngineStatus::ResourceLimit);
    const auto result=player2::api::renderEngineRealtimeHarness(handle(engine),output,frames);
    env->ReleaseFloatArrayElements(array,output,0);return static_cast<jint>(result);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_enableCueSearchMonitorV9(JNIEnv*,jobject,jlong engine) {
    return static_cast<jint>(player2::api::enableCueSearchMonitorV9(handle(engine)));
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_transitionSnapshotV9(JNIEnv* env,jobject,jlong engine) {
    if(!player2::api::cueSearchMonitorV9Enabled(handle(engine))) return nullptr;
    const auto snapshot=player2::api::engineSnapshot(handle(engine));
    jlong values[16]={9};for(std::size_t i=0;i<snapshot.transition.size();++i) values[i+1]=snapshot.transition[i];
    values[15]=snapshot.position.value;
    auto result=env->NewLongArray(16);if(result)env->SetLongArrayRegion(result,0,16,values);return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_snapshot(JNIEnv* env,jobject,jlong engine) {
    const auto s=player2::api::engineSnapshot(handle(engine));
    std::uint32_t left{},right{}; std::memcpy(&left,&s.peakLeft,sizeof(left)); std::memcpy(&right,&s.peakRight,sizeof(right));
    jlong values[65]={8,s.position.value,s.status,s.activeVoices,s.currentObject,static_cast<jlong>(s.droppedVoices),static_cast<jlong>(s.rejectedCommands),static_cast<int>(s.lastResult),left,right,
        s.playlist,s.playingSegment,s.playlistGroup,s.playlistItem,s.playlistCursor,s.playlistHistorySize,static_cast<jlong>(s.playlistRandomState),
        s.switchContainer,s.switchTarget,s.switchPath,s.switchSpecificity,s.switchPriority,s.switchCandidates,s.switchReason};
    for(std::size_t i=0;i<s.transition.size();++i) values[24+i]=s.transition[i];
    if(values[30]<0 || values[31]<0 || values[32]<0) {
        const player2::api::EngineSnapshot absent;
        for(std::size_t i=0;i<absent.transition.size();++i) values[24+i]=absent.transition[i];
    }
    for(std::size_t i=0;i<s.stinger.size();++i) values[38+i]=s.stinger[i];
    for(std::size_t i=0;i<s.event.size();++i) values[52+i]=s.event[i];
    values[63]=static_cast<jlong>(s.voiceNumericalCorrections);values[64]=static_cast<jlong>(s.mixerNumericalCorrections);
    auto result=env->NewLongArray(65); if(result) env->SetLongArrayRegion(result,0,65,values); return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_playlistDecisionSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto s=player2::api::enginePlaylistDecisionSnapshot(handle(engine),index);
    jlong values[149]={1,s.index,s.container,static_cast<int>(s.reason),s.cursor,s.chosenItem,s.configuredAvoid,s.effectiveAvoid,s.count,0,0,0,0,0,0,0,s.selectedAt.value,s.at.value,s.recycledShuffle};
    std::memcpy(&values[9],&s.randomBefore,sizeof(s.randomBefore));std::memcpy(&values[10],&s.unit,sizeof(s.unit));
    std::memcpy(&values[11],&s.scale,sizeof(s.scale));std::memcpy(&values[12],&s.total,sizeof(s.total));
    std::memcpy(&values[13],&s.eligible,sizeof(s.eligible));std::memcpy(&values[14],&s.repeated,sizeof(s.repeated));std::memcpy(&values[15],&s.shuffleUsed,sizeof(s.shuffleUsed));
    for(std::size_t n=0;n<s.candidates.size();++n) {values[19+n]=s.candidates[n];std::memcpy(&values[83+n],&s.weights[n],sizeof(double));}
    std::memcpy(&values[147],&s.selectedEpoch,sizeof(s.selectedEpoch));std::memcpy(&values[148],&s.epoch,sizeof(s.epoch));
    auto result=env->NewLongArray(149);if(result)env->SetLongArrayRegion(result,0,149,values);return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_playlistGroupSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto s=player2::api::enginePlaylistGroupSnapshot(handle(engine),index);
    jlong values[76]={1,s.index,s.container,s.active,static_cast<int>(s.mode),static_cast<int>(s.randomPolicy),s.avoidRepeat,s.cursor,s.chosenItem,s.historySize,0,s.at.value};
    std::memcpy(&values[10],&s.randomState,sizeof(s.randomState));
    for(std::size_t n=0;n<s.history.size();++n) values[12+n]=s.history[n];
    auto result=env->NewLongArray(76); if(result) env->SetLongArrayRegion(result,0,76,values); return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_busSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto s=player2::api::engineBusSnapshot(handle(engine),index);
    jlong values[16]={2,s.index,s.output,s.auxiliary,s.bypass,0,0,0,s.tailEffects,s.at.value,0,0,s.lowPassEnabled,s.highPassEnabled,s.reverbWetConfigured,0};
    std::uint32_t left,right; std::memcpy(&left,&s.peakLeft,sizeof(left)); std::memcpy(&right,&s.peakRight,sizeof(right));
    values[6]=left; values[7]=right; std::memcpy(&values[5],&s.localGainDb,sizeof(double));
    std::memcpy(&values[10],&s.lowPassHz,sizeof(double));std::memcpy(&values[11],&s.highPassHz,sizeof(double));std::memcpy(&values[15],&s.reverbWet,sizeof(double));
    auto result=env->NewLongArray(16); if(result) env->SetLongArrayRegion(result,0,16,values); return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_parameterSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto s=player2::api::engineParameterSnapshot(handle(engine),index);
    jlong values[10]={1,s.index,0,0,0,0,0,s.attackFrames,s.releaseFrames,s.at.value};
    const double numbers[5]={s.current,s.target,s.min,s.max,s.defaultValue};
    for(int n=0;n<5;++n) std::memcpy(&values[2+n],&numbers[n],sizeof(double));
    auto result=env->NewLongArray(10); if(result) env->SetLongArrayRegion(result,0,10,values); return result;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_seek(JNIEnv*,jobject,jlong engine,jlong frame) {
    return static_cast<jint>(player2::api::seekEngine(handle(engine),{frame}));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_streamingSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto s=player2::api::engineStreamingSnapshot(handle(engine));
    const jlong values[23]={4,s.at.value,static_cast<jlong>(s.pcmBytes),static_cast<jlong>(s.encodedBytes),static_cast<jlong>(s.sources),static_cast<jlong>(s.cachedPages),static_cast<jlong>(s.underrunFrames),static_cast<jlong>(s.requests),static_cast<jlong>(s.droppedRequests),static_cast<jlong>(s.decodedPages),static_cast<jlong>(s.decodeFailures),static_cast<jlong>(s.lastFailure),static_cast<jlong>(s.historyRanges),static_cast<jlong>(s.historyDroppedRanges),s.historyComplete,static_cast<jlong>(s.historyBytes),static_cast<jlong>(s.preparationDecisions),static_cast<jlong>(s.droppedPreparationDecisions),s.preparationHistoryComplete,static_cast<jlong>(s.preparationHistoryBytes),static_cast<jlong>(s.futureClipRequirements),static_cast<jlong>(s.futureClipResourceLimits),s.futureClipReady};
    auto result=env->NewLongArray(23);if(result)env->SetLongArrayRegion(result,0,23,values);return result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_entryStateSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto s=player2::api::engineEntryStateSnapshot(handle(engine));
    const jlong values[9]={1,s.index,s.start,s.appliedActions,s.failedAction,static_cast<jlong>(s.decision),static_cast<jlong>(s.result),s.requestedAt.value,s.at.value};
    auto result=env->NewLongArray(9);if(result)env->SetLongArrayRegion(result,0,9,values);return result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_performanceSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto s=player2::api::enginePerformanceSnapshot(handle(engine));
    const jlong values[14]={1,s.index,s.start,s.steps,s.executedSteps,s.lastStep,static_cast<jlong>(s.phase),static_cast<jlong>(s.result),s.actionAt.value,s.requestedAt.value,s.at.value,s.active,s.paused,s.endAt.value};
    auto result=env->NewLongArray(14);if(result)env->SetLongArrayRegion(result,0,14,values);return result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_layerGainSnapshot(JNIEnv* env,jobject,jlong engine,jint index) {
    const auto s=player2::api::engineLayerGainSnapshot(handle(engine),index);jlong values[7]={1,s.index,0,0,0,0,s.at.value};
    const double numbers[4]={s.trimDb,s.baseDb,s.rtpcDb,s.finalDb};for(int n=0;n<4;++n)std::memcpy(&values[2+n],&numbers[n],8);
    auto result=env->NewLongArray(7);if(result)env->SetLongArrayRegion(result,0,7,values);return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_gameSyncSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto packet=player2::api::engineGameSyncSnapshot(handle(engine));
    std::vector<jlong> values(packet.begin(),packet.end());
    auto result=env->NewLongArray(values.size());if(result && !values.empty())env->SetLongArrayRegion(result,0,values.size(),values.data());return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_automaticSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto packet=player2::api::engineAutomaticSnapshot(handle(engine));std::array<jlong,21> values{};
    for(std::size_t n=0;n<values.size();++n)values[n]=packet[n];
    auto result=env->NewLongArray(values.size());if(result)env->SetLongArrayRegion(result,0,values.size(),values.data());return result;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_musicalLoopSnapshot(JNIEnv* env,jobject,jlong engine) {
    const auto packet=player2::api::engineMusicalLoopSnapshot(handle(engine));std::array<jlong,10> values{};
    for(std::size_t n=0;n<values.size();++n)values[n]=packet[n];
    auto result=env->NewLongArray(values.size());if(result)env->SetLongArrayRegion(result,0,values.size(),values.data());return result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_sourceSnapshot(JNIEnv* env,jobject,jlong engine,jint slot) {
    const auto packet=player2::api::engineSourceSnapshot(handle(engine),slot);
    std::array<jlong,std::tuple_size<player2::api::SourceMonitorPacket>::value> values{};
    for(std::size_t n=0;n<packet.size();++n)values[n]=packet[n];
    auto result=env->NewLongArray(values.size());if(result)env->SetLongArrayRegion(result,0,values.size(),values.data());return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_pauseDecodeWorkerHarness(JNIEnv*,jobject,jlong engine,jboolean pause) {
    return static_cast<jint>(player2::api::pauseEngineDecodeWorkerHarness(handle(engine),pause));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_setLinearModuleChannels(JNIEnv*,jobject,jlong engine,jlong mute,jlong solo) {
    return static_cast<jint>(player2::api::setLinearModuleChannels(handle(engine),static_cast<std::uint64_t>(mute),static_cast<std::uint64_t>(solo)));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_setLinearRepeat(JNIEnv*,jobject,jlong engine,jboolean enabled,jlong begin,jlong end) {
    if(begin< -1 || end< -1 || (begin==-1)!=(end==-1))return static_cast<jint>(player2::api::EngineStatus::InvalidRequest);
    std::optional<player2::audio::SourceLoopMetadata> range;
    if(begin>=0)range=player2::audio::SourceLoopMetadata{{begin},{end}};
    return static_cast<jint>(player2::api::setLinearRepeat(handle(engine),enabled,range));
}
