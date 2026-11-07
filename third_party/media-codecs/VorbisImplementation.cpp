// Bounded Engine push input and arena allocation only; no stdio/pull playback.
#define STB_VORBIS_NO_STDIO
#define STB_VORBIS_NO_INTEGER_CONVERSION
#define STB_VORBIS_NO_PULLDATA_API
#define STB_VORBIS_MAX_CHANNELS 2
#include <array>
#include <Player2VorbisImplementation.inc>
