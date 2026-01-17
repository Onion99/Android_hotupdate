/**
 * bspatch.c
 * 
 * BsPatch 算法实现 - 二进制补丁应用
 * 
 * 基于 Colin Percival 的 bspatch 算法
 * Copyright 2003-2005 Colin Percival
 * 
 * Requirements: 12.2, 12.6
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "bspatch.h"
#include "bsdiff.h"
#include "compress.h"

/* ============================================================================
 * 读取偏移量（小端序）
 * ============================================================================ */

static int64_t offtin(const uint8_t *buf) {
    int64_t y;

    y = buf[7] & 0x7F;
    y = y * 256; y += buf[6];
    y = y * 256; y += buf[5];
    y = y * 256; y += buf[4];
    y = y * 256; y += buf[3];
    y = y * 256; y += buf[2];
    y = y * 256; y += buf[1];
    y = y * 256; y += buf[0];

    if (buf[7] & 0x80) y = -y;

    return y;
}

/* ============================================================================
 * 验证补丁文件格式
 * ============================================================================ */

int bspatch_validate(const uint8_t* patchData, int64_t patchSize) {
    if (patchData == NULL || patchSize < BSDIFF_HEADER_SIZE) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    /* 检查魔数 */
    if (memcmp(patchData, BSDIFF_MAGIC, BSDIFF_MAGIC_LEN) != 0) {
        pe_set_error(PE_ERROR_CORRUPT_PATCH, "Invalid patch magic");
        return PE_ERROR_CORRUPT_PATCH;
    }
    
    /* 检查新文件大小是否合理 */
    int64_t newsize = offtin(patchData + 24);
    if (newsize < 0) {
        pe_set_error(PE_ERROR_CORRUPT_PATCH, "Invalid new file size in patch");
        return PE_ERROR_CORRUPT_PATCH;
    }
    
    return PE_SUCCESS;
}

/* ============================================================================
 * 获取新文件大小
 * ============================================================================ */

int bspatch_get_newsize(
    const uint8_t* patchData,
    int64_t patchSize,
    int64_t* newSize
) {
    if (patchData == NULL || newSize == NULL) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    int result = bspatch_validate(patchData, patchSize);
    if (result != PE_SUCCESS) {
        return result;
    }
    
    *newSize = offtin(patchData + 24);
    return PE_SUCCESS;
}


/* ============================================================================
 * 应用 BsPatch 补丁
 * ============================================================================ */

int bspatch_apply(
    const uint8_t* old,
    int64_t oldsize,
    const uint8_t* patch,
    int64_t patchsize,
    uint8_t* new,
    int64_t newsize,
    ProgressCallback callback,
    void* userData
) {
    int64_t ctrllen, difflen;
    int64_t oldpos, newpos;
    int64_t ctrl[3];
    int64_t i;
    const uint8_t *ctrlblock, *diffblock, *extrablock;
    
    /* 参数检查 */
    if (old == NULL || patch == NULL || new == NULL) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    /* 验证补丁格式 */
    int result = bspatch_validate(patch, patchsize);
    if (result != PE_SUCCESS) {
        return result;
    }
    
    /* 检查取消标志 */
    if (pe_is_cancelled()) {
        return PE_ERROR_CANCELLED;
    }
    
    /* 读取头部信息 */
    ctrllen = offtin(patch + 8);
    difflen = offtin(patch + 16);
    int64_t expectedNewsize = offtin(patch + 24);
    
    if (expectedNewsize != newsize) {
        pe_set_error(PE_ERROR_SIZE_MISMATCH, "New size mismatch");
        return PE_ERROR_SIZE_MISMATCH;
    }
    
    /* 检查块大小是否合理 */
    if (ctrllen < 0 || difflen < 0) {
        pe_set_error(PE_ERROR_CORRUPT_PATCH, "Invalid block sizes");
        return PE_ERROR_CORRUPT_PATCH;
    }
    
    /* 设置块指针 */
    ctrlblock = patch + BSDIFF_HEADER_SIZE;
    diffblock = patch + BSDIFF_HEADER_SIZE + ctrllen;
    extrablock = patch + BSDIFF_HEADER_SIZE + ctrllen + difflen;
    
    /* 应用补丁 */
    oldpos = 0;
    newpos = 0;
    const uint8_t *ctrlptr = ctrlblock;
    const uint8_t *diffptr = diffblock;
    const uint8_t *extraptr = extrablock;
    
    while (newpos < newsize) {
        /* 检查取消标志 */
        if (pe_is_cancelled()) {
            return PE_ERROR_CANCELLED;
        }
        
        /* 读取控制数据 */
        ctrl[0] = offtin(ctrlptr);
        ctrl[1] = offtin(ctrlptr + 8);
        ctrl[2] = offtin(ctrlptr + 16);
        ctrlptr += 24;
        
        /* 检查控制数据有效性 */
        if (ctrl[0] < 0 || ctrl[1] < 0) {
            pe_set_error(PE_ERROR_CORRUPT_PATCH, "Invalid control data");
            return PE_ERROR_CORRUPT_PATCH;
        }
        
        if (newpos + ctrl[0] > newsize) {
            pe_set_error(PE_ERROR_CORRUPT_PATCH, "Control data exceeds new size");
            return PE_ERROR_CORRUPT_PATCH;
        }
        
        /* 应用差异块 */
        for (i = 0; i < ctrl[0]; i++) {
            if (oldpos + i >= 0 && oldpos + i < oldsize) {
                new[newpos + i] = old[oldpos + i] + diffptr[i];
            } else {
                new[newpos + i] = diffptr[i];
            }
        }
        diffptr += ctrl[0];
        newpos += ctrl[0];
        oldpos += ctrl[0];
        
        /* 检查边界 */
        if (newpos + ctrl[1] > newsize) {
            pe_set_error(PE_ERROR_CORRUPT_PATCH, "Extra data exceeds new size");
            return PE_ERROR_CORRUPT_PATCH;
        }
        
        /* 复制额外块 */
        memcpy(new + newpos, extraptr, (size_t)ctrl[1]);
        extraptr += ctrl[1];
        newpos += ctrl[1];
        oldpos += ctrl[2];
        
        /* 更新进度 */
        if (callback && newsize > 0) {
            int64_t progress = (newpos * 100) / newsize;
            callback(progress, 100, userData);
        }
    }
    
    if (callback) callback(100, 100, userData);
    return PE_SUCCESS;
}


