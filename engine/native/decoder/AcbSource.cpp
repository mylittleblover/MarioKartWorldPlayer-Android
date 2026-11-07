#include "decoder/AcbFormat.hpp"
#include "decoder/BoundedSourceInput.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class AcbSource final:public AudioFrameSource {
    std::vector<SourceEntry> entries_;
    std::unique_ptr<AudioFrameSource> decoder_;
    SourceMetadata metadata_;
public:
    AcbSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);auto layout=parseAcb(bytes,limits);entries_=std::move(layout.entries);
        if(!selected)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(entries_.begin(),entries_.end(),[&](const auto& entry){return std::strcmp(entry.id.data(),selected)==0;});
        if(found==entries_.end())fail(SourceStatus::EntryMissing);
        // Canonical ID is already matched. Its suffix is the original uint16
        // memory Wave ID, not a Cue ID or a guessed default Cue/Sequence.
        const std::string child=std::string("awb:wave:")+(found->id.data()+sizeof("acb:memory:wave:")-1);
        auto childLimits=limits;childLimits.maximumDecodedBytes-=entries_.size()*256;
        decoder_=openAwb(std::make_unique<BoundedSourceInput>(std::move(input),layout.start,layout.size),childLimits,child.c_str());
        metadata_=decoder_->metadata();metadata_.providerId="native.cri-acb";metadata_.kind=SourceKind::GameBank;metadata_.flags|=ContainerEntry;
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
std::unique_ptr<AudioFrameSource> openAcb(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<AcbSource>(std::move(input),limits,entryId);
}
std::unique_ptr<AudioFrameSource> openAcb(ByteView bytes,const DecodeLimits& limits,const char* entryId) {
    return openAcb(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entryId);
}
std::vector<SourceEntry> inspectAcbEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parseAcb(input,limits).entries;
}
}
