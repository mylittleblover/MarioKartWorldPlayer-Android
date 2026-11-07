#include "runtime/EngineHandleState.hpp"
namespace player2::api {
AlbumFlowMonitorPacket engineAlbumFlowSnapshot(const EngineHandle* engine) noexcept {
    if(!engine)return albumFlowPacket({});
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        const auto packet=engine->album.load();std::atomic_thread_fence(std::memory_order_acquire);
        if(before==engine->sequence.load(std::memory_order_relaxed))return packet;
    }
}
}
