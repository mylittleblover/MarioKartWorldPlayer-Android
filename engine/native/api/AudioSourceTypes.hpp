#pragma once
#include <cstddef>
#include <cstdint>
#include <optional>
#include <array>

namespace player2::audio {
inline constexpr std::size_t MaximumEncodedSourceBytes=64*1024*1024;
struct SourceFrameCount {std::int64_t value{};};
struct SourceSamplePosition { std::int64_t value{}; };
struct TransportFrame { std::int64_t value{}; };
struct TransportFrameOffset { std::int64_t value{}; };
struct DeviceFrame { std::int64_t value{}; };
struct SourceByteOffset {std::uint64_t value{};};
struct SourceLoopMetadata { SourceSamplePosition start,end; };
struct ByteView { const std::uint8_t* data{}; std::size_t size{}; };
// Generic root-scoped song identity. No invented tracker row/byte origin.
struct SourceSubsong {std::array<char,129> id{};};
struct SourceEntry {std::array<char,129> id{};};
// Stable API IDs; never use declaration order as a JNI or persistence protocol.
enum class SourceKind : std::int32_t { StandardMedia=0,GameStream=1,GameBank=2,Sequence=3,Module=4,Emulated=5,ChipCommand=6 };
enum class SeekSupport : std::int32_t { None=0,Replay=1,Fast=2 };
enum class SampleEncoding : std::int32_t { Unknown=0,PcmUnsigned=1,PcmSigned=2,IeeeFloat=3,DspAdpcm=4,Flac=5,MpegLayer3=6,Vorbis=7,AdxAdpcm=8,Hca=9,Opus=10,ImaAdpcm=11,MsAdpcm=12,G711ALaw=13,G711MuLaw=14,PsAdpcm=15 };
inline constexpr bool validSampleEncoding(SampleEncoding encoding)noexcept {
    switch(encoding) {
        case SampleEncoding::Unknown: case SampleEncoding::PcmUnsigned:
        case SampleEncoding::PcmSigned: case SampleEncoding::IeeeFloat:
        case SampleEncoding::DspAdpcm: case SampleEncoding::Flac:
        case SampleEncoding::MpegLayer3: case SampleEncoding::Vorbis: case SampleEncoding::AdxAdpcm: case SampleEncoding::Hca: case SampleEncoding::Opus: case SampleEncoding::ImaAdpcm: case SampleEncoding::MsAdpcm: case SampleEncoding::G711ALaw: case SampleEncoding::G711MuLaw: case SampleEncoding::PsAdpcm: return true;
    }
    return false;
}
inline constexpr int sourceMetadataWireVersion(SampleEncoding encoding)noexcept {
    if(encoding==SampleEncoding::PsAdpcm)return 10;
    if(encoding==SampleEncoding::MsAdpcm || encoding==SampleEncoding::G711ALaw || encoding==SampleEncoding::G711MuLaw)return 9;
    if(encoding==SampleEncoding::ImaAdpcm)return 8;
    if(encoding==SampleEncoding::Opus)return 7;
    if(encoding==SampleEncoding::Hca)return 6;
    if(encoding==SampleEncoding::AdxAdpcm)return 5;
    return (encoding==SampleEncoding::Flac || encoding==SampleEncoding::MpegLayer3 || encoding==SampleEncoding::Vorbis) ? 4 : encoding==SampleEncoding::DspAdpcm ? 3 : 2;
}
enum class SourceStatus : std::int32_t {
    Ok=0,UnsupportedFormat=1,MalformedSource=2,ResourceLimit=3,UnsupportedCodec=4,
    UnsupportedChannels=5,UnsupportedSampleRate=6,SourceLoopBounds=7,SeekUnsupported=8,
    EntryMissing=9,SubsongMissing=10,VariantMissing=11,InvalidRequest=12,
    InputUnavailable=13,InputChanged=14
};
enum SourceFlag : std::uint32_t {
    Subsong=1u<<0,ContainerEntry=1u<<1,LoopMetadata=1u<<2,KnownDuration=1u<<3,
    Dependencies=1u<<4,StateSnapshot=1u<<5,RealtimeRender=1u<<6,DeterministicRender=1u<<7,
    Streaming=1u<<8,ChannelControl=1u<<9,SequenceInspection=1u<<10
};
struct SourceMetadata {
    const char* providerId{};
    SourceKind kind{};
    std::int32_t sampleRate{},channels{};
    SourceSamplePosition duration;
    SeekSupport seek{SeekSupport::None};
    std::uint32_t flags{};
    std::optional<SourceLoopMetadata> sourceLoop;
    SampleEncoding encoding{SampleEncoding::Unknown};
    std::int32_t bitDepth{};
    const char* selectedSubsongId{}; // source-owned canonical ID, never an enumeration index
};
struct DecodeLimits {
    std::size_t maximumInputBytes{MaximumEncodedSourceBytes},maximumDecodedBytes{128*1024*1024};
    std::size_t maximumEvents{1048576};
    std::int64_t maximumGeneratedFrames{44100LL*60*30};
    // Duration admission is separate from retained decoder/PCM heap. Seconds
    // are converted using each source's own rate, never the Transport rate.
    std::int64_t maximumSourceDurationSeconds{30*60};
};
}
