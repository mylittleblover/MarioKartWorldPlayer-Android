#pragma once
#include "api/AudioSourceTypes.hpp"
#include "api/EncodedSourceInput.hpp"
#include <array>
namespace player2::audio {
using SourceDigest=std::array<std::uint8_t,32>;
// Owner/control only. Hashes the entire bounded encoded root asset, not decoded PCM.
// Invalid input leaves the output unchanged. No allocation or platform dependency.
bool sourceSha256(ByteView bytes,SourceDigest& output) noexcept;
// Bounded 8 KiB reads; verifies the same original bytes without retaining a copy.
// Short/failed reads preserve output and their structured diagnostic.
SourceStatus sourceSha256(EncodedSourceInput& input,const DecodeLimits& limits,SourceDigest& output) noexcept;
}
