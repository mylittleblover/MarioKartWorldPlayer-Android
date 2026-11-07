#include "decoder/FsbMpegFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/Mp3Layout.hpp"

namespace player2::audio {
namespace {
struct Frame {std::size_t physical{},logical{},bytes{};};
// A transient bounded view of the original lease; strips only validated FMOD
// frame alignment. The existing MP3 Source owns decoding/history/PCM/Seek.
class FsbMpegInput final:public EncodedSourceInput {
    std::unique_ptr<EncodedSourceInput> root_;
    std::vector<Frame> frames_;
    std::size_t length_{};
    std::uint64_t samples_{};
    std::size_t budget_{};
public:
    FsbMpegInput(std::unique_ptr<EncodedSourceInput> input,std::size_t start,std::size_t end,
        const SourceMetadata& expected,unsigned padding,bool outerAlignment,const DecodeLimits& limits):root_(std::move(input)) {
        EncodedSourceBytes bytes(*root_,limits);
        if(start>=end || end>bytes.size() || (padding!=0 && padding!=2 && padding!=4))fail(SourceStatus::MalformedSource);
        const auto slots=limits.maximumDecodedBytes/4/sizeof(Frame); // Reserve the rest for decoder/checkpoints.
        std::size_t cursor=start;int frameSamples=0;
        while(cursor<end) {
            // FSB5's outer sample alignment is at most31 zero bytes; legacy
            // samples end exactly after their final padded MPEG frame.
            if(end-cursor<4 || bytes.u32(cursor,true)==0) {
                if(!outerAlignment || end-cursor>=32)fail(SourceStatus::UnsupportedCodec);
                while(cursor<end)if(bytes.u8(cursor++))fail(SourceStatus::MalformedSource);
                break;
            }
            const auto frame=mp3Header(bytes.u32(cursor,true));
            if(frame.rate!=expected.sampleRate || frame.channels!=expected.channels || (frameSamples && frameSamples!=frame.samples))fail(SourceStatus::MalformedSource);
            frameSamples=frame.samples;
            const auto size=static_cast<std::size_t>(frame.bytes),aligned=padding ? ((size+padding-1)/padding)*padding : size;
            if(aligned>end-cursor)fail(SourceStatus::MalformedSource);
            for(auto at=cursor+size;at<cursor+aligned;++at)if(bytes.u8(at))fail(SourceStatus::UnsupportedCodec);
            if(frames_.size()>=1048576 || frames_.size()>=slots)fail(SourceStatus::ResourceLimit);
            if(frames_.size()==frames_.capacity())frames_.reserve(std::min(slots,std::max<std::size_t>(16,frames_.capacity()*2)));
            frames_.push_back({cursor,length_,size});length_+=size;samples_+=frame.samples;cursor+=aligned;
        }
        if(!samples_ || expected.duration.value<=0 || static_cast<std::uint64_t>(expected.duration.value)>samples_ ||
            samples_-expected.duration.value>=static_cast<unsigned>(frameSamples))fail(SourceStatus::MalformedSource);
        budget_=sizeof(*this)+frames_.capacity()*sizeof(Frame);
    }
    std::uint64_t byteLength()const noexcept override{return length_;}
    std::uint64_t samples()const noexcept{return samples_;}
    std::size_t budget()const noexcept{return budget_;}
    SourceReadResult read(SourceByteOffset offset,std::uint8_t* output,std::size_t count)noexcept override {
        if(offset.value>length_ || count>length_-offset.value)return {SourceStatus::MalformedSource,0};
        if(!count)return {SourceStatus::Ok,0};
        auto frame=std::upper_bound(frames_.begin(),frames_.end(),offset.value,[](auto at,const Frame& f){return at<f.logical;});
        if(frame==frames_.begin())return {SourceStatus::MalformedSource,0};
        --frame;std::size_t done=0;
        while(done<count && frame!=frames_.end()) {
            const auto within=static_cast<std::size_t>(offset.value+done)-frame->logical;
            if(within>=frame->bytes)return {SourceStatus::MalformedSource,done};
            const auto size=std::min(count-done,frame->bytes-within);
            const auto got=root_->read({frame->physical+within},output+done,size);
            if(got.status!=SourceStatus::Ok || got.bytes!=size)return {got.status==SourceStatus::Ok ? SourceStatus::InputUnavailable : got.status,done};
            done+=size;++frame;
        }
        return {done==count ? SourceStatus::Ok : SourceStatus::MalformedSource,done};
    }
};
class FsbMpegSource final:public AudioFrameSource {
    std::unique_ptr<AudioFrameSource> decoder_;
    SourceMetadata metadata_;
public:
    FsbMpegSource(std::unique_ptr<AudioFrameSource> decoder,SourceMetadata metadata):decoder_(std::move(decoder)),metadata_(metadata){}
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return decoder_->position();}
    SourceStatus renderStatus()const noexcept override{return decoder_->renderStatus();}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position().value));
        const auto got=decoder_->render(output,wanted);
        std::fill_n(output+got*metadata_.channels,(frames-got)*metadata_.channels,0.0f);return got;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        return decoder_->seek(target);
    }
    void reset()noexcept override{decoder_->reset();}
};
}
std::unique_ptr<AudioFrameSource> openFsbMpeg(std::unique_ptr<EncodedSourceInput> input,
    std::size_t start,std::size_t end,const SourceMetadata& metadata,unsigned padding,bool outerAlignment,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    auto view=std::make_unique<FsbMpegInput>(std::move(input),start,end,metadata,padding,outerAlignment,limits);
    const auto samples=view->samples();const auto budget=view->budget();
    if(budget>limits.maximumDecodedBytes)fail(SourceStatus::ResourceLimit);
    auto childLimits=limits;childLimits.maximumDecodedBytes-=budget;
    auto decoder=openMp3(std::move(view),childLimits);
    // FSB has no implicit encoder delay. Refuse a Xing/LAME-trimmed child rather
    // than silently moving Source coordinates or sample loop boundaries.
    if(decoder->metadata().duration.value!=static_cast<std::int64_t>(samples))fail(SourceStatus::UnsupportedCodec);
    return std::make_unique<FsbMpegSource>(std::move(decoder),metadata);
}
}
