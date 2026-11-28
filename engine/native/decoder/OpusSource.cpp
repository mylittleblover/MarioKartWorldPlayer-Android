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
    static constexpr std::uint64_t kSeekPreRollFrames=5760;
    std::unique_ptr<EncodedSourceInput> input_;
    DecodeLimits limits_;
    OpusLayout layout_;
    std::vector<std::uint64_t> packetEnds_;
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
        packetEnds_.reserve(layout_.packets.size());
        std::uint64_t total=0;
        for(const auto& packet:layout_.packets){total+=static_cast<std::uint64_t>(packet.frames);packetEnds_.push_back(total);}
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
        if(state_ && status_==SourceStatus::Ok && target.value==position_.value)return SourceStatus::Ok;
        const auto rawTarget=layout_.preSkip+static_cast<std::uint64_t>(target.value);
        if(state_ && status_==SourceStatus::Ok && target.value>=position_.value &&
            (rawTarget<state_->end || rawTarget-state_->end<=kSeekPreRollFrames)) {
            try {
                if(target.value<metadata().duration.value)prepare(*state_,rawTarget);
                position_=target;status_=SourceStatus::Ok;return status_;
            }catch(const SourceFailure& failure){return failure.status;}
             catch(const std::bad_alloc&){return SourceStatus::ResourceLimit;}
             catch(...){return SourceStatus::MalformedSource;}
        }
        try {
            auto next=std::make_unique<OpusState>(*input_,limits_,layout_);
            if(target.value<metadata().duration.value && !packetEnds_.empty()) {
                const auto preRollStart=rawTarget>kSeekPreRollFrames ? rawTarget-kSeekPreRollFrames : 0ULL;
                const auto it=std::upper_bound(packetEnds_.begin(),packetEnds_.end(),preRollStart);
                const auto startIdx=static_cast<std::size_t>(it-packetEnds_.begin());
                if(startIdx<layout_.packets.size()) {
                    next->nextPacket=startIdx;
                    const auto origin=startIdx>0 ? packetEnds_[startIdx-1] : 0ULL;
                    next->start=origin;
                    next->end=origin;
                }
                prepare(*next,rawTarget);
            }
            state_=std::move(next);position_=target;status_=SourceStatus::Ok;return status_;
        }catch(const SourceFailure& failure){return failure.status;}
         catch(const std::bad_alloc&){return SourceStatus::ResourceLimit;}
         catch(...){return SourceStatus::MalformedSource;}
    }
    void reset()noexcept override{const auto result=seek({0});if(result!=SourceStatus::Ok)status_=result;}
};

// One independent packet chain, decoded by its own mono decoder.
//
// A BWAV codec-2 file stores each speaker as a *separate* mono Opus stream in its own region, not as one
// interleaved multichannel stream. The jukebox corpus is exactly this: two regions, 960-frame CELT packets,
// a 120-sample pre-skip each. Reading the pair as one stream would play one region twice, which is why the
// single-stream path refuses them; refusing the whole file is the other extreme, and it is what made 227
// jukebox tracks unplayable.
class OpusChannelTrack {
    OpusLayout layout_;
    EncodedSourceBytes bytes_;
    std::unique_ptr<OpusDecoder,OpusDelete> decoder_;
    std::array<std::uint8_t,MaximumOpusPacket> encoded_{};
    std::array<float,5760> pcm_{};
    std::size_t nextPacket_{};
    std::uint64_t start_{},end_{};
public:
    OpusChannelTrack(EncodedSourceInput& input,const DecodeLimits& limits,OpusLayout layout)
        :layout_(std::move(layout)),bytes_(input,limits) {
        int error=OPUS_OK;
        // Mono, because each region carries one speaker. The layout's own channel count is 2 (the container's),
        // which would make libopus expect stereo packets and fail every one of them.
        decoder_.reset(opus_decoder_create(48000,1,&error));
        if(error==OPUS_ALLOC_FAIL || !decoder_)fail(SourceStatus::ResourceLimit);
        if(error!=OPUS_OK)fail(SourceStatus::MalformedSource);
    }
    struct Checkpoint {
        std::size_t nextPacket=0;
        std::uint64_t sampleEnd=0;
        std::vector<unsigned char> state;
    };
    static constexpr std::uint64_t kInitialCheckpointStride=192000; // 4s at 48kHz
    std::vector<Checkpoint> checkpoints_;
    std::uint64_t checkpointStride_=kInitialCheckpointStride;
    std::uint64_t minCheckpointEnd_=0;

