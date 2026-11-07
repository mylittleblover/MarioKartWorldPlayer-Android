#include "decoder/VorbisLayout.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#define STB_VORBIS_HEADER_ONLY
#define STB_VORBIS_NO_STDIO
#define STB_VORBIS_NO_INTEGER_CONVERSION
#define STB_VORBIS_NO_PULLDATA_API
#include <stb_vorbis.c>
#include <cmath>

namespace player2::audio {
namespace {
class VorbisFrameSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;VorbisLayout layout_;
    std::vector<std::uint8_t> window_;
    std::vector<std::max_align_t> arena_;
    std::unique_ptr<stb_vorbis,decltype(&stb_vorbis_close)> decoder_{nullptr,&stb_vorbis_close};
    std::uint64_t readOffset_{};std::size_t head_{},tail_{},consumed_{},available_{};
    std::uint64_t encodedLength_{};
    float** pcm_{};SourceSamplePosition position_;SourceStatus status_{SourceStatus::Ok};bool invalidState_{};
    static SourceStatus codecStatus(int error)noexcept {
        return error==VORBIS_outofmem ? SourceStatus::ResourceLimit :
            error==VORBIS_feature_not_supported ? SourceStatus::UnsupportedCodec : SourceStatus::MalformedSource;
    }
    bool fill() {
        if(input_->byteLength()!=encodedLength_ || readOffset_>encodedLength_)fail(SourceStatus::InputChanged);
        if(head_) {std::memmove(window_.data(),window_.data()+head_,tail_-head_);tail_-=head_;head_=0;}
        if(readOffset_==input_->byteLength())return false;
        if(tail_==window_.size())fail(SourceStatus::ResourceLimit);
        const auto count=std::min<std::size_t>({32768,window_.size()-tail_,static_cast<std::size_t>(input_->byteLength()-readOffset_)});
        const auto result=input_->read({readOffset_},window_.data()+tail_,count);
        if(result.status!=SourceStatus::Ok || result.bytes!=count)fail(result.status==SourceStatus::Ok ? SourceStatus::InputUnavailable : result.status);
        readOffset_+=count;tail_+=count;return true;
    }
    void reopen() {
        decoder_.reset();head_=tail_=0;readOffset_=0;consumed_=available_=0;pcm_=nullptr;
        while(tail_<layout_.headerPrefix)if(!fill())fail(SourceStatus::MalformedSource);
        const stb_vorbis_alloc allocation{reinterpret_cast<char*>(arena_.data()),static_cast<int>(arena_.size()*sizeof(std::max_align_t))};
        int used=0,error=0;decoder_.reset(stb_vorbis_open_pushdata(window_.data(),static_cast<int>(tail_),&used,&error,&allocation));
        if(!decoder_)fail(codecStatus(error));
        const auto info=stb_vorbis_get_info(decoder_.get());
        if(used<=0 || static_cast<std::size_t>(used)>tail_ || info.channels!=metadata().channels || info.sample_rate!=static_cast<std::uint32_t>(metadata().sampleRate) ||
            info.max_frame_size<=0 || info.max_frame_size>8192)fail(SourceStatus::MalformedSource);
        head_=used;position_={0};status_=SourceStatus::Ok;invalidState_=false;
    }
    bool decode() {
        consumed_=available_=0;
        for(;;) {
            if(head_==tail_ && !fill())return false;
            int channels=0,samples=0;float** output=nullptr;
            const auto used=stb_vorbis_decode_frame_pushdata(decoder_.get(),window_.data()+head_,static_cast<int>(tail_-head_),&channels,&output,&samples);
            const auto error=stb_vorbis_get_error(decoder_.get());
            if(error!=VORBIS__no_error && error!=VORBIS_need_more_data)fail(codecStatus(error));
            if(used<0 || static_cast<std::size_t>(used)>tail_-head_ || samples<0 || samples>8192 || (samples && (channels!=metadata().channels || !output)))fail(SourceStatus::MalformedSource);
            head_+=used;
            if(samples) {
                for(int ch=0;ch<channels;++ch) {if(!output[ch])fail(SourceStatus::MalformedSource);for(int n=0;n<samples;++n)if(!std::isfinite(output[ch][n]))fail(SourceStatus::MalformedSource);}
                pcm_=output;available_=samples;return true;
            }
            if(!used && !fill()) {if(head_!=tail_)fail(SourceStatus::MalformedSource);return false;}
        }
    }
    void advance(SourceSamplePosition target) {
        while(position_.value<target.value) {
            if(consumed_==available_ && !decode())fail(SourceStatus::MalformedSource);
            const auto count=std::min<std::size_t>(target.value-position_.value,available_-consumed_);
            consumed_+=count;position_.value+=static_cast<std::int64_t>(count);
        }
    }
public:
    VorbisFrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)) {
        EncodedSourceBytes bytes(*input_,limits);encodedLength_=input_->byteLength();layout_=parseVorbisLayout(bytes,limits);
        // The push decoder's entire allocation arena is explicit. File sources
        // retain at most a bounded encoded window, never the complete source.
        const auto budget=std::min<std::size_t>(9*1024*1024,limits.maximumDecodedBytes);
        if(budget<MaximumVorbisWindow+65536)fail(SourceStatus::ResourceLimit);
        window_.resize(MaximumVorbisWindow);arena_.resize((budget-window_.size())/sizeof(std::max_align_t));reopen();
        std::int64_t frames=0;
        while(decode()) {
            if(available_>static_cast<std::uint64_t>(metadata().duration.value-frames))fail(SourceStatus::MalformedSource);
            frames+=available_;
        }
        if(frames!=metadata().duration.value)fail(SourceStatus::MalformedSource);
        reopen();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        std::size_t rendered=0;const auto channels=static_cast<std::size_t>(metadata().channels);
        try {
            if(!invalidState_) {
                const auto wanted=std::min<std::size_t>(frames,metadata().duration.value-position_.value);status_=SourceStatus::Ok;
                while(rendered<wanted) {
                    if(consumed_==available_ && !decode())fail(SourceStatus::MalformedSource);
                    const auto count=std::min(wanted-rendered,available_-consumed_);
                    for(std::size_t n=0;n<count;++n)for(std::size_t ch=0;ch<channels;++ch)output[(rendered+n)*channels+ch]=pcm_[ch][consumed_+n];
                    consumed_+=count;rendered+=count;
                }
            }
        }catch(const SourceFailure& failure){status_=failure.status;invalidState_=true;}
         catch(...){status_=SourceStatus::MalformedSource;invalidState_=true;}
        position_.value+=static_cast<std::int64_t>(rendered);std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);return rendered;
    }
    SourceStatus seek(SourceSamplePosition at)noexcept override {
        if(at.value<0 || at.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        const auto before=position_;
        try {if(invalidState_ || at.value<position_.value)reopen();advance(at);status_=SourceStatus::Ok;invalidState_=false;}
        catch(const SourceFailure& failure){status_=failure.status;position_=before;invalidState_=true;}
        catch(...){status_=SourceStatus::MalformedSource;position_=before;invalidState_=true;}
        return status_;
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openVorbis(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<VorbisFrameSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openVorbis(ByteView bytes,const DecodeLimits& limits) {
    return openVorbis(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
