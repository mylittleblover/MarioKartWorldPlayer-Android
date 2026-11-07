#include "decoder/NintendoStreamParser.hpp"
#include "decoder/NintendoStreamTracks.hpp"
#include "decoder/EncodedSourceBytes.hpp"

namespace player2::audio {
namespace {
class NintendoPcmInputFrameSource final:public AudioFrameSource {
    NintendoStreamLayout layout_;
    SourceStreamInfo stream_;
    std::unique_ptr<EncodedSourceInput> input_;
    std::array<std::uint8_t,32768> scratch_{};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
public:
    NintendoPcmInputFrameSource(NintendoStreamLayout layout,std::unique_ptr<EncodedSourceInput> input,NintendoStreamContainer container=NintendoStreamContainer::Bfstm):layout_(layout),input_(std::move(input)) {
        layout_.metadata.flags&=~std::uint32_t(RealtimeRender);
        if(layout_.metadata.channels>2){EncodedSourceBytes bytes(*input_,{});stream_=parseNintendoStreamTracks(bytes,layout_,container);}
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t rendered=0;
        status_=SourceStatus::Ok;
        try {
            while(rendered<wanted) {
                const auto frame=static_cast<std::size_t>(position_.value),within=frame%layout_.blockFrames;
                const auto count=std::min({wanted-rendered,layout_.blockFrames-within,scratch_.size()/layout_.width});
                const auto bytes=count*layout_.width;
                for(std::size_t ch=0;ch<channels;++ch) {
                    const auto read=input_->read({layout_.offset(frame,ch)},scratch_.data(),bytes);
                    if(read.status!=SourceStatus::Ok)fail(read.status);
                    if(read.bytes!=bytes)fail(SourceStatus::InputUnavailable);
                    const SourceBytes slice({scratch_.data(),scratch_.size()},DecodeLimits{scratch_.size()});
                    for(std::size_t n=0;n<count;++n)output[(rendered+n)*channels+ch]=bfstmPcmSample(slice,n*layout_.width,layout_.width,layout_.big);
                }
                rendered+=count;position_.value+=static_cast<std::int64_t>(count);
            }
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        // A failed channel read cannot publish a partially decoded frame window.
        std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);return rendered;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(position.value<0 || position.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=position;status_=SourceStatus::Ok;return SourceStatus::Ok;
    }
    void reset()noexcept override{position_.value=0;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openBfstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);const auto layout=parseBfstmLayout(bytes,limits);
    return projectStreamStereo(std::make_unique<NintendoPcmInputFrameSource>(layout,std::move(input)));
}
std::unique_ptr<AudioFrameSource> openBcstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);const auto layout=parseBfstmLayout(bytes,limits,false,NintendoStreamContainer::Bcstm);
    return projectStreamStereo(std::make_unique<NintendoPcmInputFrameSource>(layout,std::move(input),NintendoStreamContainer::Bcstm));
}
std::unique_ptr<AudioFrameSource> openBrstm(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);const auto layout=parseBrstmLayout(bytes,limits);
    return projectStreamStereo(std::make_unique<NintendoPcmInputFrameSource>(layout,std::move(input),NintendoStreamContainer::Brstm));
}
}
