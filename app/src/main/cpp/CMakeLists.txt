cmake_minimum_required(VERSION 3.18.1)
project("whisperdemo")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(whisper SHARED
    whisper-jni.cpp
    whisper.cpp
    ggml.c
)

find_library(log-lib log)

target_link_libraries(whisper ${log-lib})
