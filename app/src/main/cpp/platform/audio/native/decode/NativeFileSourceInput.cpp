#include "NativeFileSourceInput.hpp"
#include <cerrno>
#include <fcntl.h>
#include <limits>
#include <sys/stat.h>
#include <unistd.h>

namespace player2::platform {
namespace {
using audio::SourceStatus;
bool sameRevision(const struct stat& a,const struct stat& b) noexcept {
    return a.st_dev==b.st_dev && a.st_ino==b.st_ino && a.st_size==b.st_size &&
        a.st_mtim.tv_sec==b.st_mtim.tv_sec && a.st_mtim.tv_nsec==b.st_mtim.tv_nsec &&
        a.st_ctim.tv_sec==b.st_ctim.tv_sec && a.st_ctim.tv_nsec==b.st_ctim.tv_nsec;
}
class FileSourceInput final:public audio::EncodedSourceInput {
    int descriptor_;struct stat revision_;
public:
    FileSourceInput(int descriptor,const struct stat& revision):descriptor_(descriptor),revision_(revision) {}
    ~FileSourceInput()override {::close(descriptor_);}
    std::uint64_t byteLength()const noexcept override {return static_cast<std::uint64_t>(revision_.st_size);}
    audio::SourceReadResult read(audio::SourceByteOffset offset,std::uint8_t* destination,std::size_t count)noexcept override {
        if((!destination && count) || count>65536 || offset.value>byteLength() || count>byteLength()-offset.value)
            return {SourceStatus::InvalidRequest,0};
        struct stat current{};
        if(::fstat(descriptor_,&current)!=0)return {SourceStatus::InputUnavailable,0};
        if(!sameRevision(revision_,current))return {SourceStatus::InputChanged,0};
        std::size_t done=0;unsigned interrupts=0;
        while(done<count) {
            const auto received=::pread(descriptor_,destination+done,count-done,static_cast<off_t>(offset.value+done));
            if(received<0 && errno==EINTR && ++interrupts<=16)continue;
            if(received<=0)return {SourceStatus::InputUnavailable,0};
            done+=static_cast<std::size_t>(received);
        }
        if(::fstat(descriptor_,&current)!=0)return {SourceStatus::InputUnavailable,0};
        if(!sameRevision(revision_,current))return {SourceStatus::InputChanged,0};
        return {SourceStatus::Ok,done};
    }
};
}
FileSourceInputResult openFileSourceInput(int borrowedDescriptor) noexcept {
    if(borrowedDescriptor<0)return {};
    const auto descriptor=::fcntl(borrowedDescriptor,F_DUPFD_CLOEXEC,0);
    if(descriptor<0)return {};
    struct stat revision{};
    if(::fstat(descriptor,&revision)!=0 || !S_ISREG(revision.st_mode) || revision.st_size<0) {
        ::close(descriptor);return {};
    }
    // All offsets passed to pread must be representable on every Android ABI.
    if(static_cast<std::uint64_t>(revision.st_size)>static_cast<std::uint64_t>(std::numeric_limits<off_t>::max())) {
        ::close(descriptor);return {SourceStatus::ResourceLimit,{}};
    }
    try {return {SourceStatus::Ok,std::make_unique<FileSourceInput>(descriptor,revision)};}
    catch(...) {::close(descriptor);return {SourceStatus::ResourceLimit,{}};}
}
}
