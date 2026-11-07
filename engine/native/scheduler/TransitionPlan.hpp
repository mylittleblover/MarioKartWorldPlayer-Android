#pragma once
#include "compiled/Graph.hpp"
#include "music/Selection.hpp"
#include <optional>
#include <bitset>

namespace player2 {
inline Frame addFrame(Frame a,Frame b) noexcept {
    if(b>0 && a>std::numeric_limits<Frame>::max()-b) return std::numeric_limits<Frame>::max();
    if(b<0 && a<std::numeric_limits<Frame>::min()-b) return std::numeric_limits<Frame>::min();
    return a+b;
}
struct TransitionChoice { const Transition* rule{}; int candidates{},specificity{-1},priority{}; };
inline bool transitionPattern(const std::vector<int>& pattern,int object,int segment,const std::bitset<4096>* owners=nullptr) noexcept {
    if(pattern.empty())return true;
    for(int index:pattern)if(index==object || index==segment || (owners && index>=0 && index<4096 && owners->test(index)))return true;
    return false;
}
inline TransitionChoice chooseTransition(const Graph& graph,int source,int sourceSegment,int destination,int destinationSegment,
    const std::array<int,4096>& values,const std::bitset<4096>* sourceOwners=nullptr,const std::bitset<4096>* destinationOwners=nullptr) noexcept {
    TransitionChoice result;
    for(const auto& rule:graph.transitions) {
        if(!transitionPattern(rule.sources,source,sourceSegment,sourceOwners) || !transitionPattern(rule.destinations,destination,destinationSegment,destinationOwners) ||
            !matches(rule.conditions,values)) continue;
        ++result.candidates;
        const int specificity=(rule.sources.empty() ? 0 : 1)+(rule.destinations.empty() ? 0 : 1)+static_cast<int>(rule.conditions.size());
        if(!result.rule || specificity>result.specificity || (specificity==result.specificity &&
            (rule.priority>result.priority || (rule.priority==result.priority && rule.index<result.rule->index)))) {
            result.rule=&rule; result.specificity=specificity; result.priority=rule.priority;
        }
    }
    return result;
}
// Musical offsets are compiled Transport-rate samples, never source sample positions.
// Convert once to an absolute frame on the same SampleClock as all voices.
inline std::optional<Frame> sourceSyncFrame(const Segment& segment,const Tempo& tempo,long double position,double speed,
    Frame now,SyncPoint sync,const std::optional<std::string>& filter,bool includeCurrent=false,bool loopAvailable=true,
    long double musicalPosition=std::numeric_limits<long double>::quiet_NaN(),int remainingLoops=-2,bool exitBreaksLoop=true,long double* musicalDistance=nullptr,Frame* matchedCue=nullptr) noexcept {
    if(!std::isfinite(speed) || speed<=0) return std::nullopt;
    if(!std::isfinite(musicalPosition)) musicalPosition=position;
    const Frame musical=Tempo::roundedFrame(musicalPosition);
    if(remainingLoops==-2) remainingLoops=segment.hasLoop ? segment.loop.count : 0;
    if(!loopAvailable) remainingLoops=0;
    const long double loopSpan=segment.hasLoop ? static_cast<long double>(segment.loop.end)-segment.loop.start : 0;
    long double delta=0;
    switch(sync) {
        case SyncPoint::Immediate: break;
        case SyncPoint::NextBeat: delta=tempo.nextBeat(musical)-musicalPosition; break;
        case SyncPoint::NextBar: delta=tempo.nextBar(musical)-musicalPosition; break;
        case SyncPoint::NextGrid: delta=tempo.nextGrid(musical)-musicalPosition; break;
        case SyncPoint::ExitCue:
            delta=segment.exit-position;
            if(!exitBreaksLoop && segment.hasLoop && position<=segment.loop.end && segment.exit>=segment.loop.end && remainingLoops!=0) {
                if(remainingLoops<0) return std::nullopt;
                delta+=loopSpan*remainingLoops;
            }
            break;
        case SyncPoint::NextCue:
        case SyncPoint::NextCustomCue: {
            std::optional<long double> best;
            auto candidate=[&](Frame frame) {
                long double distance=frame-position;
                if(segment.hasLoop && position<=segment.loop.end && frame>segment.loop.end && remainingLoops!=0) {
                    if(remainingLoops<0) return;
                    distance+=loopSpan*remainingLoops;
                }
                if(distance<0 || (!includeCurrent && distance==0)) {
                    if(remainingLoops==0 || !segment.hasLoop || frame<segment.loop.start || frame>segment.loop.end) return;
                    distance+=loopSpan;
                }
                if(distance>=0 && (!best || distance<*best)) {best=distance;if(matchedCue)*matchedCue=frame;}
            };
            if(sync==SyncPoint::NextCue && !filter) candidate(segment.exit);
            for(const auto& cue:segment.cues) if((sync!=SyncPoint::NextCustomCue || cue.kind==0) && (!filter || cue.name==*filter)) candidate(cue.frame);
            if(!best) return std::nullopt;
            delta=*best; break;
        }
    }
    delta=std::max(0.0L,delta);
    if(musicalDistance) *musicalDistance=delta;
    const auto frames=Tempo::roundedFrame(std::ceil(delta/speed));
    return addFrame(now,frames);
}
inline Frame fadeDuration(const Fade& fade,const Tempo& tempo,double speed) noexcept {
    return Tempo::roundedFrame(tempo.atBeat(fade.beats)/static_cast<long double>(speed));
}
inline Frame fadeOffset(const Fade& fade,const Tempo& tempo,double speed) noexcept {
    return Tempo::roundedFrame(tempo.atBeat(fade.offset)/static_cast<long double>(speed));
}
inline std::optional<Frame> destinationEntryFrame(const Segment& segment,EntryMode mode,Frame sameTime,Frame lastExit,
    const std::optional<std::string>& filter,SelectionState& random) noexcept {
    if(mode==EntryMode::EntryCue) return segment.entry;
    if(mode==EntryMode::SameTime) return std::clamp(sameTime,segment.entry,segment.exit-1);
    if(mode==EntryMode::LastExit) return lastExit<0 ? segment.entry : std::clamp(lastExit,segment.entry,segment.exit-1);
    if(mode!=EntryMode::RandomCue && mode!=EntryMode::RandomCustomCue) return std::nullopt;
    std::size_t count=mode==EntryMode::RandomCue && !filter ? 1 : 0;
    for(const auto& cue:segment.cues) if(cue.frame<segment.exit && (mode!=EntryMode::RandomCustomCue || cue.kind==0) && (!filter || cue.name==*filter)) ++count;
    if(count==0) return std::nullopt;
    auto selected=static_cast<std::size_t>(random.nextRandom()%count);
    if(mode==EntryMode::RandomCue && !filter) { if(selected==0) return segment.entry; --selected; }
    for(const auto& cue:segment.cues) if(cue.frame<segment.exit && (mode!=EntryMode::RandomCustomCue || cue.kind==0) && (!filter || cue.name==*filter)) {
        if(selected==0) return cue.frame;
        --selected;
    }
    return std::nullopt;
}
}
