#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
// Prefix-only probe: never treat DSP bytes as the PCM provider and never require
// the DATA body to be present in a bounded probe window. Full admission stays strict.
inline const char* probeNintendoStream(ByteView prefix)noexcept {
    try {
        SourceBytes bytes(prefix,DecodeLimits{4096});
        if(bytes.magic(0,"RSTM")) {
            if(bytes.u16(4,true)!=0xfeff || bytes.u16(6,true)!=0x0100 || bytes.u16(12,true)<40)return nullptr;
            const auto head=std::size_t(bytes.u32(16,true)),size=std::size_t(bytes.u32(20,true));
            if(head<bytes.u16(12,true) || size<32 || !bytes.magic(head,"HEAD") || bytes.u16(head+8,true)!=0x0100)return nullptr;
            const auto relative=std::size_t(bytes.u32(head+12,true));if(relative<24 || relative>=size-8)return nullptr;
            const auto codec=bytes.u8(bytes.offset(head+8,relative));
            return codec==2 ? "native.brstm-dsp" : codec<=1 ? "native.brstm-pcm" : nullptr;
        }
        const bool bcstm=bytes.magic(0,"CSTM");if(!bcstm && !bytes.magic(0,"FSTM"))return nullptr;
        const auto bom=bytes.u16(4,true);if(bom!=0xfeff && bom!=0xfffe)return nullptr;
        const bool big=bom==0xfeff;
        const auto header=bytes.u16(6,big),count=bytes.u16(16,big);
        if(header<20 || count<2 || count>16 || 20+std::size_t(count)*12>header)return nullptr;
        bytes.need(0,header);
        for(std::size_t n=0;n<count;++n)if(bytes.u16(20+n*12,big)==0x4000) {
            const auto info=std::size_t(bytes.u32(24+n*12,big)),size=std::size_t(bytes.u32(28+n*12,big));
            if(info<header || size<32 || !bytes.magic(info,"INFO") || bytes.u16(info+8,big)!=0x4100)return nullptr;
            const auto relative=std::size_t(bytes.u32(info+12,big));if(relative<24 || relative>=size-8)return nullptr;
            const auto codec=bytes.u8(bytes.offset(info+8,relative));
            if(bcstm)return codec==2 ? "native.bcstm-dsp" : codec<=1 ? "native.bcstm-pcm" : nullptr;
            return codec==2 ? "native.bfstm-dsp" : codec<=1 ? "native.bfstm-pcm" : nullptr;
        }
    }catch(...){return nullptr;}
    return nullptr;
}
}
