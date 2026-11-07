#pragma once
#include "api/SequenceTypes.hpp"
#include <vector>
namespace player2::audio {
// Immutable, read-only source inspection. Never executed by PLAYER2 scheduling.
struct SourceSequenceData {SequenceSummary summary;std::vector<SequenceEvent> events;};
}
