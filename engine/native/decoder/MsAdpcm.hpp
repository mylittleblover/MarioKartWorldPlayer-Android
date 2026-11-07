#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>
#include <array>
#include <limits>
namespace player2::audio {
inline constexpr std::array<std::array<int,2>,7> MsAdpcmCoefficients{{{{256,0}},{{512,-256}},{{0,0}},{{192,64}},{{240,0}},{{460,-208}},{{392,-232}}}};
struct MsAdpcmHistory {std::int32_t first{},second{};std::int64_t delta{};unsigned predictor{};};
inline std::int32_t msAdpcmSigned(std::uint16_t value)noexcept{return value<32768 ? std::int32_t(value) : std::int32_t(value)-65536;}
inline std::int32_t msAdpcmSample(unsigned code,MsAdpcmHistory& history) {
    // Refuse numerically unbounded streams outside the portable32-bit reference profile;
    // arbitrary 64-bit saturation would invent a different future delta state.
    if(history.delta>std::numeric_limits<std::int32_t>::max()/768)fail(SourceStatus::UnsupportedCodec);
    constexpr int adaptation[]{230,230,230,230,307,409,512,614,768,614,512,409,307,230,230,230};
    const auto& coefficients=MsAdpcmCoefficients[history.predictor];
    const auto raw=std::int64_t(history.first)*coefficients[0]+std::int64_t(history.second)*coefficients[1];
    // Microsoft ACM uses arithmetic SHR, i.e. floor for negative predictions.
    const auto predicted=raw>=0 ? raw/256 : -((-raw+255)/256);
    const auto nibble=code<8 ? int(code) : int(code)-16;
    const auto value=std::clamp(predicted+nibble*history.delta,std::int64_t(-32768),std::int64_t(32767));
    history.second=history.first;history.first=static_cast<std::int32_t>(value);
    history.delta=std::max<std::int64_t>(16,history.delta*adaptation[code]/256);return history.first;
}
template<class Bytes> MsAdpcmHistory msAdpcmHistory(Bytes& input,std::size_t start,unsigned channels,unsigned channel) {
    const auto predictor=input.u8(start+channel);const auto delta=input.u16(start+channels+channel*2);
    if(predictor>=MsAdpcmCoefficients.size() || delta<16 || delta>32767)fail(SourceStatus::MalformedSource);
    return {msAdpcmSigned(input.u16(start+3*channels+channel*2)),msAdpcmSigned(input.u16(start+5*channels+channel*2)),delta,predictor};
}
template<class Bytes> void decodeMsAdpcmBlock(Bytes& input,std::size_t start,unsigned channels,std::size_t frames,float* output) {
    std::array<MsAdpcmHistory,2> histories{};
    for(unsigned ch=0;ch<channels;++ch) {
        histories[ch]=msAdpcmHistory(input,start,channels,ch);
        if(output){output[ch]=histories[ch].second/32768.0f;output[channels+ch]=histories[ch].first/32768.0f;}
    }
    for(std::size_t frame=2;frame<frames;++frame)for(unsigned ch=0;ch<channels;++ch) {
        const auto n=(frame-2)*channels+ch;const auto packed=input.u8(start+7*channels+n/2);
        const auto sample=msAdpcmSample(n%2 ? packed&15 : packed>>4,histories[ch]);
        if(output)output[frame*channels+ch]=sample/32768.0f;
    }
}
struct MsAdpcmLayout {SourceMetadata metadata;std::size_t data{},bytes{},align{},blockFrames{};};
template<class Bytes> void validateMsAdpcm(Bytes& input,const MsAdpcmLayout& layout,const DecodeLimits& limits) {
    const auto channels=static_cast<unsigned>(layout.metadata.channels);
    if(channels!=1 && channels!=2)fail(SourceStatus::UnsupportedChannels);
    if(layout.align<7*channels || layout.bytes%layout.align || !layout.bytes || layout.blockFrames!=2+(layout.align-7*channels)*2/channels)fail(SourceStatus::MalformedSource);
    if(layout.align>32768 || 16384+std::uint64_t(layout.blockFrames)*channels*sizeof(float)>limits.maximumDecodedBytes)fail(SourceStatus::ResourceLimit);
    input.need(layout.data,layout.bytes);const auto blocks=layout.bytes/layout.align;
    if(blocks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
    const auto capacity=std::uint64_t(blocks)*layout.blockFrames,frames=std::uint64_t(layout.metadata.duration.value);
    if(!frames || frames>capacity || frames<=capacity-layout.blockFrames)fail(SourceStatus::MalformedSource);
    for(std::size_t n=0;n<blocks;++n)decodeMsAdpcmBlock(input,layout.data+n*layout.align,channels,layout.blockFrames,nullptr);
}
std::unique_ptr<AudioFrameSource> openMsAdpcm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,MsAdpcmLayout layout);
}
