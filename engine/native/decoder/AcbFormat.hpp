#pragma once
#include "decoder/Afs2Index.hpp"
#include "decoder/SourceWindowBytes.hpp"
#include <algorithm>
#include <cstdio>
#include <string>
#include <vector>

namespace player2::audio {
struct AcbLayout {std::size_t start{},size{};std::vector<SourceEntry> entries;};
// Plain UTF Header v0/v1 only. Embedded AWB media is selectable independently
// of Cue execution. Opaque Cue/Synth/Sequence tables never become inferred audio.
template<class Bytes> AcbLayout parseAcb(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"@UTF"))fail(SourceStatus::UnsupportedFormat);
    bytes.need(0,32);
    if(std::uint64_t(bytes.u32(4,true))+8!=bytes.size())fail(SourceStatus::MalformedSource);
    if(bytes.u16(8,true)>1)fail(SourceStatus::UnsupportedCodec);
    if(bytes.u32(28,true)!=1)fail(SourceStatus::UnsupportedCodec);
    if(limits.maximumDecodedBytes<65536)fail(SourceStatus::ResourceLimit);
    const auto rows=std::size_t(bytes.u16(10,true))+8;
    const auto strings=std::uint64_t(bytes.u32(12,true))+8,data=std::uint64_t(bytes.u32(16,true))+8;
    const auto columns=bytes.u16(24,true),rowWidth=bytes.u16(26,true);
    if(rows<32 || rows>strings || strings>=data || data>bytes.size() || rowWidth>strings-rows)
        fail(SourceStatus::MalformedSource);
    if(!columns)fail(SourceStatus::MalformedSource);
    if(columns>256 || columns>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
    // Names may retain geometric string capacity, vector records and a temporary
    // value per column while the AFS2 index is built. Bound that peak explicitly.
    const std::size_t overhead=65536+std::size_t(columns)*1024;
    if(limits.maximumDecodedBytes<overhead)fail(SourceStatus::ResourceLimit);
    const auto stringAt=[&](std::uint32_t offset) {
        if(offset>=data-strings)fail(SourceStatus::MalformedSource);
        std::string value;
        for(auto at=strings+offset;at<data;++at) {
            const auto ch=bytes.u8(static_cast<std::size_t>(at));if(!ch)return value;
            if(value.size()>=256)fail(SourceStatus::ResourceLimit);
            value.push_back(static_cast<char>(ch));
        }
        fail(SourceStatus::MalformedSource);
    };
    if(stringAt(bytes.u32(20,true))!="Header")fail(SourceStatus::UnsupportedFormat);
    std::size_t cursor=32,rowOffset=0;AcbLayout result;bool found=false;
    std::vector<std::string> names;names.reserve(columns);
    std::vector<std::pair<std::size_t,std::size_t>> blobs;
    for(unsigned n=0;n<columns;++n) {
        if(cursor>rows || rows-cursor<5)fail(SourceStatus::MalformedSource);
        const unsigned info=bytes.u8(cursor);const auto type=info&15u,storage=info&0xf0u;
        const auto name=stringAt(bytes.u32(cursor+1,true));cursor+=5;
        if(name.empty())fail(SourceStatus::MalformedSource);
        names.push_back(name);
        if(storage!=0x10 && storage!=0x30 && storage!=0x50)fail(SourceStatus::UnsupportedCodec);
        constexpr unsigned widths[]{1,1,2,2,4,4,8,8,4,8,4,8,16};
        if(type>=sizeof(widths)/sizeof(widths[0]))fail(SourceStatus::UnsupportedCodec);
        const auto width=widths[type];std::size_t at=0;
        if(storage==0x30) {
            if(width>rows-cursor)fail(SourceStatus::MalformedSource);
            at=cursor;cursor+=width;
        } else if(storage==0x50) {
            if(rowOffset>rowWidth || width>rowWidth-rowOffset)fail(SourceStatus::MalformedSource);
            at=rows+rowOffset;rowOffset+=width;
        }
        if(name=="AwbFile" && type!=11)fail(SourceStatus::UnsupportedCodec);
        if(type==10 && at)static_cast<void>(stringAt(bytes.u32(at,true)));
        if(type==11 && at) {
            const auto relative=bytes.u32(at,true),size=bytes.u32(at+4,true);
            if(relative>bytes.size()-data || size>bytes.size()-data-relative)fail(SourceStatus::MalformedSource);
            if(size)blobs.push_back({static_cast<std::size_t>(data+relative),size});
            if(name=="AwbFile") {found=true;result.start=static_cast<std::size_t>(data+relative);result.size=size;}
        }
    }
    if(rowOffset!=rowWidth)fail(SourceStatus::MalformedSource);
    std::sort(names.begin(),names.end());
    if(std::adjacent_find(names.begin(),names.end())!=names.end())fail(SourceStatus::MalformedSource);
    std::sort(blobs.begin(),blobs.end());
    for(std::size_t n=1;n<blobs.size();++n) {
        const auto& a=blobs[n-1];const auto& b=blobs[n];
        if(b.first<a.first+a.second && a!=b)fail(SourceStatus::MalformedSource);
    }
    if(!found || !result.size)fail(SourceStatus::UnsupportedCodec); // external AWB requires explicit dependency resolution
    SourceWindowBytes<Bytes> awb(bytes,result.start,result.size);const auto index=parseAfs2Index(awb,limits);
    if(index.size()>(limits.maximumDecodedBytes-overhead)/512)fail(SourceStatus::ResourceLimit);
    result.entries.resize(index.size());
    for(std::size_t n=0;n<index.size();++n)
        std::snprintf(result.entries[n].id.data(),result.entries[n].id.size(),"acb:memory:wave:%u",unsigned(index[n].waveId));
    return result;
}
}
