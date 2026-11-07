#include "decoder/SwarIndex.hpp"
#include "decoder/SwavFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/SourceWindowBytes.hpp"
#include "decoder/BoundedSourceInput.hpp"
#include <cstdio>

namespace player2::audio {
namespace {
std::vector<SourceEntry> identities(std::size_t count) {
    std::vector<SourceEntry> entries(count);
    for(std::size_t n=0;n<count;++n)std::snprintf(entries[n].id.data(),entries[n].id.size(),"swar:wave:%u",static_cast<unsigned>(n));
    return entries;
}
class SwarSource final:public AudioFrameSource {
    std::vector<SourceEntry> entries_;std::unique_ptr<AudioFrameSource> source_;SourceMetadata metadata_;
public:
    SwarSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);const auto index=parseSwar(bytes,limits);entries_=identities(index.size());
        if(!selected)fail(SourceStatus::EntryMissing);
        const auto found=std::find_if(entries_.begin(),entries_.end(),[&](const auto& e){return std::strcmp(e.id.data(),selected)==0;});
        if(found==entries_.end())fail(SourceStatus::EntryMissing);
        const auto& entry=index[static_cast<std::size_t>(found-entries_.begin())];
        auto childLimits=limits;childLimits.maximumDecodedBytes-=entries_.size()*512;
        SourceWindowBytes window(bytes,entry.start,entry.size);const auto layout=parseNitroWave(window,0,childLimits);
        source_=makeNintendoWaveSource(std::make_unique<BoundedSourceInput>(std::move(input),entry.start,entry.size),childLimits,layout);
        metadata_=source_->metadata();metadata_.providerId="native.nintendo-swar";metadata_.kind=SourceKind::GameBank;metadata_.flags|=ContainerEntry;
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const std::vector<SourceEntry>* sourceEntries()const noexcept override{return &entries_;}
    SourceSamplePosition position()const noexcept override{return source_->position();}
    SourceStatus renderStatus()const noexcept override{return source_->renderStatus();}
    std::size_t render(float* output,std::size_t frames)noexcept override{return source_->render(output,frames);}
    SourceStatus seek(SourceSamplePosition position)noexcept override{return source_->seek(position);}
    void reset()noexcept override{source_->reset();}
};
}
std::vector<SourceEntry> inspectSwarEntries(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);return identities(parseSwar(input,limits).size());
}
std::unique_ptr<AudioFrameSource> openSwar(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,const char* selected) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<SwarSource>(std::move(input),limits,selected);
}
std::unique_ptr<AudioFrameSource> openSwar(ByteView bytes,const DecodeLimits& limits,const char* selected) {
    return openSwar(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,selected);
}
}
