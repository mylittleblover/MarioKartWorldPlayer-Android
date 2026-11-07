#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <vector>

namespace player2::audio {
struct Afs2Entry {std::uint16_t waveId{};std::size_t start{},end{};};
// Structural index only. Cue/ACB references and codec admission are separate.
template<class Bytes> std::vector<Afs2Entry> parseAfs2Index(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"AFS2"))fail(SourceStatus::UnsupportedFormat);
    bytes.need(0,16);
    if(bytes.u8(4)!=1 && bytes.u8(4)!=2)fail(SourceStatus::UnsupportedCodec);
    const auto width=bytes.u8(5);const auto idStride=bytes.u16(6),alignment=bytes.u16(12);
    if((width!=2 && width!=4) || (idStride!=2 && idStride!=4) || !alignment || alignment>4096 ||
        (alignment&(alignment-1)) || bytes.u16(14))fail(SourceStatus::UnsupportedCodec);
    const auto count=bytes.u32(8);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>(limits.maximumDecodedBytes-16384)/256)fail(SourceStatus::ResourceLimit);
    const std::size_t ids=16,offsets=ids+std::size_t(count)*idStride,end=offsets+(std::size_t(count)+1)*width;
    bytes.need(0,end);
    const auto offset=[&](unsigned n){return width==2 ? std::uint32_t(bytes.u16(offsets+std::size_t(n)*width)) : bytes.u32(offsets+std::size_t(n)*width);};
    const auto aligned=[&](std::uint32_t value){return (std::uint64_t(value)+alignment-1)/alignment*alignment;};
    if(offset(count)!=bytes.size() || offset(0)<end)fail(SourceStatus::MalformedSource);
    std::vector<Afs2Entry> result;result.reserve(count);std::vector<std::uint16_t> waveIds;waveIds.reserve(count);
    for(unsigned n=0;n<count;++n) {
        const auto raw=offset(n),next=offset(n+1);const auto start=aligned(raw);
        const auto last=n+1==count ? std::uint64_t(next) : aligned(next);
        if(raw>next || start>=last || last>bytes.size())fail(SourceStatus::MalformedSource);
        const auto id=bytes.u16(ids+std::size_t(n)*idStride);
        if(idStride==4 && bytes.u16(ids+std::size_t(n)*idStride+2))fail(SourceStatus::UnsupportedCodec);
        waveIds.push_back(id);result.push_back({id,static_cast<std::size_t>(start),static_cast<std::size_t>(last)});
    }
    std::sort(waveIds.begin(),waveIds.end());
    if(std::adjacent_find(waveIds.begin(),waveIds.end())!=waveIds.end())fail(SourceStatus::MalformedSource);
    return result;
}
}
