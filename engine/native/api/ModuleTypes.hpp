#pragma once
#include "api/SequenceTypes.hpp"

namespace player2::audio {
enum class ModuleFormatKind:std::uint32_t {Mod=0,Xm=1};
struct ModuleSummary {
    std::uint32_t orders{},patterns{},rows{},channels{},instruments{};
    std::array<std::uint8_t,32> encodedRootSha256{};
    std::uint32_t subsongs{};
    std::array<char,21> title{};
    ModuleFormatKind format{ModuleFormatKind::Mod};std::uint32_t samples{31},speed{6},tempo{125};
    std::uint64_t encodedBytes{},cells{};
};
// Stable within original source content. Library enumeration indices are private.
struct ModuleSubsong {SourceByteOffset orderOrigin;std::uint32_t row{};std::array<char,64> id{};};
struct ModuleOrder {std::uint32_t position{};SourceByteOffset origin,pattern;};
struct ModulePattern {SourceByteOffset origin;std::uint32_t rows{},channels{};};
struct ModuleCell {
    SourceByteOffset origin;std::uint32_t row{},channel{};
    std::uint16_t period{};std::uint8_t instrument{},effect{},parameter{};
    std::uint8_t note{},volumeColumn{};bool hasEncodedOrigin{true};
};
struct ModuleEnvelope {
    std::uint8_t count{},type{},sustain{},loopStart{},loopEnd{};
    std::array<std::uint16_t,12> ticks{},values{};
};
struct ModuleInstrument {
    // frames/loop address this instrument's embedded PCM sample, never the
    // rendered Module timeline. origin/data address the original encoded root.
    SourceByteOffset origin,data;SourceFrameCount frames;
    std::int32_t fineTune{},volume{};std::optional<SourceLoopMetadata> loop;
    std::array<char,23> name{};
    std::uint32_t sampleOffset{},sampleCount{1};std::array<std::uint8_t,96> sampleMap{};
    ModuleEnvelope volumeEnvelope{},panningEnvelope{};
    std::array<std::uint8_t,4> vibrato{};std::uint16_t fadeOut{};
};
struct ModuleSample {
    SourceByteOffset origin,data;SourceFrameCount frames;
    std::int32_t fineTune{},relativeNote{};std::uint8_t depth{},volume{},panning{},loopMode{};bool delta{};
    std::optional<SourceLoopMetadata> loop;std::array<char,23> name{};
};
}
