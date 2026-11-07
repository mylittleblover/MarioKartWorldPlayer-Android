#pragma once
#include "api/AudioSourceApi.hpp"
#include "api/Mo3Types.hpp"

namespace player2::api {
SourceRenderResult audioMo3Timeline(const AudioSourceHandle*,std::size_t,audio::Mo3PlaybackRow*,std::size_t)noexcept;
audio::SourceStatus audioMo3Summary(const AudioSourceHandle*,audio::Mo3Summary&)noexcept;
SourceRenderResult audioMo3Orders(const AudioSourceHandle*,std::size_t,audio::Mo3Order*,std::size_t)noexcept;
SourceRenderResult audioMo3Patterns(const AudioSourceHandle*,std::size_t,audio::Mo3Pattern*,std::size_t)noexcept;
SourceRenderResult audioMo3Cells(const AudioSourceHandle*,std::uint32_t,std::size_t,audio::Mo3Cell*,std::size_t)noexcept;
SourceRenderResult audioMo3Instruments(const AudioSourceHandle*,std::size_t,audio::Mo3Instrument*,std::size_t)noexcept;
audio::SourceStatus audioMo3Envelope(const AudioSourceHandle*,std::uint32_t instrument,audio::Mo3EnvelopeKind,audio::Mo3Envelope&)noexcept;
SourceRenderResult audioMo3Samples(const AudioSourceHandle*,std::size_t,audio::Mo3Sample*,std::size_t)noexcept;
SourceRenderResult audioMo3Subsongs(const AudioSourceHandle*,std::size_t,audio::Mo3Subsong*,std::size_t)noexcept;
}
