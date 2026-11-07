#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include "decoder/CodecAllocation.hpp"
#include "decoder/EmulatedFormat.hpp"
#include <zlib.h>
#include <limits>

namespace player2::audio {
namespace {
struct GzipInflater {
    CodecAllocationBudget budget;
    z_stream stream{};
    explicit GzipInflater(std::size_t maximum) {
        budget.maximum=maximum;
        stream.opaque=&budget;
        stream.zalloc=[](void* owner,unsigned items,unsigned size)->void* {
            if(size && items>std::numeric_limits<std::size_t>::max()/size)return nullptr;
            return CodecAllocationBudget::allocate(std::size_t(items)*size,owner);
        };
        stream.zfree=[](void* owner,void* memory){CodecAllocationBudget::release(memory,owner);};
        const auto status=inflateInit2(&stream,15+16); // gzip only; checks CRC32 and ISIZE.
        if(status!=Z_OK)fail(status==Z_MEM_ERROR ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource);
    }
    ~GzipInflater(){inflateEnd(&stream);}
};
class VgzFrameSource final:public AudioFrameSource {
    std::unique_ptr<AudioFrameSource> source_;
    SourceMetadata metadata_;
public:
    explicit VgzFrameSource(std::unique_ptr<AudioFrameSource> source):source_(std::move(source)),metadata_(source_->metadata()) {
        metadata_.providerId=std::strcmp(metadata_.providerId,"native.vgm-fm")==0 ? "native.vgz-fm" : "native.vgz-psg";
        // VGM command origins refer to inflated bytes, not the immutable gzip
        // root. Do not publish them as root SourceByteOffsets without a mapping.
        metadata_.flags&=~SequenceInspection;
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return source_->position();}
    std::size_t render(float* output,std::size_t frames)noexcept override{return source_->render(output,frames);}
    SourceStatus seek(SourceSamplePosition position)noexcept override{return source_->seek(position);}
    void reset()noexcept override{source_->reset();}
    SourceStatus renderStatus()const noexcept override{return source_->renderStatus();}
};
std::vector<std::uint8_t> inflateVgz(EncodedSourceInput& source,const DecodeLimits& limits) {
    EncodedSourceBytes input(source,limits);
    if(input.u8(0)!=0x1f || input.u8(1)!=0x8b)fail(SourceStatus::UnsupportedFormat);
    if(input.u8(2)!=8)fail(SourceStatus::UnsupportedCodec);
    if(input.u8(3)&0xe0)fail(SourceStatus::MalformedSource);
    // Reserve separate ceilings for zlib state, vector reallocation peak and
    // the emitted bytes. Never trust the untrusted footer as an allocation size.
    const auto ceiling=std::min(MaximumEncodedSourceBytes,limits.maximumDecodedBytes/3);
    if(!ceiling)fail(SourceStatus::ResourceLimit);
    GzipInflater inflater(limits.maximumDecodedBytes/3);
    std::array<std::uint8_t,16384> encoded{},decoded{};
    std::vector<std::uint8_t> output;std::size_t offset=0;
    auto& stream=inflater.stream;
    for(;;) {
        if(!stream.avail_in && offset<input.size()) {
            const auto count=std::min(encoded.size(),input.size()-offset);
            input.copy(offset,encoded.data(),count);offset+=count;
            stream.next_in=encoded.data();stream.avail_in=static_cast<unsigned>(count);
        }
        stream.next_out=decoded.data();stream.avail_out=decoded.size();
        const auto before=stream.avail_in;
        const auto status=inflate(&stream,Z_NO_FLUSH);
        const auto count=decoded.size()-stream.avail_out;
        if(count>ceiling-output.size())fail(SourceStatus::ResourceLimit);
        if(output.size()+count>output.capacity()) {
            const auto capacity=std::min(ceiling,std::max(output.size()+count,std::max<std::size_t>(1024,output.capacity()*2)));
            output.reserve(capacity);
        }
        output.insert(output.end(),decoded.data(),decoded.data()+count);
        if(status==Z_STREAM_END) {
            // A VGZ is exactly one gzip member. Concatenated members/trailing
            // garbage are refused instead of silently dropping source content.
            if(stream.avail_in || offset!=input.size())fail(SourceStatus::MalformedSource);
            return output;
        }
        if(status==Z_MEM_ERROR || inflater.budget.exhausted)fail(SourceStatus::ResourceLimit);
        if(status!=Z_OK || (!count && stream.avail_in==before && offset==input.size()))fail(SourceStatus::MalformedSource);
    }
}
}
bool probeVgzFm(ByteView bytes)noexcept {
    // Probe only a bounded prefix; full gzip CRC/size and command validation is
    // still mandatory at open. No encoded-root coordinate is invented here.
    try {
        if(!bytes.data || bytes.size<3)return false;
        GzipInflater inflater(128*1024);std::array<std::uint8_t,64> header{};
        auto& stream=inflater.stream;
        stream.next_in=const_cast<Bytef*>(bytes.data);stream.avail_in=static_cast<unsigned>(std::min<std::size_t>(bytes.size,4096));
        stream.next_out=header.data();stream.avail_out=header.size();
        const auto status=inflate(&stream,Z_NO_FLUSH);
        if((status!=Z_OK && status!=Z_STREAM_END) || stream.avail_out)return false;
        SourceBytes input({header.data(),header.size()},{});
        return input.magic(0,"Vgm ") && (input.u32(0x10)!=0 || input.u32(0x2c)!=0);
    }catch(...){return false;}
}
std::unique_ptr<AudioFrameSource> openVgz(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,bool fm) {
    if(!input)fail(SourceStatus::InvalidRequest);
    auto bytes=inflateVgz(*input,limits);
    auto inner=limits;
    inner.maximumInputBytes=MaximumEncodedSourceBytes;
    inner.maximumDecodedBytes-=bytes.capacity();
    auto source=fm ? openEmulated({bytes.data(),bytes.size()},inner,EmulatedKind::VgmFm) : openVgm({bytes.data(),bytes.size()},inner);
    return std::make_unique<VgzFrameSource>(std::move(source));
}
std::unique_ptr<AudioFrameSource> openVgz(ByteView bytes,const DecodeLimits& limits,bool fm) {
    return openVgz(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,fm);
}
}
