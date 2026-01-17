package com.orange.update;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

/**
 * 热更新 Application
 * 
 * 在 attachBaseContext 中加载补丁，确保：
 * 1. DEX 补丁在类加载前注入
 * 2. 资源补丁在 Activity 创建前加载
 * 3. 所有组件都能使用更新后的代码和资源
 */
public class PatchApplication extends Application {
    
    private static final String TAG = "PatchApplication";
    private static final String PREFS_NAME = "real_hot_update_prefs";
    private static final String KEY_PATCH_APPLIED = "patch_applied";
    private static final String KEY_PATCH_FILE = "patch_file";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 在最早的时机加载补丁
        loadPatchIfNeeded();
    }
    
    /**
     * 加载已应用的补丁
     */
    private void loadPatchIfNeeded() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            if (!prefs.getBoolean(KEY_PATCH_APPLIED, false)) {
                Log.d(TAG, "No patch applied, skip loading");
                return;
            }
            
            String patchPath = prefs.getString(KEY_PATCH_FILE, null);
            if (patchPath == null) {
                Log.w(TAG, "Patch file path not found");
                return;
            }
            
            File patchFile = new File(patchPath);
            if (!patchFile.exists()) {
                Log.w(TAG, "Patch file not found: " + patchPath);
                // 清除无效的补丁记录
                prefs.edit().putBoolean(KEY_PATCH_APPLIED, false).apply();
                return;
            }
            
            Log.i(TAG, "Loading patch from: " + patchPath);
            
            // 1. 加载 DEX 补丁
            loadDexPatch(patchFile);
            
            // 2. 加载 SO 库补丁
            loadSoPatch(patchFile);
            
            // 3. 加载资源补丁
            loadResourcePatch(patchFile);
            
            Log.i(TAG, "Patch loaded successfully in attachBaseContext");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch in attachBaseContext", e);
        }
    }
    
    /**
     * 加载 DEX 补丁
     */
    private void loadDexPatch(File patchFile) {
        try {
            // 检查是否支持
            if (!DexPatcher.isSupported()) {
                Log.w(TAG, "DEX patching not supported on this device");
                return;
            }
            
            // 提取 DEX 文件
            File dexDir = new File(getFilesDir(), "hotupdate/dex");
            if (!dexDir.exists()) dexDir.mkdirs();
            
            File extractedDex = extractDexFromPatch(patchFile, dexDir);
            if (extractedDex != null && extractedDex.exists()) {
                DexPatcher.injectPatchDex(this, extractedDex.getAbsolutePath());
                Log.i(TAG, "DEX patch injected in attachBaseContext");
            } else {
                Log.d(TAG, "No DEX file in patch");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load DEX patch", e);
        }
    }
    
    /**
     * 加载 SO 库补丁
     */
    private void loadSoPatch(File patchFile) {
        try {
            // 检查是否支持
            if (!SoPatcher.isSupported()) {
                Log.w(TAG, "SO patching not supported on this device");
                return;
            }
            
            // 加载 SO 库
            SoPatcher.loadPatchLibraries(this, patchFile);
            Log.i(TAG, "SO libraries loaded in attachBaseContext");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load SO patch", e);
        }
    }
    
    /**
     * 加载资源补丁
     * 使用 ResourceMerger 合并原始 APK 和补丁资源，生成完整资源包后加载
     */
    private void loadResourcePatch(File patchFile) {
        try {
            // 检查是否支持
            if (!ResourcePatcher.isSupported()) {
                Log.w(TAG, "Resource patching not supported on this device");
                return;
            }
            
            // 检查补丁包是否包含资源
            if (!hasResources(patchFile)) {
                Log.d(TAG, "No resources in patch");
                return;
            }
            
            // 优先使用已合并的资源文件（如果存在）
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String mergedResourcePath = prefs.getString("merged_resource_file", null);
            
            File mergedResourceFile = null;
            if (mergedResourcePath != null) {
                mergedResourceFile = new File(mergedResourcePath);
            }
            
            // 如果合并文件不存在，重新合并
            if (mergedResourceFile == null || !mergedResourceFile.exists()) {
                Log.d(TAG, "Merged resource file not found, creating...");
                
                File mergedResourceDir = new File(getFilesDir(), "hotupdate/merged");
                if (!mergedResourceDir.exists()) mergedResourceDir.mkdirs();
                mergedResourceFile = new File(mergedResourceDir, "merged_resources.apk");
                
                // 合并原始 APK 和补丁资源
                boolean mergeSuccess = ResourceMerger.mergeResources(this, patchFile, mergedResourceFile);
                
                if (!mergeSuccess || !mergedResourceFile.exists()) {
                    Log.e(TAG, "Failed to merge resources");
                    return;
                }
                
                // 保存合并资源文件路径
                prefs.edit().putString("merged_resource_file", mergedResourceFile.getAbsolutePath()).apply();
            }
            
            // 加载合并后的完整资源包
            Log.d(TAG, "Loading merged resources from: " + mergedResourceFile.getAbsolutePath());
            ResourcePatcher.loadPatchResources(this, mergedResourceFile.getAbsolutePath());
            Log.i(TAG, "Merged resource patch loaded in attachBaseContext");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load resource patch", e);
        }
    }
    
    /**
     * 从补丁包提取 DEX 文件
     */
    private File extractDexFromPatch(File patchFile, File outputDir) {
        File outputDex = new File(outputDir, "patch.dex");
        
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(patchFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".dex") || name.equals("classes.dex")) {
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputDex)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    return outputDex;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract DEX", e);
        }
        return null;
    }
    
    /**
     * 检查补丁包是否包含资源
     */
    private boolean hasResources(File patchFile) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(patchFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("resources.arsc") || name.startsWith("res/")) {
                    return true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check resources", e);
        }
        return false;
    }
}
