#pragma once
#include "decoder/EncodedSourceBytes.hpp"
#include <vector>

namespace player2::audio {
// MO3's documented LZ grammar; compare the pinned BSD Load_mo3.cpp reader.
// All input, variable-length codes, output and backward copies are bounded.
template<class Input> class Mo3Bits {
    Input& input_;std::size_t end_;std::uint8_t control_{};unsigned bits_{};
public:
    std::size_t cursor;
    Mo3Bits(Input& input,std::size_t begin,std::size_t end):input_(input),end_(end),cursor(begin){}
    std::uint8_t byte(){if(cursor>=end_)fail(SourceStatus::MalformedSource);return input_.u8(cursor++);}
    unsigned bit(){if(!bits_){control_=byte();bits_=8;}const auto result=control_>>7;control_<<=1;--bits_;return result;}
    std::uint32_t variable(){std::uint32_t result=1;do {if(result>0x3fffffffu)fail(SourceStatus::MalformedSource);result=result*2+bit();}while(bit());return result;}
};
template<class Input> std::vector<std::uint8_t> unpackMo3Music(Input& input,std::uint32_t size,std::size_t& sampleStart,unsigned version) {
    const auto begin=version>=5 ? 12u : 8u;
    const auto end=version>=5 ? input.offset(begin,input.u32(8)) : input.size();
    Mo3Bits<Input> bits(input,begin,end);std::vector<std::uint8_t> output;output.reserve(size);
    output.push_back(bits.byte());std::uint32_t previousDistance=0;
    while(output.size()<size) {
        if(!bits.bit()){output.push_back(bits.byte());continue;}
        const auto encoded=bits.variable();std::uint32_t distance=previousDistance,length=0,adjust=0;
        if(encoded==2)length=0;
        else {
            if(encoded<3 || encoded-3>0x7fffffu)fail(SourceStatus::MalformedSource);
            distance=((encoded-3)<<8)+bits.byte()+1;previousDistance=distance;
            adjust=1+(distance>1280 ? 1 : 0)+(distance>32000 ? 1 : 0);
        }
        if(!distance || distance>output.size())fail(SourceStatus::MalformedSource);
        length=length*2+bits.bit();length=length*2+bits.bit();
        if(!length)length=bits.variable()+2;
        if(length>size-output.size() || adjust>size-output.size()-length)fail(SourceStatus::MalformedSource);
        length+=adjust;
        for(std::uint32_t n=0;n<length;++n)output.push_back(output[output.size()-distance]);
    }
    if(version>=5 && bits.cursor!=end)fail(SourceStatus::MalformedSource);
    sampleStart=bits.cursor;return output;
}
}
