#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <vector>

namespace player2::audio {
struct SwarEntry {std::size_t start{},size{};};
// Nitro SWAR Wave IDs are the original offset-table indexes, including aliases.
// Unlike a standalone SWAV, each stored record starts with12 wave-parameter bytes.
template<class Bytes> std::vector<SwarEntry> parseSwar(Bytes& input,const DecodeLimits& limits) {
    input.need(0,60);
    if(!input.magic(0,"SWAR"))fail(SourceStatus::UnsupportedFormat);
    if(input.u16(4)!=0xfeff || input.u16(6)!=0x100)fail(SourceStatus::UnsupportedCodec);
    if(input.u32(8)!=input.size() || input.u16(12)!=16 || input.u16(14)!=1 || !input.magic(16,"DATA") ||
        input.u32(20)!=input.size()-16)fail(SourceStatus::MalformedSource);
    for(unsigned n=24;n<56;++n)if(input.u8(n))fail(SourceStatus::UnsupportedCodec);
    const auto count=input.u32(56);
    if(!count)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<16384 || count>4096 || count>limits.maximumEvents || count>(limits.maximumDecodedBytes-16384)/512)
        fail(SourceStatus::ResourceLimit);
    const std::size_t header=60+count*4;input.need(0,header);
    std::vector<SwarEntry> result;result.reserve(count);
    std::vector<std::pair<std::size_t,std::size_t>> spans;spans.reserve(count);
    for(unsigned n=0;n<count;++n) {
        const auto at=input.u32(60+n*4);input.need(at,12);
        if(at<header || (at&3))fail(SourceStatus::MalformedSource);
        if(input.u8(at)>2)fail(SourceStatus::UnsupportedCodec);
        if(input.u8(at+1)>1)fail(SourceStatus::MalformedSource);
        const auto stored=(std::uint64_t(input.u16(at+6))+input.u32(at+8))*4;
        if(!stored || stored>input.size()-at-12)fail(SourceStatus::MalformedSource);
        const auto end=at+12+static_cast<std::size_t>(stored);
        spans.emplace_back(at,end);result.push_back({at,end-at});
    }
    std::sort(spans.begin(),spans.end());auto end=header;std::pair<std::size_t,std::size_t> previous{};
    for(const auto& span:spans) {
        if(span.first<end && span!=previous)fail(SourceStatus::MalformedSource);
        for(auto p=end;p<span.first;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
        end=std::max(end,span.second);previous=span;
    }
    for(auto p=end;p<input.size();++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    return result;
}
}
