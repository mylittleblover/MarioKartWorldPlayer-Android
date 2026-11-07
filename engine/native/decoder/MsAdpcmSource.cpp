#include "decoder/MsAdpcm.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include <limits>

namespace player2::audio {
namespace {
class MsAdpcmSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;EncodedSourceBytes bytes_;MsAdpcmLayout layout_;
    std::vector<float> pcm_;std::size_t block_{std::numeric_limits<std::size_t>::max()};
    SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void load(std::size_t block) {
        block_=std::numeric_limits<std::size_t>::max();const auto channels=static_cast<unsigned>(metadata().channels);const auto start=layout_.data+block*layout_.align;
        decodeMsAdpcmBlock(bytes_,start,channels,layout_.blockFrames,pcm_.data());
        block_=block; // Do not publish partial channel/block decoding after input failure.
    }
public:
    MsAdpcmSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,MsAdpcmLayout layout):input_(std::move(input)),
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
std::unique_ptr<AudioFrameSource> openMsAdpcm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,MsAdpcmLayout layout) {
    if(!input)fail(SourceStatus::InvalidRequest);
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);validateMsAdpcm(bytes,layout,limits);
    return std::make_unique<MsAdpcmSource>(std::move(input),limits,layout);
}
}
