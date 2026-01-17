/**
 * compress.h
 * 
 * 压缩/解压缩头文件 - bzip2 支持
 * 
 * Requirements: 12.5
 */

#ifndef COMPRESS_H
#define COMPRESS_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 压缩级别
 * ============================================================================ */

#define COMPRESS_LEVEL_FAST    1
#define COMPRESS_LEVEL_DEFAULT 6
#define COMPRESS_LEVEL_BEST    9

/* ============================================================================
 * bzip2 压缩/解压
 * ============================================================================ */

/**
 * 使用 bzip2 压缩数据
 * 
 * @param src       源数据
 * @param srcSize   源数据大小
 * @param dst       目标缓冲区
 * @param dstSize   目标缓冲区大小（输入），实际压缩大小（输出）
 * @param level     压缩级别 (1-9)
 * @return 0 成功，非 0 失败
 */
int compress_bzip2(
    const void* src,
    size_t srcSize,
    void* dst,
    size_t* dstSize,
    int level
);

/**
 * 使用 bzip2 解压数据
 * 
 * @param src       源数据（压缩数据）
 * @param srcSize   源数据大小
 * @param dst       目标缓冲区
 * @param dstSize   目标缓冲区大小（输入），实际解压大小（输出）
 * @return 0 成功，非 0 失败
 */
int decompress_bzip2(
    const void* src,
    size_t srcSize,
    void* dst,
    size_t* dstSize
);

/**
 * 估算 bzip2 压缩后的最大大小
 * 
 * @param srcSize   源数据大小
 * @return 估算的最大压缩大小
 */
size_t compress_bzip2_bound(size_t srcSize);

/**
 * 使用流式 bzip2 压缩写入文件
 * 
 * @param src       源数据
 * @param srcSize   源数据大小
 * @param filePath  输出文件路径
 * @param level     压缩级别 (1-9)
 * @return 0 成功，非 0 失败
 */
int compress_bzip2_to_file(
    const void* src,
    size_t srcSize,
    const char* filePath,
    int level
);

/**
 * 从文件流式 bzip2 解压
 * 
 * @param filePath  输入文件路径
 * @param dst       目标缓冲区
 * @param dstSize   目标缓冲区大小（输入），实际解压大小（输出）
 * @return 0 成功，非 0 失败
 */
int decompress_bzip2_from_file(
    const char* filePath,
    void* dst,
    size_t* dstSize
);

#ifdef __cplusplus
}
#endif

#endif /* COMPRESS_H */
