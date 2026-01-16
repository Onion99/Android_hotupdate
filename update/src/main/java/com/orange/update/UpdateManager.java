package com.orange.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 热更新 SDK 主管理类，提供单例模式访问。
 * 
 * 功能：
 * - SDK 初始化
 * - 检查更新
 * - 下载补丁
 * - 应用补丁
 * - 回滚补丁
 * - 回调分发
 * 
 * 使用方式：
 * <pre>
 * // 1. 初始化 SDK
 * UpdateConfig config = new UpdateConfig.Builder()
 *     .serverUrl("https://example.com")
 *     .appKey("your-app-key")
 *     .appVersion("1.0.0")
 *     .build();
 * UpdateManager.init(context, config);
 * 
 * // 2. 设置回调
 * UpdateManager.getInstance().setCallback(new SimpleUpdateCallback() {
 *     @Override
 *     public void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
 *         // 处理检查结果
 *     }
 * });
 * 
 * // 3. 检查更新
 * UpdateManager.getInstance().checkUpdate();
 * </pre>
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 7.1, 7.2, 7.3, 7.4, 7.5
 */
public class UpdateManager {
    
    private static final String TAG = "UpdateManager";
    
    // 单例实例
    private static volatile UpdateManager instance;
    
    // 核心组件
    private final Context context;
    private final UpdateConfig config;
    private final VersionChecker versionChecker;
    private final PatchManager patchManager;
    private final PatchApplier patchApplier;
    
    // 回调
    private UpdateCallback callback;
    
    // 主线程 Handler，用于回调分发
    private final Handler mainHandler;
    
    // 后台线程池
    private final ExecutorService executor;
    
    // 状态标志
    private final AtomicBoolean isChecking = new AtomicBoolean(false);
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean isApplying = new AtomicBoolean(false);

    
    // ==================== 构造函数 ====================
    
    /**
     * 私有构造函数
     * @param context 应用上下文
     * @param config SDK 配置
     */
    private UpdateManager(Context context, UpdateConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.versionChecker = new VersionChecker(config);
        this.patchManager = new PatchManager(this.context, config);
        this.patchApplier = new PatchApplier(this.context, patchManager.getStorage());
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        
        Log.d(TAG, "UpdateManager initialized with server: " + config.getServerUrl());
    }
    
    /**
     * 构造函数（用于测试，允许注入依赖）
     */
    UpdateManager(Context context, UpdateConfig config, VersionChecker versionChecker,
                  PatchManager patchManager, PatchApplier patchApplier) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.versionChecker = versionChecker;
        this.patchManager = patchManager;
        this.patchApplier = patchApplier;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    // ==================== 初始化方法 ====================
    
    /**
     * 初始化 SDK
     * 
     * @param context 应用上下文
     * @param config SDK 配置
     * @return UpdateManager 单例实例
     * @throws IllegalArgumentException 如果参数无效
     * @throws IllegalStateException 如果配置无效
     * 
     * Requirements: 1.1, 1.2, 1.3
     */
    public static UpdateManager init(Context context, UpdateConfig config) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        
        // 双重检查锁定
        if (instance == null) {
            synchronized (UpdateManager.class) {
                if (instance == null) {
                    instance = new UpdateManager(context, config);
                }
            }
        }
        
