#include "decoder/ModuleXmFormat.hpp"
#include "decoder/ModulePlayback.hpp"
#include "decoder/ModuleAdmission.hpp"
#include "audio/source/SourceDigest.hpp"
#include "audio/source/SourceSequenceData.hpp"
#include <cmath>
#include <cstdio>

namespace player2::audio {
namespace {
class XmFrameSource final:public AudioFrameSource {
    XmFormat format_;std::vector<std::uint8_t> bytes_;std::unique_ptr<EncodedSourceInput> input_;DecodeLimits limits_;
    std::vector<ModuleSubsong> songs_;std::uint32_t seed_{};int selected_{};
    ModuleRenderer renderer_{nullptr,openmpt_module_destroy};SourceMetadata metadata_;SourceSequenceData inspection_;
    ModuleRenderBlock block_;SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void initialize(const char* subsong,const SourceDigest& digest) {
        for(int n=0;n<4;++n)seed_|=std::uint32_t(digest[n])<<(8*n);
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,0);
        if(openmpt_module_get_num_channels(renderer_.get())!=format_.channels || openmpt_module_get_num_patterns(renderer_.get())!=static_cast<int>(format_.patterns.size()) ||
            openmpt_module_get_num_instruments(renderer_.get())!=static_cast<int>(format_.instruments.size()))fail(SourceStatus::MalformedSource);
        for(std::size_t n=0;n<format_.patterns.size();++n)if(openmpt_module_get_pattern_num_rows(renderer_.get(),n)!=format_.patterns[n].rows)fail(SourceStatus::MalformedSource);
        selected_=discoverModuleSubsongs(renderer_,songs_,"xm",subsong,[&](int order,int row) {
            if(order<0 || static_cast<std::size_t>(order)>=format_.orders.size() || row<0 || row>=format_.patterns[format_.orders[order]].rows)fail(SourceStatus::MalformedSource);
            return ModuleSubsong{{80+static_cast<std::uint64_t>(order)},static_cast<std::uint32_t>(row),{}};
        });
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
        const auto duration=measureModuleDuration(renderer_,limits_).value;
        metadata_={"native.module.xm",SourceKind::Module,44100,2,{duration},SeekSupport::Replay,KnownDuration|DeterministicRender|Subsong|SequenceInspection|Streaming,std::nullopt};
        metadata_.selectedSubsongId=songs_[selected_].id.data();
        std::uint64_t cells=0;for(const auto& pattern:format_.patterns)cells+=pattern.cells.size();
        inspection_.summary={SequenceKind::Module,44100,format_.channels,cells,std::nullopt,digest};
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
    }
public:
    XmFrameSource(ByteView bytes,const DecodeLimits& limits,const char* subsong):format_(readXmFormat(bytes,limits)),bytes_(bytes.data,bytes.data+bytes.size),limits_(limits) {
        SourceDigest digest{};if(!sourceSha256(bytes,digest))fail(SourceStatus::ResourceLimit);initialize(subsong,digest);
    }
    XmFrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsong):input_(std::move(input)),limits_(limits) {
        if(!input_)fail(SourceStatus::InvalidRequest);
        format_=readXmFormat(*input_,limits);SourceDigest digest{};const auto status=sourceSha256(*input_,limits,digest);if(status!=SourceStatus::Ok)fail(status);initialize(subsong,digest);
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    const XmFormat* moduleXmFormat()const noexcept override{return &format_;}
    const std::vector<ModuleSubsong>* moduleSubsongs()const noexcept override{return &songs_;}
    const SourceSequenceData* sequenceInspection()const noexcept override{return &inspection_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        return renderModuleFrames(renderer_,block_,position_,status_,metadata_.duration.value,output,frames);
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        return replayModuleSeek(renderer_,block_,position_,status_,metadata_.duration.value,target,
            [&] {return createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);});
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openXm(ByteView bytes,const DecodeLimits& limits,const char* subsong){return std::make_unique<XmFrameSource>(bytes,limits,subsong);}
std::unique_ptr<AudioFrameSource> openXm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsong){return std::make_unique<XmFrameSource>(std::move(input),limits,subsong);}
}
