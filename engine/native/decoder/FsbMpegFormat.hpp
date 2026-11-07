#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
// FMOD MPEG stores padded frames, with zero initial decoder discard and an
// explicit finite Source duration. No implicit Xing/LAME gapless substitution.
std::unique_ptr<AudioFrameSource> openFsbMpeg(std::unique_ptr<EncodedSourceInput> input,
    std::size_t start,std::size_t end,const SourceMetadata& metadata,unsigned padding,bool outerAlignment,const DecodeLimits& limits);
}
