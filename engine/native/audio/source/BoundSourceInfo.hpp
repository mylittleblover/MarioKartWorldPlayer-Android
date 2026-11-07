#pragma once
#include "api/SourceMonitorTypes.hpp"
#include "audio/source/AudioFrameSource.hpp"
#include "api/AudioSourceApi.hpp"
namespace player2::audio {
inline void copyBoundId(std::array<char,129>& output,const char* input) {
    output.fill(0);if(!input)return;
    std::size_t n=0;
    for(;n<128 && input[n];++n) {
        const auto c=input[n];if(!((c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9') || c=='_' || c=='.' || c==':' || c=='-'))fail(SourceStatus::MalformedSource);
        output[n]=c;
    }
    if(n==0 || input[n])fail(SourceStatus::MalformedSource);
}
inline void setBoundSelection(api::BoundSourceInfo& info,const api::SourceOpenRequest& request) {
    copyBoundId(info.assetId,request.assetId);copyBoundId(info.entryId,request.entryId);copyBoundId(info.variantId,request.variantId);
    // Preserve canonical source-owned default selection; explicit IDs have already
    // been validated by the provider. No format decisions happen in Platform.
    if(info.subsongId[0]==0)copyBoundId(info.subsongId,request.subsongId);
}
inline api::BoundSourceInfo boundSourceInfo(const SourceMetadata& m) {
    api::BoundSourceInfo info;
    // Direct internal PCM buffers may have no provider facts; never invent a provider.
    if(!m.providerId)return info;
    std::size_t n=0;
    for(;n<info.provider.size();++n) {
        const auto c=m.providerId[n];if(!c)break;
        if(n==128 || !((c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9') || c=='_' || c=='.' || c==':' || c=='-'))fail(SourceStatus::MalformedSource);
        info.provider[n]=c;
    }
    if(n==0 || static_cast<int>(m.kind)<0 || static_cast<int>(m.kind)>6 || static_cast<int>(m.seek)<0 || static_cast<int>(m.seek)>2 ||
       (m.flags&~2047u) || !validSampleEncoding(m.encoding) || m.bitDepth<0 || m.bitDepth>64)fail(SourceStatus::MalformedSource);
    info.present=true;info.kind=m.kind;info.seek=m.seek;info.flags=m.flags;info.encoding=m.encoding;info.bitDepth=m.bitDepth;
    copyBoundId(info.subsongId,m.selectedSubsongId);return info;
}
}
