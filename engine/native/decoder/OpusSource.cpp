#include "decoder/OpusFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cmath>

namespace player2::audio {
namespace {
struct OpusDelete {void operator()(OpusDecoder* value)const noexcept{opus_decoder_destroy(value);}};
struct OpusState {
    std::unique_ptr<OpusDecoder,OpusDelete> decoder;
    EncodedSourceBytes bytes;
    std::array<std::uint8_t,MaximumOpusPacket> encoded{};
    std::array<float,5760*2> pcm{};
    std::size_t nextPacket{};std::uint64_t start{},end{};
    OpusState(EncodedSourceInput& input,const DecodeLimits& limits,const OpusLayout& layout):bytes(input,limits) {
        int error=OPUS_OK;decoder.reset(opus_decoder_create(48000,layout.metadata.channels,&error));
        if(error==OPUS_ALLOC_FAIL || !decoder)fail(SourceStatus::ResourceLimit);
        if(error!=OPUS_OK || opus_decoder_ctl(decoder.get(),OPUS_SET_GAIN(layout.gain))!=OPUS_OK)fail(SourceStatus::MalformedSource);
    }
};
class OpusSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    DecodeLimits limits_;
    OpusLayout layout_;
    std::unique_ptr<OpusState> state_;
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void prepare(OpusState& state,std::uint64_t raw) {
        while(raw>=state.end) {
            if(state.nextPacket>=layout_.packets.size())fail(SourceStatus::MalformedSource);
            const auto& packet=layout_.packets[state.nextPacket];std::size_t at=0;
            for(const auto& span:packet.spans){state.bytes.copy(span.offset,state.encoded.data()+at,span.size);at+=span.size;}
            if(at!=packet.bytes)fail(SourceStatus::MalformedSource);
            const auto count=opus_decode_float(state.decoder.get(),state.encoded.data(),static_cast<opus_int32>(at),state.pcm.data(),5760,0);
            if(count!=packet.frames)fail(SourceStatus::MalformedSource);
            for(std::size_t n=0;n<static_cast<std::size_t>(count)*metadata().channels;++n)
                if(!std::isfinite(state.pcm[n]))fail(SourceStatus::MalformedSource);
            state.start=state.end;state.end+=static_cast<unsigned>(count);++state.nextPacket;
        }
    }
public:
    OpusSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,OpusLayout layout):input_(std::move(input)),limits_(limits),layout_(std::move(layout)) {
        state_=std::make_unique<OpusState>(*input_,limits,layout_);
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t count=0;
        try {
            while(count<wanted) {
                const auto raw=layout_.preSkip+static_cast<std::uint64_t>(position_.value);prepare(*state_,raw);
                const auto take=std::min(wanted-count,static_cast<std::size_t>(state_->end-raw));
                std::copy_n(state_->pcm.data()+(raw-state_->start)*channels,take*channels,output+count*channels);
                position_.value+=static_cast<std::int64_t>(take);count+=take;
            }
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;}
         catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        try {
            auto next=std::make_unique<OpusState>(*input_,limits_,layout_);
            if(target.value<metadata().duration.value)prepare(*next,layout_.preSkip+static_cast<std::uint64_t>(target.value));
            state_=std::move(next);position_=target;status_=SourceStatus::Ok;return status_;
        }catch(const SourceFailure& failure){return failure.status;}
         catch(const std::bad_alloc&){return SourceStatus::ResourceLimit;}
         catch(...){return SourceStatus::MalformedSource;}
    }
    void reset()noexcept override{const auto result=seek({0});if(result!=SourceStatus::Ok)status_=result;}
};
}
std::unique_ptr<AudioFrameSource> makePacketOpusSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,OpusLayout layout) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<OpusSource>(std::move(input),limits,std::move(layout));
}
std::unique_ptr<AudioFrameSource> openOpus(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes bytes(*input,limits);auto layout=parseOpus(bytes,limits);
    return makePacketOpusSource(std::move(input),limits,std::move(layout));
}
std::unique_ptr<AudioFrameSource> openOpus(ByteView bytes,const DecodeLimits& limits) {
    return openOpus(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
