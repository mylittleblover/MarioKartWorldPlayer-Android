#include <jni.h>
#include "../oboe/NativeOutput.hpp"
#include <new>
namespace {
player2::platform::NativeOutput* output(jlong value) noexcept { return reinterpret_cast<player2::platform::NativeOutput*>(static_cast<std::intptr_t>(value)); }
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_createOutput(JNIEnv*,jobject,jlong value) {
    if(!value) return 0;
    try { return reinterpret_cast<std::intptr_t>(new player2::platform::NativeOutput(reinterpret_cast<player2::api::EngineHandle*>(static_cast<std::intptr_t>(value)))); }
    catch(const std::exception&) { return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_destroyOutput(JNIEnv*,jobject,jlong value) { delete output(value); }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_startOutput(JNIEnv*,jobject,jlong value,jint rate) { return value && output(value)->start(rate); }
extern "C" JNIEXPORT void JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_stopOutput(JNIEnv*,jobject,jlong value) { if(value) output(value)->stop(); }
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_rebuildOutput(JNIEnv*,jobject,jlong value,jint rate) { return value && output(value)->rebuild(rate); }
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_outputSnapshot(JNIEnv* env,jobject,jlong value) {
    if(!value) return nullptr;
    const auto* handle=output(value);
    const jlong values[]={handle->rate(),handle->lastError(),handle->rebuilds(),static_cast<jlong>(handle->callbacks())};
    auto result=env->NewLongArray(4); if(result) env->SetLongArrayRegion(result,0,4,values); return result;
}

// Separate versioned diagnostic packet; the existing four-value packet stays stable.
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_outputTimingSnapshot(JNIEnv* env,jobject,jlong value) {
    if(!value)return nullptr;
    try {
        const auto snapshot=output(value)->timingSnapshot();std::array<jlong,20> values{};
        std::copy(snapshot.begin(),snapshot.end(),values.begin());
        auto result=env->NewLongArray(values.size());if(result)env->SetLongArrayRegion(result,0,values.size(),values.data());return result;
    }catch(const std::exception&) {return nullptr;}
}
