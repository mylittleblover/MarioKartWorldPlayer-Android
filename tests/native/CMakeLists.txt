cmake_minimum_required(VERSION 3.22)
project(jukekart_native_tests LANGUAGES C CXX)
get_filename_component(MKW_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
add_subdirectory("${MKW_ROOT}/engine/native" engine)

add_executable(test_native_audio_updates test_native_audio_updates.cpp)
target_link_libraries(test_native_audio_updates PRIVATE player2_engine Opus::opus)
target_compile_features(test_native_audio_updates PRIVATE cxx_std_17)
target_compile_options(test_native_audio_updates PRIVATE -Wall -Wextra -Wpedantic -Werror)
