package com.orange.patchnative;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Native Patch Engine - JNI 接口类
 * 
 * 提供高性能的二进制差异生成和应用功能，包括：
 * - BsDiff/BsPatch 二进制差异算法
 * - MD5/SHA256 哈希计算
 * - 进度回调和取消机制
 * 
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10
 */
public class NativePatchEngine {
    
    private static boolean sLibraryLoaded = false;
    private static final Object sLoadLock = new Object();
    
    /** 全局进度回调（可选） */
    private NativeProgressCallback globalCallback;
    
    /**
     * 错误码定义
     */
    public static final int SUCCESS = 0;
    public static final int ERROR_FILE_NOT_FOUND = -1;
    public static final int ERROR_FILE_READ = -2;
    public static final int ERROR_FILE_WRITE = -3;
    public static final int ERROR_OUT_OF_MEMORY = -4;
    public static final int ERROR_INVALID_PARAM = -5;
    public static final int ERROR_CANCELLED = -6;
    public static final int ERROR_CORRUPT_PATCH = -7;
    public static final int ERROR_COMPRESS_FAILED = -8;
    public static final int ERROR_DECOMPRESS_FAILED = -9;
    public static final int ERROR_HASH_FAILED = -10;
    public static final int ERROR_SIZE_MISMATCH = -11;
    public static final int ERROR_CHECKSUM_MISMATCH = -12;
    public static final int ERROR_INTERNAL = -99;
    
    /**
     * 检查 Native 库是否可用
     */
    public static boolean isAvailable() {
        synchronized (sLoadLock) {
            if (!sLibraryLoaded) {
                try {
                    System.loadLibrary("patchengine");
                    sLibraryLoaded = true;
                } catch (UnsatisfiedLinkError e) {
                    sLibraryLoaded = false;
                }
            }
            return sLibraryLoaded;
        }
    }
    
    /**
     * 加载 Native 库
     */
    private static void ensureLibraryLoaded() {
        if (!isAvailable()) {
            throw new UnsatisfiedLinkError("Native library 'patchengine' not available");
        }
    }
    
    /**
     * 初始化引擎
     * 
     * @return true 成功，false 失败
     */
    public native boolean init();
    
    /**
     * 释放引擎资源
     */
    public native void release();
    
    /**
     * 获取引擎版本
     * 
     * @return 版本字符串
     */
    public native String getVersion();
    
    /**
     * 检查引擎是否已初始化
     * 
     * @return true 已初始化，false 未初始化
     */
    public native boolean isInitialized();

    /**
     * 生成二进制差异补丁
     * 
     * @param oldFilePath   旧文件路径
     * @param newFilePath   新文件路径
     * @param patchFilePath 输出补丁文件路径
     * @param callback      进度回调（可为 null）
     * @return 错误码，SUCCESS 表示成功
     */
    public native int generateDiff(
            @NonNull String oldFilePath,
            @NonNull String newFilePath,
            @NonNull String patchFilePath,
            @Nullable NativeProgressCallback callback
    );
    
    /**
     * 应用二进制差异补丁
     * 
     * @param oldFilePath   旧文件路径
     * @param patchFilePath 补丁文件路径
     * @param newFilePath   输出新文件路径
     * @param callback      进度回调（可为 null）
     * @return 错误码，SUCCESS 表示成功
     */
    public native int applyPatch(
            @NonNull String oldFilePath,
            @NonNull String patchFilePath,
            @NonNull String newFilePath,
            @Nullable NativeProgressCallback callback
    );
    
    /**
     * 计算文件 MD5 哈希
     * 
     * @param filePath 文件路径
     * @return MD5 哈希字符串，失败返回 null
     */
    @Nullable
    public native String calculateMd5(@NonNull String filePath);
    
    /**
     * 计算文件 SHA256 哈希
     * 
     * @param filePath 文件路径
     * @return SHA256 哈希字符串，失败返回 null
     */
    @Nullable
    public native String calculateSha256(@NonNull String filePath);
    
