#include "decoder/ModuleModFormat.hpp"
#include "decoder/ModulePlayback.hpp"
#include "decoder/ModuleAdmission.hpp"
#include "audio/source/SourceDigest.hpp"
#include "libopenmpt/libopenmpt.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdio>

namespace player2::audio {
namespace {
class ModFrameSource final:public AudioFrameSource {
    ModFormat format_;std::vector<std::uint8_t> bytes_;std::uint32_t seed_{};int selected_{};
    std::vector<ModuleSubsong> subsongs_;
    std::unique_ptr<EncodedSourceInput> input_;DecodeLimits limits_;
    ModuleRenderer renderer_{nullptr,openmpt_module_destroy};SourceMetadata metadata_;
    ModuleRenderBlock block_;SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void initialize(const DecodeLimits& limits,const char* subsongId,const SourceDigest& digest) {
        for(int n=0;n<4;++n)seed_|=std::uint32_t(digest[n])<<(8*n);
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
        selected_=discoverModuleSubsongs(renderer_,subsongs_,"mod",subsongId,[&](int order,int row) {
            if(order<0 || static_cast<std::size_t>(order)>=format_.orders.size() || row<0 || row>=64)fail(SourceStatus::MalformedSource);
            return ModuleSubsong{{952+static_cast<std::uint64_t>(order)},static_cast<std::uint32_t>(row),{}};
        });
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
        const auto duration=measureModuleDuration(renderer_,limits).value;
        metadata_={"native.module.mod",SourceKind::Module,44100,2,{duration},SeekSupport::Replay,KnownDuration|DeterministicRender|Subsong|SequenceInspection|Streaming,std::nullopt};
        metadata_.selectedSubsongId=subsongs_[selected_].id.data();
        renderer_=createModuleRenderer(bytes_,input_.get(),limits_,seed_,selected_);
    }
public:
    ModFrameSource(ByteView bytes,const DecodeLimits& limits,const char* subsongId):format_(readModFormat(bytes,limits)),bytes_(bytes.data,bytes.data+bytes.size),limits_(limits) {
        SourceDigest digest{};if(!sourceSha256(bytes,digest))fail(SourceStatus::ResourceLimit);
        initialize(limits,subsongId,digest);
    }
    ModFrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsongId):input_(std::move(input)),limits_(limits) {
        if(!input_)fail(SourceStatus::InvalidRequest);
        format_=readModFormat(*input_,limits);SourceDigest digest{};
        const auto status=sourceSha256(*input_,limits,digest);if(status!=SourceStatus::Ok)fail(status);
        initialize(limits,subsongId,digest);
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    const ModFormat* moduleFormat()const noexcept override{return &format_;}
    const std::vector<ModuleSubsong>* moduleSubsongs()const noexcept override{return &subsongs_;}
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
std::unique_ptr<AudioFrameSource> openMod(ByteView bytes,const DecodeLimits& limits,const char* subsongId){return std::make_unique<ModFrameSource>(bytes,limits,subsongId);}
std::unique_ptr<AudioFrameSource> openMod(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* subsongId){return std::make_unique<ModFrameSource>(std::move(input),limits,subsongId);}
}
