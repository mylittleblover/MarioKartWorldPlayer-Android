#pragma once
#include <cstddef>
#include <cstdlib>
#include <algorithm>
#include <cstring>
#include <limits>
#include <new>

namespace player2::audio {
// Decoder/control-only allocation callbacks. Live allocation is bounded even
// for malicious codebooks/block sizes; no decoder enters the audio callback.
struct CodecAllocationBudget {
    std::size_t maximum{8*1024*1024},live{},peak{};bool exhausted{};
    struct alignas(std::max_align_t) Header {std::size_t size;};
    static void* allocate(std::size_t size,void* owner)noexcept {
        auto& budget=*static_cast<CodecAllocationBudget*>(owner);
        if(size>budget.maximum-budget.live || size>std::numeric_limits<std::size_t>::max()-sizeof(Header)) {budget.exhausted=true;return nullptr;}
        auto* memory=static_cast<Header*>(std::malloc(size+sizeof(Header)));
        if(!memory){budget.exhausted=true;return nullptr;}
        ::new(static_cast<void*>(memory)) Header{size};budget.live+=size;budget.peak=std::max(budget.peak,budget.live);return memory+1;
    }
    static void release(void* memory,void* owner)noexcept {
        if(!memory)return;
        auto& budget=*static_cast<CodecAllocationBudget*>(owner);auto* header=static_cast<Header*>(memory)-1;
        budget.live-=header->size;std::free(header);
    }
    static void* resize(void* memory,std::size_t size,void* owner)noexcept {
        if(!memory)return allocate(size,owner);
        if(!size){release(memory,owner);return nullptr;}
        auto& budget=*static_cast<CodecAllocationBudget*>(owner);auto* header=static_cast<Header*>(memory)-1;
        const auto old=header->size;
        if(size>budget.maximum-(budget.live-old) || size>std::numeric_limits<std::size_t>::max()-sizeof(Header)) {budget.exhausted=true;return nullptr;}
        auto* resized=static_cast<Header*>(std::realloc(header,size+sizeof(Header)));
        if(!resized){budget.exhausted=true;return nullptr;}
        ::new(static_cast<void*>(resized)) Header{size};budget.live=budget.live-old+size;budget.peak=std::max(budget.peak,budget.live);return resized+1;
    }
};
}
