#include <jni.h>
#include "api/AudioSourceApi.hpp"
#include "../decode/NativeFileSourceInput.hpp"
#include "NativeSourceInputArguments.hpp"
#include <memory>
#include <vector>
#include <cstdio>

namespace {
using player2::audio::SourceStatus;
#define SOURCE_JNI(name) Java_com_mylittleblover_player2_platform_audio_source_NativeSourceCalls_##name
player2::api::AudioSourceHandle* handle(jlong value) noexcept {
    return reinterpret_cast<player2::api::AudioSourceHandle*>(static_cast<std::intptr_t>(value));
}
class BytesPin {
    JNIEnv* env_; jbyteArray array_; jbyte* data_{}; bool owned_;
public:
    BytesPin(JNIEnv* env,jbyteArray array,bool owned=false):env_(env),array_(array),owned_(owned) {
        if(array) data_=env->GetByteArrayElements(array,nullptr);
    }
    ~BytesPin() { if(data_) env_->ReleaseByteArrayElements(array_,data_,JNI_ABORT); if(owned_ && array_) env_->DeleteLocalRef(array_); }
    player2::audio::ByteView view() const noexcept { if(!data_) return {}; return {reinterpret_cast<const std::uint8_t*>(data_),array_ ? static_cast<std::size_t>(env_->GetArrayLength(array_)) : 0}; }
};
class TextPin {
    JNIEnv* env_; jstring string_; const char* data_{}; bool owned_;
public:
    TextPin(JNIEnv* env,jstring string,bool owned=false):env_(env),string_(string),owned_(owned) {
        if(string && !env_->ExceptionCheck()) data_=env_->GetStringUTFChars(string,nullptr);
    }
    ~TextPin() { if(data_) env_->ReleaseStringUTFChars(string_,data_); if(owned_ && string_) env_->DeleteLocalRef(string_); }
    const char* data() const noexcept { return data_; }
};
jobjectArray entryPacket(JNIEnv* env,SourceStatus status,const std::vector<player2::audio::SourceEntry>& entries) {
    if(entries.size()>4096)return nullptr;
    auto string=env->FindClass("java/lang/String");if(!string)return nullptr;
    auto output=env->NewObjectArray(entries.size()+2,string,nullptr);env->DeleteLocalRef(string);if(!output)return nullptr;
    char code[16]{};std::snprintf(code,sizeof(code),"%d",static_cast<int>(status));
    for(std::size_t n=0;n<entries.size()+2;++n) {
        auto text=env->NewStringUTF(n==0 ? "1" : n==1 ? code : entries[n-2].id.data());if(!text)return nullptr;
        env->SetObjectArrayElement(output,n,text);env->DeleteLocalRef(text);if(env->ExceptionCheck())return nullptr;
    }
    return output;
}
}
extern "C" JNIEXPORT jobjectArray JNICALL SOURCE_JNI(providerIds)(JNIEnv* env,jobject) {
    auto string=env->FindClass("java/lang/String"); if(!string) return nullptr;
    const auto ids=player2::api::audioProviderIds(); auto output=env->NewObjectArray(ids.size(),string,nullptr); env->DeleteLocalRef(string);
    if(!output) return nullptr;
    for(std::size_t n=0;n<ids.size();++n) { auto id=env->NewStringUTF(ids[n]); if(!id) return nullptr; env->SetObjectArrayElement(output,n,id); env->DeleteLocalRef(id); }
    return output;
}
extern "C" JNIEXPORT jstring JNICALL SOURCE_JNI(probe)(JNIEnv* env,jobject,jbyteArray bytes) {
    if(!bytes || env->GetArrayLength(bytes)>4096) return nullptr;
    BytesPin pin(env,bytes); const auto id=player2::api::probeAudioSource(pin.view());
    return id ? env->NewStringUTF(id) : nullptr;
}
extern "C" JNIEXPORT jlongArray JNICALL SOURCE_JNI(open)(JNIEnv* env,jobject,jstring provider,jbyteArray bytes,
    jstring entry,jstring subsong,jstring variant,jobjectArray resourceIds,jobjectArray resourceBytes,jint maximumInput) {
    auto output=env->NewLongArray(2); if(!output) return nullptr;
    auto complete=[&](player2::api::SourceOpenResult result) {
        if(env->ExceptionCheck()) {
            if(result.handle) player2::api::closeAudioSource(result.handle);
            return static_cast<jlongArray>(nullptr);
        }
        const jlong fields[]{static_cast<jlong>(result.status),static_cast<jlong>(reinterpret_cast<std::intptr_t>(result.handle))};
        env->SetLongArrayRegion(output,0,2,fields);
        if(env->ExceptionCheck() && result.handle) player2::api::closeAudioSource(result.handle);
        return output;
    };
    if(!provider || !bytes || !resourceIds || !resourceBytes || maximumInput<=0) return complete({SourceStatus::InvalidRequest,nullptr});
    const auto count=env->GetArrayLength(resourceIds);
    if(static_cast<std::size_t>(count)>player2::api::MaximumSourceResources || count!=env->GetArrayLength(resourceBytes)) return complete({SourceStatus::InvalidRequest,nullptr});
    if(env->EnsureLocalCapacity(count*2+32)<0) return nullptr;
    std::uint64_t total=env->GetArrayLength(bytes);
    for(jsize n=0;n<count;++n) {
        auto resource=static_cast<jbyteArray>(env->GetObjectArrayElement(resourceBytes,n));
        if(!resource) return complete({SourceStatus::InvalidRequest,nullptr});
        total+=env->GetArrayLength(resource); env->DeleteLocalRef(resource);
    }
    if(total>static_cast<std::uint64_t>(maximumInput)) return complete({SourceStatus::ResourceLimit,nullptr});
    try {
        TextPin providerPin(env,provider),entryPin(env,entry),subsongPin(env,subsong),variantPin(env,variant);
        BytesPin source(env,bytes);
        std::vector<std::unique_ptr<TextPin>> ids; std::vector<std::unique_ptr<BytesPin>> pins;
        std::vector<player2::api::SourceResource> resources; resources.reserve(count);
        for(jsize n=0;n<count;++n) {
            ids.push_back(std::make_unique<TextPin>(env,static_cast<jstring>(env->GetObjectArrayElement(resourceIds,n)),true));
            pins.push_back(std::make_unique<BytesPin>(env,static_cast<jbyteArray>(env->GetObjectArrayElement(resourceBytes,n)),true));
            resources.push_back({ids.back()->data(),pins.back()->view()});
            if(env->ExceptionCheck()) return complete({SourceStatus::ResourceLimit,nullptr});
        }
        if(env->ExceptionCheck()) return complete({SourceStatus::ResourceLimit,nullptr});
        player2::api::SourceOpenRequest request;
        request.providerId=providerPin.data(); request.bytes=source.view(); request.entryId=entryPin.data();
        request.subsongId=subsongPin.data(); request.variantId=variantPin.data();
        request.resources=resources.data(); request.resourceCount=resources.size(); request.limits.maximumInputBytes=maximumInput;
        return complete(player2::api::openAudioSource(request));
    } catch(...) { return complete({SourceStatus::ResourceLimit,nullptr}); }
}
extern "C" JNIEXPORT jlongArray JNICALL SOURCE_JNI(openInput)(JNIEnv* env,jobject,jstring provider,jobject descriptor,
    jstring asset,jstring entry,jstring subsong,jstring variant,jint maximumInput,jbyteArray expectedDigest) {
    auto output=env->NewLongArray(2);if(!output)return nullptr;
    player2::api::SourceOpenResult result{SourceStatus::InvalidRequest,nullptr};
    if(provider && descriptor && asset && maximumInput>0) {
        TextPin providerPin(env,provider),assetPin(env,asset),entryPin(env,entry),subsongPin(env,subsong),variantPin(env,variant);
        if(env->ExceptionCheck())return nullptr;
        const auto fd=player2::platform::fileDescriptor(env,descriptor);if(env->ExceptionCheck())return nullptr;
        auto lease=player2::platform::openFileSourceInput(fd);
        result.status=lease.status;
        if(lease.input) {
            player2::api::SourceOpenRequest request;
            request.providerId=providerPin.data();request.assetId=assetPin.data();request.entryId=entryPin.data();
            request.subsongId=subsongPin.data();request.variantId=variantPin.data();request.limits.maximumInputBytes=maximumInput;
            std::array<std::uint8_t,32> digest{};
            if(player2::platform::sourceDigest(env,expectedDigest,digest,request.expectedRootSha256))
                result=player2::api::openAudioSourceInput(request,std::move(lease.input));
            else if(env->ExceptionCheck())return nullptr;
            else result.status=SourceStatus::InvalidRequest;
        }
    }
    const jlong fields[]{static_cast<jlong>(result.status),static_cast<jlong>(reinterpret_cast<std::intptr_t>(result.handle))};
    env->SetLongArrayRegion(output,0,2,fields);
    if(env->ExceptionCheck() && result.handle)player2::api::closeAudioSource(result.handle);
    return output;
}
extern "C" JNIEXPORT jlongArray JNICALL SOURCE_JNI(metadata)(JNIEnv* env,jobject,jlong value) {
    player2::audio::SourceMetadata metadata;
    if(player2::api::audioSourceMetadata(handle(value),metadata)!=SourceStatus::Ok) return nullptr;
    // Engine chooses the explicit metadata wire version; Graph protocol is independent.
    const jlong fields[]{player2::audio::sourceMetadataWireVersion(metadata.encoding),static_cast<jlong>(metadata.kind),metadata.sampleRate,metadata.channels,metadata.duration.value,
        static_cast<jlong>(metadata.seek),metadata.flags,metadata.sourceLoop ? 1 : 0,
        metadata.sourceLoop ? metadata.sourceLoop->start.value : 0,metadata.sourceLoop ? metadata.sourceLoop->end.value : 0,
        static_cast<jlong>(metadata.encoding),metadata.bitDepth};
    auto output=env->NewLongArray(12); if(output) env->SetLongArrayRegion(output,0,12,fields); return output;
}
extern "C" JNIEXPORT jobjectArray JNICALL SOURCE_JNI(subsongs)(JNIEnv* env,jobject,jlong value) {
    std::array<player2::audio::SourceSubsong,128> songs{};
    const auto result=player2::api::audioSourceSubsongs(handle(value),0,songs.data(),songs.size());
    if(result.status!=SourceStatus::Ok || !result.frames) {
        auto error=env->FindClass("java/lang/IllegalStateException");
        if(error){env->ThrowNew(error,"SOURCE_SUBSONGS_UNAVAILABLE");env->DeleteLocalRef(error);}
        return nullptr;
    }
    auto string=env->FindClass("java/lang/String");if(!string)return nullptr;
    auto output=env->NewObjectArray(result.frames,string,nullptr);env->DeleteLocalRef(string);
    if(!output)return nullptr;
    for(std::size_t n=0;n<result.frames;++n) {
        auto text=env->NewStringUTF(songs[n].id.data());if(!text)return nullptr;
        env->SetObjectArrayElement(output,n,text);env->DeleteLocalRef(text);
        if(env->ExceptionCheck())return nullptr;
    }
    return output;
}
extern "C" JNIEXPORT jobjectArray JNICALL SOURCE_JNI(inspectEntries)(JNIEnv* env,jobject,jstring provider,jbyteArray bytes,jint maximumInput) {
    if(!provider || !bytes || maximumInput<=0)return entryPacket(env,SourceStatus::InvalidRequest,{});
    if(env->GetArrayLength(bytes)>maximumInput || static_cast<std::size_t>(env->GetArrayLength(bytes))>player2::audio::MaximumEncodedSourceBytes)
        return entryPacket(env,SourceStatus::ResourceLimit,{});
    TextPin text(env,provider);BytesPin pin(env,bytes);if(env->ExceptionCheck())return nullptr;
    player2::audio::DecodeLimits limits;limits.maximumInputBytes=maximumInput;
    const auto result=player2::api::inspectAudioContainerEntries(text.data(),pin.view(),limits);
    return entryPacket(env,result.status,result.entries);
}
extern "C" JNIEXPORT jobjectArray JNICALL SOURCE_JNI(entries)(JNIEnv* env,jobject,jlong value) {
    try {
        std::vector<player2::audio::SourceEntry> entries;std::array<player2::audio::SourceEntry,128> page{};
        while(entries.size()<=4096) {
            const auto result=player2::api::audioSourceEntries(handle(value),entries.size(),page.data(),page.size());
            if(result.status!=SourceStatus::Ok)return entryPacket(env,result.status,{});
            if(!result.frames)return entryPacket(env,SourceStatus::Ok,entries);
            if(result.frames>4096-entries.size())break;
            entries.insert(entries.end(),page.begin(),page.begin()+result.frames);
        }
        return entryPacket(env,SourceStatus::ResourceLimit,{});
    }catch(...){return entryPacket(env,SourceStatus::ResourceLimit,{});}
}
extern "C" JNIEXPORT jint JNICALL SOURCE_JNI(render)(JNIEnv* env,jobject,jlong value,jint frames,jfloatArray output) {
    if(!output || frames<0) return -static_cast<jint>(SourceStatus::InvalidRequest);
    const auto capacity=env->GetArrayLength(output); auto* pcm=env->GetFloatArrayElements(output,nullptr);
    if(!pcm) return -static_cast<jint>(SourceStatus::ResourceLimit);
    const auto result=player2::api::renderAudioSource(handle(value),pcm,frames,capacity);
    env->ReleaseFloatArrayElements(output,pcm,0);
    return result.status==SourceStatus::Ok ? static_cast<jint>(result.frames) : -static_cast<jint>(result.status);
}
extern "C" JNIEXPORT jint JNICALL SOURCE_JNI(seek)(JNIEnv*,jobject,jlong value,jlong position) {
    return static_cast<jint>(player2::api::seekAudioSource(handle(value),{position}));
}
extern "C" JNIEXPORT jlong JNICALL SOURCE_JNI(position)(JNIEnv*,jobject,jlong value) { return player2::api::audioSourcePosition(handle(value)).value; }
extern "C" JNIEXPORT jint JNICALL SOURCE_JNI(reset)(JNIEnv*,jobject,jlong value) { return static_cast<jint>(player2::api::resetAudioSource(handle(value))); }
extern "C" JNIEXPORT void JNICALL SOURCE_JNI(close)(JNIEnv*,jobject,jlong value) { player2::api::closeAudioSource(handle(value)); }
extern "C" JNIEXPORT jlongArray JNICALL SOURCE_JNI(sequenceSummary)(JNIEnv* env,jobject,jlong value) {
    player2::audio::SequenceSummary summary;const auto status=player2::api::audioSequenceSummary(handle(value),summary);
    std::array<jlong,19> fields{1,static_cast<jlong>(status),static_cast<jlong>(summary.kind),summary.sourceRate,summary.channels,static_cast<jlong>(summary.events),summary.loop ? 1 : 0};
    if(summary.loop){fields[7]=summary.loop->from.value;fields[8]=summary.loop->to.value;fields[9]=summary.loop->samples.start.value;fields[10]=summary.loop->samples.end.value;}
    for(std::size_t n=0;n<8;++n){std::uint32_t word=0;for(std::size_t b=0;b<4;++b)word|=std::uint32_t(summary.encodedRootSha256[n*4+b])<<(8*b);fields[11+n]=word;}
    auto output=env->NewLongArray(fields.size());if(output)env->SetLongArrayRegion(output,0,fields.size(),fields.data());return output;
}
extern "C" JNIEXPORT jlongArray JNICALL SOURCE_JNI(sequenceEvents)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    std::array<player2::audio::SequenceEvent,player2::audio::MaximumSequencePage> events{};
    player2::api::SourceRenderResult result{SourceStatus::InvalidRequest,0};
    if(offset>=0 && maximum>0 && static_cast<std::size_t>(maximum)<=events.size())result=player2::api::audioSequenceEvents(handle(value),offset,events.data(),maximum);
    std::array<jlong,4+player2::audio::MaximumSequencePage*7> fields{1,static_cast<jlong>(result.status),offset,static_cast<jlong>(result.frames)};
    for(std::size_t n=0;n<result.frames;++n){const auto& event=events[n];const auto p=4+n*7;fields[p]=event.origin.value;fields[p+1]=event.at.value;fields[p+2]=event.duration.value;fields[p+3]=static_cast<jlong>(event.kind);fields[p+4]=event.channel;fields[p+5]=event.opcode;fields[p+6]=event.value;}
    const auto size=4+result.frames*7;auto output=env->NewLongArray(size);if(output)env->SetLongArrayRegion(output,0,size,fields.data());return output;
}
