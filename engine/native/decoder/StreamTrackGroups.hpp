#pragma once
#include "decoder/NintendoStreamLayout.hpp"
#include "api/StreamTypes.hpp"
#include <cstdio>

namespace player2::audio {
inline void appendUnmappedStreamGroups(SourceStreamInfo& result,const char* format) {
    std::array<bool,MaximumNintendoStreamChannels> covered{};
    for(const auto& group:result.groups)for(auto channel:group.channels)covered[channel]=true;
    // Derived pairs retain every lane without inventing an original file Track.
    for(unsigned ch=0;ch<result.originalChannels;) {
        if(covered[ch]){++ch;continue;}
        SourceStreamGroup group;const auto first=ch;group.channels.push_back(ch++);
        if(ch<result.originalChannels && !covered[ch])group.channels.push_back(ch++);
        std::snprintf(group.id.data(),group.id.size(),"%s:channels:%u:%zu",format,first,group.channels.size());
        result.groups.push_back(std::move(group));
    }
    if(result.groups.size()>32)fail(SourceStatus::ResourceLimit);
}
}