    /**
     * 取消当前操作
     */
    public native void cancel();
    
    /**
     * 检查是否已请求取消
     * 
     * @return true 已请求取消，false 未请求取消
     */
    public native boolean isCancelled();
    
    /**
     * 获取最后一次错误信息
     * 
     * @return 错误信息字符串
     */
    @NonNull
    public native String getLastError();
    
    /**
     * 获取错误码对应的描述
     * 
     * @param errorCode 错误码
     * @return 错误描述字符串
     */
    @NonNull
    public native String errorToString(int errorCode);
    
    /**
     * 构造函数
     */
    public NativePatchEngine() {
        ensureLibraryLoaded();
    }
    
    /**
     * 设置全局进度回调
     * 
     * 设置后，所有操作都会使用此回调报告进度（除非方法调用时传入了特定回调）
     * 
     * @param callback 进度回调，传入 null 清除回调
     * 
     * Requirements: 11.6
     */
    public void setProgressCallback(@Nullable NativeProgressCallback callback) {
        this.globalCallback = callback;
    }
    
    /**
     * 获取当前全局进度回调
     * 
     * @return 当前设置的全局进度回调，可能为 null
     */
    @Nullable
    public NativeProgressCallback getProgressCallback() {
        return globalCallback;
    }
    
    /**
     * 获取有效的回调（优先使用传入的回调，否则使用全局回调）
     */
    private NativeProgressCallback getEffectiveCallback(@Nullable NativeProgressCallback callback) {
        return callback != null ? callback : globalCallback;
    }
    
    /* ========================================================================
     * 异常抛出版本的方法（Requirements: 11.9）
     * ======================================================================== */
    
    /**
     * 生成二进制差异补丁（抛出异常版本）
     * 
     * @param oldFilePath   旧文件路径
     * @param newFilePath   新文件路径
     * @param patchFilePath 输出补丁文件路径
     * @throws NativePatchException 如果生成失败
     * 
     * Requirements: 11.3, 11.9
     */
    public void generateDiffOrThrow(
            @NonNull String oldFilePath,
            @NonNull String newFilePath,
            @NonNull String patchFilePath
    ) throws NativePatchException {
        generateDiffOrThrow(oldFilePath, newFilePath, patchFilePath, null);
    }
    
    /**
     * 生成二进制差异补丁（抛出异常版本，带回调）
     * 
     * @param oldFilePath   旧文件路径
     * @param newFilePath   新文件路径
     * @param patchFilePath 输出补丁文件路径
     * @param callback      进度回调（可为 null，将使用全局回调）
     * @throws NativePatchException 如果生成失败
     * 
     * Requirements: 11.3, 11.6, 11.9
     */
    public void generateDiffOrThrow(
            @NonNull String oldFilePath,
            @NonNull String newFilePath,
            @NonNull String patchFilePath,
            @Nullable NativeProgressCallback callback
    ) throws NativePatchException {
        int result = generateDiff(oldFilePath, newFilePath, patchFilePath, 
                getEffectiveCallback(callback));
        if (result != SUCCESS) {
            throw new NativePatchException(result, errorToString(result) + ": " + getLastError());
        }
    }
    
    /**
     * 应用二进制差异补丁（抛出异常版本）
     * 
     * @param oldFilePath   旧文件路径
     * @param patchFilePath 补丁文件路径
     * @param newFilePath   输出新文件路径
     * @throws NativePatchException 如果应用失败
     * 
     * Requirements: 11.4, 11.9
     */
    public void applyPatchOrThrow(
            @NonNull String oldFilePath,
            @NonNull String patchFilePath,
            @NonNull String newFilePath
    ) throws NativePatchException {
        applyPatchOrThrow(oldFilePath, patchFilePath, newFilePath, null);
    }
    
