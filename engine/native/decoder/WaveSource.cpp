#include "decoder/WaveLayout.hpp"
#include "decoder/ImaWaveFormat.hpp"
#include "decoder/MsAdpcmWaveFormat.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cmath>
#include <limits>

namespace player2::audio {
namespace {
// Own encoded bytes independently of the caller/JNI pin. Decode only requested
// PCM frames; source inspection/open no longer allocates an expanded PCM copy.
class WaveFrameSource final : public AudioFrameSource {
    SourceMetadata metadata_;
    std::vector<std::uint8_t> bytes_;
    std::size_t data_,width_;
    bool floating_,big_;
    SourceSamplePosition position_;
public:
    WaveFrameSource(SourceMetadata metadata,ByteView bytes,std::size_t data,std::uint16_t bits,bool floating,bool big)
        :metadata_(metadata),bytes_(bytes.data,bytes.data+bytes.size),data_(data),width_(bits/8),floating_(floating),big_(big) {}
    const SourceMetadata& metadata() const noexcept override { return metadata_; }
    SourceSamplePosition position() const noexcept override { return position_; }
    std::size_t render(float* output,std::size_t frames) noexcept override {
        const auto channels=static_cast<std::size_t>(metadata_.channels);
        const auto rendered=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));
        const SourceBytes input({bytes_.data(),bytes_.size()},DecodeLimits{bytes_.size()});
        const auto offset=data_+static_cast<std::size_t>(position_.value)*channels*width_;
        for(std::size_t n=0;n<rendered*channels;++n) output[n]=waveLinearSample(input,offset+n*width_,metadata_.bitDepth,floating_,big_,metadata_.encoding);
        std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);
        position_.value+=static_cast<std::int64_t>(rendered);return rendered;
    }
    SourceStatus seek(SourceSamplePosition position) noexcept override {
        if(position.value<0 || position.value>metadata_.duration.value) return SourceStatus::InvalidRequest;
        position_=position;return SourceStatus::Ok;
    }
    void reset() noexcept override { position_.value=0; }
};
}
std::unique_ptr<AudioFrameSource> openWave(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);
    if(waveMsAdpcmFormat(input))return openMsAdpcm(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,parseMsAdpcmWave(input,limits));
    if(waveImaFormat(input))return openImaWave(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,parseImaWave(input,limits));
    const auto layout=parseWaveLayout(input,limits);
    if(layout.floating)for(std::size_t n=0;n<static_cast<std::size_t>(layout.metadata.duration.value)*layout.metadata.channels;++n)
        wavePcmSample(input,layout.dataOffset+n*(layout.metadata.bitDepth/8),layout.metadata.bitDepth,true,layout.big);
    return std::make_unique<WaveFrameSource>(layout.metadata,bytes,layout.dataOffset,layout.metadata.bitDepth,layout.floating,layout.big);
}
}