    void maybeSaveCheckpoint() {
        if(nextPacket_==0||end_==0||end_<minCheckpointEnd_)return;
        if(!checkpoints_.empty()&&(end_<=checkpoints_.back().sampleEnd||end_-checkpoints_.back().sampleEnd<checkpointStride_))return;
        if(checkpoints_.empty()&&end_<checkpointStride_)return;
        const int sz=opus_decoder_get_size(1);
        if(sz<=0||!decoder_)return;
        Checkpoint cp;
        cp.nextPacket=nextPacket_;
        cp.sampleEnd=end_;
        cp.state.resize(static_cast<std::size_t>(sz));
        std::copy_n(reinterpret_cast<const unsigned char*>(decoder_.get()),cp.state.size(),cp.state.begin());
        checkpoints_.push_back(std::move(cp));
        if(checkpoints_.size()>48) {
            std::vector<Checkpoint> thinned;
            thinned.reserve(24);
            for(std::size_t i=0;i<checkpoints_.size();i+=2)thinned.push_back(std::move(checkpoints_[i]));
            checkpoints_=std::move(thinned);
            checkpointStride_*=2;
        }
    }
    void decodeNextPacket() {
        maybeSaveCheckpoint();
        if(nextPacket_>=layout_.packets.size())fail(SourceStatus::MalformedSource);
        const auto& packet=layout_.packets[nextPacket_];std::size_t at=0;
        for(const auto& span:packet.spans){bytes_.copy(span.offset,encoded_.data()+at,span.size);at+=span.size;}
        if(at!=packet.bytes)fail(SourceStatus::MalformedSource);
        const auto count=opus_decode_float(decoder_.get(),encoded_.data(),static_cast<opus_int32>(at),pcm_.data(),5760,0);
        if(count!=packet.frames)fail(SourceStatus::MalformedSource);
        for(int n=0;n<count;++n)if(!std::isfinite(pcm_[n]))fail(SourceStatus::MalformedSource);
        start_=end_;end_+=static_cast<unsigned>(count);++nextPacket_;
    }
    void reset() {
        if(decoder_)opus_decoder_ctl(decoder_.get(),OPUS_RESET_STATE);
        nextPacket_=0;start_=0;end_=0;minCheckpointEnd_=0;
        checkpoints_.clear();checkpointStride_=kInitialCheckpointStride;
    }
    void seekTo(std::int64_t target) {
        const auto packetFrames=static_cast<std::uint64_t>(layout_.packets.front().frames);
        const auto rawTarget=std::min(layout_.preSkip+static_cast<std::uint64_t>(target),
                                      layout_.packets.size()*packetFrames);
        const Checkpoint* best=nullptr;
        for(const auto& cp:checkpoints_) {
            if(cp.sampleEnd<=rawTarget)best=&cp;
            else break;
        }
        if(rawTarget<start_) {
            if(best!=nullptr) {
                std::copy_n(best->state.data(),best->state.size(),reinterpret_cast<unsigned char*>(decoder_.get()));
                nextPacket_=best->nextPacket;start_=best->sampleEnd;end_=best->sampleEnd;minCheckpointEnd_=0;
            } else {
                if(decoder_)opus_decoder_ctl(decoder_.get(),OPUS_RESET_STATE);
                nextPacket_=0;start_=0;end_=0;minCheckpointEnd_=0;
            }
        } else if(best!=nullptr&&best->sampleEnd>end_) {
            std::copy_n(best->state.data(),best->state.size(),reinterpret_cast<unsigned char*>(decoder_.get()));
            nextPacket_=best->nextPacket;start_=best->sampleEnd;end_=best->sampleEnd;minCheckpointEnd_=0;
        }
        // BWAV uses independent CELT packets with fixed duration. Warm the decoder before
        // an uncached jump, without replaying minutes of audio on the control thread.
        constexpr std::uint64_t preRollFrames=5760;
        if(rawTarget>end_ && rawTarget-end_>preRollFrames+packetFrames) {
            opus_decoder_ctl(decoder_.get(),OPUS_RESET_STATE);
            nextPacket_=static_cast<std::size_t>((rawTarget-preRollFrames)/packetFrames);
            start_=end_=nextPacket_*packetFrames;
            minCheckpointEnd_=start_+preRollFrames;
        }
        while(rawTarget>end_)decodeNextPacket();
    }
    // Decodes and discards `count` samples from start, advancing the decoder state.
    void skip(std::size_t count) {
        if(count>0)seekTo(static_cast<std::int64_t>(count));
    }
    // Fills [output] with `wanted` mono samples starting at source position `from`.
    void read(std::int64_t from,float* output,std::size_t wanted) {
        std::size_t done=0;
        while(done<wanted) {
            const auto raw=layout_.preSkip+static_cast<std::uint64_t>(from)+done;
            if(raw<start_)seekTo(from+static_cast<std::int64_t>(done));
            while(raw>=end_ && nextPacket_<layout_.packets.size())decodeNextPacket();
            if(raw>=end_) {
                // The container can declare up to one pre-skip of zero padding at EOF.
                std::fill_n(output+done,wanted-done,0.0f);
                return;
            }
            const auto take=std::min(wanted-done,static_cast<std::size_t>(end_-raw));
            std::copy_n(pcm_.data()+(raw-start_),take,output+done);
            done+=take;
        }
    }
};

