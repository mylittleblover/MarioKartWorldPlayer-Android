#include "decoder/LegacyFsbFormat.hpp"
#include "decoder/FsbMpegFormat.hpp"
#include "decoder/FsbAdpcmFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/WaveLayout.hpp"
#include <cstring>

namespace player2::audio {
namespace {
struct SampleBytes {std::array<std::uint8_t,2> data{};std::uint8_t u8(std::size_t n)const noexcept{return data[n];}};
class LegacyFsbSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    LegacyFsbIndex index_;
    const LegacyFsbSample* selected_{};
    SourceMetadata metadata_;
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    XboxImaBlock ima_;
    std::unique_ptr<AudioFrameSource> decoder_;
    void next(float* output) {
        if(metadata_.encoding==SampleEncoding::ImaAdpcm){ima_.sample(bytes_,selected_->start,metadata_.channels,position_.value,output);++position_.value;return;}
        const unsigned channels=metadata_.channels,width=metadata_.bitDepth/8;
        const auto start=selected_->start+static_cast<std::size_t>(position_.value)*channels*width;
        std::array<float,2> pcm{};
        for(unsigned ch=0;ch<channels;++ch) {
            SampleBytes sample;
            for(unsigned n=0;n<width;++n)sample.data[n]=bytes_.u8(start+ch*width+(index_.big ? width-1-n : n));
            if(width==1 && metadata_.encoding==SampleEncoding::PcmSigned)sample.data[0]^=0x80;
            pcm[ch]=wavePcmSample(sample,0,metadata_.bitDepth,false);
        }
        ++position_.value;if(output)std::copy_n(pcm.data(),channels,output);
    }
public:
    LegacyFsbSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry):
        input_(std::move(input)),bytes_(*input_,limits),index_(parseLegacyFsb(bytes_,limits)) {
        if(!entry)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(index_.entries.begin(),index_.entries.end(),[&](const auto& e){return std::strcmp(e.id.data(),entry)==0;});
        if(found==index_.entries.end())fail(SourceStatus::EntryMissing);
        selected_=&index_.samples[static_cast<std::size_t>(found-index_.entries.begin())];
        metadata_=legacyFsbMetadata(index_,*selected_,limits);
        if(metadata_.encoding==SampleEncoding::MpegLayer3){auto child=limits;child.maximumDecodedBytes-=index_.entries.size()*512;decoder_=openFsbMpeg(std::move(input_),selected_->start,selected_->start+selected_->size,metadata_,index_.mpegPadding,false,child);}
        if(metadata_.encoding==SampleEncoding::DspAdpcm || metadata_.encoding==SampleEncoding::PsAdpcm) {
            const auto channels=metadata_.encoding==SampleEncoding::DspAdpcm ? parseFsbDspChannels(bytes_,selected_->header+index_.minimumHeader,metadata_.channels) : std::array<NintendoDspChannel,2>{};
            auto child=limits;child.maximumDecodedBytes-=index_.entries.size()*512;
            decoder_=openFsbAdpcm(std::move(input_),selected_->start,selected_->start+selected_->size,metadata_,index_.planar,false,channels,child);
        }
        if(metadata_.encoding==SampleEncoding::ImaAdpcm)validateXboxIma(bytes_,selected_->start,selected_->size,selected_->frames,selected_->channels);
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const std::vector<SourceEntry>* sourceEntries()const noexcept override{return &index_.entries;}
    SourceSamplePosition position()const noexcept override{return decoder_ ? decoder_->position() : position_;}
    SourceStatus renderStatus()const noexcept override{return decoder_ ? decoder_->renderStatus() : status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        if(decoder_)return decoder_->render(output,frames);
        const auto channels=static_cast<std::size_t>(metadata_.channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));std::size_t count=0;
        try{while(count<wanted){next(output+count*channels);++count;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(decoder_)return decoder_->seek(position);
        if(position.value<0 || position.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        position_=position;status_=SourceStatus::Ok;return status_;
    }
    void reset()noexcept override{if(decoder_)decoder_->reset();position_.value=0;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openLegacyFsb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entry) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<LegacyFsbSource>(std::move(input),limits,entry);
}
std::unique_ptr<AudioFrameSource> openLegacyFsb(ByteView bytes,const DecodeLimits& limits,const char* entry) {
    return openLegacyFsb(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entry);
}
std::vector<SourceEntry> inspectLegacyFsbEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parseLegacyFsb(input,limits).entries;
}
}
