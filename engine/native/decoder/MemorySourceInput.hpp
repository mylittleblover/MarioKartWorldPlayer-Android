#pragma once
#include "decoder/SourceBytes.hpp"
#include <algorithm>

namespace player2::audio {
class MemoryEncodedSourceInput final:public EncodedSourceInput {
    std::vector<std::uint8_t> bytes_;
public:
    MemoryEncodedSourceInput(ByteView bytes,const DecodeLimits& limits) {
        const SourceBytes checked(bytes,limits);static_cast<void>(checked);
        bytes_.assign(bytes.data,bytes.data+bytes.size);
    }
    std::uint64_t byteLength()const noexcept override{return bytes_.size();}
    SourceReadResult read(SourceByteOffset offset,std::uint8_t* output,std::size_t count)noexcept override {
        if(offset.value>bytes_.size() || count>bytes_.size()-offset.value)return {SourceStatus::MalformedSource,0};
        std::copy_n(bytes_.data()+offset.value,count,output);return {SourceStatus::Ok,count};
    }
};
}
