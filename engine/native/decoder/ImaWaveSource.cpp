#include "decoder/ImaWaveFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include <limits>

namespace player2::audio {
namespace {
class ImaWaveSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;EncodedSourceBytes bytes_;ImaWaveLayout layout_;
    std::vector<float> pcm_;std::size_t block_{std::numeric_limits<std::size_t>::max()};
    SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void load(std::size_t block) {
        block_=std::numeric_limits<std::size_t>::max();const auto channels=static_cast<unsigned>(metadata().channels);const auto start=layout_.data+block*layout_.align;
        for(unsigned ch=0;ch<channels;++ch) {
            const auto header=start+ch*4;const auto raw=bytes_.u16(header);
            ImaHistory history{raw<32768 ? std::int32_t(raw) : std::int32_t(raw)-65536,bytes_.u8(header+2)};
            if(history.index>88)fail(SourceStatus::MalformedSource);
            if(bytes_.u8(header+3))fail(SourceStatus::UnsupportedCodec);
            pcm_[ch]=history.sample/32768.0f;
            // WAVE IMA emits the header predictor, followed by 4-byte groups per channel.
            for(std::size_t frame=1;frame<layout_.blockFrames;++frame) {
                const auto n=frame-1,at=start+4*channels+n/8*(4*channels)+ch*4+n%8/2;
                const auto packed=bytes_.u8(at);pcm_[frame*channels+ch]=imaSample(n%2 ? packed>>4 : packed&15,history)/32768.0f;
            }
        }
        block_=block; // Do not publish partial channel/block decoding after input failure.
    }
public:
    ImaWaveSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,ImaWaveLayout layout):input_(std::move(input)),
        bytes_(*input_,limits),layout_(layout),pcm_(layout.blockFrames*layout.metadata.channels) {}
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);std::size_t got=0;
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));
        if(status_==SourceStatus::Ok)try {
            while(got<wanted) {
                const auto block=static_cast<std::size_t>(position_.value)/layout_.blockFrames,within=static_cast<std::size_t>(position_.value)%layout_.blockFrames;
                if(block_!=block)load(block);
                const auto count=std::min(wanted-got,layout_.blockFrames-within);
                std::copy_n(pcm_.data()+within*channels,count*channels,output+got*channels);got+=count;position_.value+=static_cast<std::int64_t>(count);
            }
        }catch(const SourceFailure& e){status_=e.status;}catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+got*channels,(frames-got)*channels,0.0f);return got;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=target;status_=SourceStatus::Ok;return SourceStatus::Ok; // Each block contains its own complete state.
    }
    void reset()noexcept override{position_.value=0;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openImaWave(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,ImaWaveLayout layout) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<ImaWaveSource>(std::move(input),limits,layout);
}
}
