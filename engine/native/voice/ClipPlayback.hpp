#pragma once
#include "compiled/ClipLoop.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
namespace player2 {
// Arithmetic mapping only; the same Voice/clock/filter/stream cursor executes it.
struct ClipPlayback {
    static double position(const CompiledClipLoop& loop,std::int64_t offset,long double progress) noexcept {
        const long double boundary=loop.end.value-offset,width=loop.end.value-loop.start.value;
        if(progress<boundary)return static_cast<double>(offset+progress);
        const auto extra=progress-boundary;
        if(loop.count<0 || extra<width*loop.count)return static_cast<double>(loop.start.value+std::fmod(extra,width));
        return static_cast<double>(offset+progress-width*loop.count);
    }
    static std::int64_t wraps(const CompiledClipLoop& loop,std::int64_t offset,long double progress) noexcept {
        const long double extra=progress-(loop.end.value-offset);
        if(extra<0 || loop.count==0)return 0;
        auto count=std::floor(extra/(loop.end.value-loop.start.value))+1;
        if(loop.count>=0)count=std::min(count,static_cast<long double>(loop.count));
        return count>=static_cast<long double>(std::numeric_limits<std::int64_t>::max()) ? std::numeric_limits<std::int64_t>::max() : static_cast<std::int64_t>(count);
    }
    static long double remaining(const CompiledClipLoop& loop,std::int64_t length,long double progress) noexcept {
        return loop.count<0 ? std::numeric_limits<long double>::infinity() : static_cast<long double>(length)+(static_cast<long double>(loop.end.value)-loop.start.value)*loop.count-progress;
    }
};
}
