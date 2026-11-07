#pragma once
#include "api/EffectMonitorTypes.hpp"
#include <atomic>
namespace player2::api {
struct EffectMonitorSlot {
    std::array<std::atomic<std::uint32_t>,82> words{};
    void invalidate() noexcept {words[2].store(0,std::memory_order_relaxed);}
    void store(const EffectMonitorPacket& packet) noexcept {
        for(std::size_t n=0;n<packet.size();++n) {std::uint64_t bits;std::memcpy(&bits,&packet[n],8);
            words[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);
            words[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
    }
    EffectMonitorPacket load() const noexcept {
        if(!words[2].load(std::memory_order_relaxed))return effectPacket({});
        EffectMonitorPacket packet{};for(std::size_t n=0;n<packet.size();++n) {
            const auto bits=std::uint64_t(words[n*2].load(std::memory_order_relaxed))|(std::uint64_t(words[n*2+1].load(std::memory_order_relaxed))<<32);
            std::memcpy(&packet[n],&bits,8);
        }return packet;
    }
};
static_assert(std::atomic<std::uint32_t>::is_always_lock_free,"Effect publication must be lock-free");
}