/* ============================================================================
 * 从文件应用 BsPatch 补丁
 * ============================================================================ */

int bspatch_apply_file(
    const char* oldPath,
    const char* patchPath,
    const char* newPath,
    ProgressCallback callback,
    void* userData
) {
    FILE *f;
    uint8_t *old, *patch, *new;
    int64_t oldsize, patchsize, newsize;
    int result;

    /* 参数检查 */
    if (oldPath == NULL || patchPath == NULL || newPath == NULL) {
        return PE_ERROR_INVALID_PARAM;
    }

    /* 读取旧文件 */
    f = fopen(oldPath, "rb");
    if (f == NULL) {
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open old file");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    fseek(f, 0, SEEK_END);
    oldsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    old = (uint8_t*)malloc((size_t)(oldsize + 1));
    if (old == NULL) {
        fclose(f);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    if (oldsize > 0 && fread(old, 1, (size_t)oldsize, f) != (size_t)oldsize) {
        fclose(f);
        free(old);
        return PE_ERROR_FILE_READ;
    }
    fclose(f);

    /* 读取补丁文件 */
    f = fopen(patchPath, "rb");
    if (f == NULL) {
        free(old);
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open patch file");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    fseek(f, 0, SEEK_END);
    patchsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    patch = (uint8_t*)malloc((size_t)(patchsize + 1));
    if (patch == NULL) {
        fclose(f);
        free(old);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    if (fread(patch, 1, (size_t)patchsize, f) != (size_t)patchsize) {
        fclose(f);
        free(old);
        free(patch);
        return PE_ERROR_FILE_READ;
    }
    fclose(f);

    /* 获取新文件大小 */
    result = bspatch_get_newsize(patch, patchsize, &newsize);
    if (result != PE_SUCCESS) {
        free(old);
        free(patch);
        return result;
    }

    /* 分配新文件缓冲区 */
    new = (uint8_t*)malloc((size_t)(newsize + 1));
    if (new == NULL) {
        free(old);
        free(patch);
        return PE_ERROR_OUT_OF_MEMORY;
    }

    /* 应用补丁 */
    result = bspatch_apply(old, oldsize, patch, patchsize, new, newsize, callback, userData);
    
    if (result == PE_SUCCESS) {
        /* 写入新文件 */
        f = fopen(newPath, "wb");
        if (f == NULL) {
            free(old);
            free(patch);
            free(new);
            pe_set_error(PE_ERROR_FILE_WRITE, "Cannot create new file");
            return PE_ERROR_FILE_WRITE;
        }
        if (newsize > 0 && fwrite(new, 1, (size_t)newsize, f) != (size_t)newsize) {
            fclose(f);
            free(old);
            free(patch);
            free(new);
            return PE_ERROR_FILE_WRITE;
        }
        fclose(f);
    }

    free(old);
    free(patch);
    free(new);
    return result;
}
