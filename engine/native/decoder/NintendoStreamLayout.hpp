#pragma once
#include "decoder/SourceBytes.hpp"
#include <array>

namespace player2::audio {
inline constexpr unsigned MaximumNintendoStreamChannels=32;
enum class NintendoStreamContainer {Bfstm,Bcstm,Brstm};
struct NintendoStreamLayout {
    SourceMetadata metadata;
    std::size_t start;
    std::uint32_t blocks,blockBytes,blockFrames,lastPadded,width;
    bool big;
    std::size_t offset(std::size_t frame,std::size_t channel)const noexcept {
        const auto block=frame/blockFrames,within=frame%blockFrames;
        const auto stride=block==blocks-1 ? lastPadded : blockBytes;
        return start+block*blockBytes*metadata.channels+channel*stride+within*width;
    }
};
template<class Bytes> float bfstmPcmSample(Bytes& bytes,std::size_t offset,std::uint32_t width,bool big) {
    int sample=bytes.u8(offset);
    if(width==2)sample=big ? (sample<<8)|bytes.u8(offset+1) : sample|(int(bytes.u8(offset+1))<<8);
    const int sign=width==2 ? 32768 : 128;
    if(sample>=sign)sample-=sign*2;
    return static_cast<float>(sample)/sign;
}
inline void validateNintendoBlocks(std::uint32_t frames,std::uint32_t channels,std::uint32_t blocks,
    std::uint32_t blockBytes,std::uint32_t blockFrames,std::uint32_t lastBytes,std::uint32_t lastFrames,
    std::uint32_t lastPadded,std::uint32_t width,bool dsp,std::size_t fileSize,unsigned maximumChannels=2) {
    if(channels<1 || channels>maximumChannels)fail(SourceStatus::UnsupportedChannels);
    const auto minimumLastBytes=std::uint64_t(lastFrames/14)*8+(lastFrames%14 ? 1+(lastFrames%14+1)/2 : 0);
    const bool sizesValid=dsp ? (blockFrames%14==0 && std::uint64_t(blockFrames/14)*8==blockBytes &&
        lastBytes>=minimumLastBytes && lastBytes<=(std::uint64_t(lastFrames)+13)/14*8) :
        (std::uint64_t(blockFrames)*width==blockBytes && std::uint64_t(lastFrames)*width==lastBytes);
    if(blocks==0 || blockFrames==0 || lastFrames==0 || lastFrames>blockFrames || !sizesValid || lastPadded<lastBytes ||
        std::uint64_t(blocks-1)*blockFrames+lastFrames!=frames || blockBytes>fileSize/channels || lastPadded>fileSize/channels)
        fail(SourceStatus::MalformedSource);
}
inline SourceMetadata nintendoStreamMetadata(NintendoStreamContainer container,bool dsp,std::int32_t rate,
    std::int32_t channels,std::uint32_t frames,std::uint8_t loop,std::uint32_t loopStart,std::uint32_t width,const DecodeLimits& limits) {
    if(loop>1)fail(SourceStatus::MalformedSource);
    if(loop && loopStart>=frames)fail(SourceStatus::SourceLoopBounds);
    if(channels>2) {
        boundStreamDuration(frames,rate,limits);
        if(limits.maximumDecodedBytes<65536)fail(SourceStatus::ResourceLimit);
    } else boundPcm(frames,channels,limits);
    const char* provider=container==NintendoStreamContainer::Brstm ? (dsp ? "native.brstm-dsp" : "native.brstm-pcm") :
        container==NintendoStreamContainer::Bcstm ? (dsp ? "native.bcstm-dsp" : "native.bcstm-pcm") : (dsp ? "native.bfstm-dsp" : "native.bfstm-pcm");
    SourceMetadata metadata{provider,SourceKind::GameStream,rate,channels,{frames},SeekSupport::Fast,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,std::nullopt};
    if(!dsp)metadata.flags|=RealtimeRender;
    metadata.encoding=dsp ? SampleEncoding::DspAdpcm : SampleEncoding::PcmSigned;
    metadata.bitDepth=dsp ? 4 : static_cast<std::int32_t>(width*8);
    if(loop)metadata.sourceLoop=SourceLoopMetadata{{loopStart},{frames}};
    return metadata;
}
}
