#pragma once
#include "audio/source/AudioFrameSource.hpp"
#include "voice/Voice.hpp"
#include <memory>

namespace player2::audio {
// Worker-only bridge from every Provider to the existing Voice/Mixer substrate.
// Persistent pre-render caches belong to Data; this buffer is temporary Engine state.
std::unique_ptr<AudioBuffer> prepareBuffer(int identity,AudioFrameSource& source,
    std::size_t maximumBytes=128*1024*1024,bool requireDeterministic=false);
}
