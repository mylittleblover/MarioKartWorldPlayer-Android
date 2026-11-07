#pragma once
#include "api/AudioSourceTypes.hpp"
#include <array>
#include <cstring>

namespace player2::api {
// Owned facts: no provider pointers, decoder cursor or cache page address crosses the API.
struct BoundSourceInfo {
    bool present{},hasDigest{},compiledDigestVerified{};
    std::array<char,129> provider{};
    audio::SourceKind kind{};audio::SeekSupport seek{};std::uint32_t flags{};
    audio::SampleEncoding encoding{};int bitDepth{};
    std::array<std::uint8_t,32> sha256{};
    std::array<char,129> assetId{},entryId{},subsongId{},variantId{};
};
struct ClipLoopExecution {audio::SourceSamplePosition start,end;int count{-1};std::int64_t wraps{};bool released{};};
struct SourceVoiceSnapshot {
    int slot{-1},asset{-1},segment{-1},track{-1},clip{-1};
    bool sourceEnded{},streamed{},repeating{};
    audio::SourceSamplePosition position,duration,repeatBegin,repeatEnd;double fraction{};
    int sampleRate{},channels{};audio::TransportFrame at;
    std::optional<audio::SourceLoopMetadata> sourceLoop;
    BoundSourceInfo info;std::optional<ClipLoopExecution> clipLoop;
};
// Separate versioned query, not a Compiled Graph or Authoring serialization.
using SourceMonitorPacket=std::array<std::int64_t,207>;
inline SourceMonitorPacket sourceMonitorPacket(const SourceVoiceSnapshot& s) noexcept {
    SourceMonitorPacket p{audio::sourceMetadataWireVersion(s.info.encoding)+1,s.slot>=0,s.slot,s.asset,s.segment,s.track,s.clip,s.sourceEnded,s.streamed,s.repeating,s.position.value,0,
        s.info.present ? static_cast<int>(s.info.kind) : -1,s.sampleRate,s.channels,s.duration.value,s.info.flags,static_cast<int>(s.info.seek),static_cast<int>(s.info.encoding),s.info.bitDepth,
        s.sourceLoop.has_value(),s.sourceLoop ? s.sourceLoop->start.value : 0,s.sourceLoop ? s.sourceLoop->end.value : 0,s.repeatBegin.value,s.repeatEnd.value,
        s.info.hasDigest,s.info.compiledDigestVerified,s.at.value,0};
    std::memcpy(&p[11],&s.fraction,sizeof(double));
    while(p[28]<128 && s.info.provider[static_cast<std::size_t>(p[28])])++p[28];
    for(std::size_t n=0;n<128;++n)p[29+n/4]|=static_cast<std::uint32_t>(static_cast<unsigned char>(s.info.provider[n]))<<((n%4)*8);
    for(std::size_t n=0;n<32;++n)p[61+n/4]|=static_cast<std::uint32_t>(s.info.sha256[n])<<((n%4)*8);
    const auto text=[&](std::size_t offset,const auto& id) {
        while(p[offset]<128 && id[static_cast<std::size_t>(p[offset])])++p[offset];
        for(std::size_t n=0;n<128;++n)p[offset+1+n/4]|=static_cast<std::uint32_t>(static_cast<unsigned char>(id[n]))<<((n%4)*8);
    };
    text(69,s.info.assetId);text(102,s.info.entryId);text(135,s.info.subsongId);text(168,s.info.variantId);
    p[201]=s.clipLoop.has_value();
    if(s.clipLoop){p[202]=s.clipLoop->start.value;p[203]=s.clipLoop->end.value;p[204]=s.clipLoop->count;p[205]=s.clipLoop->wraps;p[206]=s.clipLoop->released;}
    return p;
}
}
