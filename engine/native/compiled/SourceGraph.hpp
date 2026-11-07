#pragma once
#include "compiled/GraphTypes.hpp"
#include "api/AudioSourceTypes.hpp"
#include <optional>

namespace player2 {
// Expected original source content/selection only. No decoder, Data repository
// pointer or Platform resource enters a Compiled Playback Graph.
struct CompiledSourceReference {
    std::string assetId;
    std::optional<std::string> entryId,subsongId,variantId;
};
struct Asset {
    int index{-1};std::string path;int sampleRate{},channels{};
    Frame frames{},lookAhead{};std::array<std::uint8_t,32> sha256{};bool hasDigest{};
    std::optional<CompiledSourceReference> source{};
    audio::SourceFrameCount sourceLookAhead()const noexcept{return {lookAhead};}
};
}
