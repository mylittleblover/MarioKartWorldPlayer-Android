#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace player2 {
enum class Shape { Linear=0,Hold=1,SCurve=2,EaseIn=3,EaseOut=4 };
enum class Modifier { Absolute=0,Sum=1,Highest=2,Boolean=3 };
inline double shapeValue(double x,Shape shape) noexcept {
    x=std::clamp(x,0.0,1.0);
    switch(shape) {
        case Shape::Hold: return x>=1 ? 1 : 0;
        case Shape::SCurve: return x*x*(3-2*x);
        case Shape::EaseIn: return x*x;
        case Shape::EaseOut: return 1-(1-x)*(1-x);
        default: return x;
    }
}
struct Point { double x{},y{}; Shape shape{}; };
inline double curveValue(const std::vector<Point>& points,double x) noexcept {
    if(points.empty()) return 0;
    if(x<=points.front().x) return points.front().y;
    for(std::size_t i=1;i<points.size();++i) if(x<=points[i].x) {
        const auto& a=points[i-1]; const auto& b=points[i];
        const double width=b.x-a.x;
        const double position=std::isfinite(width) ? (x-a.x)/width : (x*0.5-a.x*0.5)/(b.x*0.5-a.x*0.5);
        return a.y+(b.y-a.y)*shapeValue(position,a.shape);
    }
    return points.back().y;
}
inline double modify(double base,double curve,Modifier policy) noexcept {
    switch(policy) {
        case Modifier::Sum: return base+curve;
        case Modifier::Highest: return std::max(base,curve);
        case Modifier::Boolean: return base>=0.5 || curve>=0.5 ? 1 : 0;
        default: return curve;
    }
}
struct SmoothedParameter {
    double current{},target{},attackStep{1},releaseStep{1};
    void prepare(double value,std::int64_t attack,std::int64_t release) noexcept {
        current=target=value; attackStep=attack>0 ? -std::expm1(-1.0/attack) : 1;
        releaseStep=release>0 ? -std::expm1(-1.0/release) : 1;
    }
    double next() noexcept {
        const double step=target>current ? attackStep : releaseStep;
        const double low=std::min(current,target),high=std::max(current,target);
        current=std::clamp(current*(1-step)+target*step,low,high); return current;
    }
};
inline double dbGain(double db) noexcept { return std::pow(10.0,db/20); }
}
