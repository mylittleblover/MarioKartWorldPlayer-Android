#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>

namespace player2::audio {
// Admission only: consume the actual IT2.14/2.15 width-control stream before
// the pinned renderer allocates/decodes it. Do not let truncated blocks become
// the renderer's zero-filled replacement sample. PCM integration stays upstream.
template<class Bytes,class Operation> std::size_t itPackedSampleBytes(Bytes& input,std::size_t start,
    std::uint32_t frames,unsigned channels,unsigned depth,Operation operation) {
    auto cursor=start;const unsigned maximum=depth+1,blockFrames=32768/(depth/8),fetch=depth==8 ? 3 : 4;
    for(unsigned channel=0;channel<channels;++channel) {
        std::uint32_t remaining=frames;
        while(remaining) {
            const auto size=input.u16(cursor);cursor+=2;
            if(!size)fail(SourceStatus::MalformedSource);
            input.need(cursor,size);const auto end=cursor+size;
            auto byte=cursor;unsigned bit=0,width=maximum;
            const auto read=[&](unsigned bits) {
                if(bits>std::uint64_t(end-byte)*8-bit)fail(SourceStatus::MalformedSource);
                std::uint32_t value=0;unsigned shift=0;
                while(bits) {
                    const auto take=std::min(bits,8-bit);
                    value|=((std::uint32_t(input.u8(byte))>>bit)&((1u<<take)-1))<<shift;
                    shift+=take;bits-=take;bit+=take;if(bit==8){++byte;bit=0;}
                }
                return value;
            };
            const auto wanted=std::min(remaining,blockFrames);unsigned decoded=0;
            while(decoded<wanted) {
                if(!width || width>maximum)fail(SourceStatus::MalformedSource);
                const auto value=read(width),top=1u<<(width-1);bool control=false;unsigned next=0;
                if(width<=6 && value==top) {next=read(fetch)+1;control=true;if(next>=width)++next;}
                else if(width>6 && width<maximum) {
                    const auto low=top-(depth==8 ? 4u : 8u),high=top+(depth==8 ? 3u : 7u);
                    if(value>=low && value<=high){next=value-low+1;control=true;if(next>=width)++next;}
                } else if(width==maximum && (value&top)) {next=(value&~top)+1;control=true;}
                if(control){operation();width=next;}
                else ++decoded;
            }
            remaining-=wanted;cursor=end; // Unused declared tail bits/bytes are not further samples.
        }
    }
    return cursor-start;
}
}
