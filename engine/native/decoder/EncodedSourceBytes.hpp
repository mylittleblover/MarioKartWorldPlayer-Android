#pragma once
#include "api/EncodedSourceInput.hpp"
#include "decoder/SourceBytes.hpp"
#include <array>
#include <algorithm>
#include <limits>

#include <vector>

namespace player2::audio {
// Parser/control only. Up to 64 KiB read-ahead window amortizes pread/fstat syscalls across
// BWAV Opus packet scans and DSP-ADPCM block decodes, falling back to 4 KiB under tight limits.
class EncodedSourceBytes {
    static constexpr std::size_t SmallCacheBytes=4096,LargeCacheBytes=65536;
    EncodedSourceInput& input_;
    std::size_t size_;
    std::vector<std::uint8_t> cache_;
    std::size_t start_{std::numeric_limits<std::size_t>::max()},count_{};
    void fill(std::size_t offset) {
        start_=offset/cache_.size()*cache_.size();count_=std::min(cache_.size(),size_-start_);
        const auto result=input_.read({start_},cache_.data(),count_);
        if(result.status!=SourceStatus::Ok || result.bytes!=count_) {
            start_=std::numeric_limits<std::size_t>::max();count_=0;
            fail(result.status==SourceStatus::Ok ? SourceStatus::InputUnavailable : result.status);
        }
    }
public:
    EncodedSourceBytes(EncodedSourceInput& input,const DecodeLimits& limits):input_(input),size_(0) {
        const auto length=input.byteLength();
        if(length>limits.maximumInputBytes || length>MaximumEncodedSourceBytes)fail(SourceStatus::ResourceLimit);
        if(length<4)fail(SourceStatus::MalformedSource);
        size_=static_cast<std::size_t>(length);
        const auto target=limits.maximumDecodedBytes>=1024*1024 ? LargeCacheBytes : SmallCacheBytes;
        const auto window=std::max<std::size_t>(SmallCacheBytes,std::min<std::size_t>(target,size_));
        try{cache_.resize(window);}catch(const std::bad_alloc&){fail(SourceStatus::ResourceLimit);}
    }
    std::size_t size()const noexcept{return size_;}
    std::size_t cacheBytes()const noexcept{return cache_.size();}
    void need(std::size_t offset,std::size_t count)const {
        if(offset>size_ || count>size_-offset)fail(SourceStatus::MalformedSource);
    }
    std::size_t offset(std::size_t base,std::size_t relative)const {need(base,relative);return base+relative;}
    std::uint8_t u8(std::size_t offset) {
        need(offset,1);
        if(start_==std::numeric_limits<std::size_t>::max() || offset<start_ || offset-start_>=count_)fill(offset);
        return cache_[offset-start_];
    }
    void copy(std::size_t offset,std::uint8_t* output,std::size_t count) {
        need(offset,count);
        while(count) {
            if(start_==std::numeric_limits<std::size_t>::max() || offset<start_ || offset-start_>=count_)fill(offset);
            const auto window=std::min(count,count_-(offset-start_));
            std::copy_n(cache_.data()+(offset-start_),window,output);
            offset+=window;output+=window;count-=window;
        }
    }
    bool magic(std::size_t offset,const char* text) {
        need(offset,4);for(std::size_t n=0;n<4;++n)if(u8(offset+n)!=static_cast<std::uint8_t>(text[n]))return false;
        return true;
    }
    std::uint16_t u16(std::size_t offset,bool big=false) {
        need(offset,2);return big ? (std::uint16_t(u8(offset))<<8)|u8(offset+1) : std::uint16_t(u8(offset))|(std::uint16_t(u8(offset+1))<<8);
    }
    std::uint32_t u32(std::size_t offset,bool big=false) {
        need(offset,4);std::uint32_t value=0;
        for(int n=0;n<4;++n)value=(value<<8)|u8(offset+(big ? n : 3-n));
        return value;
    }
};
}
