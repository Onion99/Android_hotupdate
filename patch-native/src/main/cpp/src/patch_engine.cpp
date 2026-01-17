/**
 * patch_engine.cpp
 * 
 * Patch Engine 主实现文件
 * 
 * 提供引擎初始化、错误处理、进度回调和取消机制
 * 
 * Requirements: 10.8, 10.9
 */

#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <atomic>
#include <mutex>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "PatchEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

extern "C" {
#include "patch_engine.h"
#include "bsdiff.h"
#include "bspatch.h"
}

/* ============================================================================
 * 全局状态
 * ============================================================================ */

static std::atomic<bool> g_initialized(false);
static std::atomic<bool> g_cancelled(false);
static std::mutex g_errorMutex;
static char g_lastError[512] = {0};
static int g_lastErrorCode = PE_SUCCESS;

/* ============================================================================
 * 引擎初始化和释放
 * ============================================================================ */

int pe_init(void) {
    if (g_initialized.load()) {
        return PE_SUCCESS;
    }
    
    g_cancelled.store(false);
    g_lastErrorCode = PE_SUCCESS;
    g_lastError[0] = '\0';
    g_initialized.store(true);
    
    LOGI("Patch Engine %s initialized\n", PATCH_ENGINE_VERSION_STRING);
    return PE_SUCCESS;
}

void pe_release(void) {
    if (!g_initialized.load()) {
        return;
    }
    
    g_cancelled.store(false);
    g_initialized.store(false);
    
    LOGI("Patch Engine released\n");
}

const char* pe_get_version(void) {
    return PATCH_ENGINE_VERSION_STRING;
}

bool pe_is_initialized(void) {
    return g_initialized.load();
}

/* ============================================================================
 * 取消操作
 * ============================================================================ */

void pe_cancel(void) {
    g_cancelled.store(true);
    LOGI("Patch Engine: cancel requested\n");
}

void pe_reset_cancel(void) {
    g_cancelled.store(false);
}

bool pe_is_cancelled(void) {
    return g_cancelled.load();
}

/* ============================================================================
 * 错误处理
 * ============================================================================ */

const char* pe_get_last_error(void) {
    std::lock_guard<std::mutex> lock(g_errorMutex);
    return g_lastError;
}

void pe_set_error(int errorCode, const char* message) {
    std::lock_guard<std::mutex> lock(g_errorMutex);
    g_lastErrorCode = errorCode;
    if (message != nullptr) {
        strncpy(g_lastError, message, sizeof(g_lastError) - 1);
        g_lastError[sizeof(g_lastError) - 1] = '\0';
    } else {
        g_lastError[0] = '\0';
    }
    LOGE("Patch Engine error %d: %s\n", errorCode, g_lastError);
}

const char* pe_error_to_string(int errorCode) {
    switch (errorCode) {
        case PE_SUCCESS:
            return "Success";
        case PE_ERROR_FILE_NOT_FOUND:
            return "File not found";
        case PE_ERROR_FILE_READ:
            return "File read error";
        case PE_ERROR_FILE_WRITE:
            return "File write error";
        case PE_ERROR_OUT_OF_MEMORY:
            return "Out of memory";
        case PE_ERROR_INVALID_PARAM:
            return "Invalid parameter";
        case PE_ERROR_CANCELLED:
            return "Operation cancelled";
        case PE_ERROR_CORRUPT_PATCH:
            return "Corrupt patch file";
        case PE_ERROR_COMPRESS_FAILED:
            return "Compression failed";
        case PE_ERROR_DECOMPRESS_FAILED:
            return "Decompression failed";
        case PE_ERROR_HASH_FAILED:
            return "Hash calculation failed";
        case PE_ERROR_SIZE_MISMATCH:
            return "Size mismatch";
        case PE_ERROR_CHECKSUM_MISMATCH:
            return "Checksum mismatch";
        case PE_ERROR_INTERNAL:
            return "Internal error";
        default:
            return "Unknown error";
    }
}


/* ============================================================================
 * BsDiff/BsPatch 包装函数
 * ============================================================================ */

int pe_generate_diff(
    const char* oldFilePath,
    const char* newFilePath,
    const char* patchFilePath,
    ProgressCallback callback,
    void* userData
) {
    if (!g_initialized.load()) {
        pe_set_error(PE_ERROR_INTERNAL, "Engine not initialized");
        return PE_ERROR_INTERNAL;
    }
    
    pe_reset_cancel();
    
    int result = bsdiff_generate_file(oldFilePath, newFilePath, patchFilePath, callback, userData);
    
    if (result != PE_SUCCESS) {
        LOGE("pe_generate_diff failed: %s\n", pe_error_to_string(result));
    }
    
    return result;
}

int pe_apply_patch(
    const char* oldFilePath,
    const char* patchFilePath,
    const char* newFilePath,
    ProgressCallback callback,
    void* userData
) {
    if (!g_initialized.load()) {
        pe_set_error(PE_ERROR_INTERNAL, "Engine not initialized");
        return PE_ERROR_INTERNAL;
    }
    
    pe_reset_cancel();
    
    int result = bspatch_apply_file(oldFilePath, patchFilePath, newFilePath, callback, userData);
    
    if (result != PE_SUCCESS) {
        LOGE("pe_apply_patch failed: %s\n", pe_error_to_string(result));
    }
    
    return result;
}
