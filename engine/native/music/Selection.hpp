#pragma once
#include "compiled/MusicGraph.hpp"
#include "music/SelectionDecision.hpp"
#include <array>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>

namespace player2 {
inline std::uint64_t playlistItemSeed(const Playlist& playlist,const PlaylistItem& item) noexcept {
    // v1-v5 retain their historical index-derived sequence. v6 carries a
    // compiler-owned stable ID seed and never hashes or allocates on audio.
    return item.stateSeed.value_or(playlist.seed^static_cast<std::uint64_t>(item.index+1));
}

struct SelectionState {
    std::uint64_t random{1},revision{}; int cursor{-1},historyHead{},historySize{};
    std::array<int,64> history{}; std::array<bool,64> bag{};
    SelectionDecision decision;
    void reset(std::uint64_t seed) noexcept { ++revision; random=seed ? seed : 1; cursor=-1; historyHead=historySize=0; bag.fill(false); decision={}; }
    std::uint64_t nextRandom() noexcept { auto x=random; x^=x>>12; x^=x<<25; x^=x>>27; random=x; return x*2685821657736338717ULL; }
    double unit() noexcept { return (nextRandom()>>11)*(1.0/9007199254740992.0); }
    bool recently(int choice,int avoid) const noexcept {
        for(int i=0;i<std::min(avoid,historySize);++i) if(history[(historyHead+63-i)%64]==choice) return true;
        return false;
    }
    void remember(int choice) noexcept { ++revision; history[historyHead]=choice; historyHead=(historyHead+1)%64; historySize=std::min(64,historySize+1); }
    int sequence(int count,std::int64_t at=-1,std::uint64_t epoch=0) noexcept {
        if(count<=0 || count>64) return -1;
        cursor=(cursor+1)%count; remember(cursor); decision={};decision.kind=SelectionDecisionKind::Sequence;
        decision.eligible=std::uint64_t{1}<<cursor;decision.randomBefore=random;decision.at=at;decision.epoch=epoch;return cursor;
    }
    void jumpTo(int choice,std::int64_t at=-1,std::uint64_t epoch=0) noexcept {
        if(choice<0 || choice>=64)return;
        cursor=choice;remember(choice);decision={};decision.kind=SelectionDecisionKind::Jump;
        decision.randomBefore=random;decision.eligible=std::uint64_t{1}<<choice;decision.at=at;decision.epoch=epoch;
    }
    int weighted(const double* weights,int count,int avoid,RandomMode mode,std::int64_t at=-1,std::uint64_t epoch=0) noexcept {
        if(!weights || count<=0 || count>64) return -1;
        for(int n=0;n<count;++n) if(!std::isfinite(weights[n]) || weights[n]<0) return -1;
        bool unused=false; for(int i=0;i<count;++i) unused|=!bag[i];
        std::uint64_t used=0;
        if(mode==RandomMode::Shuffle)for(int i=0;i<count;++i)if(bag[i])used|=std::uint64_t{1}<<i;
        const bool recycled=mode==RandomMode::Shuffle && !unused;
        if(recycled) bag.fill(false);
        avoid=std::clamp(avoid,0,count-1);
        std::array<bool,64> allowed{}; double total=0;
        while(true) {
            total=0;
            for(int i=0;i<count;++i) { allowed[i]=!(mode==RandomMode::Shuffle && bag[i]) && !recently(i,avoid); if(allowed[i]) total+=weights[i]; }
            if(total>0 || avoid==0) break;
            --avoid;
        }
        if(total<=0) return -1;
        double scale=1,maximum=0;
        for(int i=0;i<count;++i) if(allowed[i]) maximum=std::max(maximum,weights[i]);
        // Finite authored weights may still overflow their sum or lose almost
        // every ticket to subnormal rounding. Normalize only these extremes;
        // ordinary-weight projects keep their exact existing random sequence.
        if(!std::isfinite(total) || maximum<std::numeric_limits<double>::min()) {
            scale=maximum; total=0;
            for(int i=0;i<count;++i) if(allowed[i]) total+=weights[i]/scale;
        }
        decision={};decision.kind=mode==RandomMode::Shuffle ? SelectionDecisionKind::Shuffle : SelectionDecisionKind::Weighted;
        decision.effectiveAvoid=avoid;decision.randomBefore=random;decision.scale=scale;decision.total=total;
        decision.shuffleUsed=used;decision.recycledShuffle=recycled;decision.at=at;decision.epoch=epoch;
        for(int i=0;i<count;++i) {
            if(allowed[i])decision.eligible|=std::uint64_t{1}<<i;
            if(recently(i,avoid))decision.repeated|=std::uint64_t{1}<<i;
        }
        decision.unit=unit();double ticket=decision.unit*total; int choice=-1;
        for(int i=0;i<count;++i) if(allowed[i]) { choice=i; ticket-=weights[i]/scale; if(ticket<0) break; }
        bag[choice]=true; cursor=choice; remember(choice); return choice;
    }
};
inline bool matches(const std::vector<Condition>& conditions,const std::array<int,4096>& values) noexcept {
    for(const auto& c:conditions) if(c.group<0 || c.group>=4096 || values[c.group]!=c.value) return false;
    return true;
}
inline int selectSwitchSubTrack(const Track& track,int value) noexcept {
    int fallback=-1;
    for(std::size_t n=0;n<track.subTracks.size();++n) {
        if(track.subTracks[n].value==-1)fallback=static_cast<int>(n);
        else if(track.subTracks[n].value==value)return static_cast<int>(n);
    }
    return fallback;
}
struct SwitchChoice { int target{-1},path{-1},matched{},specificity{-1},priority{}; };
inline SwitchChoice resolveSwitch(const Switch& container,const std::array<int,4096>& values) noexcept {
    SwitchChoice result; result.target=container.fallback;
    for(const auto& path:container.paths) if(matches(path.conditions,values)) {
        const auto specificity=static_cast<int>(path.conditions.size());
        if(result.matched==0 || specificity>result.specificity ||
            (specificity==result.specificity && (path.priority>result.priority ||
                (path.priority==result.priority && path.index<result.path)))) {
            result.target=path.target; result.path=path.index; result.specificity=specificity; result.priority=path.priority;
        }
        ++result.matched;
    }
    return result;
}
}
