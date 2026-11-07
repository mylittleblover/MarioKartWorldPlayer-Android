#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
template<class Bytes> void validateItEnvelope(Bytes& input,std::size_t at,bool volume,bool pitch) {
    const auto flags=input.u8(at),count=input.u8(at+1);
    if(flags&~(pitch ? 0x8fu : 0x0fu))fail(SourceStatus::UnsupportedCodec);
    if(count>25 || ((flags&1) && !count))fail(SourceStatus::MalformedSource);
    if((flags&2) && (input.u8(at+2)>input.u8(at+3) || input.u8(at+3)>=count))fail(SourceStatus::MalformedSource);
    if((flags&4) && (input.u8(at+4)>input.u8(at+5) || input.u8(at+5)>=count))fail(SourceStatus::MalformedSource);
    std::uint16_t previous=0;
    for(unsigned n=0;n<count;++n) {
        const auto value=input.u8(at+6+n*3);
        const auto tick=input.u16(at+7+n*3);
        if(volume ? value>64 : (value>32 && value<224))fail(SourceStatus::MalformedSource);
        if(n && tick<previous)fail(SourceStatus::MalformedSource);
        previous=tick;
    }
}
template<class Bytes> void validateItInstrument(Bytes& input,std::size_t at,unsigned samples) {
    input.need(at,554);
    if(!input.magic(at,"IMPI"))fail(SourceStatus::MalformedSource);
    // No external MIDI/plugin execution or MPTM extended keyboard structures.
    if(input.u8(at+60) || input.u8(at+17)>3 || input.u8(at+18)>3 || input.u8(at+19)>2)
        fail(SourceStatus::UnsupportedCodec);
    const auto pps=input.u8(at+22);
    if((pps>32 && pps<224) || input.u8(at+23)>119 || input.u8(at+24)>128 ||
        (input.u8(at+25)&127)>64 || input.u8(at+26)>100 || input.u8(at+27)>64)fail(SourceStatus::MalformedSource);
    for(unsigned n=0;n<120;++n)
        if(input.u8(at+64+n*2)>119 || input.u8(at+65+n*2)>samples)fail(SourceStatus::MalformedSource);
    validateItEnvelope(input,at+304,true,false);validateItEnvelope(input,at+386,false,false);validateItEnvelope(input,at+468,false,true);
    for(unsigned n=550;n<554;++n)if(input.u8(at+n))fail(SourceStatus::UnsupportedCodec);
}
}
