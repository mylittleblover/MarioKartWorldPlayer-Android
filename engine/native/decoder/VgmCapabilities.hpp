#pragma once
#include "decoder/SourceBytes.hpp"
#include <array>

namespace player2::audio {
// File chip IDs/clock offsets are explicit VGM facts, never enum declaration order.
// Availability is provider-specific; PSG admission retains its original chip mask.
struct VgmChipCapability {std::uint8_t id;const char* name;std::size_t clockOffset;bool implemented;};
inline constexpr std::array<VgmChipCapability,48> VgmChips{{
    {0x00,"SN76489",0x0c,true},{0x01,"YM2413",0x10,true},{0x02,"YM2612",0x2c,true},{0x03,"YM2151",0x30,false},
    {0x04,"Sega PCM",0x38,false},{0x05,"RF5C68",0x40,false},{0x06,"YM2203",0x44,false},{0x07,"YM2608",0x48,false},
    {0x08,"YM2610",0x4c,false},{0x09,"YM3812",0x50,false},{0x0a,"YM3526",0x54,false},{0x0b,"Y8950",0x58,false},
    {0x0c,"YMF262",0x5c,false},{0x0d,"YMF278B",0x60,false},{0x0e,"YMF271",0x64,false},{0x0f,"YMZ280B",0x68,false},
    {0x10,"RF5C164",0x6c,false},{0x11,"PWM",0x70,false},{0x12,"AY8910",0x74,false},{0x13,"Game Boy DMG",0x80,false},
    {0x14,"NES APU",0x84,false},{0x15,"MultiPCM",0x88,false},{0x16,"uPD7759",0x8c,false},{0x17,"OKIM6258",0x90,false},
    {0x18,"OKIM6295",0x98,false},{0x19,"K051649",0x9c,false},{0x1a,"K054539",0xa0,false},{0x1b,"HuC6280",0xa4,false},
    {0x1c,"C140",0xa8,false},{0x1d,"K053260",0xac,false},{0x1e,"Pokey",0xb0,false},{0x1f,"QSound",0xb4,false},
    {0x20,"SCSP",0xb8,false},{0x21,"WonderSwan",0xc0,false},{0x22,"VSU",0xc4,false},{0x23,"SAA1099",0xc8,false},
    {0x24,"ES5503",0xcc,false},{0x25,"ES5506",0xd0,false},{0x26,"X1-010",0xd8,false},{0x27,"C352",0xdc,false},
    {0x28,"GA20",0xe0,false},{0x29,"Mikey",0xe4,false},{0x2a,"K007232",0xe8,false},{0x2b,"K005289",0xec,false},
    {0x2c,"MSM5205",0xf0,false},{0x2d,"MSM5232",0xf4,false},{0x2e,"BSMT2000",0xf8,false},{0x2f,"ICS2115",0xfc,false}
}};
enum class VgmWriteKind {PsgRegister=0,PsgStereo=1};
struct VgmWriteCapability {std::uint8_t command,chip,instance;VgmWriteKind kind;};
inline constexpr std::array<VgmWriteCapability,4> VgmWrites{{{0x50,0x00,0,VgmWriteKind::PsgRegister},{0x4f,0x00,0,VgmWriteKind::PsgStereo},
    {0x30,0x00,1,VgmWriteKind::PsgRegister},{0x3f,0x00,1,VgmWriteKind::PsgStereo}}};
inline const VgmWriteCapability* vgmWriteCapability(std::uint8_t command)noexcept {
    for(const auto& entry:VgmWrites)if(entry.command==command)return &entry;
    return nullptr;
}
template<class Input>
inline void validateVgmChipCapabilities(Input& input,std::size_t headerEnd,std::uint64_t allowedChips=1) {
    input.need(0,headerEnd);
    for(const auto& chip:VgmChips)
        if(chip.clockOffset+4<=headerEnd && (!(allowedChips&(std::uint64_t{1}<<chip.id)) || !chip.implemented) && input.u32(chip.clockOffset)!=0)fail(SourceStatus::UnsupportedCodec);
    // Extended chip clocks/volumes and nondefault logarithmic source gain are not
    // implemented; refuse rather than render a silently changed source.
    if((headerEnd>=0xc0 && input.u32(0xbc)!=0) || (headerEnd>=0x7d && input.u8(0x7c)!=0))fail(SourceStatus::UnsupportedCodec);
}
}
