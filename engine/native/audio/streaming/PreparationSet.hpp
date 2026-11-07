#pragma once
#include "audio/streaming/SourcePageCache.hpp"
#include <bitset>
#include <algorithm>
#include <cmath>
namespace player2::audio {
// Bounded page reservations for the one Runtime, not another playback scheduler.
// Retiring reservations survive to frame end, letting new Voices acquire their own
// leases before queued/replaced plans release old preparation pages.
class PreparationSet {
public:
    struct Requirement {SourcePageCache* cache{};int asset{-1};SourceSamplePosition sample;double fraction{};std::optional<SourceSamplePosition> nextSample{};};
    static constexpr std::size_t capacity=128;
private:
    struct Key {SourcePageCache* cache{};int asset{-1};std::int64_t first{-1},next{-1};
        bool operator==(const Key& b)const noexcept{return cache==b.cache && asset==b.asset && first==b.first && next==b.next;}};
    struct LeaseSlot {Key key;Requirement requirement;StreamCursor cursor;};
    std::array<LeaseSlot,capacity> slots_{};std::bitset<capacity> occupied_,wanted_;
    static Key key(const Requirement& r)noexcept {
        const auto first=r.sample.value/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
        const auto last=r.cache->duration(r.asset).value-1;
        const auto next=(r.fraction==0 ? r.sample.value : (r.nextSample ? r.nextSample->value : std::min(r.sample.value+1,last)))/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
        return {r.cache,r.asset,first,next};
    }
public:
    struct FrameEnd {PreparationSet* set{};~FrameEnd(){if(set)set->trim();}};
    bool configure(const Requirement* input,std::size_t count)noexcept {
        if(count>capacity || (count && !input))return false;
        std::array<Key,capacity> unique{};std::array<std::size_t,capacity> indexes{};std::size_t size=0,needed=0;
        for(std::size_t n=0;n<count;++n) {
            const auto& r=input[n];
            if(!r.cache || r.asset<0 || r.sample.value<0 || r.sample.value>=r.cache->duration(r.asset).value || !std::isfinite(r.fraction) || r.fraction<0 || r.fraction>=1 || (r.nextSample && (r.nextSample->value<0 || r.nextSample->value>=r.cache->duration(r.asset).value)))return false;
            const auto k=key(r);std::size_t i=0;for(;i<size;++i)if(unique[i]==k)break;
            if(i<size)continue;
            unique[size]=k;indexes[size++]=n;
            bool found=false;for(std::size_t s=0;s<capacity;++s)if(occupied_[s] && slots_[s].key==k){found=true;break;}
            needed+=!found;
        }
        if(needed>capacity-occupied_.count())return false; // Old reservations remain intact on refusal.
        std::bitset<capacity> wanted;
        for(std::size_t n=0;n<size;++n) {
            std::size_t slot=0;for(;slot<capacity;++slot)if(occupied_[slot] && slots_[slot].key==unique[n])break;
            if(slot==capacity) {
                for(slot=0;slot<capacity && occupied_[slot];++slot){}
                auto& s=slots_[slot];s.key=unique[n];s.requirement=input[indexes[n]];s.cursor.bind(s.key.cache,s.key.asset);occupied_.set(slot);
            }
            wanted.set(slot);
        }
        wanted_=wanted;return true;
    }
    std::size_t size()const noexcept{return wanted_.count();}
    bool ready()noexcept {
        bool ready=true;
        for(std::size_t n=0;n<capacity;++n)if(wanted_[n]) {
            auto& s=slots_[n];if(!s.cursor.prepare(static_cast<double>(s.requirement.sample.value)+s.requirement.fraction,s.requirement.nextSample))ready=false;
        }
        return ready;
    }
    void retire()noexcept{wanted_.reset();}
    void trim()noexcept{for(std::size_t n=0;n<capacity;++n)if(occupied_[n] && !wanted_[n]){slots_[n].cursor.reset();occupied_.reset(n);}}
};
}
