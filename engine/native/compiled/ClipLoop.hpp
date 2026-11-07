#pragma once
#include "api/AudioSourceTypes.hpp"
#include <limits>
namespace player2 {
// Independent authored loop, never inferred from SourceLoopMetadata.
struct CompiledClipLoop {audio::SourceSamplePosition start,end;int count{-1};};
inline bool validClipLoop(const CompiledClipLoop& loop,std::int64_t offset,std::int64_t length) noexcept {
    if(offset<0 || length<=0 || offset>std::numeric_limits<std::int64_t>::max()-length || loop.count< -1 || loop.start.value<offset || loop.end.value<=loop.start.value || loop.end.value>offset+length)return false;
    const auto width=loop.end.value-loop.start.value;
    return loop.count<=0 || width<=(std::numeric_limits<std::int64_t>::max()-length)/loop.count;
}
}
