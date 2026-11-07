#include "decoder/XwbFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cstring>

namespace player2::audio {
namespace {
class XwbSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;EncodedSourceBytes bytes_;XwbIndex index_;
    std::unique_ptr<AudioFrameSource> compressed_;const XwbSample* selected_{};SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        const auto channels=metadata().channels;std::array<float,2> pcm{};
        const auto at=selected_->start+static_cast<std::size_t>(position_.value)*channels*selected_->width;
        for(unsigned ch=0;ch<static_cast<unsigned>(channels);++ch) {
            if(selected_->width==1)pcm[ch]=(std::int32_t(bytes_.u8(at+ch))-128)/128.0f;
            else {const auto word=bytes_.u16(at+ch*2,index_.big);pcm[ch]=(word<32768 ? std::int32_t(word) : std::int32_t(word)-65536)/32768.0f;}
        }
        std::copy_n(pcm.data(),channels,output);++position_.value;
    }
public:
    XwbSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry):input_(std::move(input)),bytes_(*input_,limits),index_(parseXwb(bytes_,limits)) {
        if(!entry)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(index_.entries.begin(),index_.entries.end(),[&](const auto& e){return std::strcmp(e.id.data(),entry)==0;});
        if(found==index_.entries.end())fail(SourceStatus::EntryMissing);
        selected_=&index_.samples[static_cast<std::size_t>(found-index_.entries.begin())];
        if(selected_->metadata.encoding==SampleEncoding::MsAdpcm) {
            auto childLimits=limits;childLimits.maximumDecodedBytes-=index_.entries.size()*512;
            compressed_=openMsAdpcm(std::move(input_),childLimits,{selected_->metadata,selected_->start,selected_->size,selected_->blockAlign,selected_->blockFrames});
        }
    }
    const SourceMetadata& metadata()const noexcept override{return selected_->metadata;}
    const std::vector<SourceEntry>* sourceEntries()const noexcept override{return &index_.entries;}
    SourceSamplePosition position()const noexcept override{return compressed_ ? compressed_->position() : position_;}
    SourceStatus renderStatus()const noexcept override{return compressed_ ? compressed_->renderStatus() : status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        if(compressed_)return compressed_->render(output,frames);
        const auto channels=static_cast<std::size_t>(metadata().channels);std::size_t count=0;
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));
        try{while(count<wanted && status_==SourceStatus::Ok){next(output+count*channels);++count;}}
        catch(const SourceFailure& e){status_=e.status;}catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(compressed_)return compressed_->seek(target);
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=target;status_=SourceStatus::Ok;return status_;
    }
    void reset()noexcept override{if(compressed_)compressed_->reset();else {position_={0};status_=SourceStatus::Ok;}}
};
}
std::unique_ptr<AudioFrameSource> openXwb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<XwbSource>(std::move(input),limits,entry);
}
std::unique_ptr<AudioFrameSource> openXwb(ByteView bytes,const DecodeLimits& limits,const char* entry) {
    return openXwb(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entry);
}
std::vector<SourceEntry> inspectXwbEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes in(bytes,limits);return parseXwb(in,limits).entries;
}
}
