#pragma once
#include <array>
#include <cstdint>

namespace player2::audio {
// SN76489 command subset. Integer chip-cycle remainder is carried across render
// blocks; output has no wall-clock dependence and no allocations.
class Psg {
    std::array<std::uint16_t,3> periods_{};
    std::array<std::uint16_t,3> counters_{{1,1,1}};
    std::array<bool,3> levels_{{true,true,true}};
    std::array<std::uint8_t,4> volumes_{{15,15,15,15}};
    std::uint8_t latched_{},noiseControl_{},stereo_{0xff};
    std::uint16_t noiseCounter_{16},lfsr_{0x8000};
    bool noiseClock_{};
    std::uint64_t cycleRemainder_{};
    static constexpr std::array<float,16> gains_{{1,0.794328235f,0.630957344f,0.501187234f,
        0.398107171f,0.316227766f,0.251188643f,0.199526232f,0.158489319f,0.125892541f,
        0.1f,0.0794328235f,0.0630957344f,0.0501187234f,0.0398107171f,0}};
    void tick() noexcept {
        for(std::size_t ch=0;ch<3;++ch) if(--counters_[ch]==0) {
            counters_[ch]=periods_[ch] ? periods_[ch] : 1024;
            levels_[ch]=!levels_[ch];
        }
        if(--noiseCounter_==0) {
            noiseCounter_=(noiseControl_&3)==3 ? (periods_[2] ? periods_[2] : 1024) : static_cast<std::uint16_t>(16u<<(noiseControl_&3));
            noiseClock_=!noiseClock_;
            if(noiseClock_) {
                const auto bit=(noiseControl_&4) ? ((lfsr_^(lfsr_>>3))&1) : (lfsr_&1);
                lfsr_=static_cast<std::uint16_t>((lfsr_>>1)|(bit<<15));
            }
        }
    }
public:
    void stereo(std::uint8_t value) noexcept { stereo_=value; }
    void write(std::uint8_t value) noexcept {
        const bool latch=(value&0x80)!=0;
        if(latch) latched_=(value>>4)&7;
        const auto channel=latched_>>1;
        if(latched_&1) volumes_[channel]=value&15;
        else if(channel<3) periods_[channel]=latch ? static_cast<std::uint16_t>((periods_[channel]&0x3f0)|(value&15)) :
            static_cast<std::uint16_t>((periods_[channel]&15)|((value&63)<<4));
        else { noiseControl_=value&7; lfsr_=0x8000; noiseClock_=false; }
    }
    std::array<float,2> sample(std::uint32_t clock) noexcept {
        cycleRemainder_+=clock;
        while(cycleRemainder_>=44100u*16u) { cycleRemainder_-=44100u*16u; tick(); }
        std::array<float,2> result{};
        for(int ch=0;ch<4;++ch) {
            const float signal=((ch<3 ? levels_[ch] : (lfsr_&1)!=0) ? 1.0f : -1.0f)*gains_[volumes_[ch]]*0.25f;
            if(stereo_&(1u<<(ch+4))) result[0]+=signal;
            if(stereo_&(1u<<ch)) result[1]+=signal;
        }
        return result;
    }
};
}
