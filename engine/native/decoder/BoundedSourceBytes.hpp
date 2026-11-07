#pragma once
#include "decoder/SourceBytes.hpp"
namespace player2::audio {
// Parser-only relative view over an existing original root. No copying or
// independent input lease; every delegated read first checks the child range.
template<class Bytes> class BoundedSourceBytes {
    Bytes& root_;std::size_t start_,size_;
public:
    BoundedSourceBytes(Bytes& root,std::size_t start,std::size_t size):root_(root),start_(start),size_(size){root_.need(start,size);}
    std::size_t size()const noexcept{return size_;}
    void need(std::size_t at,std::size_t count)const{if(at>size_ || count>size_-at)fail(SourceStatus::MalformedSource);}
    bool magic(std::size_t at,const char* text){need(at,4);return root_.magic(start_+at,text);}
    std::uint8_t u8(std::size_t at){need(at,1);return root_.u8(start_+at);}
    std::uint16_t u16(std::size_t at,bool big=false){need(at,2);return root_.u16(start_+at,big);}
    std::uint32_t u32(std::size_t at,bool big=false){need(at,4);return root_.u32(start_+at,big);}
};
}
