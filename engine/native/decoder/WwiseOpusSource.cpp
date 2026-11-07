#include "decoder/WwiseOpusFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
std::unique_ptr<AudioFrameSource> openWwiseOpus(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);auto layout=parseWwiseOpus(bytes,limits);
    return makePacketOpusSource(std::move(input),limits,std::move(layout));
}
std::unique_ptr<AudioFrameSource> openWwiseOpus(ByteView bytes,const DecodeLimits& limits) {
    return openWwiseOpus(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
std::unique_ptr<AudioFrameSource> openRiffMedia(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);
    return wwiseOpusFormat(bytes) ? openWwiseOpus(std::move(input),limits) : openWave(std::move(input),limits);
}
}
