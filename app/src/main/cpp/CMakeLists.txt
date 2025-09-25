# app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.22)
project(whisper_android LANGUAGES C CXX)

# Standards
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- Library bauen ----
add_library(whisper_jni SHARED
    jni/whisper_bridge.cpp

    # whisper.cpp + ggml (aus dem vollständigen Repo-Ordner!)
    whisper.cpp/src/whisper.cpp
    whisper.cpp/ggml/src/ggml.c
    whisper.cpp/ggml/src/ggml-alloc.c
    whisper.cpp/ggml/src/ggml-backend.c
    whisper.cpp/ggml/src/ggml-quants.c
)

# Include-Pfade
target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/whisper.cpp
)

# Optionale Optimierungen/Flags (ok so)
target_compile_options(whisper_jni PRIVATE -O3)

# NDK-Logcat
find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
