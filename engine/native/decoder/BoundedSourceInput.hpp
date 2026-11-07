#pragma once
#include "audio/source/AudioFrameSource.hpp"
#include <memory>

namespace player2::audio {
// Owns the original root lease while exposing only one validated container span.
class BoundedSourceInput final:public EncodedSourceInput {
    std::unique_ptr<EncodedSourceInput> root_;
    std::size_t start_,size_;
public:
    BoundedSourceInput(std::unique_ptr<EncodedSourceInput> root,std::size_t start,std::size_t size):
        root_(std::move(root)),start_(start),size_(size){
        if(!root_ || start_>root_->byteLength() || size_>root_->byteLength()-start_)
            throw SourceFailure{SourceStatus::MalformedSource};
    }
    std::uint64_t byteLength()const noexcept override{return size_;}
    SourceReadResult read(SourceByteOffset offset,std::uint8_t* output,std::size_t count)noexcept override {
        if(offset.value>size_ || count>size_-offset.value)return {SourceStatus::MalformedSource,0};
        return root_->read({start_+offset.value},output,count);
    }
};
}
