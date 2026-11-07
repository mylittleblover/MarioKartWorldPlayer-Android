#pragma once
#include <atomic>
#include <chrono>
#include <thread>

namespace player2::platform {
// One callback consumer. Closing blocks new entry and waits ONLY on control.
// The callback uses lock-free atomics; no lock, I/O, allocation or waiting.
class AudioCallbackLifetime {
    static constexpr unsigned Closed=2;
    std::atomic<unsigned> state_{Closed};
public:
    static_assert(std::atomic<unsigned>::is_always_lock_free);
    bool open()noexcept {unsigned expected=Closed;return state_.compare_exchange_strong(expected,0,std::memory_order_acq_rel);}
    bool enter()noexcept {unsigned expected=0;return state_.compare_exchange_strong(expected,1,std::memory_order_acquire);}
    void leave()noexcept {state_.fetch_sub(1,std::memory_order_release);}
    void close()noexcept {state_.fetch_or(Closed,std::memory_order_acq_rel);}
    bool idle()const noexcept{return state_.load(std::memory_order_acquire)==Closed;}
    void joinControl()const {
        while(!idle())std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    class Lease {
        AudioCallbackLifetime& owner_;bool entered_;
    public:
        explicit Lease(AudioCallbackLifetime& owner)noexcept:owner_(owner),entered_(owner.enter()){}
        ~Lease(){if(entered_)owner_.leave();}
        explicit operator bool()const noexcept{return entered_;}
        Lease(const Lease&)=delete;Lease& operator=(const Lease&)=delete;
    };
};
}
