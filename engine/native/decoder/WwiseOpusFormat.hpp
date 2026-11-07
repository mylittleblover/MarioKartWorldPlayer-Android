#pragma once
#include "decoder/OpusFormat.hpp"

namespace player2::audio {
// Wwise uses an unpadded RIFF chunk directory. This is a codec probe, not
// admission: the complete directory/seek table/packets are checked on open.
template<class Bytes> bool wwiseOpusFormat(Bytes& bytes) {
    if(bytes.size()<12 || !bytes.magic(0,"RIFF") || !bytes.magic(8,"WAVE"))return false;
    for(std::size_t at=12,chunks=0;at<bytes.size() && chunks<4096;++chunks) {
        if(bytes.size()-at<8)return false;
        const auto size=bytes.u32(at+4);at+=8;
        if(bytes.magic(at-8,"fmt "))return size>=2 && bytes.size()-at>=2 && bytes.u16(at)==0x3041;
        if(size>bytes.size()-at)return false;
        at+=size;
    }
    return false;
}
inline bool probeWwiseOpus(ByteView view)noexcept {
    try {SourceBytes bytes(view,{});return wwiseOpusFormat(bytes);}catch(...){return false;}
}
// Complete modern WEM OPUS_WEM v1, mapping0, 48k mono/stereo. Seek words
// contain actual packet byte lengths, not decoder-state snapshots.
template<class Bytes> OpusLayout parseWwiseOpus(Bytes& bytes,const DecodeLimits& limits) {
    if(!bytes.magic(0,"RIFF") || !bytes.magic(8,"WAVE"))fail(SourceStatus::UnsupportedFormat);
    if(std::uint64_t(bytes.u32(4))+8!=bytes.size())fail(SourceStatus::MalformedSource);
    std::size_t fmt=0,seek=0,seekSize=0,data=0,dataSize=0,chunks=0;
    std::optional<SourceLoopMetadata> loop;bool sawLoop=false;
    for(std::size_t at=12;at<bytes.size();) {
        bytes.need(at,8);const auto size=bytes.u32(at+4);const auto begin=at+8;bytes.need(begin,size);
        if(++chunks>4096 || chunks>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
        if(bytes.magic(at,"fmt ")) {
            if(fmt)fail(SourceStatus::MalformedSource);
            if(size!=36)fail(SourceStatus::UnsupportedCodec);
            fmt=begin;
        } else if(bytes.magic(at,"seek")) {
            if(seek)fail(SourceStatus::MalformedSource);
            seek=begin;seekSize=size;
        } else if(bytes.magic(at,"data")) {
            if(data)fail(SourceStatus::MalformedSource);
            data=begin;dataSize=size;
        } else if(bytes.magic(at,"smpl")) {
            if(sawLoop || size<36)fail(SourceStatus::MalformedSource);
            sawLoop=true;
            const auto count=bytes.u32(begin+28);if(count>1)fail(SourceStatus::UnsupportedCodec);
            if(count==1) {
                if(size<60)fail(SourceStatus::MalformedSource);
                if(bytes.u32(begin+40) || bytes.u32(begin+52))fail(SourceStatus::UnsupportedCodec);
                loop=SourceLoopMetadata{{bytes.u32(begin+44)},{std::int64_t(bytes.u32(begin+48))+1}};
            }
        } else if(!bytes.magic(at,"JUNK") && !bytes.magic(at,"LIST") && !bytes.magic(at,"akd ") && !bytes.magic(at,"cue "))
            fail(SourceStatus::UnsupportedCodec);
        at=begin+size; // Wwise chunks do not add standard RIFF word padding.
    }
    if(!fmt || !seek || !data)fail(SourceStatus::MalformedSource);
    if(bytes.u16(fmt)!=0x3041 || bytes.u16(fmt+12) || bytes.u16(fmt+14) || bytes.u16(fmt+16)!=16 ||
        bytes.u8(fmt+34)!=1 || bytes.u8(fmt+35)!=0)fail(SourceStatus::UnsupportedCodec);
    const auto channels=bytes.u16(fmt+2);validateFormat(48000,channels);
    if(bytes.u32(fmt+4)!=48000)fail(SourceStatus::UnsupportedSampleRate);
    const auto config=bytes.u32(fmt+20),mask=channels==1 ? 4u : 3u;
    if(config!=0 && config!=mask && config!=(unsigned(channels)|0x100u|(mask<<12)))fail(SourceStatus::UnsupportedChannels);
    const auto count=bytes.u32(fmt+28),sourceFrames=bytes.u32(fmt+24);const auto packetFrames=bytes.u16(fmt+18);
    if(!count || !sourceFrames || std::uint64_t(count)*2!=seekSize)fail(SourceStatus::MalformedSource);
    if(limits.maximumDecodedBytes<OpusWorkingBudget || count>limits.maximumEvents ||
        count>(limits.maximumDecodedBytes-OpusWorkingBudget)/320)fail(SourceStatus::ResourceLimit);
    OpusLayout result;result.preSkip=bytes.u16(fmt+32);result.packets.reserve(count);
    std::array<std::uint8_t,MaximumOpusPacket> packet{};std::size_t offset=0;std::uint64_t raw=0;
    for(std::size_t n=0;n<count;++n) {
        const auto size=bytes.u16(seek+n*2);
        if(!size || size>dataSize-offset)fail(SourceStatus::MalformedSource);
        for(std::size_t i=0;i<size;++i)packet[i]=bytes.u8(data+offset+i);
        const auto frames=opus_packet_get_nb_samples(packet.data(),size,48000);
        if(frames<=0 || frames>5760 || frames!=packetFrames)fail(SourceStatus::MalformedSource);
        result.packets.push_back({{{data+offset,size}},size,frames});offset+=size;raw+=frames;
    }
    const auto end=std::uint64_t(result.preSkip)+sourceFrames;
    if(offset!=dataSize || end>raw || raw-end>=packetFrames)fail(SourceStatus::MalformedSource);
    boundStreamDuration(sourceFrames,48000,limits);
    if(loop && (loop->start.value>=loop->end.value || loop->end.value>sourceFrames))fail(SourceStatus::SourceLoopBounds);
    result.metadata={"native.wwise-opus",SourceKind::GameStream,48000,channels,{sourceFrames},SeekSupport::Replay,
        KnownDuration|DeterministicRender|Streaming|(loop ? LoopMetadata : 0u),loop,SampleEncoding::Opus,0};
    return result;
}
std::unique_ptr<AudioFrameSource> openWwiseOpus(ByteView bytes,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openWwiseOpus(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
std::unique_ptr<AudioFrameSource> openRiffMedia(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits);
}
