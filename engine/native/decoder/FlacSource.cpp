#include "decoder/FlacLayout.hpp"
#include "decoder/CodecAllocation.hpp"
#include "decoder/CodecInputCursor.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <dr_flac.h>
#include <cmath>

namespace player2::audio {
namespace {
class FlacFrameSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    CodecInputCursor cursor_;
    CodecAllocationBudget budget_;
    SourceMetadata metadata_;
    std::unique_ptr<drflac,decltype(&drflac_close)> decoder_{nullptr,&drflac_close};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};bool invalidState_{};
    static std::size_t read(void* owner,void* output,std::size_t count)noexcept{return static_cast<FlacFrameSource*>(owner)->cursor_.read(output,count);}
    static drflac_bool32 seekBytes(void* owner,int offset,drflac_seek_origin origin)noexcept {
        return static_cast<FlacFrameSource*>(owner)->cursor_.seek(offset,static_cast<int>(origin));
    }
    static drflac_bool32 tell(void* owner,drflac_int64* at)noexcept {if(!at)return DRFLAC_FALSE;*at=static_cast<FlacFrameSource*>(owner)->cursor_.position();return DRFLAC_TRUE;}
    SourceStatus failure()const noexcept {return cursor_.status!=SourceStatus::Ok ? cursor_.status : budget_.exhausted ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource;}
public:
    FlacFrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),cursor_(*input_) {
        EncodedSourceBytes bytes(*input_,limits);metadata_=parseFlacLayout(bytes,limits);budget_.maximum=std::min<std::size_t>(8*1024*1024,limits.maximumDecodedBytes);
        const drflac_allocation_callbacks allocation{&budget_,&CodecAllocationBudget::allocate,&CodecAllocationBudget::resize,&CodecAllocationBudget::release};
        decoder_.reset(drflac_open(&read,&seekBytes,&tell,this,&allocation));
        if(!decoder_)fail(failure());
        if(cursor_.status!=SourceStatus::Ok)fail(cursor_.status);
        if(decoder_->channels!=metadata_.channels || decoder_->sampleRate!=static_cast<std::uint32_t>(metadata_.sampleRate) ||
            decoder_->bitsPerSample!=metadata_.bitDepth || decoder_->totalPCMFrameCount!=static_cast<std::uint64_t>(metadata_.duration.value))fail(SourceStatus::MalformedSource);
        // Strict admission: tolerant CRC recovery/truncation cannot silently shorten
        // the declared PCM. Scan with one reusable stereo block, retaining no PCM.
        std::array<float,2048> scratch{};std::int64_t scanned=0;
        while(scanned<metadata_.duration.value) {
            const auto wanted=std::min<std::int64_t>(1024,metadata_.duration.value-scanned);
            const auto count=drflac_read_pcm_frames_f32(decoder_.get(),wanted,scratch.data());
            if(cursor_.status!=SourceStatus::Ok || budget_.exhausted || count!=static_cast<std::uint64_t>(wanted))fail(failure());
            for(std::size_t n=0;n<count*metadata_.channels;++n)if(!std::isfinite(scratch[n]))fail(SourceStatus::MalformedSource);
            scanned+=static_cast<std::int64_t>(count);
        }
        if(drflac_read_pcm_frames_f32(decoder_.get(),1,scratch.data())!=0 || cursor_.status!=SourceStatus::Ok)fail(failure());
        if(seek({0})!=SourceStatus::Ok)fail(status_);
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata_.channels);
        if(invalidState_) {std::fill_n(output,frames*channels,0.0f);return 0;}
        cursor_.status=SourceStatus::Ok;status_=SourceStatus::Ok;
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));
        auto count=static_cast<std::size_t>(drflac_read_pcm_frames_f32(decoder_.get(),wanted,output));
        if(cursor_.status!=SourceStatus::Ok || budget_.exhausted || count!=wanted) {status_=failure();invalidState_=true;}
        for(std::size_t n=0;n<count*channels;++n)if(!std::isfinite(output[n])) {status_=SourceStatus::MalformedSource;invalidState_=true;count=n/channels;break;}
        position_.value+=static_cast<std::int64_t>(count);std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition at)noexcept override {
        if(at.value<0 || at.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        cursor_.status=SourceStatus::Ok;
        if(!drflac_seek_to_pcm_frame(decoder_.get(),static_cast<std::uint64_t>(at.value)) || cursor_.status!=SourceStatus::Ok || budget_.exhausted) {
            status_=failure();invalidState_=true;return status_;
        }
        position_=at;status_=SourceStatus::Ok;invalidState_=false;return status_;
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openFlac(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<FlacFrameSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openFlac(ByteView bytes,const DecodeLimits& limits) {
    return openFlac(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
