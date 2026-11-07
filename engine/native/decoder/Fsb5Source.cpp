#include "decoder/Fsb5Format.hpp"
#include "decoder/FsbMpegFormat.hpp"
#include "decoder/FsbAdpcmFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/WaveLayout.hpp"
#include <cstring>

namespace player2::audio {
namespace {
class Fsb5Source final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    Fsb5Index index_;
    const Fsb5Sample* selected_{};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    XboxImaBlock ima_;
    std::unique_ptr<AudioFrameSource> decoder_;
    void next(float* output) {
        if(selected_->ima){ima_.sample(bytes_,selected_->start,metadata().channels,position_.value,output);++position_.value;return;}
        std::array<float,2> pcm{};const auto offset=selected_->start+static_cast<std::size_t>(position_.value)*metadata().channels*selected_->width;
        for(unsigned ch=0;ch<static_cast<unsigned>(metadata().channels);++ch)
            pcm[ch]=wavePcmSample(bytes_,offset+ch*selected_->width,metadata().bitDepth,selected_->floating);
        ++position_.value;if(output)std::copy_n(pcm.data(),metadata().channels,output);
    }
public:
    Fsb5Source(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry):input_(std::move(input)),bytes_(*input_,limits),index_(parseFsb5(bytes_,limits)) {
        if(!entry)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(index_.entries.begin(),index_.entries.end(),[&](const auto& e){return std::strcmp(e.id.data(),entry)==0;});
        if(found==index_.entries.end())fail(SourceStatus::EntryMissing);
        selected_=&index_.samples[static_cast<std::size_t>(found-index_.entries.begin())];
        if(selected_->mpeg){auto child=limits;child.maximumDecodedBytes-=index_.entries.size()*512;decoder_=openFsbMpeg(std::move(input_),selected_->start,selected_->end,metadata(),4,true,child);}
        if(selected_->dsp || selected_->psx){auto child=limits;child.maximumDecodedBytes-=index_.entries.size()*512;decoder_=openFsbAdpcm(std::move(input_),selected_->start,selected_->end,metadata(),index_.planar,true,selected_->dspChannels,child);}
        if(selected_->floating)while(position_.value<metadata().duration.value)next(nullptr);
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return selected_->metadata;}
    const std::vector<SourceEntry>* sourceEntries()const noexcept override{return &index_.entries;}
    SourceSamplePosition position()const noexcept override{return decoder_ ? decoder_->position() : position_;}
    SourceStatus renderStatus()const noexcept override{return decoder_ ? decoder_->renderStatus() : status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        if(decoder_)return decoder_->render(output,frames);
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t count=0;
        try{while(count<wanted){next(output+count*channels);++count;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(decoder_)return decoder_->seek(position);
        if(position.value<0 || position.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=position;status_=SourceStatus::Ok;return status_;
    }
    void reset()noexcept override{if(decoder_)decoder_->reset();position_.value=0;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openFsb5(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<Fsb5Source>(std::move(input),limits,entry);
}
std::unique_ptr<AudioFrameSource> openFsb5(ByteView bytes,const DecodeLimits& limits,const char* entry) {
    return openFsb5(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entry);
}
std::vector<SourceEntry> inspectFsb5Entries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parseFsb5(input,limits).entries;
}
}
