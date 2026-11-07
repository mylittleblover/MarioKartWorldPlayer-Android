#pragma once
#include "decoder/EncodedSourceBytes.hpp"

namespace player2::audio {
class CodecInputCursor {
    EncodedSourceInput& input_;
    std::uint64_t position_{};
public:
    SourceStatus status{SourceStatus::Ok};
    explicit CodecInputCursor(EncodedSourceInput& input):input_(input){}
    std::int64_t position()const noexcept{return static_cast<std::int64_t>(position_);}
    std::size_t read(void* output,std::size_t count)noexcept {
        if(status!=SourceStatus::Ok)return 0;
        if(!output && count){status=SourceStatus::InvalidRequest;return 0;}
        if(position_>input_.byteLength()){status=SourceStatus::InputChanged;return 0;}
        const auto wanted=std::min<std::uint64_t>(count,input_.byteLength()-position_);std::size_t completed=0;
        while(completed<wanted) {
            const auto chunk=std::min<std::size_t>(32768,wanted-completed);
            const auto result=input_.read({position_},static_cast<std::uint8_t*>(output)+completed,chunk);
            if(result.status!=SourceStatus::Ok || result.bytes!=chunk) {status=result.status==SourceStatus::Ok ? SourceStatus::InputUnavailable : result.status;break;}
            position_+=chunk;completed+=chunk;
        }
        return completed;
    }
    bool seek(int offset,int origin)noexcept {
        const auto base=origin==0 ? 0 : origin==1 ? position() : origin==2 ? static_cast<std::int64_t>(input_.byteLength()) : -1;
        const auto next=base+static_cast<std::int64_t>(offset);
        if(base<0 || next<0 || static_cast<std::uint64_t>(next)>input_.byteLength())return false;
        position_=static_cast<std::uint64_t>(next);return true;
    }
};
}
