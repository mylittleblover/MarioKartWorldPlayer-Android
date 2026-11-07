#include <jni.h>
#include "api/Mo3Api.hpp"
#include <cstring>

namespace {
using player2::audio::SourceStatus;
#define MO3_JNI(name) Java_com_mylittleblover_player2_platform_audio_source_NativeMo3Calls_##name
auto* handle(jlong value)noexcept{return reinterpret_cast<player2::api::AudioSourceHandle*>(static_cast<std::intptr_t>(value));}
template<std::size_t Size> jlongArray packet(JNIEnv* env,const std::array<jlong,Size>& data,std::size_t count) {
    auto result=env->NewLongArray(count);if(result)env->SetLongArrayRegion(result,0,count,data.data());return result;
}
template<class T,std::size_t Stride,class Read,class Write> jlongArray page(JNIEnv* env,jlong value,jlong offset,jint maximum,Read read,Write write,jlong version=1) {
    std::array<jlong,4+player2::audio::MaximumSequencePage*Stride> data{};data[0]=version;data[1]=static_cast<jlong>(SourceStatus::InvalidRequest);data[2]=offset;
    if(offset<0 || maximum<1 || maximum>static_cast<jint>(player2::audio::MaximumSequencePage))return packet(env,data,4);
    std::array<T,player2::audio::MaximumSequencePage> records{};const auto result=read(handle(value),offset,records.data(),maximum);
    data[1]=static_cast<jlong>(result.status);data[3]=result.frames;
    for(std::size_t n=0;n<result.frames;++n)write(records[n],data.data()+4+n*Stride);
    return packet(env,data,4+result.frames*Stride);
}
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(summary)(JNIEnv* env,jobject,jlong value) {
    player2::audio::Mo3Summary s;const auto status=player2::api::audioMo3Summary(handle(value),s);
    const std::array<jlong,14> data{{1,static_cast<jlong>(status),s.version,s.kind,s.channels,s.orders,s.patterns,s.instruments,s.samples,s.subsongs,s.speed,s.tempo,static_cast<jlong>(s.cells),static_cast<jlong>(s.structureBytes)}};
    return packet(env,data,data.size());
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(orders)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Order,3>(env,value,offset,maximum,player2::api::audioMo3Orders,[](const auto& item,jlong* p){p[0]=item.position;p[1]=item.pattern;p[2]=item.origin.value;});
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(patterns)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Pattern,4>(env,value,offset,maximum,player2::api::audioMo3Patterns,[](const auto& item,jlong* p){p[0]=item.index;p[1]=item.rows;p[2]=item.channels;p[3]=item.origin.value;});
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(cells)(JNIEnv* env,jobject,jlong value,jint pattern,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Cell,34>(env,value,offset,maximum,
        [pattern](auto* source,std::size_t begin,auto* output,std::size_t count){if(pattern<0)return player2::api::SourceRenderResult{SourceStatus::InvalidRequest,0};return player2::api::audioMo3Cells(source,pattern,begin,output,count);},
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.row;p[2]=item.channel;p[3]=item.count;for(unsigned n=0;n<15;++n){p[4+n*2]=item.commands[n].opcode;p[5+n*2]=item.commands[n].value;}});
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(instruments)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Instrument,121>(env,value,offset,maximum,player2::api::audioMo3Instruments,
        [](const auto& item,jlong* p){p[0]=item.origin.value;for(unsigned n=0;n<120;++n)p[n+1]=item.samples[n];});
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(envelope)(JNIEnv* env,jobject,jlong value,jint instrument,jint kind) {
    player2::audio::Mo3Envelope item;auto status=SourceStatus::InvalidRequest;
    if(instrument>=0 && kind>=0 && kind<=2)status=player2::api::audioMo3Envelope(handle(value),instrument,static_cast<player2::audio::Mo3EnvelopeKind>(kind),item);
    std::array<jlong,61> data{{1,static_cast<jlong>(status),instrument,kind,static_cast<jlong>(item.origin.value),item.flags,item.count,item.sustainStart,item.sustainEnd,item.loopStart,item.loopEnd}};
    for(unsigned n=0;n<25;++n){data[11+n*2]=item.points[n].tick;data[12+n*2]=item.points[n].value;}
    return packet(env,data,data.size());
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(samples)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Sample,8>(env,value,offset,maximum,player2::api::audioMo3Samples,
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.data.value;p[2]=item.frames;p[3]=item.flags;p[4]=item.loopStart;p[5]=item.loopEnd;p[6]=item.compressedBytes;p[7]=item.sharedSample;});
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(timeline)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3PlaybackRow,71>(env,value,offset,maximum,player2::api::audioMo3Timeline,
        [](const auto& item,jlong* p){p[0]=item.begin.value;p[1]=item.end.value;p[2]=item.order;p[3]=item.pattern;p[4]=item.row;std::memcpy(p+5,&item.tempo,sizeof(item.tempo));p[6]=item.speed;for(unsigned n=0;n<64;++n)p[7+n]=item.channelPeaks[n];},2);
}
extern "C" JNIEXPORT jlongArray JNICALL MO3_JNI(subsongs)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::Mo3Subsong,67>(env,value,offset,maximum,player2::api::audioMo3Subsongs,
        [](const auto& item,jlong* p){p[0]=item.orderOrigin.value;p[1]=item.order;p[2]=item.row;for(unsigned n=0;n<64;++n)p[n+3]=static_cast<unsigned char>(item.id[n]);});
}
