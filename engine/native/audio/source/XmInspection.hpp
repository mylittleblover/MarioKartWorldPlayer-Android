#pragma once
#include "api/AudioSourceApi.hpp"

namespace player2::api {
audio::SourceStatus xmSummary(const AudioSourceHandle*,audio::ModuleSummary&)noexcept;
SourceRenderResult xmOrders(const AudioSourceHandle*,std::size_t,audio::ModuleOrder*,std::size_t)noexcept;
SourceRenderResult xmPatterns(const AudioSourceHandle*,std::size_t,audio::ModulePattern*,std::size_t)noexcept;
SourceRenderResult xmCells(const AudioSourceHandle*,audio::SourceByteOffset,std::size_t,audio::ModuleCell*,std::size_t)noexcept;
SourceRenderResult xmInstruments(const AudioSourceHandle*,std::size_t,audio::ModuleInstrument*,std::size_t)noexcept;
SourceRenderResult xmSamples(const AudioSourceHandle*,std::size_t,audio::ModuleSample*,std::size_t)noexcept;
}
