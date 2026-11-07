#include "audio/source/AudioFrameSource.hpp"
#include <algorithm>

namespace player2::audio {
std::size_t PcmFrameSource::render(float* output,std::size_t frames) noexcept {
    const auto channels=static_cast<std::size_t>(metadata_.channels);
    const auto remaining=static_cast<std::size_t>(metadata_.duration.value-position_.value);
    const auto rendered=std::min(frames,remaining);
    std::copy_n(pcm_.data()+static_cast<std::size_t>(position_.value)*channels,rendered*channels,output);
    std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);
    position_.value+=static_cast<std::int64_t>(rendered);
    return rendered;
}
SourceStatus PcmFrameSource::seek(SourceSamplePosition position) noexcept {
    if(position.value<0 || position.value>metadata_.duration.value) return SourceStatus::InvalidRequest;
    position_=position; return SourceStatus::Ok;
}
}
