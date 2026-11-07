#include "decoder/Mo3Format.hpp"
#include "decoder/ModuleAdmission.hpp"
#include "decoder/ModulePlayback.hpp"
#include "audio/source/SourceDigest.hpp"
#include "audio/source/SourceSequenceData.hpp"

namespace player2::audio {
namespace {
class Mo3FrameSource final:public AudioFrameSource {
    Mo3Format format_;std::vector<std::uint8_t> bytes_;std::unique_ptr<EncodedSourceInput> input_;DecodeLimits limits_;
    ModuleRenderer renderer_{nullptr,openmpt_module_destroy};
    SourceMetadata metadata_;SourceSequenceData inspection_;std::uint32_t seed_{};int selected_{};std::uint64_t muteMask_{},soloMask_{};
    std::vector<Mo3PlaybackRow> timeline_;
    ModuleRenderBlock block_;SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void initialize(const char* requested,const SourceDigest& digest) {
        for(unsigned n=0;n<4;++n)seed_|=std::uint32_t(digest[n])<<(8*n);
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,0);
        if(openmpt_module_get_num_channels(renderer_.get())!=int(format_.summary.channels) ||
            openmpt_module_get_num_patterns(renderer_.get())!=int(format_.summary.patterns) ||
            openmpt_module_get_num_samples(renderer_.get())!=int(format_.summary.samples))fail(SourceStatus::MalformedSource);
        const auto count=openmpt_module_get_num_subsongs(renderer_.get());if(count<1 || count>128)fail(SourceStatus::ResourceLimit);
        auto& songs=format_.songs;songs.reserve(count);
        for(int n=0;n<count;++n) {
            if(!openmpt_module_select_subsong(renderer_.get(),n))fail(SourceStatus::MalformedSource);
            const auto order=openmpt_module_get_current_order(renderer_.get()),row=openmpt_module_get_current_row(renderer_.get());
            if(order<0 || order>=int(format_.orders.size()) || row<0)fail(SourceStatus::MalformedSource);
            const auto pattern=format_.orders[order].pattern;
            if(pattern>=format_.patterns.size() || row>=int(format_.patterns[pattern].rows))fail(SourceStatus::MalformedSource);
            Mo3Subsong song{format_.orders[order].origin,static_cast<std::uint32_t>(order),static_cast<std::uint32_t>(row),{}};
            const auto length=std::snprintf(song.id.data(),song.id.size(),"mo3:structure:order-byte:%llu:row:%u",static_cast<unsigned long long>(song.orderOrigin.value),song.row);
            if(length<1 || static_cast<std::size_t>(length)>=song.id.size())fail(SourceStatus::ResourceLimit);
            if(std::any_of(songs.begin(),songs.end(),[&](const auto& previous){return previous.id==song.id;}))fail(SourceStatus::MalformedSource);
            songs.push_back(song);
        }
        if(requested) {
            const auto found=std::find_if(songs.begin(),songs.end(),[&](const auto& song){return std::strcmp(song.id.data(),requested)==0;});
            if(found==songs.end())fail(SourceStatus::SubsongMissing);
            selected_=static_cast<int>(found-songs.begin());
        }
        format_.summary.subsongs=songs.size();renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
        // Admission already renders the selected song once. Observe actual tick
        // boundaries in that pass, retaining repeated Order occurrences separately.
        SourceSamplePosition duration;std::array<float,1024> scratch{};
        for(;;) {
            const auto begin=duration;
            const auto got=openmpt_module_read_interleaved_float_stereo(renderer_.get(),44100,1,scratch.data());
            const auto error=openmpt_module_error_get_last(renderer_.get());
            if(error!=OPENMPT_ERROR_OK)fail(error==OPENMPT_ERROR_OUT_OF_MEMORY ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource);
            if(!got)break;
            if(got!=1 || !std::isfinite(scratch[0]) || !std::isfinite(scratch[1]))fail(SourceStatus::MalformedSource);
            const auto order=openmpt_module_get_current_order(renderer_.get()),row=openmpt_module_get_current_row(renderer_.get());
            if(order<0 || order>=int(format_.orders.size()) || row<0)fail(SourceStatus::MalformedSource);
            const auto pattern=format_.orders[order].pattern;
            if(pattern>=format_.patterns.size() || row>=int(format_.patterns[pattern].rows))fail(SourceStatus::MalformedSource);
            if(timeline_.empty() || timeline_.back().order!=unsigned(order) || timeline_.back().row!=unsigned(row)) {
                if(timeline_.size()>=limits_.maximumEvents || (timeline_.size()+1)*3*sizeof(Mo3PlaybackRow)>limits_.maximumDecodedBytes-format_.workingBytes)fail(SourceStatus::ResourceLimit);
                if(!timeline_.empty())timeline_.back().end=begin;
                const auto tempo=openmpt_module_get_current_tempo2(renderer_.get());
                const auto speed=openmpt_module_get_current_speed(renderer_.get());
                if(!std::isfinite(tempo) || tempo<=0 || tempo>65535 || speed<1 || speed>65535)fail(SourceStatus::MalformedSource);
                timeline_.push_back({begin,{},unsigned(order),pattern,unsigned(row),tempo,unsigned(speed),{}});
            }
            // Deterministic source-row peak analysis, not callback/device metering.
            for(unsigned channel=0;channel<format_.summary.channels;++channel) {
                const auto level=openmpt_module_get_current_channel_vu_mono(renderer_.get(),channel);
                if(!std::isfinite(level) || level<0)fail(SourceStatus::MalformedSource);
                const auto peak=static_cast<std::uint8_t>(std::lround(std::min(1.0f,level)*255));
                timeline_.back().channelPeaks[channel]=std::max(timeline_.back().channelPeaks[channel],peak);
            }
            ++duration.value;
            auto remaining=openmpt_module_ctl_get_integer(renderer_.get(),"player2.tick_remaining");
            if(remaining<0 || remaining>44100*60)fail(SourceStatus::MalformedSource);
            while(remaining>0) {
                const auto requested=std::min<std::int64_t>(512,remaining);
                const auto count=openmpt_module_read_interleaved_float_stereo(renderer_.get(),44100,requested,scratch.data());
                if(count!=static_cast<std::size_t>(requested))fail(SourceStatus::MalformedSource);
                for(std::size_t i=0;i<count*2;++i)if(!std::isfinite(scratch[i]))fail(SourceStatus::MalformedSource);
                duration.value+=count;remaining-=count;
            }
            if(duration.value>limits_.maximumGeneratedFrames)fail(SourceStatus::ResourceLimit);
            if(openmpt_module_error_get_last(renderer_.get())!=OPENMPT_ERROR_OK)fail(SourceStatus::MalformedSource);
        }
        if(timeline_.empty() || !duration.value)fail(SourceStatus::MalformedSource);
        timeline_.back().end=duration;
        metadata_={"native.module.mo3",SourceKind::Module,44100,2,duration,SeekSupport::Replay,KnownDuration|DeterministicRender|Subsong|SequenceInspection|Streaming|ChannelControl,std::nullopt};
        metadata_.selectedSubsongId=songs[selected_].id.data();
        const auto restartOrder=openmpt_module_get_restart_order(renderer_.get(),selected_);
        const auto restartRow=openmpt_module_get_restart_row(renderer_.get(),selected_);
        const auto restart=std::find_if(timeline_.begin(),timeline_.end(),[&](const auto& point){return int(point.order)==restartOrder && int(point.row)==restartRow;});
        if(restart!=timeline_.end()) {metadata_.sourceLoop=SourceLoopMetadata{restart->begin,duration};metadata_.flags|=LoopMetadata;}
        inspection_.summary={SequenceKind::Module,44100,int(format_.summary.channels),format_.summary.cells,std::nullopt,digest};
        renderer_=mixedRenderer(0,0);
    }
    ModuleRenderer mixedRenderer(std::uint64_t mute,std::uint64_t solo) {
        auto next=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_,true);
        if(!next.channels(mute,solo))fail(SourceStatus::UnsupportedCodec);
        return next;
    }
public:
    Mo3FrameSource(ByteView bytes,const DecodeLimits& limits,const char* subsong):bytes_(bytes.data,bytes.data+bytes.size),limits_(limits) {
        SourceBytes input(bytes,limits);format_=parseMo3Format(input,limits);SourceDigest digest{};
        if(!sourceSha256(bytes,digest))fail(SourceStatus::ResourceLimit);
        initialize(subsong,digest);
    }
    Mo3FrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsong):input_(std::move(input)),limits_(limits) {
        if(!input_)fail(SourceStatus::InvalidRequest);
        EncodedSourceBytes bytes(*input_,limits);format_=parseMo3Format(bytes,limits);
        SourceDigest digest{};const auto status=sourceSha256(*input_,limits,digest);if(status!=SourceStatus::Ok)fail(status);initialize(subsong,digest);
    }
    unsigned moduleChannelCount()const noexcept override{return format_.summary.channels;}
    SourceStatus setModuleChannels(std::uint64_t mute,std::uint64_t solo)noexcept override {
        const auto count=moduleChannelCount();
        if(count<64 && ((mute|solo)>>count))return SourceStatus::InvalidRequest;
        try {
            auto next=mixedRenderer(mute,solo);renderer_=std::move(next);
            muteMask_=mute;soloMask_=solo;block_.clear();position_={0};status_=SourceStatus::Ok;return status_;
        }catch(const SourceFailure& failure){return failure.status;}
         catch(...){return SourceStatus::ResourceLimit;}
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    const std::vector<Mo3PlaybackRow>* moduleMo3Timeline()const noexcept override{return &timeline_;}
    const Mo3Format* moduleMo3Format()const noexcept override{return &format_;}
    const SourceSequenceData* sequenceInspection()const noexcept override{return &inspection_;}
    std::size_t render(float* output,std::size_t frames)noexcept override{return renderModuleFrames(renderer_,block_,position_,status_,metadata_.duration.value,output,frames);}
    SourceStatus seek(SourceSamplePosition target)noexcept override{return replayModuleSeek(renderer_,block_,position_,status_,metadata_.duration.value,target,[&]{return mixedRenderer(muteMask_,soloMask_);});}
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openMo3(ByteView bytes,const DecodeLimits& limits,const char* subsong){SourceBytes checked(bytes,limits);return std::make_unique<Mo3FrameSource>(bytes,limits,subsong);}
std::unique_ptr<AudioFrameSource> openMo3(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsong){return std::make_unique<Mo3FrameSource>(std::move(input),limits,subsong);}
}
