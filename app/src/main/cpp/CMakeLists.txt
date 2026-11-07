cmake_minimum_required(VERSION 3.22)
project(mkw_player_audio LANGUAGES CXX)

# Debug APKs still execute a realtime callback. Keep symbols/assertions and
# strict diagnostics, but optimize this complete native subtree, including
# codec workers. Unoptimized Engine builds are for host diagnostics only.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O2)
endif()

get_filename_component(MKW_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
add_subdirectory("${MKW_ROOT}/engine/native" "${CMAKE_CURRENT_BINARY_DIR}/engine")
find_package(oboe REQUIRED CONFIG)

# Single native library exposing the PLAYER2 engine runtime to the app.
# There is exactly one transport, one mixer and one output owner; this library
# adds no scheduling logic of its own.
add_library(mkw_native SHARED
    platform/audio/native/jni/NativeAudioBridge.cpp
    platform/audio/native/jni/StreamSourceBridge.cpp
    platform/audio/native/jni/AudioSourceBridge.cpp
    platform/audio/native/jni/ModuleBridge.cpp
    platform/audio/native/jni/Mo3Bridge.cpp
    platform/audio/native/decode/NativeFileSourceInput.cpp
    platform/audio/native/jni/AudioOutputBridge.cpp
    platform/audio/native/oboe/NativeOutput.cpp)

target_link_libraries(mkw_native PRIVATE player2_engine oboe::oboe)
target_compile_features(mkw_native PRIVATE cxx_std_17)
target_compile_options(mkw_native PRIVATE -Wall -Wextra -Wpedantic -Werror)

# The NDK toolchain does not add libc++ to targets that mix C and C++ sources
# or that link a mixed-language static library. Add it explicitly, otherwise the
# link fails on __cxa_guard_acquire / operator new / to_string.
if(ANDROID)
    target_link_libraries(mkw_native PRIVATE c++_shared)
endif()
