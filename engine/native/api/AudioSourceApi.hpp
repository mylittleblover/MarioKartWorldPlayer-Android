#pragma once
#include "api/AudioSourceTypes.hpp"
#include "api/SequenceTypes.hpp"
#include "api/ModuleTypes.hpp"
#include "api/EncodedSourceInput.hpp"
#include "api/StreamTypes.hpp"
#include <array>
#include <memory>
#include <vector>

namespace player2::api {
class AudioSourceHandle;
inline constexpr std::size_t MaximumSourceResources=4096;
struct SourceResource { const char* id{}; audio::ByteView bytes; };
struct SourceOpenRequest {
    const char* providerId{};
    audio::ByteView bytes;
    const char* entryId{};
    const char* subsongId{};
    const char* variantId{};
    const SourceResource* resources{};
    std::size_t resourceCount{};
    audio::DecodeLimits limits;
    const char* assetId{};
    // Optional expected original root identity, valid only during this control call.
    const std::array<std::uint8_t,32>* expectedRootSha256{};
};
struct SourceOpenResult { audio::SourceStatus status{}; AudioSourceHandle* handle{}; };
struct SourceRenderResult { audio::SourceStatus status{}; std::size_t frames{}; };
struct SourceEntryIndexResult {audio::SourceStatus status{};std::vector<audio::SourceEntry> entries;};
inline constexpr std::size_t MaximumAudioProviders=64;
// Process-lifetime immutable strings; fixed facade shape independent of format count.
// Selection/persistence use the stable string ID, never this catalog's position.
struct SourceProviderCatalog {
    const char* const* identifiers{};std::size_t count{};
    std::size_t size()const noexcept{return count;}
    const char* operator[](std::size_t index)const noexcept{return identifiers[index];}
    const char* const* begin()const noexcept{return identifiers;}
    const char* const* end()const noexcept{return count ? identifiers+count : identifiers;}
};
SourceProviderCatalog audioProviderIds() noexcept;
const char* probeAudioSource(audio::ByteView bytes) noexcept;
// Owner/control thread only. No JNI or platform types enter these APIs.
SourceOpenResult openAudioSource(const SourceOpenRequest& request) noexcept;
SourceOpenResult openAudioSourceInput(const SourceOpenRequest& selection,std::unique_ptr<audio::EncodedSourceInput> input) noexcept;
void closeAudioSource(AudioSourceHandle* handle) noexcept;
audio::SourceStatus audioSourceMetadata(const AudioSourceHandle* handle,audio::SourceMetadata& metadata) noexcept;
SourceRenderResult audioSourceSubsongs(const AudioSourceHandle* handle,std::size_t offset,audio::SourceSubsong* output,std::size_t capacity)noexcept;
SourceRenderResult audioSourceEntries(const AudioSourceHandle* handle,std::size_t offset,audio::SourceEntry* output,std::size_t capacity)noexcept;
SourceEntryIndexResult inspectAudioContainerEntries(const char* provider,audio::ByteView bytes,const audio::DecodeLimits& limits)noexcept;
audio::SourceSamplePosition audioSourcePosition(const AudioSourceHandle* handle) noexcept;
SourceRenderResult renderAudioSource(AudioSourceHandle* handle,float* output,std::size_t frames,std::size_t sampleCapacity) noexcept;
audio::SourceStatus seekAudioSource(AudioSourceHandle* handle,audio::SourceSamplePosition position) noexcept;
audio::SourceStatus resetAudioSource(AudioSourceHandle* handle) noexcept;
audio::SourceStatus audioStreamInspection(const AudioSourceHandle*,audio::SourceStreamInfo&,std::array<std::uint8_t,32>& rootDigest)noexcept;
audio::SourceStatus setAudioStreamGroups(AudioSourceHandle*,std::uint32_t mute,std::uint32_t solo)noexcept;
// Read-only control inspection. Pages are bounded and never advance source position.
audio::SourceStatus audioSequenceSummary(const AudioSourceHandle* handle,audio::SequenceSummary& summary) noexcept;
SourceRenderResult audioSequenceEvents(const AudioSourceHandle* handle,std::size_t offset,audio::SequenceEvent* events,std::size_t capacity) noexcept;
audio::SourceStatus audioModuleSummary(const AudioSourceHandle*,audio::ModuleSummary&) noexcept;
SourceRenderResult audioModuleOrders(const AudioSourceHandle*,std::size_t,audio::ModuleOrder*,std::size_t) noexcept;
SourceRenderResult audioModulePatterns(const AudioSourceHandle*,std::size_t,audio::ModulePattern*,std::size_t) noexcept;
SourceRenderResult audioModuleCells(const AudioSourceHandle*,audio::SourceByteOffset,std::size_t,audio::ModuleCell*,std::size_t) noexcept;
SourceRenderResult audioModuleInstruments(const AudioSourceHandle*,std::size_t,audio::ModuleInstrument*,std::size_t) noexcept;
SourceRenderResult audioModuleSamples(const AudioSourceHandle*,std::size_t,audio::ModuleSample*,std::size_t) noexcept;
SourceRenderResult audioModuleSubsongs(const AudioSourceHandle*,std::size_t,audio::ModuleSubsong*,std::size_t) noexcept;

}
