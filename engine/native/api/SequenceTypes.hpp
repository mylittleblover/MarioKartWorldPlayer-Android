#pragma once
#include "api/AudioSourceTypes.hpp"
#include <array>

namespace player2::audio {
enum class SequenceKind : std::int32_t {ChipCommand=0,Module=1};
enum class SequenceEventKind : std::int32_t {ChipWrite=0,Wait=1,End=2};
struct SequenceEvent {
    SourceByteOffset origin;SourceSamplePosition at;SourceFrameCount duration;
    SequenceEventKind kind{};std::int32_t channel{-1};std::uint8_t opcode{},value{};
};
struct SequenceLoop {SourceByteOffset from,to;SourceLoopMetadata samples;};
struct SequenceSummary {
    SequenceKind kind{};std::int32_t sourceRate{},channels{};std::uint64_t events{};
    std::optional<SequenceLoop> loop;std::array<std::uint8_t,32> encodedRootSha256{};
};
inline constexpr std::size_t MaximumSequencePage=256;
}
