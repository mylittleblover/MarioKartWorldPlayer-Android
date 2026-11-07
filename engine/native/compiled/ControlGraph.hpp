#pragma once
#include "compiled/GraphTypes.hpp"
#include <optional>

namespace player2 {
enum class SyncPoint { Immediate=0,NextBeat=1,NextBar=2,NextGrid=3,ExitCue=4,NextCue=5,NextCustomCue=6 };
enum class EntryMode { EntryCue=0,SameTime=1,RandomCue=2,RandomCustomCue=3,LastExit=4,PlaylistLast=5,PlaylistNext=6,PlaylistJump=7 };
enum class ActionKind { Play=0,Stop=1,Pause=2,Resume=3,SetSync=4,SetParameter=5,Trigger=6,Event=7,LayerGain=8 };
struct SyncGroup { int index{-1},kind{}; std::vector<int> values; int defaultValue{-1}; };
struct Parameter { int index{-1}; double min{},max{},defaultValue{}; Frame attack{},release{}; };
struct Action { ActionKind kind{}; int target{-1},value{-1}; double number{}; bool retainTail{true}; };
struct Event { int index{-1}; std::vector<Action> actions; };
struct Fade { double beats{},offset{}; Shape shape{}; };
struct Transition { int index{-1}; std::vector<int> sources,destinations; int specificity{}; SyncPoint sync{}; std::optional<std::string> sourceCue; EntryMode entry{}; std::optional<std::string> destinationCue; int jumpItem{-1},bridge{-1}; Fade fadeOut,fadeIn; bool postExit{},preEntry{}; int priority{}; std::vector<Condition> conditions; };
struct Rtpc { int index{-1},parameter{-1},target{-1},property{}; Modifier policy{}; std::vector<Point> points; };
struct Stinger { int index{-1},trigger{-1},segment{-1}; SyncPoint sync{}; int priority{}; bool replace{}; double cooldownBeats{}; };
}
