#pragma once
#include "voice/Voice.hpp"
#include <array>
#include <vector>
namespace player2 {
// Eight-line orthogonal feedback network with stereo diffusion and HF damping.
// All buffers are allocated on preparation; reset only invalidates ring history.
class ReverbRuntime {
    class Line {
        std::vector<float> values_;
        std::size_t cursor_{},written_{},audible_{},length_{};
    public:
        void prepare(std::size_t length,std::size_t capacity=0) { values_.assign(std::max(length,capacity),0);length_=length;clear(); }
        void length(std::size_t value) noexcept { length_=std::clamp<std::size_t>(value,1,values_.size()); }
        float read() const noexcept { return written_<length_ ? 0 : values_[(cursor_+values_.size()-length_)%values_.size()]; }
        void write(float value) noexcept {
            if(written_==values_.size() && std::abs(values_[cursor_])>1e-9f) --audible_;
            values_[cursor_]=value;if(std::abs(value)>1e-9f) ++audible_;
            cursor_=(cursor_+1)%values_.size();written_=std::min(written_+1,values_.size());
        }
        float process(float value) noexcept { const float output=read();write(value);return output; }
        bool hasTail() const noexcept { return audible_>0; }
        void clear() noexcept { cursor_=written_=audible_=0; }
    };
    static constexpr std::array<int,8> lengths_{1493,1601,1747,1867,1999,2137,2269,2411};
    static constexpr std::array<int,8> rightSigns_{1,-1,1,-1,1,-1,1,-1};
    static constexpr double scale_=0.3535533905932737622;
    std::array<Line,8> network_;
    std::array<Line,2> preDelay_;
    std::array<std::array<Line,2>,2> diffusion_;
    std::array<double,8> damping_{};
    double feedback_{},dampingStep_{},wet_{}; std::uint64_t numericalCorrections_{};
    static std::size_t length(int base,int rate) noexcept { return std::max<std::size_t>(1,static_cast<std::size_t>(static_cast<double>(base)*rate/48000)); }
    static std::size_t preDelayLength(double ms,int rate) noexcept { return std::max<std::size_t>(1,static_cast<std::size_t>(ms*rate/1000)); }
    static float diffuse(Line& line,float input,std::uint64_t& corrections) noexcept {
        const double output=line.read()-0.5*input;line.write(audioSample(input+0.5*output,corrections));return audioSample(output,corrections);
    }
public:
    static std::size_t stateBytes(double delayMs,int rate,double maximumDelayMs=0) noexcept {
        std::size_t frames=2*preDelayLength(std::max(delayMs,maximumDelayMs),rate);
        for(auto value:lengths_)frames+=length(value,rate);
        frames+=2*(length(113,rate)+length(337,rate));return frames*sizeof(float);
    }
    void prepare(int rate,double delayMs,double feedback,double dampingHz,double wet,double maximumDelayMs=0) {
        feedback_=feedback;wet_=wet;numericalCorrections_=0;
        dampingStep_=-std::expm1(-6.2831853071795864769*std::min(dampingHz,rate*0.49)/rate);
        for(std::size_t n=0;n<network_.size();++n)network_[n].prepare(length(lengths_[n],rate));
        for(auto& line:preDelay_)line.prepare(preDelayLength(delayMs,rate),preDelayLength(std::max(delayMs,maximumDelayMs),rate));
        for(auto& channel:diffusion_) { channel[0].prepare(length(113,rate));channel[1].prepare(length(337,rate)); }
        clear();
    }
    void wet(double value) noexcept { wet_=std::clamp(value,0.0,1.0); }
    void configure(int rate,double delayMs,double feedback,double dampingHz,double wet) noexcept {
        feedback_=feedback;wet_=wet;
        dampingStep_=-std::expm1(-6.2831853071795864769*std::min(dampingHz,rate*0.49)/rate);
        for(auto& line:preDelay_)line.length(preDelayLength(delayMs,rate));
    }
    Stereo process(Stereo input) noexcept {
        Stereo diffused{preDelay_[0].process(input.left),preDelay_[1].process(input.right)};
        diffused.left=diffuse(diffusion_[0][1],diffuse(diffusion_[0][0],diffused.left,numericalCorrections_),numericalCorrections_);
        diffused.right=diffuse(diffusion_[1][1],diffuse(diffusion_[1][0],diffused.right,numericalCorrections_),numericalCorrections_);
        std::array<double,8> feedback{};double left=0,right=0;
        for(std::size_t n=0;n<network_.size();++n) {
            const auto value=network_[n].read();left+=value;right+=value*rightSigns_[n];
            damping_[n]+=dampingStep_*(value-damping_[n]);feedback[n]=damping_[n];
        }
        // Normalized Hadamard matrix has spectral norm 1: feedback < 1 decays.
        for(std::size_t width=1;width<8;width*=2) for(std::size_t group=0;group<8;group+=2*width) for(std::size_t n=0;n<width;++n) {
            const double a=feedback[group+n],b=feedback[group+n+width];feedback[group+n]=a+b;feedback[group+n+width]=a-b;
        }
        for(std::size_t n=0;n<network_.size();++n)network_[n].write(audioSample(scale_*(diffused.left+rightSigns_[n]*static_cast<double>(diffused.right)+feedback_*feedback[n]),numericalCorrections_));
        return {audioSample(input.left*(1-wet_)+scale_*left*wet_,numericalCorrections_),audioSample(input.right*(1-wet_)+scale_*right*wet_,numericalCorrections_)};
    }
    std::uint64_t numericalCorrections() const noexcept { return numericalCorrections_; }
    void resetDiagnostics() noexcept { numericalCorrections_=0; }
    bool hasTail() const noexcept {
        if(wet_==0)return false;
        for(const auto& line:preDelay_)if(line.hasTail())return true;
        for(const auto& channel:diffusion_)for(const auto& line:channel)if(line.hasTail())return true;
        for(std::size_t n=0;n<network_.size();++n)if(network_[n].hasTail() || (feedback_>0 && std::abs(damping_[n])>1e-9))return true;
        return false;
    }
    void clear() noexcept {
        for(auto& line:preDelay_)line.clear();
        for(auto& channel:diffusion_)for(auto& line:channel)line.clear();
        for(auto& line:network_)line.clear();
        damping_.fill(0);
    }
};
}
