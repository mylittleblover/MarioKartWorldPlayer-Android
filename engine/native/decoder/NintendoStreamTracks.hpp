#pragma once
#include "decoder/BfstmTracks.hpp"
#include "decoder/BrstmTracks.hpp"

namespace player2::audio {
template<class Bytes> SourceStreamInfo parseNintendoStreamTracks(Bytes& bytes,const NintendoStreamLayout& layout,NintendoStreamContainer container) {
    return container==NintendoStreamContainer::Brstm ? parseBrstmTracks(bytes,layout) :
        parseBfstmTracks(bytes,layout,container==NintendoStreamContainer::Bcstm ? "bcstm" : "bfstm");
}
}
