/**
 * bspatch.h
 * 
 * BsPatch 算法头文件 - 二进制补丁应用
 * 
 * 基于 Colin Percival 的 bspatch 算法实现
 * 
 * Requirements: 12.2, 12.6
 */

#ifndef BSPATCH_H
#define BSPATCH_H

#include <stdint.h>
#include <stddef.h>
#include "patch_engine.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * BsPatch 上下文结构
 */
typedef struct {
    uint8_t* oldData;       // 旧文件数据
    int64_t oldSize;        // 旧文件大小
    uint8_t* patchData;     // 补丁数据
    int64_t patchSize;      // 补丁大小
    int64_t newSize;        // 新文件大小（从补丁头读取）
    ProgressCallback callback;  // 进度回调
    void* userData;         // 用户数据
} BsPatchContext;

/**
 * 应用 BsPatch 补丁
 * 
 * @param oldData    旧文件数据
 * @param oldSize    旧文件大小
 * @param patchData  补丁数据
 * @param patchSize  补丁大小
 * @param newData    输出新文件数据缓冲区
 * @param newSize    新文件大小（从补丁头获取）
 * @param callback   进度回调（可为 NULL）
 * @param userData   用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bspatch_apply(
    const uint8_t* oldData,
    int64_t oldSize,
    const uint8_t* patchData,
    int64_t patchSize,
    uint8_t* newData,
    int64_t newSize,
    ProgressCallback callback,
    void* userData
);

/**
 * 从文件应用 BsPatch 补丁
 * 
 * @param oldPath    旧文件路径
 * @param patchPath  补丁文件路径
 * @param newPath    输出新文件路径
 * @param callback   进度回调（可为 NULL）
 * @param userData   用户数据（可为 NULL）
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bspatch_apply_file(
    const char* oldPath,
    const char* patchPath,
    const char* newPath,
    ProgressCallback callback,
    void* userData
);

/**
 * 读取补丁文件头获取新文件大小
 * 
 * @param patchData  补丁数据
 * @param patchSize  补丁大小
 * @param newSize    输出新文件大小
 * @return PE_SUCCESS 成功，其他值表示错误
 */
int bspatch_get_newsize(
    const uint8_t* patchData,
    int64_t patchSize,
    int64_t* newSize
);

/**
 * 验证补丁文件格式
 * 
 * @param patchData  补丁数据
 * @param patchSize  补丁大小
 * @return PE_SUCCESS 格式有效，其他值表示错误
 */
int bspatch_validate(
    const uint8_t* patchData,
    int64_t patchSize
);

#ifdef __cplusplus
}
#endif

#endif /* BSPATCH_H */
