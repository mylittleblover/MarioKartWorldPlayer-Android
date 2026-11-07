#pragma once
#include "api/PlaylistMonitorTypes.hpp"
#include <atomic>
#include <cstring>
namespace player2::api {
// One bounded slot per possible compiled index. Only changed histories are copied
// by the single publisher; queries participate in EngineHandle's coherent sequence.
struct PlaylistMonitorSlot {
    std::atomic<bool> valid{false},active{false};
    std::atomic<int> container{-1},mode{},policy{},avoid{},cursor{-1},chosen{-1},historySize{};
    std::array<std::atomic<int>,64> history{};
    std::array<std::atomic<std::uint32_t>,2> random{};
    std::uint64_t revision{}; // Publisher only; never inspected by query threads.
    // Static graph identities/weights are copied only on schema replacement.
    std::array<std::atomic<int>,64> candidates{};
    std::array<std::atomic<std::uint32_t>,128> weights{};
    std::array<std::atomic<std::uint32_t>,16> decisionWords{};
    std::atomic<int> reason{},effectiveAvoid{},count{};
    std::atomic<bool> recycled{false};std::atomic<std::int64_t> selectedAt{-1};
    static void storeBits(std::atomic<std::uint32_t>* target,std::uint64_t bits) noexcept {
        target[0].store(static_cast<std::uint32_t>(bits),std::memory_order_relaxed);target[1].store(static_cast<std::uint32_t>(bits>>32),std::memory_order_relaxed);
    }
    static std::uint64_t loadBits(const std::atomic<std::uint32_t>* source) noexcept {
        return std::uint64_t(source[0].load(std::memory_order_relaxed)) | (std::uint64_t(source[1].load(std::memory_order_relaxed))<<32);
    }
    static std::uint64_t doubleBits(double value) noexcept {std::uint64_t bits;std::memcpy(&bits,&value,sizeof(bits));return bits;}
    static double doubleValue(std::uint64_t bits) noexcept {double value;std::memcpy(&value,&bits,sizeof(value));return value;}
    void store(const PlaylistGroupSnapshot& value,std::uint64_t nextRevision,bool schemaDirty) noexcept {
        active.store(value.active,std::memory_order_relaxed);
        if(!schemaDirty && revision==nextRevision) return;
        container.store(value.container,std::memory_order_relaxed);
        mode.store(static_cast<int>(value.mode),std::memory_order_relaxed); policy.store(static_cast<int>(value.randomPolicy),std::memory_order_relaxed);
        avoid.store(value.avoidRepeat,std::memory_order_relaxed); cursor.store(value.cursor,std::memory_order_relaxed);
        chosen.store(value.chosenItem,std::memory_order_relaxed); historySize.store(value.historySize,std::memory_order_relaxed);
        random[0].store(static_cast<std::uint32_t>(value.randomState),std::memory_order_relaxed);
        random[1].store(static_cast<std::uint32_t>(value.randomState>>32),std::memory_order_relaxed);
        for(std::size_t n=0;n<history.size();++n) history[n].store(value.history[n],std::memory_order_relaxed);
        const auto& trace=value.decision;
        reason.store(static_cast<int>(trace.reason),std::memory_order_relaxed);effectiveAvoid.store(trace.effectiveAvoid,std::memory_order_relaxed);
        recycled.store(trace.recycledShuffle,std::memory_order_relaxed);selectedAt.store(trace.selectedAt.value,std::memory_order_relaxed);
        const std::array<std::uint64_t,8> words{trace.randomBefore,trace.eligible,trace.repeated,trace.shuffleUsed,doubleBits(trace.unit),doubleBits(trace.scale),doubleBits(trace.total),trace.selectedEpoch};
        for(std::size_t n=0;n<words.size();++n)storeBits(decisionWords.data()+n*2,words[n]);
        if(schemaDirty) {
            count.store(trace.count,std::memory_order_relaxed);
            for(std::size_t n=0;n<candidates.size();++n) {candidates[n].store(trace.candidates[n],std::memory_order_relaxed);storeBits(weights.data()+n*2,doubleBits(trace.weights[n]));}
        }
        revision=nextRevision; valid.store(true,std::memory_order_relaxed);
    }
    PlaylistGroupSnapshot load(int index) const noexcept {
        PlaylistGroupSnapshot value;
        if(!valid.load(std::memory_order_relaxed)) return value;
        value.index=index; value.container=container.load(std::memory_order_relaxed); value.active=active.load(std::memory_order_relaxed);
        value.mode=static_cast<PlaylistSelectionMode>(mode.load(std::memory_order_relaxed)); value.randomPolicy=static_cast<PlaylistRandomPolicy>(policy.load(std::memory_order_relaxed));
        value.avoidRepeat=avoid.load(std::memory_order_relaxed); value.cursor=cursor.load(std::memory_order_relaxed);
        value.chosenItem=chosen.load(std::memory_order_relaxed); value.historySize=historySize.load(std::memory_order_relaxed);
        value.randomState=std::uint64_t(random[0].load(std::memory_order_relaxed)) | (std::uint64_t(random[1].load(std::memory_order_relaxed))<<32);
        for(std::size_t n=0;n<history.size();++n) value.history[n]=history[n].load(std::memory_order_relaxed);
        value.decision=loadDecision(index);
        return value;
    }
    PlaylistDecisionSnapshot loadDecision(int index) const noexcept {
        PlaylistDecisionSnapshot value;
        if(!valid.load(std::memory_order_relaxed))return value;
        value.index=index;value.container=container.load(std::memory_order_relaxed);value.cursor=cursor.load(std::memory_order_relaxed);value.chosenItem=chosen.load(std::memory_order_relaxed);
        value.configuredAvoid=avoid.load(std::memory_order_relaxed);value.effectiveAvoid=effectiveAvoid.load(std::memory_order_relaxed);value.count=count.load(std::memory_order_relaxed);
        value.reason=static_cast<PlaylistDecisionReason>(reason.load(std::memory_order_relaxed));value.recycledShuffle=recycled.load(std::memory_order_relaxed);value.selectedAt={selectedAt.load(std::memory_order_relaxed)};
        value.randomBefore=loadBits(decisionWords.data());value.eligible=loadBits(decisionWords.data()+2);value.repeated=loadBits(decisionWords.data()+4);value.shuffleUsed=loadBits(decisionWords.data()+6);
        value.unit=doubleValue(loadBits(decisionWords.data()+8));value.scale=doubleValue(loadBits(decisionWords.data()+10));value.total=doubleValue(loadBits(decisionWords.data()+12));
        value.selectedEpoch=loadBits(decisionWords.data()+14);
        for(std::size_t n=0;n<candidates.size();++n) {value.candidates[n]=candidates[n].load(std::memory_order_relaxed);value.weights[n]=doubleValue(loadBits(weights.data()+n*2));}
        return value;
    }
};
static_assert(std::atomic<int>::is_always_lock_free && std::atomic<bool>::is_always_lock_free && std::atomic<std::uint32_t>::is_always_lock_free,"Playlist publication must be lock-free");
}
