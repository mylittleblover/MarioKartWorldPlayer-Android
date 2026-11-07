#pragma once
#include "decoder/BrstmLayout.hpp"
#include "decoder/StreamTrackGroups.hpp"

namespace player2::audio {
template<class Bytes> SourceStreamInfo parseBrstmTracks(Bytes& bytes,const NintendoStreamLayout& layout) {
    SourceStreamInfo result;result.originalChannels=layout.metadata.channels;
    const auto sections=parseBrstmSections(bytes);const auto head=sections.head,end=head+sections.headSize;
    const auto inside=[&](std::size_t at,std::size_t count){if(at<head+8 || at>end || count>end-at)fail(SourceStatus::MalformedSource);};
    const auto relative=bytes.u32(head+20,true);
    if(relative!=0 && relative!=0xffffffffu) {
        if(bytes.u16(head+16,true)!=0x0100 || bytes.u16(head+18,true))fail(SourceStatus::MalformedSource);
        const auto table=bytes.offset(head+8,relative);inside(table,4);
        const auto count=bytes.u8(table),type=bytes.u8(table+1);
        if(count>32)fail(SourceStatus::ResourceLimit);
        if(type>1)fail(SourceStatus::UnsupportedCodec);
        if(bytes.u16(table+2,true))fail(SourceStatus::MalformedSource);
        inside(table,4+8*count);
        for(unsigned n=0;n<count;++n) {
            const auto ref=table+4+8*n;
            if(bytes.u8(ref)!=1 || bytes.u8(ref+1)!=type || bytes.u16(ref+2,true))fail(SourceStatus::MalformedSource);
            // NW4R Track references are HEAD+8-relative, unlike F/CSTM tables.
            const auto at=bytes.offset(head+8,bytes.u32(ref+4,true));inside(at,type ? 11 : 3);
            SourceStreamGroup group;group.origin=SourceByteOffset{at};
            std::snprintf(group.id.data(),group.id.size(),"brstm:track-byte:%zu",at);
            if(type){group.volume=bytes.u8(at);group.pan=bytes.u8(at+1);}
            if(group.volume>127 || group.pan>127)fail(SourceStatus::UnsupportedCodec);
            const auto mapping=at+(type ? 8 : 0);const auto channels=bytes.u8(mapping);
            if(channels<1 || channels>2)fail(SourceStatus::UnsupportedChannels);
            for(unsigned ch=0;ch<channels;++ch) {
                const auto index=bytes.u8(mapping+1+ch);
                if(index>=result.originalChannels || (ch && index==group.channels[0]))fail(SourceStatus::MalformedSource);
                group.channels.push_back(index);
            }
            for(const auto& prior:result.groups)if(prior.origin->value==at)fail(SourceStatus::MalformedSource);
            result.groups.push_back(std::move(group));
        }
    }
    appendUnmappedStreamGroups(result,"brstm");return result;
}
}
