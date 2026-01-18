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

            // 获取已应用的补丁文件
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File appliedFile = new java.io.File(appliedDir, "current_patch.zip");

            if (!appliedFile.exists()) {
                Log.w(TAG, "Applied patch file not found: " + appliedFile.getAbsolutePath());
                return;
            }

            // ✅ 验证补丁完整性（防止篡改）
            if (!verifyPatchIntegrity(appliedFile, prefs)) {
                Log.e(TAG, "⚠️ Patch integrity verification failed");

                // 尝试恢复
                if (!recoverPatch(appliedPatchId, appliedFile, prefs)) {
                    Log.e(TAG, "⚠️ Patch recovery failed, patch has been cleared");
                    return;
                }
            }
            
            // ✅ APK 签名验证（启动时验证）
            if (hasApkSignature(appliedFile)) {
                Log.d(TAG, "检测到 APK 签名，开始验证...");
                ApkSignatureVerifier signatureVerifier = new ApkSignatureVerifier(this);
                boolean signatureValid = signatureVerifier.verifyPatchSignature(appliedFile);
                
                if (!signatureValid) {
                    Log.e(TAG, "⚠️ APK 签名验证失败！补丁可能被篡改。");
                    
                    // 清除被篡改的补丁
                    prefs.edit()
                        .remove("applied_patch_id")
                        .remove("applied_patch_hash")
                        .apply();
                    
                    if (appliedFile.exists()) {
                        appliedFile.delete();
                    }
                    
                    Log.e(TAG, "⚠️ 已清除被篡改的补丁");
                    return;
                }
                
                Log.d(TAG, "✅ APK 签名验证通过（启动时）");
            }

            // 检查补丁是否是 ZIP 密码保护的
            java.io.File actualPatchFile = appliedFile;
            if (isZipPasswordProtected(appliedFile)) {
                Log.d(TAG, "Patch is ZIP password protected, decrypting...");
                
                // 获取保存的自定义密码（如果有）
                String customPassword = prefs.getString("custom_zip_password", null);
                
                // 自动解密 ZIP 密码保护的补丁
                actualPatchFile = decryptZipPatchOnLoad(appliedFile, customPassword);
                
                if (actualPatchFile == null) {
                    Log.e(TAG, "Failed to decrypt ZIP password protected patch");
                    return;
                }
                
                Log.d(TAG, "✓ ZIP password protected patch decrypted");
            }

            String patchPath = actualPatchFile.getAbsolutePath();

            // 检查补丁是否包含资源
            if (hasResourcePatch(actualPatchFile)) {
                Log.d(TAG, "Patch contains resources, merging with original APK");

                // 使用 ResourceMerger 合并资源（Tinker 的方式）
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");

                boolean merged = ResourceMerger.mergeResources(
                    this, actualPatchFile, mergedResourceFile);

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

    /**
     * 验证补丁完整性
     */
    private boolean verifyPatchIntegrity(java.io.File patchFile, android.content.SharedPreferences prefs) {
        if (!patchFile.exists()) {
            return false;
        }

        String savedHash = prefs.getString("applied_patch_hash", null);
        if (savedHash == null || savedHash.isEmpty()) {
            Log.w(TAG, "No saved hash, patch may be from old version (backward compatible)");
            return true; // 向后兼容
        }

        String currentHash = calculateSHA256(patchFile);
        if (currentHash == null) {
            Log.e(TAG, "Failed to calculate current hash");
            return false;
        }

        boolean valid = savedHash.equals(currentHash);

        if (valid) {
            Log.d(TAG, "✅ Patch integrity verified: " + currentHash.substring(0, 16) + "...");
        } else {
            Log.e(TAG, "⚠️ PATCH INTEGRITY CHECK FAILED!");
            Log.e(TAG, "Expected: " + savedHash);
            Log.e(TAG, "Actual:   " + currentHash);
        }

        return valid;
    }

    /**
     * 恢复补丁
     */
    private boolean recoverPatch(String patchId, java.io.File appliedFile, android.content.SharedPreferences prefs) {
        int tamperCount = prefs.getInt("tamper_count", 0) + 1;
        prefs.edit().putInt("tamper_count", tamperCount).apply();

        Log.e(TAG, "⚠️ Patch tampered! Attempt: " + tamperCount + "/3");

        // 超过限制，清除补丁
        if (tamperCount >= 3) {
            Log.e(TAG, "⚠️ Too many tamper attempts, clearing patch");
            prefs.edit()
                .remove("applied_patch_id")
                .remove("applied_patch_hash")
                .apply();

            if (appliedFile.exists()) {
                appliedFile.delete();
            }
            return false;
        }

        // 尝试从加密存储恢复
        Log.i(TAG, "Attempting to recover from encrypted storage...");

        try {
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File patchesDir = new java.io.File(updateDir, "patches");
            java.io.File encryptedFile = new java.io.File(patchesDir, patchId + ".enc");

            if (!encryptedFile.exists()) {
                Log.e(TAG, "Encrypted patch not found");
                return false;
            }

            // 使用 SecurityManager 解密
            SecurityManager securityManager = new SecurityManager(this);
            java.io.File decryptedFile = securityManager.decryptPatch(encryptedFile);

            // 替换被篡改的文件
            if (appliedFile.exists()) {
                appliedFile.delete();
            }

            if (!decryptedFile.renameTo(appliedFile)) {
                // 复制文件
                copyFile(decryptedFile, appliedFile);
                decryptedFile.delete();
            }

            // 重新计算哈希
            String newHash = calculateSHA256(appliedFile);
            if (newHash != null) {
                prefs.edit().putString("applied_patch_hash", newHash).apply();
            }

            // 验证恢复结果
            if (verifyPatchIntegrity(appliedFile, prefs)) {
                Log.i(TAG, "✅ Patch recovered successfully");
                prefs.edit().putInt("tamper_count", 0).apply();
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to recover patch", e);
        }

        return false;
    }

    /**
     * 计算 SHA-256 哈希
     */
    private String calculateSHA256(java.io.File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate SHA-256", e);
            return null;
        }
    }

    /**
     * 复制文件
     */
    private void copyFile(java.io.File source, java.io.File target) throws java.io.IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.flush();
        fos.close();
        fis.close();
    }

    /**
     * 检查补丁是否是 ZIP 密码保护的
     */
    private boolean isZipPasswordProtected(java.io.File patchFile) {
        try {
            net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile);
            return zipFile.isEncrypted();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查补丁是否有 APK 签名
     */
    private boolean hasApkSignature(java.io.File patchFile) {
        // 方法1: 检查 zip 内部是否有 META-INF/ 签名文件（新方案）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否有 META-INF/ 签名文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    zipFile.close();
                    Log.d(TAG, "✓ 检测到 APK 签名文件: " + name);
                    return true;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.d(TAG, "检查 META-INF/ 签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查 zip 内部是否有 signature.sig 标记文件（向后兼容）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                Log.d(TAG, "✓ 检测到 zip 内部的签名标记文件");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "检查 zip 内部签名标记失败: " + e.getMessage());
        }
        
        // 方法3: 检查外部 .sig 文件（向后兼容）
        java.io.File signatureFile = new java.io.File(patchFile.getPath() + ".sig");
        if (signatureFile.exists()) {
            Log.d(TAG, "✓ 检测到外部签名文件");
            return true;
        }
        
        return false;
    }
    
    /**
     * 在加载时解密 ZIP 密码保护的补丁
     * 使用从应用签名派生的密码或保存的自定义密码自动解密
     * 
     * @param encryptedPatch 加密的补丁文件
     * @param customPassword 自定义密码（如果有）
     */
    private java.io.File decryptZipPatchOnLoad(java.io.File encryptedPatch, String customPassword) {
        try {
            // 创建 ZipPasswordManager 实例
            ZipPasswordManager zipPasswordManager = new ZipPasswordManager(this);
            
            // 获取密码：优先使用自定义密码，否则使用派生密码
            String zipPassword;
            if (customPassword != null && !customPassword.isEmpty()) {
                Log.d(TAG, "Using custom ZIP password");
                zipPassword = customPassword;
            } else {
                Log.d(TAG, "Using derived ZIP password");
                zipPassword = zipPasswordManager.getZipPassword();
            }
            
            // 解密到临时目录
            java.io.File tempDir = new java.io.File(getCacheDir(), "patch_load_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            boolean extracted = zipPasswordManager.extractEncryptedZip(encryptedPatch, tempDir, zipPassword);
            
            if (!extracted) {
                Log.e(TAG, "Failed to extract encrypted ZIP");
                deleteDirectory(tempDir);
                return null;
            }
            
            // 重新打包为未加密的 ZIP（临时文件）
            java.io.File decryptedZip = new java.io.File(getCacheDir(), "patch_decrypted_" + System.currentTimeMillis() + ".zip");
            repackZip(tempDir, decryptedZip);
            
            // 清理临时目录
            deleteDirectory(tempDir);
            
            return decryptedZip;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt ZIP password protected patch", e);
            return null;
        }
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(java.io.File directory) {
        if (directory.exists()) {
            java.io.File[] files = directory.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 重新打包目录为 ZIP 文件（不加密，不压缩）
     */
    private void repackZip(java.io.File sourceDir, java.io.File destZip) throws Exception {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(destZip);
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setCompressionMethod(net.lingala.zip4j.model.enums.CompressionMethod.STORE);
        
        // 添加目录中的所有文件和文件夹
        java.io.File[] files = sourceDir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    zipFile.addFolder(file, params);
                } else {
                    zipFile.addFile(file, params);
                }
            }
        }
    }
}
