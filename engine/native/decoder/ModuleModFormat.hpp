#pragma once
#include "decoder/SourceBytes.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "api/SequenceTypes.hpp"
#include <algorithm>
#include <array>
#include <string>

namespace player2::audio {
struct ModSampleInfo {
    SourceByteOffset origin,data;std::uint32_t bytes{},loopStart{},loopBytes{};
    std::int32_t fineTune{},volume{};std::string name;
};
struct ModCell {
    SourceByteOffset origin;std::uint16_t period{};std::uint8_t instrument{},effect{},parameter{};
};
struct ModFormat {
    std::string title;std::array<ModSampleInfo,31> samples;
    std::vector<std::uint8_t> orders;std::vector<ModCell> cells;std::size_t patterns{};
};
inline bool probeMod(ByteView bytes)noexcept {
    if(!bytes.data || bytes.size<1084)return false;
    const auto* p=bytes.data+1080;
    return p[0]=='M' && p[2]=='K' && ((p[1]=='.' && p[3]=='.') || (p[1]=='!' && p[3]=='!'));
}
template<class Input>
inline std::string modText(Input& bytes,std::size_t begin,std::size_t count) {
    std::string result;
    for(std::size_t n=0;n<count;++n) {
        const auto c=bytes.u8(begin+n);if(c==0)break;
        // Retain the original bytes in the source. Restricted initial subset
        // accepts ASCII labels; other encodings need explicit charset support.
        if(c<32 || c>126)fail(SourceStatus::UnsupportedCodec);
        result.push_back(static_cast<char>(c));
    }
    return result;
}
template<class Input>
inline ModFormat parseModFormat(Input& input,const DecodeLimits& limits) {
    input.need(0,1084);
    if(!input.magic(1080,"M.K.") && !input.magic(1080,"M!K!"))fail(SourceStatus::UnsupportedFormat);
    ModFormat result;result.title=modText(input,0,20);
    const auto orderCount=input.u8(950);
    if(orderCount==0 || orderCount>128)fail(SourceStatus::MalformedSource);
    std::size_t maximumPattern=0;
    for(std::size_t n=0;n<128;++n) {
        const auto pattern=input.u8(952+n);
        if(n<orderCount) {
            if(pattern>=128)fail(SourceStatus::MalformedSource);
            result.orders.push_back(pattern);maximumPattern=std::max(maximumPattern,std::size_t(pattern));
        }else if(pattern!=0)fail(SourceStatus::UnsupportedCodec);
    }
    result.patterns=maximumPattern+1;const auto cells=result.patterns*64*4;
    if(cells>limits.maximumEvents || cells>limits.maximumDecodedBytes/(2*sizeof(ModCell)))fail(SourceStatus::ResourceLimit);
    input.need(1084,cells*4);result.cells.reserve(cells);
    for(std::size_t n=0;n<cells;++n) {
        const auto p=1084+n*4;const auto a=input.u8(p),b=input.u8(p+1),c=input.u8(p+2),d=input.u8(p+3);
        ModCell cell{{p},static_cast<std::uint16_t>(((a&15)<<8)|b),static_cast<std::uint8_t>((a&0xf0)|(c>>4)),static_cast<std::uint8_t>(c&15),d};
        if(cell.instrument>31 || (cell.effect==12 && d>64))fail(SourceStatus::MalformedSource);
        if(cell.effect==13 && ((d>>4)*10+(d&15)>63 || (d&15)>9))fail(SourceStatus::MalformedSource);
        result.cells.push_back(cell);
    }
    std::size_t sampleStart=1084+cells*4;
    for(std::size_t n=0;n<result.samples.size();++n) {
        const auto p=20+n*30;auto& sample=result.samples[n];sample.origin={p};sample.data={sampleStart};
        sample.name=modText(input,p,22);sample.bytes=std::uint32_t(input.u16(p+22,true))*2;
        const auto tune=input.u8(p+24);sample.volume=input.u8(p+25);
        if(tune>15 || sample.volume>64)fail(SourceStatus::MalformedSource);
        sample.fineTune=tune<8 ? tune : int(tune)-16;
        sample.loopStart=std::uint32_t(input.u16(p+26,true))*2;sample.loopBytes=std::uint32_t(input.u16(p+28,true))*2;
        if(sample.loopBytes<=2){sample.loopStart=sample.loopBytes=0;}
        else if(sample.loopStart>sample.bytes || sample.loopBytes>sample.bytes-sample.loopStart)fail(SourceStatus::SourceLoopBounds);
        input.need(sampleStart,sample.bytes);sampleStart+=sample.bytes;
    }
    if(sampleStart!=input.size())fail(SourceStatus::UnsupportedCodec); // Trailer/extended MOD variants need explicit support.
    return result;
}
inline ModFormat readModFormat(ByteView bytes,const DecodeLimits& limits) {
    SourceBytes input(bytes,limits);
    if(!probeMod(bytes))fail(SourceStatus::UnsupportedFormat);
    return parseModFormat(input,limits);
}
inline ModFormat readModFormat(EncodedSourceInput& source,const DecodeLimits& limits) {
    EncodedSourceBytes input(source,limits);return parseModFormat(input,limits);
}

}
