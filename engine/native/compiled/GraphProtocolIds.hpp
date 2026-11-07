#pragma once
#include "compiled/Graph.hpp"
#include <optional>

namespace player2::protocol {
constexpr int PlaylistSeedVersion=6;
constexpr int PlaybackExtensionVersion=7;
constexpr int LayerControlVersion=8;
constexpr int MusicalLoopVersion=9;
constexpr int EffectRtpcVersion=10;
constexpr int AlbumFlowVersion=11;
constexpr int SegmentEnvelopeVersion=12;
constexpr int ClipEnvelopeVersion=13;
constexpr int Version=ClipEnvelopeVersion;
constexpr int ClipLoopVersion=5;
constexpr int SourceReferenceVersion=4;
constexpr int SourceDigestVersion=3;
constexpr int PropertyPresenceVersion=2;
constexpr int LegacyVersion=1;
constexpr bool supportedVersion(int version) noexcept {
    switch(version) {
        case LegacyVersion:case PropertyPresenceVersion:case SourceDigestVersion:
        case SourceReferenceVersion:case ClipLoopVersion:case PlaylistSeedVersion:case PlaybackExtensionVersion:case LayerControlVersion:case MusicalLoopVersion:case EffectRtpcVersion:case AlbumFlowVersion:case SegmentEnvelopeVersion:case ClipEnvelopeVersion:return true;
        default:return false;
    }
}
enum class CueKind { Custom=0,Event=1 };
enum class SyncKind { State=0,Switch=1 };
enum class Property { VolumeDb=0,PlaybackSpeed=1,LowPassHz=2,HighPassHz=3,ReverbWet=4,Pan=5,Bypass=6 };
enum class EffectKind { LowPass=0,HighPass=1,Equalizer=2,Compressor=3,Limiter=4,Delay=5,Reverb=6 };
enum class EffectParameter { Frequency=0,Q=1,GainDb=2,ThresholdDb=3,Ratio=4,AttackMs=5,ReleaseMs=6,DelayMs=7,Feedback=8,Wet=9 };
// Enumerate accepted IDs explicitly: additions and gaps are never implicit ABI.
template<class E> std::optional<E> enumFromWire(int value) noexcept;
template<> inline std::optional<AlbumFlowMode> enumFromWire<AlbumFlowMode>(int value) noexcept {
    switch(value) {
        case 0:return AlbumFlowMode::Gapless;case 1:return AlbumFlowMode::Crossfade;case 2:return AlbumFlowMode::ExitCue;
        case 3:return AlbumFlowMode::Bridge;case 4:return AlbumFlowMode::Tail;case 5:return AlbumFlowMode::Transition;
        case 6:return AlbumFlowMode::Ending;default:return std::nullopt;
    }
}
template<> inline std::optional<EffectParameter> enumFromWire<EffectParameter>(int value) noexcept {
    switch(value) {
        case 0:return EffectParameter::Frequency;case 1:return EffectParameter::Q;case 2:return EffectParameter::GainDb;
        case 3:return EffectParameter::ThresholdDb;case 4:return EffectParameter::Ratio;case 5:return EffectParameter::AttackMs;
        case 6:return EffectParameter::ReleaseMs;case 7:return EffectParameter::DelayMs;case 8:return EffectParameter::Feedback;
        case 9:return EffectParameter::Wet;default:return std::nullopt;
    }
}
template<> inline std::optional<Kind> enumFromWire<Kind>(int value) noexcept {
    switch(value) {
        case 0: return Kind::Folder;
        case 1: return Kind::Segment;
        case 2: return Kind::Track;
        case 3: return Kind::Playlist;
        case 4: return Kind::Switch;
        case 5: return Kind::SyncGroup;
        case 6: return Kind::Parameter;
        case 7: return Kind::Trigger;
        case 8: return Kind::Event;
        case 9: return Kind::Transition;
        case 10: return Kind::Rtpc;
        case 11: return Kind::Stinger;
        case 12: return Kind::Bus;
        case 13: return Kind::Media;
        default: return std::nullopt;
    }
}
template<> inline std::optional<TrackMode> enumFromWire<TrackMode>(int value) noexcept {
    switch(value) {
        case 0: return TrackMode::Normal;
        case 1: return TrackMode::RandomStep;
        case 2: return TrackMode::SequenceStep;
        case 3: return TrackMode::Switch;
        default: return std::nullopt;
    }
}
template<> inline std::optional<PlaylistMode> enumFromWire<PlaylistMode>(int value) noexcept {
    switch(value) {
        case 0: return PlaylistMode::SequenceContinuous;
        case 1: return PlaylistMode::SequenceStep;
        case 2: return PlaylistMode::RandomContinuous;
        case 3: return PlaylistMode::RandomStep;
        default: return std::nullopt;
    }
}
template<> inline std::optional<RandomMode> enumFromWire<RandomMode>(int value) noexcept {
    switch(value) {
        case 0: return RandomMode::Standard;
        case 1: return RandomMode::Shuffle;
        default: return std::nullopt;
    }
}
template<> inline std::optional<SyncPoint> enumFromWire<SyncPoint>(int value) noexcept {
    switch(value) {
        case 0: return SyncPoint::Immediate;
        case 1: return SyncPoint::NextBeat;
        case 2: return SyncPoint::NextBar;
        case 3: return SyncPoint::NextGrid;
        case 4: return SyncPoint::ExitCue;
        case 5: return SyncPoint::NextCue;
        case 6: return SyncPoint::NextCustomCue;
        default: return std::nullopt;
    }
}
template<> inline std::optional<EntryMode> enumFromWire<EntryMode>(int value) noexcept {
    switch(value) {
        case 0: return EntryMode::EntryCue;
        case 1: return EntryMode::SameTime;
        case 2: return EntryMode::RandomCue;
        case 3: return EntryMode::RandomCustomCue;
        case 4: return EntryMode::LastExit;
        case 5: return EntryMode::PlaylistLast;
        case 6: return EntryMode::PlaylistNext;
        case 7: return EntryMode::PlaylistJump;
        default: return std::nullopt;
    }
}
template<> inline std::optional<ActionKind> enumFromWire<ActionKind>(int value) noexcept {
    switch(value) {
        case 0: return ActionKind::Play;
        case 1: return ActionKind::Stop;
        case 2: return ActionKind::Pause;
        case 3: return ActionKind::Resume;
        case 4: return ActionKind::SetSync;
        case 5: return ActionKind::SetParameter;
        case 6: return ActionKind::Trigger;
        case 7: return ActionKind::Event;
        case 8: return ActionKind::LayerGain;
        default: return std::nullopt;
    }
}
template<> inline std::optional<EndBehavior> enumFromWire<EndBehavior>(int value) noexcept {
    switch(value) {
        case 0: return EndBehavior::Natural;
        case 1: return EndBehavior::Immediate;
        case 2: return EndBehavior::Fade;
        case 3: return EndBehavior::Ending;
        default: return std::nullopt;
    }
}
template<> inline std::optional<Shape> enumFromWire<Shape>(int value) noexcept {
    switch(value) {
        case 0: return Shape::Linear;
        case 1: return Shape::Hold;
        case 2: return Shape::SCurve;
        case 3: return Shape::EaseIn;
        case 4: return Shape::EaseOut;
        default: return std::nullopt;
    }
}
template<> inline std::optional<Modifier> enumFromWire<Modifier>(int value) noexcept {
    switch(value) {
        case 0: return Modifier::Absolute;
        case 1: return Modifier::Sum;
        case 2: return Modifier::Highest;
        case 3: return Modifier::Boolean;
        default: return std::nullopt;
    }
}
template<> inline std::optional<CueKind> enumFromWire<CueKind>(int value) noexcept {
    switch(value) {
        case 0: return CueKind::Custom;
        case 1: return CueKind::Event;
        default: return std::nullopt;
    }
}
template<> inline std::optional<SyncKind> enumFromWire<SyncKind>(int value) noexcept {
    switch(value) {
        case 0: return SyncKind::State;
        case 1: return SyncKind::Switch;
        default: return std::nullopt;
    }
}
template<> inline std::optional<Property> enumFromWire<Property>(int value) noexcept {
    switch(value) {
        case 0: return Property::VolumeDb;
        case 1: return Property::PlaybackSpeed;
        case 2: return Property::LowPassHz;
        case 3: return Property::HighPassHz;
        case 4: return Property::ReverbWet;
        case 5: return Property::Pan;
        case 6: return Property::Bypass;
        default: return std::nullopt;
    }
}
template<> inline std::optional<EffectKind> enumFromWire<EffectKind>(int value) noexcept {
    switch(value) {
        case 0: return EffectKind::LowPass;
        case 1: return EffectKind::HighPass;
        case 2: return EffectKind::Equalizer;
        case 3: return EffectKind::Compressor;
        case 4: return EffectKind::Limiter;
        case 5: return EffectKind::Delay;
        case 6: return EffectKind::Reverb;
        default: return std::nullopt;
    }
}
}
