#pragma once
#include "api/MusicalLoopMonitorTypes.hpp"
#include <atomic>
#include <cstring>
namespace player2::api {
// Written under the existing Engine publication sequence; no callback allocation.
struct MusicalLoopMonitorSlot {
    std::array<std::atomic<std::uint32_t>,20> words{};
    MusicalLoopMonitorSlot()noexcept {store(musicalLoopPacket({}));}
    void store(const MusicalLoopMonitorPacket& p)noexcept {
        for(std::size_t n=0;n<p.size();++n){std::uint64_t bits;std::memcpy(&bits,&p[n],8);words[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);words[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
    }
    MusicalLoopMonitorPacket load()const noexcept {
        MusicalLoopMonitorPacket p{};for(std::size_t n=0;n<p.size();++n){const auto bits=std::uint64_t(words[n*2].load(std::memory_order_relaxed))|(std::uint64_t(words[n*2+1].load(std::memory_order_relaxed))<<32);std::memcpy(&p[n],&bits,8);}return p;
    }
};
}
