#include "decoder/BwavFormat.hpp"
#include "decoder/BwavGroups.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class BwavSource final:public AudioFrameSource {
    static constexpr std::size_t CheckpointFrames=14*292,MaximumCheckpoints=65536;
    using Histories=std::array<DspHistory,MaximumNintendoStreamChannels>;
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    std::vector<EncodedSourceBytes> channelBytes_; // Channels1..N-1; lane0 reuses parser window.
    BwavLayout layout_;
    std::vector<DspHistory> checkpoints_;
    Histories history_{};
    SourceSamplePosition position_;
    SourceStreamInfo stream_;
    SourceStatus status_{SourceStatus::Ok};
    void recordCheckpointIfNeeded(std::size_t channels) {
        const auto frame=static_cast<std::size_t>(position_.value);
        if(frame%CheckpointFrames!=0)return;
        const auto index=frame/CheckpointFrames;
        if(index==checkpoints_.size()/channels) {
            checkpoints_.insert(checkpoints_.end(),history_.begin(),history_.begin()+channels);
        }
    }
    void decodeDspSpan(std::size_t frames,float* output) {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        std::size_t done=0;
        while(done<frames) {
            recordCheckpointIfNeeded(channels);
            const auto frame=static_cast<std::size_t>(position_.value);
            const auto within=frame%14;
            const auto untilBlockEnd=14-within;
            const auto untilCheckpoint=CheckpointFrames-(frame%CheckpointFrames);
            const auto run=std::min({frames-done,untilBlockEnd,untilCheckpoint});
            auto nextHistory=history_;
            const auto blockIndex=frame/14;
            const auto firstByte=within/2;
            const auto byteCount=(within+run-1)/2-firstByte+1;
            for(std::size_t ch=0;ch<channels;++ch) {
                auto& bytes=ch ? channelBytes_[ch-1] : bytes_;
                const auto blockOffset=layout_.starts[ch]+blockIndex*8;
                const auto header=bytes.u8(blockOffset);
                const auto predictor=header>>4;
                if(predictor>=8)fail(SourceStatus::MalformedSource);
                const std::int64_t scale=std::int64_t{1}<<int(header&15);
                const std::int64_t c0=layout_.channels[ch].coefficients[predictor*2];
                const std::int64_t c1=layout_.channels[ch].coefficients[predictor*2+1];
                std::uint8_t packed[7]{};
                bytes.copy(blockOffset+1+firstByte,packed,byteCount);
                auto h=nextHistory[ch];
                for(std::size_t i=0;i<run;++i) {
                    const auto idx=within+i;
                    const auto byte=packed[idx/2-firstByte];
                    int nibble=(idx&1u) ? (byte&15) : (byte>>4);
                    if(nibble>=8)nibble-=16;
                    const std::int64_t sum=std::int64_t(nibble)*scale*2048+c0*h.previous+c1*h.older+1024;
                    const auto rounded=sum>=0 ? sum/2048 : -((-sum+2047)/2048);
                    const auto sample=static_cast<std::int32_t>(std::clamp<std::int64_t>(rounded,-32768,32767));
                    h.older=h.previous;h.previous=sample;
                    if(output)output[(done+i)*channels+ch]=static_cast<float>(sample)/32768.0f;
                }
                nextHistory[ch]=h;
            }
            history_=nextHistory;
            position_.value+=static_cast<std::int64_t>(run);
            done+=run;
        }
        recordCheckpointIfNeeded(channels);
    }
    void decodePcmSpan(std::size_t frames,float* output) {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        const auto startFrame=static_cast<std::size_t>(position_.value);
        for(std::size_t i=0;i<frames;++i) {
            const auto frame=startFrame+i;
            for(std::size_t ch=0;ch<channels;++ch) {
                auto& bytes=ch ? channelBytes_[ch-1] : bytes_;
                output[i*channels+ch]=static_cast<float>(dspSigned(bytes.u16(layout_.starts[ch]+frame*2)))/32768.0f;
            }
        }
        position_.value+=static_cast<std::int64_t>(frames);
    }
