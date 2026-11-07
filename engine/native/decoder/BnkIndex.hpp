#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <cstdio>
#include <vector>

namespace player2::audio {
struct BnkMedia {std::uint32_t id{};std::size_t start{},size{};};
struct BnkIndex {std::vector<BnkMedia> media;std::vector<SourceEntry> entries;};
// Modern, little- or big-endian DIDX/DATA media addressing. HIRC is retained in the
// original bank, not executed or translated into an alternative Runtime.
template<class Bytes> BnkIndex parseBnkIndex(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"BKHD"))fail(SourceStatus::UnsupportedFormat);
    bytes.need(0,28);
    const auto littleVersion=bytes.u32(8);
    const bool big=littleVersion>141 && bytes.u32(8,true)>=77 && bytes.u32(8,true)<=141;
    const auto headerSize=bytes.u32(4,big),version=bytes.u32(8,big);
    if(version<77 || version>141)fail(SourceStatus::UnsupportedCodec);
    if(headerSize<20)fail(SourceStatus::MalformedSource);
    std::size_t index=0,indexSize=0,data=0,dataSize=0,chunks=0;
    for(std::size_t p=0;p<bytes.size();) {
        bytes.need(p,8);const auto size=bytes.u32(p+4,big);const auto begin=p+8;bytes.need(begin,size);
        if(++chunks>4096)fail(SourceStatus::ResourceLimit);
        if(bytes.magic(p,"BKHD")) {if(p!=0)fail(SourceStatus::MalformedSource);}
        else if(bytes.magic(p,"DIDX")) {
            if(index || !size || size%12)fail(SourceStatus::MalformedSource);
            index=begin;indexSize=size;
        } else if(bytes.magic(p,"DATA")) {
            if(data || !size)fail(SourceStatus::MalformedSource);
            data=begin;dataSize=size;
        } else if(!bytes.magic(p,"HIRC") && !bytes.magic(p,"STID") && !bytes.magic(p,"STMG") &&
            !bytes.magic(p,"ENVS") && !bytes.magic(p,"FXPR") && !bytes.magic(p,"PLAT") && !bytes.magic(p,"INIT"))
            fail(SourceStatus::UnsupportedCodec);
        p=begin+size; // Bank chunks are not RIFF-padded.
    }
    if(!index || !data)fail(SourceStatus::MalformedSource);
    const auto count=indexSize/12;
    if(count>4096 || limits.maximumDecodedBytes<65536 || count>(limits.maximumDecodedBytes-65536)/256)
        fail(SourceStatus::ResourceLimit);
    BnkIndex result;result.media.reserve(count);result.entries.resize(count);
    std::vector<std::uint32_t> ids;ids.reserve(count);
    for(std::size_t n=0;n<count;++n) {
        const auto p=index+n*12;
        const auto id=bytes.u32(p,big),offset=bytes.u32(p+4,big),size=bytes.u32(p+8,big);
        if(!id || !size || offset>dataSize || size>dataSize-offset)fail(SourceStatus::MalformedSource);
        result.media.push_back({id,data+offset,size});ids.push_back(id);
        std::snprintf(result.entries[n].id.data(),result.entries[n].id.size(),"bnk:media:%u",id);
    }
    std::sort(ids.begin(),ids.end());
    if(std::adjacent_find(ids.begin(),ids.end())!=ids.end())fail(SourceStatus::MalformedSource);
    auto spans=result.media;
    std::sort(spans.begin(),spans.end(),[](const auto& a,const auto& b){return a.start==b.start ? a.size<b.size : a.start<b.start;});
    for(std::size_t n=1;n<spans.size();++n) {
        const auto& a=spans[n-1];const auto& b=spans[n];
        if(b.start<a.start+a.size && (b.start!=a.start || b.size!=a.size))fail(SourceStatus::MalformedSource);
    }
    return result;
}
}
