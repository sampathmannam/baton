cmake_minimum_required(VERSION 3.22.1)

project(baton_llama LANGUAGES C CXX)

# Baton JNI bridge for llama.cpp.
#
# Strategy: out-of-tree build against a vendored llama.cpp source tree.
# A Gradle task (see app/build.gradle.kts) downloads + extracts the
# pinned llama.cpp release tarball into app/src/main/cpp/llama-cpp/ if
# the directory is missing. The build is then a normal CMake
# add_subdirectory + target_link_libraries.
#
# If the vendoring approach fails on a developer's machine (e.g. NDK
# path issues on Windows), the fallback is to use the prebuilt
# libllama.so from ggerganov/llama.cpp releases and skip the
# add_subdirectory line.

# Quiet the deprecation noise from llama.cpp on newer NDKs.
add_compile_options(-Wno-deprecated-declarations -Wno-unused-function)

# Bring in llama.cpp from the vendored source tree.
add_subdirectory(llama-cpp)

# Our JNI bridge. Compiles to libbaton-llama.so which is bundled in
# the APK.
add_library(baton-llama SHARED
    llama_jni.cpp
)

target_include_directories(baton-llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    llama-cpp/include
)

# Pull in the llama + ggml static libs.
target_link_libraries(baton-llama PRIVATE
    llama
    ggml
    ggml-cpu
    ggml-base
    ${log-lib}
)

# JNI is part of the NDK; no extra find_library needed for the headers.
target_link_libraries(baton-llama PRIVATE log)
