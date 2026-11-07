#pragma once
#include "decoder/SourceBytes.hpp"

namespace player2::audio {
// Non-owning parser view. Container origins remain inside the root; no copy or
// detached child asset identity. Runtime ownership uses BoundedSourceInput.
template<class Bytes> class SourceWindowBytes {
    Bytes& root_;
    std::size_t start_,size_;
public:
    SourceWindowBytes(Bytes& root,std::size_t start,std::size_t size):root_(root),start_(start),size_(size){root.need(start,size);}
    std::size_t size()const noexcept{return size_;}
    void need(std::size_t offset,std::size_t count)const {
        if(offset>size_ || count>size_-offset)fail(SourceStatus::MalformedSource);
    }
    std::uint8_t u8(std::size_t offset){need(offset,1);return root_.u8(start_+offset);}
    std::uint16_t u16(std::size_t offset,bool big=false){need(offset,2);return root_.u16(start_+offset,big);}
    std::uint32_t u32(std::size_t offset,bool big=false){need(offset,4);return root_.u32(start_+offset,big);}
    bool magic(std::size_t offset,const char* value){need(offset,4);return root_.magic(start_+offset,value);}
};
}
