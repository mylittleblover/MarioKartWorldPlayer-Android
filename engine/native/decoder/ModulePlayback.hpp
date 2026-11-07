#pragma once
#include "decoder/ModuleRenderer.hpp"
#include <cmath>
namespace player2::audio {
// Worker-only decoded look-ahead, not a second clock or persistent audio cache.
// Kernel float DC tails can depend on call boundaries; use the same 512-frame
// partition as finite-duration measurement for every Module family and caller.
struct ModuleRenderBlock {
    std::array<float,1024> pcm{};
    std::size_t cursor{},count{};
    void clear()noexcept{cursor=count=0;}
};
inline std::size_t renderModuleFrames(ModuleRenderer& renderer,ModuleRenderBlock& block,SourceSamplePosition& position,SourceStatus& status,
    std::int64_t duration,float* output,std::size_t frames) noexcept {
    const auto wanted=std::min<std::size_t>(frames,static_cast<std::size_t>(duration-position.value));std::size_t count=0;
    while(count<wanted && status==SourceStatus::Ok) {
        if(block.cursor==block.count) {
            const auto requested=std::min<std::size_t>(512,static_cast<std::size_t>(duration-position.value));
            block.clear();
            const auto got=renderer ? openmpt_module_read_interleaved_float_stereo(renderer.get(),44100,requested,block.pcm.data()) : 0;
            const auto error=renderer ? openmpt_module_error_get_last(renderer.get()) : OPENMPT_ERROR_UNKNOWN;
            if(got!=requested || error!=OPENMPT_ERROR_OK)status=error==OPENMPT_ERROR_OUT_OF_MEMORY ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource;
            if(status==SourceStatus::Ok && !std::all_of(block.pcm.begin(),block.pcm.begin()+got*2,[](float f){return std::isfinite(f);}))status=SourceStatus::MalformedSource;
            if(status!=SourceStatus::Ok)break;
            block.count=got; // Publish a complete finite stereo block atomically.
        }
        const auto take=std::min(wanted-count,block.count-block.cursor);
        std::copy_n(block.pcm.data()+block.cursor*2,take*2,output+count*2);
        block.cursor+=take;position.value+=static_cast<std::int64_t>(take);count+=take;
    }
    std::fill_n(output+count*2,(frames-count)*2,0.0f);return count;
}
template<class Factory> SourceStatus replayModuleSeek(ModuleRenderer& renderer,ModuleRenderBlock& block,SourceSamplePosition& position,SourceStatus& status,
    std::int64_t duration,SourceSamplePosition target,Factory recreate) noexcept {
    if(target.value<0 || target.value>duration)return SourceStatus::InvalidRequest;
    const auto before=position;
    auto reject=[&](SourceStatus failure) {renderer.reset();block.clear();position=before;status=failure;return status;};
    try {
        if(status!=SourceStatus::Ok || target.value<position.value) {renderer=recreate();block.clear();position={0};status=SourceStatus::Ok;}
        std::array<float,1024> scratch{};
        while(position.value<target.value) {
            const auto count=renderModuleFrames(renderer,block,position,status,duration,scratch.data(),
                static_cast<std::size_t>(std::min<std::int64_t>(512,target.value-position.value)));
            if(status!=SourceStatus::Ok || !count)return reject(status==SourceStatus::Ok ? SourceStatus::MalformedSource : status);
        }
        return SourceStatus::Ok;
    }catch(const SourceFailure& failure){return reject(failure.status);}
    catch(const std::bad_alloc&){return reject(SourceStatus::ResourceLimit);}
    catch(...){return reject(SourceStatus::MalformedSource);}
}
}
