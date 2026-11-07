#pragma once
#include <cmath>
#include <cstdint>
#include <limits>
#include <stdexcept>

namespace player2 {
using Frame = std::int64_t;
struct Tempo {
    int sampleRate{48000}; double bpm{120}; int numerator{4}, denominator{4};
    double grid{1}, gridOffset{0};
    long double framesPerBeat() const noexcept {
        return static_cast<long double>(sampleRate)*60/bpm*4/denominator;
    }
    static Frame roundedFrame(long double value) noexcept {
        const auto maximum=static_cast<long double>(std::numeric_limits<Frame>::max());
        const auto minimum=static_cast<long double>(std::numeric_limits<Frame>::min());
        if(std::isnan(value)) return 0;
        if(value>=maximum) return std::numeric_limits<Frame>::max();
        if(value<=minimum) return std::numeric_limits<Frame>::min();
        return static_cast<Frame>(std::round(value));
    }
    Frame atBeat(long double beat) const noexcept { return roundedFrame(beat*framesPerBeat()); }
    long double beatAt(Frame frame) const noexcept { return frame/framesPerBeat(); }
    Frame quantize(Frame frame,long double quantum,long double offset,bool strict=true) const noexcept {
        if(!std::isfinite(quantum) || quantum<=0 || !std::isfinite(offset)) return frame;
        long double position=(frame-offset)/quantum;
        const long double nearest=std::round(position);
        // A rounded sample boundary can lie half a frame on either side of the exact beat.
        if(std::abs(position-nearest)<=0.5000001L/quantum) position=nearest;
        const long double cell=strict ? std::floor(position)+1 : std::ceil(position);
        return roundedFrame(cell*quantum+offset);
    }
    Frame nextBeat(Frame frame,bool strict=true) const noexcept { return quantize(frame,framesPerBeat(),0,strict); }
    Frame nextBar(Frame frame,bool strict=true) const noexcept { return quantize(frame,framesPerBeat()*numerator,0,strict); }
    Frame nextGrid(Frame frame,bool strict=true) const noexcept { return quantize(frame,framesPerBeat()/grid,framesPerBeat()*gridOffset,strict); }
    bool valid() const noexcept {
        return sampleRate>=8000 && sampleRate<=192000 && std::isfinite(bpm) && bpm>=10 && bpm<=400 &&
            numerator>=1 && numerator<=32 && (denominator==1 || denominator==2 || denominator==4 || denominator==8 || denominator==16 || denominator==32) &&
            std::isfinite(grid) && grid>0 && std::isfinite(gridOffset);
    }
};
enum class TransportStatus { Stopped=0,Playing=1,Paused=2 };
class SampleClock {
    Frame frame_{}; TransportStatus status_{TransportStatus::Stopped};
public:
    Frame frame() const noexcept { return frame_; }
    TransportStatus status() const noexcept { return status_; }
    bool playing() const noexcept { return status_==TransportStatus::Playing; }
    void play() noexcept { status_=TransportStatus::Playing; }
    void pause() noexcept { if(playing()) status_=TransportStatus::Paused; }
    void finish() noexcept { status_=TransportStatus::Stopped; }
    void stop() noexcept { frame_=0; status_=TransportStatus::Stopped; }
    void seek(Frame frame) noexcept { frame_=frame<0 ? 0 : frame; }
    void advance(Frame amount) noexcept {
        if(playing() && amount>0) {
            if(frame_<=std::numeric_limits<Frame>::max()-amount) frame_+=amount;
            else status_=TransportStatus::Stopped;
        }
    }
};
}
