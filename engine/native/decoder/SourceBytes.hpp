#pragma once
#include "audio/source/AudioFrameSource.hpp"
#include <cstring>

namespace player2::audio {
class SourceBytes {
    ByteView bytes_;
public:
    explicit SourceBytes(ByteView bytes,const DecodeLimits& limits):bytes_(bytes) {
        if(!bytes.data || bytes.size<4) fail(SourceStatus::MalformedSource);
        if(bytes.size>limits.maximumInputBytes || bytes.size>MaximumEncodedSourceBytes) fail(SourceStatus::ResourceLimit);
    }
    std::size_t size() const noexcept { return bytes_.size; }
    void need(std::size_t offset,std::size_t count) const {
        if(offset>bytes_.size || count>bytes_.size-offset) fail(SourceStatus::MalformedSource);
    }
    std::size_t offset(std::size_t base,std::size_t relative) const {
        need(base,relative); return base+relative;
    }
    bool magic(std::size_t offset,const char* text) const {
        need(offset,4); return std::memcmp(bytes_.data+offset,text,4)==0;
    }
    std::uint8_t u8(std::size_t offset) const { need(offset,1); return bytes_.data[offset]; }
    std::uint16_t u16(std::size_t offset,bool big=false) const {
        need(offset,2); return big ? (std::uint16_t(bytes_.data[offset])<<8)|bytes_.data[offset+1] :
            bytes_.data[offset]|(std::uint16_t(bytes_.data[offset+1])<<8);
    }
    std::uint32_t u32(std::size_t offset,bool big=false) const {
        need(offset,4); std::uint32_t value=0;
        for(int n=0;n<4;++n) value=(value<<8)|bytes_.data[offset+(big ? n : 3-n)];
        return value;
    }
    float pcm16(std::size_t offset,bool big=false) const {
        const auto bits=u16(offset,big);
        const auto signedValue=bits<32768 ? static_cast<std::int32_t>(bits) : static_cast<std::int32_t>(bits)-65536;
        return static_cast<float>(signedValue)/32768.0f;
    }
};
inline void validateFormat(std::int32_t rate,std::int32_t channels) {
    if(rate<8000 || rate>192000) fail(SourceStatus::UnsupportedSampleRate);
    if(channels<1 || channels>2) fail(SourceStatus::UnsupportedChannels);
}
inline void boundPcm(std::uint64_t frames,std::uint32_t channels,const DecodeLimits& limits) {
    if(frames==0) fail(SourceStatus::MalformedSource);
    if(frames>limits.maximumDecodedBytes/(channels*sizeof(float))) fail(SourceStatus::ResourceLimit);
}
inline void boundStreamDuration(std::uint64_t frames,std::uint32_t sourceRate,const DecodeLimits& limits) {
    if(!frames)fail(SourceStatus::MalformedSource);
    if(frames>INT64_MAX || !sourceRate || limits.maximumSourceDurationSeconds<=0)fail(SourceStatus::ResourceLimit);
    const auto seconds=static_cast<std::uint64_t>(limits.maximumSourceDurationSeconds);
    // Division avoids overflow even if a caller expands the duration policy.
    if(frames/sourceRate>seconds || (frames/sourceRate==seconds && frames%sourceRate))fail(SourceStatus::ResourceLimit);
}
}
