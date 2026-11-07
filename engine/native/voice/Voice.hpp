#pragma once
#include "clock/MusicTime.hpp"
#include "compiled/MusicGraph.hpp"
#include "voice/ClipPlayback.hpp"
#include "api/SourceMonitorTypes.hpp"
#include "rtpc/Curve.hpp"
#include "dsp/AudioMath.hpp"
#include "dsp/MusicFilters.hpp"
#include "audio/streaming/SourcePageCache.hpp"
#include <algorithm>
#include <array>
#include <bitset>
#include <cmath>
#include <limits>
#include <vector>

namespace player2 {
struct Stereo { float left{},right{}; };
struct AudioBuffer {
    int index{-1},sampleRate{48000},channels{2}; std::vector<float> samples;
    audio::SourcePageCache* stream{};int streamedAsset{-1};audio::SourceSamplePosition streamedFrames;
    std::optional<audio::SourceLoopMetadata> sourceLoop;api::BoundSourceInfo sourceInfo;
    Frame frames() const noexcept { return stream ? streamedFrames.value : channels>0 ? static_cast<Frame>(samples.size()/channels) : 0; }
    bool valid() const noexcept {
        return sampleRate>=8000 && sampleRate<=192000 && (channels==1 || channels==2) &&
            (!sourceLoop || (sourceLoop->start.value>=0 && sourceLoop->end.value>sourceLoop->start.value && sourceLoop->end.value<=frames())) &&
            (stream ? streamedAsset>=0 && streamedFrames.value>0 : !samples.empty() && samples.size()%channels==0);
    }
    float at(Frame frame,int channel) const noexcept {
        if(frame<0 || frame>=frames()) return 0;
        return samples[static_cast<std::size_t>(frame)*channels+(channels==1 ? 0 : channel)];
    }
    Stereo sample(double position,audio::StreamCursor* cursor=nullptr,std::optional<audio::SourceSamplePosition> nextSample=std::nullopt) const noexcept {
        if(stream) {
            if(!cursor)return {};
            cursor->bind(stream,streamedAsset);const auto value=cursor->sample(position,nextSample);return {value[0],value[1]};
        }
        const auto frame=static_cast<Frame>(position); const double fraction=position-frame;
        const Frame next=nextSample ? nextSample->value : std::min(frame+1,frames()-1);
        // A convex interpolation of finite PCM must stay finite, including
        // opposite-sign float extremes whose subtraction overflows in float.
        auto interpolate=[&](int channel) {
            return static_cast<float>(static_cast<double>(at(frame,channel))*(1-fraction)+static_cast<double>(at(next,channel))*fraction);
        };
        return {interpolate(0),interpolate(1)};
    }
};
struct Voice {
    // Frozen music ownership survives Scene reuse while outgoing voices keep ringing.
    std::bitset<4096> musicOwners;
    bool active{},sourceEnded{},lastUnderrun{};
    // Decoded-linear repetition only. Graph Clip/Musical loops never infer this policy.
    bool sourceRepeats{};audio::SourceSamplePosition sourceRepeatBegin,sourceRepeatEnd; MusicFilters filters; audio::StreamCursor streamCursor; const AudioBuffer* buffer{}; std::uint64_t numericalCorrections{};
    int segment{-1},track{-1},clip{-1},bus{-1},instance{-1},owner{-1}; Frame startFrame{},sourceOffset{},sourceLength{};
    std::optional<CompiledClipLoop> clipLoop;bool clipLoopReleased{};std::int64_t releasedWraps{};long double sourceProgress{},sourceProgressCorrection{};
    double sourcePosition{},timelinePosition{},speed{1},timelineRate{1},gain{1},pan{};
    Frame fadeIn{},fadeOut{},fadeStart{-1},fadeEnd{-1}; Shape fadeShape{Shape::Linear};
    Frame entranceStart{-1},entranceEnd{-1}; Shape entranceShape{Shape::Linear};
    bool exitFade{},entryFade{};
    Frame stopFrame{-1};
    // Graph owns this immutable Segment until all of its Voices retire.
    const Segment* envelopeSegment{}; const Clip* envelopeClip{}; Frame segmentClipStart{};
    void begin(const AudioBuffer* data,int rate,double catchup=0) noexcept {
        streamCursor.reset(); sourceRepeats=false;clipLoopReleased=false;releasedWraps=0; buffer=data; active=data && data->valid() && (!clipLoop || validClipLoop(*clipLoop,sourceOffset,sourceLength)); sourceEnded=false; filters.clear(); timelinePosition=catchup;
        sourceProgress=static_cast<long double>(catchup)*speed/timelineRate*(data ? data->sampleRate : rate)/rate;
        sourceProgressCorrection=0;
        sourcePosition=clipLoop ? ClipPlayback::position(*clipLoop,sourceOffset,sourceProgress) : static_cast<double>(sourceOffset+sourceProgress);
    }
    void repeatSource(bool enabled) noexcept {
        sourceRepeats=enabled && buffer && buffer->valid();
        if(sourceRepeats) {
            sourceRepeatBegin=buffer->sourceLoop ? buffer->sourceLoop->start : audio::SourceSamplePosition{0};
            sourceRepeatEnd=buffer->sourceLoop ? buffer->sourceLoop->end : audio::SourceSamplePosition{buffer->frames()};
        }
    }
    void repeatRange(bool enabled,const std::optional<audio::SourceLoopMetadata>& range) noexcept {
        repeatSource(enabled);
        if(sourceRepeats && range) {sourceRepeatBegin=range->start;sourceRepeatEnd=range->end;}
    }
    void wrapSource() noexcept {
        if(sourceRepeats && sourceOffset+sourceProgress>=sourceRepeatEnd.value) {
            sourceProgress=sourceRepeatBegin.value+std::fmod(sourceOffset+sourceProgress-sourceRepeatEnd.value,
                static_cast<long double>(sourceRepeatEnd.value-sourceRepeatBegin.value))-sourceOffset;
            sourceProgressCorrection=0;sourcePosition=static_cast<double>(sourceOffset+sourceProgress);
        }
    }
    // Depart an infinite authored loop through its current region and suffix;
    // retain ordinary source/filter tails instead of leaking an endless old Voice.
    void releaseClipLoop() noexcept {
        if(!clipLoop || clipLoop->count>=0 || clipLoopReleased)return;
        releasedWraps=ClipPlayback::wraps(*clipLoop,sourceOffset,sourceProgress);clipLoopReleased=true;
        sourceProgress=sourcePosition-sourceOffset;
        sourceProgressCorrection=0;
    }
    // Cut the former source on the same Transport frame while retaining only
    // already-generated per-Voice filter state. This is not natural source tail.
    void retireSource() noexcept {
        sourceEnded=true;sourceRepeats=false;streamCursor.reset();
        if(!filters.hasTail())active=false;
    }
    Stereo next(Frame frame,int rate,double propertyGain=1,double propertySpeed=1,double propertyPan=0,double propertyTimelineRate=-1,bool lowEnabled=false,double lowHz=20000,bool highEnabled=false,double highHz=20,bool forceUnderrun=false) noexcept {
        lastUnderrun=false;
        if(!active || !buffer || frame<startFrame) return {};
        if(stopFrame>=0 && frame>=stopFrame) { active=false; streamCursor.reset(); return {}; }
        if(propertyTimelineRate>0 && std::isfinite(propertyTimelineRate))timelineRate=propertyTimelineRate;
        double segmentGain=1;
        const auto automate=[&](const std::vector<PreparedTimelineEnvelope>& curves,long double local) {
            const Frame at=local>=static_cast<long double>(std::numeric_limits<Frame>::max()) ? std::numeric_limits<Frame>::max() : static_cast<Frame>(local);
            for(const auto& curve:curves)if(const auto value=curve.valueAt(at)) {
                switch(curve.target()) {
                    case EnvelopeTarget::VolumeDb:segmentGain*=dbGain(*value);break;
                    case EnvelopeTarget::LowPassHz:lowHz=lowEnabled ? std::min(lowHz,*value) : *value;lowEnabled=true;break;
                    case EnvelopeTarget::HighPassHz:highHz=highEnabled ? std::max(highHz,*value) : *value;highEnabled=true;break;
                }
            }
        };
        if(envelopeSegment)automate(envelopeSegment->envelopes,static_cast<long double>(timelinePosition)+segmentClipStart);
        if(envelopeClip)automate(envelopeClip->envelopes,timelinePosition);
        filters.configure(rate,lowEnabled,lowHz,highEnabled,highHz);
        const long double step=static_cast<long double>(speed)*propertySpeed*buffer->sampleRate/rate;
        const double end=static_cast<double>(sourceOffset+sourceLength);
        if(!std::isfinite(sourcePosition) || step<=0) { active=false; streamCursor.reset(); return {}; }
        if(clipLoop && !clipLoopReleased && !sourceEnded)sourcePosition=ClipPlayback::position(*clipLoop,sourceOffset,sourceProgress);
        wrapSource();
        if(clipLoop && !clipLoopReleased && !sourceEnded)sourceEnded=ClipPlayback::remaining(*clipLoop,sourceLength,sourceProgress)<=0;
        if(!sourceRepeats)sourceEnded|=sourcePosition>=end || sourcePosition>=buffer->frames();
        double envelope=1;
        if(fadeIn>0) envelope*=std::clamp(timelinePosition/fadeIn,0.0,1.0);
        if(fadeOut>0) {
            if(clipLoop && !clipLoopReleased)envelope*=static_cast<double>(std::clamp(ClipPlayback::remaining(*clipLoop,sourceLength,sourceProgress)/step*timelineRate/fadeOut,0.0L,1.0L));
            else envelope*=static_cast<double>(std::clamp((end-sourcePosition)/step*timelineRate/fadeOut,0.0L,1.0L));
        }
        if((entryFade || entranceStart>=0) && frame<entranceEnd) envelope*=shapeValue((static_cast<long double>(frame)-entranceStart)/std::max<long double>(1,static_cast<long double>(entranceEnd)-entranceStart),entranceShape);
        if((exitFade || fadeStart>=0) && frame>=fadeStart) {
            if(frame>=fadeEnd) { active=false; streamCursor.reset(); return {}; }
            envelope*=1-shapeValue((static_cast<long double>(frame)-fadeStart)/std::max<long double>(1,static_cast<long double>(fadeEnd)-fadeStart),fadeShape);
        }
        Stereo result;
        if(!sourceEnded) {
            if(forceUnderrun){streamCursor.reset();lastUnderrun=true;}
            else {
                std::optional<audio::SourceSamplePosition> neighbor;
                if(clipLoop && !clipLoopReleased)neighbor=audio::SourceSamplePosition{std::min({buffer->frames()-1,sourceOffset+sourceLength-1,static_cast<std::int64_t>(ClipPlayback::position(*clipLoop,sourceOffset,std::floor(sourceProgress)+1))})};
                else if(sourceRepeats && std::floor(sourcePosition)+1>=sourceRepeatEnd.value)neighbor=sourceRepeatBegin;
                else if(!sourceRepeats && std::floor(sourcePosition)+1>=end)neighbor=audio::SourceSamplePosition{std::min(buffer->frames()-1,sourceOffset+sourceLength-1)};
                result=buffer->sample(sourcePosition,&streamCursor,neighbor);lastUnderrun=buffer->stream && streamCursor.missing();
            }
        }
        const double p=std::clamp(pan+propertyPan,-1.0,1.0),g=gain*propertyGain*envelope;
        result.left=audioSample(result.left*g*(p>0 ? 1-p : 1),numericalCorrections);
        result.right=audioSample(result.right*g*(p<0 ? 1+p : 1),numericalCorrections);
        result.left=filters.process(result.left,0,numericalCorrections);
        result.right=filters.process(result.right,1,numericalCorrections);
        // Time automation trims the audible Voice, including accumulated filter
        // state after source end. Legacy gain and pan keep their existing order.
        result.left=audioSample(result.left*segmentGain,numericalCorrections);
        result.right=audioSample(result.right*segmentGain,numericalCorrections);
        if(!sourceEnded) {
            // Retain the source phase independently of the rounded interpolation
            // coordinate. Repeated double additions can extend a 44.1 kHz Clip
            // by a Transport sample and shift subsequent loop boundaries.
            const auto increment=step-sourceProgressCorrection;
            const auto progress=sourceProgress+increment;
            sourceProgressCorrection=(progress-sourceProgress)-increment;sourceProgress=progress;
            sourcePosition=clipLoop && !clipLoopReleased ? ClipPlayback::position(*clipLoop,sourceOffset,sourceProgress) : static_cast<double>(sourceOffset+sourceProgress);
            timelinePosition+=timelineRate;
        } else if((envelopeSegment && !envelopeSegment->envelopes.empty()) || (envelopeClip && !envelopeClip->envelopes.empty())) {
            // A departing Voice keeps its music time through the filter tail.
            timelinePosition+=timelineRate;
        }
        if(clipLoop && !clipLoopReleased && !sourceEnded)sourceEnded=ClipPlayback::remaining(*clipLoop,sourceLength,sourceProgress)<=0;
        wrapSource();
        if(!sourceRepeats)sourceEnded|=sourcePosition>=end || sourcePosition>=buffer->frames();
        if(sourceEnded) {streamCursor.reset();if(!filters.hasTail())active=false;}
        return result;
    }
};
class VoiceManager {
    std::array<Voice,128> voices_{}; std::uint64_t dropped_{};
public:
    Voice* add(const Voice& voice) noexcept { for(auto& slot:voices_) if(!slot.active) { slot=voice; return &slot; } ++dropped_; return nullptr; }
    Voice* add(Voice&& voice) noexcept {for(auto& slot:voices_)if(!slot.active){slot=std::move(voice);return &slot;}++dropped_;return nullptr;}
    void clear() noexcept { for(auto& voice:voices_) {voice.active=false;voice.streamCursor.reset();} }
    void releaseInactive() noexcept {for(auto& voice:voices_)if(!voice.active)voice.streamCursor.reset();}
    std::size_t count() const noexcept { std::size_t n=0; for(const auto& voice:voices_) n+=voice.active; return n; }
    std::uint64_t dropped() const noexcept { return dropped_; }
    void restoreDropped(std::uint64_t value) noexcept { dropped_=value; }
    std::array<Voice,128>& slots() noexcept { return voices_; }
    const std::array<Voice,128>& slots() const noexcept { return voices_; }
    void fadeInstance(int instance,Frame start,Frame end,Shape shape) noexcept {
        for(auto& voice:voices_) if(voice.active && voice.instance==instance) { voice.fadeStart=start; voice.fadeEnd=end; voice.fadeShape=shape; }
    }
};
}
