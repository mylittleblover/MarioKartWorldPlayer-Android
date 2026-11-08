#pragma once
#include "decoder/NintendoDsp.hpp"
#include "decoder/OpusFormat.hpp"

namespace player2::audio {
struct BwavLayout {
    SourceMetadata metadata;
    std::array<std::size_t,MaximumNintendoStreamChannels> starts{};
    std::array<NintendoDspChannel,MaximumNintendoStreamChannels> channels{};
    std::array<std::uint8_t,MaximumNintendoStreamChannels> speakers{};
    bool dsp{};
    // Non-empty when the channels are Opus rather than DSP or PCM. In that case `starts` and `channels`
    // are unused and the payload is read through these packet chains instead.
    std::vector<OpusLayout> opusChannels;
};
// Chunk tags that carry a codec-2 payload and its configuration.
inline constexpr std::uint32_t BwavChunkConfig=0x80000001u,BwavChunkPayload=0x80000004u;
// CELT-only Opus has a 2.5 ms decoder delay, which is 120 samples at 48 kHz. The config block's sixth word
// says the same number independently.
inline constexpr unsigned BwavOpusPreSkip=120;
inline constexpr unsigned BwavOpusFrameSamples=960;
// Reads one channel's codec-2 region into packet spans.
//
// The chain is `u32 size (BIG-endian)`, four opaque bytes, then the packet. Those four bytes are not needed
// to decode and their meaning is not established; every checksum and counter tried against them failed.
// What is established is that ignoring them consumes all 594 channel regions in the corpus exactly, and
// that the packets then decode to within one LSB of the reference decoder.
template<class Bytes> OpusLayout parseBwavOpusChannel(Bytes& input,std::size_t start,std::uint32_t samples,
    unsigned channels,const DecodeLimits& limits) {
    OpusLayout layout;
    // Every channel in the corpus carries this first chunk and this pre-skip. A different value is a
    // format this reader has no evidence for, so it is refused rather than guessed at.
    if(input.u32(start)!=BwavChunkConfig)fail(SourceStatus::UnsupportedCodec);
    const auto configBytes=input.u32(start+4);
    if(configBytes<24 || configBytes>64)fail(SourceStatus::MalformedSource);
    if(input.u32(start+8+16)!=BwavOpusPreSkip)fail(SourceStatus::UnsupportedCodec);
    const auto payload=start+8+configBytes;
    if(input.u32(payload)!=BwavChunkPayload)fail(SourceStatus::UnsupportedCodec);
    const auto payloadBytes=input.u32(payload+4);
    std::size_t at=payload+8;
    const auto end=at+payloadBytes;
    input.need(at,payloadBytes);
    std::uint64_t decoded=0;
    while(at<end) {
        // The only big-endian field in an otherwise little-endian container.
        const auto size=input.u32(at,true);
        // An Opus packet is at most 5760 samples and realistically a few hundred bytes here; accepting a
        // huge value would let one bad length run to the end of the region and surface as a decode error.
        if(size==0 || size>MaximumOpusPacket)fail(SourceStatus::MalformedSource);
        const auto packetStart=at+8;
        if(packetStart+size>end)fail(SourceStatus::MalformedSource);
        layout.packets.push_back({{OpusSpan{packetStart,size}},size,static_cast<int>(BwavOpusFrameSamples)});
        decoded+=BwavOpusFrameSamples;
        at=packetStart+size;
        if(layout.packets.size()>limits.maximumEvents)fail(SourceStatus::ResourceLimit);
    }
    if(at!=end || layout.packets.empty())fail(SourceStatus::MalformedSource);
    // The declared sample count is the packet count times the frame, minus the pre-skip. A channel whose
    // packets fall short is padded with silence by the renderer, which is what the reference decoder does.
    if(samples>decoded)fail(SourceStatus::MalformedSource);
    if(decoded-samples>BwavOpusFrameSamples)fail(SourceStatus::MalformedSource);
    layout.preSkip=BwavOpusPreSkip;
    layout.gain=0;
    layout.metadata={"native.bwav",SourceKind::GameStream,48000,static_cast<std::int32_t>(channels),
        {static_cast<std::int64_t>(samples)},SeekSupport::Replay,
        KnownDuration|DeterministicRender|Streaming,std::nullopt,SampleEncoding::Opus,0};
    return layout;
}
// BWAV v1, complete little-endian planar PCM16, Nintendo DSP channels, or chunked Opus (codec kind 2).
// Prefetch fragments and NXOpus are still refused: they are different containers, not different codecs.
template<class Bytes> BwavLayout parseBwav(Bytes& input,const DecodeLimits& limits) {
    if(!input.magic(0,"BWAV"))fail(SourceStatus::UnsupportedFormat);
    input.need(0,16);
    if(input.u16(4)!=0xfeff || input.u16(6)!=1 || input.u16(12))fail(SourceStatus::UnsupportedCodec);
    const auto count=input.u16(14);
    if(count<1 || count>MaximumNintendoStreamChannels)fail(SourceStatus::UnsupportedChannels);
    if(count>2 && limits.maximumDecodedBytes<65536)fail(SourceStatus::ResourceLimit);
    const std::size_t headerEnd=16+count*76;input.need(0,headerEnd);
    BwavLayout result;std::uint32_t rate=0,frames=0;unsigned codec=0;
    std::optional<SourceLoopMetadata> loop;
    std::size_t previousEnd=headerEnd;
    for(unsigned ch=0;ch<count;++ch) {
        const auto h=16+ch*76;
        const auto kind=input.u16(h),speaker=input.u16(h+2);
        const auto hz=input.u32(h+4),full=input.u32(h+8),samples=input.u32(h+12);
        // Codec kind 2 is a chunked Opus stream. It is a different payload shape, not a different header,
        // so the per-channel header checks below still apply to it -- and then the payload is read through
        // its own chain instead of the DSP block arithmetic.
        if(kind>2)fail(SourceStatus::UnsupportedCodec);
        validateFormat(static_cast<std::int32_t>(hz),1);boundStreamDuration(samples,hz,limits);
        if(full!=samples || input.u32(h+48)!=input.u32(h+52) || input.u32(h+56)!=1 || input.u16(h+74))fail(SourceStatus::UnsupportedCodec);
        if((count==1 && speaker!=0 && speaker!=2) || (count==2 && speaker!=ch))fail(SourceStatus::UnsupportedChannels);
        if(speaker>2)fail(SourceStatus::UnsupportedChannels);
        result.speakers[ch]=static_cast<std::uint8_t>(speaker);
        const auto last=input.u32(h+60),first=input.u32(h+64);
        std::optional<SourceLoopMetadata> channelLoop;
        if(kind==2) {
            // Opus files carry no loop window; the marker records that the game uses are elsewhere, and a
            // loop invented here would be this reader's claim rather than the container's.
            if(last!=0xffffffffu || first)fail(SourceStatus::UnsupportedCodec);
        } else if(last!=0xffffffffu) {
            if(first>=last || last>samples)fail(SourceStatus::SourceLoopBounds);
            channelLoop=SourceLoopMetadata{{first},{last}};
        } else if(first)fail(SourceStatus::UnsupportedCodec);
        if(ch==0){codec=kind;rate=hz;frames=samples;loop=channelLoop;}
        else if(codec!=kind || rate!=hz || frames!=samples || bool(loop)!=bool(channelLoop) ||
            (loop && (loop->start.value!=channelLoop->start.value || loop->end.value!=channelLoop->end.value)))fail(SourceStatus::UnsupportedCodec);
        if(kind==2) {
            const auto start=static_cast<std::size_t>(input.u32(h+52));
            // The region's own two chunks bound it, so the offset does not need a declared size here.
            input.need(start,16);
            result.opusChannels.push_back(parseBwavOpusChannel(input,start,samples,count,limits));
            continue;
        }
        const auto blocks=(std::uint64_t(samples)+13)/14;
        const auto encoded=kind ? blocks*8 : std::uint64_t(samples)*2;
        // MKW truncates the final DSP block of the final channel to exactly the
        // bytes its remaining samples need (1 header byte + one nibble per
        // sample), and pads every other channel up to a full block. A strict
        // "full block present" requirement rejects 2403 of the 20991 real files,
        // 140 of them music. Accept the tight form as well; the caller bounds
        // every read by the frame count, not by the declared block end.
        const auto finalSamples=kind ? samples-static_cast<std::uint32_t>((blocks-1)*14) : 0u;
        const auto minimum=kind ? (blocks-1)*8+1+(finalSamples+1)/2 : encoded;
        const auto start=static_cast<std::size_t>(input.u32(h+52));
        input.need(start,static_cast<std::size_t>(minimum));
        if(start<previousEnd || start-previousEnd>63 || start%4)fail(SourceStatus::MalformedSource);
        for(auto p=previousEnd;p<start;++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
        result.starts[ch]=start;
        // Only the final channel may omit its block padding; every earlier channel
        // is followed by that padding, which the gap check above already validates.
        previousEnd=start+static_cast<std::size_t>(ch+1==count ? minimum : encoded);
        auto& channel=result.channels[ch];
        if(kind) {
            for(unsigned n=0;n<16;++n)channel.coefficients[n]=static_cast<std::int16_t>(dspSigned(input.u16(h+16+n*2)));
            channel.initial={dspSigned(input.u16(h+70)),dspSigned(input.u16(h+72))};
            // The initial predictor/scale at h+68 seeds the history when a seek
            // lands mid-stream; it is not required to equal the first block's own
            // header byte. 654 of the 20991 real files legitimately differ, so the
            // equality this parser used to demand rejected valid audio. Only the
            // range is an invariant: predictor >= 8 cannot be decoded.
            if(input.u16(h+68)>0x7f)fail(SourceStatus::MalformedSource);
        } else {
            for(unsigned n=16;n<48;++n)if(input.u8(h+n))fail(SourceStatus::UnsupportedCodec);
            for(unsigned n=68;n<74;++n)if(input.u8(h+n))fail(SourceStatus::UnsupportedCodec);
        }
    }
    // An Opus BWAV's byte layout is bounded by its own chunk headers, so the trailing-gap check applies
    // only to the DSP and PCM forms where the region end is arithmetic.
    if(codec!=2) {
        if(input.size()-previousEnd>63)fail(SourceStatus::UnsupportedCodec);
        for(auto p=previousEnd;p<input.size();++p)if(input.u8(p))fail(SourceStatus::UnsupportedCodec);
    }
    // The opaque container checksum is not advertised as content authentication;
    // the shared Source root SHA-256 identity remains authoritative.
    result.dsp=codec==1;
    if(codec==2) {
        // Every channel is its own mono Opus stream, so the number of chains must equal the channel count.
        if(result.opusChannels.size()!=count)fail(SourceStatus::MalformedSource);
        result.metadata=result.opusChannels.front().metadata;
        result.metadata.channels=static_cast<std::int32_t>(count);
        return result;
    }
    result.metadata={"native.bwav",SourceKind::GameStream,static_cast<std::int32_t>(rate),count,{frames},SeekSupport::Fast,
        KnownDuration|LoopMetadata|DeterministicRender|Streaming,loop,codec ? SampleEncoding::DspAdpcm : SampleEncoding::PcmSigned,codec ? 4 : 16};
    return result;
}
}
