#include "decoder/AdxFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class AdxSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    AdxLayout layout_;
    std::array<AdxHistory,2> history_;
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        auto history=history_;std::array<float,2> pcm{};
        const auto frame=static_cast<std::size_t>(position_.value);const auto channels=static_cast<unsigned>(metadata().channels);
        const auto block=frame/32,within=frame%32;
        for(unsigned channel=0;channel<channels;++channel) {
            const auto p=layout_.start+(block*channels+channel)*18;
            const auto scale=bytes_.u16(p,true);if(scale&0x8000)fail(SourceStatus::UnsupportedCodec);
            const auto packed=bytes_.u8(p+2+within/2);
            pcm[channel]=adxSample(within%2 ? packed&15 : packed>>4,unsigned(scale)+1,layout_,history[channel])/32768.0f;
        }
        history_=history;++position_.value;if(output)std::copy_n(pcm.data(),channels,output);
    }
public:
    AdxSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),bytes_(*input_,limits),
        layout_(parseAdx(bytes_,limits)),history_(layout_.initial) {}
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t count=0;
        try{while(count<wanted){next(output+count*channels);++count;}}
        catch(const SourceFailure& error){status_=error.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        const auto previous=position_;const auto old=history_;status_=SourceStatus::Ok;
        try {
            if(target.value<position_.value){position_.value=0;history_=layout_.initial;}
            while(position_.value<target.value)next(nullptr);
        }catch(const SourceFailure& error){status_=error.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=old;}return status_;
    }
    void reset()noexcept override{position_.value=0;history_=layout_.initial;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openAdx(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    if(limits.maximumDecodedBytes<8192)fail(SourceStatus::ResourceLimit);
    return std::make_unique<AdxSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openAdx(ByteView bytes,const DecodeLimits& limits) {
    return openAdx(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
