# Auditable patches against the immutable pinned archive. No algorithm replacement.
player2_gme_replace("${source_root}/gme/Ym2413_Emu.h" [==[
class Ym2413_Emu  {
	struct OPLL* opll;
public:
	Ym2413_Emu();
]==] [==[
class Ym2413_Emu  {
	struct __OPLL* opll;
public:
	Ym2413_Emu();
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[typedef struct
{
    Bit32u cycles;
    Bit32u channel;
]==] [==[typedef struct
{
    Bit32u chip_type; // PLAYER2: per-instance chip model
    Bit32u cycles;
    Bit32u channel;
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[/* EXTRA, original was "void OPN2_Reset(ym3438_t *chip)" */
void OPN2_Reset(ym3438_t *chip, Bit32u rate, Bit32u clock);
void OPN2_SetChipType(Bit32u type);
void OPN2_Clock(ym3438_t *chip, Bit16s *buffer);
void OPN2_Write(ym3438_t *chip, Bit32u port, Bit8u data);
]==] [==[/* EXTRA, original was "void OPN2_Reset(ym3438_t *chip)" */
void OPN2_Reset(ym3438_t *chip, Bit32u rate, Bit32u clock);
void OPN2_SetChipType(ym3438_t *chip, Bit32u type);
void OPN2_Clock(ym3438_t *chip, Bit16s *buffer);
void OPN2_Write(ym3438_t *chip, Bit32u port, Bit8u data);
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[void OPN2_GenerateStream(ym3438_t *chip, Bit16s *output, Bit32u numsamples);
void OPN2_GenerateStreamMix(ym3438_t *chip, Bit16s *output, Bit32u numsamples);
void OPN2_SetOptions(Bit8u flags);
void OPN2_SetMute(ym3438_t *chip, Bit32u mute);

]==] [==[void OPN2_GenerateStream(ym3438_t *chip, Bit16s *output, Bit32u numsamples);
void OPN2_GenerateStreamMix(ym3438_t *chip, Bit16s *output, Bit32u numsamples);
void OPN2_SetOptions(ym3438_t *chip, Bit8u flags);
void OPN2_SetMute(ym3438_t *chip, Bit32u mute);

]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[};

static Bit32u chip_type = ym3438_type_discrete;

void OPN2_DoIO(ym3438_t *chip)
]==] [==[};

// PLAYER2: model state belongs to ym3438_t, never a mutable process global.

void OPN2_DoIO(ym3438_t *chip)
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[    chip->mor = 0;

    if (chip_type == ym3438_type_ym2612)
    {
        out_en = ((cycles & 3) == 3) || test_dac;
]==] [==[    chip->mor = 0;

    if (chip->chip_type == ym3438_type_ym2612)
    {
        out_en = ((cycles & 3) == 3) || test_dac;
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[        out_en = ((cycles & 3) != 0) || test_dac;
        /* Discrete YM3438 seems has the ladder effect too */
        if (out >= 0 && chip_type == ym3438_type_discrete)
        {
            out++;
]==] [==[        out_en = ((cycles & 3) != 0) || test_dac;
        /* Discrete YM3438 seems has the ladder effect too */
        if (out >= 0 && chip->chip_type == ym3438_type_discrete)
        {
            out++;
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[    Bit32u i, rateratio;
    rateratio = (Bit32u)chip->rateratio;
    memset(chip, 0, sizeof(ym3438_t));
    for (i = 0; i < 24; i++)
    {
]==] [==[    Bit32u i, rateratio;
    rateratio = (Bit32u)chip->rateratio;
    const Bit32u type = chip->chip_type;
    memset(chip, 0, sizeof(ym3438_t));
    chip->chip_type = type;
    for (i = 0; i < 24; i++)
    {
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[}

void OPN2_SetChipType(Bit32u type)
{
    chip_type = type;
}

]==] [==[}

void OPN2_SetChipType(ym3438_t *chip, Bit32u type)
{
    chip->chip_type = type;
}

]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[Bit8u OPN2_Read(ym3438_t *chip, Bit32u port)
{
    if ((port & 3) == 0 || chip_type == ym3438_type_asic)
    {
        if (chip->mode_test_21[6])
]==] [==[Bit8u OPN2_Read(ym3438_t *chip, Bit32u port)
{
    if ((port & 3) == 0 || chip->chip_type == ym3438_type_asic)
    {
        if (chip->mode_test_21[6])
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[

void OPN2_SetOptions(Bit8u flags)
{
    switch ((flags >> 3) & 0x03)
]==] [==[

void OPN2_SetOptions(ym3438_t *chip, Bit8u flags)
{
    switch ((flags >> 3) & 0x03)
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[    case 0x00: /* YM2612 */
    default:
        OPN2_SetChipType(ym3438_type_ym2612);
        break;
    case 0x01: /* ASIC YM3438 */
        OPN2_SetChipType(ym3438_type_asic);
        break;
    case 0x02: /* Discrete YM3438 */
        OPN2_SetChipType(ym3438_type_discrete);
        break;
    }
]==] [==[    case 0x00: /* YM2612 */
    default:
        OPN2_SetChipType(chip, ym3438_type_ym2612);
        break;
    case 0x01: /* ASIC YM3438 */
        OPN2_SetChipType(chip, ym3438_type_asic);
        break;
    case 0x02: /* Discrete YM3438 */
        OPN2_SetChipType(chip, ym3438_type_discrete);
        break;
    }
]==])
player2_gme_replace("${source_root}/gme/Ym2612_Nuked.cpp" [==[

Ym2612_Nuked_Emu::Ym2612_Nuked_Emu()
{
	Ym2612_NukedImpl::OPN2_SetChipType( Ym2612_NukedImpl::ym3438_type_asic );
	impl = new Ym2612_NukedImpl::ym3438_t;
}

]==] [==[

Ym2612_Nuked_Emu::Ym2612_Nuked_Emu():impl(new Ym2612_NukedImpl::ym3438_t{}),prev_sample_rate(44100),prev_clock_rate(7670454)
{
    Ym2612_NukedImpl::OPN2_SetChipType(reinterpret_cast<Ym2612_NukedImpl::ym3438_t*>(impl), Ym2612_NukedImpl::ym3438_type_ym2612);
}

]==])
player2_gme_replace("${source_root}/gme/ext/emu2413.c" [==[}

static uint8_t table_initialized = 0;

static void initializeTables(void) {
]==] [==[}

// PLAYER2: immutable tables initialized once across all OPLL/VRC7 instances.
#include <stdatomic.h>
static atomic_flag player2_table_lock = ATOMIC_FLAG_INIT;
static atomic_int table_initialized = ATOMIC_VAR_INIT(0);

static void initializeTables(void) {
]==])
player2_gme_replace("${source_root}/gme/ext/emu2413.c" [==[  makeSinTable();
  makeDefaultPatch();
  table_initialized = 1;
}

]==] [==[  makeSinTable();
  makeDefaultPatch();
  atomic_store_explicit(&table_initialized, 1, memory_order_release);
}

]==])
player2_gme_replace("${source_root}/gme/ext/emu2413.c" [==[  int i;

  if (!table_initialized) {
    initializeTables();
  }

]==] [==[  int i;

  if (!atomic_load_explicit(&table_initialized, memory_order_acquire)) {
    while (atomic_flag_test_and_set_explicit(&player2_table_lock, memory_order_acquire)) {}
    if (!atomic_load_explicit(&table_initialized, memory_order_relaxed)) initializeTables();
    atomic_flag_clear_explicit(&player2_table_lock, memory_order_release);
  }

]==])
configure_file("${CMAKE_CURRENT_LIST_DIR}/Ym2413_Emu.cpp" "${source_root}/gme/Ym2413_Emu.cpp" COPYONLY)
