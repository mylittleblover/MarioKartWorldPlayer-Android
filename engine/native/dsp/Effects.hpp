#pragma once
#include "compiled/MixerGraph.hpp"
#include "voice/Voice.hpp"
#include "dsp/Reverb.hpp"
#include "dsp/Biquad.hpp"
#include <array>
#include <vector>

namespace player2 {
class EffectRuntime {
    int kind_{},rate_{}; bool bypass_{}; std::uint64_t numericalCorrections_{};
    std::array<double,10> parameters_{};
    std::array<double,10> base_{};
    Biquad filter_;
    ReverbRuntime reverb_;
    std::vector<Stereo> delay_;
    std::size_t cursor_{},written_{},audibleSlots_{},delayLength_{};
    double envelope_{},attack_{},release_{};
public:
    static std::size_t stateBytes(const Effect& effect,int rate,double maximumDelayMs=0) noexcept {
        if(effect.kind==6) return ReverbRuntime::stateBytes(effect.parameters[7],rate,maximumDelayMs);
        if(effect.kind!=5) return 0;
        const double ms=std::clamp(std::max(effect.parameters[7],maximumDelayMs),0.01,2000.0);
        return static_cast<std::size_t>(std::max(1.0,ms*rate/1000))*sizeof(Stereo);
    }
    void prepare(const Effect& effect,int rate,double maximumDelayMs=0) {
        kind_=effect.kind;rate_=rate; bypass_=effect.bypass; base_=parameters_=effect.parameters; numericalCorrections_=0;
        filter_.prepare(rate,parameters_[0],parameters_[1],kind_==1,parameters_[2],kind_==2);
        const double attackMs=std::clamp(parameters_[5],0.01,10000.0),releaseMs=std::clamp(parameters_[6],0.01,10000.0);
        attack_=std::exp(-1.0/(attackMs*rate/1000)); release_=std::exp(-1.0/(releaseMs*rate/1000));
        if(kind_==6) reverb_.prepare(rate,parameters_[7],parameters_[8],parameters_[0],parameters_[9],maximumDelayMs);
        if(kind_==5) {
            const double delayMs=std::clamp(parameters_[7],0.01,2000.0);
            delayLength_=static_cast<std::size_t>(std::max(1.0,delayMs*rate/1000));
            delay_.assign(static_cast<std::size_t>(std::max(1.0,std::max(delayMs,maximumDelayMs)*rate/1000)),{});
        }
        clear();
    }
    void reverbWet(bool configured,double value) noexcept { if(kind_==6) reverb_.wet(configured ? value : parameters_[9]); }
    void configure(const std::array<double,10>& values) noexcept {
        if(values==parameters_)return;
        if(kind_<=2 && (values[0]!=parameters_[0] || values[1]!=parameters_[1] || values[2]!=parameters_[2]))
            filter_.prepare(rate_,values[0],values[1],kind_==1,values[2],kind_==2);
        if(values[5]!=parameters_[5])attack_=std::exp(-1.0/(values[5]*rate_/1000));
        if(values[6]!=parameters_[6])release_=std::exp(-1.0/(values[6]*rate_/1000));
        if(kind_==5)delayLength_=std::clamp<std::size_t>(static_cast<std::size_t>(std::max(1.0,values[7]*rate_/1000)),1,delay_.size());
        if(kind_==6)reverb_.configure(rate_,values[7],values[8],values[0],values[9]);
        parameters_=values;
    }
    const std::array<double,10>& parameters() const noexcept { return parameters_; }
    const std::array<double,10>& base() const noexcept { return base_; }
    int kind() const noexcept { return kind_; }
    bool bypass() const noexcept { return bypass_; }
    Stereo process(Stereo input) noexcept {
        input={audioSample(input.left,numericalCorrections_),audioSample(input.right,numericalCorrections_)};
        if(bypass_) return input;
        if(kind_==6) return reverb_.process(input);
        if(kind_<=2) return {filter_.process(input.left,0,numericalCorrections_),filter_.process(input.right,1,numericalCorrections_)};
        if(kind_==3 || kind_==4) {
            const double level=std::max(std::abs(input.left),std::abs(input.right));
            const double coefficient=level>envelope_ ? attack_ : release_;
            envelope_=level+(envelope_-level)*coefficient;
            const double threshold=dbGain(std::clamp(parameters_[3],-96.0,0.0));
            const double ratio=kind_==4 ? 1000.0 : std::clamp(parameters_[4],1.0,1000.0);
            const double detection=kind_==4 ? std::max(envelope_,level) : envelope_;
            const double gain=detection>threshold ? std::pow(detection/threshold,1/ratio-1) : 1;
            return {audioSample(input.left*gain,numericalCorrections_),audioSample(input.right*gain,numericalCorrections_)};
        }
        const auto delayed=written_<delayLength_ ? Stereo{} : delay_[(cursor_+delay_.size()-delayLength_)%delay_.size()];
        const double wet=std::clamp(parameters_[9],0.0,1.0),feedback=std::clamp(parameters_[8],0.0,0.99);
        auto audible=[](Stereo value) { return std::abs(value.left)>1e-9f || std::abs(value.right)>1e-9f; };
        if(written_==delay_.size() && audible(delay_[cursor_])) --audibleSlots_;
        delay_[cursor_]={audioSample(input.left+delayed.left*feedback,numericalCorrections_),audioSample(input.right+delayed.right*feedback,numericalCorrections_)};
        if(audible(delay_[cursor_])) ++audibleSlots_;
        cursor_=(cursor_+1)%delay_.size(); written_=std::min(written_+1,delay_.size());
        return {audioSample(input.left*(1-wet)+delayed.left*wet,numericalCorrections_),audioSample(input.right*(1-wet)+delayed.right*wet,numericalCorrections_)};
    }
    std::uint64_t numericalCorrections() const noexcept { auto count=numericalCorrections_;countAudioCorrections(count,reverb_.numericalCorrections());return count; }
    void resetDiagnostics() noexcept { numericalCorrections_=0;reverb_.resetDiagnostics(); }
    bool hasTail() const noexcept {
        if(bypass_) return false;
        if(kind_==6) return reverb_.hasTail();
        if(kind_<=2) return filter_.hasTail();
        return kind_==5 && parameters_[9]>0 && audibleSlots_>0;
    }
    void clear() noexcept { filter_.clear(); reverb_.clear(); cursor_=written_=audibleSlots_=0; envelope_=0; }
};
}
