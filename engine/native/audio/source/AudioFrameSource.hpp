#pragma once
#include "api/AudioSourceTypes.hpp"
#include "api/ModuleTypes.hpp"
#include "api/EncodedSourceInput.hpp"
#include <memory>
#include <stdexcept>
#include <vector>

namespace player2::audio {
struct SourceSequenceData;
struct SourceStreamInfo;
enum class TrackerKind;
struct ModFormat;
struct XmFormat;
struct Mo3Format;
struct Mo3PlaybackRow;
enum class EmulatedKind;
class SourceFailure : public std::invalid_argument {
public:
    SourceStatus status;
    explicit SourceFailure(SourceStatus value):std::invalid_argument("AUDIO_SOURCE_FAILURE"),status(value) {}
};
[[noreturn]] inline void fail(SourceStatus status) { throw SourceFailure(status); }
// Engine-only contract. Open, seek/replay and destruction run on a worker/control
// thread. Only RealtimeRender-certified sources may render on the audio consumer.
// Other decoders render on control/worker threads. Source loop is metadata only.
class AudioFrameSource {
public:
    virtual ~AudioFrameSource()=default;
    virtual const SourceMetadata& metadata() const noexcept=0;
    virtual SourceSamplePosition position() const noexcept=0;
    virtual std::size_t render(float* output,std::size_t frames) noexcept=0;
    virtual SourceStatus seek(SourceSamplePosition position) noexcept=0;
    virtual void reset() noexcept=0;
    virtual const SourceSequenceData* sequenceInspection()const noexcept{return nullptr;}
    virtual const SourceStreamInfo* streamInspection()const noexcept{return nullptr;}
    virtual SourceStatus setStreamGroups(std::uint32_t,std::uint32_t)noexcept{return SourceStatus::UnsupportedFormat;}
    virtual SourceStatus renderStatus()const noexcept{return SourceStatus::Ok;}
    virtual const ModFormat* moduleFormat()const noexcept{return nullptr;}
    virtual const XmFormat* moduleXmFormat()const noexcept{return nullptr;}
    virtual const std::vector<Mo3PlaybackRow>* moduleMo3Timeline()const noexcept{return nullptr;}
    virtual const Mo3Format* moduleMo3Format()const noexcept{return nullptr;}
    virtual unsigned moduleChannelCount()const noexcept{return 0;}
    // Decode worker/stopped control only. Selection changes invalidate derived PCM.
    virtual SourceStatus setModuleChannels(std::uint64_t,std::uint64_t)noexcept{return SourceStatus::UnsupportedFormat;}
    virtual const std::vector<ModuleSubsong>* moduleSubsongs()const noexcept{return nullptr;}
    virtual const std::vector<SourceSubsong>* sourceSubsongs()const noexcept{return nullptr;}
    virtual const std::vector<SourceEntry>* sourceEntries()const noexcept{return nullptr;}
};
class PcmFrameSource final : public AudioFrameSource {
    SourceMetadata metadata_;
    std::vector<float> pcm_;
    SourceSamplePosition position_;
public:
    PcmFrameSource(SourceMetadata metadata,std::vector<float> pcm):metadata_(metadata),pcm_(std::move(pcm)) {}
    const SourceMetadata& metadata() const noexcept override { return metadata_; }
    SourceSamplePosition position() const noexcept override { return position_; }
    std::size_t render(float* output,std::size_t frames) noexcept override;
    SourceStatus seek(SourceSamplePosition position) noexcept override;
    void reset() noexcept override { position_.value=0; }
};
std::unique_ptr<AudioFrameSource> openWave(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openWave(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openOpus(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openOpus(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openVorbis(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openVorbis(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openMp3(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openMp3(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openFlac(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openFlac(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> projectStreamStereo(std::unique_ptr<AudioFrameSource> source);
std::unique_ptr<AudioFrameSource> openBfstm(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBfstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBfstmDsp(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBfstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBcstm(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBcstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBcstmDsp(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBcstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBrstm(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBrstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBrstmDsp(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBrstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openMod(ByteView bytes,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openXm(ByteView bytes,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openMo3(ByteView bytes,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openMo3(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openXm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openMod(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openTracker(ByteView bytes,const DecodeLimits& limits,TrackerKind kind,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openTracker(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,TrackerKind kind,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openNintendoDsp(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openNintendoDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAdx(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAdx(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openHca(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openHca(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBwav(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBwav(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openNintendoWave(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openNintendoWave(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openSwar(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openSwar(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectSwarEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openXwb(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openXwb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectXwbEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openRwar(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openRwar(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectRwarEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAcb(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openAcb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectAcbEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAwb(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openAwb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectAwbEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openLegacyFsb(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openLegacyFsb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectLegacyFsbEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openFsb5(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openFsb5(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectFsb5Entries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openPck(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openPck(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectPckEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openBnk(ByteView bytes,const DecodeLimits& limits,const char* entryId);
std::unique_ptr<AudioFrameSource> openBnk(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId);
std::vector<SourceEntry> inspectBnkEntries(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAiff(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openAiff(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openVag(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openVag(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
bool probeVgzFm(ByteView bytes)noexcept;
std::unique_ptr<AudioFrameSource> openVgz(ByteView bytes,const DecodeLimits& limits,bool fm=false);
std::unique_ptr<AudioFrameSource> openEmulated(ByteView bytes,const DecodeLimits& limits,EmulatedKind kind,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openEmulated(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,EmulatedKind kind,const char* subsongId=nullptr);
std::unique_ptr<AudioFrameSource> openVgz(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,bool fm=false);
std::unique_ptr<AudioFrameSource> openVgm(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openVgm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
}
