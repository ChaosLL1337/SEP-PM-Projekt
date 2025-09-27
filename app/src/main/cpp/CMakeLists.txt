cmake_minimum_required(VERSION 3.22)
project(whisper_android LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Nur deine JNI-Bridge kompilieren
add_library(whisper_jni SHARED
        jni/whisper_bridge.cpp
)

# Das Upstream-Repo whisper.cpp als Subprojekt einbinden
# (Dessen eigenes CMake kümmert sich um ggml/whisper-Sources!)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/whisper.cpp)

# Includes setzen, damit "whisper.h" gefunden wird
target_include_directories(whisper_jni PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/whisper.cpp
        ${CMAKE_CURRENT_LIST_DIR}/whisper.cpp/include
)

# Optimierung + Android-Log
target_compile_options(whisper_jni PRIVATE -O3)
find_library(log-lib log)

# Gegen die Upstream-Lib "whisper" linken
target_link_libraries(whisper_jni PRIVATE whisper ${log-lib})