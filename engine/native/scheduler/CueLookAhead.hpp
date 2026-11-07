#pragma once
#include "music/PlaylistSession.hpp"
#include "music/SelectionPreview.hpp"
#include "scheduler/TransitionPlan.hpp"
namespace player2 {
// A read-only selection preview, not an execution runtime. No audio, Events,
// live history commits, wall-clock time or heap allocation occurs here.
struct CueForecast {
    enum class Outcome { Found, Exhausted, Blocked, Limited };
    Outcome outcome{Outcome::Exhausted};
    Frame sync{},position{};
    int segment{-1};
    std::size_t hops{};
    std::array<int,65> path{};std::array<double,65> rates{};
};
inline bool matchesForecastSuffix(const CueForecast& previous,const CueForecast& current,bool entered) noexcept {
    if(previous.outcome!=CueForecast::Outcome::Found || current.outcome!=CueForecast::Outcome::Found ||
        previous.sync!=current.sync || previous.segment!=current.segment || previous.position!=current.position)return false;
    const std::size_t offset=entered ? 1 : 0;
    if(previous.hops<offset || current.hops!=previous.hops-offset)return false;
    for(std::size_t n=0;n<=current.hops;++n)
        if(current.path[n]!=previous.path[n+offset] || current.rates[n]!=previous.rates[n+offset])return false;
    return true;
}
template<class Speed>
CueForecast forecastCue(const Graph& graph,PlaylistSession session,SelectionPreview& preview,
    const std::array<int,4096>& values,const Segment& current,long double position,
    long double musicalPosition,int remainingLoops,bool loopAvailable,Frame now,
    SyncPoint sync,const std::optional<std::string>& filter,Speed speed,bool includeCurrent=false) noexcept {
    preview.reset();CueForecast result;const Segment* segment=&current;int traversalBudget=4096;
    // All future next() calls share one traversal budget, not 4096 per hop.
    for(std::size_t hop=0;hop<=64;++hop) {
        result.hops=hop;result.path[hop]=segment->index;const auto* node=graph.node(segment->index);
        const auto rate=speed(segment->index);result.rates[hop]=rate;
        if(!node || !std::isfinite(rate) || rate<=0) {result.outcome=CueForecast::Outcome::Blocked;return result;}
        Frame local{};
        const auto cue=sourceSyncFrame(*segment,node->tempo,position,rate,now,sync,filter,
            includeCurrent,loopAvailable,musicalPosition,remainingLoops,false,nullptr,&local);
        // A post-exit marker must not pin a forecast to a segment the live
        // playlist has already left. The live deferred path handles exhaustion.
        if(cue && local<=segment->exit) {
            result.outcome=CueForecast::Outcome::Found;result.sync=*cue;result.position=local;result.segment=segment->index;return result;
        }
        if(hop==64) {result.outcome=CueForecast::Outcome::Limited;return result;}
        const auto exit=sourceSyncFrame(*segment,node->tempo,position,rate,now,SyncPoint::ExitCue,
            std::nullopt,true,loopAvailable,musicalPosition,remainingLoops,false);
        if(!exit) {result.outcome=CueForecast::Outcome::Blocked;return result;}
        now=*exit;
        const int next=session.next(graph,preview,values,-1,0,&traversalBudget);
        if(next<0) {
            result.outcome=session.limited() ? CueForecast::Outcome::Limited :
                session.waitingSwitch() ? CueForecast::Outcome::Blocked : CueForecast::Outcome::Exhausted;
            return result;
        }
        segment=find(graph.segments,next);
        if(!segment) {result.outcome=CueForecast::Outcome::Blocked;return result;}
        position=musicalPosition=segment->entry;remainingLoops=segment->hasLoop ? segment->loop.count : 0;
        loopAvailable=true;includeCurrent=true;
    }
    result.outcome=CueForecast::Outcome::Limited;return result;
}
}
