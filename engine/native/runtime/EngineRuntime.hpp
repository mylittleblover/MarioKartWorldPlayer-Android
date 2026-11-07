#pragma once
#include "api/AlbumFlowMonitorTypes.hpp"
#include "api/EngineTypes.hpp"
#include "api/PlaylistMonitorTypes.hpp"
#include "mixer/BusMixer.hpp"
#include "music/Selection.hpp"
#include "music/PlaylistSession.hpp"
#include "music/SelectionPreview.hpp"
#include "scheduler/TransitionPlan.hpp"
#include "scheduler/CueLookAhead.hpp"
#include "runtime/Parameters.hpp"
#include "scheduler/CommandQueue.hpp"
#include "scheduler/CommandJournal.hpp"
#include "scheduler/ActionDecisionJournal.hpp"
#include "audio/streaming/StreamHistory.hpp"
#include "audio/streaming/PreparationHistory.hpp"
#include "audio/streaming/PreparationSet.hpp"
#include <memory>
namespace player2 {
// One execution runtime for prepared frames, voices and transport. Worker-only preparation;
// exactly one command producer and one audio consumer. No Authoring JSON enters this class.
class EngineRuntime {
    struct Scene {
        bool active{}; const Segment* segment{}; int instance{},loopCount{};
        long double position{},musicalPosition{}; std::array<bool,4096> clips{},cues{};
        std::array<int,4096> selections{};
        PlaylistSession playlist;
        int object{-1},playlistObject{-1},switchObject{-1},switchTarget{-1},owner{-1};
        SwitchChoice switchChoice;
        std::bitset<4096> switchOwners;
        std::uint64_t switchRevision{};
        std::array<int,4096> switchValues{};
        PlaylistSession::SwitchBranch playlistSwitch;std::array<int,4096> playlistSwitchValues{};std::uint64_t playlistSwitchRevision{};
        bool segmentEnded{};
        bool preentry{},controlled{true},disableLoop{},reserved{};
        bool entrance{},departure{};
        Frame entranceStart{},entranceEnd{},departureStart{},departureEnd{},stopFrame{-1};
        Shape entranceShape{},departureShape{};
    };
    struct TransitionRun {
        bool active{},cueSearch{},forecast{},sourceLeft{},bridgeStarted{},destinationStarted{},destinationCommitted{},switchChange{},playlistSwitchChange{},naturalSource{},albumEdge{};
        const Transition* rule{}; Scene* source{}; Scene* bridge{}; Scene* destination{};
        int target{-1},segment{-1},sourceSegment{-1},sourceSwitchTarget{-1},candidates{},specificity{},priority{};
        CueForecast forecastPlan;
        Frame requested{},sourceSync{},destinationEntry{},preStart{},bridgeStart{},position{},destinationLead{};
        std::uint64_t revision{},preparationGeneration{};bool preparationDecided{};
        long double sourceBoundary{}; double sourceSpeed{1},destinationSpeed{1},bridgeSpeed{1};
        int sourceInstance{}; bool sourceLoopDisabled{};
        PlaylistSession playlist;
        int playlistObject{-1},switchObject{-1},switchTarget{-1};
        PlaylistSession::SwitchBranch playlistSwitch;std::array<int,4096> playlistSwitchValues{};std::uint64_t playlistSwitchRevision{};
        SwitchChoice switchChoice; std::bitset<4096> switchOwners;
        std::array<int,4096> switchValues{}; std::uint64_t switchRevision{};
    };
    struct StingerRun {
        bool active{},started{},entered{}; const Stinger* rule{}; Scene* scene{};
        Frame scheduled{},preparation{},cooldown{};
        std::uint64_t preparationGeneration{};bool preparationDecided{};
        Scene* source{};int sourceInstance{};long double sourceBoundary{};double sourceSpeed{1},destinationSpeed{1};
    };
    StingerRun stinger_;
    std::array<Frame,4096> stingerCooldown_{};
    std::array<std::int64_t,14> stingerDecision_{0,-1,-1,-1,0,0,0,0,0,-1,0,-1,-1,-1};
    struct PlaylistArchive { int index{-1}; PlaylistSession session; };
    std::unique_ptr<Graph> graph_;
    std::unique_ptr<Parameters> parameters_;
    std::unique_ptr<BusMixer> mixer_;
    std::array<std::unique_ptr<AudioBuffer>,4096> assets_{};
    std::unique_ptr<AudioBuffer> linear_;
    bool linearLoop_{};std::optional<audio::SourceLoopMetadata> linearRepeatRange_;
    std::array<Scene,16> scenes_{};
    std::array<SelectionState,4096> selections_{};
    std::array<const PlaylistItem*,4096> playlistGroups_{};
    std::array<int,4096> playlistGroupOwners_{};
    std::unique_ptr<SelectionPreview> preview_;
    std::unique_ptr<SelectionPreview> sourcePreview_;
    std::vector<PlaylistArchive> playlistArchives_;
    std::array<Frame,4096> lastPositions_{};
    Scene* foreground_{};
    TransitionRun transition_;
    int queuedTransition_{-1}; bool queuedSwitch_{},cueSearchReplan_{},cueSearchEnabled_{};
    std::array<std::vector<const Track*>,4096> tracksBySegment_;
    std::array<const Track*,4096> tracksByIndex_{};
    // Cache lifetime exceeds all Voice page leases. Created only for streamed bindings.
    std::unique_ptr<audio::SourcePageCache> streamCache_;
    std::unique_ptr<StreamHistory> streamHistory_;
    std::unique_ptr<PreparationHistory> preparationHistory_;
    std::unique_ptr<audio::PreparationSet> transitionPreparation_;
    std::unique_ptr<audio::PreparationSet> stingerPreparation_;
    std::unique_ptr<audio::PreparationSet> futureClipPreparation_;
    std::array<audio::PreparationSet::Requirement,128> futureClipRequirements_{};
    std::size_t futureClipCount_{};bool futureClipOverflow_{},futureClipReady_{true};
    std::uint64_t futureClipResourceLimits_{};bool futureClipLimited_{};
    std::uint64_t preparationGeneration_{};
    VoiceManager voices_;
    SampleClock clock_;
    SpscQueue<api::EngineCommand> queue_;
    std::unique_ptr<CommandJournal> journal_;
    std::unique_ptr<ActionDecisionJournal> actionDecisions_;
    api::EngineStatus entryReplayFailure_{api::EngineStatus::Ok};
    std::array<std::int64_t,11> eventDecision_{-1,0,-1,-1,-1,0,0,-1,-1,0,0};
    int cueContext_{-1},cueSegment_{-1};
    // Shared by Event, posted Event, Entry and Performance actions. Paused
    // callbacks at the same Transport frame cannot replenish the allowance.
    static constexpr std::size_t MaxActionsPerFrame=4096;
    Frame actionWorkFrame_{-1};std::uint64_t actionWorkEpoch_{};
    std::size_t actionWorkRemaining_{};
    bool consumeActionWork() noexcept;
    bool rebuilding_{}; std::uint64_t seekEpoch_{}; Frame seekFrame_{};
    std::array<api::EngineCommand,512> pending_{};
    std::size_t pendingCount_{},preparedBytes_{};
    api::EntryStateSnapshot entryState_;
    api::PerformanceSnapshot performanceState_;
    const Performance* performance_{};
    std::size_t performanceStep_{};
    const Automatic* automatic_{};
    api::AutomaticSnapshot automaticState_;
    SelectionState automaticRandom_;
    bool musicalLoopActive_{};api::MusicalLoopSnapshot musicalLoopState_;
    bool albumActive_{},albumEnding_{},albumWaitingTail_{},albumInternalRequest_{};int albumCurrent_{-1},albumOwner_{-1};
    std::array<const AlbumFlow*,4096> albumFlowsByFrom_{};
    Transition albumRule_;api::AlbumFlowSnapshot albumState_;
    api::EngineStatus startAlbumFlow(int target) noexcept;
    api::EngineStatus nextAlbumFlow(bool automatic=false) noexcept;
    int albumProbeInstance_{-1};std::uint64_t albumProbeRevision_{};bool albumTerminal_{};
    void updateAlbumCurrent(bool committedEdge=false) noexcept;
    void advanceAlbumFlow() noexcept;
    void completeAlbumFlow() noexcept;
    long double automaticBeats_{};Frame automaticFrame_{-1},automaticClockFrame_{-1};Tempo automaticTempo_;double automaticSpeed_{1};
    int rate_{48000},current_{-1}; std::uint32_t instance_{};
    std::uint64_t rejected_{},voiceNumericalCorrections_{};
    api::EngineStatus last_{api::EngineStatus::Ok};
    float peakLeft_{},peakRight_{};
    void execute(const api::EngineCommand& command,int depth=0) noexcept;
    void action(const Action& action,int depth) noexcept;
    api::EngineStatus applyEntryState(int index,int depth) noexcept;
    api::EngineStatus startPerformance(int index) noexcept;
    void advancePerformance() noexcept;
    double performanceEnvelope()const noexcept;
    void cancelPerformance() noexcept;
    void finishPerformancePlayback() noexcept;
    api::EngineStatus startAutomatic(int parameter)noexcept;
    void advanceAutomatic()noexcept;
    void advanceAutomaticClock(const Scene& scene)noexcept;
    void stopAutomatic(bool restore,api::AutomaticPhase phase=api::AutomaticPhase::Stopped)noexcept;
    double automaticGoal()noexcept;
    api::EngineStatus startMusicalLoop(int playlist)noexcept;
    api::EngineStatus stopMusicalLoop(int playlist,double fadeBeats)noexcept;
    void advanceMusicalLoop()noexcept;
    api::EngineStatus startSegment(int target) noexcept;
    TransitionChoice transitionChoice(const Scene& source,int target,const TransitionRun& destination,const std::bitset<4096>* sourceSwitchOwners=nullptr) const noexcept;
    api::EngineStatus requestPlay(int target) noexcept;
    api::EngineStatus planTransition(Scene& source,int target,const TransitionChoice& choice,bool switchChange,const PlaylistSession::SwitchBranch* leaf=nullptr,bool naturalSource=false,Frame destinationLead=0) noexcept;
    void tickTransition() noexcept;
    void collectFutureClip(const Scene& scene,const Track& track,const Clip& clip) noexcept;
    void prepareFutureClips() noexcept;
    bool collectPreparationPages(int index,Frame position,std::array<audio::PreparationSet::Requirement,128>& required,std::size_t& count) noexcept;
    api::EngineStatus prepareStingerPages(const StingerRun& run) noexcept;
    bool stingerPagesReady() noexcept;
    bool retimeWaitingStinger() noexcept;
    api::EngineStatus recordPreparationDecision(std::uint64_t generation,api::EngineStatus result,bool needsHistory,bool& historical) noexcept;
    api::EngineStatus prepareTransitionPages(const TransitionRun& run) noexcept;
    bool transitionPagesReady() noexcept;
    bool retimeWaitingTransition() noexcept;
    Frame projectSameTime(const TransitionRun& run,Frame now,double speed,Frame sync) const noexcept;
    void cancelTransition(bool retainVoices=false) noexcept;
    void musicOwners(const Scene& scene,std::bitset<4096>& owners) const noexcept;
    api::EngineStatus stopObject(int target,double fadeBeats,bool retainTail) noexcept;
    void departScene(Scene& scene,const Transition& rule,Frame at,bool naturalSource=false) noexcept;
    CueForecast forecastSourceCue(const Scene& source,const Transition& rule,bool includeCurrent=false) noexcept;
    void departForecastScene(TransitionRun& run) noexcept;
    bool refreshCueForecast() noexcept;
    bool yieldCueRequest(Scene& source) noexcept;
    void resumeCueRequest() noexcept;
    void replanCueTransition(bool entered) noexcept;
    api::EngineStatus prepareDestination(Scene& source,int target,const Transition& rule,TransitionRun& run,bool allowSilent=false) noexcept;
    void archivePlaylist(const Scene& scene) noexcept;
    api::EngineStatus fireTrigger(int trigger) noexcept;
    void tickStinger() noexcept;
    void finishStinger() noexcept;
    void clearStinger() noexcept;
    api::EngineStatus enterSegment(Scene& scene,int target) noexcept;
    api::EngineStatus enterTarget(Scene& scene,int target) noexcept;
    int resolveSceneSwitch(Scene& scene,bool& restart) noexcept;
    api::EngineStatus targetReady(int target,std::array<bool,4096>& visited,int depth=0) const noexcept;
    void updateSwitch(Scene& scene) noexcept;
    void updateRootSwitch(Scene& scene) noexcept;
    void updatePlaylistSwitch(Scene& scene) noexcept;
    api::EngineStatus preparePlaylistSwitch(Scene& source,const PlaylistSession::SwitchBranch& leaf,const Transition& rule,TransitionRun& run) noexcept;
    bool scheduleCues(Scene& scene) noexcept;
    void schedule(Scene& scene) noexcept;
    void clip(Scene& scene,const Track& track,const Clip& clip) noexcept;
    void drain() noexcept;
    void resetStopped() noexcept;
    void updateMixerProperties() noexcept;
public:
    api::AlbumFlowSnapshot albumFlowSnapshot() const noexcept {
        auto s=albumState_;s.current=albumCurrent_;s.active=albumActive_;s.paused=s.active && clock_.status()==TransportStatus::Paused;s.at={clock_.frame()};return s;
    }
    api::MusicalLoopSnapshot musicalLoopSnapshot()const noexcept {
        auto s=musicalLoopState_;s.at={clock_.frame()};s.active=musicalLoopActive_;s.paused=s.active && clock_.status()==TransportStatus::Paused;
        s.segment=foreground_ && foreground_->segment ? foreground_->segment->index : -1;
        s.item=foreground_ ? foreground_->playlist.item() : -1;return s;
    }
    void enableCueSearch() noexcept { cueSearchEnabled_=true; }
    explicit EngineRuntime(std::unique_ptr<Graph> graph);
    explicit EngineRuntime(int rate);
    int sampleRate() const noexcept { return rate_; }
    const Graph* graph() const noexcept { return graph_.get(); }
    // Control-only rebinding requires every execution owner and page reservation
    // to be idle, including silent timelines and not-yet-started plans.
    bool canBindSource() const noexcept;
    api::EngineStatus bindAsset(std::unique_ptr<AudioBuffer> buffer);
    api::EngineStatus setLinearModuleChannels(std::uint64_t mute,std::uint64_t solo)noexcept;
    api::EngineStatus setLinearStreamGroups(std::uint32_t mute,std::uint32_t solo,const std::array<std::uint8_t,32>* expectedRoot=nullptr)noexcept;
    api::EngineStatus setLinearSourceControls(std::uint64_t mute,std::uint64_t solo,bool groups)noexcept;
    api::EngineStatus setLinearRepeat(bool enabled,std::optional<audio::SourceLoopMetadata> range)noexcept;
    void bindLinear(std::unique_ptr<AudioBuffer> buffer,bool loop) noexcept;
    api::EngineStatus bindStreamedSource(int index,std::unique_ptr<audio::AudioFrameSource> source,std::size_t encodedBytes,bool linear=false,bool loop=false,const std::array<std::uint8_t,32>* digest=nullptr,bool verified=false,const api::BoundSourceInfo* selection=nullptr);
    api::PerformanceSnapshot performanceSnapshot()const noexcept {auto value=performanceState_;value.at={clock_.frame()};value.active=performance_!=nullptr;value.paused=value.active && clock_.status()==TransportStatus::Paused;return value;}
    api::LayerGainSnapshot layerSnapshot(int index)const noexcept {auto value=parameters_ ? parameters_->layerSnapshot(index) : api::LayerGainSnapshot{};value.at={clock_.frame()};return value;}
    int syncValue(int index)const noexcept {return parameters_ && index>=0 && index<4096 ? parameters_->values()[index] : -1;}
    api::AutomaticSnapshot automaticSnapshot()const noexcept {
        auto state=automaticState_;state.at={clock_.frame()};state.active=automatic_!=nullptr;state.paused=state.active && clock_.status()==TransportStatus::Paused;
        const auto p=parameterSnapshot(state.parameter);state.current=p.current;state.target=p.target;state.random=automaticRandom_.random;return state;
    }
    api::EntryStateSnapshot entryStateSnapshot()const noexcept {auto value=entryState_;value.at={clock_.frame()};return value;}
    api::StreamingSnapshot streamingSnapshot() const noexcept;
    api::SourceVoiceSnapshot sourceSnapshot(int slot) const noexcept;
    void renderControl(float* stereo,std::size_t frames) noexcept;
    api::EngineStatus pauseDecodeWorkerHarness(bool pause) noexcept;
    bool submit(const api::EngineCommand& command) noexcept;
    void render(float* stereo,std::size_t frames) noexcept;
    api::EngineSnapshot snapshot() const noexcept;
    api::PlaylistGroupSnapshot playlistGroupSnapshot(int index,bool includeCandidates=true) const noexcept;
    std::bitset<4096> activePlaylistGroups() const noexcept;
    std::uint64_t selectionRevision(int index) const noexcept { return selections_[index].revision; }
    bool playlistGroupActive(int index) const noexcept;
    api::BusSnapshot busSnapshot(int index) const noexcept {
        auto value=mixer_ ? mixer_->snapshot(index) : api::BusSnapshot{}; value.at={clock_.frame()}; return value;
    }
    api::EffectSnapshot effectSnapshot(int index) const noexcept {
        auto s=mixer_ ? mixer_->effectSnapshot(index) : api::EffectSnapshot{};s.at={clock_.frame()};return s;
    }
    api::GameParameterSnapshot parameterSnapshot(int index) const noexcept {
        auto value=parameters_ ? parameters_->snapshot(index) : api::GameParameterSnapshot{};
        value.at={clock_.frame()}; return value;
    }
    // Requires output stopped; replay uses this same renderer to reconstruct state.
    api::EngineStatus seek(audio::TransportFrame frame,std::uint64_t maximumReplayFrames=48000*60*10) noexcept;
};
}
