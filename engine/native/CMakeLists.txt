# This is the only Engine translation-unit list, shared by host and Android.
find_package(Threads REQUIRED)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/libopenmpt" "${CMAKE_CURRENT_BINARY_DIR}/module-renderer")
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/media-codecs" "${CMAKE_CURRENT_BINARY_DIR}/media-codecs")
add_library(player2_engine STATIC runtime/EngineRuntime.cpp runtime/RuntimeTransitions.cpp
    runtime/RuntimeSwitches.cpp runtime/RuntimeSeek.cpp runtime/RuntimePreparation.cpp
    runtime/RuntimeCueLookAhead.cpp runtime/RuntimeLookAhead.cpp runtime/RuntimeSources.cpp runtime/RuntimeEvents.cpp runtime/RuntimeExtensions.cpp runtime/RuntimeAutomatic.cpp runtime/RuntimeMusicalLoop.cpp runtime/RuntimePlaylistMonitor.cpp
    runtime/RuntimeStingers.cpp runtime/RuntimeAlbumFlow.cpp music/GraphReader.cpp api/EngineApi.cpp api/EffectMonitorApi.cpp api/AlbumFlowMonitorApi.cpp api/AudioSourceApi.cpp api/StreamSourceApi.cpp api/ModuleApi.cpp api/ModuleXmApi.cpp
    audio/streaming/SourcePageCache.cpp audio/source/SourceDigest.cpp audio/source/AudioFrameSource.cpp audio/source/StreamStereoSource.cpp audio/source/PrepareBuffer.cpp decoder/WaveSource.cpp decoder/WaveInputSource.cpp decoder/FlacSource.cpp decoder/Mp3Source.cpp decoder/VorbisSource.cpp decoder/BfstmSource.cpp decoder/BfstmInputSource.cpp decoder/BfstmDspSource.cpp decoder/VagSource.cpp decoder/VgmSource.cpp decoder/ModSource.cpp decoder/XmSource.cpp)
target_sources(player2_engine PRIVATE decoder/TrackerSource.cpp decoder/Mo3Source.cpp api/Mo3Api.cpp)
target_include_directories(player2_engine PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_features(player2_engine PUBLIC cxx_std_17)
target_link_libraries(player2_engine PUBLIC Threads::Threads PRIVATE player2_module_renderer player2_flac_codec player2_mp3_codec player2_vorbis_codec)
target_compile_options(player2_engine PRIVATE -Wall -Wextra -Wpedantic -Werror)
set_target_properties(player2_engine PROPERTIES POSITION_INDEPENDENT_CODE ON)

# gzip is a codec dependency, with the same Engine provider on host/Android.
if(ANDROID)
    target_link_libraries(player2_engine PRIVATE z)
else()
    find_package(ZLIB REQUIRED)
    target_link_libraries(player2_engine PRIVATE ZLIB::ZLIB)
endif()
target_sources(player2_engine PRIVATE decoder/VgzSource.cpp)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/game-music-emu" "${CMAKE_CURRENT_BINARY_DIR}/emulation-renderer")
target_sources(player2_engine PRIVATE decoder/EmulatedSource.cpp)
target_link_libraries(player2_engine PRIVATE player2_emulation_renderer)

target_sources(player2_engine PRIVATE decoder/AdxSource.cpp)
target_sources(player2_engine PRIVATE decoder/BwavSource.cpp)
target_sources(player2_engine PRIVATE decoder/NintendoWaveSource.cpp decoder/ImaWaveSource.cpp decoder/MsAdpcmSource.cpp)
target_sources(player2_engine PRIVATE decoder/SwarSource.cpp decoder/RwarSource.cpp decoder/XwbSource.cpp)
target_sources(player2_engine PRIVATE decoder/AwbSource.cpp decoder/AcbSource.cpp)
target_sources(player2_engine PRIVATE decoder/Fsb5Source.cpp)
target_sources(player2_engine PRIVATE decoder/LegacyFsbSource.cpp)
target_sources(player2_engine PRIVATE decoder/AiffSource.cpp decoder/AiffImaSource.cpp)
target_sources(player2_engine PRIVATE decoder/FsbMpegSource.cpp decoder/FsbAdpcmSource.cpp)
target_sources(player2_engine PRIVATE decoder/BnkSource.cpp decoder/PckSource.cpp)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/clhca" "${CMAKE_CURRENT_BINARY_DIR}/hca-codec")
target_sources(player2_engine PRIVATE decoder/HcaSource.cpp)
target_link_libraries(player2_engine PRIVATE player2_hca_codec)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/opus" "${CMAKE_CURRENT_BINARY_DIR}/opus-codec")
target_sources(player2_engine PRIVATE decoder/OpusSource.cpp decoder/WwiseOpusSource.cpp)
target_link_libraries(player2_engine PRIVATE Opus::opus)
