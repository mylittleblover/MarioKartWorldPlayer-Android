#pragma once
#include "dsp/AudioMath.hpp"
#include <algorithm>
#include <array>
#include <cmath>
namespace player2 {
class Biquad {
    double b0_{1},b1_{},b2_{},a1_{},a2_{};
    std::array<double,2> z1_{},z2_{};
public:
    void prepare(double rate,double frequency,double q,bool high=false,double gainDb=0,bool peak=false) noexcept {
        frequency=std::clamp(frequency,10.0,rate*0.49); q=std::clamp(q,0.1,20.0);
        const double w=6.2831853071795864769*frequency/rate,c=std::cos(w),s=std::sin(w),alpha=s/(2*q);
        if(peak) {
            const double a=std::pow(10.0,std::clamp(gainDb,-60.0,60.0)/40),den=1+alpha/a;
            b0_=(1+alpha*a)/den; b1_=-2*c/den; b2_=(1-alpha*a)/den; a1_=-2*c/den; a2_=(1-alpha/a)/den;
        } else {
            const double den=1+alpha;
            b0_=(high ? 1+c : 1-c)/(2*den); b1_=(high ? -(1+c) : 1-c)/den; b2_=b0_;
            a1_=-2*c/den; a2_=(1-alpha)/den;
        }
    }
    float process(float input,int channel,std::uint64_t& corrections) noexcept {
        const double y=b0_*input+z1_[channel]; z1_[channel]=b1_*input-a1_*y+z2_[channel]; z2_[channel]=b2_*input-a2_*y;
        return audioSample(y,corrections);
    }
    bool hasTail() const noexcept {
        for(int channel=0;channel<2;++channel) if(std::abs(z1_[channel])>1e-9 || std::abs(z2_[channel])>1e-9) return true;
        return false;
    }
    void clear() noexcept { z1_.fill(0); z2_.fill(0); }
};
}
