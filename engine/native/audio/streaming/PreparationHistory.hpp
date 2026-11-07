#pragma once
#include "api/EngineTypes.hpp"
#include <array>
#include <limits>
#include <optional>
namespace player2 {
// One terminal readiness decision per scheduled preparation. No authored state,
// source pointer or worker wall-clock time: replay keys are epoch + plan generation.
class PreparationHistory {
public:
    struct Key {std::uint64_t epoch{},generation{};audio::TransportFrame at;};
    struct Record {Key key;api::EngineStatus result{};};
    static constexpr std::size_t capacity=8192;
private:
    static_assert(capacity<=std::numeric_limits<std::uint16_t>::max() && (capacity*2&(capacity*2-1))==0);
    // Append by outcome time; a bounded half-full index retains exact plan identity.
    std::array<Record,capacity> records_{};std::size_t size_{};
    std::array<std::uint16_t,capacity*2> plans_{};
    bool complete_{true};std::uint64_t dropped_{};
    static bool beforeTime(Key a,Key b)noexcept{return a.epoch<b.epoch || (a.epoch==b.epoch && a.at.value<b.at.value);}
    static std::size_t hash(Key key)noexcept {
        auto bits=key.generation^(key.epoch*0x9e3779b97f4a7c15ULL);bits^=bits>>33;bits*=0xff51afd7ed558ccdULL;bits^=bits>>33;
        return static_cast<std::size_t>(bits)&(capacity*2-1);
    }
    const Record* find(Key key)const noexcept {
        auto slot=hash(key);
        for(std::size_t n=0;n<plans_.size();++n,slot=(slot+1)&(capacity*2-1)) {
            const auto index=plans_[slot];if(!index)return nullptr;
            const auto& record=records_[index-1];if(record.key.epoch==key.epoch && record.key.generation==key.generation)return &record;
        }
        return nullptr;
    }
    void index(std::size_t record)noexcept {
        auto slot=hash(records_[record].key);
        for(std::size_t n=0;n<plans_.size();++n,slot=(slot+1)&(capacity*2-1))if(!plans_[slot]){plans_[slot]=static_cast<std::uint16_t>(record+1);return;}
    }
    void lose()noexcept{complete_=false;if(dropped_<static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))++dropped_;}
public:
    bool complete()const noexcept{return complete_;}
    std::size_t size()const noexcept{return size_;}
    std::uint64_t dropped()const noexcept{return dropped_;}
    void rewind()noexcept{}
    std::optional<api::EngineStatus> expected(Key key)noexcept {
        if(const auto* record=find(key)) {
            if(record->key.at.value!=key.at.value){lose();return api::EngineStatus::ResourceLimit;}
            return record->result;
        }
        return std::nullopt;
    }
    // A new input at this frame replaces outcomes from this frame onward only.
    void branch(std::uint64_t epoch,audio::TransportFrame at)noexcept {
        const Key point{epoch,0,at};std::size_t keep=0;
        while(keep<size_ && beforeTime(records_[keep].key,point))++keep;
        if(keep==size_)return;
        size_=keep;plans_.fill(0);for(std::size_t n=0;n<size_;++n)index(n);
        // Lost evidence cannot be reconstructed by merely truncating known records.
    }
    bool record(Key key,api::EngineStatus result)noexcept {
        if(!complete_)return false;
        if(key.at.value<0 || key.generation==0 || (result!=api::EngineStatus::Ok && result!=api::EngineStatus::DeadlineMiss && result!=api::EngineStatus::ResourceLimit)){lose();return false;}
        if(size_ && records_[size_-1].key.epoch==key.epoch && records_[size_-1].key.generation==key.generation) {
            if(records_[size_-1].key.at.value==key.at.value && records_[size_-1].result==result)return true;
            lose();return false;
        }
        if(const auto prior=expected(key)) {
            if(complete_ && *prior==result)return true;
            lose();return false;
        }
        if(size_ && beforeTime(key,records_[size_-1].key)){lose();return false;}
        if(size_==capacity){lose();return false;}
        records_[size_]={key,result};index(size_++);return true;
    }
};
}
