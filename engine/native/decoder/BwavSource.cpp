#include "decoder/BwavFormat.hpp"
#include "decoder/BwavGroups.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class BwavSource final:public AudioFrameSource {
    static constexpr std::size_t CheckpointFrames=14*292,MaximumCheckpoints=65536;
    using Histories=std::array<DspHistory,MaximumNintendoStreamChannels>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    std::vector<EncodedSourceBytes> channelBytes_; // Channels1..N-1; lane0 reuses parser window.
    BwavLayout layout_;
    std::vector<DspHistory> checkpoints_;
    Histories history_{};
    SourceSamplePosition position_;
    SourceStreamInfo stream_;
    SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        auto history=history_;std::array<float,MaximumNintendoStreamChannels> pcm{};
        const auto frame=static_cast<std::size_t>(position_.value);
        for(unsigned ch=0;ch<static_cast<unsigned>(metadata().channels);++ch) {
            auto& bytes=ch ? channelBytes_[ch-1] : bytes_;
            if(layout_.dsp) {
                const auto p=layout_.starts[ch]+frame/14*8,within=frame%14;
                const auto header=bytes.u8(p),packed=bytes.u8(p+1+within/2);
                pcm[ch]=dspSample(header,within%2 ? packed&15 : packed>>4,layout_.channels[ch],history[ch])/32768.0f;
            } else pcm[ch]=dspSigned(bytes.u16(layout_.starts[ch]+frame*2))/32768.0f;
        }
        history_=history;++position_.value;if(output)std::copy_n(pcm.data(),metadata().channels,output);
    }
public:
    BwavSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),
        bytes_(*input_,limits),layout_(parseBwav(bytes_,limits)) {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        // Account for each encoded window and, for raw multichannel streams,
        // both bounded group metadata and the shared stereo projection scratch.
        const auto fixed=sizeof(*this)+(channels-1)*sizeof(EncodedSourceBytes)+(channels>2 ? 32768 : 0);
        if(fixed>limits.maximumDecodedBytes)fail(SourceStatus::ResourceLimit);
        channelBytes_.reserve(channels-1);
        for(std::size_t ch=0;ch<channels;++ch){history_[ch]=layout_.channels[ch].initial;if(ch)channelBytes_.emplace_back(*input_,limits);}
        if(channels>2)stream_=bwavStereoGroups(layout_);
        if(layout_.dsp) {
            const auto count=(std::uint64_t(metadata().duration.value)+CheckpointFrames-1)/CheckpointFrames;
            if(count>MaximumCheckpoints || count>(limits.maximumDecodedBytes-fixed)/sizeof(DspHistory)/channels)fail(SourceStatus::ResourceLimit);
            checkpoints_.reserve(static_cast<std::size_t>(count)*channels);
            while(position_.value<metadata().duration.value) {
                if(position_.value%CheckpointFrames==0)checkpoints_.insert(checkpoints_.end(),history_.begin(),history_.begin()+channels);
                next(nullptr);
            }
        }
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
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
        const auto previous=position_;const auto history=history_;status_=SourceStatus::Ok;
        try {
            if(layout_.dsp) {
                const auto checkpoint=std::min(static_cast<std::size_t>(target.value)/CheckpointFrames,checkpoints_.size()/metadata().channels-1);
                position_.value=static_cast<std::int64_t>(checkpoint*CheckpointFrames);std::copy_n(checkpoints_.data()+checkpoint*metadata().channels,metadata().channels,history_.begin());
                while(position_.value<target.value)next(nullptr);
            } else position_=target;
        }catch(const SourceFailure& error){status_=error.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=history;}return status_;
    }
    void reset()noexcept override {
        position_.value=0;status_=SourceStatus::Ok;
        for(int ch=0;ch<metadata().channels;++ch)history_[ch]=layout_.channels[ch].initial;
    }
};
}
std::unique_ptr<AudioFrameSource> openBwav(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    if(limits.maximumDecodedBytes<16384)fail(SourceStatus::ResourceLimit);
    return projectStreamStereo(std::make_unique<BwavSource>(std::move(input),limits));
}
std::unique_ptr<AudioFrameSource> openBwav(ByteView bytes,const DecodeLimits& limits) {
    return openBwav(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
