#pragma once
#include "api/EngineApi.hpp"
#include "api/PlaylistMonitorPublication.hpp"
#include "api/AutomaticMonitorPublication.hpp"
#include "api/MusicalLoopMonitorPublication.hpp"
#include "music/GraphReader.hpp"
#include "runtime/EngineRuntime.hpp"
#include "runtime/DeviceRateAdapter.hpp"
#include "audio/source/SourceHandle.hpp"
#include "audio/source/PrepareBuffer.hpp"
#include "audio/source/SourceDigest.hpp"
#include "audio/source/BoundSourceInfo.hpp"
#include <atomic>
#include <memory>
#include <cstring>
#include <new>
#include "api/EffectMonitorPublication.hpp"
#include "api/AlbumFlowMonitorPublication.hpp"
namespace player2::api {
// Engine-owned private facade publication state, never a Platform dependency.
class EngineHandle {
public:
    std::unique_ptr<EngineRuntime> runtime;
    std::atomic<bool> cueSearchV9{false};
    DeviceRateAdapter device;
    std::atomic<bool> outputActive{false};
    // Atomic payload as well as sequence avoids C++ data races in the monitor reader.
    std::atomic<std::uint32_t> sequence{0};
    std::atomic<Frame> frame{0};
    std::array<std::atomic<std::uint32_t>,2> transportEpoch{};
    std::atomic<int> status{0},voices{0},current{-1},result{0};
    std::atomic<std::uint64_t> dropped{0},rejected{0};
    std::array<std::atomic<std::uint32_t>,4> numerical{};
    std::atomic<float> left{0},right{0};
    std::atomic<int> playlist{-1},segment{-1},group{-1},item{-1},cursor{-1},history{0};
    std::atomic<std::uint64_t> randomState{0};
    std::atomic<int> switchContainer{-1},switchTarget{-1},switchPath{-1},switchSpecificity{-1},switchPriority{0},switchCandidates{0},switchReason{0};
    std::array<std::atomic<std::int64_t>,14> transition{};
    std::array<std::atomic<std::int64_t>,14> stinger{};
    std::array<std::atomic<std::int64_t>,11> event{};
    struct ParameterSlot {
        std::atomic<bool> valid{false};
        // Split IEEE values into lock-free 32-bit words; sequence covers the pair.
        std::array<std::atomic<std::uint32_t>,10> words{};
        void store(std::size_t index,double value) noexcept {
            std::uint64_t bits; std::memcpy(&bits,&value,sizeof(bits));
            words[index*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);
            words[index*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);
        }
        double load(std::size_t index) const noexcept {
            const auto bits=std::uint64_t(words[index*2].load(std::memory_order_relaxed)) |
                (std::uint64_t(words[index*2+1].load(std::memory_order_relaxed))<<32);
            double value; std::memcpy(&value,&bits,sizeof(value)); return value;
        }
        std::atomic<Frame> attack{0},release{0};
    };
    static_assert(std::atomic<std::uint32_t>::is_always_lock_free,"Parameter publication must be lock-free");
    std::array<ParameterSlot,4096> parameters;
    std::array<std::atomic<int>,4096> syncValues;
    struct BusSlot {
        std::atomic<bool> valid{false},auxiliary{false},bypass{false},lowEnabled{false},highEnabled{false},wetConfigured{false};
        std::atomic<int> output{-1},tailEffects{0}; std::atomic<float> left{0},right{0};
        std::array<std::atomic<std::uint32_t>,8> words{};
        void store(std::size_t index,double value) noexcept {
            std::uint64_t bits; std::memcpy(&bits,&value,sizeof(bits));
            words[index*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);
            words[index*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);
        }
        double load(std::size_t index) const noexcept {
            const auto bits=std::uint64_t(words[index*2].load(std::memory_order_relaxed)) | (std::uint64_t(words[index*2+1].load(std::memory_order_relaxed))<<32);
            double value; std::memcpy(&value,&bits,sizeof(value)); return value;
        }
    };
    std::array<BusSlot,4096> buses;
    struct LayerSlot {
        std::atomic<bool> valid{false};std::array<std::atomic<std::uint32_t>,8> words{};
        void store(std::size_t n,double value)noexcept {std::uint64_t bits;std::memcpy(&bits,&value,8);words[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);words[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
        double load(std::size_t n)const noexcept {const auto bits=std::uint64_t(words[n*2].load(std::memory_order_relaxed))|(std::uint64_t(words[n*2+1].load(std::memory_order_relaxed))<<32);double value;std::memcpy(&value,&bits,8);return value;}
    };
    std::array<LayerSlot,4096> layers;
    std::array<PlaylistMonitorSlot,4096> playlistGroups;
    std::array<std::atomic<std::uint32_t>,42> streaming{};
    std::array<std::atomic<std::uint32_t>,16> entryState{};
    std::array<std::atomic<std::uint32_t>,26> performance{};
    AutomaticMonitorSlot automatic;
    MusicalLoopMonitorSlot musicalLoop;
    struct SourceSlot {
        std::array<std::atomic<std::uint32_t>,std::tuple_size<SourceMonitorPacket>::value*2> words{};
        void store(const SourceMonitorPacket& packet) noexcept {
            if(!packet[1]){words[2].store(0,std::memory_order_relaxed);return;}
            for(std::size_t n=0;n<packet.size();++n) {
                std::uint64_t bits;std::memcpy(&bits,&packet[n],sizeof(bits));
                words[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);
                words[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);
            }
        }
        SourceMonitorPacket load() const noexcept {
            if(!words[2].load(std::memory_order_relaxed))return sourceMonitorPacket({});
            SourceMonitorPacket packet{};
            for(std::size_t n=0;n<packet.size();++n) {
                const std::uint64_t bits=std::uint64_t(words[n*2].load(std::memory_order_relaxed)) | (std::uint64_t(words[n*2+1].load(std::memory_order_relaxed))<<32);
                std::memcpy(&packet[n],&bits,sizeof(bits));
            }
            return packet;
        }
    };
    std::array<SourceSlot,128> sources;
    std::array<EffectMonitorSlot,4096> effects;
    AlbumFlowMonitorSlot album;
    bool graphSchemaDirty{true};
    EngineHandle() noexcept {
        for(auto& value:syncValues)value.store(-1,std::memory_order_relaxed);
        for(const auto index: {0,1,4,7,8,12}) {
            performance[index*2].store(UINT32_MAX,std::memory_order_relaxed);
            performance[index*2+1].store(UINT32_MAX,std::memory_order_relaxed);
        }
        for(const auto index: {0,1,3,6}) {
            entryState[index*2].store(UINT32_MAX,std::memory_order_relaxed);
            entryState[index*2+1].store(UINT32_MAX,std::memory_order_relaxed);
        }
        streaming[24].store(1,std::memory_order_relaxed);streaming[32].store(1,std::memory_order_relaxed);streaming[40].store(1,std::memory_order_relaxed);
        const auto emptyEvent=EngineSnapshot{}.event;
        for(std::size_t i=0;i<emptyEvent.size();++i) event[i].store(emptyEvent[i],std::memory_order_relaxed);
        const auto emptyStinger=EngineSnapshot{}.stinger;
        for(std::size_t i=0;i<emptyStinger.size();++i) stinger[i].store(emptyStinger[i],std::memory_order_relaxed);
        const auto empty=EngineSnapshot{}.transition;
        for(std::size_t i=0;i<empty.size();++i) transition[i].store(empty[i],std::memory_order_relaxed);
    }
    void publish(float peakLeft=-1,float peakRight=-1) noexcept {
        auto s=runtime ? runtime->snapshot() : EngineSnapshot{};
        if(peakLeft>=0) { s.peakLeft=peakLeft; s.peakRight=peakRight; }
        sequence.fetch_add(1,std::memory_order_acq_rel);
        frame.store(s.position.value,std::memory_order_relaxed); status.store(s.status,std::memory_order_relaxed);
        PlaylistMonitorSlot::storeBits(transportEpoch.data(),s.transportEpoch);
        voices.store(s.activeVoices,std::memory_order_relaxed); current.store(s.currentObject,std::memory_order_relaxed);
        result.store(static_cast<int>(s.lastResult),std::memory_order_relaxed);
        dropped.store(s.droppedVoices,std::memory_order_relaxed); rejected.store(s.rejectedCommands,std::memory_order_relaxed);
        const std::array<std::uint64_t,2> counts{s.voiceNumericalCorrections,s.mixerNumericalCorrections};
        for(std::size_t n=0;n<2;++n) { numerical[n*2].store(static_cast<std::uint32_t>(counts[n]),std::memory_order_relaxed);numerical[n*2+1].store(static_cast<std::uint32_t>(counts[n]>>32),std::memory_order_relaxed); }
        left.store(s.peakLeft,std::memory_order_relaxed); right.store(s.peakRight,std::memory_order_relaxed);
        playlist.store(s.playlist,std::memory_order_relaxed); segment.store(s.playingSegment,std::memory_order_relaxed);
        group.store(s.playlistGroup,std::memory_order_relaxed); item.store(s.playlistItem,std::memory_order_relaxed);
        cursor.store(s.playlistCursor,std::memory_order_relaxed); history.store(s.playlistHistorySize,std::memory_order_relaxed);
        randomState.store(s.playlistRandomState,std::memory_order_relaxed);
        switchContainer.store(s.switchContainer,std::memory_order_relaxed); switchTarget.store(s.switchTarget,std::memory_order_relaxed);
        switchPath.store(s.switchPath,std::memory_order_relaxed); switchSpecificity.store(s.switchSpecificity,std::memory_order_relaxed);
        switchPriority.store(s.switchPriority,std::memory_order_relaxed); switchCandidates.store(s.switchCandidates,std::memory_order_relaxed);
        switchReason.store(s.switchReason,std::memory_order_relaxed);
        for(std::size_t i=0;i<s.transition.size();++i) transition[i].store(s.transition[i],std::memory_order_relaxed);
        for(std::size_t i=0;i<s.stinger.size();++i) stinger[i].store(s.stinger[i],std::memory_order_relaxed);
        for(std::size_t i=0;i<s.event.size();++i) event[i].store(s.event[i],std::memory_order_relaxed);
        if(graphSchemaDirty) for(auto& parameter:parameters) parameter.valid.store(false,std::memory_order_relaxed);
        if(graphSchemaDirty)for(auto& value:syncValues)value.store(-1,std::memory_order_relaxed);
        if(runtime && runtime->graph())for(const auto& definition:runtime->graph()->groups)
            syncValues[definition.index].store(runtime->syncValue(definition.index),std::memory_order_relaxed);
        if(runtime && runtime->graph()) for(const auto& definition:runtime->graph()->parameters) {
            const auto value=runtime->parameterSnapshot(definition.index); auto& slot=parameters[definition.index];
            slot.store(0,value.current); slot.store(1,value.target);
            if(graphSchemaDirty) {
                slot.store(2,value.min); slot.store(3,value.max);
                slot.store(4,value.defaultValue);
                slot.attack.store(value.attackFrames,std::memory_order_relaxed); slot.release.store(value.releaseFrames,std::memory_order_relaxed);
                slot.valid.store(true,std::memory_order_relaxed);
            }
        }
        if(graphSchemaDirty) for(auto& bus:buses) bus.valid.store(false,std::memory_order_relaxed);
        if(runtime && runtime->graph()) for(const auto& definition:runtime->graph()->buses) {
            const auto value=runtime->busSnapshot(definition.index); auto& slot=buses[definition.index];
            slot.store(0,value.localGainDb);slot.store(1,value.lowPassHz);slot.store(2,value.highPassHz);slot.store(3,value.reverbWet);
            slot.lowEnabled.store(value.lowPassEnabled,std::memory_order_relaxed);slot.highEnabled.store(value.highPassEnabled,std::memory_order_relaxed);slot.wetConfigured.store(value.reverbWetConfigured,std::memory_order_relaxed); slot.left.store(value.peakLeft,std::memory_order_relaxed); slot.right.store(value.peakRight,std::memory_order_relaxed);
            slot.bypass.store(value.bypass,std::memory_order_relaxed); slot.tailEffects.store(value.tailEffects,std::memory_order_relaxed);
            if(graphSchemaDirty) { slot.output.store(value.output,std::memory_order_relaxed); slot.auxiliary.store(value.auxiliary,std::memory_order_relaxed); slot.valid.store(true,std::memory_order_relaxed); }
        }
        if(graphSchemaDirty) for(auto& slot:playlistGroups) slot.valid.store(false,std::memory_order_relaxed);
        const auto activeGroups=runtime ? runtime->activePlaylistGroups() : std::bitset<4096>{};
        if(runtime && runtime->graph()) for(const auto& definition:runtime->graph()->playlists) for(const auto& group:definition.items) if(group.target==-1) {
            auto& slot=playlistGroups[group.index]; const auto revision=runtime->selectionRevision(group.index);
            if(graphSchemaDirty || revision!=slot.revision)slot.store(runtime->playlistGroupSnapshot(group.index,graphSchemaDirty),revision,graphSchemaDirty);
            slot.active.store(activeGroups.test(group.index),std::memory_order_relaxed);
        }
        const auto entry=runtime ? runtime->entryStateSnapshot() : EntryStateSnapshot{};
        if(graphSchemaDirty)for(auto& layer:layers)layer.valid.store(false,std::memory_order_relaxed);
        if(runtime && runtime->graph())for(const auto& track:runtime->graph()->tracks){const auto s=runtime->layerSnapshot(track.index);auto& layer=layers[track.index];layer.store(0,s.trimDb);layer.store(1,s.baseDb);layer.store(2,s.rtpcDb);layer.store(3,s.finalDb);layer.valid.store(true,std::memory_order_relaxed);}
        const auto perf=runtime ? runtime->performanceSnapshot() : PerformanceSnapshot{};
        automatic.store(automaticPacket(runtime ? runtime->automaticSnapshot() : AutomaticSnapshot{}));
        musicalLoop.store(musicalLoopPacket(runtime ? runtime->musicalLoopSnapshot() : MusicalLoopSnapshot{}));
        const std::array<std::int64_t,13> performanceValues{perf.index,perf.start,perf.steps,perf.executedSteps,perf.lastStep,static_cast<int>(perf.phase),static_cast<int>(perf.result),perf.actionAt.value,perf.requestedAt.value,perf.at.value,perf.active,perf.paused,perf.endAt.value};
        for(std::size_t n=0;n<performanceValues.size();++n){std::uint64_t bits;std::memcpy(&bits,&performanceValues[n],8);performance[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);performance[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
        const std::array<std::int64_t,8> entryValues{entry.index,entry.start,entry.appliedActions,entry.failedAction,static_cast<int>(entry.decision),static_cast<int>(entry.result),entry.requestedAt.value,entry.at.value};
        for(std::size_t n=0;n<entryValues.size();++n){std::uint64_t bits;std::memcpy(&bits,&entryValues[n],8);entryState[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);entryState[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
        const auto source=runtime ? runtime->streamingSnapshot() : StreamingSnapshot{};
        const std::array<std::uint64_t,21> sourceValues{source.pcmBytes,source.encodedBytes,source.sources,source.cachedPages,source.underrunFrames,source.requests,source.droppedRequests,source.decodedPages,source.decodeFailures,static_cast<std::uint64_t>(source.lastFailure),source.historyRanges,source.historyDroppedRanges,source.historyComplete,source.historyBytes,source.preparationDecisions,source.droppedPreparationDecisions,source.preparationHistoryComplete,source.preparationHistoryBytes,source.futureClipRequirements,source.futureClipResourceLimits,source.futureClipReady};
        for(std::size_t n=0;n<sourceValues.size();++n){streaming[n*2].store(static_cast<std::uint32_t>(sourceValues[n]),std::memory_order_relaxed);streaming[n*2+1].store(static_cast<std::uint32_t>(sourceValues[n]>>32),std::memory_order_relaxed);}
        for(std::size_t n=0;n<sources.size();++n)sources[n].store(sourceMonitorPacket(runtime ? runtime->sourceSnapshot(static_cast<int>(n)) : SourceVoiceSnapshot{}));
        if(graphSchemaDirty)for(auto& effect:effects)effect.invalidate();
        if(runtime && runtime->graph())for(const auto& bus:runtime->graph()->buses)for(const auto& effect:bus.effects)
            effects[effect.index].store(effectPacket(runtime->effectSnapshot(effect.index)));
        album.store(albumFlowPacket(runtime ? runtime->albumFlowSnapshot() : AlbumFlowSnapshot{}));
        graphSchemaDirty=false;
        sequence.fetch_add(1,std::memory_order_release);
    }
};
}
