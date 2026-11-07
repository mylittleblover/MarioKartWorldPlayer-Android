#pragma once
#include "runtime/EngineRuntime.hpp"
namespace player2 {
// Engine owns rate conversion and coordinate carry; Platform only supplies device rate/frames.
class DeviceRateAdapter {
    Stereo current_,next_;
    std::uint64_t phase_{};
    int deviceRate_{};
    bool initialized_{};
    audio::DeviceFrame position_;
public:
    void reset() noexcept { current_=next_={}; phase_=0; deviceRate_=0; initialized_=false; position_={}; }
    void render(EngineRuntime& runtime,float* output,std::size_t frames,int rate) noexcept {
        const int transportRate=runtime.sampleRate();
        if(rate<8000 || rate>192000 || transportRate<8000 || transportRate>192000) { std::fill(output,output+frames*2,0); return; }
        if(!initialized_) {
            float first[4]{};runtime.render(first,2);
            current_={first[0],first[1]};next_={first[2],first[3]};initialized_=true;
        }
        if(deviceRate_ && deviceRate_!=rate) phase_=phase_*rate/deviceRate_;
        deviceRate_=rate;
        // Render exactly the same transport samples as the scalar adapter, in
        // bounded blocks. No extra read-ahead, allocation or decoder work here.
        // 128 device frames need at most 3,072 transport frames (192 kHz / 8 kHz).
        constexpr std::size_t DeviceBlock=128,TransportBlock=DeviceBlock*192000/8000;
        std::array<float,TransportBlock*2> samples;
        for(std::size_t offset=0;offset<frames;) {
            const auto count=std::min(DeviceBlock,frames-offset);
            const auto needed=static_cast<std::size_t>((phase_+count*static_cast<std::uint64_t>(transportRate))/rate);
            if(needed)runtime.render(samples.data(),needed);
            std::size_t cursor=0;
            for(std::size_t n=offset;n<offset+count;++n) {
                const double fraction=static_cast<double>(phase_)/rate;
                const auto l=static_cast<float>(static_cast<double>(current_.left)*(1-fraction)+static_cast<double>(next_.left)*fraction);
                const auto r=static_cast<float>(static_cast<double>(current_.right)*(1-fraction)+static_cast<double>(next_.right)*fraction);
                output[n*2]=std::isfinite(l) ? std::clamp(l,-1.0f,1.0f) : 0;
                output[n*2+1]=std::isfinite(r) ? std::clamp(r,-1.0f,1.0f) : 0;
                phase_+=transportRate;
                while(phase_>=static_cast<std::uint64_t>(rate)) {
                    phase_-=rate;current_=next_;next_={samples[cursor*2],samples[cursor*2+1]};++cursor;
                }
            }
            offset+=count;
        }
        if(frames<=static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()-position_.value)) position_.value+=frames;
    }
    audio::DeviceFrame position() const noexcept { return position_; }
};
}
