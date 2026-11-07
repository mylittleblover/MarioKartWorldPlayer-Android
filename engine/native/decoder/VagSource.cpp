#include "decoder/VagFormat.hpp"
#include "decoder/PsAdpcm.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"

namespace player2::audio {
namespace {
class VagSource final:public AudioFrameSource {
    std::unique_ptr<EncodedSourceInput> input_;EncodedSourceBytes bytes_;SourceMetadata metadata_;
    struct State {PsAdpcmHistory history;std::array<float,28> pcm{};SourceSamplePosition position;std::size_t used{28};};
    State state_;SourceStatus status_{SourceStatus::Ok};
    void read(State& state,float* output,std::size_t count) {
        std::size_t got=0;
        while(got<count) {
            if(state.used==28) {
                std::array<std::uint8_t,16> block{};bytes_.copy(48+static_cast<std::size_t>(state.position.value/28)*16,block.data(),block.size());
                auto history=state.history;const auto pcm=decodePsAdpcm(block,history);
                state.history=history;state.pcm=pcm;state.used=0;
            }
            const auto take=std::min(count-got,28-state.used);
            if(output)std::copy_n(state.pcm.data()+state.used,take,output+got);
            state.used+=take;state.position.value+=static_cast<std::int64_t>(take);got+=take;
        }
    }
public:
    VagSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits):input_(std::move(input)),bytes_(*input_,limits),metadata_(parseVag(bytes_,limits)) {}
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    SourceSamplePosition position()const noexcept override{return state_.position;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames,0.0f);return 0;}
        const auto count=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-state_.position.value));const auto before=state_.position.value;
        try{read(state_,output,count);}catch(const SourceFailure& error){status_=error.status;}catch(...){status_=SourceStatus::MalformedSource;}
        const auto got=static_cast<std::size_t>(state_.position.value-before);std::fill_n(output+got,frames-got,0.0f);return got;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        try{State next;read(next,nullptr,static_cast<std::size_t>(target.value));state_=next;status_=SourceStatus::Ok;}
        catch(const SourceFailure& error){status_=error.status;}catch(...){status_=SourceStatus::MalformedSource;}
        return status_;
    }
    void reset()noexcept override{state_={};status_=SourceStatus::Ok;}
};
}
std::unique_ptr<AudioFrameSource> openVag(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<VagSource>(std::move(input),limits);
}
std::unique_ptr<AudioFrameSource> openVag(ByteView bytes,const DecodeLimits& limits) {
    return openVag(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits);
}
}
