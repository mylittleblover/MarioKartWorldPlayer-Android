#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/MsAdpcm.hpp"
#include <algorithm>
#include <array>
#include <cstdio>
#include <vector>

namespace player2::audio {
struct XwbSample {SourceMetadata metadata;std::size_t start{},size{};unsigned width{};std::size_t blockAlign{},blockFrames{};};
struct XwbIndex {std::vector<XwbSample> samples;std::vector<SourceEntry> entries;bool big{};};
// XACT3 standard metadata, header44. Compact/compressed/version-hijack formats
// must never fall through to ordinary PCM decoding.
template<class Bytes> XwbIndex parseXwb(Bytes& in,const DecodeLimits& limits) {
    const bool big=in.magic(0,"DNBW");if(!big && !in.magic(0,"WBND"))fail(SourceStatus::UnsupportedFormat);
    in.need(0,52);const auto tool=in.u32(4,big),version=in.u32(8,big);
    if(tool<42 || tool>46 || version!=44)fail(SourceStatus::UnsupportedCodec);
    struct Span {std::size_t at{},size{};};std::array<Span,5> segments{};
    std::vector<std::pair<std::size_t,std::size_t>> directory{{0,52}};
    for(unsigned n=0;n<5;++n) {
        const auto at=in.u32(12+n*8,big),size=in.u32(16+n*8,big);in.need(at,size);
        if(size){if(at<52)fail(SourceStatus::MalformedSource);directory.emplace_back(at,std::size_t(at)+size);}
        else if(at)fail(SourceStatus::UnsupportedCodec);
        segments[n]={at,size};
    }
    if(segments[0].size!=96)fail(SourceStatus::UnsupportedCodec);
    const auto bank=segments[0].at;const auto flags=in.u32(bank,big),count=in.u32(bank+4,big);
    if(flags&~0x50001u || in.u32(bank+72,big)!=24 || in.u32(bank+84,big) || segments[2].size)fail(SourceStatus::UnsupportedCodec);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>limits.maximumEvents || count>(limits.maximumDecodedBytes-16384)/512)fail(SourceStatus::ResourceLimit);
    if(segments[1].size!=std::uint64_t(count)*24 || !segments[4].size)fail(SourceStatus::MalformedSource);
    const auto alignment=in.u32(bank+80,big);if(alignment<4 || alignment>32768 || (alignment&(alignment-1)))fail(SourceStatus::UnsupportedCodec);
    if(segments[4].at%alignment)fail(SourceStatus::MalformedSource);
    const auto nameSize=in.u32(bank+76,big);const bool names=flags&0x10000;
    if(names ? nameSize!=64 || segments[3].size!=std::uint64_t(count)*64 : nameSize || segments[3].size)fail(SourceStatus::UnsupportedCodec);
    bool ended=false;for(unsigned n=0;n<64;++n)ended=ended || in.u8(bank+8+n)==0;if(!ended)fail(SourceStatus::MalformedSource);
    if(names)for(unsigned entry=0;entry<count;++entry){ended=false;for(unsigned n=0;n<64;++n)ended=ended || in.u8(segments[3].at+entry*64+n)==0;if(!ended)fail(SourceStatus::MalformedSource);}
    std::sort(directory.begin(),directory.end());std::size_t end=0;
    for(const auto& span:directory) {
        if(span.first<end)fail(SourceStatus::MalformedSource);
        for(auto p=end;p<span.first;++p)if(in.u8(p))fail(SourceStatus::UnsupportedCodec);
        end=span.second;
    }
    if(end!=in.size())fail(SourceStatus::UnsupportedCodec);
    XwbIndex result;result.big=big;result.samples.reserve(count);result.entries.resize(count);
    std::vector<std::pair<std::size_t,std::size_t>> payloads;payloads.reserve(count);
    for(unsigned n=0;n<count;++n) {
        const auto at=segments[1].at+n*24;const auto info=in.u32(at,big),format=in.u32(at+4,big);
        const unsigned tag=format&3;
        if(tag!=0 && tag!=2)fail(SourceStatus::UnsupportedCodec);
        if(tag==2 && (big || (format>>31)))fail(SourceStatus::UnsupportedCodec);
        const unsigned channels=(format>>2)&7,rate=(format>>5)&0x3ffff,width=tag==2 ? 0 : (format>>31) ? 2 : 1;
        validateFormat(rate,channels);const auto frames=info>>4;boundStreamDuration(frames,rate,limits);
        const auto encodedAlign=(format>>23)&255;
        if(!tag && encodedAlign!=width*channels)fail(SourceStatus::MalformedSource);
        const std::size_t blockAlign=tag==2 ? (encodedAlign+22)*channels : 0;
        const std::size_t blockFrames=tag==2 ? 2+(blockAlign-7*channels)*2/channels : 0;
        const auto offset=in.u32(at+8,big),size=in.u32(at+12,big),first=in.u32(at+16,big),length=in.u32(at+20,big);
        const auto encoded=tag==2 ? ((std::uint64_t(frames)+blockFrames-1)/blockFrames)*blockAlign : std::uint64_t(frames)*channels*width;
        if(offset%alignment || offset>segments[4].size || size>segments[4].size-offset || encoded!=size)fail(SourceStatus::MalformedSource);
        const auto last=std::uint64_t(first)+length;std::optional<SourceLoopMetadata> loop;
        if(length) {
            if(last>frames)fail(SourceStatus::SourceLoopBounds);
            if(!(info&8))loop=SourceLoopMetadata{{first},{static_cast<std::int64_t>(last)}};
        } else if(first)fail(SourceStatus::SourceLoopBounds);
        SourceMetadata metadata{"native.xact-xwb",SourceKind::GameBank,static_cast<std::int32_t>(rate),static_cast<std::int32_t>(channels),{frames},SeekSupport::Fast,
            ContainerEntry|KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,tag==2 ? SampleEncoding::MsAdpcm : width==1 ? SampleEncoding::PcmUnsigned : SampleEncoding::PcmSigned,static_cast<std::int32_t>(tag==2 ? 4 : width*8)};
        const auto start=segments[4].at+offset;result.samples.push_back({metadata,start,size,width,blockAlign,blockFrames});payloads.emplace_back(start,start+size);
        std::snprintf(result.entries[n].id.data(),result.entries[n].id.size(),"xwb:wave:%u",n);
    }
    std::sort(payloads.begin(),payloads.end());end=segments[4].at;std::pair<std::size_t,std::size_t> previous{};
    for(const auto& span:payloads) {
        if(span.first<end && span!=previous)fail(SourceStatus::MalformedSource);
        for(auto p=end;p<span.first;++p)if(in.u8(p))fail(SourceStatus::UnsupportedCodec);
        end=std::max(end,span.second);previous=span;
    }
    for(auto p=end;p<segments[4].at+segments[4].size;++p)if(in.u8(p))fail(SourceStatus::UnsupportedCodec);
    return result;
}
}
