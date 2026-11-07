#pragma once
#include <cstddef>
#include <cstdint>
#include "api/EngineTypes.hpp"
#include "api/EffectMonitorTypes.hpp"
#include "api/AlbumFlowMonitorTypes.hpp"
#include "api/SourceMonitorTypes.hpp"
#include "api/PlaylistMonitorTypes.hpp"
#include "api/GameSyncMonitorTypes.hpp"
#include "api/AutomaticMonitorTypes.hpp"
#include "api/MusicalLoopMonitorTypes.hpp"
#include "api/AudioSourceApi.hpp"

namespace player2::api {
// Opaque Engine lifetime. All calls currently belong to the owning control thread;
// loadCompiledGraph allocates and validates and must never run on an audio callback.
class EngineHandle;
MusicalLoopMonitorPacket engineMusicalLoopSnapshot(const EngineHandle* engine)noexcept;
enum class LoadResult : std::int32_t { Ok = 0, InvalidGraph = 1, OutOfMemory = 2, OutputActive = 3, ResourceLimit = 4 };
EngineHandle* createEngine() noexcept;
void destroyEngine(EngineHandle* engine) noexcept;
// Opt in before loading a graph or starting an audio consumer.
EngineStatus enableCueSearchMonitorV9(EngineHandle* engine) noexcept;
bool cueSearchMonitorV9Enabled(const EngineHandle* engine) noexcept;
LoadResult loadCompiledGraph(EngineHandle* engine, const std::uint8_t* bytes, std::size_t size) noexcept;
std::int32_t transportSampleRate(const EngineHandle* engine = nullptr) noexcept;
// Mutations, binding, seek and destruction require the audio consumer to be stopped/joined.
// V3 compares encoded root bytes against the compiled SHA-256 before changing source state.
// Caller retains stable input bytes throughout this synchronous owner/control call.
EngineStatus bindEngineSource(EngineHandle* engine,int assetIndex,const SourceOpenRequest& source,SourcePreparation preparation=SourcePreparation::Prepared) noexcept;
EngineStatus loadLinearSource(EngineHandle* engine,const SourceOpenRequest& source,bool loop=false,SourcePreparation preparation=SourcePreparation::Prepared) noexcept;
// Owned blocking input is decoded only by control/preparation workers. Streamed
// mode uses the existing bounded PCM page cache; the callback never reads input.
EngineStatus bindEngineSourceInput(EngineHandle*,int assetIndex,const SourceOpenRequest&,
    std::unique_ptr<audio::EncodedSourceInput>,SourcePreparation=SourcePreparation::Streamed) noexcept;
EngineStatus loadLinearSourceInput(EngineHandle*,const SourceOpenRequest&,
    std::unique_ptr<audio::EncodedSourceInput>,bool loop=false,SourcePreparation=SourcePreparation::Streamed) noexcept;
EngineStatus submitEngineCommand(EngineHandle* engine,const EngineCommand& command) noexcept;
// Device-rate rendering is the same runtime; no second clock or scheduling implementation.
void renderEngineDevice(EngineHandle* engine,float* stereo,std::size_t frames,int deviceRate) noexcept;
void renderEngine(EngineHandle* engine,float* stereo,std::size_t frames) noexcept;
EngineSnapshot engineSnapshot(const EngineHandle* engine) noexcept;
EntryStateSnapshot engineEntryStateSnapshot(const EngineHandle* engine) noexcept;
PerformanceSnapshot enginePerformanceSnapshot(const EngineHandle* engine) noexcept;
LayerGainSnapshot engineLayerGainSnapshot(const EngineHandle* engine,int index) noexcept;
GameSyncMonitorPacket engineGameSyncSnapshot(const EngineHandle* engine) noexcept;
AutomaticMonitorPacket engineAutomaticSnapshot(const EngineHandle* engine)noexcept;
StreamingSnapshot engineStreamingSnapshot(const EngineHandle* engine) noexcept;
SourceMonitorPacket engineSourceSnapshot(const EngineHandle* engine,int voiceSlot) noexcept;
// Stopped-control rendering may prepare pages synchronously through the same runtime.
EngineStatus renderEngineControl(EngineHandle* engine,float* stereo,std::size_t frames) noexcept;
// Debug/test callback harness: owns a temporary output lease, never prepares pages.
EngineStatus renderEngineRealtimeHarness(EngineHandle* engine,float* stereo,std::size_t frames) noexcept;
// Stopped-owner debug harness for deterministic unavailable-worker regressions.
// Control render/seek resumes the normal worker; pause again before another harness block.
EngineStatus pauseEngineDecodeWorkerHarness(EngineHandle* engine,bool pause) noexcept;
// Coherent published state; safe to query while the single output consumer runs.
GameParameterSnapshot engineParameterSnapshot(const EngineHandle* engine,int index) noexcept;
BusSnapshot engineBusSnapshot(const EngineHandle* engine,int index) noexcept;
EffectMonitorPacket engineEffectSnapshot(const EngineHandle* engine,int index) noexcept;
AlbumFlowMonitorPacket engineAlbumFlowSnapshot(const EngineHandle* engine) noexcept;
PlaylistGroupSnapshot enginePlaylistGroupSnapshot(const EngineHandle* engine,int index) noexcept;
PlaylistDecisionSnapshot enginePlaylistDecisionSnapshot(const EngineHandle* engine,int index) noexcept;
EngineStatus setLinearStreamGroups(EngineHandle*,std::uint32_t mute,std::uint32_t solo,const std::array<std::uint8_t,32>* expectedRoot=nullptr)noexcept;
EngineStatus setLinearModuleChannels(EngineHandle*,std::uint64_t mute,std::uint64_t solo)noexcept;
EngineStatus setLinearRepeat(EngineHandle*,bool enabled,std::optional<audio::SourceLoopMetadata> range=std::nullopt)noexcept;
EngineStatus seekEngine(EngineHandle* engine,audio::TransportFrame position) noexcept;
// One output consumer lease. A second output and worker mutations are rejected while leased.
bool claimEngineOutput(EngineHandle* engine) noexcept;
void releaseEngineOutput(EngineHandle* engine) noexcept;
}
