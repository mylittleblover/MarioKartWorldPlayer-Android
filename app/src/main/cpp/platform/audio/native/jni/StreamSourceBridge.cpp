#include <jni.h>
#include "api/AudioSourceApi.hpp"
#include "api/EngineApi.hpp"
#include <string>
#include <vector>
#include <limits>
#include <sstream>
#include <iomanip>
#include <locale>

namespace {
using player2::audio::SourceStatus;
player2::api::AudioSourceHandle* handle(jlong value){return reinterpret_cast<player2::api::AudioSourceHandle*>(value);}
std::string real(double value){std::ostringstream out;out.imbue(std::locale::classic());out<<std::setprecision(17)<<value;return out.str();}
jobjectArray packet(JNIEnv* env,const std::vector<std::string>& fields) {
    auto string=env->FindClass("java/lang/String");if(!string)return nullptr;
    auto output=env->NewObjectArray(fields.size(),string,nullptr);env->DeleteLocalRef(string);if(!output)return nullptr;
    for(std::size_t n=0;n<fields.size();++n){auto text=env->NewStringUTF(fields[n].c_str());if(!text)return nullptr;env->SetObjectArrayElement(output,n,text);env->DeleteLocalRef(text);if(env->ExceptionCheck())return nullptr;}
    return output;
}
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mylittleblover_player2_platform_audio_source_NativeSourceCalls_streamInfo(JNIEnv* env,jobject,jlong value) {
    try {
        player2::audio::SourceStreamInfo info;std::array<std::uint8_t,32> digest{};
        const auto status=player2::api::audioStreamInspection(handle(value),info,digest);
        std::vector<std::string> fields{"1",std::to_string(static_cast<int>(status))};
        if(status!=SourceStatus::Ok)return packet(env,fields);
        std::string hash;constexpr char hex[]="0123456789abcdef";
        for(auto byte:digest){hash+=hex[byte>>4];hash+=hex[byte&15];}
        fields.insert(fields.end(),{std::to_string(info.originalChannels),std::to_string(info.groups.size()),hash,
            std::to_string(info.mutedGroups),std::to_string(info.soloGroups),real(info.stereoScale)});
        for(const auto& group:info.groups) {
            fields.insert(fields.end(),{group.id.data(),group.origin ? std::to_string(group.origin->value) : "-1",std::to_string(group.volume),std::to_string(group.pan),std::to_string(group.surroundPan),std::to_string(group.flags),std::to_string(group.channels.size())});
            for(auto channel:group.channels)fields.push_back(std::to_string(channel));
        }
        return packet(env,fields);
    }catch(...){return packet(env,{"1",std::to_string(static_cast<int>(SourceStatus::ResourceLimit))});}
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_source_NativeSourceCalls_setStreamGroups(JNIEnv*,jobject,jlong value,jlong mute,jlong solo) {
    if(mute<0 || solo<0 || std::uint64_t(mute)>UINT32_MAX || std::uint64_t(solo)>UINT32_MAX)return static_cast<jint>(SourceStatus::InvalidRequest);
    return static_cast<jint>(player2::api::setAudioStreamGroups(handle(value),static_cast<std::uint32_t>(mute),static_cast<std::uint32_t>(solo)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mylittleblover_player2_platform_audio_NativeAudioLibrary_setLinearStreamGroups(JNIEnv* env,jobject,jlong value,jlong mute,jlong solo,jbyteArray expectedRoot) {
    if(mute<0 || solo<0 || std::uint64_t(mute)>UINT32_MAX || std::uint64_t(solo)>UINT32_MAX)return static_cast<jint>(player2::api::EngineStatus::InvalidRequest);
    if(!expectedRoot || env->GetArrayLength(expectedRoot)!=32)return static_cast<jint>(player2::api::EngineStatus::InvalidRequest);
    std::array<std::uint8_t,32> digest{};env->GetByteArrayRegion(expectedRoot,0,32,reinterpret_cast<jbyte*>(digest.data()));
    if(env->ExceptionCheck())return static_cast<jint>(player2::api::EngineStatus::InvalidRequest);
    return static_cast<jint>(player2::api::setLinearStreamGroups(reinterpret_cast<player2::api::EngineHandle*>(value),static_cast<std::uint32_t>(mute),static_cast<std::uint32_t>(solo),&digest));
}
