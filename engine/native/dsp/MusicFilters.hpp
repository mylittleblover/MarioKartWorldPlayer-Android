#pragma once
#include "dsp/Biquad.hpp"
namespace player2 {
// Implicit properties are neutral. Explicit defaults and RTPCs activate filters.
// Coefficients and state are fixed-size; changing a frequency retains filter history.
class MusicFilters {
    Biquad low_,high_;
    double lowHz_{},highHz_{}; int rate_{};
    bool lowEnabled_{},highEnabled_{};
public:
    void configure(int rate,bool lowEnabled,double lowHz,bool highEnabled,double highHz) noexcept {
        lowHz=lowEnabled ? std::clamp(lowHz,10.0,rate*0.49) : 0;
        highHz=highEnabled ? std::clamp(highHz,10.0,rate*0.49) : 0;
        if(!lowEnabled) low_.clear();
        else if(!lowEnabled_ || lowHz!=lowHz_ || rate!=rate_) low_.prepare(rate,lowHz,0.7071067811865476);
        if(!highEnabled) high_.clear();
        else if(!highEnabled_ || highHz!=highHz_ || rate!=rate_) high_.prepare(rate,highHz,0.7071067811865476,true);
        lowEnabled_=lowEnabled;highEnabled_=highEnabled;lowHz_=lowHz;highHz_=highHz;rate_=rate;
    }
    float process(float input,int channel,std::uint64_t& corrections) noexcept {
        if(lowEnabled_) input=low_.process(input,channel,corrections);
        if(highEnabled_) input=high_.process(input,channel,corrections);
        return input;
    }
    bool lowEnabled() const noexcept { return lowEnabled_; }
    bool highEnabled() const noexcept { return highEnabled_; }
    double lowHz() const noexcept { return lowHz_; }
    double highHz() const noexcept { return highHz_; }
    int tailCount() const noexcept { return (lowEnabled_ && low_.hasTail())+(highEnabled_ && high_.hasTail()); }
    bool hasTail() const noexcept { return tailCount()!=0; }
    void clear() noexcept { low_.clear();high_.clear(); }
};
}
