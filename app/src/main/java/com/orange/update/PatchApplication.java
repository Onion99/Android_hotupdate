package com.orange.update;

import android.app.Application;
import android.content.Context;
import android.util.Log;

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
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 在最早的时机加载补丁
        loadPatchIfNeeded();
    }
    
    /**
     * 加载已应用的补丁
     * 
     * 1. 检查是否有已应用的补丁
     * 2. 验证补丁完整性（防止篡改）
     * 3. 如果补丁包含资源，使用 ResourceMerger 合并原始 APK 和补丁资源
     * 4. 生成完整资源包到 merged_resources.apk
     * 5. 加载完整资源包（而不是直接使用补丁）
     * 6. 加载 DEX 补丁
     */
    private void loadPatchIfNeeded() {
        try {
            // 注意：在 attachBaseContext 中不能使用 getApplicationContext()
            // 因为 Application 还没有完全初始化，需要手动创建 SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
            String appliedPatchId = prefs.getString("applied_patch_id", null);
            
            if (appliedPatchId == null || appliedPatchId.isEmpty()) {
                Log.d(TAG, "No applied patch to load");
                return;
            }
            
            Log.d(TAG, "Loading applied patch: " + appliedPatchId);
            
            // ✅ 验证补丁完整性（防止篡改）
            PatchStorage storage = new PatchStorage(this);
            if (!storage.verifyAndRecoverPatch()) {
                Log.e(TAG, "⚠️ Patch integrity verification failed and recovery failed");
                Log.e(TAG, "Patch has been cleared for security reasons");
                return;
            }
            
            // 获取已应用的补丁文件
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File appliedFile = new java.io.File(appliedDir, "current_patch.zip");
            
            if (!appliedFile.exists()) {
                Log.w(TAG, "Applied patch file not found: " + appliedFile.getAbsolutePath());
                return;
            }
            
            String patchPath = appliedFile.getAbsolutePath();
            
            // 检查补丁是否包含资源
            if (hasResourcePatch(appliedFile)) {
                Log.d(TAG, "Patch contains resources, merging with original APK");
                
                // 使用 ResourceMerger 合并资源（Tinker 的方式）
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
                
                boolean merged = ResourceMerger.mergeResources(
                    this, appliedFile, mergedResourceFile);
                
                if (merged && mergedResourceFile.exists()) {
                    Log.i(TAG, "Resources merged successfully, size: " + mergedResourceFile.length());
                    // 使用合并后的完整资源包
                    patchPath = mergedResourceFile.getAbsolutePath();
                } else {
                    Log.w(TAG, "Failed to merge resources, using patch directly");
                }
            }
            
            // 注入 DEX 补丁
            if (!DexPatcher.isPatchInjected(this, patchPath)) {
                DexPatcher.injectPatchDex(this, patchPath);
                Log.d(TAG, "Dex patch loaded successfully");
            }
            
            // 加载资源补丁（使用合并后的完整资源包）
            try {
                ResourcePatcher.loadPatchResources(this, patchPath);
                Log.d(TAG, "Resource patch loaded successfully");
            } catch (ResourcePatcher.PatchResourceException e) {
                Log.w(TAG, "Failed to load resource patch", e);
            }
            
            Log.i(TAG, "✅ Patch loading completed with integrity verification");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch in attachBaseContext", e);
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(java.io.File patchFile) {
        String fileName = patchFile.getName().toLowerCase(java.util.Locale.ROOT);
        
        // 如果是 APK 或 ZIP 文件，可能包含资源
        if (fileName.endsWith(".apk") || fileName.endsWith(".zip")) {
            return true;
        }
        
        // 如果是纯 DEX 文件，不包含资源
        if (fileName.endsWith(".dex")) {
            return false;
        }
        
        // 检查文件魔数
        try {
            byte[] header = new byte[4];
            java.io.FileInputStream fis = new java.io.FileInputStream(patchFile);
            fis.read(header);
            fis.close();
            
            // ZIP/APK 魔数: PK (0x50 0x4B)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }
            
            // DEX 魔数: dex\n (0x64 0x65 0x78 0x0A)
            if (header[0] == 0x64 && header[1] == 0x65 && 
                header[2] == 0x78 && header[3] == 0x0A) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check patch file type", e);
        }
        
        return false;
    }
}
