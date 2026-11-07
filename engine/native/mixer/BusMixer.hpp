#pragma once
#include "compiled/Graph.hpp"
#include "dsp/Effects.hpp"
#include "api/EngineTypes.hpp"
#include "api/EffectMonitorTypes.hpp"
#include <vector>
#include <stdexcept>

namespace player2 {
struct BusMeter { float peakLeft{},peakRight{}; };
class BusMixer {
    struct State {
        int index{-1},output{-1}; double gain{1},gainDb{}; bool bypass{},auxiliary{};
        std::vector<EffectRuntime> effects;
        std::vector<Send> sends;
        Stereo input;
        BusMeter meter;
        MusicFilters filters; bool wetConfigured{}; double wet{};
    };
    std::vector<State> buses_;
    std::array<int,4096> positions_{};
    std::array<int,4096> effectBuses_{},effectSlots_{};
    std::array<int,4096> effectBindings_{};
    Stereo master_; std::uint64_t numericalCorrections_{}; int rate_;
public:
    static double maximumDelay(const Graph& graph,const Effect& effect) noexcept {
        double result=effect.parameters[7];
        for(const auto& curve:graph.effectCurves)if(curve.effect==effect.index && curve.property==7)
            for(const auto& point:curve.points)result=std::max(result,point.y);
        return result;
    }
    explicit BusMixer(const Graph& graph,std::size_t maximumStateBytes=32*1024*1024):rate_(graph.sampleRate) {
        std::size_t total=0;
        for(const auto& bus:graph.buses) for(const auto& effect:bus.effects) {
            const auto bytes=EffectRuntime::stateBytes(effect,graph.sampleRate,maximumDelay(graph,effect));
            if(bytes>maximumStateBytes-total) throw std::length_error("Mixer state budget");
            total+=bytes;
        }
        positions_.fill(-1);effectBuses_.fill(-1);effectSlots_.fill(-1); buses_.reserve(graph.buses.size());
        for(const auto& curve:graph.effectCurves)++effectBindings_[curve.effect];
        for(const auto& bus:graph.buses) {
            State state; state.index=bus.index; state.output=bus.output; state.auxiliary=bus.auxiliary; state.sends=bus.sends;
            const auto* node=graph.node(bus.index); double localGain=node->properties[0];
            for(int parent=node->parent;parent!=-1;) {
                const auto* ancestor=graph.node(parent);
                if(parent==bus.output) { localGain-=ancestor->properties[0]; break; }
                parent=ancestor->parent;
            }
            state.gainDb=localGain; state.gain=dbGain(localGain); state.bypass=node->properties[6]>=0.5;
            state.effects.resize(bus.effects.size());
            for(std::size_t n=0;n<bus.effects.size();++n) {
                const auto& effect=bus.effects[n];state.effects[n].prepare(effect,graph.sampleRate,maximumDelay(graph,effect));
                if(effect.index>=0 && effect.index<4096) {effectBuses_[effect.index]=static_cast<int>(buses_.size());effectSlots_[effect.index]=static_cast<int>(n);}
            }
            positions_[bus.index]=static_cast<int>(buses_.size()); buses_.push_back(std::move(state));
        }
    }
    void beginFrame() noexcept { master_={}; for(auto& bus:buses_) bus.input={}; }
    void beginBlock() noexcept { for(auto& bus:buses_) bus.meter={}; }
    void add(int bus,Stereo input,double gain=1) noexcept {
        Stereo* output=&master_;
        if(bus>=0 && bus<4096 && positions_[bus]>=0) output=&buses_[positions_[bus]].input;
        output->left=audioSample(output->left+input.left*gain,numericalCorrections_); output->right=audioSample(output->right+input.right*gain,numericalCorrections_);
    }
    Stereo finishFrame() noexcept {
        for(auto& bus:buses_) {
            Stereo signal{audioSample(bus.input.left*bus.gain,numericalCorrections_),audioSample(bus.input.right*bus.gain,numericalCorrections_)};
            if(!bus.bypass) {
                signal={bus.filters.process(signal.left,0,numericalCorrections_),bus.filters.process(signal.right,1,numericalCorrections_)};
                for(auto& effect:bus.effects) { const auto before=effect.numericalCorrections();signal=effect.process(signal);countAudioCorrections(numericalCorrections_,effect.numericalCorrections()-before); }
            }
            bus.meter.peakLeft=std::max(bus.meter.peakLeft,std::abs(signal.left));
            bus.meter.peakRight=std::max(bus.meter.peakRight,std::abs(signal.right));
            for(const auto& send:bus.sends) add(send.bus,signal,dbGain(send.gainDb));
            add(bus.output,signal);
        }
        return master_;
    }
    void properties(int id,double gainDb,bool bypass,bool lowEnabled=false,double lowHz=20000,bool highEnabled=false,double highHz=20,bool wetConfigured=false,double wet=0) noexcept {
        if(id>=0 && id<4096 && positions_[id]>=0) { auto& bus=buses_[positions_[id]];
            if(bypass && !bus.bypass) { bus.filters.clear(); for(auto& effect:bus.effects) effect.clear(); }
            bus.filters.configure(rate_,lowEnabled,lowHz,highEnabled,highHz);
            bus.wetConfigured=wetConfigured;bus.wet=wetConfigured ? wet : 0;
            for(auto& effect:bus.effects) effect.reverbWet(wetConfigured,wet);
            bus.gainDb=gainDb; bus.gain=dbGain(gainDb); bus.bypass=bypass; }
    }
    std::uint64_t numericalCorrections() const noexcept { return numericalCorrections_; }
    void effectParameters(int index,const std::array<double,10>& values) noexcept {
        if(index>=0 && index<4096 && effectBuses_[index]>=0)buses_[effectBuses_[index]].effects[effectSlots_[index]].configure(values);
    }
    api::EffectSnapshot effectSnapshot(int index) const noexcept {
        if(index<0 || index>=4096 || effectBuses_[index]<0)return {};
        const auto& bus=buses_[effectBuses_[index]];const auto order=effectSlots_[index];const auto& effect=bus.effects[order];
        api::EffectSnapshot s;s.index=index;s.bus=bus.index;s.order=order;s.busBypass=bus.bypass;s.kind=effect.kind();s.bypass=effect.bypass();s.bindings=effectBindings_[index];
        s.base=effect.base();s.rtpc=effect.parameters();s.effective=s.rtpc;s.tail=!bus.bypass && effect.hasTail();
        if(s.kind==6 && bus.wetConfigured)s.effective[9]=bus.wet;
        return s;
    }
    void resetDiagnostics() noexcept { numericalCorrections_=0;for(auto& bus:buses_)for(auto& effect:bus.effects)effect.resetDiagnostics(); }
    bool hasTail() const noexcept {
        for(const auto& bus:buses_) if(!bus.bypass) { if(bus.filters.hasTail()) return true; for(const auto& effect:bus.effects) if(effect.hasTail()) return true; }
        return false;
    }
    api::BusSnapshot snapshot(int index) const noexcept {
        if(index<0 || index>=4096 || positions_[index]<0) return {};
        const auto& bus=buses_[positions_[index]]; int tails=0;
        if(!bus.bypass) { tails+=bus.filters.tailCount();for(const auto& effect:bus.effects) tails+=effect.hasTail(); }
        return {bus.index,bus.output,bus.auxiliary,bus.bypass,bus.gainDb,bus.meter.peakLeft,bus.meter.peakRight,tails,{},bus.filters.lowHz(),bus.filters.highHz(),bus.wet,bus.filters.lowEnabled(),bus.filters.highEnabled(),bus.wetConfigured};
    }
    BusMeter meter(int id) const noexcept { return id>=0 && id<4096 && positions_[id]>=0 ? buses_[positions_[id]].meter : BusMeter{}; }
    void clear(bool resetMeters=true) noexcept { master_={}; for(auto& bus:buses_) { bus.input={}; bus.filters.clear(); if(resetMeters) bus.meter={}; for(auto& effect:bus.effects) effect.clear(); } }
};
}
