#include "music/GraphReader.hpp"

namespace player2 {
void GraphReader::segmentEnvelopes(Graph& graph) {
    const int count=integer();if(count<0 || count>12288)invalid();
    for(int n=0;n<count;++n) {
        const int segmentIndex=index(),target=integer();
        if(target!=0 && target!=2 && target!=3)invalid();
        auto segment=std::find_if(graph.segments.begin(),graph.segments.end(),[&](const Segment& s){return s.index==segmentIndex;});
        if(segment==graph.segments.end() || segment->envelopes.size()>=3)invalid();
        for(const auto& existing:segment->envelopes)if(static_cast<int>(existing.target())==target)invalid();
        TimelineEnvelope source;source.target=static_cast<EnvelopeTarget>(target);
        source.points=list<EnvelopePoint>([&]{EnvelopePoint p{frame(),number(),enumeration<Shape>()};if(p.frame<0)invalid();return p;},4096);
        envelopePoints_+=source.points.size();if(envelopePoints_>16384)invalid();
        auto prepared=PreparedTimelineEnvelope::prepare(source,4096);if(!prepared)invalid();
        segment->envelopes.push_back(std::move(*prepared));
    }
}
void GraphReader::clipEnvelopes(Graph& graph) {
    const int count=integer();if(count<0 || count>12288)invalid();
    for(int n=0;n<count;++n) {
        const int clipIndex=index(),target=integer();
        if(target!=0 && target!=2 && target!=3)invalid();
        Clip* owner=nullptr;
        const auto match=[&](std::vector<Clip>& clips) {
            for(auto& clip:clips)if(clip.index==clipIndex){if(owner)invalid();owner=&clip;}
        };
        for(auto& track:graph.tracks){match(track.clips);for(auto& sub:track.subTracks)match(sub.clips);}
        if(!owner || owner->envelopes.size()>=3)invalid();
        for(const auto& curve:owner->envelopes)if(static_cast<int>(curve.target())==target)invalid();
        TimelineEnvelope source;source.target=static_cast<EnvelopeTarget>(target);
        source.points=list<EnvelopePoint>([&]{EnvelopePoint p{frame(),number(),enumeration<Shape>()};if(p.frame<0)invalid();return p;},4096);
        envelopePoints_+=source.points.size();if(envelopePoints_>16384)invalid();
        auto prepared=PreparedTimelineEnvelope::prepare(source,4096);if(!prepared)invalid();
        owner->envelopes.push_back(std::move(*prepared));
    }
}
Graph GraphReader::read() {
        if(u32()!=0x50324731) invalid();
        const auto version=integer();version_=version;if(!protocol::supportedVersion(version))invalid();
        const auto body=u32(),checksum=u32();
        if(body!=size_-16 || crc(data_+16,body)!=checksum) invalid();
        Graph g;g.version=version; g.sampleRate=integer(); if(g.sampleRate!=44100 && g.sampleRate!=48000 && g.sampleRate!=96000) invalid();
        g.identities=list<std::string>([&] { return string(); }); identities_=static_cast<int>(g.identities.size()); if(identities_==0) invalid();
        g.nodes=list<Node>([&] { Node n; n.index=index(); n.kind=enumeration<Kind>(); n.parent=index(true); n.tempo.sampleRate=g.sampleRate; n.tempo.bpm=number(); n.tempo.numerator=integer(); n.tempo.denominator=integer(); n.tempo.grid=number(); n.tempo.gridOffset=number(); if(integer()!=7 || !n.tempo.valid()) invalid(); for(auto& property:n.properties) property=number();
            if(version>=2)n.configuredProperties=u32();
            else for(int property=0;property<7;++property)if(n.properties[property]!=propertyDescriptor(property)->defaultValue)n.configuredProperties|=std::uint32_t{1}<<property;
            return n; });
        g.segments=list<Segment>([&] { Segment s; s.index=index(); s.entry=frame(); s.exit=frame(); if(s.entry<0 || s.exit<=s.entry) invalid(); s.hasLoop=boolean(); if(s.hasLoop) { s.loop.start=frame(); s.loop.end=frame(); s.loop.count=integer(); s.loop.retainTail=boolean(); if(s.loop.start<s.entry || s.loop.end>s.exit || s.loop.end<=s.loop.start || s.loop.count< -1) invalid(); } s.cues=list<Cue>([&] { Cue c; c.index=index(); c.name=string(); c.frame=frame(); c.kind=static_cast<int>(enumeration<protocol::CueKind>()); c.event=index(true); return c; }); return s; });
        g.tracks=list<Track>([&] { Track t; t.index=index(); t.segment=index(); t.mode=enumeration<TrackMode>(); t.group=index(true); t.bus=index(true); t.seed=static_cast<std::uint64_t>(frame()); t.avoidRepeat=integer(); t.reset=boolean(); t.clips=list<Clip>([&] { return clip(); }); t.subTracks=list<SubTrack>([&] { SubTrack s; s.index=index(); s.weight=number(); s.value=index(true); s.clips=list<Clip>([&] { return clip(); }); if(s.weight<=0) invalid(); return s; },64); t.sends=list<Send>([&] { return send(); }); if(t.avoidRepeat<0 || t.avoidRepeat>63) invalid(); return t; });
        g.playlists=list<Playlist>([&] { Playlist p; p.index=index(); p.root=index(); p.seed=static_cast<std::uint64_t>(frame()); p.reset=boolean(); p.items=list<PlaylistItem>([&] { PlaylistItem item; item.index=index(); item.target=index(true); item.mode=enumeration<PlaylistMode>(); item.random=enumeration<RandomMode>(); item.avoidRepeat=integer(); item.weight=number(); item.loops=integer(); item.children=indices(64); if(version>=protocol::PlaylistSeedVersion)item.stateSeed=static_cast<std::uint64_t>(frame()); if(item.weight<=0 || item.loops< -1 || item.loops==0 || item.avoidRepeat<0 || item.avoidRepeat>63) invalid(); return item; }); return p; });
        g.switches=list<Switch>([&] { Switch s; s.index=index(); s.groups=indices(); s.paths=list<Path>([&] { Path p; p.index=index(); p.conditions=list<Condition>([&] { return condition(); }); p.target=index(true); p.priority=integer(); return p; }); s.fallback=index(true); s.continueSame=boolean(); return s; });
        g.groups=list<SyncGroup>([&] { SyncGroup s; s.index=index(); s.kind=static_cast<int>(enumeration<protocol::SyncKind>()); s.values=indices(); s.defaultValue=index(); return s; });
        g.parameters=list<Parameter>([&] { Parameter p; p.index=index(); p.min=number(); p.max=number(); p.defaultValue=number(); p.attack=frame(); p.release=frame(); if(p.min>=p.max || p.defaultValue<p.min || p.defaultValue>p.max || p.attack<0 || p.release<0) invalid(); return p; });
        g.events=list<Event>([&] { Event e; e.index=index(); e.actions=list<Action>([&] { return action(); }); return e; });
        g.transitions=list<Transition>([&] { Transition t; t.index=index(); t.sources=indices(); t.destinations=indices(); t.specificity=integer(); t.sync=enumeration<SyncPoint>(); t.sourceCue=nullableString(); t.entry=enumeration<EntryMode>(); t.destinationCue=nullableString(); t.jumpItem=index(true); t.bridge=index(true); t.fadeOut=fade(); t.fadeIn=fade(); t.postExit=boolean(); t.preEntry=boolean(); t.priority=integer(); t.conditions=list<Condition>([&] { return condition(); }); return t; });
        g.curves=list<Rtpc>([&] { Rtpc r; r.index=index(); r.parameter=index(); r.target=index(); r.property=static_cast<int>(enumeration<protocol::Property>()); r.policy=enumeration<Modifier>(); r.points=list<Point>([&] { Point p; p.x=number(); p.y=number(); p.shape=enumeration<Shape>(); return p; }); if(r.points.size()<2) invalid(); for(std::size_t n=1;n<r.points.size();++n) if(r.points[n].x<=r.points[n-1].x) invalid(); return r; });
        g.stingers=list<Stinger>([&] { Stinger s; s.index=index(); s.trigger=index(); s.segment=index(); s.sync=enumeration<SyncPoint>(); s.priority=integer(); s.replace=boolean(); s.cooldownBeats=number(); if(s.cooldownBeats<0) invalid(); return s; });
        g.buses=list<Bus>([&] { Bus b; b.index=index(); b.output=index(true); b.auxiliary=boolean(); b.effects=list<Effect>([&] { Effect e; e.index=index(); e.kind=static_cast<int>(enumeration<protocol::EffectKind>()); e.bypass=boolean(); if(integer()!=10) invalid(); for(auto& p:e.parameters) p=number(); return e; }); b.sends=list<Send>([&] { return send(); }); return b; });
        g.assets=list<Asset>([&] { Asset a; a.index=index(); a.path=string(); a.sampleRate=integer(); a.channels=integer(); a.frames=frame(); a.lookAhead=frame(); if(version>=3) {need(a.sha256.size());std::copy_n(data_+position_,a.sha256.size(),a.sha256.begin());position_+=a.sha256.size();a.hasDigest=true;}
            if(version>=4 && boolean())a.source=CompiledSourceReference{string(),nullableString(),nullableString(),nullableString()};
            if(a.sampleRate<8000 || a.sampleRate>192000 || a.channels<1 || a.channels>2 || a.frames<=0 || a.lookAhead<0) invalid();
            return a; });
        g.entries=list<Entry>([&] { Entry e; e.index=index(); e.start=index(); e.actions=list<Action>([&] { return action(); }); return e; });
        g.performances=list<Performance>([&] { Performance p; p.index=index(); p.start=index(); p.steps=list<PerformanceStep>([&] { PerformanceStep s; s.index=index(); s.frame=frame(); s.action=action(); if(s.frame<0) invalid(); return s; }); p.endFrame=frame(); if(p.endFrame!= -1 && p.endFrame<=0) invalid(); p.end=enumeration<EndBehavior>(); if(version>=protocol::PlaybackExtensionVersion) {if(boolean())p.endFade=PerformanceFade{{frame()},{frame()},enumeration<Shape>()};p.ending=index(true);} return p; });
        g.hasMusicalLoop=boolean(); if(g.hasMusicalLoop) { auto& m=g.musicalLoop; m.intro=index(true); m.loop=index(); m.ending=index(true); m.repeats=integer(); m.retainIntroTail=boolean(); m.stop=enumeration<EndBehavior>(); if(version>=protocol::MusicalLoopVersion)m.playlist=index(); if(m.repeats<1) invalid(); }
        g.hasAutomatic=boolean(); if(g.hasAutomatic) { auto& a=g.automatic; a.parameter=index(); a.low=number(); a.high=number(); a.cycleBeats=number(); a.returnDefault=boolean(); a.seed=static_cast<std::uint64_t>(frame()); if(a.low>a.high || a.cycleBeats<=0) invalid(); }
        if(version>=protocol::EffectRtpcVersion)g.effectCurves=list<EffectRtpc>([&] {
            EffectRtpc r;r.index=index();r.parameter=index();r.bus=index();r.effect=index();
            r.property=static_cast<int>(enumeration<protocol::EffectParameter>());r.policy=enumeration<Modifier>();
            r.points=list<Point>([&] {return Point{number(),number(),enumeration<Shape>()};});return r;
        });
        if(version>=protocol::AlbumFlowVersion)g.albumFlows=list<AlbumFlow>([&] {
            AlbumFlow f;f.from=index();f.to=index();f.mode=enumeration<AlbumFlowMode>();f.duration={frame()};f.bridge=index(true);f.retainTail=boolean();return f;
        });
        if(version>=protocol::SegmentEnvelopeVersion)segmentEnvelopes(g);
        if(version>=protocol::ClipEnvelopeVersion)clipEnvelopes(g);
        if(position_!=size_) invalid();
        std::array<bool,4096> occupied{}; for(const auto& node:g.nodes) { if(occupied[node.index]) invalid(); occupied[node.index]=true; }
        g.indexNodes();
        if(!validGraph(g)) invalid();
        return g;
    }
}
