/**
 * patch_engine_jni.cpp
 * 
 * JNI 桥接层实现
 * 
 * 连接 Java 和 Native 代码，提供：
 * - Java 回调到 Native 的转换
 * - 错误码到异常的转换
 * - 内存管理和资源清理
 * 
 * Requirements: 11.1-11.10
 */

#include <jni.h>
#include <string>
#include <cstring>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "PatchEngineJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) printf(__VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#define LOGD(...) printf(__VA_ARGS__)
#endif

extern "C" {
#include "patch_engine.h"
}

/* ============================================================================
 * JNI 回调结构 (Requirements: 11.6)
 * ============================================================================ */

/**
 * JNI 回调数据结构
 * 用于在 Native 回调中调用 Java 方法
 */
struct JNICallbackData {
    JNIEnv* env;
    jobject callback;
    jmethodID onProgressMethod;
    bool hasException;  // 标记是否发生异常
};

/**
 * JNI 进度回调函数
 * 将 Native 进度回调转换为 Java 回调
 */
static void jni_progress_callback(int64_t current, int64_t total, void* userData) {
    JNICallbackData* data = static_cast<JNICallbackData*>(userData);
    if (data == nullptr || data->callback == nullptr || data->hasException) {
        return;
    }
    
    JNIEnv* env = data->env;
    if (env == nullptr) {
        return;
    }
    
    // 检查是否有待处理的异常
    if (env->ExceptionCheck()) {
        data->hasException = true;
        return;
    }
    
    // 调用 Java 回调方法
    env->CallVoidMethod(data->callback, data->onProgressMethod, 
                        static_cast<jlong>(current), static_cast<jlong>(total));
    
    // 检查回调是否抛出异常
    if (env->ExceptionCheck()) {
        data->hasException = true;
        LOGE("Exception occurred in progress callback\n");
    }
}

/* ============================================================================
 * 辅助函数
 * ============================================================================ */

/**
 * 安全获取 UTF 字符串
 * 返回 nullptr 如果获取失败
 */
static const char* safeGetStringUTFChars(JNIEnv* env, jstring str, jboolean* isCopy) {
    if (str == nullptr) {
        return nullptr;
    }
    return env->GetStringUTFChars(str, isCopy);
}

/**
 * 安全释放 UTF 字符串
 */
static void safeReleaseStringUTFChars(JNIEnv* env, jstring str, const char* chars) {
    if (str != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(str, chars);
    }
}

/**
 * 初始化回调数据结构
 * 
 * @param env       JNI 环境
 * @param callback  Java 回调对象
 * @param data      输出回调数据
 * @return true 如果初始化成功，false 如果失败
 */
static bool initCallbackData(JNIEnv* env, jobject callback, JNICallbackData* data) {
    data->env = nullptr;
    data->callback = nullptr;
    data->onProgressMethod = nullptr;
    data->hasException = false;
    
    if (callback == nullptr) {
        return true;  // 没有回调也是有效的
    }
    
    jclass callbackClass = env->GetObjectClass(callback);
    if (callbackClass == nullptr) {
        LOGE("Failed to get callback class\n");
        return false;
    }
    
    jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(JJ)V");
    if (onProgressMethod == nullptr) {
        LOGE("Failed to find onProgress method\n");
        env->ExceptionClear();  // 清除 NoSuchMethodError
        return false;
    }
    
    data->env = env;
    data->callback = callback;
    data->onProgressMethod = onProgressMethod;
    
    return true;
}

/* ============================================================================
 * JNI 函数实现 (Requirements: 11.2-11.10)
 * ============================================================================ */

