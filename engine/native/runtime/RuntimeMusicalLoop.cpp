#include "runtime/EngineRuntime.hpp"
namespace player2 {
api::EngineStatus EngineRuntime::startMusicalLoop(int playlist)noexcept {
    const auto requested=clock_.frame();
    if(!graph_ || !graph_->hasMusicalLoop || graph_->musicalLoop.playlist<0 || playlist!=graph_->musicalLoop.playlist)
        return api::EngineStatus::InvalidRequest;
    std::array<bool,4096> visited{};const auto ready=targetReady(playlist,visited);
    if(ready!=api::EngineStatus::Ok) {
        if(!musicalLoopActive_){musicalLoopState_={};musicalLoopState_.playlist=playlist;musicalLoopState_.requestedAt={requested};musicalLoopState_.phase=api::MusicalLoopPhase::StartFailed;musicalLoopState_.result=ready;}
        return ready;
    }
    // Explicit profile restart begins a new transport epoch; Game Sync remains
    // unified and is not reset into a separate profile-specific state machine.
    resetStopped();const auto result=startSegment(playlist);
    musicalLoopState_={};musicalLoopState_.playlist=playlist;musicalLoopState_.requestedAt={requested};musicalLoopState_.result=result;
    musicalLoopActive_=result==api::EngineStatus::Ok;
    musicalLoopState_.phase=musicalLoopActive_ ? api::MusicalLoopPhase::Playing : api::MusicalLoopPhase::StartFailed;
    return result;
}
api::EngineStatus EngineRuntime::stopMusicalLoop(int playlist,double fadeBeats)noexcept {
    if(!musicalLoopActive_ || musicalLoopState_.phase!=api::MusicalLoopPhase::Playing || !graph_ || playlist!=graph_->musicalLoop.playlist || !std::isfinite(fadeBeats) || fadeBeats<0)
        return api::EngineStatus::InvalidRequest;
    const auto& profile=graph_->musicalLoop;api::EngineStatus result;
    if(profile.stop==EndBehavior::Ending) {
        const bool paused=clock_.status()==TransportStatus::Paused;
        result=requestPlay(profile.ending);
        if(result==api::EngineStatus::Ok) {
            // Waiting transitions retain their source scene and its ownership,
            // but cannot advance to another profile item before departure.
            if(transition_.active && transition_.source && transition_.source->object==playlist) {
                if(!transition_.cueSearch && !transition_.forecast)transition_.source->playlist.clear();
            }
            else stopObject(playlist,0,true);
            musicalLoopState_.phase=api::MusicalLoopPhase::Ending;
            if(paused)clock_.pause();
        }
    } else {
        if(profile.stop==EndBehavior::Fade && fadeBeats<=0)return api::EngineStatus::InvalidRequest;
        result=stopObject(playlist,profile.stop==EndBehavior::Fade ? fadeBeats : 0,profile.stop==EndBehavior::Natural);
        if(result==api::EngineStatus::Ok)musicalLoopState_.phase=api::MusicalLoopPhase::Stopping;
    }
    musicalLoopState_.result=result;return result;
}
void EngineRuntime::advanceMusicalLoop()noexcept {
    if(!musicalLoopActive_)return;
    if(musicalLoopState_.phase==api::MusicalLoopPhase::Playing && foreground_ && foreground_->object!=musicalLoopState_.playlist) {
        musicalLoopActive_=false;musicalLoopState_.phase=api::MusicalLoopPhase::Overridden;return;
    }
    bool active=transition_.active;
    for(const auto& scene:scenes_)active|=scene.active && scene.object==musicalLoopState_.playlist;
    if(musicalLoopState_.phase==api::MusicalLoopPhase::Ending && foreground_)active|=foreground_->active;
    for(const auto& voice:voices_.slots())active|=voice.active && voice.musicOwners.test(musicalLoopState_.playlist);
    if(musicalLoopState_.phase==api::MusicalLoopPhase::Ending && foreground_)
        for(const auto& voice:voices_.slots())active|=voice.active && voice.owner==foreground_->owner;
    if(!active && (!mixer_ || !mixer_->hasTail())){musicalLoopActive_=false;musicalLoopState_.phase=api::MusicalLoopPhase::Finished;}
}
}
