// Separate, prefixed Vorbis symbols keep MO3's worker decoder independent of
// the bounded standalone Vorbis provider implementation.
#define MINIMP3_IMPLEMENTATION
#include <minimp3/minimp3.h>
#define STB_VORBIS_NO_STDIO
#define STB_VORBIS_NO_INTEGER_CONVERSION
#define STB_VORBIS_NO_PULLDATA_API
#define STB_VORBIS_MAX_CHANNELS 2
#include <array>
#include <stb_vorbis/stb_vorbis.c>
