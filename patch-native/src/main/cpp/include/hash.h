/**
 * hash.h
 * 
 * 哈希计算头文件 - MD5 和 SHA256
 * 
 * 支持文件和内存数据的哈希计算
 * 使用内存映射优化大文件处理
 * 
 * Requirements: 10.7
 */

#ifndef HASH_H
#define HASH_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * 常量定义
 * ============================================================================ */

#define MD5_DIGEST_LENGTH 16
#define MD5_STRING_LENGTH 33      // 32 hex chars + null terminator

#define SHA256_DIGEST_LENGTH 32
#define SHA256_STRING_LENGTH 65   // 64 hex chars + null terminator

/* ============================================================================
 * MD5 计算
 * ============================================================================ */

/**
 * MD5 上下文结构
 */
typedef struct {
    uint32_t state[4];      // 状态 (ABCD)
    uint32_t count[2];      // 位数计数
    uint8_t buffer[64];     // 输入缓冲区
} MD5Context;

/**
 * 初始化 MD5 上下文
 */
void md5_init(MD5Context* ctx);

/**
 * 更新 MD5 计算
 */
void md5_update(MD5Context* ctx, const uint8_t* data, size_t len);

/**
 * 完成 MD5 计算
 */
void md5_final(MD5Context* ctx, uint8_t digest[MD5_DIGEST_LENGTH]);

/**
 * 计算数据的 MD5 哈希
 * 
 * @param data    输入数据
 * @param len     数据长度
 * @param digest  输出摘要（16 字节）
 */
void md5_compute(const uint8_t* data, size_t len, uint8_t digest[MD5_DIGEST_LENGTH]);

/**
 * 计算数据的 MD5 哈希并转换为十六进制字符串
 * 
 * @param data    输入数据
 * @param len     数据长度
 * @param hexStr  输出十六进制字符串（至少 33 字节）
 */
void md5_compute_hex(const uint8_t* data, size_t len, char hexStr[MD5_STRING_LENGTH]);

/* ============================================================================
 * SHA256 计算
 * ============================================================================ */

/**
 * SHA256 上下文结构
 */
typedef struct {
    uint32_t state[8];      // 状态
    uint64_t count;         // 位数计数
    uint8_t buffer[64];     // 输入缓冲区
} SHA256Context;

/**
 * 初始化 SHA256 上下文
 */
void sha256_init(SHA256Context* ctx);

/**
 * 更新 SHA256 计算
 */
void sha256_update(SHA256Context* ctx, const uint8_t* data, size_t len);

/**
 * 完成 SHA256 计算
 */
void sha256_final(SHA256Context* ctx, uint8_t digest[SHA256_DIGEST_LENGTH]);

/**
 * 计算数据的 SHA256 哈希
 * 
 * @param data    输入数据
 * @param len     数据长度
 * @param digest  输出摘要（32 字节）
 */
void sha256_compute(const uint8_t* data, size_t len, uint8_t digest[SHA256_DIGEST_LENGTH]);

/**
 * 计算数据的 SHA256 哈希并转换为十六进制字符串
 * 
 * @param data    输入数据
 * @param len     数据长度
 * @param hexStr  输出十六进制字符串（至少 65 字节）
 */
void sha256_compute_hex(const uint8_t* data, size_t len, char hexStr[SHA256_STRING_LENGTH]);

/* ============================================================================
 * 工具函数
 * ============================================================================ */

/**
 * 将二进制摘要转换为十六进制字符串
 * 
 * @param digest     二进制摘要
 * @param digestLen  摘要长度
 * @param hexStr     输出十六进制字符串
 * @param hexStrLen  字符串缓冲区长度
 */
void hash_to_hex(const uint8_t* digest, size_t digestLen, char* hexStr, size_t hexStrLen);

#ifdef __cplusplus
}
#endif

#endif /* HASH_H */
