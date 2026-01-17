# CMakeLists.txt for Patch Engine Native Library
# 支持多架构编译: ARM64, ARMv7, x86, x86_64

cmake_minimum_required(VERSION 3.22.1)

project("patchengine" VERSION 1.0.0 LANGUAGES C CXX)

# 设置 C/C++ 标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 编译选项
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fexceptions")

# Release 优化
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# 头文件目录
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/bzip2
)

# bzip2 源文件
set(BZIP2_SOURCES
    third_party/bzip2/blocksort.c
    third_party/bzip2/huffman.c
    third_party/bzip2/crctable.c
    third_party/bzip2/randtable.c
    third_party/bzip2/compress.c
    third_party/bzip2/decompress.c
    third_party/bzip2/bzlib.c
)

# 核心引擎源文件
set(ENGINE_SOURCES
    src/patch_engine.cpp
    src/patch_engine_jni.cpp
    src/bsdiff.c
    src/bspatch.c
    src/hash.cpp
    src/compress.cpp
)

# 创建共享库
add_library(patchengine SHARED
    ${BZIP2_SOURCES}
    ${ENGINE_SOURCES}
)

# 查找 Android 日志库
find_library(log-lib log)

# 链接库
target_link_libraries(patchengine
    ${log-lib}
)

# 设置库属性
set_target_properties(patchengine PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
)

# 导出符号
target_compile_definitions(patchengine PRIVATE
    PATCH_ENGINE_EXPORTS
)
