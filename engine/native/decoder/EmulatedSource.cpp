#include "decoder/EmulatedFormat.hpp"
#include "decoder/EncodedSourceBytes.hpp"
#include "decoder/MemorySourceInput.hpp"
#include <gme.h>
#include <algorithm>
#include <cstdio>
#include <cstring>

namespace player2::audio {
namespace {
static_assert(sizeof(short)==2);
using Emulator=std::unique_ptr<Music_Emu,decltype(&gme_delete)>;
void checkEmulationError(const char* error) {
    if(error)fail(std::strcmp(error,"Out of memory")==0 ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource);
}
class EmulatedSource final:public AudioFrameSource {
    std::vector<std::uint8_t> original_;
    EmulatedFormat format_;
    std::vector<SourceSubsong> songs_;
    unsigned selected_{};
    SourceMetadata metadata_;
    std::int64_t fadeStart_{};
    struct State {
        Emulator emulator{nullptr,gme_delete};
        std::array<short,512> pcm{};
        SourceSamplePosition position;
        std::size_t used{},available{};
    };
    State state_;
    SourceStatus status_{SourceStatus::Ok};
    State create()const {
        Music_Emu* raw=nullptr;
        const auto error=gme_open_data(original_.data(),static_cast<long>(original_.size()),&raw,44100);
        Emulator result(raw,gme_delete);
        checkEmulationError(error);
        if(!result)fail(SourceStatus::ResourceLimit);
        if(gme_warning(raw))fail(SourceStatus::UnsupportedCodec);
        gme_clear_playlist(raw);
        if(gme_track_count(raw)!=static_cast<int>(songs_.size()))fail(SourceStatus::MalformedSource);
        gme_ignore_silence(raw,1); // Never skip a source's initial silence/clock.
        gme_set_autoload_playback_limit(raw,0);
        checkEmulationError(gme_start_track(raw,selected_));
        if(gme_warning(raw))fail(SourceStatus::UnsupportedCodec);
        State state;state.emulator=std::move(result);return state;
    }
    void read(State& state,float* output,std::size_t frames)const {
        std::size_t copied=0;
        while(copied<frames) {
            if(state.used==state.available) {
                const auto available=static_cast<std::size_t>(std::min<std::int64_t>(256,metadata_.duration.value-state.position.value));
                if(!available)fail(SourceStatus::MalformedSource);
                checkEmulationError(gme_play(state.emulator.get(),static_cast<int>(available*2),state.pcm.data()));
                // Some emulator failures are warning-only at the C boundary.
                // Refuse before publishing this quantum, rather than admitting
                // a successful silent EOF after an illegal CPU instruction.
                if(gme_warning(state.emulator.get()))fail(SourceStatus::UnsupportedCodec);
                state.available=available;state.used=0;
            }
            const auto count=std::min(frames-copied,state.available-state.used);
            if(output)for(std::size_t frame=0;frame<count;++frame) {
                const auto at=state.position.value+static_cast<std::int64_t>(frame);
                // Format-declared fade is a Source-clock linear envelope. Never
                // let a library's caller-block fade move its musical boundary.
                const float gain=at<fadeStart_ || fadeStart_==metadata_.duration.value ? 1.0f :
                    static_cast<float>(metadata_.duration.value-at)/static_cast<float>(metadata_.duration.value-fadeStart_);
                for(unsigned channel=0;channel<2;++channel)
                    output[(copied+frame)*2+channel]=(state.pcm[(state.used+frame)*2+channel]/32768.0f)*gain;
            }
            copied+=count;state.used+=count;state.position.value+=count;
        }
    }
public:
    EmulatedSource(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,EmulatedKind kind,const char* selected) {
        EncodedSourceBytes bytes(*input,limits);format_=parseEmulated(bytes,limits,kind);
        original_.resize(bytes.size());bytes.copy(0,original_.data(),original_.size());
        songs_.resize(format_.songs.size());
        if(!format_.directMetadata)for(unsigned n=0;n<songs_.size();++n)
            std::snprintf(songs_[n].id.data(),songs_[n].id.size(),kind==EmulatedKind::Nsfe ? "nsfe:song:%u" : "spc:state:%u",n);
        selected_=format_.initialSong;
        if(selected && format_.directMetadata)fail(SourceStatus::SubsongMissing);
        if(selected) {
            const auto found=std::find_if(songs_.begin(),songs_.end(),[&](const auto& song){return std::strcmp(selected,song.id.data())==0;});
            if(found==songs_.end())fail(SourceStatus::SubsongMissing);
            selected_=static_cast<unsigned>(found-songs_.begin());
        }
        if(format_.directMetadata) {
            metadata_=*format_.directMetadata;fadeStart_=metadata_.duration.value;
        } else {
            const auto timing=format_.songs[selected_];
            const auto frames=((timing.ticks+timing.fadeTicks)*44100+63999)/64000;
            fadeStart_=static_cast<std::int64_t>((timing.ticks*44100+63999)/64000);
            metadata_={kind==EmulatedKind::Nsfe ? "native.emulated.nsfe" : "native.emulated.spc",SourceKind::Emulated,44100,2,
                {static_cast<std::int64_t>(frames)},SeekSupport::Replay,Subsong|KnownDuration|DeterministicRender|Streaming,std::nullopt,
                SampleEncoding::Unknown,0,songs_[selected_].id.data()};
        }
        state_=create();
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const std::vector<SourceSubsong>* sourceSubsongs()const noexcept override{return format_.directMetadata ? nullptr : &songs_;}
    SourceSamplePosition position()const noexcept override{return state_.position;}
    SourceStatus renderStatus()const noexcept override{return status_;}
    std::size_t render(float* output,std::size_t frames)noexcept override {
        if(status_!=SourceStatus::Ok){std::fill_n(output,frames*2,0.0f);return 0;}
        const auto wanted=std::min(frames,static_cast<std::size_t>(metadata_.duration.value-state_.position.value));
        const auto before=state_.position;status_=SourceStatus::Ok;
        try{read(state_,output,wanted);}
        catch(const SourceFailure& error){status_=error.status;}
        catch(...){status_=SourceStatus::MalformedSource;}
        const auto count=static_cast<std::size_t>(state_.position.value-before.value);
        std::fill_n(output+count*2,(frames-count)*2,0.0f);return count;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override {
        if(target.value<0 || target.value>metadata_.duration.value)return SourceStatus::InvalidRequest;
        try {
            // Transactional replay: load canonical original state even for SPC;
            // a failed allocation/decode does not replace the executing cursor.
            auto replacement=create();
            read(replacement,nullptr,static_cast<std::size_t>(target.value));
            state_=std::move(replacement);status_=SourceStatus::Ok;
        }catch(const SourceFailure& error){status_=error.status;}
         catch(const std::bad_alloc&){status_=SourceStatus::ResourceLimit;}
         catch(...){status_=SourceStatus::MalformedSource;}
        return status_;
    }
    void reset()noexcept override{static_cast<void>(seek({0}));}
};
}
std::unique_ptr<AudioFrameSource> openEmulated(std::unique_ptr<EncodedSourceInput> input,const DecodeLimits& limits,EmulatedKind kind,const char* song) {
    if(!input)fail(SourceStatus::InvalidRequest);
    return std::make_unique<EmulatedSource>(std::move(input),limits,kind,song);
}
std::unique_ptr<AudioFrameSource> openEmulated(ByteView bytes,const DecodeLimits& limits,EmulatedKind kind,const char* song) {
    return openEmulated(std::make_unique<MemoryEncodedSourceInput>(bytes,limits),limits,kind,song);
}
}
