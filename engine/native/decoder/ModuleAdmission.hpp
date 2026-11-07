#pragma once
#include "decoder/ModuleRenderer.hpp"
#include "api/SequenceTypes.hpp"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>
namespace player2::audio {
// Control/worker-only source admission. This does not own a transport, mixer,
// interactive session or persistent cache; parser facts retain original IDs.
template<class Locate>
inline int discoverModuleSubsongs(ModuleRenderer& renderer,std::vector<ModuleSubsong>& songs,
    const char* formatName,const char* requested,Locate locate) {
    const auto count=openmpt_module_get_num_subsongs(renderer.get());
    if(count<1 || count>128)fail(SourceStatus::ResourceLimit);
    songs.reserve(count);
    for(int n=0;n<count;++n) {
        if(!openmpt_module_select_subsong(renderer.get(),n))fail(SourceStatus::MalformedSource);
        const auto order=openmpt_module_get_current_order(renderer.get()),row=openmpt_module_get_current_row(renderer.get());
        auto song=locate(order,row);
        const auto length=std::snprintf(song.id.data(),song.id.size(),"%s:order-byte:%llu:row:%u",formatName,
            static_cast<unsigned long long>(song.orderOrigin.value),song.row);
        if(length<0 || static_cast<std::size_t>(length)>=song.id.size())fail(SourceStatus::ResourceLimit);
        if(std::any_of(songs.begin(),songs.end(),[&](const auto& previous){return std::strcmp(previous.id.data(),song.id.data())==0;}))fail(SourceStatus::MalformedSource);
        songs.push_back(song);
    }
    if(!requested)return 0;
    const auto found=std::find_if(songs.begin(),songs.end(),[&](const auto& song){return std::strcmp(song.id.data(),requested)==0;});
    if(found==songs.end())fail(SourceStatus::SubsongMissing);
    return static_cast<int>(found-songs.begin());
}
inline SourceSamplePosition measureModuleDuration(ModuleRenderer& renderer,const DecodeLimits& limits) {
    std::array<float,1024> scratch{};SourceSamplePosition duration;
    if(limits.maximumGeneratedFrames<=0)fail(SourceStatus::ResourceLimit);
    for(;;) {
        const auto count=openmpt_module_read_interleaved_float_stereo(renderer.get(),44100,512,scratch.data());
        const auto error=openmpt_module_error_get_last(renderer.get());
        if(error!=OPENMPT_ERROR_OK)fail(error==OPENMPT_ERROR_OUT_OF_MEMORY ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource);
        if(count>scratch.size()/2)fail(SourceStatus::MalformedSource);
        if(count>static_cast<std::uint64_t>(limits.maximumGeneratedFrames-duration.value))fail(SourceStatus::ResourceLimit);
        for(std::size_t n=0;n<count*2;++n)if(!std::isfinite(scratch[n]))fail(SourceStatus::MalformedSource);
        if(!count)break;
        duration.value+=static_cast<std::int64_t>(count);
    }
    if(!duration.value)fail(SourceStatus::MalformedSource);
    return duration;
}
}
