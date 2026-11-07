#include "runtime/EngineRuntime.hpp"
#include <limits>
namespace player2 {
double EngineRuntime::automaticGoal()noexcept {
    const auto& config=*automatic_;double value=config.low;
    for(int draw=0;draw<4;++draw) {
        const auto unit=automaticRandom_.unit();value=(1-unit)*config.low+unit*config.high;
        if(value!=automaticState_.goal || config.low==config.high)return value;
    }
    return automaticState_.goal!=config.low ? config.low : config.high;
}
api::EngineStatus EngineRuntime::startAutomatic(int parameter)noexcept {
    const auto* config=graph_ && graph_->hasAutomatic ? &graph_->automatic : nullptr;
    auto fail=[&](api::EngineStatus result){if(!automatic_){automaticState_={};automaticState_.parameter=parameter==-1 && config ? config->parameter : parameter;automaticState_.phase=api::AutomaticPhase::StartFailed;automaticState_.requestedAt={clock_.frame()};automaticState_.result=result;}return result;};
    if(!config || (parameter!=-1 && parameter!=config->parameter))return fail(api::EngineStatus::InvalidRequest);
    if(!foreground_ || !foreground_->segment || clock_.status()==TransportStatus::Stopped)return fail(api::EngineStatus::InvalidRequest);
    // At most four musical cycles per sample, even at max supported meter/BPM/speed.
    const auto minimumBeatFrames=static_cast<long double>(rate_)*60/400*4/32;
    if(!std::isfinite(config->cycleBeats) || config->cycleBeats*minimumBeatFrames<1)return fail(api::EngineStatus::ResourceLimit);
    automatic_=config;automaticState_={};automaticState_.parameter=config->parameter;automaticState_.phase=api::AutomaticPhase::Running;
    automaticState_.requestedAt={clock_.frame()};automaticState_.start=parameters_->snapshot(config->parameter).target;
    automaticState_.cycleBeats=config->cycleBeats;automaticState_.low=config->low;automaticState_.high=config->high;
    automaticRandom_.reset(config->seed);automaticState_.goal=std::numeric_limits<double>::quiet_NaN();automaticState_.goal=automaticGoal();
    automaticBeats_=0;automaticFrame_=automaticClockFrame_=-1;automaticTempo_=graph_->node(foreground_->segment->index)->tempo;automaticSpeed_=1;return api::EngineStatus::Ok;
}
void EngineRuntime::stopAutomatic(bool restore,api::AutomaticPhase phase)noexcept {
    if(!automatic_)return;
    const auto* config=automatic_;automatic_=nullptr;automaticState_.phase=phase;
    if(restore && config->returnDefault)parameters_->setParameter(config->parameter,parameters_->snapshot(config->parameter).defaultValue);
}
void EngineRuntime::advanceAutomatic()noexcept {
    if(!automatic_ || automaticFrame_==clock_.frame())return;
    automaticFrame_=clock_.frame();const auto& config=*automatic_;
    if(foreground_ && foreground_->segment) {
        automaticTempo_=graph_->node(foreground_->segment->index)->tempo;automaticState_.clockSegment=foreground_->segment->index;
        automaticSpeed_=parameters_->property(foreground_->segment->index,1);
    }else automaticState_.clockSegment=-1;
    if(config.returnDefault && performance_ && performance_->endFrame>=0 && automaticState_.phase==api::AutomaticPhase::Running) {
        auto end=performance_->endFrame;
        if(performance_->end==EndBehavior::Fade && performance_->endFade)end=addFrame(addFrame(end,performance_->endFade->offset.value),performance_->endFade->duration.value);
        const auto remaining=static_cast<long double>(end)-clock_.frame();
        if(remaining>0 && remaining*automaticSpeed_/automaticTempo_.framesPerBeat()<=config.cycleBeats) {
            automaticState_.phase=api::AutomaticPhase::Returning;automaticState_.returnStart={clock_.frame()};automaticState_.returnEnd={end};
            automaticState_.start=parameters_->snapshot(config.parameter).target;automaticState_.goal=parameters_->snapshot(config.parameter).defaultValue;
        }
    }
    if(automaticState_.phase==api::AutomaticPhase::Returning) {
        automaticState_.progress=std::clamp(static_cast<double>(clock_.frame()-automaticState_.returnStart.value)/(automaticState_.returnEnd.value-automaticState_.returnStart.value),0.0,1.0);
    }else {
        // Progress is advanced once per Transport sample, never by wall clock or block.
        const auto tolerance=config.cycleBeats*std::numeric_limits<double>::epsilon()*8;
        for(int cycle=0;automaticBeats_>=config.cycleBeats-tolerance && cycle<4;++cycle) {
            automaticBeats_=std::max(0.0L,automaticBeats_-config.cycleBeats);automaticState_.start=automaticState_.goal;automaticState_.goal=automaticGoal();
            if(automaticState_.cycles<std::numeric_limits<std::int64_t>::max())++automaticState_.cycles;
        }
        automaticState_.progress=std::clamp(static_cast<double>(automaticBeats_/config.cycleBeats),0.0,1.0);
    }
    const auto fraction=shapeValue(automaticState_.progress,Shape::SCurve);
    parameters_->setParameter(config.parameter,(1-fraction)*automaticState_.start+fraction*automaticState_.goal);
}
void EngineRuntime::advanceAutomaticClock(const Scene& scene)noexcept {
    if(!automatic_ || &scene!=foreground_ || !scene.segment || automaticClockFrame_==clock_.frame())return;
    automaticClockFrame_=clock_.frame();automaticTempo_=graph_->node(scene.segment->index)->tempo;
    automaticSpeed_=parameters_->property(scene.segment->index,1);automaticState_.clockSegment=scene.segment->index;
    if(automaticState_.phase==api::AutomaticPhase::Running)automaticBeats_+=automaticSpeed_/automaticTempo_.framesPerBeat();
}
}
