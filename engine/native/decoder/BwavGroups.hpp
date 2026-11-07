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
        if(speaker==0 && ch<result.originalChannels && layout.speakers[ch]==1)group.channels.push_back(ch++);
        else group.pan=speaker==0 ? 0 : speaker==1 ? 127 : 64;
        // BWAV has per-lane L/R/C facts but no file Track table. These groups
        // and their pan are explicitly derived stereo routes, with null origin.
        std::snprintf(group.id.data(),group.id.size(),"bwav:channels:%u:%zu",first,group.channels.size());
        result.groups.push_back(std::move(group));
    }
    return result;
}
}
