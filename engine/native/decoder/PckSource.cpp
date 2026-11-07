#include "decoder/PckMediaResolver.hpp"
#include "decoder/BoundedSourceInput.hpp"
#include "decoder/WwiseOpusFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class PckSource final:public AudioFrameSource {
    std::vector<SourceEntry> entries_;
    std::unique_ptr<AudioFrameSource> decoder_;
    SourceMetadata metadata_;
public:
    PckSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);auto index=parsePckIndex(bytes,limits);
        if(!selected)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(index.entries.begin(),index.entries.end(),[&](const auto& entry){return std::strcmp(entry.id.data(),selected)==0;});
        if(found==index.entries.end())rejectUnavailablePckBank(index,selected);
        const auto media=resolvePckMedia(bytes,index,static_cast<std::size_t>(found-index.entries.begin()));
        entries_=std::move(index.entries);
        auto childLimits=limits;childLimits.maximumDecodedBytes-=entries_.size()*512;
        decoder_=openRiffMedia(std::make_unique<BoundedSourceInput>(std::move(input),media.start,media.size),childLimits);
        metadata_=decoder_->metadata();metadata_.providerId="native.wwise-pck";metadata_.kind=SourceKind::GameBank;metadata_.flags|=ContainerEntry;
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
std::unique_ptr<AudioFrameSource> openPck(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* entryId) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<PckSource>(std::move(input),limits,entryId);
}
std::unique_ptr<AudioFrameSource> openPck(ByteView bytes,const DecodeLimits& limits,const char* entryId) {
    return openPck(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,entryId);
}
std::vector<SourceEntry> inspectPckEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return parsePckIndex(input,limits).entries;
}
}