extern "C" {

/**
 * 初始化引擎 (Requirements: 11.2)
 */
JNIEXPORT jboolean JNICALL
Java_com_orange_patchnative_NativePatchEngine_init(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    
    LOGD("NativePatchEngine.init() called\n");
    int result = pe_init();
    
    if (result == PE_SUCCESS) {
        LOGI("Patch engine initialized successfully\n");
        return JNI_TRUE;
    } else {
        LOGE("Failed to initialize patch engine: %s\n", pe_error_to_string(result));
        return JNI_FALSE;
    }
}

/**
 * 释放引擎资源 (Requirements: 11.8)
 */
JNIEXPORT void JNICALL
Java_com_orange_patchnative_NativePatchEngine_release(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    
    LOGD("NativePatchEngine.release() called\n");
    pe_release();
}

/**
 * 获取引擎版本
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchnative_NativePatchEngine_getVersion(JNIEnv* env, jobject thiz) {
    (void)thiz;
    
    const char* version = pe_get_version();
    return env->NewStringUTF(version);
}

/**
 * 检查引擎是否已初始化
 */
JNIEXPORT jboolean JNICALL
Java_com_orange_patchnative_NativePatchEngine_isInitialized(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    
    return pe_is_initialized() ? JNI_TRUE : JNI_FALSE;
}

/**
 * 生成二进制差异补丁 (Requirements: 11.3, 11.6)
 */
JNIEXPORT jint JNICALL
Java_com_orange_patchnative_NativePatchEngine_generateDiff(
    JNIEnv* env,
    jobject thiz,
    jstring oldFilePath,
    jstring newFilePath,
    jstring patchFilePath,
    jobject callback
) {
    (void)thiz;
    
    LOGD("NativePatchEngine.generateDiff() called\n");
    
    // 获取字符串参数
    const char* oldPath = safeGetStringUTFChars(env, oldFilePath, nullptr);
    const char* newPath = safeGetStringUTFChars(env, newFilePath, nullptr);
    const char* patchPath = safeGetStringUTFChars(env, patchFilePath, nullptr);
    
    // 检查参数有效性
    if (oldPath == nullptr || newPath == nullptr || patchPath == nullptr) {
        safeReleaseStringUTFChars(env, oldFilePath, oldPath);
        safeReleaseStringUTFChars(env, newFilePath, newPath);
        safeReleaseStringUTFChars(env, patchFilePath, patchPath);
        LOGE("Invalid parameters: null path\n");
        return PE_ERROR_INVALID_PARAM;
    }
    
    // 初始化回调
    JNICallbackData callbackData;
    ProgressCallback progressCallback = nullptr;
    
    if (initCallbackData(env, callback, &callbackData)) {
        if (callback != nullptr) {
            progressCallback = jni_progress_callback;
        }
    }
    
    // 执行差异生成
    LOGI("Generating diff: %s -> %s => %s\n", oldPath, newPath, patchPath);
    int result = pe_generate_diff(oldPath, newPath, patchPath, progressCallback, &callbackData);
    
    // 释放字符串
    safeReleaseStringUTFChars(env, oldFilePath, oldPath);
    safeReleaseStringUTFChars(env, newFilePath, newPath);
    safeReleaseStringUTFChars(env, patchFilePath, patchPath);
    
    // 检查回调是否发生异常
    if (callbackData.hasException) {
        LOGE("Exception occurred during diff generation\n");
        return PE_ERROR_CANCELLED;
    }
    
    if (result != PE_SUCCESS) {
        LOGE("Diff generation failed: %s\n", pe_error_to_string(result));
    } else {
        LOGI("Diff generation completed successfully\n");
    }
    
    return result;
}

/**
 * 应用二进制差异补丁 (Requirements: 11.4, 11.6)
 */
JNIEXPORT jint JNICALL
Java_com_orange_patchnative_NativePatchEngine_applyPatch(
    JNIEnv* env,
    jobject thiz,
    jstring oldFilePath,
    jstring patchFilePath,
    jstring newFilePath,
    jobject callback
) {
    (void)thiz;
    
    LOGD("NativePatchEngine.applyPatch() called\n");
    
    // 获取字符串参数
    const char* oldPath = safeGetStringUTFChars(env, oldFilePath, nullptr);
    const char* patchPath = safeGetStringUTFChars(env, patchFilePath, nullptr);
    const char* newPath = safeGetStringUTFChars(env, newFilePath, nullptr);
    
    // 检查参数有效性
    if (oldPath == nullptr || patchPath == nullptr || newPath == nullptr) {
        safeReleaseStringUTFChars(env, oldFilePath, oldPath);
        safeReleaseStringUTFChars(env, patchFilePath, patchPath);
        safeReleaseStringUTFChars(env, newFilePath, newPath);
        LOGE("Invalid parameters: null path\n");
        return PE_ERROR_INVALID_PARAM;
    }
    
    // 初始化回调
    JNICallbackData callbackData;
    ProgressCallback progressCallback = nullptr;
    
    if (initCallbackData(env, callback, &callbackData)) {
        if (callback != nullptr) {
            progressCallback = jni_progress_callback;
        }
    }
    
    // 执行补丁应用
    LOGI("Applying patch: %s + %s => %s\n", oldPath, patchPath, newPath);
    int result = pe_apply_patch(oldPath, patchPath, newPath, progressCallback, &callbackData);
    
    // 释放字符串
    safeReleaseStringUTFChars(env, oldFilePath, oldPath);
    safeReleaseStringUTFChars(env, patchFilePath, patchPath);
    safeReleaseStringUTFChars(env, newFilePath, newPath);
    
    // 检查回调是否发生异常
    if (callbackData.hasException) {
        LOGE("Exception occurred during patch application\n");
        return PE_ERROR_CANCELLED;
    }
    
    if (result != PE_SUCCESS) {
        LOGE("Patch application failed: %s\n", pe_error_to_string(result));
    } else {
        LOGI("Patch application completed successfully\n");
    }
    
    return result;
}

/**
 * 计算文件 MD5 哈希 (Requirements: 11.5)
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchnative_NativePatchEngine_calculateMd5(
    JNIEnv* env,
    jobject thiz,
    jstring filePath
) {
    (void)thiz;
    
    const char* path = safeGetStringUTFChars(env, filePath, nullptr);
    if (path == nullptr) {
        LOGE("Invalid parameter: null file path\n");
        return nullptr;
    }
    
    char hash[33];
    int result = pe_calculate_md5(path, hash, sizeof(hash));
    
    safeReleaseStringUTFChars(env, filePath, path);
    
    if (result != PE_SUCCESS) {
        LOGE("MD5 calculation failed: %s\n", pe_error_to_string(result));
        return nullptr;
    }
    
    return env->NewStringUTF(hash);
}

/**
 * 计算文件 SHA256 哈希 (Requirements: 11.5)
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchnative_NativePatchEngine_calculateSha256(
    JNIEnv* env,
    jobject thiz,
    jstring filePath
) {
    (void)thiz;
    
    const char* path = safeGetStringUTFChars(env, filePath, nullptr);
    if (path == nullptr) {
        LOGE("Invalid parameter: null file path\n");
        return nullptr;
    }
    
    char hash[65];
    int result = pe_calculate_sha256(path, hash, sizeof(hash));
    
    safeReleaseStringUTFChars(env, filePath, path);
    
    if (result != PE_SUCCESS) {
        LOGE("SHA256 calculation failed: %s\n", pe_error_to_string(result));
        return nullptr;
    }
    
    return env->NewStringUTF(hash);
}

/**
 * 取消当前操作 (Requirements: 11.7)
 */
JNIEXPORT void JNICALL
Java_com_orange_patchnative_NativePatchEngine_cancel(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    
    LOGI("Cancel requested\n");
    pe_cancel();
}

/**
 * 检查是否已请求取消
 */
JNIEXPORT jboolean JNICALL
Java_com_orange_patchnative_NativePatchEngine_isCancelled(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    
    return pe_is_cancelled() ? JNI_TRUE : JNI_FALSE;
}

/**
 * 获取最后一次错误信息
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchnative_NativePatchEngine_getLastError(JNIEnv* env, jobject thiz) {
    (void)thiz;
    
    const char* error = pe_get_last_error();
    return env->NewStringUTF(error != nullptr ? error : "");
}

/**
 * 获取错误码对应的描述 (Requirements: 11.9)
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchnative_NativePatchEngine_errorToString(
    JNIEnv* env,
    jobject thiz,
    jint errorCode
) {
    (void)thiz;
    
    const char* errorStr = pe_error_to_string(errorCode);
    return env->NewStringUTF(errorStr != nullptr ? errorStr : "Unknown error");
}

/* ============================================================================
 * JNI_OnLoad - 库加载时调用
 * ============================================================================ */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment\n");
        return JNI_ERR;
    }
    
    LOGI("PatchEngine native library loaded (version %s)\n", pe_get_version());
    return JNI_VERSION_1_6;
}

/* ============================================================================
 * JNI_OnUnload - 库卸载时调用
 * ============================================================================ */

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    
    LOGI("PatchEngine native library unloading\n");
    pe_release();
}

} /* extern "C" */
