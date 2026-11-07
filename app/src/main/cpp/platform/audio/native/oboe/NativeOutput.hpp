#pragma once
#include "api/EngineApi.hpp"
#include "AudioCallbackLifetime.hpp"
#include "CallbackTiming.hpp"
#include <oboe/Oboe.h>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <thread>
namespace player2::platform {
// Owns one device stream. Engine owns all transport, resampling and music execution.
class NativeOutput final {
    struct State;
    std::shared_ptr<State> state_;
    struct Callbacks;
    std::shared_ptr<Callbacks> callback_;
    api::EngineHandle* engine_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex control_;
    std::atomic<bool> exit_{false};
    std::atomic<int> rate_{0},rebuilds_{0};
    int requestedRate_{};
    std::thread worker_;
    bool openLocked();
    void closeLocked();
    void recoverLoop();
public:
    explicit NativeOutput(api::EngineHandle* engine);
    ~NativeOutput();
    bool start(int requestedRate=0);
    void stop();
    bool rebuild(int requestedRate=0);
    int rate() const noexcept { return rate_.load(); }
    int lastError() const noexcept;
    int rebuilds() const noexcept { return rebuilds_.load(); }
    std::uint64_t callbacks() const noexcept;
    std::array<std::int64_t,20> timingSnapshot();
};
}