    /**
     * 应用二进制差异补丁（抛出异常版本，带回调）
     * 
     * @param oldFilePath   旧文件路径
     * @param patchFilePath 补丁文件路径
     * @param newFilePath   输出新文件路径
     * @param callback      进度回调（可为 null，将使用全局回调）
     * @throws NativePatchException 如果应用失败
     * 
     * Requirements: 11.4, 11.6, 11.9
     */
    public void applyPatchOrThrow(
            @NonNull String oldFilePath,
            @NonNull String patchFilePath,
            @NonNull String newFilePath,
            @Nullable NativeProgressCallback callback
    ) throws NativePatchException {
        int result = applyPatch(oldFilePath, patchFilePath, newFilePath, 
                getEffectiveCallback(callback));
        if (result != SUCCESS) {
            throw new NativePatchException(result, errorToString(result) + ": " + getLastError());
        }
    }
    
    /**
     * 计算文件 MD5 哈希（抛出异常版本）
     * 
     * @param filePath 文件路径
     * @return MD5 哈希字符串
     * @throws NativePatchException 如果计算失败
     * 
     * Requirements: 11.5, 11.9
     */
    @NonNull
    public String calculateMd5OrThrow(@NonNull String filePath) throws NativePatchException {
        String result = calculateMd5(filePath);
        if (result == null) {
            throw new NativePatchException(ERROR_HASH_FAILED, 
                    "Failed to calculate MD5: " + getLastError());
        }
        return result;
    }
    
    /**
     * 计算文件 SHA256 哈希（抛出异常版本）
     * 
     * @param filePath 文件路径
     * @return SHA256 哈希字符串
     * @throws NativePatchException 如果计算失败
     * 
     * Requirements: 11.5, 11.9
     */
    @NonNull
    public String calculateSha256OrThrow(@NonNull String filePath) throws NativePatchException {
        String result = calculateSha256(filePath);
        if (result == null) {
            throw new NativePatchException(ERROR_HASH_FAILED, 
                    "Failed to calculate SHA256: " + getLastError());
        }
        return result;
    }
    
    /**
     * 同步生成差异补丁（带自动初始化和释放）
     * 
     * @param oldFilePath   旧文件路径
     * @param newFilePath   新文件路径
     * @param patchFilePath 输出补丁文件路径
     * @param callback      进度回调（可为 null）
     * @return 错误码，SUCCESS 表示成功
     */
    public int generateDiffSync(
            @NonNull String oldFilePath,
            @NonNull String newFilePath,
            @NonNull String patchFilePath,
            @Nullable NativeProgressCallback callback
    ) {
        if (!init()) {
            return ERROR_INTERNAL;
        }
        try {
            return generateDiff(oldFilePath, newFilePath, patchFilePath, 
                    getEffectiveCallback(callback));
        } finally {
            release();
        }
    }
    
    /**
     * 同步应用补丁（带自动初始化和释放）
     * 
     * @param oldFilePath   旧文件路径
     * @param patchFilePath 补丁文件路径
     * @param newFilePath   输出新文件路径
     * @param callback      进度回调（可为 null）
     * @return 错误码，SUCCESS 表示成功
     */
    public int applyPatchSync(
            @NonNull String oldFilePath,
            @NonNull String patchFilePath,
            @NonNull String newFilePath,
            @Nullable NativeProgressCallback callback
    ) {
        if (!init()) {
            return ERROR_INTERNAL;
        }
        try {
            return applyPatch(oldFilePath, patchFilePath, newFilePath, 
                    getEffectiveCallback(callback));
        } finally {
            release();
        }
    }
    
    /* ========================================================================
     * 异步操作支持（Requirements: 11.10）
     * ======================================================================== */
    
    /**
     * 异步操作结果回调
     */
    public interface AsyncResultCallback {
        /**
         * 操作成功完成
         */
        void onSuccess();
        
        /**
         * 操作失败
         * 
         * @param errorCode 错误码
         * @param message   错误信息
         */
        void onError(int errorCode, String message);
    }
    
