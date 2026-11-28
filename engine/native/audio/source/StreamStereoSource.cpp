#include "audio/source/AudioFrameSource.hpp"
#include "api/StreamTypes.hpp"
#include <algorithm>
#include <array>
#include <cmath>

namespace player2::audio {
namespace {
constexpr double kInvSqrt2=0.70710678118654752440;
constexpr std::uint8_t kRoleFront=1;
constexpr std::uint8_t kRoleCenter=2;
constexpr std::uint8_t kRoleLfe=3;
constexpr std::uint8_t kRoleRear=4;

double speakerRoleGain(std::uint8_t flags) noexcept {
    switch(flags) {
        case kRoleFront: return 1.0;
        case kRoleCenter: return kInvSqrt2;
        case kRoleLfe: return 0.0;
        case kRoleRear: return kInvSqrt2;
        default: return 1.0;
    }
}

std::array<double,2> groupStereoGains(const SourceStreamGroup& group) noexcept {
    const double gain=(group.volume/127.0)*speakerRoleGain(group.flags);
    const unsigned pan=std::min<unsigned>(group.pan,127u);
    const double left=gain*(pan>64u ? (127.0-pan)/63.0 : 1.0);
    const double right=gain*(pan<64u ? pan/64.0 : 1.0);
    return {left,right};
}

class StreamStereoSource final:public AudioFrameSource {
    static constexpr double kSoftKnee=0.88;
    static constexpr double kSoftHeadroom=0.115; // Asymptotic ceiling 0.995 (< 1.0 full scale).
    std::unique_ptr<AudioFrameSource> raw_;SourceMetadata metadata_;SourceStreamInfo info_;
    std::uint32_t mute_{},solo_{};double scale_{1};bool hasSpeakerRoles_{false};
    // Source decoder and projection run on the same control/worker. Callback
    // consumes existing stereo pages; raw lanes never enlarge the page cache.
    std::array<float,64*32> scratch_{};
public:
    explicit StreamStereoSource(std::unique_ptr<AudioFrameSource> raw):raw_(std::move(raw)),metadata_(raw_->metadata()),info_(*raw_->streamInspection()) {
        if(info_.originalChannels<3 || info_.originalChannels>32 || info_.originalChannels!=static_cast<unsigned>(metadata_.channels) || info_.groups.empty() || info_.groups.size()>32)fail(SourceStatus::MalformedSource);
        std::array<double,2> routeWeight{};
        for(const auto& group:info_.groups) {
            if(group.channels.empty())fail(SourceStatus::MalformedSource);
            if(group.flags==kRoleFront || group.flags==kRoleCenter || group.flags==kRoleLfe || group.flags==kRoleRear)hasSpeakerRoles_=true;
            const auto gains=groupStereoGains(group);
            for(std::size_t n=0;n<group.channels.size();++n) {
                if(group.channels[n]>=info_.originalChannels)fail(SourceStatus::MalformedSource);
                if(group.channels.size()==1){routeWeight[0]+=gains[0];routeWeight[1]+=gains[1];}
                else routeWeight[n%2]+=gains[n%2];
            }
        }
        // Surround speaker-role streams (BWAV 4.0/5.0/5.1) use standard ITU-R BS.775-1
        // downmix weights (L/R=1, C=1/sqrt(2), LFE=0, Ls/Rs=1/sqrt(2)) at unity front-channel
        // scale (matching v0.1.0-beta01 loudness without conservative L1 attenuation or extra
        // +6 dB boost), paired with a deterministic stereo-linked C2 soft-knee peak limiter
        // above 0.88 (ceiling < 0.995) to prevent clipping on hot peaks.
        // Legacy multi-track BFSTM/BRSTM stem streams (flags=0) keep fixed L1 stem headroom.
        if(hasSpeakerRoles_) {
            scale_=1.0;
        } else {
            scale_=1.0/std::max({1.0,routeWeight[0],routeWeight[1]});
        }
        info_.stereoScale=scale_;metadata_.channels=2;
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
                    const auto& group=info_.groups[n];
                    const auto gains=groupStereoGains(group);
                    if(gains[0]==0.0 && gains[1]==0.0)continue;
                    for(std::size_t ch=0;ch<group.channels.size();++ch) {
                        const auto sample=scratch_[frame*info_.originalChannels+group.channels[ch]];
                        if(group.channels.size()==1){mixed[0]+=sample*gains[0];mixed[1]+=sample*gains[1];}
                        else mixed[ch%2]+=sample*gains[ch%2];
                    }
                }
                double left=mixed[0]*scale_;
                double right=mixed[1]*scale_;
                if(hasSpeakerRoles_) {
                    const double peak=std::max(std::abs(left),std::abs(right));
                    if(peak>kSoftKnee) {
                        const double limited=kSoftKnee+kSoftHeadroom*std::tanh((peak-kSoftKnee)/kSoftHeadroom);
                        const double ratio=limited/peak;
                        left*=ratio;
                        right*=ratio;
                    }
                }
                output[(done+frame)*2]=static_cast<float>(left);
                output[(done+frame)*2+1]=static_cast<float>(right);
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
