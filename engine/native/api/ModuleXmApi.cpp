#include "audio/source/XmInspection.hpp"
#include "audio/source/SourceHandle.hpp"
#include "decoder/ModuleXmFormat.hpp"

namespace player2::api {
using audio::SourceStatus;
namespace {
const audio::XmFormat* format(const AudioSourceHandle* handle)noexcept{return handle && handle->source ? handle->source->moduleXmFormat() : nullptr;}
template<class Record> SourceStatus check(const AudioSourceHandle* handle,Record* output,std::size_t capacity)noexcept {
    if(!handle || !output || !capacity || capacity>audio::MaximumSequencePage)return SourceStatus::InvalidRequest;
    return format(handle) ? SourceStatus::Ok : SourceStatus::UnsupportedFormat;
}
std::size_t samples(const audio::XmFormat& data)noexcept {std::size_t count=0;for(const auto& instrument:data.instruments)count+=instrument.samples.size();return count;}
audio::ModuleEnvelope envelope(const audio::XmEnvelope& source)noexcept {
    audio::ModuleEnvelope result{static_cast<std::uint8_t>(source.points.size()),source.type,source.sustain,source.loopStart,source.loopEnd,{},{}};
    for(std::size_t n=0;n<source.points.size();++n){result.ticks[n]=source.points[n].tick;result.values[n]=source.points[n].value;}return result;
}
}
SourceStatus xmSummary(const AudioSourceHandle* handle,audio::ModuleSummary& summary)noexcept {
    const auto* data=format(handle);if(!data)return SourceStatus::UnsupportedFormat;
    summary={};summary.format=audio::ModuleFormatKind::Xm;summary.encodedBytes=data->encodedBytes;summary.encodedRootSha256=handle->encodedRootSha256;
    summary.orders=data->orders.size();summary.patterns=data->patterns.size();summary.channels=data->channels;summary.instruments=data->instruments.size();
    summary.samples=samples(*data);summary.speed=data->speed;summary.tempo=data->tempo;
    for(const auto& pattern:data->patterns){summary.rows=std::max<std::uint32_t>(summary.rows,pattern.rows);summary.cells+=pattern.cells.size();}
    const auto* songs=handle->source->moduleSubsongs();summary.subsongs=songs ? songs->size() : 0;
    std::copy(data->title.begin(),data->title.end(),summary.title.begin());return SourceStatus::Ok;
}
SourceRenderResult xmOrders(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleOrder* output,std::size_t capacity)noexcept {
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);if(offset>data.orders.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data.orders.size()-offset);
    for(std::size_t n=0;n<count;++n)output[n]={static_cast<std::uint32_t>(offset+n),{80+offset+n},data.patterns[data.orders[offset+n]].origin};
    return {SourceStatus::Ok,count};
}
SourceRenderResult xmPatterns(const AudioSourceHandle* handle,std::size_t offset,audio::ModulePattern* output,std::size_t capacity)noexcept {
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);if(offset>data.patterns.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data.patterns.size()-offset);
    for(std::size_t n=0;n<count;++n){const auto& pattern=data.patterns[offset+n];output[n]={pattern.origin,pattern.rows,data.channels};}
    return {SourceStatus::Ok,count};
}
SourceRenderResult xmCells(const AudioSourceHandle* handle,audio::SourceByteOffset origin,std::size_t offset,audio::ModuleCell* output,std::size_t capacity)noexcept {
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);const auto pattern=std::find_if(data.patterns.begin(),data.patterns.end(),[&](const auto& item){return item.origin.value==origin.value;});
    if(pattern==data.patterns.end() || offset>pattern->cells.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,pattern->cells.size()-offset);
    for(std::size_t n=0;n<count;++n) {const auto& cell=pattern->cells[offset+n];output[n]={cell.origin.value_or(origin),static_cast<std::uint32_t>((offset+n)/data.channels),
        static_cast<std::uint32_t>((offset+n)%data.channels),0,cell.instrument,cell.effect,cell.parameter,cell.note,cell.volume,cell.origin.has_value()};}
    return {SourceStatus::Ok,count};
}
SourceRenderResult xmInstruments(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleInstrument* output,std::size_t capacity)noexcept {
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);if(offset>data.instruments.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data.instruments.size()-offset);std::size_t sampleOffset=0;
    for(std::size_t n=0;n<offset;++n)sampleOffset+=data.instruments[n].samples.size();
    for(std::size_t n=0;n<count;++n) {
        const auto& instrument=data.instruments[offset+n];auto& record=output[n];record={};record.origin=instrument.origin;
        record.sampleOffset=sampleOffset;record.sampleCount=instrument.samples.size();record.sampleMap=instrument.sampleMap;
        record.volumeEnvelope=envelope(instrument.volumeEnvelope);record.panningEnvelope=envelope(instrument.panningEnvelope);record.vibrato=instrument.vibrato;record.fadeOut=instrument.fadeOut;
        std::copy(instrument.name.begin(),instrument.name.end(),record.name.begin());
        if(!instrument.samples.empty()) {const auto& first=instrument.samples.front();record.data=first.data;record.frames=first.frames;record.fineTune=first.fineTune;record.volume=first.volume;record.loop=first.loop;}
        sampleOffset+=instrument.samples.size();
    }
    return {SourceStatus::Ok,count};
}
SourceRenderResult xmSamples(const AudioSourceHandle* handle,std::size_t offset,audio::ModuleSample* output,std::size_t capacity)noexcept {
    const auto status=check(handle,output,capacity);if(status!=SourceStatus::Ok)return {status,0};
    const auto& data=*format(handle);if(offset>samples(data))return {SourceStatus::InvalidRequest,0};
    std::size_t index=0,count=0;
    for(const auto& instrument:data.instruments)for(const auto& sample:instrument.samples) {
        if(index++<offset || count==capacity)continue;
        auto& record=output[count++];record={sample.origin,sample.data,sample.frames,sample.fineTune,sample.relativeNote,sample.depth,sample.volume,sample.panning,sample.loopMode,true,sample.loop,{}};
        std::copy(sample.name.begin(),sample.name.end(),record.name.begin());
    }
    return {SourceStatus::Ok,count};
}
}