public:
    BwavSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,BwavLayout layout):input_(std::move(input)),
        bytes_(*input_,limits),layout_(std::move(layout)) {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        // Account for each encoded window and, for raw multichannel streams,
        // both bounded group metadata and the shared stereo projection scratch.
        const auto fixed=sizeof(*this)+(channels-1)*sizeof(EncodedSourceBytes)+(channels>2 ? 32768 : 0);
        if(fixed>limits.maximumDecodedBytes)fail(SourceStatus::ResourceLimit);
        channelBytes_.reserve(channels-1);
        for(std::size_t ch=0;ch<channels;++ch){history_[ch]=layout_.channels[ch].initial;if(ch)channelBytes_.emplace_back(*input_,limits);}
        if(channels>2)stream_=bwavStereoGroups(layout_);
        if(layout_.dsp) {
            const auto count=(std::uint64_t(metadata().duration.value)+CheckpointFrames-1)/CheckpointFrames;
            if(count>MaximumCheckpoints || count>(limits.maximumDecodedBytes-fixed)/sizeof(DspHistory)/channels)fail(SourceStatus::ResourceLimit);
            // Lazy checkpointing: seed checkpoint 0 (initial history) only, avoiding full-file
            // DSP-ADPCM decode on open. Subsequent checkpoints are appended as render/seek advances.
            checkpoints_.insert(checkpoints_.end(),history_.begin(),history_.begin()+channels);
        }
        reset();
    }
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    const SourceStreamInfo* streamInspection()const noexcept override{return stream_.originalChannels ? &stream_ : nullptr;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));
        const auto before=position_.value;
        try {
            if(wanted>0) {
                if(layout_.dsp)decodeDspSpan(wanted,output);
                else decodePcmSpan(wanted,output);
            }
        } catch(const SourceFailure& error){status_=error.status;}
          catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;}
          catch(...){status_=SourceStatus::MalformedSource;}
        const auto count=static_cast<std::size_t>(position_.value-before);
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        if(status_==SourceStatus::Ok && target.value==position_.value)return SourceStatus::Ok;
        const auto previous=position_;const auto history=history_;const auto prevCheckpoints=checkpoints_.size();
        const bool wasOk=status_==SourceStatus::Ok;
        status_=SourceStatus::Ok;
        try {
            if(layout_.dsp) {
                const auto channels=static_cast<std::size_t>(metadata().channels);
                const auto builtCount=checkpoints_.size()/channels;
                const auto desiredCp=static_cast<std::size_t>(target.value)/CheckpointFrames;
                const auto checkpoint=std::min(desiredCp,builtCount-1);
                const auto cpFrame=static_cast<std::int64_t>(checkpoint*CheckpointFrames);
                if(wasOk && target.value>=position_.value && position_.value>=cpFrame) {
                    if(target.value>position_.value) {
                        decodeDspSpan(static_cast<std::size_t>(target.value-position_.value),nullptr);
                    }
                } else {
                    position_.value=cpFrame;
                    std::copy_n(checkpoints_.data()+checkpoint*channels,channels,history_.begin());
                    if(target.value>position_.value) {
                        decodeDspSpan(static_cast<std::size_t>(target.value-position_.value),nullptr);
                    }
                }
            } else position_=target;
        }catch(const SourceFailure& error){status_=error.status;}
         catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;}
         catch(...){status_=SourceStatus::MalformedSource;}
        if(status_!=SourceStatus::Ok){position_=previous;history_=history;checkpoints_.resize(prevCheckpoints);}
        return status_;
    }
    void reset()noexcept override {
        position_.value=0;status_=SourceStatus::Ok;
        for(int ch=0;ch<metadata().channels;++ch)history_[ch]=layout_.channels[ch].initial;
    }
};
}
std::unique_ptr<AudioFrameSource> openBwav(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    if(limits.maximumDecodedBytes<16384)fail(SourceStatus::ResourceLimit);
    // A codec-2 file is a set of independent mono Opus streams, so it goes to the Opus reader rather than
    // the DSP one. Parsing the container is the only way to learn which kind this file is, and the parse is
    // cheap: it walks chunk headers, not audio.
    BwavLayout layout;
    {
        EncodedSourceBytes probe(*input,limits);
        layout=parseBwav(probe,limits);
    }
    if(!layout.opusChannels.empty()) {
        // Each region is one *independent mono* Opus stream -- not a channel of a multichannel stream.
        // The jukebox corpus is exactly that: two regions, 960-frame CELT packets, 120-sample pre-skip
        // each. Handing one region to a stereo decoder would fail every packet, and refusing the file
        // outright is what left 227 jukebox tracks unplayable. So each chain gets its own mono decoder
        // and the results are interleaved.
        const auto channels=static_cast<unsigned>(layout.opusChannels.size());
        if(channels>MaximumNintendoStreamChannels)fail(SourceStatus::UnsupportedChannels);
        return projectStreamStereo(makeMultiPacketOpusSource(std::move(input),limits,std::move(layout.opusChannels),layout.metadata,
            channels>2 ? std::optional<SourceStreamInfo>(bwavStereoGroups(layout)) : std::nullopt));
    }
    return projectStreamStereo(std::make_unique<BwavSource>(std::move(input),limits,std::move(layout)));
}
std::unique_ptr<AudioFrameSource> openBwav(ByteView bytes,const DecodeLimits& limits) {
    return openBwav(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
