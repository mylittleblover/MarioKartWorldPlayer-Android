#pragma once
#include "api/AudioSourceTypes.hpp"
#include <array>
#include <bitset>
#include <limits>
namespace player2 {
// Bounded execution evidence, not authored state or a persistent Data cache.
class StreamHistory {
public:
    using Mask=std::bitset<128>;
    struct Point {std::uint64_t epoch{};audio::TransportFrame at;};
    struct Range {Point begin;audio::TransportFrame end;Mask missing;};
    static constexpr std::size_t capacity=8192;
private:
    std::array<Range,capacity> ranges_{};std::size_t size_{},cursor_{};
    Point frontier_,loss_;bool lost_{};std::uint64_t dropped_{};
    static bool before(Point a,Point b) noexcept {return a.epoch<b.epoch || (a.epoch==b.epoch && a.at.value<b.at.value);}
    void lose(Point point) noexcept {
        if(!lost_){loss_=point;lost_=true;}
        if(dropped_<static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))++dropped_;
    }
public:
    bool complete()const noexcept{return !lost_;}
    std::size_t size()const noexcept{return size_;}
    std::uint64_t dropped()const noexcept{return dropped_;}
    bool known(Point point)const noexcept{return before(point,frontier_);}
    void rewind()noexcept{cursor_=0;}
    Mask expected(Point point)noexcept {
        while(cursor_<size_ && (ranges_[cursor_].begin.epoch<point.epoch ||
            (ranges_[cursor_].begin.epoch==point.epoch && ranges_[cursor_].end.value<=point.at.value)))++cursor_;
        if(cursor_<size_) {
            const auto& range=ranges_[cursor_];
            if(range.begin.epoch==point.epoch && range.begin.at.value<=point.at.value && point.at.value<range.end.value)return range.missing;
        }
        return {};
    }
    // New external input or newly different decode availability branches only the
    // future execution evidence. The verified prefix remains available to seek.
    void branch(Point point)noexcept {
        if(!before(point,frontier_))return;
        std::size_t keep=0;
        while(keep<size_ && before(ranges_[keep].begin,point)) {
            if(ranges_[keep].begin.epoch==point.epoch && ranges_[keep].end.value>point.at.value){ranges_[keep].end=point.at;++keep;break;}
            ++keep;
        }
        size_=keep;cursor_=0;frontier_=point;
        if(lost_ && !before(loss_,point))lost_=false;
    }
    bool record(Point point,const Mask& missing)noexcept {
        if(known(point)) {
            if(expected(point)==missing)return !lost_;
            branch(point);
        }
        if(point.at.value==std::numeric_limits<std::int64_t>::max()){lose(point);return false;}
        const audio::TransportFrame end{point.at.value+1};
        if(missing.any()) {
            if(size_ && ranges_[size_-1].begin.epoch==point.epoch && ranges_[size_-1].end.value==point.at.value && ranges_[size_-1].missing==missing)
                ranges_[size_-1].end=end;
            else if(size_<capacity)ranges_[size_++]={point,end,missing};
            else lose(point);
        }
        frontier_={point.epoch,end};return !lost_;
    }
};
}
