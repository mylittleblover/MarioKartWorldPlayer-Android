#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/SpcTiming.hpp"
#include "decoder/VgmFmFormat.hpp"
#include <array>
#include <algorithm>
#include <vector>

namespace player2::audio {
enum class EmulatedKind {Nsfe,Spc,VgmFm};
struct EmulatedTiming {std::uint64_t ticks{},fadeTicks{};}; // 1/64000s, including exact xid6 timing
struct EmulatedFormat {
    std::vector<EmulatedTiming> songs;
    unsigned initialSong{};
    std::optional<SourceMetadata> directMetadata;
};
inline constexpr std::size_t MaximumEmulatedInput=128*1024;
inline void validateNsfeProgram(std::uint32_t load,std::uint32_t init,std::uint32_t play,std::size_t bytes,
    const std::array<std::uint8_t,8>& banks) {
    if(load<0x8000 || init<0x8000 || play<0x8000)fail(SourceStatus::UnsupportedCodec);
    const bool switched=std::any_of(banks.begin(),banks.end(),[](auto bank){return bank!=0;});
    if(!switched) {
        if(bytes>0x10000-load || init<load || init>=load+bytes || play<load || play>=load+bytes)fail(SourceStatus::UnsupportedCodec);
        return;
    }
    const std::size_t prefix=load%4096,pages=(prefix+bytes+4095)/4096;
    for(const auto bank:banks)if(bank>=pages)fail(SourceStatus::MalformedSource);
    for(const auto address:{init,play}) {
        const auto at=std::size_t(banks[(address-0x8000)/4096])*4096+address%4096;
        if(at<prefix || at-prefix>=bytes)fail(SourceStatus::MalformedSource);
    }
}
template<class Input> EmulatedFormat parseNsfe(Input& input,const DecodeLimits& limits) {
    if(!input.magic(0,"NSFE"))fail(SourceStatus::UnsupportedFormat);
    // Reviewed base APU/six expansion bits and explicit pre-DATA BANK mapping.
    std::size_t p=4,chunks=0;unsigned phase=0,tracks=0,initial=0;
    std::uint32_t load=0,init=0,play=0;
    std::vector<std::uint32_t> times,fades;
    bool seenTime=false,seenFade=false,seenBank=false;std::array<bool,3> descriptive{};std::array<std::uint8_t,8> banks{};
    while(p<input.size()) {
        if(chunks++>=limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        input.need(p,8);const auto size=input.u32(p);const auto tag=p+4,begin=p+8;
        input.need(begin,size);p=begin+size;
        if(input.magic(tag,"INFO")) {
            if(phase || size!=10)fail(SourceStatus::MalformedSource);
            load=input.u16(begin);init=input.u16(begin+2);play=input.u16(begin+4);
            const auto region=input.u8(begin+6),chips=input.u8(begin+7);
            if(region>2 || (chips&~0x3fu))fail(SourceStatus::UnsupportedCodec);
            tracks=input.u8(begin+8);initial=input.u8(begin+9);
            if(!tracks || tracks>128 || initial>=tracks)fail(SourceStatus::MalformedSource);
            phase=1;
        } else if(input.magic(tag,"BANK")) {
            // The kernel consumes its initial bank map when DATA is loaded.
            if(phase!=1 || seenBank || size!=banks.size())fail(SourceStatus::MalformedSource);
            seenBank=true;for(unsigned n=0;n<banks.size();++n)banks[n]=input.u8(begin+n);
        } else if(input.magic(tag,"DATA")) {
            if(phase!=1 || !size)fail(SourceStatus::MalformedSource);
            validateNsfeProgram(load,init,play,size,banks);
            phase=2;
        } else if(input.magic(tag,"NEND")) {
            if(phase!=2 || size || p!=input.size())fail(SourceStatus::MalformedSource);
            phase=3;break;
        } else if(input.magic(tag,"time") || input.magic(tag,"fade")) {
            const bool time=input.magic(tag,"time");auto& seen=time ? seenTime : seenFade;
            if(!phase || seen || size!=tracks*4)fail(SourceStatus::MalformedSource);
            seen=true;auto& values=time ? times : fades;values.reserve(tracks);
            for(unsigned n=0;n<tracks;++n) {
                const auto value=input.u32(begin+n*4);
                if(value>INT32_MAX || (time && !value))fail(SourceStatus::UnsupportedCodec);
                values.push_back(value);
            }
        } else if(input.magic(tag,"auth") || input.magic(tag,"tlbl") || input.magic(tag,"text")) {
            const auto which=input.magic(tag,"auth") ? 0u : input.magic(tag,"tlbl") ? 1u : 2u;
            if(!phase || descriptive[which] || (size && input.u8(p-1)!=0))fail(SourceStatus::MalformedSource);
            descriptive[which]=true;
        } else {
            // Playlist remapping, rate extensions and unknown chunks may
            // change execution/identity; do not silently skip them at admission.
            fail(SourceStatus::UnsupportedCodec);
        }
    }
    if(phase!=3)fail(SourceStatus::MalformedSource);
    if(!seenTime)fail(SourceStatus::UnsupportedCodec); // No invented duration.
    EmulatedFormat result;result.initialSong=initial;result.songs.reserve(tracks);
    for(unsigned n=0;n<tracks;++n)result.songs.push_back({std::uint64_t(times[n])*64,seenFade ? std::uint64_t(fades[n])*64 : 0});
    return result;
}
template<class Input> EmulatedFormat parseSpc(Input& input,const DecodeLimits& limits) {
    input.need(0,37);
    constexpr char magic[]="SNES-SPC700 Sound File Data v0.30";
    for(unsigned n=0;n<33;++n)if(input.u8(n)!=static_cast<std::uint8_t>(magic[n]))fail(SourceStatus::UnsupportedFormat);
    if(input.size()<0x10200)fail(SourceStatus::UnsupportedCodec);
    if(input.u8(33)!=0x1a || input.u8(34)!=0x1a || input.u8(36)!=30)fail(SourceStatus::MalformedSource);
    const auto timing=spcTiming(input,limits);
    return {{{timing.first,timing.second}},0,std::nullopt};
}
template<class Input> EmulatedFormat parseEmulated(Input& input,const DecodeLimits& limits,EmulatedKind kind) {
    if(kind==EmulatedKind::VgmFm) {
        if(input.size()>8*1024*1024 || limits.maximumDecodedBytes<4*1024*1024+3*input.size())fail(SourceStatus::ResourceLimit);
        return {{{0,0}},0,parseVgmFm(input,limits)};
    }
    if(input.size()>MaximumEmulatedInput)fail(SourceStatus::ResourceLimit);
    auto result=kind==EmulatedKind::Nsfe ? parseNsfe(input,limits) : parseSpc(input,limits);
    for(const auto& timing:result.songs) {
        const auto ticks=timing.ticks+timing.fadeTicks;
        if(ticks>std::uint64_t(INT32_MAX)*64)fail(SourceStatus::ResourceLimit);
        const auto frames=(ticks*44100+63999)/64000;
        boundStreamDuration(frames,44100,limits);
        if(limits.maximumGeneratedFrames<=0 || frames>static_cast<std::uint64_t>(limits.maximumGeneratedFrames))fail(SourceStatus::ResourceLimit);
    }
    // Conservative admission for fixed emulator/filter state plus the small
    // original. This is not a claim of allocator interception in third-party code.
    if(limits.maximumDecodedBytes<4*1024*1024+input.size())fail(SourceStatus::ResourceLimit);
    return result;
}
}
