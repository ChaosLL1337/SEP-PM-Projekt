cmake_minimum_required(VERSION 3.22)
project(whisper_android C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release)

add_compile_options(-O3 -ffast-math -fvisibility=hidden -fPIC)

# === Quellen aus whisper.cpp ===
# Passe die Liste an deine Struktur an:
add_library(whisper STATIC
    ggml.c
    ggml-alloc.c
    ggml-backend.c
    ggml-quants.c
    whisper.cpp
)

target_include_directories(whisper PUBLIC ${CMAKE_CURRENT_LIST_DIR})

# JNI-Bridge
add_library(whisper_bridge SHARED
    whisper_bridge.cpp
)

find_library(log-lib log)
target_link_libraries(whisper_bridge whisper ${log-lib})
