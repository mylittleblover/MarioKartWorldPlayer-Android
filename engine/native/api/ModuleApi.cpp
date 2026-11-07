#include "api/AudioSourceApi.hpp"
#include "audio/source/SourceHandle.hpp"
#include "decoder/ModuleModFormat.hpp"
#include "audio/source/XmInspection.hpp"
#include <algorithm>

namespace player2::api {
using audio::SourceStatus;
namespace {
const audio::ModFormat* format(const AudioSourceHandle* handle)noexcept{return handle && handle->source ? handle->source->moduleFormat() : nullptr;}
template<class Record> SourceStatus check(const AudioSourceHandle* handle,Record* output,std::size_t capacity)noexcept {
    if(!handle || !output || capacity==0 || capacity>audio::MaximumSequencePage)return SourceStatus::InvalidRequest;
    return (format(handle) || handle->source->moduleXmFormat()) ? SourceStatus::Ok : SourceStatus::UnsupportedFormat;
}
}
SourceStatus audioModuleSummary(const AudioSourceHandle* handle,audio::ModuleSummary& summary)noexcept {
    summary={};if(!handle)return SourceStatus::InvalidRequest;
    const auto* data=format(handle);if(!data)return xmSummary(handle,summary);
    summary={static_cast<std::uint32_t>(data->orders.size()),static_cast<std::uint32_t>(data->patterns),64,4,31,handle->encodedRootSha256,0,{}};
    const auto* songs=handle->source->moduleSubsongs();if(songs)summary.subsongs=static_cast<std::uint32_t>(songs->size());
    std::copy(data->title.begin(),data->title.end(),summary.title.begin());return SourceStatus::Ok;
}
SourceRenderResult audioModuleSubsongs(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleSubsong* output,std::size_t capacity)noexcept {
    if(!handle || !output || capacity==0 || capacity>audio::MaximumSequencePage)return {SourceStatus::InvalidRequest,0};
    const auto* songs=handle->source->moduleSubsongs();if(!songs)return {SourceStatus::UnsupportedFormat,0};
    if(offset>songs->size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,songs->size()-offset);if(count)std::copy_n(songs->data()+offset,count,output);return {SourceStatus::Ok,count};
}
SourceRenderResult audioModuleOrders(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleOrder* output,std::size_t capacity)noexcept {
    if(handle && handle->source->moduleXmFormat())return xmOrders(handle,offset,output,capacity);
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto* data=format(handle);if(offset>data->orders.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data->orders.size()-offset);
    for(std::size_t n=0;n<count;++n){const auto p=offset+n;output[n]={static_cast<std::uint32_t>(p),{952+p},{1084+std::uint64_t(data->orders[p])*1024}};}
    return {SourceStatus::Ok,count};
}
SourceRenderResult audioModulePatterns(const AudioSourceHandle* handle,std::size_t offset,audio::ModulePattern* output,std::size_t capacity)noexcept {
    if(handle && handle->source->moduleXmFormat())return xmPatterns(handle,offset,output,capacity);
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto* data=format(handle);if(offset>data->patterns)return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data->patterns-offset);
    for(std::size_t n=0;n<count;++n)output[n]={{1084+(offset+n)*1024},64,4};
    return {SourceStatus::Ok,count};
}
SourceRenderResult audioModuleCells(const AudioSourceHandle* handle,audio::SourceByteOffset pattern,std::size_t offset,audio::ModuleCell* output,std::size_t capacity)noexcept {
    if(handle && handle->source->moduleXmFormat())return xmCells(handle,pattern,offset,output,capacity);
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto* data=format(handle);
    if(pattern.value<1084 || (pattern.value-1084)%1024 || (pattern.value-1084)/1024>=data->patterns || offset>256)return {SourceStatus::InvalidRequest,0};
    const auto begin=(pattern.value-1084)/4;const auto count=std::min(capacity,256-offset);
    for(std::size_t n=0;n<count;++n){const auto& cell=data->cells[begin+offset+n];output[n]={cell.origin,static_cast<std::uint32_t>((offset+n)/4),static_cast<std::uint32_t>((offset+n)%4),cell.period,cell.instrument,cell.effect,cell.parameter};}
    return {SourceStatus::Ok,count};
}
SourceRenderResult audioModuleInstruments(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleInstrument* output,std::size_t capacity)noexcept {
    if(handle && handle->source->moduleXmFormat())return xmInstruments(handle,offset,output,capacity);
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto* data=format(handle);if(offset>data->samples.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data->samples.size()-offset);
    for(std::size_t n=0;n<count;++n){const auto& sample=data->samples[offset+n];auto& result=output[n];
        result={sample.origin,sample.data,{sample.bytes},sample.fineTune,sample.volume,std::nullopt,{}};
        if(sample.loopBytes)result.loop=audio::SourceLoopMetadata{{sample.loopStart},{sample.loopStart+sample.loopBytes}};
        std::copy(sample.name.begin(),sample.name.end(),result.name.begin());
    }
    return {SourceStatus::Ok,count};
}
SourceRenderResult audioModuleSamples(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleSample* output,std::size_t capacity)noexcept {
    if(handle && handle->source->moduleXmFormat())return xmSamples(handle,offset,output,capacity);
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);if(offset>data.samples.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data.samples.size()-offset);
    for(std::size_t n=0;n<count;++n){const auto& sample=data.samples[offset+n];auto& record=output[n];
        record={sample.origin,sample.data,{sample.bytes},sample.fineTune,0,8,static_cast<std::uint8_t>(sample.volume),128,static_cast<std::uint8_t>(sample.loopBytes ? 1 : 0),false,std::nullopt,{}};
        if(sample.loopBytes)record.loop=audio::SourceLoopMetadata{{sample.loopStart},{sample.loopStart+sample.loopBytes}};
        std::copy(sample.name.begin(),sample.name.end(),record.name.begin());
    }
    return {SourceStatus::Ok,count};
}
}