        return instance;
    }
    
    /**
     * 获取单例实例
     * 
     * @return UpdateManager 单例实例
     * @throws IllegalStateException 如果 SDK 未初始化
     * 
     * Requirements: 1.4
     */
    public static UpdateManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "UpdateManager not initialized. Call init() first.");
        }
        return instance;
    }
    
    /**
     * 检查 SDK 是否已初始化
     * @return 是否已初始化
     */
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * 重置 SDK（仅用于测试）
     */
    static void reset() {
        if (instance != null) {
            instance.executor.shutdown();
            instance = null;
        }
    }

    
    // ==================== 回调设置 ====================
    
    /**
     * 设置更新回调
     * @param callback 回调接口实现
     */
    public void setCallback(UpdateCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 获取当前回调
     * @return 当前设置的回调，可能为 null
     */
    public UpdateCallback getCallback() {
        return callback;
    }
    
    // ==================== 检查更新 ====================
    
    /**
     * 检查更新（异步）
     * 
     * 回调顺序：
     * 1. onCheckStart() - 开始检查
     * 2. onCheckComplete(hasUpdate, patchInfo) - 检查完成
     * 或
     * 2. onError(errorCode, message) - 检查失败
     * 
     * Requirements: 7.1
     */
    public void checkUpdate() {
        if (isChecking.get()) {
            Log.w(TAG, "Already checking for updates");
            return;
        }
        
        isChecking.set(true);
        
        // 通知检查开始
        dispatchOnCheckStart();
        
        executor.execute(() -> {
            try {
                // 获取当前补丁版本
                String currentPatchVersion = getCurrentPatchVersion();
                
                // 检查更新
                PatchInfo patchInfo = versionChecker.checkUpdate(currentPatchVersion);
                
                boolean hasUpdate = patchInfo != null;
                
                // 通知检查完成
                dispatchOnCheckComplete(hasUpdate, patchInfo);
                
            } catch (ServerApi.UpdateException e) {
                Log.e(TAG, "Check update failed", e);
                dispatchOnError(e.getErrorCode(), e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during check update", e);
                dispatchOnError(UpdateErrorCode.ERROR_SERVER_ERROR, 
                        "Unexpected error: " + e.getMessage());
            } finally {
                isChecking.set(false);
            }
        });
    }
    
    /**
     * 同步检查更新
     * 
     * @return 如果有更新返回 PatchInfo，否则返回 null
     * @throws ServerApi.UpdateException 如果检查失败
     */
    public PatchInfo checkUpdateSync() throws ServerApi.UpdateException {
        String currentPatchVersion = getCurrentPatchVersion();
        return versionChecker.checkUpdate(currentPatchVersion);
    }
    
    /**
     * 获取当前补丁版本
     */
    private String getCurrentPatchVersion() {
        PatchInfo appliedPatch = patchApplier.getAppliedPatchInfo();
        return appliedPatch != null ? appliedPatch.getPatchVersion() : null;
    }

    
    // ==================== 下载补丁 ====================
    
    /**
     * 下载补丁（异步）
     * 
     * 回调顺序：
     * 1. onDownloadProgress(current, total) - 下载进度（多次）
     * 2. onDownloadComplete(patchInfo) - 下载完成
     * 或
     * 2. onError(errorCode, message) - 下载失败
     * 
     * @param patchInfo 补丁信息
     * 
     * Requirements: 7.2
     */
    public void downloadPatch(PatchInfo patchInfo) {
        if (patchInfo == null) {
            dispatchOnError(UpdateErrorCode.ERROR_INVALID_PATCH_FORMAT, 
                    "PatchInfo cannot be null");
            return;
        }
        
        if (isDownloading.get()) {
            Log.w(TAG, "Already downloading a patch");
            return;
        }
        
        isDownloading.set(true);
        
        // 创建下载回调
        DownloadCallback downloadCallback = new DownloadCallback() {
            @Override
            public void onProgress(long current, long total) {
                dispatchOnDownloadProgress(current, total);
            }
            
            @Override
            public void onSuccess(File file) {
                isDownloading.set(false);
                dispatchOnDownloadComplete(patchInfo);
            }
            
            @Override
            public void onError(int errorCode, String message) {
                isDownloading.set(false);
                dispatchOnError(errorCode, message);
            }
        };
        
        // 开始下载
        patchManager.download(patchInfo, downloadCallback);
    }
    
    /**
     * 取消当前下载
     */
    public void cancelDownload() {
        if (isDownloading.get()) {
            patchManager.cancelDownload();
            isDownloading.set(false);
            Log.d(TAG, "Download cancelled");
        }
    }
    
    /**
     * 检查补丁是否已下载
     * @param patchId 补丁ID
     * @return 是否已下载
     */
    public boolean isPatchDownloaded(String patchId) {
        return patchManager.isPatchDownloaded(patchId);
    }

    
    // ==================== 应用补丁 ====================
    
    /**
     * 应用补丁（异步）
     * 
     * 回调顺序：
     * 1. onApplyStart() - 开始应用
     * 2. onApplyComplete(success) - 应用完成
     * 3. onUpdateSuccess() - 更新成功（如果应用成功）
     * 或
     * 2. onError(errorCode, message) - 应用失败
     * 
     * @param patchInfo 补丁信息
     * 
     * Requirements: 7.3
     */
    public void applyPatch(PatchInfo patchInfo) {
        if (patchInfo == null) {
            dispatchOnError(UpdateErrorCode.ERROR_INVALID_PATCH_FORMAT, 
                    "PatchInfo cannot be null");
            return;
        }
        
        if (isApplying.get()) {
            Log.w(TAG, "Already applying a patch");
            return;
        }
        
        isApplying.set(true);
        
        // 通知应用开始
        dispatchOnApplyStart();
        
        executor.execute(() -> {
            try {
                // 验证补丁
                if (!patchManager.verify(patchInfo)) {
                    dispatchOnError(UpdateErrorCode.ERROR_INVALID_PATCH_FORMAT, 
                            "Patch verification failed");
                    dispatchOnApplyComplete(false);
                    return;
                }
                
                // 应用补丁
                boolean success = patchApplier.apply(patchInfo);
                
                // 通知应用完成
                dispatchOnApplyComplete(success);
                
                if (success) {
                    // 通知更新成功
                    dispatchOnUpdateSuccess();
                } else {
                    dispatchOnError(UpdateErrorCode.ERROR_APPLY_FAILED, 
                            "Failed to apply patch");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during apply patch", e);
                dispatchOnError(UpdateErrorCode.ERROR_APPLY_FAILED, 
                        "Unexpected error: " + e.getMessage());
                dispatchOnApplyComplete(false);
            } finally {
                isApplying.set(false);
            }
        });
    }
    
    /**
     * 同步应用补丁
     * 
     * @param patchInfo 补丁信息
     * @return 是否应用成功
     */
    public boolean applyPatchSync(PatchInfo patchInfo) {
        if (patchInfo == null) {
            return false;
        }
        
        // 验证补丁
        if (!patchManager.verify(patchInfo)) {
            return false;
        }
        
        // 应用补丁
        return patchApplier.apply(patchInfo);
    }
    
    /**
     * 加载已应用的补丁（应用启动时调用）
     * 
     * 此方法应在 Application.onCreate() 中尽早调用，
     * 以确保补丁在应用启动时生效。
     */
    public void loadAppliedPatch() {
        patchApplier.loadAppliedPatch();
    }

    
    // ==================== 回滚功能 ====================
    
    /**
     * 回滚到上一版本（异步）
     * 
     * 回调顺序：
     * 1. onApplyStart() - 开始回滚
     * 2. onApplyComplete(success) - 回滚完成
     * 或
     * 2. onError(errorCode, message) - 回滚失败
     * 
     * Requirements: 7.4
     */
    public void rollback() {
        if (isApplying.get()) {
            Log.w(TAG, "Cannot rollback while applying a patch");
            return;
        }
        
        isApplying.set(true);
        
        // 通知开始（复用 onApplyStart）
        dispatchOnApplyStart();
        
        executor.execute(() -> {
            try {
                boolean success = patchApplier.rollback();
                
                // 通知完成
                dispatchOnApplyComplete(success);
                
                if (!success) {
                    dispatchOnError(UpdateErrorCode.ERROR_ROLLBACK_FAILED, 
                            "Failed to rollback");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during rollback", e);
                dispatchOnError(UpdateErrorCode.ERROR_ROLLBACK_FAILED, 
                        "Unexpected error: " + e.getMessage());
                dispatchOnApplyComplete(false);
            } finally {
                isApplying.set(false);
            }
        });
    }
    
    /**
     * 同步回滚到上一版本
     * @return 是否回滚成功
     */
    public boolean rollbackSync() {
        return patchApplier.rollback();
    }
    
    /**
     * 回滚到原始状态（清除所有补丁）
     * @return 是否回滚成功
     */
    public boolean rollbackToOriginal() {
        return patchApplier.rollbackToOriginal();
    }

    
    // ==================== 查询功能 ====================
    
    /**
     * 获取当前补丁信息
     * @return 当前应用的补丁信息，如果没有返回 null
     */
    public PatchInfo getCurrentPatchInfo() {
        return patchApplier.getAppliedPatchInfo();
    }
    
    /**
     * 获取当前补丁ID
     * @return 当前应用的补丁ID，如果没有返回 null
     */
    public String getCurrentPatchId() {
        return patchApplier.getAppliedPatchId();
    }
    
    /**
     * 检查是否有已应用的补丁
     * @return 是否有已应用的补丁
     */
    public boolean hasAppliedPatch() {
        return patchApplier.hasAppliedPatch();
    }
    
    /**
     * 获取 SDK 配置
     * @return SDK 配置
     */
    public UpdateConfig getConfig() {
        return config;
    }
    
    /**
     * 获取应用上下文
     * @return 应用上下文
     */
    public Context getContext() {
        return context;
    }
    
    // ==================== 状态查询 ====================
    
    /**
     * 是否正在检查更新
     * @return 是否正在检查
     */
    public boolean isChecking() {
        return isChecking.get();
    }
    
    /**
     * 是否正在下载
     * @return 是否正在下载
     */
    public boolean isDownloading() {
        return isDownloading.get();
    }
    
    /**
     * 是否正在应用补丁
     * @return 是否正在应用
     */
    public boolean isApplying() {
        return isApplying.get();
    }
    
    /**
     * 检查当前设备是否支持热更新
     * @return 是否支持
     */
    public static boolean isSupported() {
        return PatchApplier.isSupported();
    }

    
    // ==================== 回调分发（主线程） ====================
    
    /**
     * 分发 onCheckStart 回调
     * Requirements: 7.1
     */
    private void dispatchOnCheckStart() {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onCheckStart();
                } catch (Exception e) {
                    Log.e(TAG, "Error in onCheckStart callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onCheckComplete 回调
     * Requirements: 7.1
     */
    private void dispatchOnCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onCheckComplete(hasUpdate, patchInfo);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onCheckComplete callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onDownloadProgress 回调
     * Requirements: 7.2
     */
    private void dispatchOnDownloadProgress(long current, long total) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onDownloadProgress(current, total);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDownloadProgress callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onDownloadComplete 回调
     * Requirements: 7.2
     */
    private void dispatchOnDownloadComplete(PatchInfo patchInfo) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onDownloadComplete(patchInfo);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onDownloadComplete callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onApplyStart 回调
     * Requirements: 7.3
     */
    private void dispatchOnApplyStart() {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onApplyStart();
                } catch (Exception e) {
                    Log.e(TAG, "Error in onApplyStart callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onApplyComplete 回调
     * Requirements: 7.3
     */
    private void dispatchOnApplyComplete(boolean success) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onApplyComplete(success);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onApplyComplete callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onUpdateSuccess 回调
     * Requirements: 7.5
     */
    private void dispatchOnUpdateSuccess() {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onUpdateSuccess();
                } catch (Exception e) {
                    Log.e(TAG, "Error in onUpdateSuccess callback", e);
                }
            });
        }
    }
    
    /**
     * 分发 onError 回调
     * Requirements: 7.4
     */
    private void dispatchOnError(int errorCode, String message) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    callback.onError(errorCode, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onError callback", e);
                }
            });
        }
    }
}
