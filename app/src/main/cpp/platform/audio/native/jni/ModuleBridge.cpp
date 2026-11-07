#include <jni.h>
#include "api/AudioSourceApi.hpp"

namespace {
using player2::audio::SourceStatus;
#define MODULE_JNI(name) Java_com_mylittleblover_player2_platform_audio_source_NativeModuleCalls_##name
player2::api::AudioSourceHandle* handle(jlong value)noexcept {
    return reinterpret_cast<player2::api::AudioSourceHandle*>(static_cast<std::intptr_t>(value));
}
int version(jlong value)noexcept {
    player2::audio::ModuleSummary summary;
    return player2::api::audioModuleSummary(handle(value),summary)==SourceStatus::Ok && summary.format==player2::audio::ModuleFormatKind::Xm ? 2 : 1;
}
void envelope(const player2::audio::ModuleEnvelope& item,jlong* p)noexcept {
    p[0]=item.count;p[1]=item.type;p[2]=item.sustain;p[3]=item.loopStart;p[4]=item.loopEnd;
    for(std::size_t n=0;n<12;++n){p[5+n*2]=item.ticks[n];p[6+n*2]=item.values[n];}
}
template<std::size_t Size> jlongArray packet(JNIEnv* env,const std::array<jlong,Size>& fields,std::size_t size) {
    auto output=env->NewLongArray(size);if(output)env->SetLongArrayRegion(output,0,size,fields.data());return output;
}
template<class Record,std::size_t Width,class Query,class Write>
jlongArray page(JNIEnv* env,jlong value,jlong offset,jint maximum,Query query,Write write) {
    std::array<Record,player2::audio::MaximumSequencePage> records{};
    player2::api::SourceRenderResult result{SourceStatus::InvalidRequest,0};
    if(offset>=0 && maximum>0 && static_cast<std::size_t>(maximum)<=records.size())result=query(handle(value),offset,records.data(),maximum);
    std::array<jlong,4+player2::audio::MaximumSequencePage*Width> fields{version(value),static_cast<jlong>(result.status),offset,static_cast<jlong>(result.frames)};
    for(std::size_t n=0;n<result.frames;++n)write(records[n],fields.data()+4+n*Width);
    return packet(env,fields,4+result.frames*Width);
}
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(summary)(JNIEnv* env,jobject,jlong value) {
    player2::audio::ModuleSummary summary;const auto status=player2::api::audioModuleSummary(handle(value),summary);
    const auto v=summary.format==player2::audio::ModuleFormatKind::Xm ? 2 : 1;
    std::array<jlong,42> fields{v,static_cast<jlong>(status),summary.orders,summary.patterns,summary.rows,summary.channels,summary.instruments,summary.subsongs};
    for(std::size_t n=0;n<8;++n){std::uint32_t word=0;for(std::size_t b=0;b<4;++b)word|=std::uint32_t(summary.encodedRootSha256[n*4+b])<<(8*b);fields[8+n]=word;}
    for(std::size_t n=0;n<20;++n)fields[16+n]=static_cast<unsigned char>(summary.title[n]);
    fields[36]=static_cast<jlong>(summary.format);fields[37]=summary.samples;fields[38]=summary.speed;fields[39]=summary.tempo;fields[40]=summary.encodedBytes;fields[41]=summary.cells;
    return packet(env,fields,v==2 ? 42 : 36);
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(orders)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::ModuleOrder,3>(env,value,offset,maximum,player2::api::audioModuleOrders,
        [](const auto& item,jlong* p){p[0]=item.position;p[1]=item.origin.value;p[2]=item.pattern.value;});
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(patterns)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::ModulePattern,3>(env,value,offset,maximum,player2::api::audioModulePatterns,
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.rows;p[2]=item.channels;});
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(cells)(JNIEnv* env,jobject,jlong value,jlong pattern,jlong offset,jint maximum) {
    if(version(value)==2)return page<player2::audio::ModuleCell,10>(env,value,offset,maximum,
        [pattern](auto* source,std::size_t begin,auto* output,std::size_t count){
            if(pattern<0)return player2::api::SourceRenderResult{SourceStatus::InvalidRequest,0};
            return player2::api::audioModuleCells(source,{static_cast<std::uint64_t>(pattern)},begin,output,count);},
        [pattern](const auto& item,jlong* p){p[0]=item.hasEncodedOrigin ? static_cast<jlong>(item.origin.value) : -1;p[1]=item.row;p[2]=item.channel;p[3]=item.period;
            p[4]=item.instrument;p[5]=item.effect;p[6]=item.parameter;p[7]=item.note;p[8]=item.volumeColumn;p[9]=pattern;});
    return page<player2::audio::ModuleCell,7>(env,value,offset,maximum,
        [pattern](auto* source,std::size_t begin,auto* output,std::size_t count){
            if(pattern<0)return player2::api::SourceRenderResult{SourceStatus::InvalidRequest,0};
            return player2::api::audioModuleCells(source,{static_cast<std::uint64_t>(pattern)},begin,output,count);},
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.row;p[2]=item.channel;p[3]=item.period;p[4]=item.instrument;p[5]=item.effect;p[6]=item.parameter;});
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(instruments)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    if(version(value)==2)return page<player2::audio::ModuleInstrument,191>(env,value,offset,maximum,player2::api::audioModuleInstruments,
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.data.value;p[2]=item.frames.value;p[3]=item.fineTune;p[4]=item.volume;p[5]=item.loop ? 1 : 0;
            p[6]=item.loop ? item.loop->start.value : 0;p[7]=item.loop ? item.loop->end.value : 0;for(std::size_t n=0;n<22;++n)p[8+n]=static_cast<unsigned char>(item.name[n]);
            p[30]=item.sampleOffset;p[31]=item.sampleCount;for(std::size_t n=0;n<96;++n)p[32+n]=item.sampleMap[n];
            envelope(item.volumeEnvelope,p+128);envelope(item.panningEnvelope,p+157);for(std::size_t n=0;n<4;++n)p[186+n]=item.vibrato[n];p[190]=item.fadeOut;});
    return page<player2::audio::ModuleInstrument,30>(env,value,offset,maximum,player2::api::audioModuleInstruments,
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.data.value;p[2]=item.frames.value;p[3]=item.fineTune;p[4]=item.volume;p[5]=item.loop ? 1 : 0;
            p[6]=item.loop ? item.loop->start.value : 0;p[7]=item.loop ? item.loop->end.value : 0;for(std::size_t n=0;n<22;++n)p[8+n]=static_cast<unsigned char>(item.name[n]);});
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(samples)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::ModuleSample,34>(env,value,offset,maximum,player2::api::audioModuleSamples,
        [](const auto& item,jlong* p){p[0]=item.origin.value;p[1]=item.data.value;p[2]=item.frames.value;p[3]=item.depth;p[4]=item.loopMode;
            p[5]=item.loop ? item.loop->start.value : 0;p[6]=item.loop ? item.loop->end.value : 0;p[7]=item.fineTune;p[8]=item.relativeNote;
            p[9]=item.volume;p[10]=item.panning;p[11]=item.delta;for(std::size_t n=0;n<22;++n)p[12+n]=static_cast<unsigned char>(item.name[n]);});
}
extern "C" JNIEXPORT jlongArray JNICALL MODULE_JNI(subsongs)(JNIEnv* env,jobject,jlong value,jlong offset,jint maximum) {
    return page<player2::audio::ModuleSubsong,66>(env,value,offset,maximum,player2::api::audioModuleSubsongs,
        [](const auto& item,jlong* p){p[0]=item.orderOrigin.value;p[1]=item.row;for(std::size_t n=0;n<64;++n)p[2+n]=static_cast<unsigned char>(item.id[n]);});
}
