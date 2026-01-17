/**
 * bsdiff.h
 * 
 * BsDiff 算法头文件 - 二进制差异生成
 * 
 * 基于 Colin Percival 的 bsdiff 算法实现
 * 使用 suffix sorting 进行高效差异计算
 * 
 * Requirements: 12.1, 12.4
 */

#ifndef BSDIFF_H
#define BSDIFF_H

#include <stdint.h>
#include <stddef.h>
#include "patch_engine.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * BsDiff 补丁文件格式
 * ============================================================================
 * 
 * Header (32 bytes):
 *   - Magic: "BSDIFF40" (8 bytes)
 *   - Control block size (8 bytes, little-endian)
 *   - Diff block size (8 bytes, little-endian)
 *   - New file size (8 bytes, little-endian)
 * 
 * Body (bzip2 compressed):
 *   - Control block
 *   - Diff block
 *   - Extra block
 */

#define BSDIFF_MAGIC "BSDIFF40"
#define BSDIFF_MAGIC_LEN 8
#define BSDIFF_HEADER_SIZE 32

/**
 * BsDiff 上下文结构
 */
typedef struct {
    uint8_t* oldData;       // 旧文件数据
    int64_t oldSize;        // 旧文件大小
    uint8_t* newData;       // 新文件数据
    int64_t newSize;        // 新文件大小
    int64_t* suffixArray;   // 后缀数组
    ProgressCallback callback;  // 进度回调
    void* userData;         // 用户数据
} BsDiffContext;

/**
 * 生成 BsDiff 补丁
 * 
 * @param oldData    旧文件数据
 * @param oldSize    旧文件大小
 * @param newData    新文件数据
 * @param newSize    新文件大小
 * @param patchPath  输出补丁文件路径
 * @param callback   进度回调（可为 NULL）
 * @param userData   用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bsdiff_generate(
    const uint8_t* oldData,
    int64_t oldSize,
    const uint8_t* newData,
    int64_t newSize,
    const char* patchPath,
    ProgressCallback callback,
    void* userData
);

/**
 * 从文件生成 BsDiff 补丁
 * 
 * @param oldPath    旧文件路径
 * @param newPath    新文件路径
 * @param patchPath  输出补丁文件路径
 * @param callback   进度回调（可为 NULL）
 * @param userData   用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bsdiff_generate_file(
    const char* oldPath,
    const char* newPath,
    const char* patchPath,
    ProgressCallback callback,
    void* userData
);

/**
 * 构建后缀数组
 * 
 * 使用 qsufsort 算法构建后缀数组，用于快速字符串匹配
 * 
 * @param data       输入数据
 * @param size       数据大小
 * @param suffixArray 输出后缀数组（大小为 size + 1）
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bsdiff_build_suffix_array(
    const uint8_t* data,
    int64_t size,
    int64_t* suffixArray
);

/**
 * 在后缀数组中搜索最长匹配
 * 
 * @param suffixArray  后缀数组
 * @param oldData      旧数据
 * @param oldSize      旧数据大小
 * @param newData      新数据（搜索目标）
 * @param newSize      新数据大小
 * @param start        搜索起始位置
 * @param end          搜索结束位置
 * @param matchPos     输出匹配位置
 * @return 匹配长度
 */
int64_t bsdiff_search(
    const int64_t* suffixArray,
    const uint8_t* oldData,
    int64_t oldSize,
    const uint8_t* newData,
    int64_t newSize,
    int64_t start,
    int64_t end,
    int64_t* matchPos
);

#ifdef __cplusplus
}
#endif

#endif /* BSDIFF_H */
