#include "api/Mo3Api.hpp"
#include "audio/source/SourceHandle.hpp"
#include "decoder/Mo3Format.hpp"
#include <algorithm>

namespace player2::api {
using audio::SourceStatus;
namespace {
const audio::Mo3Format* format(const AudioSourceHandle* handle)noexcept{return handle && handle->source ? handle->source->moduleMo3Format() : nullptr;}
template<class T> SourceRenderResult page(const AudioSourceHandle* handle,const std::vector<T>* records,std::size_t offset,T* output,std::size_t capacity)noexcept {
    if(!handle || !output || !capacity || capacity>audio::MaximumSequencePage)return {SourceStatus::InvalidRequest,0};
    if(!records)return {SourceStatus::UnsupportedFormat,0};
    if(offset>records->size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,records->size()-offset);
    if(count)std::copy_n(records->data()+offset,count,output);
    return {SourceStatus::Ok,count};
}
}
SourceRenderResult audioMo3Timeline(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3PlaybackRow* output,std::size_t capacity)noexcept {
    return page(handle,handle && handle->source ? handle->source->moduleMo3Timeline() : nullptr,offset,output,capacity);
}
SourceStatus audioMo3Summary(const AudioSourceHandle* handle,audio::Mo3Summary& summary)noexcept {
    summary={};if(!handle)return SourceStatus::InvalidRequest;
    const auto* data=format(handle);if(!data)return SourceStatus::UnsupportedFormat;
    summary=data->summary;return SourceStatus::Ok;
}
SourceRenderResult audioMo3Orders(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3Order* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);return page(handle,data ? &data->orders : nullptr,offset,output,capacity);
}
SourceRenderResult audioMo3Patterns(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3Pattern* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);return page(handle,data ? &data->patterns : nullptr,offset,output,capacity);
}
SourceRenderResult audioMo3Cells(const AudioSourceHandle* handle,std::uint32_t pattern,std::size_t offset,audio::Mo3Cell* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);
    if(data && pattern>=data->cells.size())return {SourceStatus::InvalidRequest,0};
    return page(handle,data ? &data->cells[pattern] : nullptr,offset,output,capacity);
}
SourceRenderResult audioMo3Instruments(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3Instrument* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);return page(handle,data ? &data->instruments : nullptr,offset,output,capacity);
}
SourceStatus audioMo3Envelope(const AudioSourceHandle* handle,std::uint32_t instrument,audio::Mo3EnvelopeKind kind,audio::Mo3Envelope& output)noexcept {
    output={};if(!handle)return SourceStatus::InvalidRequest;
    const auto* data=format(handle);if(!data)return SourceStatus::UnsupportedFormat;
    const auto index=static_cast<std::uint32_t>(kind);
    if(instrument>=data->instruments.size() || index>=3)return SourceStatus::InvalidRequest;
    output=data->instruments[instrument].envelopes[index];return SourceStatus::Ok;
}
SourceRenderResult audioMo3Samples(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3Sample* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);return page(handle,data ? &data->samples : nullptr,offset,output,capacity);
}
SourceRenderResult audioMo3Subsongs(const AudioSourceHandle* handle,std::size_t offset,audio::Mo3Subsong* output,std::size_t capacity)noexcept {
    const auto* data=format(handle);return page(handle,data ? &data->songs : nullptr,offset,output,capacity);
}
}
