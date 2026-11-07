#pragma once
#include "api/AudioSourceApi.hpp"
#include "audio/source/AudioFrameSource.hpp"
namespace player2::api {
// Private opaque-handle storage. Only Engine implementations may see its frame source.
class AudioSourceHandle {
public:
    std::unique_ptr<audio::AudioFrameSource> source;
    std::array<std::uint8_t,32> encodedRootSha256{};
};
}
