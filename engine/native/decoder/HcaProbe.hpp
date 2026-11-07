#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
inline bool probeHca(ByteView bytes)noexcept {
    constexpr std::uint8_t tag[]{'H','C','A',0};
    if(!bytes.data || bytes.size<4)return false;
    for(unsigned n=0;n<4;++n)if((bytes.data[n]&0x7fu)!=tag[n])return false;
    return true;
}
template<class Bytes> bool hcaTag(Bytes& bytes,std::size_t offset,const char* tag) {
    bytes.need(offset,4);
    for(unsigned n=0;n<4;++n)if((bytes.u8(offset+n)&0x7fu)!=static_cast<std::uint8_t>(tag[n]))return false;
    return true;
}
}
