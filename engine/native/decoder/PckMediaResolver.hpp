#pragma once
#include "decoder/PckIndex.hpp"
#include <cstring>
namespace player2::audio {
// Resolve only a named nested DIDX media identity against the same package's
// matching language/media ID. External-source cookies and fallback languages
// cannot be inferred from a DIDX media number.
template<class Bytes>
PckMedia resolvePckMedia(Bytes& bytes,const PckIndex& index,std::size_t selected) {
    auto media=index.media[selected];const char* id=index.entries[selected].id.data();
    if(media.size<12)fail(SourceStatus::MalformedSource);
    const bool big=bytes.magic(media.start,"RIFX");
    if((!big && !bytes.magic(media.start,"RIFF")) || !bytes.magic(media.start+8,"WAVE"))fail(SourceStatus::UnsupportedCodec);
    const std::uint64_t declared=std::uint64_t(bytes.u32(media.start+4,big))+8;
    if(declared==media.size)return media;
    if(declared<media.size || std::strncmp(id,"pck:bank:",9)!=0)fail(SourceStatus::MalformedSource);
    // IDs in this index were created from integer identities, never user text.
    unsigned bank{},language{},number{};int consumed{};
    if(std::sscanf(id,"pck:bank:%u:lang:%u:media:%u%n",&bank,&language,&number,&consumed)!=3 || id[consumed])
        fail(SourceStatus::MalformedSource);
    SourceEntry candidate;std::snprintf(candidate.id.data(),candidate.id.size(),"pck:media:%u:lang:%u",number,language);
    const auto found=std::find_if(index.entries.begin(),index.entries.end(),[&](const auto& entry){return entry.id==candidate.id;});
    if(found==index.entries.end())fail(SourceStatus::InputUnavailable);
    const auto complete=index.media[static_cast<std::size_t>(found-index.entries.begin())];
    if(complete.size!=declared)fail(SourceStatus::MalformedSource);
    std::array<std::uint8_t,4096> prefix{};
    for(std::size_t at=0;at<media.size;) {
        const auto count=std::min(prefix.size(),media.size-at);
        for(std::size_t n=0;n<count;++n)prefix[n]=bytes.u8(media.start+at+n);
        for(std::size_t n=0;n<count;++n)if(prefix[n]!=bytes.u8(complete.start+at+n))fail(SourceStatus::InputChanged);
        at+=count;
    }
    return complete;
}
[[noreturn]] inline void rejectUnavailablePckBank(const PckIndex& index,const char* selected) {
    for(const auto& bank:index.banks)if(bank.status!=SourceStatus::Ok) {
        SourceEntry prefix;const int count=std::snprintf(prefix.id.data(),prefix.id.size(),"pck:bank:%u:lang:%u:media:",bank.id,bank.language);
        if(count>0 && std::strncmp(selected,prefix.id.data(),static_cast<std::size_t>(count))==0)fail(bank.status);
    }
    fail(SourceStatus::EntryMissing);
}
}
