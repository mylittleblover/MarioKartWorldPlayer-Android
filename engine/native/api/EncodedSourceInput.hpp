#pragma once
#include "api/AudioSourceTypes.hpp"

namespace player2::audio {
struct SourceReadResult {SourceStatus status{SourceStatus::Ok};std::size_t bytes{};};
// Owned encoded-input lease. Platform implements fd/pread/reopen policy; Engine
// implements format parsing. Reads may block and are control/decode-worker ONLY.
// No JVM callback, format choice, sample clock or scheduling belongs in this port.
class EncodedSourceInput {
public:
    virtual ~EncodedSourceInput()=default;
    virtual std::uint64_t byteLength()const noexcept=0;
    virtual SourceReadResult read(SourceByteOffset offset,std::uint8_t* destination,std::size_t count)noexcept=0;
};
}
