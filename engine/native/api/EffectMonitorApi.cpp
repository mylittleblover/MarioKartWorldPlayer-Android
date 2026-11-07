#include "runtime/EngineHandleState.hpp"
namespace player2::api {
EffectMonitorPacket engineEffectSnapshot(const EngineHandle* engine,int index) noexcept {
    if(!engine || index<0 || index>=4096)return effectPacket({});
    for(;;) {
        const auto before=engine->sequence.load(std::memory_order_acquire);if(before&1)continue;
        auto packet=engine->effects[index].load();packet[9]=engine->frame.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if(before==engine->sequence.load(std::memory_order_relaxed))return packet;
    }
}
}
