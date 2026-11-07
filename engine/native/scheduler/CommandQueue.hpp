#pragma once
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <type_traits>

namespace player2 {
enum class CommandKind { Play,Stop,Pause,Resume,Event,SetSync,SetParameter,Trigger,Performance,LayerGain };
struct Command { CommandKind kind{}; int target{-1},value{-1}; double number{}; std::int64_t frame{-1}; };
// Exactly one serialized control producer, one audio consumer. Seek/reconfigure run with output stopped.
template<class T,std::size_t Capacity=512> class SpscQueue {
    static_assert(Capacity>1 && std::is_trivially_copyable<T>::value,"Queue values must not allocate");
    std::array<T,Capacity> values_{};
    alignas(64) std::atomic<std::size_t> read_{0};
    alignas(64) std::atomic<std::size_t> write_{0};
public:
    bool push(const T& value) noexcept {
        const auto write=write_.load(std::memory_order_relaxed),next=(write+1)%Capacity;
        if(next==read_.load(std::memory_order_acquire)) return false;
        values_[write]=value; write_.store(next,std::memory_order_release); return true;
    }
    bool pop(T& value) noexcept {
        const auto read=read_.load(std::memory_order_relaxed);
        if(read==write_.load(std::memory_order_acquire)) return false;
        value=values_[read]; read_.store((read+1)%Capacity,std::memory_order_release); return true;
    }
    void clearStopped() noexcept { read_.store(0); write_.store(0); }
};
}
