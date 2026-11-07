// PLAYER2 adapter to the pinned MIT emu2413, within the replaceable LGPL GME library.
#include "Ym2413_Emu.h"
#include "ext/emu2413.h"
#include <cmath>
#include <cstdint>
#include <limits>
Ym2413_Emu::Ym2413_Emu():opll(nullptr) {}
Ym2413_Emu::~Ym2413_Emu(){if(opll)OPLL_delete(opll);}
int Ym2413_Emu::set_rate(double sample_rate,double clock_rate) {
    if(!std::isfinite(sample_rate) || !std::isfinite(clock_rate) || sample_rate<1 || clock_rate<1 ||
        sample_rate>std::numeric_limits<std::uint32_t>::max() || clock_rate>std::numeric_limits<std::uint32_t>::max())return 1;
    auto* next=OPLL_new(static_cast<std::uint32_t>(clock_rate),static_cast<std::uint32_t>(sample_rate));
    if(!next)return 1;
    OPLL_setChipType(next,0);OPLL_resetPatch(next,0);OPLL_reset(next);
    if(opll)OPLL_delete(opll);
    opll=next;
    return 0;
}
void Ym2413_Emu::reset(){if(opll){const auto mask=opll->mask;OPLL_reset(opll);OPLL_setMask(opll,mask);}}
void Ym2413_Emu::write(int address,int data){if(opll)OPLL_writeReg(opll,address,data);}
void Ym2413_Emu::mute_voices(int mask){if(opll)OPLL_setMask(opll,static_cast<std::uint32_t>(mask));}
void Ym2413_Emu::run(int pairs,sample_t* output) {
    for(int n=0;n<pairs;++n){const auto sample=opll ? OPLL_calc(opll) : 0;output[n*2]=output[n*2+1]=sample;}
}
