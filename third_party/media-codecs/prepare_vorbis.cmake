# Keep the pinned upstream original intact; derive a clearly recorded C++ patch.
# Upstream writes a global CRC table during every open, racing independent
# Providers on different workers. The identical polynomial table can be constant.
if(NOT DEFINED PLAYER2_VORBIS_SOURCE)
    set(PLAYER2_VORBIS_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/stb_vorbis.c")
endif()
file(READ "${PLAYER2_VORBIS_SOURCE}" vorbis_source)
set(old_crc [=[static uint32 crc_table[256];
static void crc32_init(void)
{
   int i,j;
   uint32 s;
   for(i=0; i < 256; i++) {
      for (s=(uint32) i << 24, j=0; j < 8; ++j)
         s = (s << 1) ^ (s >= (1U<<31) ? CRC32_POLY : 0);
      crc_table[i] = s;
   }
}]=])
set(new_crc [=[static constexpr auto crc_table = [] {
   std::array<uint32,256> table{};
   for (unsigned i=0; i<256; ++i) {
      uint32 s=i<<24;
      for (int j=0;j<8;++j) s=(s<<1)^((s&(1U<<31)) ? CRC32_POLY : 0);
      table[i]=s;
   }
   return table;
}();
static void crc32_init(void) {}]=])
string(FIND "${vorbis_source}" "${old_crc}" crc_offset)
if(crc_offset EQUAL -1)
    message(FATAL_ERROR "Pinned stb_vorbis CRC patch context mismatch")
endif()
string(REPLACE "${old_crc}" "${new_crc}" vorbis_source "${vorbis_source}")
set(vorbis_patch "${CMAKE_CURRENT_BINARY_DIR}/Player2VorbisImplementation.inc")
set(previous "")
if(EXISTS "${vorbis_patch}")
    file(READ "${vorbis_patch}" previous)
endif()
if(NOT previous STREQUAL vorbis_source)
    file(WRITE "${vorbis_patch}" "${vorbis_source}")
endif()
