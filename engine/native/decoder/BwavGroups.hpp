#pragma once
#include "decoder/BwavFormat.hpp"
#include "api/StreamTypes.hpp"
#include <cstdio>

namespace player2::audio {
inline SourceStreamInfo bwavStereoGroups(const BwavLayout& layout) {
    SourceStreamInfo result;result.originalChannels=layout.metadata.channels;
    for(unsigned ch=0;ch<result.originalChannels;) {
        SourceStreamGroup group;const auto first=ch;
        const auto speaker=layout.speakers[ch];group.channels.push_back(ch++);
        if(ch<result.originalChannels && ((speaker==0 && layout.speakers[ch]==1) || (speaker==4 && layout.speakers[ch]==5)))group.channels.push_back(ch++);
        else group.pan=(speaker==0 || speaker==4) ? 0 : (speaker==1 || speaker==5) ? 127 : 64;
        // Speaker-role tags for StreamStereoSource (0=unspecified/legacy stem track,
        // 1=Front L/R, 2=Center, 3=LFE, 4=Rear Ls/Rs).
        if(speaker==0 || speaker==1)group.flags=1;
        else if(speaker==2)group.flags=2;
        else if(speaker==3)group.flags=3;
        else if(speaker==4 || speaker==5){group.flags=4;group.surroundPan=127;}
        // BWAV has per-lane L/R/C facts but no file Track table. These groups
        // and their pan are explicitly derived stereo routes, with null origin.
        std::snprintf(group.id.data(),group.id.size(),"bwav:channels:%u:%zu",first,group.channels.size());
        result.groups.push_back(std::move(group));
    }
    return result;
}
}
