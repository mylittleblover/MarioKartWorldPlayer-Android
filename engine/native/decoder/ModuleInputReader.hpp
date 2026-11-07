#pragma once
#include "decoder/EncodedSourceBytes.hpp"
#include "libopenmpt/libopenmpt.h"

namespace player2::audio {
// Control-thread library construction only. A seekable input avoids the library's
// nonseekable whole-file staging path. libopenmpt discards these callbacks after
// construction; retained source input is used only to reconstruct Replay seek.
class ModuleInputReader {
    EncodedSourceBytes bytes_;
    std::int64_t position_{};
    SourceStatus status_{SourceStatus::Ok};
    static std::size_t read(void* owner,void* output,std::size_t requested)noexcept {
        auto& self=*static_cast<ModuleInputReader*>(owner);
        if(self.status_!=SourceStatus::Ok)return 0;
        const auto count=std::min(requested,self.bytes_.size()-static_cast<std::size_t>(self.position_));
        try {
            self.bytes_.copy(static_cast<std::size_t>(self.position_),static_cast<std::uint8_t*>(output),count);
            self.position_+=static_cast<std::int64_t>(count);return count;
        }catch(const SourceFailure& failure){self.status_=failure.status;return 0;}
        catch(...){self.status_=SourceStatus::MalformedSource;return 0;}
    }
    static int seek(void* owner,std::int64_t offset,int whence)noexcept {
        auto& self=*static_cast<ModuleInputReader*>(owner);
        const auto length=static_cast<std::int64_t>(self.bytes_.size());std::int64_t base;
        switch(whence) {
            case OPENMPT_STREAM_SEEK_SET:base=0;break;
            case OPENMPT_STREAM_SEEK_CUR:base=self.position_;break;
            case OPENMPT_STREAM_SEEK_END:base=length;break;
            default:return -1;
        }
        // Check before addition, including INT64_MIN/MAX from a library caller.
        if(offset < -base || offset > length-base)return -1;
        self.position_=base+offset;return 0;
    }
    static std::int64_t tell(void* owner)noexcept{return static_cast<ModuleInputReader*>(owner)->position_;}
public:
    explicit ModuleInputReader(EncodedSourceInput& input,const DecodeLimits& limits):bytes_(input,limits){}
    static openmpt_stream_callbacks callbacks()noexcept{return {read,seek,tell};}
    SourceStatus status()const noexcept{return status_;}
};
}
