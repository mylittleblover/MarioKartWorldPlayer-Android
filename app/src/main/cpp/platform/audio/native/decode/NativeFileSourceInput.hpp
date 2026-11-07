#pragma once
#include "api/EncodedSourceInput.hpp"
#include <memory>

namespace player2::platform {
struct FileSourceInputResult {
    audio::SourceStatus status{audio::SourceStatus::InputUnavailable};
    std::unique_ptr<audio::EncodedSourceInput> input;
};
// Duplicates a borrowed descriptor. Only regular, seekable files are admitted.
// The lease owns its descriptor independently of the Java/Android caller.
FileSourceInputResult openFileSourceInput(int borrowedDescriptor) noexcept;
}
