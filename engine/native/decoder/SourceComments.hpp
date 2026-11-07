#pragma once
#include "decoder/SourceBytes.hpp"
#include <string>
#include <limits>

namespace player2::audio {
inline bool sourceUtf8(const std::string& text)noexcept {
    std::size_t cursor=0;
    while(cursor<text.size()) {
        const auto lead=static_cast<unsigned char>(text[cursor++]);if(lead<128)continue;
        const unsigned tails=lead>=0xc2 && lead<=0xdf ? 1 : lead>=0xe0 && lead<=0xef ? 2 : lead>=0xf0 && lead<=0xf4 ? 3 : 0;
        if(!tails || tails>text.size()-cursor)return false;
        const auto second=static_cast<unsigned char>(text[cursor]);
        if((lead==0xe0 && second<0xa0) || (lead==0xed && second>0x9f) || (lead==0xf0 && second<0x90) || (lead==0xf4 && second>0x8f))return false;
        for(unsigned n=0;n<tails;++n)if((static_cast<unsigned char>(text[cursor++])&0xc0)!=0x80)return false;
    }
    return true;
}
template<class Bytes> std::optional<SourceLoopMetadata> sourceCommentLoop(Bytes& bytes,std::size_t start,std::size_t size,SourceSamplePosition duration) {
    if(size>1024*1024)fail(SourceStatus::ResourceLimit);
    bytes.need(start,size);const auto end=start+size;auto cursor=start;
    const auto integer=[&]() {
        if(cursor>end || end-cursor<4)fail(SourceStatus::MalformedSource);
        const auto value=bytes.u32(cursor);cursor+=4;return value;
    };
    const auto text=[&]() {
        const auto count=integer();if(count>65536)fail(SourceStatus::ResourceLimit);
        if(cursor>end || count>end-cursor)fail(SourceStatus::MalformedSource);
        std::string value;value.reserve(count);for(std::size_t n=0;n<count;++n)value.push_back(static_cast<char>(bytes.u8(cursor++)));
        if(!sourceUtf8(value))fail(SourceStatus::MalformedSource);
        return value;
    };
    static_cast<void>(text());const auto count=integer();if(count>1024)fail(SourceStatus::ResourceLimit);
    std::optional<std::int64_t> loopStart,loopEnd,loopLength;
    for(std::size_t n=0;n<count;++n) {
        const auto value=text();const auto equal=value.find('=');if(equal==std::string::npos)fail(SourceStatus::MalformedSource);
        auto key=value.substr(0,equal);for(auto& c:key)if(c>='a' && c<='z')c=static_cast<char>(c-'a'+'A');
        auto* field=key=="LOOPSTART" ? &loopStart : key=="LOOPEND" ? &loopEnd : key=="LOOPLENGTH" ? &loopLength : nullptr;
        if(!field)continue;
        if(*field || equal+1==value.size())fail(SourceStatus::MalformedSource);
        std::int64_t number=0;
        for(std::size_t p=equal+1;p<value.size();++p) {
            const auto digit=value[p]-'0';if(digit<0 || digit>9 || number>(std::numeric_limits<std::int64_t>::max()-digit)/10)fail(SourceStatus::MalformedSource);
            number=number*10+digit;
        }
        *field=number;
    }
    if(cursor!=end)fail(SourceStatus::MalformedSource);
    if(!loopStart) {if(loopEnd || loopLength)fail(SourceStatus::SourceLoopBounds);return std::nullopt;}
    if(loopLength) {
        if(*loopLength>std::numeric_limits<std::int64_t>::max()-*loopStart)fail(SourceStatus::SourceLoopBounds);
        const auto endpoint=*loopStart+*loopLength;if(loopEnd && *loopEnd!=endpoint)fail(SourceStatus::SourceLoopBounds);loopEnd=endpoint;
    }
    const auto endpoint=loopEnd.value_or(duration.value);
    if(*loopStart>=endpoint || endpoint>duration.value)fail(SourceStatus::SourceLoopBounds);
    return SourceLoopMetadata{{*loopStart},{endpoint}};
}
}
