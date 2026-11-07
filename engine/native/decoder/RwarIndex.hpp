#pragma once
#include "decoder/NintendoWaveFormat.hpp"
#include <vector>

namespace player2::audio {
struct RwarEntry {std::size_t start{},size{};};
// Original TABL array position is the root-scoped Wave ID, not byte offset.
template<class Bytes> std::vector<RwarEntry> parseRwar(Bytes& input,const DecodeLimits& limits) {
    input.need(0,32);
    if(!input.magic(0,"RWAR"))fail(SourceStatus::UnsupportedFormat);
    if(input.u16(4,true)!=0xfeff || input.u16(6,true)!=0x100)fail(SourceStatus::UnsupportedCodec);
    if(input.u32(8,true)!=input.size() || input.u16(12,true)!=32 || input.u16(14,true)!=2)fail(SourceStatus::MalformedSource);
    const auto table=input.u32(16,true),tableSize=input.u32(20,true),data=input.u32(24,true),dataSize=input.u32(28,true);
    if(table<32 || data<32 || tableSize<12 || dataSize<32)fail(SourceStatus::MalformedSource);
    NintendoWaveRegions<Bytes> root(input,0,input.size());root.add(0,32);root.add(table,tableSize);root.add(data,dataSize);
    if(!input.magic(table,"TABL") || !input.magic(data,"DATA") || input.u32(table+4,true)!=tableSize || input.u32(data+4,true)!=dataSize)
        fail(SourceStatus::MalformedSource);
    const auto count=input.u32(table+8,true);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>limits.maximumEvents || count>(limits.maximumDecodedBytes-16384)/512)
        fail(SourceStatus::ResourceLimit);
    if(count>(tableSize-12)/12)fail(SourceStatus::MalformedSource);
    for(auto p=table+12+count*12;p<table+tableSize;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    std::vector<RwarEntry> result;result.reserve(count);
    std::vector<std::pair<std::size_t,std::size_t>> spans;spans.reserve(count);
    for(unsigned n=0;n<count;++n) {
        const auto ref=table+12+n*12;
        if(input.u32(ref,true)!=0x01000000)fail(SourceStatus::UnsupportedCodec);
        const auto relative=input.u32(ref+4,true),size=input.u32(ref+8,true);
        if(relative<32 || relative>dataSize || size<32 || size>dataSize-relative)fail(SourceStatus::MalformedSource);
        const auto start=input.offset(data,relative);input.need(start,size);
        if(!input.magic(start,"RWAV") || (input.u16(start+4,true)!=0xfeff && input.u16(start+4,true)!=0xfffe))fail(SourceStatus::MalformedSource);
        if(input.u32(start+8,input.u16(start+4,true)==0xfeff)!=size)fail(SourceStatus::MalformedSource);
        result.push_back({start,size});spans.emplace_back(start,start+size);
    }
    std::sort(spans.begin(),spans.end());auto end=std::size_t(data)+8;std::pair<std::size_t,std::size_t> previous{};
    for(const auto& span:spans) {
        if(span.first<end && span!=previous)fail(SourceStatus::MalformedSource);
        for(auto p=end;p<span.first;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
        end=std::max(end,span.second);previous=span;
    }
    for(auto p=end;p<std::size_t(data)+dataSize;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    root.finish();return result;
}
}
