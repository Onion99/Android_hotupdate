package com.orange.patchgen.android;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * 存储空间检查器
 * 
 * 用于检查设备可用存储空间，估算补丁生成所需空间。
 * 
 * Requirements: 9.2
 */
public class StorageChecker {
    
    /**
     * 存储空间检查结果
     */
    public static class StorageCheckResult {
        private final boolean sufficient;
        private final long availableBytes;
        private final long requiredBytes;
        private final String message;
        
        public StorageCheckResult(boolean sufficient, long availableBytes, 
                                   long requiredBytes, String message) {
            this.sufficient = sufficient;
            this.availableBytes = availableBytes;
            this.requiredBytes = requiredBytes;
            this.message = message;
        }
        
        /**
         * 存储空间是否充足
         */
        public boolean isSufficient() {
            return sufficient;
        }
        
        /**
         * 可用空间（字节）
         */
        public long getAvailableBytes() {
            return availableBytes;
        }
        
        /**
         * 所需空间（字节）
         */
        public long getRequiredBytes() {
            return requiredBytes;
        }
        
        /**
         * 检查结果消息
         */
        public String getMessage() {
            return message;
        }
        
        /**
         * 获取可用空间（MB）
         */
        public long getAvailableMB() {
            return availableBytes / (1024 * 1024);
        }
        
        /**
         * 获取所需空间（MB）
         */
        public long getRequiredMB() {
            return requiredBytes / (1024 * 1024);
        }
    }
    
    // 安全余量系数（预留 20% 额外空间）
    private static final float SAFETY_MARGIN = 1.2f;
    
    // 最小可用空间（50MB）
    private static final long MIN_AVAILABLE_SPACE = 50 * 1024 * 1024L;
    
    private final Context context;
    
    public StorageChecker(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检查存储空间是否足够进行补丁生成
     * 
     * @param baseApk 基准 APK 文件
     * @param newApk 新版本 APK 文件
     * @param outputDir 输出目录（可为 null，使用默认缓存目录）
     * @return 检查结果
     */
    @NonNull
    public StorageCheckResult checkStorage(@NonNull File baseApk, 
                                            @NonNull File newApk,
                                            @Nullable File outputDir) {
        // 估算所需空间
        long requiredSpace = estimateRequiredSpace(baseApk, newApk);
        
        // 获取可用空间
        File targetDir = outputDir != null ? outputDir : getDefaultOutputDir();
        long availableSpace = getAvailableSpace(targetDir);
        
        // 检查是否充足
        boolean sufficient = availableSpace >= requiredSpace && availableSpace >= MIN_AVAILABLE_SPACE;
        
        String message;
        if (sufficient) {
            message = String.format("Storage check passed. Available: %d MB, Required: %d MB",
                    availableSpace / (1024 * 1024), requiredSpace / (1024 * 1024));
        } else {
            message = String.format("Insufficient storage. Available: %d MB, Required: %d MB",
                    availableSpace / (1024 * 1024), requiredSpace / (1024 * 1024));
        }
        
        return new StorageCheckResult(sufficient, availableSpace, requiredSpace, message);
    }
    
    /**
     * 估算补丁生成所需空间
     * 
     * 估算公式：
     * - 解压基准 APK 所需空间（约 APK 大小的 2 倍）
     * - 解压新 APK 所需空间（约 APK 大小的 2 倍）
     * - 补丁文件空间（约新 APK 大小的 30%）
     * - 临时文件空间（约 APK 大小的 50%）
     * - 安全余量（20%）
     * 
     * @param baseApk 基准 APK 文件
     * @param newApk 新版本 APK 文件
     * @return 估算所需空间（字节）
     */
    public long estimateRequiredSpace(@NonNull File baseApk, @NonNull File newApk) {
        long baseSize = baseApk.exists() ? baseApk.length() : 0;
        long newSize = newApk.exists() ? newApk.length() : 0;
        
        // 解压空间（APK 解压后通常是原大小的 1.5-2 倍）
        long extractSpace = (long) ((baseSize + newSize) * 2);
        
        // 补丁文件空间（通常是新 APK 的 10-30%，取 30% 作为保守估计）
        long patchSpace = (long) (newSize * 0.3);
        
        // 临时文件空间
        long tempSpace = (long) ((baseSize + newSize) * 0.5);
        
        // 总计并加上安全余量
        long totalRequired = (long) ((extractSpace + patchSpace + tempSpace) * SAFETY_MARGIN);
        
        // 至少需要最小空间
        return Math.max(totalRequired, MIN_AVAILABLE_SPACE);
    }
    
    /**
     * 获取指定目录的可用空间
     * 
     * @param directory 目标目录
     * @return 可用空间（字节）
     */
    public long getAvailableSpace(@NonNull File directory) {
        try {
            // 确保目录存在
            if (!directory.exists()) {
                File parent = directory.getParentFile();
                if (parent != null && parent.exists()) {
                    directory = parent;
                } else {
                    // 使用应用缓存目录
                    directory = context.getCacheDir();
                }
            }
            
            StatFs statFs = new StatFs(directory.getAbsolutePath());
            return statFs.getAvailableBytes();
        } catch (Exception e) {
            // 如果获取失败，返回 0
            return 0;
        }
    }
    
    /**
     * 获取内部存储可用空间
     * 
     * @return 可用空间（字节）
     */
    public long getInternalStorageAvailable() {
        return getAvailableSpace(context.getFilesDir());
    }
    
    /**
     * 获取外部存储可用空间
     * 
     * @return 可用空间（字节），如果外部存储不可用返回 0
     */
    public long getExternalStorageAvailable() {
        if (!isExternalStorageAvailable()) {
            return 0;
        }
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            return 0;
        }
        return getAvailableSpace(externalDir);
    }
    
    /**
     * 检查外部存储是否可用
     * 
     * @return true 如果外部存储可用且可写
     */
    public boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    
    /**
     * 获取默认输出目录
     * 
     * 优先使用外部存储，如果不可用则使用内部存储
     * 
     * @return 默认输出目录
     */
    @NonNull
    public File getDefaultOutputDir() {
        if (isExternalStorageAvailable()) {
            File externalDir = context.getExternalFilesDir("patches");
            if (externalDir != null) {
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }
                return externalDir;
            }
        }
        
        File internalDir = new File(context.getFilesDir(), "patches");
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }
        return internalDir;
    }
    
    /**
     * 获取临时目录
     * 
     * @return 临时目录
     */
    @NonNull
    public File getTempDir() {
        File tempDir = new File(context.getCacheDir(), "patch_gen_temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        return tempDir;
    }
    
    /**
     * 清理临时目录
     * 
     * @return true 如果清理成功
     */
    public boolean cleanTempDir() {
        File tempDir = getTempDir();
        return deleteRecursively(tempDir);
    }
    
    /**
     * 递归删除目录
     */
    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        
        return file.delete();
    }
    
    /**
     * 格式化字节大小为可读字符串
     * 
     * @param bytes 字节数
     * @return 格式化后的字符串（如 "10.5 MB"）
     */
    @NonNull
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
