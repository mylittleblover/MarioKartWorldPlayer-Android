#pragma once
#include "api/ModuleTypes.hpp"

namespace player2::audio {
// Offsets in the decompressed MO3 music structure, NOT original file offsets.
// Identity always includes the exact original root digest and provider version.
struct Mo3StructureOffset {std::uint64_t value{};};
enum class Mo3ModuleKind:std::uint32_t {Xm=0,It=1,S3m=2,Mod=3,Mtm=4};
struct Mo3Summary {
    std::uint32_t version{},kind{},channels{},orders{},patterns{},instruments{},samples{},subsongs{},speed{},tempo{};
    std::uint64_t cells{},structureBytes{};
};
struct Mo3Order {std::uint32_t position{},pattern{};Mo3StructureOffset origin;};
struct Mo3Pattern {std::uint32_t index{},rows{},channels{};Mo3StructureOffset origin;};
struct Mo3Command {std::uint8_t opcode{},value{};};
struct Mo3Cell {
    Mo3StructureOffset origin;std::uint32_t row{},channel{},count{};
    std::array<Mo3Command,15> commands{};
};
enum class Mo3EnvelopeKind:std::uint32_t {Volume=0,Pan=1,Pitch=2};
struct Mo3EnvelopePoint {std::uint16_t tick{};std::int16_t value{};};
struct Mo3Envelope {
    Mo3StructureOffset origin;
    std::uint8_t flags{},count{},sustainStart{},sustainEnd{},loopStart{},loopEnd{};
    std::array<Mo3EnvelopePoint,25> points{};
};
struct Mo3Instrument {
    Mo3StructureOffset origin;std::array<std::uint16_t,120> samples{};
    std::array<Mo3Envelope,3> envelopes{};
};
struct Mo3Sample {
    Mo3StructureOffset origin;SourceByteOffset data;
    std::uint32_t frames{},flags{},loopStart{},loopEnd{},compressedBytes{};
    std::int32_t sharedSample{-1};
};
struct Mo3PlaybackRow {
    SourceSamplePosition begin,end;std::uint32_t order{},pattern{},row{};
    double tempo{};std::uint32_t speed{};std::array<std::uint8_t,64> channelPeaks{};
};
struct Mo3Subsong {Mo3StructureOffset orderOrigin;std::uint32_t order{},row{};std::array<char,64> id{};};
}
