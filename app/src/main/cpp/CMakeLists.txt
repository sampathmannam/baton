cmake_minimum_required(VERSION 3.22.1)

project(baton_native LANGUAGES C CXX)

# Baton JNI bridge for llama.cpp AND whisper.cpp.
#
# Strategy: out-of-tree builds against vendored source trees. Two
# Gradle tasks (vendorLlamaCpp + vendorWhisperCpp) download + extract
# the pinned releases into app/src/main/cpp/{llama-cpp,whisper-cpp}/.
# CMake's add_subdirectory() then compiles them as part of the
# externalNativeBuild.
#
# If the vendoring approach fails on a developer's machine (e.g. NDK
# path issues on Windows), the fallback is to use the prebuilt
# libllama.so / libwhisper.so from the ggerganov releases and skip
# the add_subdirectory line. See M1 plan Task 3 risks for the M1
# fallback precedent.

# Quiet the deprecation noise from llama.cpp / whisper.cpp on newer NDKs.
add_compile_options(-Wno-deprecated-declarations -Wno-unused-function)

# ----- llama.cpp (M1) -----
if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama-cpp/CMakeLists.txt")
    add_subdirectory(llama-cpp)
    add_library(baton-llama SHARED llama_jni.cpp)
    target_include_directories(baton-llama PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        llama-cpp/include
    )
    target_link_libraries(baton-llama PRIVATE
        llama
        ggml
        ggml-cpu
        ggml-base
        ${log-lib}
        log
    )
    message(STATUS "Baton: llama.cpp subdir present, building baton-llama")
else()
    message(WARNING "Baton: llama-cpp/ not vendored; skipping baton-llama (M1 features will be unavailable)")
endif()

# ----- whisper.cpp (M2-T3) -----
if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper-cpp/CMakeLists.txt")
    # whisper.cpp builds a single static lib named "whisper".
    add_subdirectory(whisper-cpp)
    add_library(baton-whisper SHARED whisper_jni.cpp)
    target_include_directories(baton-whisper PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        whisper-cpp
    )
    target_link_libraries(baton-whisper PRIVATE
        whisper
        ${log-lib}
        log
    )
    message(STATUS "Baton: whisper.cpp subdir present, building baton-whisper")
else()
    message(WARNING "Baton: whisper-cpp/ not vendored; skipping baton-whisper (M2 voice features will be unavailable)")
endif()
