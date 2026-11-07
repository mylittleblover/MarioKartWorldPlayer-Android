#include "audio/source/PrepareBuffer.hpp"
#include "audio/source/BoundSourceInfo.hpp"
#include <algorithm>

namespace player2::audio {
std::unique_ptr<AudioBuffer> prepareBuffer(int identity,AudioFrameSource& source,std::size_t maximumBytes,bool requireDeterministic) {
    const auto& metadata=source.metadata();
    if(!(metadata.flags&KnownDuration) || metadata.duration.value<=0) fail(SourceStatus::UnsupportedCodec);
    if(requireDeterministic && !(metadata.flags&DeterministicRender)) fail(SourceStatus::UnsupportedCodec);
    if(metadata.channels<1 || metadata.channels>2 || metadata.sampleRate<8000 || metadata.sampleRate>192000) fail(SourceStatus::MalformedSource);
    if(metadata.sourceLoop && (metadata.sourceLoop->start.value<0 || metadata.sourceLoop->end.value<=metadata.sourceLoop->start.value || metadata.sourceLoop->end.value>metadata.duration.value))fail(SourceStatus::SourceLoopBounds);
    const auto frames=static_cast<std::uint64_t>(metadata.duration.value);
    if(frames>maximumBytes/(metadata.channels*sizeof(float))) fail(SourceStatus::ResourceLimit);
    auto buffer=std::make_unique<AudioBuffer>(); buffer->index=identity; buffer->sampleRate=metadata.sampleRate; buffer->channels=metadata.channels;buffer->sourceLoop=metadata.sourceLoop;buffer->sourceInfo=boundSourceInfo(metadata);
    buffer->samples.resize(static_cast<std::size_t>(frames)*metadata.channels);
    source.reset();
    if(source.renderStatus()!=SourceStatus::Ok)fail(source.renderStatus());
    std::size_t position=0;
    while(position<frames) {
        const auto request=std::min<std::size_t>(1024,frames-position);
        const auto rendered=source.render(buffer->samples.data()+position*metadata.channels,request);
        if(source.renderStatus()!=SourceStatus::Ok)fail(source.renderStatus());
        if(rendered==0 || rendered>request) fail(SourceStatus::MalformedSource);
        for(std::size_t n=0;n<rendered*metadata.channels;++n)if(!std::isfinite(buffer->samples[position*metadata.channels+n]))fail(SourceStatus::MalformedSource);
        position+=rendered;
    }
    return buffer;
}
}
