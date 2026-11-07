#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <limits>
namespace player2::platform {
/** Device callback diagnostics only. Never drives Transport or musical scheduling.
 * Single callback writer; control queries capture stable records from a bounded
 * rolling window. Duration resolution is 1 us, rounded upward; not lifetime
 * percentiles. All callback atomics are explicitly lock-free on every ABI. */
class CallbackTiming {
    static_assert(std::atomic<std::uint32_t>::is_always_lock_free);
    static constexpr std::size_t Capacity=2048;
    struct Slot {std::atomic<std::uint32_t> stamp{},cpu{},wall{};};
    std::array<Slot,Capacity> slots_{};
    std::uint32_t serial_{};std::size_t next_{};
    std::atomic<std::uint32_t> total_{},missed_{},failed_{},clipped_{},budget_{};
    static void increment(std::atomic<std::uint32_t>& value)noexcept {
        const auto current=value.load(std::memory_order_relaxed);
        if(current!=std::numeric_limits<std::uint32_t>::max())value.store(current+1,std::memory_order_release);
    }
    static std::uint32_t micros(std::uint64_t nanos)noexcept {
        const auto value=nanos/1000+(nanos%1000!=0);
        return static_cast<std::uint32_t>(std::min<std::uint64_t>(value,std::numeric_limits<std::uint32_t>::max()));
    }
public:
    struct Percentiles {std::uint64_t p50{},p95{},p99{},p999{},maximum{};};
    struct Snapshot {
        std::uint32_t capacity{Capacity},captured{},total{},missed{},clockFailures{},clipped{},lastBudgetMicros{};
        bool saturated{};Percentiles cpu,wall;
    };
    void clockFailure()noexcept {increment(failed_);}
    void record(std::uint64_t cpuNanos,std::uint64_t wallNanos,std::uint64_t budgetNanos)noexcept {
        budget_.store(micros(budgetNanos),std::memory_order_release);
        if(wallNanos>budgetNanos)increment(missed_);
        constexpr auto limit=static_cast<std::uint64_t>(std::numeric_limits<std::uint32_t>::max())*1000;
        if(cpuNanos>limit || wallNanos>limit || budgetNanos>limit)increment(clipped_);
        auto& slot=slots_[next_];next_=(next_+1)%Capacity;
        if(++serial_==0)++serial_;
        slot.stamp.store(0,std::memory_order_release);
        slot.cpu.store(micros(cpuNanos),std::memory_order_release);
        slot.wall.store(micros(wallNanos),std::memory_order_release);
        slot.stamp.store(serial_,std::memory_order_release);increment(total_);
    }
    Snapshot snapshot()const noexcept {
        Snapshot result;std::array<std::uint32_t,Capacity> cpu{},wall{};
        for(const auto& slot:slots_) {
            const auto before=slot.stamp.load(std::memory_order_acquire);if(!before)continue;
            const auto c=slot.cpu.load(std::memory_order_acquire),w=slot.wall.load(std::memory_order_acquire);
            if(before!=slot.stamp.load(std::memory_order_acquire))continue;
            cpu[result.captured]=c;wall[result.captured++]=w;
        }
        const auto summarize=[&](auto& values) {
            Percentiles p;if(!result.captured)return p;
            std::sort(values.begin(),values.begin()+result.captured);
            const auto at=[&](std::uint32_t n,std::uint32_t d) {return static_cast<std::uint64_t>(values[(result.captured*n+d-1)/d-1])*1000;};
            return Percentiles{at(50,100),at(95,100),at(99,100),at(999,1000),static_cast<std::uint64_t>(values[result.captured-1])*1000};
        };
        result.cpu=summarize(cpu);result.wall=summarize(wall);
        result.total=total_.load(std::memory_order_acquire);result.missed=missed_.load(std::memory_order_acquire);
        result.lastBudgetMicros=budget_.load(std::memory_order_acquire);
        result.clockFailures=failed_.load(std::memory_order_acquire);result.clipped=clipped_.load(std::memory_order_acquire);
        for(auto counter:{result.total,result.missed,result.clockFailures,result.clipped})result.saturated|=counter==std::numeric_limits<std::uint32_t>::max();
        return result;
    }
};
}
