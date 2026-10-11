cmake_minimum_required(VERSION 3.22.1)
project(apunta LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- whisper.cpp / ggml (vendored, see third_party/whisper.cpp/VENDORED.md) ---
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
# One .so per CPU feature level (armv8.0 ... armv9.2 on arm64, sse4.2 ... avx512 on
# x86_64). At runtime ggml loads the best one for the phone, so old Cortex-A53
# phones still work while recent ones get dotprod/fp16/i8mm kernels.
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)

add_subdirectory(third_party/whisper.cpp)

add_library(apunta SHARED apunta_jni.cpp)
target_link_libraries(apunta PRIVATE whisper ggml ggml-base)

if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(apunta PRIVATE ${LOG_LIB})
endif ()

# Google Play requires 16 KB page-size compatible native libraries.
foreach (t apunta whisper ggml ggml-base)
    if (TARGET ${t})
        target_link_options(${t} PRIVATE "-Wl,-z,max-page-size=16384")
    endif ()
endforeach ()

target_compile_options(apunta PRIVATE -Wall -Wextra -fstack-protector-strong)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(apunta PRIVATE -O2 -fvisibility=hidden)
endif ()
