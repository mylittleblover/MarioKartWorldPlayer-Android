#include "NativeOutput.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <time.h>
namespace player2::platform {
struct NativeOutput::State {
    std::atomic<api::EngineHandle*> engine_;
    // Odd while onAudioReady or renderExternalPcm16 is reading engine_; even outside. Avoids any mutex on the RT thread.
    std::atomic<std::uint64_t> callbackGen_{0};
    std::atomic<bool> running_{false},recover_{false},external_{false};
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
namespace {
inline std::int16_t floatToPcm16(float sample) noexcept {
    const float clamped=std::clamp(sample,-1.0f,1.0f);
    return static_cast<std::int16_t>(std::lrintf(clamped*32767.0f));
}
void renderToStreamBuffer(api::EngineHandle* eng,void* data,std::int32_t frames,std::int32_t rate,std::int32_t channels,oboe::AudioFormat format) noexcept {
    if(channels==2 && format==oboe::AudioFormat::Float) {
        auto* output=static_cast<float*>(data);
        for(std::int32_t offset=0;offset<frames;) {
            const auto count=std::min<std::int32_t>(4096,frames-offset);
            api::renderEngineDevice(eng,output+offset*2,count,rate);
            offset+=count;
        }
        return;
    }
    if(channels<=0) return;
    constexpr std::int32_t ScratchFrames=2048;
    float scratch[ScratchFrames*2];
    if(format==oboe::AudioFormat::Float) {
        auto* output=static_cast<float*>(data);
        for(std::int32_t offset=0;offset<frames;) {
            const auto count=std::min<std::int32_t>(ScratchFrames,frames-offset);
            api::renderEngineDevice(eng,scratch,count,rate);
            if(channels==1) {
                for(std::int32_t i=0;i<count;++i) {
                    output[offset+i]=0.5f*(scratch[i*2]+scratch[i*2+1]);
                }
            } else {
                for(std::int32_t i=0;i<count;++i) {
                    auto* dst=output+static_cast<std::size_t>(offset+i)*static_cast<std::size_t>(channels);
                    dst[0]=scratch[i*2];
                    dst[1]=scratch[i*2+1];
                    for(std::int32_t ch=2;ch<channels;++ch) dst[ch]=0.0f;
                }
            }
            offset+=count;
        }
    } else if(format==oboe::AudioFormat::I16) {
        auto* output=static_cast<std::int16_t*>(data);
        for(std::int32_t offset=0;offset<frames;) {
            const auto count=std::min<std::int32_t>(ScratchFrames,frames-offset);
            api::renderEngineDevice(eng,scratch,count,rate);
            if(channels==1) {
                for(std::int32_t i=0;i<count;++i) {
                    output[offset+i]=floatToPcm16(0.5f*(scratch[i*2]+scratch[i*2+1]));
                }
            } else if(channels==2) {
                for(std::int32_t i=0;i<count;++i) {
                    const auto base=static_cast<std::size_t>(offset+i)*2U;
                    output[base]=floatToPcm16(scratch[i*2]);
                    output[base+1]=floatToPcm16(scratch[i*2+1]);
                }
            } else {
                for(std::int32_t i=0;i<count;++i) {
                    auto* dst=output+static_cast<std::size_t>(offset+i)*static_cast<std::size_t>(channels);
                    dst[0]=floatToPcm16(scratch[i*2]);
                    dst[1]=floatToPcm16(scratch[i*2+1]);
                    for(std::int32_t ch=2;ch<channels;++ch) dst[ch]=0;
                }
            }
            offset+=count;
        }
    } else {
        const auto bytesPerSample=oboe::convertFormatToSizeInBytes(format);
        if(bytesPerSample>0) {
            std::memset(data,0,static_cast<std::size_t>(frames)*static_cast<std::size_t>(channels)*static_cast<std::size_t>(bytesPerSample));
        }
    }
}
}
oboe::DataCallbackResult NativeOutput::Callbacks::onAudioReady(oboe::AudioStream* stream,void* data,std::int32_t frames) {
    AudioCallbackLifetime::Lease lease(callbackLifetime_);
    if(!lease || stream!=acceptedStream_.load(std::memory_order_acquire) || !state_->running_ || frames<=0)return oboe::DataCallbackResult::Stop;
    const auto readClock=[](clockid_t clock,std::uint64_t& result)noexcept {
        timespec value{};if(clock_gettime(clock,&value)!=0 || value.tv_sec<0)return false;
        result=static_cast<std::uint64_t>(value.tv_sec)*1000000000ULL+static_cast<std::uint64_t>(value.tv_nsec);return true;
    };
    std::uint64_t wallStart{},cpuStart{},cpuEnd{},wallEnd{};
    const bool started=readClock(CLOCK_MONOTONIC,wallStart) && readClock(CLOCK_THREAD_CPUTIME_ID,cpuStart);
    const auto rate=stream->getSampleRate();
    const auto channels=stream->getChannelCount();
    const auto format=stream->getFormat();
    state_->callbackGen_.fetch_add(1,std::memory_order_seq_cst);
    auto* eng=state_->engine_.load(std::memory_order_seq_cst);
    if(!eng) {
        state_->callbackGen_.fetch_add(1,std::memory_order_release);
        return oboe::DataCallbackResult::Stop;
    }
    renderToStreamBuffer(eng,data,frames,rate,channels,format);
    state_->callbackGen_.fetch_add(1,std::memory_order_release);
    const bool ended=readClock(CLOCK_THREAD_CPUTIME_ID,cpuEnd) && readClock(CLOCK_MONOTONIC,wallEnd);
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
    builder.setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(2)
        ->setChannelMask(oboe::ChannelMask::Stereo)
        ->setChannelConversionAllowed(true)
        ->setFormatConversionAllowed(true)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setPerformanceMode(oboe::PerformanceMode::None)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setBufferCapacityInFrames(16384)
        ->setDataCallback(callback_)
        ->setErrorCallback(callback_);
    if(requestedRate_) builder.setSampleRate(requestedRate_);
    auto result=builder.openStream(stream_);
    if(result!=oboe::Result::OK) { state_->lastError_=static_cast<int>(result); stream_.reset(); return false; }
    const auto burst=std::max<std::int32_t>(stream_->getFramesPerBurst(),192);
    const auto cap=stream_->getBufferCapacityInFrames();
    if(cap>0) stream_->setBufferSizeInFrames(std::min<std::int32_t>(cap,std::max<std::int32_t>(burst*8,8192)));
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
    rate_.store(0,std::memory_order_release);
}
bool NativeOutput::start(int requestedRate) {
    if(requestedRate!=0 && (requestedRate<8000 || requestedRate>192000)) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(state_->running_) return !state_->external_.load(std::memory_order_acquire);
    if(!engine_ || !api::claimEngineOutput(engine_)) return false;
    state_->external_.store(false,std::memory_order_release);
    requestedRate_=requestedRate; state_->running_=true;
    if(openLocked()) return true;
    state_->running_=false; api::releaseEngineOutput(engine_); return false;
}
bool NativeOutput::attachExternal(int rate) {
    if(rate<8000 || rate>192000) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(state_->running_) {
        if(!state_->external_.load(std::memory_order_acquire)) return false;
        requestedRate_=rate; rate_=rate; state_->lastError_=0; return true;
    }
    if(!engine_ || !api::claimEngineOutput(engine_)) return false;
    requestedRate_=rate; rate_=rate; state_->lastError_=0;
    state_->external_.store(true,std::memory_order_release);
    state_->running_.store(true,std::memory_order_release);
    return true;
}
bool NativeOutput::renderExternalPcm16(std::int16_t* pcm16,std::int32_t frames) noexcept {
    if(!pcm16 || frames<=0) return false;
    if(!state_->running_.load(std::memory_order_acquire) || !state_->external_.load(std::memory_order_acquire)) return false;
    const auto readClock=[](clockid_t clock,std::uint64_t& result)noexcept {
        timespec value{};if(clock_gettime(clock,&value)!=0 || value.tv_sec<0)return false;
        result=static_cast<std::uint64_t>(value.tv_sec)*1000000000ULL+static_cast<std::uint64_t>(value.tv_nsec);return true;
    };
    std::uint64_t wallStart{},cpuStart{},cpuEnd{},wallEnd{};
    const bool started=readClock(CLOCK_MONOTONIC,wallStart) && readClock(CLOCK_THREAD_CPUTIME_ID,cpuStart);
    state_->callbackGen_.fetch_add(1,std::memory_order_seq_cst);
    if(!state_->running_.load(std::memory_order_acquire) || !state_->external_.load(std::memory_order_acquire)) {
        state_->callbackGen_.fetch_add(1,std::memory_order_release);
        return false;
    }
    auto* eng=state_->engine_.load(std::memory_order_seq_cst);
    if(!eng) {
        state_->callbackGen_.fetch_add(1,std::memory_order_release);
        return false;
    }
    const auto rate=rate_.load(std::memory_order_relaxed);
    const auto activeRate=rate>0 ? rate : 48000;
    renderToStreamBuffer(eng,pcm16,frames,activeRate,2,oboe::AudioFormat::I16);
    state_->callbackGen_.fetch_add(1,std::memory_order_release);
    const bool ended=readClock(CLOCK_THREAD_CPUTIME_ID,cpuEnd) && readClock(CLOCK_MONOTONIC,wallEnd);
    if(started && ended && cpuEnd>=cpuStart && wallEnd>=wallStart && activeRate>0)
        state_->timing_.record(cpuEnd-cpuStart,wallEnd-wallStart,static_cast<std::uint64_t>(frames)*1000000000ULL/static_cast<unsigned>(activeRate));
    else state_->timing_.clockFailure();
    ++state_->callbacks_;
    return true;
}
void NativeOutput::stop() {
    std::lock_guard<std::mutex> lock(control_);
    const bool wasRunning=state_->running_.exchange(false,std::memory_order_seq_cst);
    const bool wasExternal=state_->external_.exchange(false,std::memory_order_seq_cst);
    if(wasExternal) {
        while((state_->callbackGen_.load(std::memory_order_acquire)&1ULL)!=0ULL) {
            std::this_thread::sleep_for(std::chrono::microseconds(100));
        }
        rate_.store(0,std::memory_order_release);
    } else {
        closeLocked();
    }
    state_->recover_=false;
    if(wasRunning && engine_) api::releaseEngineOutput(engine_);
}
bool NativeOutput::rebuild(int requestedRate) {
    if(requestedRate!=0 && (requestedRate<8000 || requestedRate>192000)) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(!state_->running_) return false;
    if(state_->external_.load(std::memory_order_acquire)) {
        if(requestedRate>0) { requestedRate_=requestedRate; rate_=requestedRate; }
        ++rebuilds_;
        return true;
    }
    requestedRate_=requestedRate; closeLocked(); ++rebuilds_;
    if(openLocked()) return true;
    state_->running_=false; if(engine_) api::releaseEngineOutput(engine_); return false;
}
bool NativeOutput::rebind(api::EngineHandle* nextEngine,int requestedRate) {
    if(!nextEngine) return false;
    if(requestedRate!=0 && (requestedRate<8000 || requestedRate>192000)) return false;
    std::lock_guard<std::mutex> lock(control_);
    if(state_->running_ && state_->external_.load(std::memory_order_acquire)) {
        if(nextEngine!=engine_) {
            if(!api::claimEngineOutput(nextEngine)) return false;
            auto* oldEngine=engine_;
            engine_=nextEngine;
            state_->engine_.store(nextEngine,std::memory_order_seq_cst);
            const auto observed=state_->callbackGen_.load(std::memory_order_seq_cst);
            if((observed&1ULL)!=0ULL) {
                while(state_->callbackGen_.load(std::memory_order_acquire)==observed) {
                    std::this_thread::sleep_for(std::chrono::microseconds(100));
                }
            }
            if(oldEngine) api::releaseEngineOutput(oldEngine);
        }
        if(requestedRate>0) {
            requestedRate_=requestedRate;
            rate_.store(requestedRate,std::memory_order_release);
        }
        return true;
    }
    const int currentRate=rate_.load(std::memory_order_acquire);
    const bool rateMatches=requestedRate==0 ||
        (currentRate>0 && requestedRate==currentRate) ||
        (requestedRate_>0 && requestedRate==requestedRate_);
    const bool streamHealthy=state_->running_ && stream_ &&
        stream_->getState()==oboe::StreamState::Started &&
        stream_->getChannelCount()==2 &&
        stream_->getFormat()==oboe::AudioFormat::Float &&
        rateMatches;
    if(nextEngine==engine_) {
        if(streamHealthy) return true;
    }
    if(streamHealthy) {
        if(nextEngine!=engine_) {
            if(!api::claimEngineOutput(nextEngine)) return false;
            auto* oldEngine=engine_;
            engine_=nextEngine;
            state_->engine_.store(nextEngine,std::memory_order_seq_cst);
            const auto observed=state_->callbackGen_.load(std::memory_order_seq_cst);
            if((observed&1ULL)!=0ULL) {
                while(state_->callbackGen_.load(std::memory_order_acquire)==observed) {
                    std::this_thread::sleep_for(std::chrono::microseconds(100));
                }
            }
            if(oldEngine) api::releaseEngineOutput(oldEngine);
        }
        return true;
    }
    const bool wasRunning=state_->running_.exchange(false);
    closeLocked(); state_->recover_=false;
    if(wasRunning && engine_) api::releaseEngineOutput(engine_);
    if(!api::claimEngineOutput(nextEngine)) {
        engine_=nullptr;
        state_->engine_.store(nullptr,std::memory_order_release);
        return false;
    }
    engine_=nextEngine;
    state_->engine_.store(nextEngine,std::memory_order_release);
    requestedRate_=requestedRate;
    state_->external_.store(false,std::memory_order_release);
    state_->running_=true;
    if(openLocked()) return true;
    state_->running_=false;
    api::releaseEngineOutput(nextEngine);
    engine_=nullptr;
    state_->engine_.store(nullptr,std::memory_order_release);
    return false;
}
int NativeOutput::lastError()const noexcept{return state_->lastError_.load();}
std::uint64_t NativeOutput::callbacks()const noexcept{return state_->callbacks_.load();}
std::array<std::int64_t,20> NativeOutput::timingSnapshot() {
    // Queries and SDK XRun inspection are control-side only. Callback records
    // use lock-free 32-bit atomics; stream lifetime remains under control_.
    std::lock_guard<std::mutex> lock(control_);const auto snapshot=state_->timing_.snapshot();
    std::int64_t xruns=state_->external_.load(std::memory_order_acquire) ? 0 : -1;
    if(stream_) {const auto count=stream_->getXRunCount();if(count)xruns=count.value();}
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
        // Let Android/Bluetooth/headphone routing settle before reopening so we do not lock onto a transient mono route.
        state_->wake_.wait_for(wait,std::chrono::milliseconds(120),[this]{return exit_.load();});
        if(exit_) break;
        wait.unlock();
        constexpr int retryDelaysMs[]={0,150,250,400,600};
        bool recovered=false;
        bool attempted=false;
        for(int delayMs:retryDelaysMs) {
            if(exit_) break;
            if(delayMs>0) {
                wait.lock();
                state_->wake_.wait_for(wait,std::chrono::milliseconds(delayMs),[this]{return exit_.load();});
                wait.unlock();
                if(exit_) break;
            }
            std::lock_guard<std::mutex> lock(control_);
            if(!state_->running_ || state_->external_.load(std::memory_order_acquire)) { recovered=true; break; }
            if(!attempted) {
                if(!callback_ || !callback_->recover_.exchange(false)) { recovered=true; break; }
                state_->lastError_=callback_->lastError_.load();
                closeLocked();
                ++rebuilds_;
                attempted=true;
            } else if(stream_ || callback_) {
                // Another control call already reopened the stream while backing off.
                recovered=true;
                break;
            }
            if(openLocked()) { recovered=true; break; }
        }
        if(attempted && !recovered && !exit_) {
            std::lock_guard<std::mutex> lock(control_);
            if(state_->running_ && !state_->external_.load(std::memory_order_acquire) && !stream_) {
                state_->running_=false;
                rate_.store(0,std::memory_order_release);
                if(engine_) api::releaseEngineOutput(engine_);
            }
        }
        wait.lock();
    }
}
}
