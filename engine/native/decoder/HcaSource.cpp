#include "decoder/HcaFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <cmath>

namespace player2::audio {
namespace {
struct HcaDelete {void operator()(clHCA* value)const noexcept{clHCA_delete(value);}};
struct HcaState {
    std::unique_ptr<clHCA,HcaDelete> decoder{clHCA_new()};
    std::vector<std::uint8_t> encoded;
    std::array<float,2048> pcm{};
    std::size_t nextBlock{};
    explicit HcaState(const HcaLayout& layout):encoded(layout.blockSize) {
        if(!decoder)fail(SourceStatus::ResourceLimit);
        if(clHCA_DecodeHeader(decoder.get(),layout.header.data(),static_cast<unsigned>(layout.header.size()))<0)
            fail(SourceStatus::MalformedSource);
    }
};
class HcaSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;
    EncodedSourceBytes bytes_;
    HcaLayout layout_;
    std::unique_ptr<HcaState> state_;
    SourceSamplePosition position_;
    SourceStatus status_{SourceStatus::Ok};
    void prepare(HcaState& state,std::size_t block) {
        while(state.nextBlock<=block) {
            if(state.nextBlock>=layout_.blockCount)fail(SourceStatus::MalformedSource);
            bytes_.copy(layout_.header.size()+state.nextBlock*layout_.blockSize,state.encoded.data(),state.encoded.size());
            const auto bits=clHCA_DecodeBlock(state.decoder.get(),state.encoded.data(),static_cast<unsigned>(state.encoded.size()));
            if(bits<0 || std::size_t(bits)>state.encoded.size()*8-16)fail(SourceStatus::MalformedSource);
            clHCA_ReadSamples(state.decoder.get(),state.pcm.data());
            for(std::size_t n=0;n<1024u*static_cast<unsigned>(metadata().channels);++n)
                if(!std::isfinite(state.pcm[n]))fail(SourceStatus::MalformedSource);
            ++state.nextBlock;
        }
    }
    void next(float* output) {
        const auto raw=layout_.delay+static_cast<std::size_t>(position_.value);prepare(*state_,raw/1024);
        if(output)std::copy_n(state_->pcm.data()+(raw%1024)*metadata().channels,metadata().channels,output);
        ++position_.value;
    }
public:
    HcaSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),bytes_(*input_,limits),
        layout_(parseHca(bytes_,limits)),state_(std::make_unique<HcaState>(layout_)){}
    const SourceMetadata& metadata()const noexcept override{return layout_.metadata;}
    SourceSamplePosition position()const noexcept override{return position_;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        const auto channels=static_cast<std::size_t>(metadata().channels);
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*channels,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata().duration.value-position_.value));std::size_t count=0;
        try{while(count<wanted){next(output+count*channels);++count;}}
        catch(const SourceFailure& failure){status_=failure.status;}
        catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;}
        catch(...){status_=SourceStatus::MalformedSource;}
        std::fill_n(output+count*channels,(frames-count)*channels,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata().duration.value)return SourceStatus::InvalidRequest;
        try {
            auto next=std::make_unique<HcaState>(layout_);
            if(target.value<metadata().duration.value)prepare(*next,(layout_.delay+static_cast<std::size_t>(target.value))/1024);
            state_=std::move(next);position_=target;status_=SourceStatus::Ok;return status_;
        }catch(const SourceFailure& failure){return failure.status;}
         catch(const std::bad_alloc&){return SourceStatus::ResourceLimit;}
         catch(...){return SourceStatus::MalformedSource;}
    }
    void reset()noexcept override{const auto result=seek({0});if(result!=SourceStatus::Ok)status_=result;}
};
}
std::unique_ptr<AudioFrameSource> openHca(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<HcaSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openHca(ByteView bytes,const DecodeLimits& limits) {
    return openHca(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
