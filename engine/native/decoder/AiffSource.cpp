#include "decoder/AiffFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/WaveLayout.hpp"

namespace player2::audio {
namespace {
struct AiffSampleBytes {std::array<std::uint8_t,8> bytes{};std::uint8_t u8(std::size_t p)const noexcept{return bytes[p];}};
class AiffSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    AiffLayout layout_;
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        std::array<float,2> pcm{};const auto start=layout_.start+static_cast<std::size_t>(position_.value)*metadata().channels*layout_.width;
        for(unsigned ch=0;ch<static_cast<unsigned>(metadata().channels);++ch) {
            AiffSampleBytes sample;
            for(unsigned n=0;n<layout_.width;++n)sample.bytes[n]=bytes_.u8(start+ch*layout_.width+(layout_.little ? n : layout_.width-1-n));
            if(metadata().encoding==SampleEncoding::PcmSigned && metadata().bitDepth==8)sample.bytes[0]^=0x80; // AIFF PCM8 is signed; shared WAVE conversion consumes unsigned PCM8.
            pcm[ch]=waveLinearSample(sample,0,metadata().bitDepth,layout_.floating,false,metadata().encoding);
        }
        ++position_.value;if(output)std::copy_n(pcm.data(),metadata().channels,output);
    }
public:
    AiffSource(std::unique_ptr<EncodedSourceInput> input,AiffLayout layout,const DecodeLimits& limits):input_(std::move(input)),bytes_(*input_,limits),layout_(layout) {
        if(layout_.floating)while(position_.value<metadata().duration.value)next(nullptr);
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t count=0;
        try{while(count<wanted){next(output+count*channels);++count;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(position.value<0 || position.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=position;status_=SourceStatus::Ok;return status_;
    }
    void reset()noexcept override{position_.value=0;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openAiff(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);const auto layout=parseAiff(bytes,limits);
    if(layout.ima4)return openAiffIma4(std::move(input),layout,limits);
    return std::make_unique<AiffSource>(std::move(input),layout,limits);
}
std::unique_ptr<AudioFrameSource> openAiff(ByteView bytes,const DecodeLimits& limits) {
    return openAiff(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
