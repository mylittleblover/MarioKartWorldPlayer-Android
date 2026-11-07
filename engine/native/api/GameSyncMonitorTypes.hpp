#pragma once
#include <cstdint>
#include <vector>
namespace player2::api {
// Control-thread packet, never constructed by the audio callback. All records
// share one publication sequence and Transport frame. Empty means unavailable.
// v1: version, frame, group count, parameter count, layer count; then groups
// (index,value), parameters (index,current bits,target bits), layers (index,trim bits).
// v2 adds transport status and transition-pending after the three counts. These
// flags share the same publication as values, so Listener gates never tear.
using GameSyncMonitorPacket=std::vector<std::int64_t>;
inline constexpr std::size_t maxGameSyncMonitorWords=7+4096*3;
}
