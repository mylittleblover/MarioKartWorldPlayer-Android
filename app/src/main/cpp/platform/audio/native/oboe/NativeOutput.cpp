#include "NativeOutput.hpp"
#include <algorithm>
#include <chrono>
#include <time.h>
namespace player2::platform {
struct NativeOutput::State {
    api::EngineHandle* engine_;
    std::atomic<bool> running_{false},recover_{false};
    std::atomic<int> lastError_{0};
    std::atomic<std::uint64_t> callbacks_{0};
    CallbackTiming timing_;
    std::mutex wakeMutex_;
    std::condition_variable wake_;
    explicit State(api::EngineHandle* engine):engine_(engine) {}
};
// A fresh context per stream generation. Retired contexts remain closed forever,
// even if an SDK thread still owns a shared callback after stream retirement.
struct NativeOutput::Callbacks final:oboe::AudioStreamDataCallback,oboe::AudioStreamErrorCallback {
    std::shared_ptr<State> state_;
    AudioCallbackLifetime callbackLifetime_;
    std::atomic<oboe::AudioStream*> acceptedStream_{nullptr};
    std::atomic<bool> recover_{false};
    std::atomic<int> lastError_{0};
    explicit Callbacks(std::shared_ptr<State> state):state_(std::move(state)) {}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*,void*,std::int32_t)override;
    void onErrorAfterClose(oboe::AudioStream*,oboe::Result)override;
};
oboe::DataCallbackResult NativeOutput::Callbacks::onAudioReady(oboe::AudioStream* stream,void* data,std::int32_t frames) {
    AudioCallbackLifetime::Lease lease(callbackLifetime_);
    if(!lease || stream!=acceptedStream_.load(std::memory_order_acquire) || !state_->running_ || frames<=0)return oboe::DataCallbackResult::Stop;
    const auto readClock=[](clockid_t clock,std::uint64_t& result)noexcept {
        timespec value{};if(clock_gettime(clock,&value)!=0 || value.tv_sec<0)return false;
        result=static_cast<std::uint64_t>(value.tv_sec)*1000000000ULL+static_cast<std::uint64_t>(value.tv_nsec);return true;
    };
    std::uint64_t wallStart{},cpuStart{},cpuEnd{},wallEnd{};
    const bool started=readClock(CLOCK_MONOTONIC,wallStart) && readClock(CLOCK_THREAD_CPUTIME_ID,cpuStart);
    auto* output=static_cast<float*>(data);
    for(std::int32_t offset=0;offset<frames;) {
        const auto count=std::min<std::int32_t>(4096,frames-offset);
        api::renderEngineDevice(state_->engine_,output+offset*2,count,stream->getSampleRate()); offset+=count;
    }
    const bool ended=readClock(CLOCK_THREAD_CPUTIME_ID,cpuEnd) && readClock(CLOCK_MONOTONIC,wallEnd);
    const auto rate=stream->getSampleRate();
    if(started && ended && cpuEnd>=cpuStart && wallEnd>=wallStart && rate>0)
        state_->timing_.record(cpuEnd-cpuStart,wallEnd-wallStart,static_cast<std::uint64_t>(frames)*1000000000ULL/static_cast<unsigned>(rate));
    else state_->timing_.clockFailure();
    ++state_->callbacks_; return oboe::DataCallbackResult::Continue;
}
void NativeOutput::Callbacks::onErrorAfterClose(oboe::AudioStream* stream,oboe::Result result) {
    if(stream!=acceptedStream_.load(std::memory_order_acquire))return;
    lastError_=static_cast<int>(result);
    if(state_->running_) {recover_=true;state_->recover_=true;state_->wake_.notify_one();}
}
NativeOutput::NativeOutput(api::EngineHandle* engine):state_(std::make_shared<State>(engine)),engine_(engine),worker_([this]{recoverLoop();}) {}
NativeOutput::~NativeOutput() { stop(); exit_=true; state_->wake_.notify_one(); worker_.join(); }
bool NativeOutput::openLocked() {
    try {callback_=std::make_shared<Callbacks>(state_);}
    catch(...) {state_->lastError_=static_cast<int>(oboe::Result::ErrorNoMemory);return false;}
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(2)->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)->setDataCallback(callback_)->setErrorCallback(callback_);
    if(requestedRate_) builder.setSampleRate(requestedRate_);
    auto result=builder.openStream(stream_);
    if(result!=oboe::Result::OK) { state_->lastError_=static_cast<int>(result); stream_.reset(); return false; }
    rate_=stream_->getSampleRate();
    if(!callback_->callbackLifetime_.open()) {state_->lastError_=static_cast<int>(oboe::Result::ErrorInvalidState);closeLocked();return false;}
    callback_->acceptedStream_.store(stream_.get(),std::memory_order_release);result=stream_->requestStart();
    if(result!=oboe::Result::OK) { state_->lastError_=static_cast<int>(result); closeLocked(); return false; }
    state_->lastError_=0; return true;
}
void NativeOutput::closeLocked() {
    if(callback_) {callback_->callbackLifetime_.close();callback_->acceptedStream_.store(nullptr,std::memory_order_release);}
    if(stream_)stream_->requestStop();
    // requestStop/close alone need not join a long in-flight Native callback.
    // Keep both Engine output ownership and the stream alive until it leaves.
    if(callback_)callback_->callbackLifetime_.joinControl();
    if(stream_) {stream_->close();stream_.reset();}
    callback_.reset();
}
bool NativeOutput::start(int requestedRate) {
    if(requestedRate!=0 && (requestedRate<8000 || requestedRate>192000)) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(state_->running_) return true;
    if(!api::claimEngineOutput(engine_)) return false;
    requestedRate_=requestedRate; state_->running_=true;
    if(openLocked()) return true;
    state_->running_=false; api::releaseEngineOutput(engine_); return false;
}
void NativeOutput::stop() {
    std::lock_guard<std::mutex> lock(control_); const bool wasRunning=state_->running_.exchange(false);
    closeLocked(); state_->recover_=false;
    if(wasRunning) api::releaseEngineOutput(engine_);
}
bool NativeOutput::rebuild(int requestedRate) {
    if(requestedRate!=0 && (requestedRate<8000 || requestedRate>192000)) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(!state_->running_) return false;
    requestedRate_=requestedRate; closeLocked(); ++rebuilds_;
    if(openLocked()) return true;
    state_->running_=false; api::releaseEngineOutput(engine_); return false;
}
int NativeOutput::lastError()const noexcept{return state_->lastError_.load();}
std::uint64_t NativeOutput::callbacks()const noexcept{return state_->callbacks_.load();}
std::array<std::int64_t,20> NativeOutput::timingSnapshot() {
    // Queries and SDK XRun inspection are control-side only. Callback records
    // use lock-free 32-bit atomics; stream lifetime remains under control_.
    std::lock_guard<std::mutex> lock(control_);const auto snapshot=state_->timing_.snapshot();
    std::int64_t xruns=-1;if(stream_) {const auto count=stream_->getXRunCount();if(count)xruns=count.value();}
    return {1,snapshot.capacity,snapshot.captured,snapshot.total,snapshot.missed,snapshot.clockFailures,snapshot.clipped,snapshot.saturated,
        static_cast<std::int64_t>(snapshot.cpu.p50),static_cast<std::int64_t>(snapshot.cpu.p95),static_cast<std::int64_t>(snapshot.cpu.p99),
        static_cast<std::int64_t>(snapshot.cpu.p999),static_cast<std::int64_t>(snapshot.cpu.maximum),
        static_cast<std::int64_t>(snapshot.wall.p50),static_cast<std::int64_t>(snapshot.wall.p95),static_cast<std::int64_t>(snapshot.wall.p99),
        static_cast<std::int64_t>(snapshot.wall.p999),static_cast<std::int64_t>(snapshot.wall.maximum),xruns,
        static_cast<std::int64_t>(snapshot.lastBudgetMicros)*1000};
}
void NativeOutput::recoverLoop() {
    std::unique_lock<std::mutex> wait(state_->wakeMutex_);
    while(!exit_) {
        state_->wake_.wait_for(wait,std::chrono::milliseconds(100),[this]{return exit_.load() || state_->recover_.load();});
        if(exit_) break;
        if(!state_->recover_.exchange(false))continue;
        wait.unlock();
        { std::lock_guard<std::mutex> lock(control_);
          if(state_->running_ && callback_ && callback_->recover_.exchange(false)) {
              state_->lastError_=callback_->lastError_.load();
              closeLocked(); ++rebuilds_;
              if(!openLocked()) { state_->running_=false; api::releaseEngineOutput(engine_); }
          }
        }
        wait.lock();
    }
}
}
