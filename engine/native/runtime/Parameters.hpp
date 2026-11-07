#pragma once
#include "compiled/Graph.hpp"
#include "api/EngineTypes.hpp"
#include "rtpc/PropertyRegistry.hpp"
#include "dsp/ParameterDescriptors.hpp"
namespace player2 {
class Parameters {
    const Graph& graph_;
    std::array<SmoothedParameter,4096> parameters_{};
    std::array<int,4096> values_{};
    std::array<const Parameter*,4096> definitions_{};
    std::array<double,4096> frameStart_{};
    Frame advancedFrame_{-1}; bool advanced_{};
    std::array<std::vector<const Rtpc*>,4096> curvesByTarget_;
    std::array<std::vector<const EffectRtpc*>,4096> curvesByEffect_;
    std::uint64_t syncRevision_{};
    std::array<double,4096> layerGains_{};
    std::array<std::uint32_t,4096> enabledProperties_{};
    std::array<std::uint32_t,4096> rtpcProperties_{};
public:
    explicit Parameters(const Graph& graph):graph_(graph) {
        for(const auto& curve:graph.curves) curvesByTarget_[curve.target].push_back(&curve);
        for(const auto& curve:graph.effectCurves) curvesByEffect_[curve.effect].push_back(&curve);
        for(const auto& p:graph.parameters) definitions_[p.index]=&p;
        for(const auto& node:graph.nodes) {
            auto& mask=enabledProperties_[node.index];
            const auto* ancestor=&node;
            for(int depth=0;ancestor && depth<33;++depth,ancestor=graph.node(ancestor->parent)) {
                mask|=ancestor->configuredProperties;
                for(const auto* curve:curvesByTarget_[ancestor->index]) {
                    const auto bit=std::uint32_t{1}<<curve->property;
                    mask|=bit;rtpcProperties_[node.index]|=bit;
                }
            }
        }
        reset();
    }
    void reset() noexcept {
        syncRevision_=0; resetFrame();
        values_.fill(-1);layerGains_.fill(0);
        for(const auto& group:graph_.groups) values_[group.index]=group.defaultValue;
        for(const auto& p:graph_.parameters) parameters_[p.index].prepare(p.defaultValue,p.attack,p.release);
    }
    const std::array<int,4096>& values() const noexcept { return values_; }
    std::uint64_t syncRevision() const noexcept { return syncRevision_; }
    bool setSync(int group,int value) noexcept {
        const auto* g=find(graph_.groups,group);
        if(!g || std::find(g->values.begin(),g->values.end(),value)==g->values.end()) return false;
        if(values_[group]!=value) { values_[group]=value; ++syncRevision_; }
        return true;
    }
    bool setLayerGain(int index,double db)noexcept {
        const auto* node=graph_.node(index);
        if(!node || node->kind!=Kind::Track || !std::isfinite(db) || db< -96 || db>24)return false;
        layerGains_[index]=db;return true;
    }
    void resetFrame() noexcept { advancedFrame_=-1; advanced_=false; }
    void beginFrame(Frame frame) noexcept { advanced_=advancedFrame_==frame; }
    api::GameParameterSnapshot snapshot(int index) const noexcept {
        if(index<0 || index>=4096 || !definitions_[index]) return {};
        const auto& p=*definitions_[index]; const auto& value=parameters_[index];
        return {index,value.current,value.target,p.min,p.max,p.defaultValue,p.attack,p.release,{}};
    }
    bool setParameter(int index,double value) noexcept {
        if(index<0 || index>=4096 || !definitions_[index] || !std::isfinite(value)) return false;
        const auto* p=definitions_[index]; value=std::clamp(value,p->min,p->max);
        auto& parameter=parameters_[index]; parameter.target=value;
        if(advanced_) { parameter.current=frameStart_[index]; parameter.next(); }
        else if((value>parameter.current ? parameter.attackStep : parameter.releaseStep)==1) parameter.current=value;
        return true;
    }
    void advance(Frame frame) noexcept {
        if(advancedFrame_!=frame) {
            for(const auto& p:graph_.parameters) { frameStart_[p.index]=parameters_[p.index].current; parameters_[p.index].next(); }
            advancedFrame_=frame;
        }
        advanced_=true;
    }
    bool propertyEnabled(int index,int property) const noexcept {
        return index>=0 && index<4096 && property>=0 && property<7 && (enabledProperties_[index]&(std::uint32_t{1}<<property));
    }
    double propertyBeforeLayer(int index,int property) const noexcept {
        const auto* node=graph_.node(index); const auto* descriptor=propertyDescriptor(property); if(!node || !descriptor) return 0;
        // Compiler has already inherited the base property. With no binding to
        // this property anywhere in the ancestry, traversing it is a no-op.
        // Dynamic properties retain the original per-frame ordered evaluation.
        if(!(rtpcProperties_[index]&(std::uint32_t{1}<<property)))
            return std::clamp(node->properties[property],descriptor->min,descriptor->max);
        double result=node->properties[property];
        // Bindings to ancestors also apply to the descendant; authored base is already compiled/inherited.
        std::array<const Node*,33> ancestors{}; std::size_t count=0;
        for(const auto* ancestor=node;ancestor && count<ancestors.size();ancestor=graph_.node(ancestor->parent)) ancestors[count++]=ancestor;
        while(count) for(const auto* curve:curvesByTarget_[ancestors[--count]->index]) if(curve->property==property) {
            result=modify(result,curveValue(curve->points,parameters_[curve->parameter].current),curve->policy);
        }
        return std::clamp(result,descriptor->min,descriptor->max);
    }
    double property(int index,int property)const noexcept {
        const auto value=propertyBeforeLayer(index,property);const auto* node=graph_.node(index);
        return property==0 && node && node->kind==Kind::Track ? std::clamp(value+layerGains_[index],-96.0,24.0) : value;
    }
    std::array<double,10> effectParameters(const Effect& effect) const noexcept {
        auto result=effect.parameters;
        for(const auto* curve:curvesByEffect_[effect.index])result[curve->property]=modify(result[curve->property],
            curveValue(curve->points,parameters_[curve->parameter].current),curve->policy);
        for(int property=0;property<10;++property) {
            const auto* descriptor=effectParameterDescriptor(property);
            result[property]=std::clamp(result[property],descriptor->min,descriptor->max);
        }
        return result;
    }
    api::LayerGainSnapshot layerSnapshot(int index)const noexcept {
        const auto* node=graph_.node(index);if(!node || node->kind!=Kind::Track)return {};
        return {index,layerGains_[index],node->properties[0],propertyBeforeLayer(index,0),property(index,0),{}};
    }

};
}
