#include "decoder/NintendoWaveFormat.hpp"
#include "decoder/SwavFormat.hpp"
#include "decoder/RwavFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class NintendoWaveSource final:public AudioFrameSource {
    static constexpr std::size_t CheckpointFrames=14*292,MaximumCheckpoints=65536;
    using Histories=std::array<DspHistory,2>;
    using ImaHistories=std::array<ImaHistory,2>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes left_,right_;NintendoWaveLayout layout_;
    std::vector<Histories> checkpoints_;Histories history_{};
    std::vector<ImaHistories> imaCheckpoints_;ImaHistories imaHistory_{};
    SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};
    void next(float* output) {
        auto history=history_;auto ima=imaHistory_;std::array<float,2> pcm{};const auto frame=static_cast<std::size_t>(position_.value);
        for(unsigned ch=0;ch<static_cast<unsigned>(metadata().channels);++ch) {
            auto& bytes=ch ? right_ : left_;
            if(layout_.codec==2) {
                const auto at=layout_.starts[ch]+frame/14*8,within=frame%14;
                const auto packed=bytes.u8(at+1+within/2);
                pcm[ch]=dspSample(bytes.u8(at),within%2 ? packed&15 : packed>>4,layout_.channels[ch],history[ch])/32768.0f;
            } else if(layout_.codec==3) {
                const auto packed=bytes.u8(layout_.starts[ch]+frame/2);
                pcm[ch]=imaSample(frame%2 ? packed>>4 : packed&15,ima[ch],layout_.ndsIma)/32768.0f;
            } else pcm[ch]=bfstmPcmSample(bytes,layout_.starts[ch]+frame*layout_.width,layout_.width,layout_.big);
        }
        history_=history;imaHistory_=ima;++position_.value;if(output)std::copy_n(pcm.data(),metadata().channels,output);
    }
public:
    NintendoWaveSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,std::optional<NintendoWaveLayout> layout=std::nullopt):input_(std::move(input)),
        left_(*input_,limits),right_(*input_,limits),layout_(layout ? *layout : left_.magic(0,"SWAV") ? parseSwav(left_,limits) : left_.magic(0,"RWAV") ? parseRwav(left_,limits) : parseNintendoWave(left_,limits)) {
        if(limits.maximumDecodedBytes<16384)fail(SourceStatus::ResourceLimit);
        for(unsigned ch=0;ch<2;++ch)history_[ch]=layout_.channels[ch].initial;
        imaHistory_=layout_.ima;
        if(layout_.codec>=2) {
            const auto count=(std::uint64_t(metadata().duration.value)+CheckpointFrames-1)/CheckpointFrames;
            if(count>MaximumCheckpoints || count>(limits.maximumDecodedBytes-16384)/sizeof(Histories))fail(SourceStatus::ResourceLimit);
            if(layout_.codec==2)checkpoints_.reserve(static_cast<std::size_t>(count));
            else imaCheckpoints_.reserve(static_cast<std::size_t>(count));
            while(position_.value<metadata().duration.value) {
                if(position_.value%CheckpointFrames==0) {
                    if(layout_.codec==2)checkpoints_.push_back(history_);else imaCheckpoints_.push_back(imaHistory_);
                }
                next(nullptr);
            }
        }
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
        catch(const SourceFailure& error){status_=error.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        const auto oldPosition=position_;const auto oldHistory=history_;const auto oldIma=imaHistory_;status_=SourceStatus::Ok;
        try {
            if(layout_.codec>=2) {
                const auto count=layout_.codec==2 ? checkpoints_.size() : imaCheckpoints_.size();
                const auto n=std::min(static_cast<std::size_t>(target.value)/CheckpointFrames,count-1);
                position_.value=static_cast<std::int64_t>(n*CheckpointFrames);
                if(layout_.codec==2)history_=checkpoints_[n];else imaHistory_=imaCheckpoints_[n];
                while(position_.value<target.value)next(nullptr);
            } else position_=target;
        }catch(const SourceFailure& error){status_=error.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=oldPosition;history_=oldHistory;imaHistory_=oldIma;}return status_;
    }
    void reset()noexcept override {
        position_.value=0;status_=SourceStatus::Ok;
        for(unsigned ch=0;ch<2;++ch)history_[ch]=layout_.channels[ch].initial;
        imaHistory_=layout_.ima;
    }
};
}
std::unique_ptr<AudioFrameSource> makeNintendoWaveSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,NintendoWaveLayout layout) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<NintendoWaveSource>(std::move(input),limits,layout);
}
std::unique_ptr<AudioFrameSource> openNintendoWave(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<NintendoWaveSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openNintendoWave(ByteView bytes,const DecodeLimits& limits) {
    return openNintendoWave(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
