#include "audio/source/SourceHandle.hpp"
namespace player2::api {
audio::SourceStatus audioStreamInspection(const AudioSourceHandle* handle,audio::SourceStreamInfo& output,std::array<std::uint8_t,32>& digest)noexcept {
    if(!handle || !handle->source)return audio::SourceStatus::InvalidRequest;
    const auto* info=handle->source->streamInspection();if(!info)return audio::SourceStatus::UnsupportedFormat;
    try{auto copy=*info;output=std::move(copy);digest=handle->encodedRootSha256;return audio::SourceStatus::Ok;}
    catch(const std::bad_alloc&){return audio::SourceStatus::ResourceLimit;}
    catch(...){return audio::SourceStatus::MalformedSource;}
}
audio::SourceStatus setAudioStreamGroups(AudioSourceHandle* handle,std::uint32_t mute,std::uint32_t solo)noexcept {
    if(!handle || !handle->source)return audio::SourceStatus::InvalidRequest;
    return handle->source->setStreamGroups(mute,solo);
}
}
