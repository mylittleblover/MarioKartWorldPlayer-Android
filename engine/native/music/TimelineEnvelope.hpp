#pragma once
#include "clock/MusicTime.hpp"
#include "rtpc/PropertyRegistry.hpp"
#include <algorithm>
#include <cmath>
#include <optional>
#include <vector>

namespace player2 {
// Values use the native property units and bounds. Frame positions are relative
// to the caller's chosen timeline origin; this type owns no playback clock.
enum class EnvelopeTarget { VolumeDb=0, LowPassHz=2, HighPassHz=3 };
struct EnvelopePoint { Frame frame{}; double value{}; Shape shape{}; };
inline double envelopeValueBetween(const EnvelopePoint& a,const EnvelopePoint& b,Frame frame) noexcept {
    if(a.shape==Shape::Hold) return frame==b.frame ? b.value : a.value;
    const auto offset=static_cast<std::uint64_t>(frame)-static_cast<std::uint64_t>(a.frame);
    const auto width=static_cast<std::uint64_t>(b.frame)-static_cast<std::uint64_t>(a.frame);
    return a.value+(b.value-a.value)*shapeValue(static_cast<double>(offset)/static_cast<double>(width),a.shape);
}
struct TimelineEnvelope {
    EnvelopeTarget target{};
    std::vector<EnvelopePoint> points;

    bool valid() const noexcept {
        const auto id=static_cast<int>(target);
        if(id!=0 && id!=2 && id!=3) return false;
        const auto* descriptor=propertyDescriptor(id);
        for(std::size_t i=0;i<points.size();++i) {
            const auto& point=points[i];
            const auto shape=static_cast<int>(point.shape);
            if(!std::isfinite(point.value) || point.value<descriptor->min || point.value>descriptor->max ||
               shape<0 || shape>static_cast<int>(Shape::EaseOut) || (i && point.frame<=points[i-1].frame)) return false;
        }
        return true;
    }
    // Control-thread evaluation only: validation and lookup are linear. A
    // render path needs a separately prepared, bounded immutable snapshot.
    std::optional<double> valueAt(Frame frame) const noexcept {
        if(!valid() || points.empty()) return std::nullopt;
        if(frame<=points.front().frame) return points.front().value;
        for(std::size_t i=1;i<points.size();++i) if(frame<=points[i].frame) {
            return envelopeValueBetween(points[i-1],points[i],frame);
        }
        return points.back().value;
    }
};
// Built on the owning control thread. The caller supplies its admitted point
// budget; failed validation/budget admission does not publish a partial curve.
// Allocation failures propagate to the preparation caller, never the callback.
// Copy/replacement/destruction and publication also belong off the callback.
class PreparedTimelineEnvelope {
    EnvelopeTarget target_;
    std::vector<EnvelopePoint> points_;
    PreparedTimelineEnvelope(const TimelineEnvelope& source):target_(source.target),points_(source.points) {}
public:
    static std::optional<PreparedTimelineEnvelope> prepare(const TimelineEnvelope& source,std::size_t pointBudget) {
        if(source.points.size()>pointBudget || !source.valid()) return std::nullopt;
        return PreparedTimelineEnvelope(source);
    }
    EnvelopeTarget target() const noexcept { return target_; }
    std::size_t size() const noexcept { return points_.size(); }
    // Immutable binary search: no validation scan, allocation, lock or cursor
    // mutation on the render path. Nonmonotonic seek/loop queries are valid.
    std::optional<double> valueAt(Frame frame) const noexcept {
        if(points_.empty()) return std::nullopt;
        if(frame<=points_.front().frame) return points_.front().value;
        if(frame>=points_.back().frame) return points_.back().value;
        const auto next=std::lower_bound(points_.begin(),points_.end(),frame,
            [](const EnvelopePoint& point,Frame at) { return point.frame<at; });
        return envelopeValueBetween(*(next-1),*next,frame);
    }
};
}
