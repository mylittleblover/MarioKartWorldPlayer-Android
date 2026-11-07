#pragma once
#include "decoder/ModuleModFormat.hpp"

namespace player2::audio {
struct XmCell {
    std::optional<SourceByteOffset> origin;
    std::uint8_t note{},instrument{},volume{},effect{},parameter{};
};
struct XmPattern {SourceByteOffset origin;std::uint16_t rows{};std::vector<XmCell> cells;};
struct XmEnvelopePoint {std::uint16_t tick{},value{};};
struct XmEnvelope {
    std::vector<XmEnvelopePoint> points;
    std::uint8_t sustain{},loopStart{},loopEnd{},type{};
};
struct XmSample {
    SourceByteOffset origin,data;SourceFrameCount frames;
    std::optional<SourceLoopMetadata> loop;std::int32_t fineTune{},relativeNote{};
    std::uint8_t volume{},panning{},depth{},loopMode{};std::string name;
};
struct XmInstrument {
    SourceByteOffset origin;std::string name;std::array<std::uint8_t,96> sampleMap{};
    XmEnvelope volumeEnvelope,panningEnvelope;
    std::array<std::uint8_t,4> vibrato{};std::uint16_t fadeOut{};
    std::vector<XmSample> samples;
};
struct XmFormat {
    std::uint64_t encodedBytes{};
    std::string title;std::uint16_t channels{},restart{},speed{},tempo{};bool linearPeriods{};
    std::vector<std::uint8_t> orders;std::vector<XmPattern> patterns;std::vector<XmInstrument> instruments;
};
inline bool probeXm(ByteView bytes)noexcept {
    return bytes.data && bytes.size>=17 && std::memcmp(bytes.data,"Extended Module: ",17)==0;
}
template<class Input> XmEnvelope xmEnvelope(Input& input,std::size_t instrument,bool panning) {
    XmEnvelope result;const auto count=input.u8(instrument+225+static_cast<std::size_t>(panning));
    if(count>12)fail(SourceStatus::MalformedSource);
    const auto controls=instrument+(panning ? 230u : 227u);
    result.sustain=input.u8(controls);result.loopStart=input.u8(controls+1);result.loopEnd=input.u8(controls+2);
    result.type=input.u8(instrument+(panning ? 234u : 233u));
    if(result.type&~7u)fail(SourceStatus::UnsupportedCodec);
    for(std::size_t n=0;n<count;++n) {
        const auto at=instrument+(panning ? 177u : 129u)+n*4;
        const XmEnvelopePoint point{input.u16(at),input.u16(at+2)};
        if(point.value>64 || (n && point.tick<=result.points.back().tick))fail(SourceStatus::MalformedSource);
        result.points.push_back(point);
    }
    if((result.type&1) && !count)fail(SourceStatus::MalformedSource);
    if((result.type&2) && result.sustain>=count)fail(SourceStatus::MalformedSource);
    if((result.type&4) && (result.loopStart>result.loopEnd || result.loopEnd>=count))fail(SourceStatus::MalformedSource);
    return result;
}
inline bool xmEffect(std::uint8_t effect)noexcept {
    return effect<=17 || effect==20 || effect==21 || effect==25 || effect==27 || effect==29 || effect==33;
}
template<class Input> XmFormat parseXmFormat(Input& input,const DecodeLimits& limits) {
    input.need(0,80);static constexpr char identity[]="Extended Module: ";
    for(std::size_t n=0;n<17;++n)if(input.u8(n)!=static_cast<std::uint8_t>(identity[n]))fail(SourceStatus::UnsupportedFormat);
    if(input.u8(37)!=0x1a)fail(SourceStatus::MalformedSource);
    if(input.u16(58)!=0x0104)fail(SourceStatus::UnsupportedCodec); // Earlier layouts differ; never infer them.
    const auto headerSize=input.u32(60);if(headerSize<276)fail(SourceStatus::MalformedSource);input.need(60,headerSize);
    const auto orderCount=input.u16(64),patternCount=input.u16(70),instrumentCount=input.u16(72);
    XmFormat result;result.encodedBytes=input.size();result.title=modText(input,17,20);result.channels=input.u16(68);result.restart=input.u16(66);
    result.speed=input.u16(76);result.tempo=input.u16(78);const auto flags=input.u16(74);
    if(!orderCount || orderCount>256 || !patternCount || patternCount>256 || instrumentCount>128)fail(SourceStatus::MalformedSource);
    if(!result.channels || result.channels>32)fail(SourceStatus::UnsupportedChannels);
    if(result.restart>=orderCount || !result.speed || result.speed>31 || result.tempo<32 || result.tempo>255)fail(SourceStatus::MalformedSource);
    if(flags&~1u)fail(SourceStatus::UnsupportedCodec);
    result.linearPeriods=flags&1;
    for(std::size_t n=0;n<orderCount;++n) {const auto pattern=input.u8(80+n);if(pattern>=patternCount)fail(SourceStatus::MalformedSource);result.orders.push_back(pattern);}
    std::size_t cursor=60+headerSize,totalCells=0,totalSampleFrames=0;
    for(std::size_t n=0;n<patternCount;++n) {
        input.need(cursor,9);const auto size=input.u32(cursor);const auto rows=input.u16(cursor+5),packed=input.u16(cursor+7);
        if(size<9 || !rows || rows>256)fail(SourceStatus::MalformedSource);
        if(input.u8(cursor+4)!=0)fail(SourceStatus::UnsupportedCodec);
        input.need(cursor,size);const auto data=cursor+size;input.need(data,packed);const auto end=data+packed;
        const auto cells=std::size_t(rows)*result.channels;
        if(cells>limits.maximumEvents || totalCells>limits.maximumEvents-cells ||
            cells>limits.maximumDecodedBytes/(2*sizeof(XmCell)) || totalCells>limits.maximumDecodedBytes/(2*sizeof(XmCell))-cells)fail(SourceStatus::ResourceLimit);
        totalCells+=cells;XmPattern pattern{{cursor},rows,{}};pattern.cells.reserve(cells);auto at=data;
        for(std::size_t k=0;k<cells;++k) {
            XmCell cell;
            if(packed) {
                if(at>=end)fail(SourceStatus::MalformedSource);
                cell.origin=SourceByteOffset{at};const auto byte=input.u8(at++);
                std::array<std::uint8_t,5> fields{};
                if(byte&128) {
                    if(byte&96)fail(SourceStatus::MalformedSource);
                    for(int f=0;f<5;++f)if(byte&(1u<<f)) {if(at>=end)fail(SourceStatus::MalformedSource);fields[f]=input.u8(at++);}
                } else {
                    if(end-at<4)fail(SourceStatus::MalformedSource);
                    fields[0]=byte;for(int f=1;f<5;++f)fields[f]=input.u8(at++);
                }
                cell.note=fields[0];cell.instrument=fields[1];cell.volume=fields[2];cell.effect=fields[3];cell.parameter=fields[4];
                if(cell.note>97 || cell.instrument>instrumentCount || (cell.volume && cell.volume<16))fail(SourceStatus::MalformedSource);
                if(!xmEffect(cell.effect))fail(SourceStatus::UnsupportedCodec);
                if((cell.effect==12 || cell.effect==16) && cell.parameter>64)fail(SourceStatus::MalformedSource);
                if(cell.effect==11 && cell.parameter>=orderCount)fail(SourceStatus::MalformedSource);
                if(cell.effect==13 && ((cell.parameter&15)>9 || (cell.parameter>>4)>9))fail(SourceStatus::MalformedSource);
            }
            pattern.cells.push_back(cell);
        }
        if(at!=end)fail(SourceStatus::MalformedSource);
        result.patterns.push_back(std::move(pattern));cursor=end;
    }
    for(std::size_t n=0;n<instrumentCount;++n) {
        input.need(cursor,29);const auto size=input.u32(cursor);const auto samples=input.u16(cursor+27);
        if(size<29 || samples>16)fail(SourceStatus::MalformedSource);
        input.need(cursor,size);if(input.u8(cursor+26)!=0)fail(SourceStatus::UnsupportedCodec);
        XmInstrument instrument;instrument.origin={cursor};instrument.name=modText(input,cursor+4,22);
        std::size_t sampleSize=0;
        if(samples) {
            if(size<243)fail(SourceStatus::MalformedSource);
            sampleSize=input.u32(cursor+29);if(sampleSize<40)fail(SourceStatus::MalformedSource);
            for(std::size_t k=0;k<96;++k) {instrument.sampleMap[k]=input.u8(cursor+33+k);if(instrument.sampleMap[k]>=samples)fail(SourceStatus::MalformedSource);}
            instrument.volumeEnvelope=xmEnvelope(input,cursor,false);instrument.panningEnvelope=xmEnvelope(input,cursor,true);
            for(std::size_t k=0;k<4;++k)instrument.vibrato[k]=input.u8(cursor+235+k);
            if(instrument.vibrato[0]>3)fail(SourceStatus::UnsupportedCodec);
            instrument.fadeOut=input.u16(cursor+239);
        }
        cursor+=size;
        if(samples && sampleSize>(input.size()-cursor)/samples)fail(SourceStatus::MalformedSource);
        const auto sampleData=cursor+sampleSize*samples;auto data=sampleData;
        for(std::size_t k=0;k<samples;++k) {
            const auto at=cursor+k*sampleSize;const auto length=input.u32(at),loopStart=input.u32(at+4),loopLength=input.u32(at+8);
            const auto type=input.u8(at+14);const auto width=(type&16) ? 2u : 1u;
            if(type&~19u || (type&3)==3 || input.u8(at+17)!=0)fail(SourceStatus::UnsupportedCodec); // Stereo/ADPCM extensions are not PCM-delta.
            if(length%width || loopStart%width || loopLength%width || input.u8(at+12)>64)fail(SourceStatus::MalformedSource);
            const auto frames=length/width;
            if(frames>limits.maximumDecodedBytes/(8*sizeof(float)) || totalSampleFrames>limits.maximumDecodedBytes/(8*sizeof(float))-frames)fail(SourceStatus::ResourceLimit);
            totalSampleFrames+=frames;input.need(data,length);XmSample sample;
            sample.origin={at};sample.data={data};sample.frames.value=frames;sample.depth=width*8;sample.loopMode=type&3;
            sample.volume=input.u8(at+12);const auto tune=input.u8(at+13),relative=input.u8(at+16);
            sample.fineTune=tune<128 ? tune : int(tune)-256;sample.relativeNote=relative<128 ? relative : int(relative)-256;
            sample.panning=input.u8(at+15);sample.name=modText(input,at+18,22);
            if(sample.loopMode) {
                if(!loopLength || loopStart>length || loopLength>length-loopStart)fail(SourceStatus::SourceLoopBounds);
                sample.loop=SourceLoopMetadata{{static_cast<std::int64_t>(loopStart/width)},{static_cast<std::int64_t>((loopStart+loopLength)/width)}};
            }
            instrument.samples.push_back(std::move(sample));data+=length;
        }
        cursor=data;result.instruments.push_back(std::move(instrument));
    }
    if(cursor!=input.size())fail(SourceStatus::UnsupportedCodec);
    return result;
}
inline XmFormat readXmFormat(ByteView bytes,const DecodeLimits& limits) {SourceBytes input(bytes,limits);return parseXmFormat(input,limits);}
inline XmFormat readXmFormat(EncodedSourceInput& source,const DecodeLimits& limits) {EncodedSourceBytes input(source,limits);return parseXmFormat(input,limits);}
}
