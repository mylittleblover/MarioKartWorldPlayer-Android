#include "decoder/BnkIndex.hpp"
#include "decoder/BoundedSourceInput.hpp"
#include "decoder/WwiseOpusFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cstring>

namespace player2::audio {
namespace {
class BnkSource final:public AudioFrameSource {
    std::vector<SourceEntry> entries_;
    std::unique_ptr<AudioFrameSource> decoder_;
    SourceMetadata metadata_;
public:
    BnkSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);auto index=parseBnkIndex(bytes,limits);entries_=std::move(index.entries);
        if(!selected)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(entries_.begin(),entries_.end(),[&](const auto& entry){return std::strcmp(entry.id.data(),selected)==0;});
        if(found==entries_.end())fail(SourceStatus::EntryMissing);
        const auto& media=index.media[static_cast<std::size_t>(found-entries_.begin())];
        // Wwise prefetch media may have a truncated RIFF. Never invent its tail.
        if(media.size<12)fail(SourceStatus::MalformedSource);
        const bool big=bytes.magic(media.start,"RIFX");
        if((!big && !bytes.magic(media.start,"RIFF")) || !bytes.magic(media.start+8,"WAVE"))fail(SourceStatus::UnsupportedCodec);
        if(std::uint64_t(bytes.u32(media.start+4,big))+8!=media.size)fail(SourceStatus::MalformedSource);
        decoder_=openRiffMedia(std::make_unique<BoundedSourceInput>(std::move(input),media.start,media.size),limits);
        metadata_=decoder_->metadata();metadata_.providerId="native.wwise-bnk";metadata_.kind=SourceKind::GameBank;metadata_.flags|=ContainerEntry;
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
std::unique_ptr<AudioFrameSource> openBnk(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<BnkSource>(std::move(input),limits,entryId);
}
std::unique_ptr<AudioFrameSource> openBnk(ByteView bytes,const DecodeLimits& limits,const char* entryId) {
    return openBnk(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entryId);
}
std::vector<SourceEntry> inspectBnkEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parseBnkIndex(input,limits).entries;
}
}
