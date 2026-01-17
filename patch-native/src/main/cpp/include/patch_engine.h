/**
 * patch_engine.h
 * 
 * Patch Engine Native Library - 补丁引擎核心头文件
 * 
 * 提供高性能的二进制差异生成和应用功能，支持:
 * - BsDiff/BsPatch 算法
 * - MD5/SHA256 哈希计算
 * - 进度回调和取消机制
 * 
 * Requirements: 10.1, 10.5, 14.4
 */

#ifndef PATCH_ENGINE_H
#define PATCH_ENGINE_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 版本信息
 * ============================================================================ */

#define PATCH_ENGINE_VERSION_MAJOR 1
#define PATCH_ENGINE_VERSION_MINOR 0
#define PATCH_ENGINE_VERSION_PATCH 0
#define PATCH_ENGINE_VERSION_STRING "1.0.0"

/* ============================================================================
 * 错误码定义
 * ============================================================================ */

typedef enum {
    PE_SUCCESS = 0,                    // 操作成功
    PE_ERROR_FILE_NOT_FOUND = -1,      // 文件未找到
    PE_ERROR_FILE_READ = -2,           // 文件读取失败
    PE_ERROR_FILE_WRITE = -3,          // 文件写入失败
    PE_ERROR_OUT_OF_MEMORY = -4,       // 内存不足
    PE_ERROR_INVALID_PARAM = -5,       // 无效参数
    PE_ERROR_CANCELLED = -6,           // 操作已取消
    PE_ERROR_CORRUPT_PATCH = -7,       // 补丁文件损坏
    PE_ERROR_COMPRESS_FAILED = -8,     // 压缩失败
    PE_ERROR_DECOMPRESS_FAILED = -9,   // 解压失败
    PE_ERROR_HASH_FAILED = -10,        // 哈希计算失败
    PE_ERROR_SIZE_MISMATCH = -11,      // 大小不匹配
    PE_ERROR_CHECKSUM_MISMATCH = -12,  // 校验和不匹配
    PE_ERROR_INTERNAL = -99            // 内部错误
} PatchEngineError;

/* ============================================================================
 * 回调函数类型定义
 * ============================================================================ */

/**
 * 进度回调函数
 * 
 * @param current   当前进度值
 * @param total     总进度值
 * @param userData  用户自定义数据指针
 */
typedef void (*ProgressCallback)(int64_t current, int64_t total, void* userData);

/* ============================================================================
 * 引擎初始化和释放
 * ============================================================================ */

/**
 * 初始化补丁引擎
 * 
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int pe_init(void);

/**
 * 释放补丁引擎资源
 */
void pe_release(void);

/**
 * 获取引擎版本字符串
 * 
 * @return 版本字符串
 */
const char* pe_get_version(void);

/**
 * 检查引擎是否已初始化
 * 
 * @return true 已初始化，false 未初始化
 */
bool pe_is_initialized(void);

/* ============================================================================
 * BsDiff/BsPatch 操作
 * ============================================================================ */

/**
 * 生成二进制差异补丁
 * 
 * 使用 BsDiff 算法比较两个文件并生成差异补丁
 * 
 * @param oldFilePath    旧文件路径
 * @param newFilePath    新文件路径
 * @param patchFilePath  输出补丁文件路径
 * @param callback       进度回调函数（可为 NULL）
 * @param userData       回调函数用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 12.1, 12.4
 */
int pe_generate_diff(
    const char* oldFilePath,
    const char* newFilePath,
    const char* patchFilePath,
    ProgressCallback callback,
    void* userData
);

/**
 * 应用二进制差异补丁
 * 
 * 使用 BsPatch 算法将补丁应用到旧文件生成新文件
 * 
 * @param oldFilePath    旧文件路径
 * @param patchFilePath  补丁文件路径
 * @param newFilePath    输出新文件路径
 * @param callback       进度回调函数（可为 NULL）
 * @param userData       回调函数用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 12.2, 12.6
 */
int pe_apply_patch(
    const char* oldFilePath,
    const char* patchFilePath,
    const char* newFilePath,
    ProgressCallback callback,
    void* userData
);

/* ============================================================================
 * 哈希计算
 * ============================================================================ */

/**
 * 计算文件 MD5 哈希值
 * 
 * @param filePath  文件路径
 * @param outHash   输出哈希字符串缓冲区（至少 33 字节）
 * @param outSize   缓冲区大小
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 10.7
 */
int pe_calculate_md5(const char* filePath, char* outHash, size_t outSize);

/**
 * 计算文件 SHA256 哈希值
 * 
 * @param filePath  文件路径
 * @param outHash   输出哈希字符串缓冲区（至少 65 字节）
 * @param outSize   缓冲区大小
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 10.7
 */
int pe_calculate_sha256(const char* filePath, char* outHash, size_t outSize);

/**
 * 计算内存数据 MD5 哈希值
 * 
 * @param data      数据指针
 * @param dataSize  数据大小
 * @param outHash   输出哈希字符串缓冲区（至少 33 字节）
 * @param outSize   缓冲区大小
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int pe_calculate_md5_memory(const void* data, size_t dataSize, char* outHash, size_t outSize);

/**
 * 计算内存数据 SHA256 哈希值
 * 
 * @param data      数据指针
 * @param dataSize  数据大小
 * @param outHash   输出哈希字符串缓冲区（至少 65 字节）
 * @param outSize   缓冲区大小
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int pe_calculate_sha256_memory(const void* data, size_t dataSize, char* outHash, size_t outSize);

/* ============================================================================
 * 取消操作
 * ============================================================================ */

/**
 * 取消当前正在进行的操作
 * 
 * 设置取消标志，正在进行的操作会在下一个检查点返回 PE_ERROR_CANCELLED
 * 
 * Requirements: 10.9
 */
void pe_cancel(void);

/**
 * 重置取消标志
 * 
 * 在开始新操作前调用以清除之前的取消状态
 */
void pe_reset_cancel(void);

/**
 * 检查是否已请求取消
 * 
 * @return true 已请求取消，false 未请求取消
 */
bool pe_is_cancelled(void);

/* ============================================================================
 * 错误处理
 * ============================================================================ */

/**
 * 获取最后一次错误的描述信息
 * 
 * @return 错误描述字符串
 */
const char* pe_get_last_error(void);

/**
 * 设置错误信息
 * 
 * @param errorCode  错误码
 * @param message    错误信息
 */
void pe_set_error(int errorCode, const char* message);

/**
 * 获取错误码对应的描述
 * 
 * @param errorCode  错误码
 * @return 错误描述字符串
 */
const char* pe_error_to_string(int errorCode);

/* ============================================================================
 * 压缩/解压缩
 * ============================================================================ */

/**
 * 使用 bzip2 压缩数据
 * 
 * @param src       源数据
 * @param srcSize   源数据大小
 * @param dst       目标缓冲区
 * @param dstSize   目标缓冲区大小（输入/输出）
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 12.5
 */
int pe_compress_bzip2(const void* src, size_t srcSize, void* dst, size_t* dstSize);

/**
 * 使用 bzip2 解压数据
 * 
 * @param src       源数据
 * @param srcSize   源数据大小
 * @param dst       目标缓冲区
 * @param dstSize   目标缓冲区大小（输入/输出）
 * @return PE_SUCCESS 成功，其他值表示错误
 * 
 * Requirements: 12.5
 */
int pe_decompress_bzip2(const void* src, size_t srcSize, void* dst, size_t* dstSize);

#ifdef __cplusplus
}
#endif

#endif /* PATCH_ENGINE_H */
