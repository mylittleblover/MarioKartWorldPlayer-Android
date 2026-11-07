#include "decoder/Mp3Layout.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <dr_mp3.h>
#include <cmath>

namespace player2::audio {
namespace {
// Copy only persistent decoder history. Scratch contains temporary pointers;
// it is regenerated for every frame and must never become a snapshot contract.
struct Mp3History {
    std::array<float,576> overlap;std::array<float,960> qmf;
    std::array<std::uint8_t,511> reservoir;std::array<std::uint8_t,4> header;
    int reservoirSize{};std::size_t offset{},frame{};
    Mp3History(const drmp3dec& decoder,std::size_t at,std::size_t index):reservoirSize(decoder.reserv),offset(at),frame(index) {
        for(int ch=0;ch<2;++ch)std::copy_n(decoder.mdct_overlap[ch],288,overlap.data()+ch*288);
        std::copy_n(decoder.qmf_state,qmf.size(),qmf.data());std::copy_n(decoder.reserv_buf,reservoir.size(),reservoir.data());
        std::copy_n(decoder.header,header.size(),header.data());
    }
    void restore(drmp3dec& decoder)const noexcept {
        decoder={};drmp3dec_init(&decoder);decoder.reserv=reservoirSize;
        for(int ch=0;ch<2;++ch)std::copy_n(overlap.data()+ch*288,288,decoder.mdct_overlap[ch]);
        std::copy_n(qmf.data(),qmf.size(),decoder.qmf_state);
        std::copy_n(reservoir.data(),reservoir.size(),decoder.reserv_buf);std::copy_n(header.data(),header.size(),decoder.header);
    }
};
class Mp3FrameSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;EncodedSourceBytes bytes_;
    Mp3Layout layout_;drmp3dec decoder_{};
    std::vector<Mp3History> checkpoints_;
    std::array<std::uint8_t,2048> encoded_{};std::array<float,2304> pcm_{};
    std::size_t offset_{},nextFrame_{},consumed_{},available_{};
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};bool invalidState_{};
    static constexpr std::size_t CheckpointInterval=64;
    void decode() {
        if(input_->byteLength()!=bytes_.size())fail(SourceStatus::InputChanged);
        if(offset_>=layout_.end)fail(SourceStatus::MalformedSource);
        const auto frame=mp3Header(bytes_.u32(offset_,true));bytes_.copy(offset_,encoded_.data(),frame.bytes);
        drmp3dec_frame_info info{};
        const auto count=drmp3dec_decode_frame(&decoder_,encoded_.data(),frame.bytes,pcm_.data(),&info);
        if(count!=layout_.frameSamples || info.frame_bytes!=frame.bytes || info.layer!=3 ||
            info.channels!=layout_.metadata.channels || info.sample_rate!=layout_.metadata.sampleRate)fail(SourceStatus::MalformedSource);
        for(std::size_t n=0;n<static_cast<std::size_t>(count*info.channels);++n)if(!std::isfinite(pcm_[n]))fail(SourceStatus::MalformedSource);
        offset_+=frame.bytes;++nextFrame_;consumed_=0;available_=count;
    }
public:
    Mp3FrameSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),bytes_(*input_,limits),layout_(parseMp3Layout(bytes_,limits)) {
        const auto count=(layout_.frameCount+CheckpointInterval-1)/CheckpointInterval;
        const auto budget=std::min<std::size_t>(8*1024*1024,limits.maximumDecodedBytes);
        if(count>1024 || sizeof(decoder_)+sizeof(encoded_)+sizeof(pcm_)>budget ||
            count>(budget-sizeof(decoder_)-sizeof(encoded_)-sizeof(pcm_))/sizeof(Mp3History))fail(SourceStatus::ResourceLimit);
        checkpoints_.reserve(count);drmp3dec_init(&decoder_);offset_=layout_.start;
        // Validate every exact encoded frame on the control thread; no full PCM
        // is retained. Histories permit identical bounded replay after any seek.
        while(nextFrame_<layout_.frameCount) {
            if(nextFrame_%CheckpointInterval==0)checkpoints_.emplace_back(decoder_,offset_,nextFrame_);
            decode();
        }
        if(offset_!=layout_.end)fail(SourceStatus::MalformedSource);
        if(seek({0})!=SourceStatus::Ok)fail(status_);
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        std::size_t rendered=0;status_=invalidState_ ? status_ : SourceStatus::Ok;
        try {
            if(!invalidState_) {
                const auto wanted=std::min<std::size_t>(frames,metadata().duration.value-position_.value);
                while(rendered<wanted) {
                    if(consumed_==available_)decode();
                    const auto count=std::min(wanted-rendered,available_-consumed_);
                    std::copy_n(pcm_.data()+consumed_*channels,count*channels,output+rendered*channels);
                    consumed_+=count;rendered+=count;
                }
            }
        }catch(const SourceFailure& failure){status_=failure.status;invalidState_=true;}
         catch(...){status_=SourceStatus::MalformedSource;invalidState_=true;}
        position_.value+=static_cast<std::int64_t>(rendered);std::fill_n(output+rendered*channels,(frames-rendered)*channels,0.0f);return rendered;
    }
    SourceStatus seek(SourceSamplePosition at)noexcept override {
        if(at.value<0 || at.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        try {
            if(at.value==metadata().duration.value) {position_=at;consumed_=available_=0;status_=SourceStatus::Ok;invalidState_=false;return status_;}
            const auto raw=static_cast<std::uint64_t>(at.value)+layout_.delay;
            const auto frame=static_cast<std::size_t>(raw/layout_.frameSamples);
            const auto& checkpoint=checkpoints_[frame/CheckpointInterval];checkpoint.restore(decoder_);
            offset_=checkpoint.offset;nextFrame_=checkpoint.frame;consumed_=available_=0;
            while(nextFrame_<=frame)decode();
            consumed_=raw%layout_.frameSamples;position_=at;status_=SourceStatus::Ok;invalidState_=false;
        }catch(const SourceFailure& failure){status_=failure.status;invalidState_=true;}
         catch(...){status_=SourceStatus::MalformedSource;invalidState_=true;}
        return status_;
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openMp3(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<Mp3FrameSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openMp3(ByteView bytes,const DecodeLimits& limits) {
    return openMp3(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
