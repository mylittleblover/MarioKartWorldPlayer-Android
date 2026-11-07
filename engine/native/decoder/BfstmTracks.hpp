#pragma once
#include "decoder/BfstmLayout.hpp"
#include "decoder/StreamTrackGroups.hpp"

namespace player2::audio {
template<class Bytes> SourceStreamInfo parseBfstmTracks(Bytes& bytes,const NintendoStreamLayout& layout,const char* format="bfstm") {
    SourceStreamInfo result;result.originalChannels=layout.metadata.channels;
    std::size_t info=0,size=0;
    for(std::size_t n=0;n<bytes.u16(16,layout.big);++n)if(bytes.u16(20+n*12,layout.big)==0x4000){info=bytes.u32(24+n*12,layout.big);size=bytes.u32(28+n*12,layout.big);break;}
    const auto inside=[&](std::size_t at,std::size_t count){if(at<info+8 || at>info+size || count>info+size-at)fail(SourceStatus::MalformedSource);};
    const auto type=bytes.u16(info+16,layout.big);const auto relative=bytes.u32(info+20,layout.big);
    if(relative!=0 && relative!=0xffffffffu) {
        if(type!=0x0101)fail(SourceStatus::MalformedSource);
        const auto table=bytes.offset(info+8,relative);inside(table,4);const auto count=bytes.u32(table,layout.big);
        if(count>32)fail(SourceStatus::ResourceLimit);
        inside(table,4+count*8);
        for(unsigned n=0;n<count;++n) {
            const auto ref=table+4+n*8;
            if(bytes.u16(ref,layout.big)!=0x4101 || bytes.u16(ref+2,layout.big))fail(SourceStatus::MalformedSource);
            const auto at=bytes.offset(table,bytes.u32(ref+4,layout.big));inside(at,12);
            SourceStreamGroup group;group.origin=SourceByteOffset{at};
            std::snprintf(group.id.data(),group.id.size(),"%s:track-byte:%zu",format,at);
            group.volume=bytes.u8(at);group.pan=bytes.u8(at+1);group.surroundPan=bytes.u8(at+2);group.flags=bytes.u8(at+3);
            if(group.volume>127 || group.pan>127 || group.surroundPan>127 || group.flags)fail(SourceStatus::UnsupportedCodec);
            if(bytes.u16(at+4,layout.big)!=0x0100 || bytes.u16(at+6,layout.big))fail(SourceStatus::MalformedSource);
            const auto mapping=bytes.offset(at,bytes.u32(at+8,layout.big));inside(mapping,4);const auto channels=bytes.u32(mapping,layout.big);
            if(!channels || channels>MaximumNintendoStreamChannels)fail(SourceStatus::UnsupportedChannels);
            inside(mapping,4+channels);std::array<bool,MaximumNintendoStreamChannels> seen{};
            for(unsigned ch=0;ch<channels;++ch) {
                const auto index=bytes.u8(mapping+4+ch);
                if(index>=result.originalChannels || seen[index])fail(SourceStatus::MalformedSource);
                seen[index]=true;group.channels.push_back(index);
            }
            for(const auto& prior:result.groups)if(prior.origin->value==at)fail(SourceStatus::MalformedSource);
            result.groups.push_back(std::move(group));
        }
    }
    appendUnmappedStreamGroups(result,format);return result;
}
}
