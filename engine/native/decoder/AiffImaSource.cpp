#include "decoder/AiffFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/ImaAdpcm.hpp"

namespace player2::audio {
namespace {
// Apple IMA4 uses 34-byte per-channel packets, low nibble first. The rounded
// 9-bit header predictor is a resynchronization hint: matching index and a
// predictor distance <=127 retain the preceding unrounded decoder history.
// This differs from independently resetting every packet; Seek needs history.
class AiffImaSource final:public AudioFrameSource {
    static constexpr std::size_t PacketFrames=64,CheckpointPackets=64,MaximumCheckpoints=65536;
    using Histories=std::array<ImaHistory,2>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    AiffLayout layout_;
    Histories history_{};
    std::vector<Histories> checkpoints_;
    std::array<float,PacketFrames*2> pcm_{};
    std::uint64_t cached_{UINT64_MAX};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void packet(std::uint64_t index) {
        auto history=history_;std::array<float,PacketFrames*2> pcm{};
        const auto channels=static_cast<std::size_t>(metadata().channels);
        for(std::size_t ch=0;ch<channels;++ch) {
            const auto at=layout_.start+static_cast<std::size_t>(index*channels+ch)*34;
            const auto header=bytes_.u16(at,true);const auto step=header&127;
            if(step>88)fail(SourceStatus::MalformedSource);
            const int raw=header&0xff80;const int predicted=raw>=32768 ? raw-65536 : raw;
            auto& state=history[ch];
            if(state.index!=step || std::abs(state.sample-predicted)>127)state={predicted,step};
            for(std::size_t n=0;n<PacketFrames;n+=2) {
                const auto packed=bytes_.u8(at+2+n/2);
                pcm[n*channels+ch]=imaSample(packed&15,state)/32768.0f;
                pcm[(n+1)*channels+ch]=imaSample(packed>>4,state)/32768.0f;
            }
        }
        // A fault in any channel cannot publish half a packet or new history.
        pcm_=pcm;history_=history;cached_=index;
    }
    void next(float* output) {
        const auto frame=static_cast<std::uint64_t>(position_.value),index=frame/PacketFrames;
        if(cached_!=index)packet(index);
        if(output)std::copy_n(pcm_.data()+(frame%PacketFrames)*metadata().channels,metadata().channels,output);
        ++position_.value;
    }
public:
    AiffImaSource(std::unique_ptr<EncodedSourceInput> input,AiffLayout layout,const DecodeLimits& limits):
        input_(std::move(input)),bytes_(*input_,limits),layout_(layout) {
        const auto packets=static_cast<std::uint64_t>(metadata().duration.value)/PacketFrames;
        const auto count=(packets+CheckpointPackets-1)/CheckpointPackets;
        if(sizeof(*this)>limits.maximumDecodedBytes || count>MaximumCheckpoints ||
            count>(limits.maximumDecodedBytes-sizeof(*this))/sizeof(Histories))fail(SourceStatus::ResourceLimit);
        checkpoints_.reserve(static_cast<std::size_t>(count));
        // Bounded canonical scan validates all packet headers and retains only
        // small histories, never a full decoded PCM allocation.
        for(std::uint64_t n=0;n<packets;++n) {
            if(n%CheckpointPackets==0)checkpoints_.push_back(history_);
            packet(n);
        }
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t done=0;
        try{while(done<wanted){next(output+done*channels);++done;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+done*channels,(frames-done)*channels,0.0f);return done;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        const auto previous=position_;const auto history=history_;const auto pcm=pcm_;const auto cached=cached_;status_=SourceStatus::Ok;
        try {
            const auto checkpoint=std::min(static_cast<std::size_t>(target.value)/(PacketFrames*CheckpointPackets),checkpoints_.size()-1);
            position_.value=static_cast<std::int64_t>(checkpoint*PacketFrames*CheckpointPackets);history_=checkpoints_[checkpoint];cached_=UINT64_MAX;
            while(position_.value<target.value)next(nullptr);
        }catch(const SourceFailure& failure){status_=failure.status;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=history;pcm_=pcm;cached_=cached;}
        return status_;
    }
    void reset()noexcept override{position_.value=0;history_={};cached_=UINT64_MAX;status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openAiffIma4(std::unique_ptr<EncodedSourceInput> input,AiffLayout layout,const DecodeLimits& limits) {
    return std::make_unique<AiffImaSource>(std::move(input),layout,limits);
}
}
