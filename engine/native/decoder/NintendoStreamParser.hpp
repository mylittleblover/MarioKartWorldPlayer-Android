#pragma once
#include "decoder/BfstmLayout.hpp"
#include "decoder/BrstmLayout.hpp"

namespace player2::audio {
template<class Bytes> NintendoStreamLayout parseNintendoStreamLayout(Bytes& input,const DecodeLimits& limits,bool dsp,
    NintendoStreamContainer container) {
    return container==NintendoStreamContainer::Brstm ? parseBrstmLayout(input,limits,dsp) : parseBfstmLayout(input,limits,dsp,container);
}
}