    /**
     * 异步生成二进制差异补丁
     * 
     * 在后台线程执行，通过回调返回结果
     * 
     * @param oldFilePath    旧文件路径
     * @param newFilePath    新文件路径
     * @param patchFilePath  输出补丁文件路径
     * @param progressCallback 进度回调（可为 null）
     * @param resultCallback   结果回调
     * @return 执行任务的线程，可用于等待或中断
     * 
     * Requirements: 11.10
     */
    @NonNull
    public Thread generateDiffAsync(
            @NonNull final String oldFilePath,
            @NonNull final String newFilePath,
            @NonNull final String patchFilePath,
            @Nullable final NativeProgressCallback progressCallback,
            @NonNull final AsyncResultCallback resultCallback
    ) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int result = generateDiffSync(oldFilePath, newFilePath, patchFilePath, 
                        getEffectiveCallback(progressCallback));
                if (result == SUCCESS) {
                    resultCallback.onSuccess();
                } else {
                    resultCallback.onError(result, errorToString(result) + ": " + getLastError());
                }
            }
        }, "NativePatchEngine-GenerateDiff");
        thread.start();
        return thread;
    }
    
    /**
     * 异步应用二进制差异补丁
     * 
     * 在后台线程执行，通过回调返回结果
     * 
     * @param oldFilePath      旧文件路径
     * @param patchFilePath    补丁文件路径
     * @param newFilePath      输出新文件路径
     * @param progressCallback 进度回调（可为 null）
     * @param resultCallback   结果回调
     * @return 执行任务的线程，可用于等待或中断
     * 
     * Requirements: 11.10
     */
    @NonNull
    public Thread applyPatchAsync(
            @NonNull final String oldFilePath,
            @NonNull final String patchFilePath,
            @NonNull final String newFilePath,
            @Nullable final NativeProgressCallback progressCallback,
            @NonNull final AsyncResultCallback resultCallback
    ) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int result = applyPatchSync(oldFilePath, patchFilePath, newFilePath, 
                        getEffectiveCallback(progressCallback));
                if (result == SUCCESS) {
                    resultCallback.onSuccess();
                } else {
                    resultCallback.onError(result, errorToString(result) + ": " + getLastError());
                }
            }
        }, "NativePatchEngine-ApplyPatch");
        thread.start();
        return thread;
    }
    
    /**
     * 异步计算文件 MD5 哈希
     * 
     * @param filePath       文件路径
     * @param resultCallback 结果回调
     * @return 执行任务的线程
     * 
     * Requirements: 11.10
     */
    @NonNull
    public Thread calculateMd5Async(
            @NonNull final String filePath,
            @NonNull final HashResultCallback resultCallback
    ) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String hash = calculateMd5(filePath);
                if (hash != null) {
                    resultCallback.onSuccess(hash);
                } else {
                    resultCallback.onError(ERROR_HASH_FAILED, "Failed to calculate MD5: " + getLastError());
                }
            }
        }, "NativePatchEngine-MD5");
        thread.start();
        return thread;
    }
    
    /**
     * 异步计算文件 SHA256 哈希
     * 
     * @param filePath       文件路径
     * @param resultCallback 结果回调
     * @return 执行任务的线程
     * 
     * Requirements: 11.10
     */
    @NonNull
    public Thread calculateSha256Async(
            @NonNull final String filePath,
            @NonNull final HashResultCallback resultCallback
    ) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String hash = calculateSha256(filePath);
                if (hash != null) {
                    resultCallback.onSuccess(hash);
                } else {
                    resultCallback.onError(ERROR_HASH_FAILED, "Failed to calculate SHA256: " + getLastError());
                }
            }
        }, "NativePatchEngine-SHA256");
        thread.start();
        return thread;
    }
    
    /**
     * 哈希计算结果回调
     */
    public interface HashResultCallback {
        /**
         * 计算成功
         * 
         * @param hash 哈希值
         */
        void onSuccess(String hash);
        
        /**
         * 计算失败
         * 
         * @param errorCode 错误码
         * @param message   错误信息
         */
        void onError(int errorCode, String message);
    }
}
