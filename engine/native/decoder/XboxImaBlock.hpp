#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/ImaAdpcm.hpp"
#include <limits>

namespace player2::audio {
// FMOD mono/stereo uses the Xbox block layout, not multichannel FSB IMA.
// Every 36 bytes/channel emits 64 frames: predictor, then 63 low-first codes.
// The last high nibble is padding and is never decoded into Source time.
inline constexpr std::uint64_t XboxImaDecodedBudget=1024;
inline std::uint64_t xboxImaBytes(std::uint64_t frames,unsigned channels)noexcept {
    return ((frames+63)/64)*36*channels;
}
template<class Bytes> void validateXboxIma(Bytes& input,std::size_t start,std::size_t size,std::uint64_t frames,unsigned channels) {
    if(!frames || (channels!=1 && channels!=2) || xboxImaBytes(frames,channels)!=size)fail(SourceStatus::MalformedSource);
    input.need(start,size);
    for(std::size_t at=start;at<start+size;at+=36*channels)for(unsigned ch=0;ch<channels;++ch) {
        if(input.u8(at+ch*4+2)>88)fail(SourceStatus::MalformedSource);
        if(input.u8(at+ch*4+3))fail(SourceStatus::UnsupportedCodec);
    }
}
class XboxImaBlock final {
    std::array<float,128> pcm_{};
    std::uint64_t block_{std::numeric_limits<std::uint64_t>::max()};
public:
    template<class Bytes> void sample(Bytes& input,std::size_t start,unsigned channels,std::uint64_t frame,float* output) {
        const auto block=frame/64;
        if(block_!=block) {
            const auto at=start+static_cast<std::size_t>(block)*36*channels;
            std::array<float,128> decoded{};std::array<ImaHistory,2> state{};
            for(unsigned ch=0;ch<channels;++ch) {
                const auto raw=input.u16(at+ch*4);const auto index=input.u8(at+ch*4+2);
                if(index>88)fail(SourceStatus::MalformedSource);
                if(input.u8(at+ch*4+3))fail(SourceStatus::UnsupportedCodec);
                state[ch]={raw<0x8000 ? std::int32_t(raw) : std::int32_t(raw)-65536,index};
                decoded[ch]=state[ch].sample/32768.0f;
            }
            for(unsigned n=1;n<64;++n)for(unsigned ch=0;ch<channels;++ch) {
                const auto nibble=n-1;const auto packed=input.u8(at+4*channels+(nibble/8)*4*channels+ch*4+(nibble%8)/2);
                decoded[n*channels+ch]=imaSample(nibble%2 ? packed>>4 : packed&15,state[ch])/32768.0f;
            }
            pcm_=decoded;block_=block; // Publish the complete block only after all channel reads succeeded.
        }
        std::copy_n(pcm_.data()+frame%64*channels,channels,output);
    }
};
}
