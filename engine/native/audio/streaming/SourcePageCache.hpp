#pragma once
#include "audio/source/AudioFrameSource.hpp"
#include "scheduler/CommandQueue.hpp"
#include <array>
#include <atomic>
#include <thread>
#include <utility>
namespace player2::audio {
struct StreamCacheSnapshot {
    std::size_t pcmBytes{},encodedBytes{},sources{},cachedPages{};
    std::uint64_t underrunFrames{},requests{},droppedRequests{};
    std::uint32_t decodedPages{},decodeFailures{};SourceStatus lastFailure{SourceStatus::Ok};
};
// One Engine decode worker. Audio consumes leased, immutable PCM pages only.
// Binding/pause/destruction require stopped audio and all cursors released.
class SourcePageCache {
public:
    static constexpr std::size_t FramesPerPage=4096,MaximumPages=512,MaximumSources=64;
    static constexpr std::size_t PagePcmBytes=FramesPerPage*2*sizeof(float);
private:
    static constexpr std::uint32_t Writing=std::uint32_t{1}<<31;
    static constexpr std::size_t HintBuckets=1024;
    struct Key { int asset{-1};std::int64_t page{-1};bool operator==(const Key& other) const noexcept {return asset==other.asset && page==other.page;} };
    struct Page {
        std::atomic<std::uint32_t> users{0};Key key;int channels{};
        std::array<float,FramesPerPage*2> pcm{};
    };
    struct Source {std::unique_ptr<AudioFrameSource> frames;std::size_t encodedBytes{};};
    std::array<Source,4096> sources_;
    std::unique_ptr<Page[]> pages_;std::size_t pageCount_,cursor_{},encodedBytes_{},sourceCount_{};
    std::array<std::atomic<std::uint64_t>,MaximumPages> pageTags_{};
    std::array<std::atomic<std::int16_t>,HintBuckets> slotHints_{};
    std::size_t maximumEncodedBytes_;bool controlRendering_{};
    int repeatAsset_{-1};std::int64_t repeatFirst_{-1},repeatLast_{-1},repeatEndPage_{-1};
    SpscQueue<Key,257> requests_,completed_;
    std::array<Key,256> pending_{};std::size_t pendingCount_{};
    std::uint64_t underruns_{},requestCount_{},dropped_{};
    std::atomic<bool> paused_{true},idle_{true},stopping_{false},workerSleeping_{false};
    std::atomic<std::uint32_t> wakeSeq_{0};
    std::atomic<std::uint32_t> decoded_{0},failures_{0},cached_{0};
    std::atomic<int> lastFailure_{0};std::thread worker_;
    static constexpr std::uint64_t packTag(int asset,std::int64_t page) noexcept {
        return (asset<0 || page<0) ? 0ULL : ((static_cast<std::uint64_t>(asset+1)<<36) | (static_cast<std::uint64_t>(page) & 0xFFFFFFFFFULL));
    }
    static constexpr std::size_t hintBucket(int asset,std::int64_t page) noexcept {
        const auto h=static_cast<std::uint64_t>(asset+1)*0x9E3779B185EBCA87ULL ^ static_cast<std::uint64_t>(page)*0xC2B2AE3D27D4EB4FULL;
        return static_cast<std::size_t>((h ^ (h>>32)) & (HintBuckets-1));
    }
    void publishSlotTag(std::size_t slot,int asset,std::int64_t page) noexcept;
    void clearSlotTag(std::size_t slot) noexcept;
    void wakeWorker() noexcept;
    void waitForWork(std::uint32_t expectedSeq,long timeoutNsec) noexcept;
    SourceStatus load(Key key) noexcept;
    SourceStatus setSourceControls(int asset,std::uint64_t mute,std::uint64_t solo,bool groups)noexcept;
    void work() noexcept;
    void collectCompleted() noexcept;
    static void increment(std::uint64_t& value) noexcept;
    static void increment(std::atomic<std::uint32_t>& value) noexcept;
public:
    explicit SourcePageCache(std::size_t maximumPcmBytes=MaximumPages*PagePcmBytes,std::size_t maximumEncodedBytes=256*1024*1024);
    ~SourcePageCache();
    SourcePageCache(const SourcePageCache&)=delete;SourcePageCache& operator=(const SourcePageCache&)=delete;
    void pauseWorker() noexcept;
    void resumeWorker() noexcept;
    void beginControlRender() noexcept {pauseWorker();controlRendering_=true;}
    void endControlRender() noexcept {controlRendering_=false;resumeWorker();}
    bool controlRendering() const noexcept {return controlRendering_;}
    std::uint32_t decodedCount() const noexcept {return decoded_.load(std::memory_order_relaxed);}
    unsigned moduleChannelCount(int asset)const noexcept;
    unsigned streamGroupCount(int asset)const noexcept;
    SourceStatus setStreamGroups(int asset,std::uint32_t mute,std::uint32_t solo)noexcept;
    SourceStatus setModuleChannels(int asset,std::uint64_t mute,std::uint64_t solo)noexcept;
    void unbind(int asset) noexcept;
    SourceStatus bind(int asset,std::unique_ptr<AudioFrameSource> source,std::size_t encodedBytes,const SourceSamplePosition* warm=nullptr,std::size_t count=0) noexcept;
    // Worker/control only, with decode worker paused; used by deterministic render/seek.
    SourceStatus prepare(int asset,SourceSamplePosition position) noexcept;
    void clearPendingRequests() noexcept;
    // Stopped linear playback only: reserve a bounded restart prefix so replay
    // decode never runs in the callback and cannot immediately evict the seam.
    SourceStatus prepareLinearRepeat(int asset,std::optional<SourceLoopMetadata> range)noexcept;
    bool request(int asset,std::int64_t page) noexcept;
    bool prefetchLinearRepeat(int asset,std::int64_t page)noexcept;
    int acquire(int asset,std::int64_t page) noexcept;
    void release(int slot) noexcept;
    float read(int slot,SourceSamplePosition position,int channel) const noexcept;
    SourceSamplePosition duration(int asset) const noexcept;
    void underrun() noexcept {increment(underruns_);}
    // RT consumer or stopped control only; facade must publish a coherent copy for queries.
    StreamCacheSnapshot snapshot() const noexcept;
};
// A Voice retains at most two pages, avoiding per-sample lookup/reclamation.
// Copies acquire no leases; assignment/reset releases old leases in bounded time.
class StreamCursor {
    SourcePageCache* cache_{};int asset_{-1};
    std::array<int,2> slots_{-1,-1};std::array<std::int64_t,2> pages_{-1,-1};bool prefetched_{},missing_{};
public:
    StreamCursor()=default;
    StreamCursor(const StreamCursor& other) noexcept:cache_(other.cache_),asset_(other.asset_) {}
    StreamCursor& operator=(const StreamCursor& other) noexcept;
    StreamCursor(StreamCursor&& other) noexcept { *this=std::move(other); }
    StreamCursor& operator=(StreamCursor&& other) noexcept;
    ~StreamCursor() {reset();}
    void bind(SourcePageCache* cache,int asset) noexcept;
    void reset() noexcept;
    bool missing()const noexcept {return missing_;}
    // Acquire and retain required pages; queue worker prefetch, never count an
    // underrun or read PCM. Decoder work is allowed only in stopped control render.
    bool prepare(double sourcePosition,std::optional<SourceSamplePosition> nextSample=std::nullopt) noexcept;
    std::array<float,2> sample(double position,std::optional<SourceSamplePosition> nextSample=std::nullopt) noexcept;
};
}
