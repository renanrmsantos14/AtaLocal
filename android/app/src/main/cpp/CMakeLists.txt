cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/whisper.cpp whisper-build)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_include_directories(whisper_jni PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/whisper.cpp/include)
target_link_libraries(whisper_jni PRIVATE whisper log android)
