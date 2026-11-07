#include "decoder/TrackerFormat.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/ModuleAdmission.hpp"
#include "decoder/ModulePlayback.hpp"
#include "audio/source/SourceDigest.hpp"

namespace player2::audio {
namespace {
class TrackerSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;DecodeLimits limits_;TrackerFormat format_;
    std::vector<std::uint8_t> empty_;std::vector<ModuleSubsong> songs_;
    std::uint32_t seed_{};int selected_{};unsigned channels_{};
    std::uint64_t mute_{},solo_{};ModuleRenderer renderer_;SourceMetadata metadata_;
    ModuleRenderBlock block_;SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    ModuleRenderer recreate(int song) {
        auto result=createModuleRenderer(empty_,input_.get(),limits_,seed_,song,true);
        const auto count=openmpt_module_get_num_channels(result.get());
        if(count<1 || count>64)fail(SourceStatus::ResourceLimit);
        if(!result.channels(mute_,solo_))fail(SourceStatus::UnsupportedCodec);
        return result;
    }
public:
    TrackerSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,TrackerKind kind,const char* subsong):
        input_(std::move(input)),limits_(limits),format_(readTracker(*input_,limits,kind)) {
        if(limits_.maximumSourceDurationSeconds<=0 || limits_.maximumGeneratedFrames<=0)fail(SourceStatus::ResourceLimit);
        if(limits_.maximumSourceDurationSeconds<=INT64_MAX/44100)
            limits_.maximumGeneratedFrames=std::min(limits_.maximumGeneratedFrames,limits_.maximumSourceDurationSeconds*44100);
        SourceDigest digest{};const auto status=sourceSha256(*input_,limits,digest);if(status!=SourceStatus::Ok)fail(status);
        for(int n=0;n<4;++n)seed_|=std::uint32_t(digest[n])<<(n*8);
        renderer_=recreate(0);channels_=openmpt_module_get_num_channels(renderer_.get());
        if(!channels_ || channels_>64 || openmpt_module_get_num_samples(renderer_.get())>256)fail(SourceStatus::ResourceLimit);
        selected_=discoverModuleSubsongs(renderer_,songs_,kind==TrackerKind::It ? "it" : kind==TrackerKind::S3m ? "s3m" : "ptm",subsong,[&](int order,int row) {
            if(order<0 || static_cast<std::size_t>(order)>=format_.orders.size() || row<0)fail(SourceStatus::MalformedSource);
            const auto pattern=format_.orders[order];
            if(pattern>=format_.rows.size() || static_cast<unsigned>(row)>=format_.rows[pattern])fail(SourceStatus::MalformedSource);
            return ModuleSubsong{{format_.orderOrigin+order},static_cast<std::uint32_t>(row),{}};
        });
        renderer_=recreate(selected_);const auto duration=measureModuleDuration(renderer_,limits_);
        boundStreamDuration(duration.value,44100,limits_);
        metadata_={kind==TrackerKind::It ? "native.module.it" : kind==TrackerKind::S3m ? "native.module.s3m" : "native.module.ptm",SourceKind::Module,44100,2,duration,
            SeekSupport::Replay,KnownDuration|DeterministicRender|Subsong|Streaming|ChannelControl,std::nullopt};
        metadata_.selectedSubsongId=songs_[selected_].id.data();renderer_=recreate(selected_);
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    unsigned moduleChannelCount()const noexcept override{return channels_;}
    const std::vector<ModuleSubsong>* moduleSubsongs()const noexcept override{return &songs_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        return renderModuleFrames(renderer_,block_,position_,status_,metadata_.duration.value,output,frames);
    }
    SourceStatus seek(SourceSamplePosition at)noexcept override {
        return replayModuleSeek(renderer_,block_,position_,status_,metadata_.duration.value,at,[&]{return recreate(selected_);});
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
    SourceStatus setModuleChannels(std::uint64_t mute,std::uint64_t solo)noexcept override {
        if(channels_<64 && ((mute|solo)>>channels_))return SourceStatus::InvalidRequest;
        mute_=mute;solo_=solo;renderer_.reset();status_=SourceStatus::MalformedSource;
        return seek({0});
    }
};
}
std::unique_ptr<AudioFrameSource> openTracker(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,TrackerKind kind,const char* song) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<TrackerSource>(std::move(input),limits,kind,song);
}
std::unique_ptr<AudioFrameSource> openTracker(ByteView bytes,const DecodeLimits& limits,TrackerKind kind,const char* song) {
    return openTracker(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,kind,song);
}
}
