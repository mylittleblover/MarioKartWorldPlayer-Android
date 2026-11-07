#include "decoder/WaveLayout.hpp"
#include "decoder/ImaWaveFormat.hpp"
#include "decoder/MsAdpcmWaveFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include <array>

namespace player2::audio {
namespace {
// Readable inputs can block. Only the Engine control/decode worker uses this
// decoder; realtime Voices consume SourcePageCache PCM leases instead.
class WaveInputFrameSource final:public AudioFrameSource {
    WaveLayout layout_;
    std::unique_ptr<EncodedSourceInput> input_;
    std::array<std::uint8_t,32768> scratch_{};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
public:
    WaveInputFrameSource(WaveLayout layout,std::unique_ptr<EncodedSourceInput> input):layout_(layout),input_(std::move(input)) {
        layout_.metadata.flags&=~std::uint32_t(RealtimeRender);
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto& metadata=layout_.metadata;const auto channels=static_cast<std::size_t>(metadata.channels),width=static_cast<std::size_t>(metadata.bitDepth/8);
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata.duration.value-position_.value));std::size_t rendered=0;
        status_=SourceStatus::Ok;
        try {
            while(rendered<wanted) {
                const auto count=std::min(wanted-rendered,scratch_.size()/(channels*width)),bytes=count*channels*width;
                const auto offset=layout_.dataOffset+static_cast<std::uint64_t>(position_.value)*channels*width;
                const auto read=input_->read({offset},scratch_.data(),bytes);
                if(read.status!=SourceStatus::Ok)fail(read.status);
                if(read.bytes!=bytes)fail(SourceStatus::InputUnavailable);
                const SourceBytes slice({scratch_.data(),scratch_.size()},DecodeLimits{scratch_.size()});
                for(std::size_t n=0;n<count*channels;++n)output[rendered*channels+n]=waveLinearSample(slice,n*width,metadata.bitDepth,layout_.floating,layout_.big,metadata.encoding);
                rendered+=count;position_.value+=static_cast<std::int64_t>(count);
            }
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);return rendered;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(position.value<0 || position.value>layout_.metadata.duration.value)return SourceStatus::InvalidRequest;
        position_=position;status_=SourceStatus::Ok;return SourceStatus::Ok;
    }
    void reset()noexcept override{position_.value=0;status_=SourceStatus::Ok;}
    void validateFloatPcm() {
        if(!layout_.floating)return;
        std::array<float,4096> pcm{};
        while(position_.value<layout_.metadata.duration.value) {
            const auto count=std::min<std::size_t>(pcm.size()/layout_.metadata.channels,static_cast<std::size_t>(layout_.metadata.duration.value-position_.value));
            const auto got=render(pcm.data(),count);
            if(status_!=SourceStatus::Ok)fail(status_);
            if(got!=count)fail(SourceStatus::InputUnavailable);
        }
        reset();
    }
};
}
std::unique_ptr<AudioFrameSource> openWave(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);
    if(waveMsAdpcmFormat(bytes)){const auto layout=parseMsAdpcmWave(bytes,limits);return openMsAdpcm(std::move(input),limits,layout);}
    if(waveImaFormat(bytes)){const auto layout=parseImaWave(bytes,limits);return openImaWave(std::move(input),limits,layout);}
    const auto layout=parseWaveLayout(bytes,limits);
    auto source=std::make_unique<WaveInputFrameSource>(layout,std::move(input));source->validateFloatPcm();return source;
}
}
