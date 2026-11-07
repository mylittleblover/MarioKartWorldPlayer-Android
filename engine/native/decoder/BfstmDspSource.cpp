#include "decoder/BrstmDspFormat.hpp"
#include "decoder/BfstmDspFormat.hpp"
#include "decoder/NintendoStreamTracks.hpp"
#include "decoder/NintendoStreamParser.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/StandaloneDspFormat.hpp"

namespace player2::audio {
namespace {
// Encoded original is retained, never replaced by a full decoded PCM allocation.
class DspMemoryInput final:public EncodedSourceInput {
    std::vector<std::uint8_t> bytes_;
public:
    explicit DspMemoryInput(ByteView bytes):bytes_(bytes.data,bytes.data+bytes.size){}
    std::uint64_t byteLength()const noexcept override{return bytes_.size();}
    SourceReadResult read(SourceByteOffset offset,std::uint8_t* output,std::size_t count)noexcept override {
        if(offset.value>bytes_.size() || count>bytes_.size()-offset.value)return {SourceStatus::MalformedSource,0};
        std::copy_n(bytes_.data()+offset.value,count,output);return {SourceStatus::Ok,count};
    }
};
class NintendoDspFrameSource final:public AudioFrameSource {
    static constexpr std::size_t CheckpointFrames=14*292,MaximumCheckpoints=65536;
    using Histories=std::array<DspHistory,MaximumNintendoStreamChannels>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    std::vector<EncodedSourceBytes> channelBytes_;
    NintendoStreamLayout layout_;
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> channels_;
    std::vector<DspHistory> checkpoints_;
    Histories history_{};
    SourceSamplePosition position_;
    SourceStreamInfo stream_;
    SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        auto histories=history_;std::array<float,MaximumNintendoStreamChannels> pcm{};
        const auto frame=static_cast<std::size_t>(position_.value),within=(frame%layout_.blockFrames)%14;
        for(std::size_t ch=0;ch<static_cast<std::size_t>(metadata().channels);++ch) {
            auto& bytes=channelBytes_[ch];
            const auto offset=nintendoDspOffset(layout_,frame,ch);
            const auto header=bytes.u8(offset),packed=bytes.u8(offset+1+within/2);
            pcm[ch]=dspSample(header,within%2 ? packed&15 : packed>>4,channels_[ch],histories[ch])/32768.0f;
        }
        history_=histories;++position_.value;
        if(output)std::copy_n(pcm.data(),metadata().channels,output);
    }
public:
    NintendoDspFrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,
        NintendoStreamContainer container=NintendoStreamContainer::Bfstm,bool standalone=false):
        input_(std::move(input)),bytes_(*input_,limits),
        layout_(standalone ? parseStandaloneDspLayout(bytes_,limits) : parseNintendoStreamLayout(bytes_,limits,true,container)),
        channels_(standalone ? parseStandaloneDspChannels(bytes_) : container==NintendoStreamContainer::Brstm ? parseBrstmDspChannels(bytes_,layout_) : parseNintendoDspChannels(bytes_,layout_)) {
        if(!standalone && metadata().channels>2)stream_=parseNintendoStreamTracks(bytes_,layout_,container);
        const auto count=(static_cast<std::uint64_t>(metadata().duration.value)+CheckpointFrames-1)/CheckpointFrames;
        const auto channels=static_cast<std::size_t>(metadata().channels);
        const auto fixed=sizeof(*this)+channels*sizeof(EncodedSourceBytes);
        if(fixed>limits.maximumDecodedBytes || count>MaximumCheckpoints ||
            count>(limits.maximumDecodedBytes-fixed)/sizeof(DspHistory)/channels)fail(SourceStatus::ResourceLimit);
        checkpoints_.reserve(static_cast<std::size_t>(count)*channels);
        channelBytes_.reserve(channels);
        for(std::size_t ch=0;ch<channels;++ch){history_[ch]=channels_[ch].initial;channelBytes_.emplace_back(*input_,limits);}
        // Control-only bounded scan validates every predictor and builds small history
        // snapshots; the SEEK block's untrusted contexts cannot alter canonical PCM.
        while(position_.value<metadata().duration.value) {
            if(position_.value%CheckpointFrames==0)checkpoints_.insert(checkpoints_.end(),history_.begin(),history_.begin()+channels);
            next(nullptr);
        }
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));
        std::size_t rendered=0;status_=SourceStatus::Ok;
        try {while(rendered<wanted){next(output+rendered*channels);++rendered;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);return rendered;
    }
    SourceStatus seek(SourceSamplePosition position)noexcept override {
        if(position.value<0 || position.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        const auto previous=position_;const auto history=history_;
        status_=SourceStatus::Ok;
        try {
            const auto checkpoint=std::min(static_cast<std::size_t>(position.value)/CheckpointFrames,checkpoints_.size()/metadata().channels-1);
            position_.value=static_cast<std::int64_t>(checkpoint*CheckpointFrames);std::copy_n(checkpoints_.data()+checkpoint*metadata().channels,metadata().channels,history_.begin());
            while(position_.value<position.value)next(nullptr);
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=history;}return status_;
    }
    void reset()noexcept override{position_.value=0;std::copy_n(checkpoints_.data(),metadata().channels,history_.begin());status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openBfstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return projectStreamStereo(std::make_unique<NintendoDspFrameSource>(std::move(input),limits));
}
std::unique_ptr<AudioFrameSource> openBfstmDsp(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes checked(bytes,limits);static_cast<void>(checked);
    return openBfstmDsp(std::make_unique<DspMemoryInput>(bytes),limits);
}
std::unique_ptr<AudioFrameSource> openBcstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return projectStreamStereo(std::make_unique<NintendoDspFrameSource>(std::move(input),limits,NintendoStreamContainer::Bcstm));
}
std::unique_ptr<AudioFrameSource> openBcstmDsp(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes checked(bytes,limits);static_cast<void>(checked);
    return openBcstmDsp(std::make_unique<DspMemoryInput>(bytes),limits);
}
std::unique_ptr<AudioFrameSource> openBrstmDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return projectStreamStereo(std::make_unique<NintendoDspFrameSource>(std::move(input),limits,NintendoStreamContainer::Brstm));
}
std::unique_ptr<AudioFrameSource> openBrstmDsp(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes checked(bytes,limits);static_cast<void>(checked);
    return openBrstmDsp(std::make_unique<DspMemoryInput>(bytes),limits);
}
std::unique_ptr<AudioFrameSource> openNintendoDsp(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<NintendoDspFrameSource>(std::move(input),limits,NintendoStreamContainer::Bfstm,true);
}
std::unique_ptr<AudioFrameSource> openNintendoDsp(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes checked(bytes,limits);static_cast<void>(checked);
    return openNintendoDsp(std::make_unique<DspMemoryInput>(bytes),limits);
}

}
