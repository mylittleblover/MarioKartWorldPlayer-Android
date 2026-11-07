#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
// GD3 is bounded original metadata, never executable data or Source coordinates.
// Exactly11 UTF16-LE strings, with valid paired surrogates; original bytes remain
// the truth. This does not manufacture a projected multilingual metadata DTO.
template<class Input> std::size_t vgmCommandEnd(Input& input,std::size_t begin) {
    if(!input.u32(0x14))return input.size();
    const auto tag=input.offset(0x14,input.u32(0x14));
    if(tag<=begin)fail(SourceStatus::MalformedSource);
    input.need(tag,12);
    if(!input.magic(tag,"Gd3 ") || input.u32(tag+4)!=0x100)fail(SourceStatus::UnsupportedCodec);
    const auto length=input.u32(tag+8);
    if(length>1024*1024)fail(SourceStatus::ResourceLimit);
    if(length%2 || length!=input.size()-tag-12)fail(SourceStatus::MalformedSource);
    std::size_t p=tag+12;
    for(unsigned field=0;field<11;++field) {
        bool terminated=false;std::size_t units=0;
        while(p<input.size()) {
            if(++units>8192)fail(SourceStatus::ResourceLimit);
            const auto code=input.u16(p);p+=2;
            if(!code){terminated=true;break;}
            if(code>=0xdc00 && code<=0xdfff)fail(SourceStatus::MalformedSource);
            if(code>=0xd800 && code<=0xdbff) {
                if(p>=input.size())fail(SourceStatus::MalformedSource);
                const auto low=input.u16(p);p+=2;
                if(low<0xdc00 || low>0xdfff)fail(SourceStatus::MalformedSource);
                if(++units>8192)fail(SourceStatus::ResourceLimit);
            }
        }
        if(!terminated)fail(SourceStatus::MalformedSource);
    }
    if(p!=input.size())fail(SourceStatus::MalformedSource);
    return tag;
}
}
