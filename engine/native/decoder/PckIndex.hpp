#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/BnkIndex.hpp"
#include "decoder/BoundedSourceBytes.hpp"
#include <algorithm>
#include <cstdio>
#include <vector>

namespace player2::audio {
struct PckMedia {std::size_t start{},size{};};
struct PckBank {std::uint32_t id{},language{};PckMedia span;SourceStatus status{SourceStatus::UnsupportedCodec};};
struct PckIndex {std::vector<PckMedia> media;std::vector<SourceEntry> entries;std::vector<PckBank> banks;};
// AKPK v1, old three-section and modern four-section directories. Language and
// actual file IDs scope identities; directory order and physical offsets do not.
// Nested bank media reuse the admitted BNK parser. HIRC is never executed and
// no bank, language or media is chosen by directory position.
template<class Bytes> PckIndex parsePckIndex(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"AKPK"))fail(SourceStatus::UnsupportedFormat);
    bytes.need(0,24);
    const bool big=bytes.u32(8,true)==1;
    if(bytes.u32(8,big)!=1)fail(SourceStatus::UnsupportedCodec);
    const auto header=bytes.u32(4,big);
    std::uint32_t sizes[]{bytes.u32(12,big),bytes.u32(16,big),bytes.u32(20,big),0};
    const std::uint64_t oldSize=16ull+sizes[0]+sizes[1]+sizes[2];
    std::size_t position=24;
    if(header!=oldSize) {
        sizes[3]=bytes.u32(24,big);position=28;
        if(header!=oldSize+4+sizes[3])fail(SourceStatus::MalformedSource);
    }
    bytes.need(8,header);const auto dataStart=std::size_t(header)+8;
    if(sizes[0]<4)fail(SourceStatus::MalformedSource);
    const auto langStart=position,langEnd=position+sizes[0];
    const auto languageCount=bytes.u32(position,big);
    if(languageCount>4096)fail(SourceStatus::ResourceLimit);
    if(languageCount>(sizes[0]-4)/8)fail(SourceStatus::MalformedSource);
    std::vector<std::uint32_t> languages;languages.reserve(languageCount);
    for(std::size_t n=0;n<languageCount;++n) {
        const auto p=langStart+4+n*8;const auto offset=bytes.u32(p,big);
        if(offset<4+languageCount*8 || offset>=sizes[0])fail(SourceStatus::MalformedSource);
        auto text=langStart+offset;bytes.need(text,2);
        if(text+2>langEnd)fail(SourceStatus::MalformedSource);
        const bool wide=bytes.u8(text)==0 || bytes.u8(text+1)==0;
        const auto width=wide ? 2u : 1u;bool terminated=false;
        for(unsigned chars=0;chars<1024 && text+width<=langEnd;++chars,text+=width) {
            if(wide ? bytes.u16(text,big)==0 : bytes.u8(text)==0){terminated=true;break;}
        }
        if(!terminated)fail(SourceStatus::MalformedSource);
        languages.push_back(bytes.u32(p+4,big));
    }
    std::sort(languages.begin(),languages.end());
    if(std::adjacent_find(languages.begin(),languages.end())!=languages.end())fail(SourceStatus::MalformedSource);
    position=langEnd;PckIndex result;std::vector<PckMedia> spans;
    std::vector<std::array<std::uint64_t,3>> keys;std::size_t total=0;
    for(unsigned section=1;section<4;++section) {
        const auto size=sizes[section];if(!size)continue;
        if(size<4)fail(SourceStatus::MalformedSource);
        const auto count=bytes.u32(position,big);
        if(count>4096-total)fail(SourceStatus::ResourceLimit);
        total+=count;
        if(limits.maximumDecodedBytes<65536 || total>(limits.maximumDecodedBytes-65536)/512)
            fail(SourceStatus::ResourceLimit);
        if(!count) {if(size!=4)fail(SourceStatus::MalformedSource);position+=size;continue;}
        if((size-4)%count)fail(SourceStatus::MalformedSource);
        const auto width=(size-4)/count;
        if(width!=20 && width!=24)fail(SourceStatus::UnsupportedCodec);
        for(std::size_t n=0;n<count;++n) {
            const auto p=position+4+n*width;
            const bool external=section==3 && width==24;
            const auto u64=[&](std::size_t at) {
                const auto a=bytes.u32(at,big),b=bytes.u32(at+4,big);
                return big ? (std::uint64_t(a)<<32)|b : a|(std::uint64_t(b)<<32);
            };
            const std::uint64_t id=external ? u64(p) : bytes.u32(p,big);
            const auto block=bytes.u32(p+(external ? 8 : 4),big);
            const auto length=width==24 && !external ? u64(p+8) : bytes.u32(p+(external ? 12 : 8),big);
            const auto offset=bytes.u32(p+width-8,big),language=bytes.u32(p+width-4,big);
            const auto start=std::uint64_t(offset)*(block ? block : 1u);
            if(!length || start<dataStart || start>bytes.size() || length>bytes.size()-start)
                fail(SourceStatus::MalformedSource);
            if(language && !std::binary_search(languages.begin(),languages.end(),language))fail(SourceStatus::MalformedSource);
            keys.push_back({section,id,language});spans.push_back({static_cast<std::size_t>(start),static_cast<std::size_t>(length)});
            if(section==1)result.banks.push_back({static_cast<std::uint32_t>(id),language,spans.back(),SourceStatus::UnsupportedCodec});
            else {
                SourceEntry entry;
                std::snprintf(entry.id.data(),entry.id.size(),"pck:%s:%llu:lang:%u",section==3 ? "external" : "media",
                    static_cast<unsigned long long>(id),language);
                result.entries.push_back(entry);result.media.push_back(spans.back());
            }
        }
        position+=size;
    }
    if(position!=dataStart)fail(SourceStatus::MalformedSource);
    std::sort(keys.begin(),keys.end());
    if(std::adjacent_find(keys.begin(),keys.end())!=keys.end())fail(SourceStatus::MalformedSource);
    std::sort(spans.begin(),spans.end(),[](const auto& a,const auto& b){return a.start==b.start ? a.size<b.size : a.start<b.start;});
    for(std::size_t n=1;n<spans.size();++n) {
        const auto& a=spans[n-1];const auto& b=spans[n];
        if(b.start<a.start+a.size && (a.start!=b.start || a.size!=b.size))fail(SourceStatus::MalformedSource);
    }
    // Preserve direct entry order/IDs; append separately scoped nested media.
    // Unsupported banks cannot make unrelated directly playable media vanish.
    for(auto& bank:result.banks) {
        try {
            BoundedSourceBytes<Bytes> child(bytes,bank.span.start,bank.span.size);
            auto nested=parseBnkIndex(child,limits);
            if(nested.entries.size()>4096-total || result.entries.size()+nested.entries.size()>(limits.maximumDecodedBytes-65536)/512)
                fail(SourceStatus::ResourceLimit);
            total+=nested.entries.size();bank.status=SourceStatus::Ok;
            for(const auto& media:nested.media) {
                SourceEntry entry;const int written=std::snprintf(entry.id.data(),entry.id.size(),"pck:bank:%u:lang:%u:media:%u",bank.id,bank.language,media.id);
                if(written<0 || static_cast<std::size_t>(written)>=entry.id.size())fail(SourceStatus::ResourceLimit);
                result.entries.push_back(entry);result.media.push_back({bank.span.start+media.start,media.size});
            }
        } catch(const SourceFailure& error) {
            if(error.status==SourceStatus::ResourceLimit || error.status==SourceStatus::InputUnavailable || error.status==SourceStatus::InputChanged)throw;
            bank.status=error.status;
        }
    }
    return result;
}
}
