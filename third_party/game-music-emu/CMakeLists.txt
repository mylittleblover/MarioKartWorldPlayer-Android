# A Source emulator dependency, not a PLAYER2 Runtime or output owner.
set(archive "${CMAKE_CURRENT_SOURCE_DIR}/game-music-emu-0.6.5.tar.gz")
file(SHA256 "${archive}" digest)
if(NOT digest STREQUAL "5e8eb240b03ee76b17aafb2ba52ce838399664fb65a6a7739335a2f581a84e0e")
    message(FATAL_ERROR "Pinned Game_Music_Emu archive integrity failure")
endif()
set(source_root "${CMAKE_CURRENT_BINARY_DIR}/source/game-music-emu-9e23d10f9fd2a6a2f33b10912dd8dc7153258995")
if(NOT EXISTS "${source_root}/gme/gme.h")
    file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/source")
    file(ARCHIVE_EXTRACT INPUT "${archive}" DESTINATION "${CMAKE_CURRENT_BINARY_DIR}/source")
endif()
include(prepare.cmake)
include(prepare-fm.cmake)
# Shared LGPL library: keep the stable C boundary and replaceable .so. Only the
# individually reviewed NSF/NSFe/SPC/VGM kernels are built; no GPL YM2612 backend,
# archive player, file UI, audio device, extra chip-format or hidden gzip route.
foreach(format AY GBS GYM HES KSS SAP)
    set(USE_GME_${format} OFF CACHE BOOL "PLAYER2 reviewed emulator selection" FORCE)
endforeach()
foreach(format NSF NSFE SPC VGM)
    set(USE_GME_${format} ON CACHE BOOL "PLAYER2 reviewed emulator selection" FORCE)
endforeach()
set(GME_BUILD_SHARED ON CACHE BOOL "Replaceable LGPL source emulator" FORCE)
foreach(option GME_BUILD_STATIC GME_BUILD_FRAMEWORK GME_BUILD_TESTING GME_BUILD_EXAMPLES GME_ZLIB)
    set(${option} OFF CACHE BOOL "PLAYER2 bounded source-only dependency" FORCE)
endforeach()
set(GME_YM2612_EMU Nuked CACHE STRING "LGPL-only OPN2 kernel" FORCE)
add_subdirectory("${source_root}" "upstream")
target_compile_features(gme_shared PRIVATE cxx_std_17)
set_target_properties(gme_shared PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES)

# This target mixes C (emu2413.c) and C++ sources. CMake then picks the C driver
# as the linker language, which adds no libc++ and leaves __cxa_guard_acquire,
# operator new and __cxa_pure_virtual undefined. Force the C++ linker and link
# libc++ explicitly.
set_target_properties(gme_shared PROPERTIES LINKER_LANGUAGE CXX)
if(ANDROID)
    target_link_libraries(gme_shared PRIVATE c++_shared)
endif()

target_include_directories(gme_shared SYSTEM INTERFACE "${source_root}/gme")
add_library(player2_emulation_renderer ALIAS gme_shared)
