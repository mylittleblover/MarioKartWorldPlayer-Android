#pragma once
#include <cstdint>
namespace player2 {
enum class SelectionDecisionKind { None,Sequence,Weighted,Shuffle,Jump };
// Latest actual selection, captured before history/bag mutation. Bounded value
// state; it never asks a second selector to reconstruct or predict the choice.
struct SelectionDecision {
    SelectionDecisionKind kind{SelectionDecisionKind::None};
    int effectiveAvoid{};
    std::uint64_t randomBefore{},eligible{},repeated{},shuffleUsed{};
    double unit{},scale{1},total{};
    bool recycledShuffle{};
    std::int64_t at{-1};
    std::uint64_t epoch{};
};
}
