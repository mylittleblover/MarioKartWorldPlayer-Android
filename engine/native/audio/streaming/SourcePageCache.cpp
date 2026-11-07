#include "audio/streaming/SourcePageCache.hpp"
#include "api/StreamTypes.hpp"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
namespace player2::audio {
void SourcePageCache::increment(std::uint64_t& value) noexcept {if(value<static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max()))++value;}
void SourcePageCache::increment(std::atomic<std::uint32_t>& value) noexcept {auto current=value.load(std::memory_order_relaxed);
    while(current<std::numeric_limits<std::uint32_t>::max() && !value.compare_exchange_weak(current,current+1,std::memory_order_relaxed)){}}
SourcePageCache::SourcePageCache(std::size_t maximumPcmBytes,std::size_t maximumEncodedBytes)
    :pageCount_(std::min(MaximumPages,maximumPcmBytes/PagePcmBytes)),maximumEncodedBytes_(maximumEncodedBytes) {
    if(pageCount_<2)fail(SourceStatus::ResourceLimit);
    pages_=std::make_unique<Page[]>(pageCount_);
    static_assert(std::atomic<std::uint32_t>::is_always_lock_free,"Page leases must be lock-free");
    worker_=std::thread([this] {work();});
}
SourcePageCache::~SourcePageCache() {stopping_.store(true,std::memory_order_release);if(worker_.joinable())worker_.join();}
void SourcePageCache::pauseWorker() noexcept {
    paused_.store(true,std::memory_order_release);
    while(!idle_.load(std::memory_order_acquire))std::this_thread::sleep_for(std::chrono::milliseconds(1));
}
void SourcePageCache::resumeWorker() noexcept {paused_.store(false,std::memory_order_release);}
SourceStatus SourcePageCache::bind(int asset,std::unique_ptr<AudioFrameSource> source,std::size_t encodedBytes,const SourceSamplePosition* warm,std::size_t count) noexcept {
    if(asset<0 || asset>=4096 || !source || count>MaximumPages || (count && !warm))return SourceStatus::InvalidRequest;
    const auto& m=source->metadata();
    // Seeking and decode happen on control/worker only. Deterministic replay
    // sources can fill the same bounded pages; they are not realtime decoders.
    if((m.seek!=SeekSupport::Fast && m.seek!=SeekSupport::Replay) || !(m.flags&DeterministicRender) || !(m.flags&KnownDuration))return SourceStatus::UnsupportedCodec;
    if(m.channels<1 || m.channels>2 || m.sampleRate<8000 || m.sampleRate>192000 || m.duration.value<=0)return SourceStatus::MalformedSource;
    if(m.sourceLoop && (m.sourceLoop->start.value<0 || m.sourceLoop->end.value<=m.sourceLoop->start.value || m.sourceLoop->end.value>m.duration.value))return SourceStatus::SourceLoopBounds;
    if(encodedBytes>maximumEncodedBytes_-(encodedBytes_-sources_[asset].encodedBytes) || (!sources_[asset].frames && sourceCount_==MaximumSources))return SourceStatus::ResourceLimit;
    for(std::size_t n=0;n<count;++n)if(warm[n].value<0 || warm[n].value>=m.duration.value)return SourceStatus::InvalidRequest;
    // Preflight every distinct warm page before replacing a working binding.
    // Temporary PCM is bounded by the cache budget and exists only on control.
    std::array<Key,MaximumPages> keys{};std::size_t keyCount=0;
    const auto append=[&](std::int64_t page) {
        if(page*static_cast<std::int64_t>(FramesPerPage)>=m.duration.value)return true;
        const Key key{asset,page};for(std::size_t i=0;i<keyCount;++i)if(keys[i]==key)return true;
        if(keyCount==pageCount_)return false;
        keys[keyCount++]=key;return true;
    };
    if(count==0){if(!append(0) || !append(1))return SourceStatus::ResourceLimit;}
    for(std::size_t n=0;n<count;++n) {
        const auto page=warm[n].value/static_cast<std::int64_t>(FramesPerPage);
        if(!append(page) || !append(page+1))return SourceStatus::ResourceLimit;
    }
    std::unique_ptr<Page[]> staged;
    try{staged=std::make_unique<Page[]>(keyCount);}catch(...){return SourceStatus::ResourceLimit;}
    for(std::size_t n=0;n<keyCount;++n) {
        const SourceSamplePosition position{keys[n].page*static_cast<std::int64_t>(FramesPerPage)};
        auto status=source->seek(position);
        const auto expected=std::min<std::size_t>(FramesPerPage,static_cast<std::size_t>(m.duration.value-position.value));
        if(status==SourceStatus::Ok) {
            const auto rendered=source->render(staged[n].pcm.data(),expected);
            status=source->renderStatus();
            if(status==SourceStatus::Ok && rendered!=expected)status=SourceStatus::MalformedSource;
            for(std::size_t i=0;i<expected*static_cast<std::size_t>(m.channels) && status==SourceStatus::Ok;++i)
                if(!std::isfinite(staged[n].pcm[i]))status=SourceStatus::MalformedSource;
        }
        if(status!=SourceStatus::Ok){increment(failures_);lastFailure_.store(static_cast<int>(status),std::memory_order_relaxed);return status;}
        staged[n].key=keys[n];staged[n].channels=m.channels;
    }
    pauseWorker();
    for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].users.load()!=0){resumeWorker();return SourceStatus::InvalidRequest;}
    // Audio is stopped and the worker acknowledged pause: control owns all pages.
    for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].key.asset==asset) {pages_[n].key={};cached_.fetch_sub(1,std::memory_order_relaxed);}
    requests_.clearStopped();completed_.clearStopped();pendingCount_=0;
    encodedBytes_=encodedBytes_-sources_[asset].encodedBytes+encodedBytes;
    if(!sources_[asset].frames)++sourceCount_;
    sources_[asset]={std::move(source),encodedBytes};
    for(std::size_t n=0;n<keyCount;++n) {
        auto& page=pages_[cursor_];cursor_=(cursor_+1)%pageCount_;
        if(page.key.asset>=0)cached_.fetch_sub(1,std::memory_order_relaxed);
        page.pcm=staged[n].pcm;page.channels=staged[n].channels;page.key=staged[n].key;
        increment(decoded_);cached_.fetch_add(1,std::memory_order_relaxed);
    }
    resumeWorker();return SourceStatus::Ok;
}
unsigned SourcePageCache::moduleChannelCount(int asset)const noexcept {
    return asset>=0 && asset<4096 && sources_[asset].frames ? sources_[asset].frames->moduleChannelCount() : 0;
}
unsigned SourcePageCache::streamGroupCount(int asset)const noexcept {
    if(asset<0 || asset>=4096 || !sources_[asset].frames)return 0;
    const auto* info=sources_[asset].frames->streamInspection();return info ? static_cast<unsigned>(info->groups.size()) : 0;
}
SourceStatus SourcePageCache::setModuleChannels(int asset,std::uint64_t mute,std::uint64_t solo)noexcept {return setSourceControls(asset,mute,solo,false);}
SourceStatus SourcePageCache::setStreamGroups(int asset,std::uint32_t mute,std::uint32_t solo)noexcept {return setSourceControls(asset,mute,solo,true);}
SourceStatus SourcePageCache::setSourceControls(int asset,std::uint64_t mute,std::uint64_t solo,bool groups)noexcept {
    const auto count=groups ? streamGroupCount(asset) : moduleChannelCount(asset);
    if(count==0)return SourceStatus::UnsupportedFormat;
    if(count<64 && ((mute|solo)>>count))return SourceStatus::InvalidRequest;
    pauseWorker();
    for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].users.load()!=0){resumeWorker();return SourceStatus::InvalidRequest;}
    const auto status=groups ? sources_[asset].frames->setStreamGroups(static_cast<std::uint32_t>(mute),static_cast<std::uint32_t>(solo)) : sources_[asset].frames->setModuleChannels(mute,solo);
    if(status==SourceStatus::Ok) {
        for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].key.asset==asset){pages_[n].key={};cached_.fetch_sub(1,std::memory_order_relaxed);}
        requests_.clearStopped();completed_.clearStopped();pendingCount_=0;
    }
    resumeWorker();return status;
}
void SourcePageCache::unbind(int asset) noexcept {
    if(asset<0 || asset>=4096 || !sources_[asset].frames)return;
    pauseWorker();
    for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].users.load()!=0){resumeWorker();return;}
    for(std::size_t n=0;n<pageCount_;++n)if(pages_[n].key.asset==asset){pages_[n].key={};cached_.fetch_sub(1,std::memory_order_relaxed);}
    requests_.clearStopped();completed_.clearStopped();pendingCount_=0;
    encodedBytes_-=sources_[asset].encodedBytes;--sourceCount_;sources_[asset]={};resumeWorker();
}
SourceStatus SourcePageCache::prepare(int asset,SourceSamplePosition position) noexcept {
    if(asset<0 || asset>=4096 || !sources_[asset].frames || position.value<0 || position.value>=duration(asset).value)return SourceStatus::InvalidRequest;
    if(!paused_.load(std::memory_order_acquire) || !idle_.load(std::memory_order_acquire))return SourceStatus::InvalidRequest;
    return load({asset,position.value/static_cast<std::int64_t>(FramesPerPage)});
}
SourceStatus SourcePageCache::prepareLinearRepeat(int asset,std::optional<SourceLoopMetadata> range)noexcept {
    pauseWorker();const auto previousAsset=repeatAsset_;const auto previousFirst=repeatFirst_,previousLast=repeatLast_,previousEnd=repeatEndPage_;
    repeatAsset_=-1;repeatFirst_=repeatLast_=repeatEndPage_=-1;
    if(range) {
        if(asset<0 || asset>=4096 || !sources_[asset].frames || range->start.value<0 || range->end.value<=range->start.value || range->end.value>duration(asset).value) {
            repeatAsset_=previousAsset;repeatFirst_=previousFirst;repeatLast_=previousLast;repeatEndPage_=previousEnd;resumeWorker();return SourceStatus::InvalidRequest;
        }
        const auto first=range->start.value/static_cast<std::int64_t>(FramesPerPage);
        const auto last=std::min((range->end.value-1)/static_cast<std::int64_t>(FramesPerPage),first+static_cast<std::int64_t>(std::max<std::size_t>(1,std::min<std::size_t>(32,pageCount_/4)))-1);
        for(auto page=first;page<=last;++page) {
            const auto status=load({asset,page});
            if(status!=SourceStatus::Ok) {repeatAsset_=previousAsset;repeatFirst_=previousFirst;repeatLast_=previousLast;repeatEndPage_=previousEnd;resumeWorker();return status;}
            repeatAsset_=asset;repeatFirst_=first;repeatLast_=page;repeatEndPage_=(range->end.value-1)/static_cast<std::int64_t>(FramesPerPage);
        }
    }
    resumeWorker();return SourceStatus::Ok;
}
int SourcePageCache::acquire(int asset,std::int64_t page) noexcept {
    const Key key{asset,page};
    for(std::size_t n=0;n<pageCount_;++n) {
        // Reader reservations never fail because another reader briefly changes
        // the count. A writing reservation is released without reading any payload.
        const auto users=pages_[n].users.fetch_add(1,std::memory_order_acquire);
        if(users&Writing){release(static_cast<int>(n));continue;}
        if(pages_[n].key==key)return static_cast<int>(n);
        release(static_cast<int>(n));
    }
    return -1;
}
void SourcePageCache::release(int slot) noexcept {if(slot>=0 && static_cast<std::size_t>(slot)<pageCount_)pages_[slot].users.fetch_sub(1,std::memory_order_release);}
float SourcePageCache::read(int slot,SourceSamplePosition position,int channel) const noexcept {
    const auto& page=pages_[slot];const auto offset=static_cast<std::size_t>(position.value%FramesPerPage);
    return page.pcm[offset*page.channels+(page.channels==1 ? 0 : channel)];
}
SourceSamplePosition SourcePageCache::duration(int asset) const noexcept {return asset>=0 && asset<4096 && sources_[asset].frames ? sources_[asset].frames->metadata().duration : SourceSamplePosition{};}
void SourcePageCache::collectCompleted() noexcept {
    Key key;for(std::size_t n=0;n<128 && completed_.pop(key);++n) {
        for(std::size_t i=0;i<pendingCount_;++i)if(pending_[i]==key){pending_[i]=pending_[--pendingCount_];break;}
    }
}
bool SourcePageCache::prefetchLinearRepeat(int asset,std::int64_t page)noexcept {
    if(asset!=repeatAsset_ || page<repeatFirst_ || page>repeatLast_ || repeatLast_==repeatEndPage_)return true;
    const auto first=request(asset,repeatLast_+1);
    const auto second=repeatLast_+2>repeatEndPage_ || request(asset,repeatLast_+2);
    return first && second;
}
bool SourcePageCache::request(int asset,std::int64_t page) noexcept {
    collectCompleted();
    if(asset<0 || asset>=4096 || !sources_[asset].frames || page<0 || page>=(duration(asset).value-1)/static_cast<std::int64_t>(FramesPerPage)+1)return false;
    const auto slot=acquire(asset,page);if(slot>=0){release(slot);return true;}
    const Key key{asset,page};for(std::size_t n=0;n<pendingCount_;++n)if(pending_[n]==key)return true;
    if(pendingCount_==pending_.size() || !requests_.push(key)){increment(dropped_);return false;}
    pending_[pendingCount_++]=key;increment(requestCount_);return true;
}
SourceStatus SourcePageCache::load(Key key) noexcept {
    if(key.asset<0 || key.asset>=4096 || !sources_[key.asset].frames || key.page<0 || key.page>(duration(key.asset).value-1)/static_cast<std::int64_t>(FramesPerPage))return SourceStatus::InvalidRequest;
    const auto existing=acquire(key.asset,key.page);if(existing>=0){release(existing);return SourceStatus::Ok;}
    for(std::size_t n=0;n<pageCount_;++n) {
        const auto slot=cursor_;cursor_=(cursor_+1)%pageCount_;auto& page=pages_[slot];
        if(repeatAsset_>=0 && page.key.asset==repeatAsset_ && page.key.page>=repeatFirst_ && page.key.page<=repeatLast_)continue;
        std::uint32_t idle=0;
        if(!page.users.compare_exchange_strong(idle,Writing,std::memory_order_acquire,std::memory_order_relaxed))continue;
        if(page.key.asset>=0)cached_.fetch_sub(1,std::memory_order_relaxed);
        page.key={};auto& source=*sources_[key.asset].frames;
        const SourceSamplePosition position{key.page*static_cast<std::int64_t>(FramesPerPage)};
        auto status=source.seek(position);
        const auto expected=std::min<std::size_t>(FramesPerPage,static_cast<std::size_t>(source.metadata().duration.value-position.value));
        if(status==SourceStatus::Ok) {
            const auto rendered=source.render(page.pcm.data(),expected);
            status=source.renderStatus();
            if(status==SourceStatus::Ok && rendered!=expected)status=SourceStatus::MalformedSource;
            for(std::size_t i=0;i<expected*static_cast<std::size_t>(source.metadata().channels) && status==SourceStatus::Ok;++i)if(!std::isfinite(page.pcm[i]))status=SourceStatus::MalformedSource;
        }
        if(status==SourceStatus::Ok) {
            page.key=key;page.channels=source.metadata().channels;increment(decoded_);cached_.fetch_add(1,std::memory_order_relaxed);
        } else {increment(failures_);lastFailure_.store(static_cast<int>(status),std::memory_order_relaxed);}
        // Readers may briefly reserve while Writing is set. Subtract only the
        // writer bit, retaining those readers' reservations until they release.
        page.users.fetch_sub(Writing,std::memory_order_release);return status;
    }
    increment(failures_);lastFailure_.store(static_cast<int>(SourceStatus::ResourceLimit),std::memory_order_relaxed);return SourceStatus::ResourceLimit;
}
void SourcePageCache::work() noexcept {
    while(!stopping_.load(std::memory_order_acquire)) {
        if(paused_.load(std::memory_order_acquire)) {idle_.store(true,std::memory_order_release);std::this_thread::sleep_for(std::chrono::milliseconds(1));continue;}
        idle_.store(false,std::memory_order_release);
        if(paused_.load(std::memory_order_acquire))continue;
        Key key;
        if(requests_.pop(key)){
            load(key);
            // At most 128 outstanding requests; the acknowledgment ring holds all 128.
            if(!completed_.push(key)){increment(failures_);lastFailure_.store(static_cast<int>(SourceStatus::ResourceLimit),std::memory_order_relaxed);}
        }
        else std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    idle_.store(true,std::memory_order_release);
}
StreamCacheSnapshot SourcePageCache::snapshot() const noexcept {
    return {pageCount_*PagePcmBytes,encodedBytes_,sourceCount_,cached_.load(std::memory_order_relaxed),underruns_,requestCount_,dropped_,decoded_.load(std::memory_order_relaxed),failures_.load(std::memory_order_relaxed),static_cast<SourceStatus>(lastFailure_.load(std::memory_order_relaxed))};
}
StreamCursor& StreamCursor::operator=(const StreamCursor& other) noexcept {if(this!=&other){reset();cache_=other.cache_;asset_=other.asset_;}return *this;}
StreamCursor& StreamCursor::operator=(StreamCursor&& other) noexcept {
    if(this!=&other) {
        reset();cache_=other.cache_;asset_=other.asset_;slots_=other.slots_;pages_=other.pages_;prefetched_=other.prefetched_;missing_=other.missing_;
        other.cache_=nullptr;other.asset_=-1;other.slots_={-1,-1};other.pages_={-1,-1};other.prefetched_=other.missing_=false;
    }
    return *this;
}
void StreamCursor::bind(SourcePageCache* cache,int asset) noexcept {if(cache_!=cache || asset_!=asset){reset();cache_=cache;asset_=asset;}}
void StreamCursor::reset() noexcept {if(cache_)for(const auto slot:slots_)cache_->release(slot);slots_={-1,-1};pages_={-1,-1};cache_=nullptr;asset_=-1;prefetched_=missing_=false;}
bool StreamCursor::prepare(double position,std::optional<SourceSamplePosition> nextSample) noexcept {
    if(!cache_ || !std::isfinite(position) || position<0 || position>=cache_->duration(asset_).value)return false;
    const auto frame=static_cast<std::int64_t>(position),next=position==static_cast<double>(frame) ? frame : nextSample ? nextSample->value : std::min(frame+1,cache_->duration(asset_).value-1);
    if(next<0 || next>=cache_->duration(asset_).value)return false;
    const auto page=frame/static_cast<std::int64_t>(SourcePageCache::FramesPerPage),nextPage=next/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
    if(pages_[0]!=page){cache_->release(slots_[0]);slots_[0]=-1;pages_[0]=page;prefetched_=false;
        if(pages_[1]==page){slots_[0]=slots_[1];slots_[1]=-1;pages_[1]=-1;}}
    if(slots_[0]<0)slots_[0]=cache_->acquire(asset_,page);
    if(nextPage!=page && pages_[1]!=nextPage){cache_->release(slots_[1]);pages_[1]=nextPage;slots_[1]=-1;}
    if(nextPage==page && slots_[1]>=0){cache_->release(slots_[1]);slots_[1]=-1;pages_[1]=-1;}
    if(cache_->controlRendering()) {
        if(slots_[0]<0 && cache_->prepare(asset_,{frame})==SourceStatus::Ok)slots_[0]=cache_->acquire(asset_,page);
        if(nextPage!=page && slots_[1]<0 && cache_->prepare(asset_,{next})==SourceStatus::Ok){
            cache_->release(slots_[1]);pages_[1]=nextPage;slots_[1]=cache_->acquire(asset_,nextPage);
        }
    }
    if(!cache_->controlRendering() && !prefetched_){const auto duration=cache_->duration(asset_).value;
        const auto first=(page+1)*static_cast<std::int64_t>(SourcePageCache::FramesPerPage)>=duration || cache_->request(asset_,page+1);
        const auto second=(page+2)*static_cast<std::int64_t>(SourcePageCache::FramesPerPage)>=duration || cache_->request(asset_,page+2);
        prefetched_=cache_->prefetchLinearRepeat(asset_,page) && first && second;}

    if(nextPage!=page && slots_[1]<0)slots_[1]=cache_->acquire(asset_,nextPage);
    if(slots_[0]<0 || (nextPage!=page && slots_[1]<0)) {
        cache_->request(asset_,page);if(nextPage!=page)cache_->request(asset_,nextPage);return false;
    }
    return true;
}
std::array<float,2> StreamCursor::sample(double position,std::optional<SourceSamplePosition> nextSample) noexcept {
    missing_=false;
    if(!cache_ || !std::isfinite(position) || position<0 || position>=cache_->duration(asset_).value)return {};
    if(!prepare(position,nextSample)) {cache_->underrun();missing_=true;return {};}
    const auto frame=static_cast<std::int64_t>(position),next=position==static_cast<double>(frame) ? frame : nextSample ? nextSample->value : std::min(frame+1,cache_->duration(asset_).value-1);
    const auto page=frame/static_cast<std::int64_t>(SourcePageCache::FramesPerPage),nextPage=next/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
    const double fraction=position-frame;std::array<float,2> result;
    for(int channel=0;channel<2;++channel)result[channel]=static_cast<float>(cache_->read(slots_[0],{frame},channel)*(1-fraction)+cache_->read(nextPage==page ? slots_[0] : slots_[1],{next},channel)*fraction);
    return result;
}
}