// Several independent mono streams, interleaved into one stereo source.
class MultiOpusSource final:public AudioFrameSource {
    SourceMetadata metadata_;
    std::unique_ptr<EncodedSourceInput> input_;
    DecodeLimits limits_;
    std::vector<OpusLayout> layouts_;
    std::unique_ptr<std::vector<std::unique_ptr<OpusChannelTrack>>> tracks_;
    std::vector<float> scratch_;
    SourceSamplePosition position_;
    SourceStreamInfo stream_{};
    SourceStatus status_{SourceStatus::Ok};
    void reopen() {
        tracks_=std::make_unique<std::vector<std::unique_ptr<OpusChannelTrack>>>();
        for(const auto& layout:layouts_)
            tracks_->push_back(std::make_unique<OpusChannelTrack>(*input_,limits_,layout));
    }
public:
    MultiOpusSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,
        std::vector<OpusLayout> layouts,SourceMetadata metadata,std::optional<SourceStreamInfo> stream=std::nullopt)
        :metadata_(metadata),input_(std::move(input)),limits_(limits),layouts_(std::move(layouts)),
        stream_(stream.value_or(SourceStreamInfo{})) {
        scratch_.resize(5760);
        reopen();
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata_.channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));
        std::fill_n(output,frames*channels,0.0f);
        try {
            // The scratch buffer is one Opus packet; a render call can be larger, so this walks in packets
            // rather than silently filling only the first bufferful and advancing the clock past the rest.
            std::size_t done=0;
            while(done<wanted) {
                const auto step=std::min(wanted-done,scratch_.size());
                for(std::size_t ch=0;ch<tracks_->size() && ch<channels;++ch) {
                    (*tracks_)[ch]->read(position_.value+static_cast<std::int64_t>(done),scratch_.data(),step);
                    for(std::size_t n=0;n<step;++n)output[(done+n)*channels+ch]=scratch_[n];
                }
                done+=step;
            }
        }catch(const SourceFailure& failure){status_=failure.status;return 0;}
         catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;return 0;}
         catch(...){status_=SourceStatus::MalformedSource;return 0;}
        position_.value+=static_cast<std::int64_t>(wanted);
        return wanted;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        if(tracks_ && status_==SourceStatus::Ok && target.value==position_.value)return SourceStatus::Ok;
        try {
            if(!tracks_ || status_!=SourceStatus::Ok) {
                reopen();
            }
            for(auto& track:*tracks_)track->seekTo(target.value);
            position_=target;status_=SourceStatus::Ok;return status_;
        }catch(const SourceFailure& failure){status_=failure.status;return failure.status;}
         catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;return SourceStatus::ResourceLimit;}
         catch(...){status_=SourceStatus::MalformedSource;return SourceStatus::MalformedSource;}
    }
    void reset()noexcept override{const auto result=seek({0});if(result!=SourceStatus::Ok)status_=result;}
};
}

std::unique_ptr<AudioFrameSource> makePacketOpusSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,OpusLayout layout) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<OpusSource>(std::move(input),limits,std::move(layout));
}

std::unique_ptr<AudioFrameSource> makeMultiPacketOpusSource(std::unique_ptr<EncodedSourceInput> input,
    const DecodeLimits& limits,std::vector<OpusLayout> layouts,SourceMetadata metadata,
    std::optional<SourceStreamInfo> stream) {
    if(!input)fail(SourceStatus::InvalidRequest);
    if(layouts.empty())fail(SourceStatus::InvalidRequest);
    return std::make_unique<MultiOpusSource>(std::move(input),limits,std::move(layouts),metadata,std::move(stream));
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
