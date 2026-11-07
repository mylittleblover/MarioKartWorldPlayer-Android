#pragma once
#include "music/Selection.hpp"
namespace player2 {
// Worker-preallocated copy-on-write selection table. Preparing a future choice
// must not consume the audible playlist's random stream or persistent cursor.
class SelectionPreview {
    const std::array<SelectionState,4096>& source_;
    std::array<SelectionState,4096> shadow_{};
    std::array<bool,4096> touched_{};
public:
    explicit SelectionPreview(const std::array<SelectionState,4096>& source):source_(source) {}
    void reset() noexcept { touched_.fill(false); }
    SelectionState& operator[](int index) noexcept {
        if(!touched_[index]) { shadow_[index]=source_[index]; touched_[index]=true; }
        return shadow_[index];
    }
    void commit(std::array<SelectionState,4096>& destination,std::int64_t at=-1,std::uint64_t epoch=0) noexcept {
        for(std::size_t n=0;n<touched_.size();++n) if(touched_[n]) {
            const auto revision=destination[n].revision+1;
            destination[n]=shadow_[n]; destination[n].revision=revision;
            if(at>=0 && destination[n].decision.kind!=SelectionDecisionKind::None) {destination[n].decision.at=at;destination[n].decision.epoch=epoch;}
        }
    }
};
}
