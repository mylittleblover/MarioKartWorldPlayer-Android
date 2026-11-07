#pragma once
#include "compiled/Graph.hpp"
#include "compiled/GraphProtocolIds.hpp"
#include "rtpc/PropertyRegistry.hpp"
#include "dsp/ParameterDescriptors.hpp"
#include "music/EffectGraphValidation.hpp"
#include <algorithm>
#include <functional>
#include <unordered_set>

namespace player2 {
// Runs before output starts. Parsing and CRC alone are not semantic validation.
inline bool validGraph(const Graph& g) {
    if(!protocol::supportedVersion(g.version))return false;
    auto typed=[&](int id,Kind kind) { const auto* n=g.node(id); return n && n->kind==kind; };
    auto playable=[&](int id) { const auto* n=g.node(id); return n && (n->kind==Kind::Segment || n->kind==Kind::Playlist || n->kind==Kind::Switch); };
    auto optionalPlayable=[&](int id) { return id==-1 || playable(id); };
    std::unordered_set<std::string> names;
    for(const auto& name:g.identities) if(name.empty() || !names.insert(name).second) return false;
    for(const auto& n:g.nodes) {
        if(n.configuredProperties&~std::uint32_t{127})return false;
        if(g.version>=2 && n.parent>=0) {const auto* parent=g.node(n.parent);if(!parent || (parent->configuredProperties&~n.configuredProperties))return false;}
        for(int property=0;property<7;++property) {
            const auto* d=propertyDescriptor(property); const auto value=n.properties[property];
            if(!std::isfinite(value) || value<d->min || value>d->max) return false;
            if(g.version>=2 && value!=d->defaultValue && !(n.configuredProperties&(std::uint32_t{1}<<property)))return false;
        }
        int parent=n.parent,depth=0;
        while(parent!=-1) { const auto* p=g.node(parent); if(!p || parent==n.index || ++depth>32) return false; parent=p->parent; }
    }
    std::array<bool,4096> defined{};
    auto define=[&](int id,Kind kind) { if(!typed(id,kind) || defined[id]) return false; defined[id]=true; return true; };
    // Stable identity indices have one owner across nodes and nested DTOs.
    std::array<bool,4096> owned{};
    auto claim=[&](int id) {
        if(id<0 || static_cast<std::size_t>(id)>=g.identities.size() || id>=4096 || owned[id]) return false;
        owned[id]=true; return true;
    };
    for(const auto& n:g.nodes) if(!claim(n.index)) return false;
    for(const auto& s:g.segments) for(const auto& c:s.cues) if(!claim(c.index)) return false;
    for(const auto& t:g.tracks) {
        for(const auto& c:t.clips) if(!claim(c.index)) return false;
        for(const auto& sub:t.subTracks) {
            if(!claim(sub.index)) return false;
            for(const auto& c:sub.clips) if(!claim(c.index)) return false;
        }
    }
    for(const auto& p:g.playlists) for(const auto& item:p.items) if(!claim(item.index) || (g.version>=protocol::PlaylistSeedVersion && !item.stateSeed)) return false;
    for(const auto& s:g.switches) for(const auto& path:s.paths) if(!claim(path.index)) return false;
    for(const auto& s:g.groups) for(int value:s.values) if(!claim(value)) return false;
    for(const auto& b:g.buses) for(const auto& e:b.effects) if(!claim(e.index)) return false;
    for(const auto& e:g.entries) if(!claim(e.index)) return false;
    for(const auto& p:g.performances) {
        if(!claim(p.index)) return false;
        for(const auto& step:p.steps) if(!claim(step.index)) return false;
    }
    for(const auto& a:g.assets) {
        const auto validSourceId=[](const std::string& id) {
            if(id.empty() || id.size()>128)return false;
            for(const auto c:id)if(!((c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9') || c=='_' || c=='.' || c==':' || c=='-'))return false;
            return true;
        };
        if(a.source && (g.version<4 || !validSourceId(a.source->assetId) || (a.source->entryId && !validSourceId(*a.source->entryId)) ||
            (a.source->subsongId && !validSourceId(*a.source->subsongId)) || (a.source->variantId && !validSourceId(*a.source->variantId))))return false;
        if((g.version>=3 && !a.hasDigest) || !claim(a.index) || a.path.empty() || a.path.front()=='/' || a.path.find('\\')!=std::string::npos ||
           a.path.find('\0')!=std::string::npos || a.path.find(':')!=std::string::npos) return false;
        std::size_t start=0;
        while(start<=a.path.size()) {
            const auto end=a.path.find('/',start);
            const auto part=a.path.substr(start,end==std::string::npos ? end : end-start);
            if(part.empty() || part=="." || part=="..") return false;
            if(end==std::string::npos) break;
            start=end+1;
        }
    }
    auto clipsValid=[&](const std::vector<Clip>& clips) {
        const auto* volume=propertyDescriptor(0);
        for(const auto& c:clips) { if(!c.envelopes.empty() && g.version<protocol::ClipEnvelopeVersion)return false; const auto* a=find(g.assets,c.asset); if(!a || !std::isfinite(c.gainDb) || c.gainDb<volume->min || c.gainDb>volume->max || c.start<0 || c.sourceOffset<0 || c.sourceLength<=0 || c.fadeIn<0 || c.fadeOut<0 || c.sourceLength>a->frames || c.sourceOffset>a->frames-c.sourceLength || (c.loop && (g.version<5 || !validClipLoop(*c.loop,c.sourceOffset,c.sourceLength)))) return false; }
        return true;
    };
    for(const auto& s:g.segments) {
        if(!define(s.index,Kind::Segment)) return false;
        Frame previous=-1; for(const auto& cue:s.cues) { if(cue.frame<s.entry || cue.frame>s.exit || cue.frame<previous || (cue.kind==1 && !typed(cue.event,Kind::Event))) return false; previous=cue.frame; }
    }
    for(const auto& t:g.tracks) {
        const auto* node=g.node(t.index);
        if(!define(t.index,Kind::Track) || !typed(t.segment,Kind::Segment) || node->parent!=t.segment || (t.bus!=-1 && !typed(t.bus,Kind::Bus)) || !clipsValid(t.clips)) return false;
        if(t.subTracks.size()>64)return false;
        if(t.mode==TrackMode::Switch) {
            const auto* group=find(g.groups,t.group);
            if(!typed(t.group,Kind::SyncGroup) || !group)return false;
            std::unordered_set<int> mapped;
            for(const auto& sub:t.subTracks) {
                if(!mapped.insert(sub.value).second)return false; // Including one optional default.
                if(sub.value!=-1 && std::find(group->values.begin(),group->values.end(),sub.value)==group->values.end())return false;
            }
        }
        for(const auto& sub:t.subTracks) if(!clipsValid(sub.clips)) return false;
        for(const auto& send:t.sends) if(!typed(send.bus,Kind::Bus)) return false;
    }
    for(const auto& p:g.playlists) {
        if(!define(p.index,Kind::Playlist)) return false;
        std::unordered_set<int> visiting,done;
        std::function<bool(int,int)> visit=[&](int id,int depth) {
            if(depth>32 || visiting.count(id)) return false;
            if(done.count(id)) return true;
            const auto* item=find(p.items,id); if(!item) return false;
            if(item->target!=-1) return playable(item->target) && item->children.empty();
            if(item->children.empty()) return false;
            visiting.insert(id); for(int child:item->children) if(!visit(child,depth+1)) return false;
            visiting.erase(id); done.insert(id); return true;
        };
        if(!visit(p.root,0)) return false;
    }
    auto validCondition=[&](const Condition& c) { const auto* group=find(g.groups,c.group); return group && std::find(group->values.begin(),group->values.end(),c.value)!=group->values.end(); };
    for(const auto& s:g.switches) {
        std::size_t conditionCount=0; for(const auto& path:s.paths) conditionCount+=path.conditions.size();
        if(!define(s.index,Kind::Switch) || !optionalPlayable(s.fallback) || s.groups.empty() ||
            s.groups.size()>64 || s.paths.size()>1024 || conditionCount>4096) return false;
        std::unordered_set<int> groups(s.groups.begin(),s.groups.end());
        if(groups.size()!=s.groups.size()) return false;
        for(int group:s.groups) if(!typed(group,Kind::SyncGroup)) return false;
        for(const auto& path:s.paths) {
            if(!optionalPlayable(path.target)) return false;
            std::unordered_set<int> conditions;
            for(const auto& c:path.conditions) if(!groups.count(c.group) || !conditions.insert(c.group).second || !validCondition(c)) return false;
        }
        for(std::size_t i=0;i<s.paths.size();++i) for(std::size_t j=i+1;j<s.paths.size();++j) {
            const auto& a=s.paths[i]; const auto& b=s.paths[j];
            if(a.priority!=b.priority || a.conditions.size()!=b.conditions.size()) continue;
            bool overlap=true;
            for(const auto& left:a.conditions) for(const auto& right:b.conditions)
                if(left.group==right.group && left.value!=right.value) overlap=false;
            if(overlap) return false;
        }
    }
    for(const auto& s:g.groups) if(!define(s.index,Kind::SyncGroup) || s.values.empty() || std::find(s.values.begin(),s.values.end(),s.defaultValue)==s.values.end()) return false;
    for(const auto& p:g.parameters) if(!define(p.index,Kind::Parameter) || !std::isfinite(p.min) || !std::isfinite(p.max) ||
        !std::isfinite(p.defaultValue) || p.min>=p.max || p.defaultValue<p.min || p.defaultValue>p.max || p.attack<0 || p.release<0) return false;
    auto validAction=[&](const Action& a) {
        if(!std::isfinite(a.number))return false;
        switch(a.kind) {
            case ActionKind::Play: return playable(a.target);
            case ActionKind::Stop: return optionalPlayable(a.target) && a.number>=0;
            case ActionKind::SetSync: return validCondition({a.target,a.value});
            case ActionKind::SetParameter: { const auto* p=find(g.parameters,a.target); return p && a.number>=p->min && a.number<=p->max; }
            case ActionKind::Trigger: return typed(a.target,Kind::Trigger);
            case ActionKind::Event: return g.version>=protocol::PlaybackExtensionVersion && typed(a.target,Kind::Event);
            case ActionKind::LayerGain:return g.version>=protocol::LayerControlVersion && typed(a.target,Kind::Track) && a.number>=-96 && a.number<=24;
            case ActionKind::Pause:case ActionKind::Resume:return true;
            default:return false;
        }
    };
    for(const auto& e:g.events) { if(!define(e.index,Kind::Event)) return false; for(const auto& a:e.actions) if(!validAction(a)) return false; }
    for(const auto& t:g.transitions) {
        if(!define(t.index,Kind::Transition) || (t.bridge!=-1 && !typed(t.bridge,Kind::Segment))) return false;
        if(t.bridge!=-1) { const auto* bridge=find(g.segments,t.bridge); if(!bridge || (bridge->hasLoop && bridge->loop.count<0)) return false; }
        if(t.entry==EntryMode::PlaylistJump && t.jumpItem==-1) return false;
        if(t.jumpItem!=-1) {
            bool found=false; for(const auto& p:g.playlists) found|=find(p.items,t.jumpItem)!=nullptr;
            if(!found) return false;
        }
        for(int id:t.sources) if(!g.node(id)) return false;
        for(int id:t.destinations) if(!g.node(id)) return false;
        if(t.entry==EntryMode::PlaylistLast || t.entry==EntryMode::PlaylistNext || t.entry==EntryMode::PlaylistJump) {
            bool candidate=t.destinations.empty(),onlyPlaylists=!t.destinations.empty(),ownsJump=false;
            for(const auto id:t.destinations) {
                const auto kind=g.node(id)->kind;
                candidate|=kind==Kind::Playlist || kind==Kind::Switch || kind==Kind::Folder;
                onlyPlaylists&=kind==Kind::Playlist;
                if(const auto* playlist=find(g.playlists,id)) ownsJump|=find(playlist->items,t.jumpItem)!=nullptr;
            }
            if(!candidate || (t.entry==EntryMode::PlaylistJump && onlyPlaylists && !ownsJump)) return false;
        }
        for(const auto& c:t.conditions) if(!validCondition(c)) return false;
    }
    for(const auto& r:g.curves) {
        const auto* d=propertyDescriptor(r.property); const auto* target=g.node(r.target); const auto* parameter=find(g.parameters,r.parameter);
        if(!define(r.index,Kind::Rtpc) || !parameter || !d || !target || !d->supports(target->kind,r.policy) || r.points.size()<2) return false;
        for(std::size_t n=0;n<r.points.size();++n) {
            const auto& point=r.points[n];
            if(!std::isfinite(point.x) || !std::isfinite(point.y) || point.x<parameter->min || point.x>parameter->max ||
                point.y<d->min || point.y>d->max || (n>0 && point.x<=r.points[n-1].x)) return false;
        }
        for(const auto& other:g.curves) if(other.index!=r.index && other.target==r.target && other.property==r.property &&
            (other.policy!=r.policy || r.policy==Modifier::Absolute)) return false;
    }
    for(const auto& s:g.stingers) if(!define(s.index,Kind::Stinger) || !typed(s.trigger,Kind::Trigger) || !typed(s.segment,Kind::Segment)) return false;
    for(std::size_t i=0;i<g.buses.size();++i) {
        const auto& b=g.buses[i]; if(!define(b.index,Kind::Bus)) return false;
        for(const auto& effect:b.effects) if(!validEffectParameters(effect)) return false;
        auto later=[&](int target) { if(target==-1) return true; for(std::size_t n=i+1;n<g.buses.size();++n) if(g.buses[n].index==target) return true; return false; };
        if(!later(b.output)) return false;
        for(const auto& send:b.sends) if(!later(send.bus)) return false;
    }
    if(!validEffectCurves(g))return false;
    for(const auto& curve:g.effectCurves)if(!define(curve.index,Kind::Rtpc))return false;
    for(const auto& n:g.nodes) if(n.kind!=Kind::Folder && n.kind!=Kind::Trigger && n.kind!=Kind::Media && !defined[n.index]) return false;
    std::array<unsigned char,4096> color{};
    std::function<bool(int,int)> cycle=[&](int id,int depth) {
        if(depth>32 || color[id]==1) return false;
        if(color[id]==2) return true;
        color[id]=1;
        if(const auto* p=find(g.playlists,id)) for(const auto& item:p->items) if(item.target!=-1 && !cycle(item.target,depth+1)) return false;
        if(const auto* s=find(g.switches,id)) { if(s->fallback!=-1 && !cycle(s->fallback,depth+1)) return false; for(const auto& path:s->paths) if(path.target!=-1 && !cycle(path.target,depth+1)) return false; }
        color[id]=2; return true;
    };
    for(const auto& n:g.nodes) if(playable(n.index) && !cycle(n.index,0)) return false;
    for(const auto& e:g.entries) { if(!playable(e.start)) return false; for(const auto& a:e.actions) if(!validAction(a)) return false; }
    for(const auto& p:g.performances) {
        if(!playable(p.start) || (p.endFrame!=-1 && p.endFrame<=0))return false;
        if((p.endFade || p.ending!=-1) && g.version<protocol::PlaybackExtensionVersion)return false;
        if(p.ending!=-1 && (p.end!=EndBehavior::Ending || p.endFrame<0 || !playable(p.ending)))return false;
        if(p.endFade) {
            const auto f=*p.endFade;
            if(p.end!=EndBehavior::Fade || p.endFrame<0 || f.duration.value<=0 || !protocol::enumFromWire<Shape>(static_cast<int>(f.shape)))return false;
            if(f.offset.value>0 && p.endFrame>std::numeric_limits<Frame>::max()-f.offset.value)return false;
            const auto start=p.endFrame+f.offset.value;
            if(start>std::numeric_limits<Frame>::max()-f.duration.value)return false;
        }
        Frame previous=-1;for(const auto& step:p.steps) {if(step.frame<previous || (p.endFrame!=-1 && step.frame>p.endFrame) || !validAction(step.action))return false;previous=step.frame;}
    }
    if(g.hasMusicalLoop && (!optionalPlayable(g.musicalLoop.intro) || !playable(g.musicalLoop.loop) || !optionalPlayable(g.musicalLoop.ending))) return false;
    if(g.hasMusicalLoop && g.version>=protocol::MusicalLoopVersion) {
        const auto& m=g.musicalLoop;const auto* p=find(g.playlists,m.playlist);
        if(!p || !p->reset || m.repeats<1 || m.repeats>1000000 || (m.stop==EndBehavior::Ending && m.ending==-1))return false;
        const auto* root=find(p->items,p->root);const auto count=1+(m.intro>=0)+(m.ending>=0);
        if(!root || root->target!=-1 || root->loops!=1 || root->mode!=PlaylistMode::SequenceContinuous ||
            root->children.size()!=static_cast<std::size_t>(count) || p->items.size()!=static_cast<std::size_t>(count+1))return false;
        std::size_t at=0;
        for(const int target:{m.intro,m.loop,m.ending})if(target>=0) {
            const auto* item=find(p->items,root->children[at++]);
            const int repetitions=at==static_cast<std::size_t>(1+(m.intro>=0)) ? m.repeats : 1;
            if(!item || item->target!=target || item->loops!=repetitions || !item->children.empty())return false;
        }
    } else if(g.hasMusicalLoop && g.musicalLoop.playlist!=-1)return false;
    if(g.hasAutomatic) {
        const auto* parameter=find(g.parameters,g.automatic.parameter);
        const auto& a=g.automatic;
        if(!parameter || !std::isfinite(a.low) || !std::isfinite(a.high) || !std::isfinite(a.cycleBeats) || a.low>a.high ||
            a.cycleBeats*static_cast<long double>(g.sampleRate)*60/400*4/32<1 || a.low<parameter->min || a.high>parameter->max)return false;
    }
    if(!g.albumFlows.empty() && g.version<protocol::AlbumFlowVersion)return false;
    std::array<bool,4096> flowSources{};
    for(const auto& flow:g.albumFlows) {
        if(!playable(flow.from) || !playable(flow.to) || flowSources[flow.from] || flow.duration.value<0 ||
            flow.duration.value>static_cast<Frame>(g.sampleRate)*3600 || !protocol::enumFromWire<AlbumFlowMode>(static_cast<int>(flow.mode)))return false;
        flowSources[flow.from]=true;
        if(flow.bridge!=-1) {
            const auto* bridge=find(g.segments,flow.bridge);
            if(!bridge || flow.mode!=AlbumFlowMode::Bridge || (bridge->hasLoop && bridge->loop.count<0))return false;
        } else if(flow.mode==AlbumFlowMode::Bridge)return false;
    }
    return true;
}
}
