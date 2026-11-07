#pragma once
#include "api/AutomaticMonitorTypes.hpp"
#include <atomic>
namespace player2::api {
struct AutomaticMonitorSlot {
    std::array<std::atomic<std::uint32_t>,42> words{};
    AutomaticMonitorSlot()noexcept {store(automaticPacket({}));}
    void store(const AutomaticMonitorPacket& packet)noexcept {
        for(std::size_t n=0;n<packet.size();++n){std::uint64_t bits;std::memcpy(&bits,&packet[n],8);words[n*2].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);words[n*2+1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);}
    }
    AutomaticMonitorPacket load()const noexcept {
        AutomaticMonitorPacket p{};for(std::size_t n=0;n<p.size();++n){const auto bits=std::uint64_t(words[n*2].load(std::memory_order_relaxed))|(std::uint64_t(words[n*2+1].load(std::memory_order_relaxed))<<32);std::memcpy(&p[n],&bits,8);}return p;
    }
};
}
