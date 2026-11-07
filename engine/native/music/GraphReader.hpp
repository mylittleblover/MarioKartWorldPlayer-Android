#pragma once
#include "compiled/Graph.hpp"
#include "music/GraphValidation.hpp"
#include "compiled/GraphProtocolIds.hpp"
#include <cstring>
#include <stdexcept>

namespace player2 {
class GraphReader {
    const std::uint8_t* data_; std::size_t size_,position_{}; int identities_{},version_{}; std::size_t envelopePoints_{};
    [[noreturn]] static void invalid() { throw std::invalid_argument("INVALID_COMPILED_GRAPH"); }
    void need(std::size_t count) const { if(count>size_-position_) invalid(); }
    std::uint32_t u32() { need(4); std::uint32_t value=0; for(int n=0;n<4;++n) value=(value<<8)|data_[position_++]; return value; }
    int integer() { const auto bits=u32(); std::int32_t value; std::memcpy(&value,&bits,4); return value; }
    Frame frame() { need(8); std::uint64_t bits=0; for(int n=0;n<8;++n) bits=(bits<<8)|data_[position_++]; Frame value; std::memcpy(&value,&bits,8); return value; }
    double number() { const auto value=frame(); double result; std::memcpy(&result,&value,8); if(!std::isfinite(result)) invalid(); return result; }
    bool boolean() { need(1); const auto value=data_[position_++]; if(value>1) invalid(); return value!=0; }
    static bool validUtf8(const std::uint8_t* data,std::size_t size) noexcept {
        std::size_t cursor=0;
        while(cursor<size) {
            const auto lead=data[cursor++];
            if(lead<0x80)continue;
            const unsigned tails=lead>=0xc2 && lead<=0xdf ? 1 : lead>=0xe0 && lead<=0xef ? 2 : lead>=0xf0 && lead<=0xf4 ? 3 : 0;
            if(!tails || tails>size-cursor)return false;
            const auto second=data[cursor];
            if((lead==0xe0 && second<0xa0) || (lead==0xed && second>0x9f) ||
               (lead==0xf0 && second<0x90) || (lead==0xf4 && second>0x8f))return false;
            for(unsigned n=0;n<tails;++n)if((data[cursor++]&0xc0)!=0x80)return false;
        }
        return true;
    }
    std::string stringPayload(int count) {
        if(count<0 || count>65536)invalid();
        need(count);
        if(!validUtf8(data_+position_,count))invalid();
        std::string value(reinterpret_cast<const char*>(data_+position_),count);position_+=count;return value;
    }
    std::string string() { return stringPayload(integer()); }
    std::optional<std::string> nullableString() {
        int count=integer();
        if(count==-1) return std::nullopt;
        return stringPayload(count);
    }
    int index(bool optional=false) { int value=integer(); if(value< (optional ? -1 : 0) || value>=identities_) invalid(); return value; }
    template<class E> E enumeration() { const auto value=protocol::enumFromWire<E>(integer()); if(!value) invalid(); return *value; }
    template<class T,class F> std::vector<T> list(F read,int maximum=4096) { int count=integer(); if(count<0 || count>maximum) invalid(); std::vector<T> result; result.reserve(count); for(int n=0;n<count;++n) result.push_back(read()); return result; }
    std::vector<int> indices(int maximum=4096) { return list<int>([&] { return index(); },maximum); }
    Action action() { Action a; a.kind=enumeration<ActionKind>(); if(version_<protocol::PlaybackExtensionVersion && a.kind==ActionKind::Event)invalid(); if(version_<protocol::LayerControlVersion && a.kind==ActionKind::LayerGain)invalid(); a.target=index(true); a.value=index(true); a.number=number(); a.retainTail=boolean(); return a; }
    Clip clip() { Clip c; c.index=index(); c.asset=index(); c.start=frame(); c.sourceOffset=frame(); c.sourceLength=frame(); c.gainDb=number(); c.fadeIn=frame(); c.fadeOut=frame(); if(version_>=protocol::ClipLoopVersion && boolean())c.loop=CompiledClipLoop{{frame()},{frame()},integer()}; if(c.start<0 || c.sourceOffset<0 || c.sourceLength<=0 || c.fadeIn<0 || c.fadeOut<0) invalid(); return c; }
    Send send() { Send s; s.bus=index(); s.gainDb=number(); return s; }
    Condition condition() { Condition c; c.group=index(); c.value=index(); return c; }
    Fade fade() { Fade f; f.beats=number(); f.offset=number(); f.shape=enumeration<Shape>(); if(f.beats<0) invalid(); return f; }
    static std::uint32_t crc(const std::uint8_t* data,std::size_t size) noexcept {
        std::uint32_t value=0xffffffff;
        for(std::size_t i=0;i<size;++i) { value^=data[i]; for(int bit=0;bit<8;++bit) value=(value>>1)^(0xedb88320u&static_cast<std::uint32_t>(-static_cast<int>(value&1))); }
        return ~value;
    }
public:
    GraphReader(const std::uint8_t* data,std::size_t size):data_(data),size_(size) { if(!data || size<16 || size>4*1024*1024+16) invalid(); }
    Graph read();
private:
    void segmentEnvelopes(Graph& graph);
    void clipEnvelopes(Graph& graph);
};
}
