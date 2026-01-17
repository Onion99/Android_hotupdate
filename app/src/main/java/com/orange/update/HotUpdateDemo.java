package com.orange.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 热更新演示类
 * 
 * 演示热更新的核心概念：
 * 1. 解析补丁包
 * 2. 提取版本信息
 * 3. 模拟热更新效果（通过 SharedPreferences 存储新版本信息）
 * 
 * 注意：真正的热更新需要使用 DexPatcher 注入 DEX，
 * 这里为了演示简化为版本信息的更新。
 */
public class HotUpdateDemo {
    
    private static final String TAG = "HotUpdateDemo";
    private static final String PREFS_NAME = "hot_update_prefs";
    private static final String KEY_PATCHED_VERSION = "patched_version";
    private static final String KEY_PATCHED_VERSION_CODE = "patched_version_code";
    private static final String KEY_PATCH_APPLIED = "patch_applied";
    private static final String KEY_PATCH_TIME = "patch_time";
    private static final String KEY_PATCH_FILE = "patch_file";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public HotUpdateDemo(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 应用补丁
     * 
     * @param patchFile 补丁文件
     * @param callback 回调
     */
    public void applyPatch(File patchFile, ApplyCallback callback) {
        if (patchFile == null || !patchFile.exists()) {
            callback.onError("补丁文件不存在");
            return;
        }
        
        new Thread(() -> {
            try {
                callback.onProgress(10, "读取补丁文件...");
                
                // 解析补丁包中的 patch.json
                String patchJson = extractPatchJson(patchFile);
                if (patchJson == null) {
                    callback.onError("无法解析补丁文件");
                    return;
                }
                
                callback.onProgress(30, "解析补丁信息...");
                
                // 解析版本信息
                String newVersion = extractJsonValue(patchJson, "newVersion");
                String newVersionCode = extractJsonValue(patchJson, "newVersionCode");
                String baseVersion = extractJsonValue(patchJson, "baseVersion");
                
                if (newVersion == null || newVersion.isEmpty()) {
                    newVersion = "1.2";  // 默认值
                }
                if (newVersionCode == null || newVersionCode.isEmpty()) {
                    newVersionCode = "3";  // 默认值
                }
                
                callback.onProgress(50, "验证补丁...");
                Thread.sleep(300);
                
                callback.onProgress(70, "应用补丁...");
                
                // 保存补丁信息
                prefs.edit()
                    .putString(KEY_PATCHED_VERSION, newVersion)
                    .putString(KEY_PATCHED_VERSION_CODE, newVersionCode)
                    .putBoolean(KEY_PATCH_APPLIED, true)
                    .putLong(KEY_PATCH_TIME, System.currentTimeMillis())
                    .putString(KEY_PATCH_FILE, patchFile.getAbsolutePath())
                    .apply();
                
                callback.onProgress(90, "完成...");
                Thread.sleep(200);
                
                callback.onProgress(100, "补丁应用成功!");
                
                // 返回结果
                PatchResult result = new PatchResult();
                result.success = true;
                result.oldVersion = baseVersion;
                result.newVersion = newVersion;
                result.newVersionCode = newVersionCode;
                result.patchSize = patchFile.length();
                
                callback.onSuccess(result);
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                callback.onError("应用补丁失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 从补丁包中提取 patch.json
     */
    private String extractPatchJson(File patchFile) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(patchFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("patch.json")) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toString("UTF-8");
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "解析补丁包失败", e);
        }
        return null;
    }
    
    /**
     * 简单的 JSON 值提取
     */
    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(searchKey);
            if (keyIndex == -1) return null;
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;
            
            int valueStart = json.indexOf("\"", colonIndex);
            if (valueStart == -1) {
                // 可能是数字
                int numStart = colonIndex + 1;
                while (numStart < json.length() && Character.isWhitespace(json.charAt(numStart))) {
                    numStart++;
                }
                int numEnd = numStart;
                while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '.')) {
                    numEnd++;
                }
                if (numEnd > numStart) {
                    return json.substring(numStart, numEnd);
                }
                return null;
            }
            
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            
            return json.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查是否已应用补丁
     */
    public boolean isPatchApplied() {
        return prefs.getBoolean(KEY_PATCH_APPLIED, false);
    }
    
    /**
     * 获取补丁后的版本
     */
    public String getPatchedVersion() {
        return prefs.getString(KEY_PATCHED_VERSION, null);
    }
    
    /**
     * 获取补丁后的版本号
     */
    public String getPatchedVersionCode() {
        return prefs.getString(KEY_PATCHED_VERSION_CODE, null);
    }
    
    /**
     * 获取补丁应用时间
     */
    public long getPatchTime() {
        return prefs.getLong(KEY_PATCH_TIME, 0);
    }
    
    /**
     * 清除补丁（回滚）
     */
    public void clearPatch() {
        prefs.edit()
            .remove(KEY_PATCHED_VERSION)
            .remove(KEY_PATCHED_VERSION_CODE)
            .remove(KEY_PATCH_APPLIED)
            .remove(KEY_PATCH_TIME)
            .remove(KEY_PATCH_FILE)
            .apply();
    }
    
    /**
     * 获取显示版本（如果有补丁则显示补丁版本）
     */
    public String getDisplayVersion(String originalVersion) {
        if (isPatchApplied()) {
            String patchedVersion = getPatchedVersion();
            if (patchedVersion != null) {
                return patchedVersion + " (热更新)";
            }
        }
        return originalVersion;
    }
    
    /**
     * 回调接口
     */
    public interface ApplyCallback {
        void onProgress(int percent, String message);
        void onSuccess(PatchResult result);
        void onError(String message);
    }
    
    /**
     * 补丁结果
     */
    public static class PatchResult {
        public boolean success;
        public String oldVersion;
        public String newVersion;
        public String newVersionCode;
        public long patchSize;
    }
}
