#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <vector>

namespace player2::audio {
// AIFF1.3 markers are boundaries BETWEEN sample frames, including duration.
// Their end position is already exclusive; unlike WAVE SMPL, do not add one.
template<class Bytes> std::optional<SourceLoopMetadata> aiffMarkers(Bytes& input,
    std::size_t mark,std::size_t markSize,std::size_t inst,std::size_t instSize,
    std::uint64_t frames,const DecodeLimits& limits,unsigned chunks) {
    std::vector<std::pair<unsigned,std::uint32_t>> markers;
    if(mark) {
        if(markSize<2)fail(SourceStatus::MalformedSource);
        const unsigned count=input.u16(mark,true);
        if(count>limits.maximumEvents-chunks || std::uint64_t(count)*32>limits.maximumDecodedBytes-16384)
            fail(SourceStatus::ResourceLimit);
        markers.reserve(count);auto p=mark+2;const auto end=mark+markSize;
        for(unsigned n=0;n<count;++n) {
            if(end-p<7)fail(SourceStatus::MalformedSource);
            const auto id=input.u16(p,true);const auto frame=input.u32(p+2,true);
            if(!id || id>32767)fail(SourceStatus::MalformedSource);
            if(frame>frames)fail(SourceStatus::SourceLoopBounds);
            const std::size_t name=input.u8(p+6),stored=7+name+((1+name)&1);
            if(stored>end-p)fail(SourceStatus::MalformedSource);
            markers.emplace_back(id,frame);p+=stored;
        }
        if(p!=end)fail(SourceStatus::MalformedSource);
        std::sort(markers.begin(),markers.end());
        for(std::size_t n=1;n<markers.size();++n)if(markers[n].first==markers[n-1].first)fail(SourceStatus::MalformedSource);
    }
    if(!inst)return std::nullopt;
    if(instSize!=20)fail(SourceStatus::MalformedSource);
    const auto detune=input.u8(inst+1);
    if(input.u8(inst)>127 || (detune>50 && detune<206) || input.u8(inst+2)>input.u8(inst+3) ||
        input.u8(inst+3)>127 || !input.u8(inst+4) || input.u8(inst+4)>input.u8(inst+5) || input.u8(inst+5)>127)
        fail(SourceStatus::MalformedSource);
    const auto mode=input.u16(inst+8,true);
    // SourceLoopMetadata represents one forward range, not sampler key-release behavior.
    if(mode>1 || input.u16(inst+14,true))fail(SourceStatus::UnsupportedCodec);
    if(!mode)return std::nullopt; // Disabled-loop marker IDs are explicitly ignored by AIFF.
    auto position=[&](unsigned id) {
        const auto found=std::lower_bound(markers.begin(),markers.end(),std::make_pair(id,std::uint32_t{0}));
        if(found==markers.end() || found->first!=id)fail(SourceStatus::SourceLoopBounds);
        return found->second;
    };
    const auto begin=position(input.u16(inst+10,true)),end=position(input.u16(inst+12,true));
    if(begin>=end)fail(SourceStatus::SourceLoopBounds);
    return SourceLoopMetadata{{begin},{end}};
}
}
