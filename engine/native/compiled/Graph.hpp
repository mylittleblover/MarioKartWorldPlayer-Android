#pragma once
#include "compiled/MusicGraph.hpp"
#include "compiled/ControlGraph.hpp"
#include "compiled/MixerGraph.hpp"
#include "compiled/ExtensionGraph.hpp"
#include "compiled/SourceGraph.hpp"

// Aggregate of compiled execution DTOs only; no authoring or runtime behavior.
namespace player2 {
struct Graph {
    Graph()=default;
    Graph(const Graph&)=delete;
    Graph& operator=(const Graph&)=delete;
    Graph(Graph&&)=default;
    Graph& operator=(Graph&&)=default;
    int version{1},sampleRate{48000}; std::vector<std::string> identities; std::vector<Node> nodes;
    std::vector<Segment> segments; std::vector<Track> tracks; std::vector<Playlist> playlists; std::vector<Switch> switches;
    std::vector<SyncGroup> groups; std::vector<Parameter> parameters; std::vector<Event> events; std::vector<Transition> transitions;
    std::vector<Rtpc> curves; std::vector<Stinger> stingers; std::vector<Bus> buses; std::vector<Asset> assets;
    std::vector<Entry> entries; std::vector<Performance> performances;
    std::vector<EffectRtpc> effectCurves;
    std::vector<AlbumFlow> albumFlows;
    bool hasMusicalLoop{},hasAutomatic{}; MusicalLoop musicalLoop; Automatic automatic;
    std::array<const Node*,4096> byIndex{};
    void indexNodes() noexcept { byIndex.fill(nullptr); for(const auto& node:nodes) if(node.index>=0 && node.index<4096) byIndex[node.index]=&node; }
    const Node* node(int index) const noexcept { return index>=0 && index<4096 ? byIndex[index] : nullptr; }
};
}
