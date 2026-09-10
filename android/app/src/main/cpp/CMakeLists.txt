cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/llama.cpp llama-build)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/whisper.cpp whisper-build)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_include_directories(whisper_jni PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/whisper.cpp/include)
target_link_libraries(whisper_jni PRIVATE whisper log android)

add_library(llama_jni SHARED llama_jni.cpp)
target_include_directories(llama_jni PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/llama.cpp/include)
target_link_libraries(llama_jni PRIVATE llama log android)
