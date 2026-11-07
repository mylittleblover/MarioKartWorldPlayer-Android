#pragma once
#include "api/AudioSourceTypes.hpp"
#include <vector>

namespace player2::audio {
// Original file channel groups, distinct from Authoring MusicTracks and buses.
// channel indexes are original sample-lane identities, not output speaker roles.
struct SourceStreamGroup {
    std::array<char,129> id{};
    std::optional<SourceByteOffset> origin;
    std::vector<std::uint8_t> channels;
    std::uint8_t volume{127},pan{64},surroundPan{},flags{};
};
struct SourceStreamInfo {
    unsigned originalChannels{};
    std::uint32_t mutedGroups{},soloGroups{};
    double stereoScale{1};
    std::vector<SourceStreamGroup> groups;
};
}
