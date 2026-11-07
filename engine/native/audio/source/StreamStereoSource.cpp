#include "audio/source/AudioFrameSource.hpp"
#include "api/StreamTypes.hpp"
#include <algorithm>
#include <array>
#include <cmath>

namespace player2::audio {
namespace {
class StreamStereoSource final:public AudioFrameSource {
    std::unique_ptr<AudioFrameSource> raw_;SourceMetadata metadata_;SourceStreamInfo info_;
    std::uint32_t mute_{},solo_{};double scale_{1};
    // Source decoder and projection run on the same control/worker. Callback
    // consumes existing stereo pages; raw lanes never enlarge the page cache.
    std::array<float,64*32> scratch_{};
public:
    explicit StreamStereoSource(std::unique_ptr<AudioFrameSource> raw):raw_(std::move(raw)),metadata_(raw_->metadata()),info_(*raw_->streamInspection()) {
        if(info_.originalChannels<3 || info_.originalChannels>32 || info_.originalChannels!=static_cast<unsigned>(metadata_.channels) || info_.groups.empty() || info_.groups.size()>32)fail(SourceStatus::MalformedSource);
        std::array<unsigned,2> routes{};
        for(const auto& group:info_.groups) {
            if(group.channels.empty())fail(SourceStatus::MalformedSource);
            for(std::size_t n=0;n<group.channels.size();++n) {
                if(group.channels[n]>=info_.originalChannels)fail(SourceStatus::MalformedSource);
                if(group.channels.size()==1){++routes[0];++routes[1];}else ++routes[n%2];
            }
        }
        // Fixed headroom for every declared route. Mute/solo does not change
        // other lanes' gain or create pumping. Mono groups feed both outputs.
        scale_=1.0/std::max({1u,routes[0],routes[1]});info_.stereoScale=scale_;metadata_.channels=2;
        metadata_.flags=(metadata_.flags|ChannelControl)&~RealtimeRender;
    }
    const SourceMetadata& metadata()const noexcept override{return metadata_;}
    const SourceStreamInfo* streamInspection()const noexcept override{return &info_;}
    SourceSamplePosition position()const noexcept override{return raw_->position();}
    SourceStatus renderStatus()const noexcept override{return raw_->renderStatus();}
    SourceStatus setStreamGroups(std::uint32_t mute,std::uint32_t solo)noexcept override {
        if(info_.groups.size()<32 && ((mute|solo)>>info_.groups.size()))return SourceStatus::InvalidRequest;
        mute_=mute;solo_=solo;info_.mutedGroups=mute;info_.soloGroups=solo;return SourceStatus::Ok;
    }
    std::size_t render(float* output,std::size_t frames)noexcept override {
        std::size_t done=0;
        while(done<frames) {
            const auto count=std::min<std::size_t>(64,frames-done),got=raw_->render(scratch_.data(),count);
            for(std::size_t frame=0;frame<got;++frame) {
                std::array<double,2> mixed{};
                for(std::size_t n=0;n<info_.groups.size();++n) {
                    if((mute_&(std::uint32_t{1}<<n)) || (solo_ && !(solo_&(std::uint32_t{1}<<n))))continue;
                    const auto& group=info_.groups[n];const double volume=group.volume/127.0;
                    const double left=volume*(group.pan>64 ? (127-group.pan)/63.0 : 1.0);
                    const double right=volume*(group.pan<64 ? group.pan/64.0 : 1.0);
                    for(std::size_t ch=0;ch<group.channels.size();++ch) {
                        const auto sample=scratch_[frame*info_.originalChannels+group.channels[ch]];
                        if(group.channels.size()==1){mixed[0]+=sample*left;mixed[1]+=sample*right;}
                        else mixed[ch%2]+=sample*(ch%2 ? right : left);
                    }
                }
                output[(done+frame)*2]=static_cast<float>(mixed[0]*scale_);
                output[(done+frame)*2+1]=static_cast<float>(mixed[1]*scale_);
            }
            done+=got;if(got<count || raw_->renderStatus()!=SourceStatus::Ok)break;
        }
        std::fill_n(output+done*2,(frames-done)*2,0.0f);return done;
    }
    SourceStatus seek(SourceSamplePosition target)noexcept override{return raw_->seek(target);}
    void reset()noexcept override{raw_->reset();}
};
}
std::unique_ptr<AudioFrameSource> projectStreamStereo(std::unique_ptr<AudioFrameSource> source) {
    if(!source)fail(SourceStatus::InvalidRequest);
    if(source->metadata().channels<=2)return source;
    if(!source->streamInspection())fail(SourceStatus::UnsupportedChannels);
    return std::make_unique<StreamStereoSource>(std::move(source));
}
}
