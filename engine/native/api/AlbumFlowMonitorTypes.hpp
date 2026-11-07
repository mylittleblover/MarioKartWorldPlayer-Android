#pragma once
#include "api/EngineTypes.hpp"
#include <cstring>
namespace player2::api {
enum class AlbumFlowPhase : std::int32_t {None=0,Playing=1,WaitingTail=2,Transitioning=3,Ending=4,Finished=5,Stopped=6,Overridden=7,Failed=8};
struct AlbumFlowSnapshot {
    int current{-1},from{-1},to{-1},mode{-1},bridge{-1};AlbumFlowPhase phase{AlbumFlowPhase::None};EngineStatus result{EngineStatus::Ok};
    bool active{},paused{};audio::TransportFrame requestedAt{-1},at,scheduledAt{-1};std::int64_t completedEdges{};
};
using AlbumFlowMonitorPacket=std::array<std::int64_t,14>;
inline AlbumFlowMonitorPacket albumFlowPacket(const AlbumFlowSnapshot& s) noexcept {
    return {1,s.current,s.from,s.to,s.mode,s.bridge,static_cast<int>(s.phase),static_cast<int>(s.result),s.active,s.paused,
        s.requestedAt.value,s.at.value,s.scheduledAt.value,s.completedEdges};
}
}
