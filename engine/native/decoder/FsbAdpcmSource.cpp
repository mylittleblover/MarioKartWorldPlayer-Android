#include "decoder/FsbAdpcmFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/PsAdpcm.hpp"

namespace player2::audio {
namespace {
struct History {PsAdpcmHistory ps;DspHistory dsp;};
class FsbAdpcmSource final:public AudioFrameSource {
    static constexpr std::size_t MaximumCheckpoints=65536;
    using Histories=std::array<History,2>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes left_,right_;
    SourceMetadata metadata_;
    std::array<NintendoDspChannel,2> channels_;
    std::size_t start_{},stride_{},blockBytes_{},blockFrames_{},checkpointBlocks_{};
    bool dsp_{},planar_{};
    Histories history_{};
    std::vector<Histories> checkpoints_;
    std::array<float,56> pcm_{};
    std::uint64_t cached_{UINT64_MAX};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void block(std::uint64_t index) {
        auto history=history_;std::array<float,56> pcm{};
        const auto count=static_cast<std::size_t>(metadata_.channels);const auto step=dsp_ ? 2u : 16u;
        for(std::size_t ch=0;ch<count;++ch) {
            auto& bytes=ch ? right_ : left_;std::array<std::uint8_t,16> encoded{};
            for(std::size_t n=0;n<blockBytes_;++n) {
                const auto lane=static_cast<std::size_t>(index)*blockBytes_+n;
                const auto offset=planar_ ? start_+ch*stride_+lane : start_+(lane/step)*count*step+ch*step+lane%step;
                encoded[n]=bytes.u8(offset);
            }
            if(dsp_) {
                for(std::size_t n=0;n<blockFrames_;++n) {
                    const auto packed=encoded[1+n/2];
                    pcm[n*count+ch]=dspSample(encoded[0],n%2 ? packed&15 : packed>>4,channels_[ch],history[ch].dsp)/32768.0f;
                }
            } else {
                const auto channel=decodePsAdpcm(encoded,history[ch].ps);
                for(std::size_t n=0;n<blockFrames_;++n)pcm[n*count+ch]=channel[n];
            }
        }
        // Each original lane must finish before a decoded block is published.
        history_=history;pcm_=pcm;cached_=index;
    }
    void next(float* output) {
        const auto frame=static_cast<std::uint64_t>(position_.value),index=frame/blockFrames_;
        if(cached_!=index)block(index);
        if(output)std::copy_n(pcm_.data()+(frame%blockFrames_)*metadata_.channels,metadata_.channels,output);
        ++position_.value;
    }
public:
    FsbAdpcmSource(std::unique_ptr<EncodedSourceInput> input,std::size_t start,std::size_t end,
        SourceMetadata metadata,bool planar,bool outerAlignment,const std::array<NintendoDspChannel,2>& channels,const DecodeLimits& limits):
        input_(std::move(input)),left_(*input_,limits),right_(*input_,limits),metadata_(metadata),channels_(channels),start_(start),
        dsp_(metadata.encoding==SampleEncoding::DspAdpcm),planar_(planar) {
        validateFormat(metadata.sampleRate,metadata.channels);boundStreamDuration(metadata.duration.value,metadata.sampleRate,limits);
        if(metadata.encoding!=SampleEncoding::DspAdpcm && metadata.encoding!=SampleEncoding::PsAdpcm)fail(SourceStatus::UnsupportedCodec);
        if(start>=end || end>left_.size())fail(SourceStatus::MalformedSource);
        blockBytes_=dsp_ ? 8 : 16;blockFrames_=dsp_ ? 14 : 28;checkpointBlocks_=dsp_ ? 292 : 146;
        const auto blocks=(static_cast<std::uint64_t>(metadata.duration.value)+blockFrames_-1)/blockFrames_;
        const auto laneBytes=blocks*blockBytes_,required=laneBytes*metadata.channels;
        if(required>end-start)fail(SourceStatus::MalformedSource);
        stride_=static_cast<std::size_t>(laneBytes);
        if(planar_) {
            if((end-start)%metadata.channels)fail(SourceStatus::MalformedSource);
            stride_=(end-start)/metadata.channels;
            if((!outerAlignment && stride_!=laneBytes) || stride_-laneBytes>=32)fail(SourceStatus::UnsupportedCodec);
            for(int ch=0;ch<metadata.channels;++ch)for(auto at=start+ch*stride_+laneBytes;at<start+(ch+1)*stride_;++at)
                if(left_.u8(static_cast<std::size_t>(at)))fail(SourceStatus::UnsupportedCodec);
        } else {
            if((!outerAlignment && required!=end-start) || end-start-required>=32)fail(SourceStatus::UnsupportedCodec);
            for(auto at=start+required;at<end;++at)if(left_.u8(static_cast<std::size_t>(at)))fail(SourceStatus::UnsupportedCodec);
        }
        const auto count=(blocks+checkpointBlocks_-1)/checkpointBlocks_;
        if(sizeof(*this)>limits.maximumDecodedBytes || count>MaximumCheckpoints ||
            count>(limits.maximumDecodedBytes-sizeof(*this))/sizeof(Histories))fail(SourceStatus::ResourceLimit);
        checkpoints_.reserve(static_cast<std::size_t>(count));reset();
        for(std::uint64_t n=0;n<blocks;++n) {
            if(n%checkpointBlocks_==0)checkpoints_.push_back(history_);
            block(n);
        }
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata_.channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));std::size_t done=0;
        try{while(done<wanted){next(output+done*channels);++done;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+done*channels,(frames-done)*channels,0.0f);return done;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        const auto previous=position_;const auto history=history_;const auto pcm=pcm_;const auto cached=cached_;status_=SourceStatus::Ok;
        try {
            const auto frames=blockFrames_*checkpointBlocks_,checkpoint=std::min(static_cast<std::size_t>(target.value)/frames,checkpoints_.size()-1);
            position_.value=static_cast<std::int64_t>(checkpoint*frames);history_=checkpoints_[checkpoint];cached_=UINT64_MAX;
            while(position_.value<target.value)next(nullptr);
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=history;pcm_=pcm;cached_=cached;}
        return status_;
    }
    void reset()noexcept override {
        position_.value=0;history_={};cached_=UINT64_MAX;status_=SourceStatus::Ok;
        if(dsp_)for(int ch=0;ch<metadata_.channels;++ch)history_[ch].dsp=channels_[ch].initial;
    }
};
}
std::unique_ptr<AudioFrameSource> openFsbAdpcm(std::unique_ptr<EncodedSourceInput> input,
    std::size_t start,std::size_t end,const SourceMetadata& metadata,bool planar,bool outerAlignment,
    const std::array<NintendoDspChannel,2>& channels,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<FsbAdpcmSource>(std::move(input),start,end,metadata,planar,outerAlignment,channels,limits);
}
}
