#include "decoder/NintendoStreamParser.hpp"
#include "decoder/NintendoStreamTracks.hpp"

namespace player2::audio {
namespace {
class NintendoPcmFrameSource final:public AudioFrameSource {
    NintendoStreamLayout layout_;
    std::vector<std::uint8_t> bytes_;
    SourceSamplePosition position_;
    SourceStreamInfo stream_;
public:
    NintendoPcmFrameSource(NintendoStreamLayout layout,ByteView bytes,NintendoStreamContainer container=NintendoStreamContainer::Bfstm):layout_(layout),bytes_(bytes.data,bytes.data+bytes.size) {
        if(layout_.metadata.channels>2){SourceBytes input(bytes,{});stream_=parseNintendoStreamTracks(input,layout_,container);}
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
    SourceSamplePosition position()const noexcept override{return position_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto rendered=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));
        const auto channels=static_cast<std::size_t>(metadata().channels);
        const SourceBytes bytes({bytes_.data(),bytes_.size()},DecodeLimits{bytes_.size()});
        for(std::size_t n=0;n<rendered;++n)for(std::size_t ch=0;ch<channels;++ch)
            output[n*channels+ch]=bfstmPcmSample(bytes,layout_.offset(static_cast<std::size_t>(position_.value)+n,ch),layout_.width,layout_.big);
        std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);
        position_.value+=static_cast<std::int64_t>(rendered);return rendered;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(position.value<0 || position.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        position_=position;return SourceStatus::Ok;
    }
    void reset()noexcept override{position_.value=0;}
};
}
std::unique_ptr<AudioFrameSource> openBfstm(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);const auto layout=parseBfstmLayout(input,limits);
    return projectStreamStereo(std::make_unique<NintendoPcmFrameSource>(layout,bytes));
}
std::unique_ptr<AudioFrameSource> openBcstm(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);const auto layout=parseBfstmLayout(input,limits,false,NintendoStreamContainer::Bcstm);
    return projectStreamStereo(std::make_unique<NintendoPcmFrameSource>(layout,bytes,NintendoStreamContainer::Bcstm));
}
std::unique_ptr<AudioFrameSource> openBrstm(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);const auto layout=parseBrstmLayout(input,limits);
    return projectStreamStereo(std::make_unique<NintendoPcmFrameSource>(layout,bytes,NintendoStreamContainer::Brstm));
}
}
