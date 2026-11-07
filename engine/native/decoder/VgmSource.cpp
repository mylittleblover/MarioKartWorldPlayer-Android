#include "decoder/SourceBytes.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/VgmCapabilities.hpp"
#include "audio/source/SourceSequenceData.hpp"
#include "emulation/Psg.hpp"
#include <algorithm>
#include <array>

namespace player2::audio {
namespace {
struct PsgEvent { SourceSamplePosition at; std::uint8_t value{},instance{}; bool stereo{}; };
class VgmFrameSource final : public AudioFrameSource {
    SourceMetadata metadata_;
    std::vector<PsgEvent> events_;
    SourceSequenceData inspection_;
    std::array<Psg,2> psg_;
    std::uint32_t clock_{};std::uint8_t instances_{1};
    SourceSamplePosition position_;
    std::size_t event_{};
public:
    VgmFrameSource(SourceMetadata metadata,std::vector<PsgEvent> events,std::uint32_t clock,std::uint8_t instances,SourceSequenceData inspection):
        metadata_(metadata),events_(std::move(events)),inspection_(std::move(inspection)),clock_(clock),instances_(instances) {}
    const SourceMetadata& metadata() const noexcept override { return metadata_; }
    SourceSamplePosition position() const noexcept override { return position_; }
    std::size_t render(float* output,std::size_t frames) noexcept override {
        const auto count=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-position_.value));
        for(std::size_t n=0;n<count;++n) {
            while(event_<events_.size() && events_[event_].at.value==position_.value) {
                const auto& event=events_[event_++];
                if(event.stereo) psg_[event.instance].stereo(event.value); else psg_[event.instance].write(event.value);
            }
            auto sample=psg_[0].sample(clock_);
            if(instances_==2) {const auto second=psg_[1].sample(clock_);sample[0]+=second[0];sample[1]+=second[1];}
            output[n*2]=sample[0]; output[n*2+1]=sample[1]; ++position_.value;
        }
        std::fill_n(output+count*2,(frames-count)*2,0.0f); return count;
    }
    SourceStatus seek(SourceSamplePosition position) noexcept override {
        if(position.value<0 || position.value>metadata_.duration.value) return SourceStatus::InvalidRequest;
        if(position.value<position_.value)reset();
        std::array<float,128> scratch{};
        while(position_.value<position.value) render(scratch.data(),static_cast<std::size_t>(std::min<std::int64_t>(64,position.value-position_.value)));
        return SourceStatus::Ok; // Linear replay: capability is Replay, never Fast.
    }
    void reset() noexcept override { psg_={}; position_.value=0; event_=0; }
    const SourceSequenceData* sequenceInspection()const noexcept override{return &inspection_;}
};
template<class Input>
std::unique_ptr<AudioFrameSource> parseVgm(Input& input,const DecodeLimits& limits) {
    if(!input.magic(0,"Vgm ")) fail(SourceStatus::UnsupportedFormat);
    input.need(0,64);
    if(std::uint64_t(input.u32(4))+4!=input.size()) fail(SourceStatus::MalformedSource);
    const auto version=input.u32(8),clockWord=input.u32(12),clock=clockWord&0x3fffffffu;
    const std::uint8_t instances=clockWord&0x40000000u ? 2 : 1;
    if((clockWord&0x80000000u) || (instances==2 && version<0x151))fail(SourceStatus::UnsupportedCodec);
    if(version<0x100 || version>0x171 || clock==0 || clock>100000000) fail(SourceStatus::UnsupportedCodec);
    // Known chip-specific variants must be rejected rather than silently approximated.
    if(version>=0x110 && ((input.u16(0x28)!=0 && input.u16(0x28)!=9) ||
        (input.u8(0x2a)!=0 && input.u8(0x2a)!=16) || input.u8(0x2b)!=0)) fail(SourceStatus::UnsupportedCodec);
    std::size_t begin=64;
    if(version>=0x150 && input.u32(0x34)!=0) begin=input.offset(0x34,input.u32(0x34));
    if(begin<64) fail(SourceStatus::MalformedSource);
    validateVgmChipCapabilities(input,begin);
    const auto loopOffset=input.u32(0x1c)==0 ? 0 : input.offset(0x1c,input.u32(0x1c));
    if(loopOffset && (loopOffset<begin || loopOffset>=input.size())) fail(SourceStatus::MalformedSource);
    std::vector<PsgEvent> events;
    SourceSequenceData inspection;inspection.summary.sourceRate=44100;inspection.summary.channels=instances*4;
    std::array<int,2> latchedChannel{};SourceByteOffset endOrigin;
    std::size_t sameFrameEvents=0,maximumSameFrameEvents=0,commands=0;
    SourceSamplePosition position,loopPosition{-1}; bool ended=false;
    for(std::size_t p=begin;p<input.size();) {
        if(commands==limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        ++commands;
        if(p==loopOffset) loopPosition=position;
        const SourceByteOffset origin{p};const auto command=input.u8(p++);
        std::uint32_t wait=0;
        SequenceEvent record;record.origin=origin;record.at=position;record.opcode=command;
        if(const auto* capability=vgmWriteCapability(command)) {
            if(capability->instance>=instances || (capability->instance==1 && version<0x161))fail(SourceStatus::UnsupportedCodec);
            const auto value=input.u8(p++);record.value=value;record.kind=SequenceEventKind::ChipWrite;
            if(capability->kind==VgmWriteKind::PsgRegister){if(value&0x80)latchedChannel[capability->instance]=(value>>5)&3;record.channel=latchedChannel[capability->instance]+capability->instance*4;}
            events.push_back({position,value,capability->instance,capability->kind==VgmWriteKind::PsgStereo});
            maximumSameFrameEvents=std::max(maximumSameFrameEvents,++sameFrameEvents);
        } else if(command==0x61) { wait=input.u16(p); p+=2; }
        else if(command==0x62) wait=735;
        else if(command==0x63) wait=882;
        else if(command>=0x70 && command<=0x7f) wait=(command&15)+1;
        else if(command==0x66) { ended=true;record.kind=SequenceEventKind::End;endOrigin=origin; }
        else fail(SourceStatus::UnsupportedCodec);
        if(record.kind!=SequenceEventKind::End && !vgmWriteCapability(command)){record.kind=SequenceEventKind::Wait;record.duration={wait};}
        // Account conservatively for both vectors' growth before adding inspection.
        const auto storage=std::uint64_t(inspection.events.size()+1)*sizeof(SequenceEvent)+std::uint64_t(events.size())*sizeof(PsgEvent);
        if(storage>limits.maximumDecodedBytes/2)fail(SourceStatus::ResourceLimit);
        inspection.events.push_back(record);if(ended)break;
        if(limits.maximumGeneratedFrames<=0 || wait>limits.maximumGeneratedFrames-position.value) fail(SourceStatus::ResourceLimit);
        position.value+=wait;
        if(wait) sameFrameEvents=0;
    }
    if(!ended || position.value<=0 || (input.u32(0x18)!=0 && input.u32(0x18)!=position.value)) fail(SourceStatus::MalformedSource);
    SourceMetadata metadata{"native.vgm-psg",SourceKind::ChipCommand,44100,2,position,SeekSupport::Replay,
        KnownDuration|LoopMetadata|DeterministicRender|SequenceInspection|Streaming,std::nullopt};
    if(maximumSameFrameEvents<=64) metadata.flags|=RealtimeRender;
    if(loopOffset) {
        if(loopPosition.value<0 || loopPosition.value>=position.value ||
            (input.u32(0x20)!=0 && input.u32(0x20)!=position.value-loopPosition.value)) fail(SourceStatus::SourceLoopBounds);
        metadata.sourceLoop=SourceLoopMetadata{loopPosition,position};
        inspection.summary.loop=SequenceLoop{endOrigin,{loopOffset},*metadata.sourceLoop};
    } else if(input.u32(0x20)!=0) fail(SourceStatus::SourceLoopBounds);
    inspection.summary.events=inspection.events.size();
    return std::make_unique<VgmFrameSource>(metadata,std::move(events),clock,instances,std::move(inspection));
}
}
std::unique_ptr<AudioFrameSource> openVgm(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parseVgm(input,limits);
}
std::unique_ptr<AudioFrameSource> openVgm(std::unique_ptr<EncodedSourceInput> source,const DecodeLimits& limits) {
    if(!source)fail(SourceStatus::InvalidRequest);
    EncodedSourceBytes input(*source,limits);
    // Bounded command/inspection data are derived execution state. No encoded
    // file copy or PCM pre-render replaces the original source. The input lease
    // closes after control-thread parsing; rendering never reads from it.
    return parseVgm(input,limits);
}
}
