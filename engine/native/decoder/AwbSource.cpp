#include "decoder/Afs2Index.hpp"
#include "decoder/HcaProbe.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/BoundedSourceInput.hpp"
#include "decoder/WwiseOpusFormat.hpp"
#include <cstdio>
#include <cstring>

namespace player2::audio {
namespace {
std::vector<SourceEntry> identities(const std::vector<Afs2Entry>& index) {
    std::vector<SourceEntry> result(index.size());
    for(std::size_t n=0;n<index.size();++n)std::snprintf(result[n].id.data(),result[n].id.size(),"awb:wave:%u",unsigned(index[n].waveId));
    return result;
}
enum class ChildCodec {Adx,Wave,Hca};
std::size_t childExtent(EncodedSourceBytes& bytes,const Afs2Entry& entry,ChildCodec& codec) {
    const auto size=entry.end-entry.start,p=entry.start;
    if(size<20)fail(SourceStatus::MalformedSource);
    std::size_t extent=0;codec=hcaTag(bytes,p,"HCA\0") ? ChildCodec::Hca : (bytes.magic(p,"RIFF") || bytes.magic(p,"RIFX")) && bytes.magic(p+8,"WAVE") ? ChildCodec::Wave : ChildCodec::Adx;
    if(codec==ChildCodec::Hca) {
        if(size<40)fail(SourceStatus::MalformedSource);
        if(bytes.u16(p+4,true)!=0x200)fail(SourceStatus::UnsupportedCodec);
        const auto length=std::uint64_t(bytes.u16(p+6,true))+std::uint64_t(bytes.u32(p+16,true))*bytes.u16(p+28,true);
        if(length<42 || length>size)fail(SourceStatus::MalformedSource);
        extent=static_cast<std::size_t>(length);
    } else if(codec==ChildCodec::Wave) {
        const std::uint64_t length=std::uint64_t(bytes.u32(p+4,bytes.magic(p,"RIFX")))+8;
        if(length<44 || length>size)fail(SourceStatus::MalformedSource);
        extent=static_cast<std::size_t>(length);
    } else {
        if(bytes.u16(p,true)!=0x8000)fail(SourceStatus::UnsupportedCodec);
        const auto channels=bytes.u8(p+7);const auto frames=bytes.u32(p+12,true);
        const std::uint64_t header=std::uint64_t(bytes.u16(p+2,true))+4;
        const auto encoded=header+((std::uint64_t(frames)+31)/32)*channels*18;
        if(header<26 || encoded>size)fail(SourceStatus::MalformedSource);
        extent=static_cast<std::size_t>(encoded);
        if(size-extent>=2 && bytes.u16(p+extent,true)==0x8001) {
            if(size-extent<18)fail(SourceStatus::MalformedSource);
            extent+=18; // The existing ADX parser validates every footer byte.
        }
    }
    // Only the container's alignment padding may follow the admitted child.
    if(size-extent>=bytes.u16(12))fail(SourceStatus::UnsupportedCodec);
    for(auto n=extent;n<size;++n)if(bytes.u8(p+n))fail(SourceStatus::UnsupportedCodec);
    return extent;
}
class AwbSource final:public AudioFrameSource {
    std::vector<SourceEntry> entries_;
    std::unique_ptr<AudioFrameSource> decoder_;
    SourceMetadata metadata_;
public:
    AwbSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);const auto index=parseAfs2Index(bytes,limits);entries_=identities(index);
        if(!selected)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(entries_.begin(),entries_.end(),[&](const auto& entry){return std::strcmp(entry.id.data(),selected)==0;});
        if(found==entries_.end())fail(SourceStatus::EntryMissing);
        const auto& entry=index[static_cast<std::size_t>(found-entries_.begin())];
        ChildCodec codec=ChildCodec::Adx;const auto extent=childExtent(bytes,entry,codec);
        if(codec==ChildCodec::Wave && limits.maximumDecodedBytes<65536+entries_.size()*256)fail(SourceStatus::ResourceLimit);
        std::unique_ptr<EncodedSourceInput> child=std::make_unique<BoundedSourceInput>(std::move(input),entry.start,extent);
        auto childLimits=limits;
        if(codec==ChildCodec::Hca) {
            const auto retained=entries_.size()*256;
            if(childLimits.maximumDecodedBytes<retained)fail(SourceStatus::ResourceLimit);
            childLimits.maximumDecodedBytes-=retained;
        }
        decoder_=codec==ChildCodec::Wave ? openRiffMedia(std::move(child),childLimits) : codec==ChildCodec::Hca ? openHca(std::move(child),childLimits) : openAdx(std::move(child),childLimits);
        metadata_=decoder_->metadata();metadata_.providerId="native.cri-awb";metadata_.kind=SourceKind::GameBank;metadata_.flags|=ContainerEntry;
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const std::vector<SourceEntry>* sourceEntries()const noexcept override{return &entries_;}
    SourceSamplePosition position()const noexcept override{return decoder_->position();}
    SourceStatus renderStatus()const noexcept override{return decoder_->renderStatus();}
    std::size_t render(float* output,std::size_t frames)noexcept override{return decoder_->render(output,frames);}
    SourceStatus seek(SourceSamplePosition position)noexcept override{return decoder_->seek(position);}
    void reset()noexcept override{decoder_->reset();}
};
}
std::vector<SourceEntry> inspectAwbEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return identities(parseAfs2Index(input,limits));
}
std::unique_ptr<AudioFrameSource> openAwb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<AwbSource>(std::move(input),limits,entryId);
}
std::unique_ptr<AudioFrameSource> openAwb(ByteView bytes,const DecodeLimits& limits,const char* entryId) {
    return openAwb(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entryId);
}
}
