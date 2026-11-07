#pragma once
#include "decoder/SourceBytes.hpp"
#include "api/Mo3Types.hpp"

namespace player2::audio {
inline Mo3Envelope readMo3Envelope(SourceBytes& input,std::size_t offset,bool xm) {
    input.need(offset,106);Mo3Envelope result;result.origin={offset};
    result.flags=input.u8(offset);result.count=input.u8(offset+1);
    result.sustainStart=input.u8(offset+2);result.sustainEnd=input.u8(offset+3);
    result.loopStart=input.u8(offset+4);result.loopEnd=input.u8(offset+5);
    // Preserve unused original flag bits; the pinned renderer interprets only known bits.
    if(result.count>25 || ((result.flags&1) && !result.count))fail(SourceStatus::MalformedSource);
    if(result.flags&2) {
        const auto end=xm ? result.sustainStart : result.sustainEnd;
        if(result.sustainStart>end || end>=result.count)fail(SourceStatus::MalformedSource);
    }
    if((result.flags&4) && (result.loopStart>result.loopEnd || result.loopEnd>=result.count))fail(SourceStatus::MalformedSource);
    for(unsigned n=0;n<result.count;++n) {
        const auto tick=input.u16(offset+6+n*4),value=input.u16(offset+8+n*4);
        if(n && tick<result.points[n-1].tick)fail(SourceStatus::MalformedSource);
        result.points[n]={tick,static_cast<std::int16_t>(value<32768 ? int(value) : int(value)-65536)};
    }
    return result;
}
}
