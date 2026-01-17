package com.orange.patch.plugin;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import java.io.File;

/**
 * Gradle 插件配置扩展
 * 
 * 定义 DSL 属性，支持以下配置：
 * - baselineApk: 基线 APK 路径
 * - newApk: 新版本 APK 路径（可选，Android 项目自动获取）
 * - outputDir: 输出目录
 * - signing: 签名配置
 * - engine: 引擎类型 (auto, java, native)
 * - patchMode: 补丁模式 (full_dex, bsdiff)
 * - enabled: 是否启用
 * 
 * Requirements: 7.3-7.6
 * 
 * 使用示例:
 * <pre>
 * patchGenerator {
 *     baselineApk = file("baseline/app-release.apk")
 *     outputDir = file("build/patch")
 *     engine = "auto"
 *     patchMode = "full_dex"
 *     enabled = true
 *     
 *     signing {
 *         keystoreFile = file("keystore/patch.jks")
 *         keystorePassword = "password"
 *         keyAlias = "patch"
 *         keyPassword = "password"
 *     }
 * }
 * </pre>
 */
public class PatchExtension {

    private final Project project;
    
    // 基线 APK 路径
    private File baselineApk;
    
    // 新版本 APK 路径（可选）
    private File newApk;
    
    // 输出目录
    private File outputDir;
    
    // 签名配置
    private SigningExtension signing;
    
    // 引擎类型: auto, java, native
    private String engine = "auto";
    
    // 补丁模式: full_dex, bsdiff
    private String patchMode = "full_dex";
    
    // 是否启用
    private boolean enabled = true;

    public PatchExtension(Project project) {
        this.project = project;
        this.outputDir = new File(project.getBuildDir(), "patch");
        this.signing = new SigningExtension();
    }

    // ==================== Getters and Setters ====================

    public File getBaselineApk() {
        return baselineApk;
    }

    public void setBaselineApk(File baselineApk) {
        this.baselineApk = baselineApk;
    }

    /**
     * 支持 DSL 中使用 baselineApk = file("path")
     */
    public void setBaselineApk(Object baselineApk) {
        this.baselineApk = project.file(baselineApk);
    }

    public File getNewApk() {
        return newApk;
    }

    public void setNewApk(File newApk) {
        this.newApk = newApk;
    }

    /**
     * 支持 DSL 中使用 newApk = file("path")
     */
    public void setNewApk(Object newApk) {
        this.newApk = project.file(newApk);
    }

    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * 支持 DSL 中使用 outputDir = file("path")
     */
    public void setOutputDir(Object outputDir) {
        this.outputDir = project.file(outputDir);
    }

    public SigningExtension getSigning() {
        return signing;
    }

    public void setSigning(SigningExtension signing) {
        this.signing = signing;
    }

    /**
     * 支持 DSL 中使用 signing { ... } 闭包配置
     */
    public void signing(Action<SigningExtension> action) {
        if (signing == null) {
            signing = new SigningExtension();
        }
        action.execute(signing);
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getPatchMode() {
        return patchMode;
    }

    public void setPatchMode(String patchMode) {
        this.patchMode = patchMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ==================== Validation ====================

    /**
     * 验证配置是否完整
     * 
     * @throws IllegalStateException 如果配置不完整
     */
    public void validate() throws IllegalStateException {
        if (baselineApk == null) {
            throw new IllegalStateException("baselineApk is not configured");
        }
        
        if (!baselineApk.exists()) {
            throw new IllegalStateException("baselineApk does not exist: " + baselineApk.getAbsolutePath());
        }
        
        if (outputDir == null) {
            throw new IllegalStateException("outputDir is not configured");
        }
        
        // 验证引擎类型
        if (engine != null && !engine.isEmpty()) {
            String normalizedEngine = engine.toLowerCase();
            if (!normalizedEngine.equals("auto") && 
                !normalizedEngine.equals("java") && 
                !normalizedEngine.equals("native")) {
                throw new IllegalStateException("Invalid engine type: " + engine + 
                        ". Must be one of: auto, java, native");
            }
        }
        
        // 验证补丁模式
        if (patchMode != null && !patchMode.isEmpty()) {
            String normalizedMode = patchMode.toLowerCase();
            if (!normalizedMode.equals("full_dex") && !normalizedMode.equals("bsdiff")) {
                throw new IllegalStateException("Invalid patchMode: " + patchMode + 
                        ". Must be one of: full_dex, bsdiff");
            }
        }
        
        // 验证签名配置（如果提供）
        if (signing != null && signing.hasAnyConfig()) {
            signing.validate();
        }
    }

    // ==================== Signing Extension ====================

    /**
     * 签名配置扩展
     */
    public static class SigningExtension {
        private File keystoreFile;
        private String keystorePassword;
        private String keyAlias;
        private String keyPassword;

        public File getKeystoreFile() {
            return keystoreFile;
        }

        public void setKeystoreFile(File keystoreFile) {
            this.keystoreFile = keystoreFile;
        }

        public void setKeystoreFile(Object keystoreFile) {
            if (keystoreFile instanceof File) {
                this.keystoreFile = (File) keystoreFile;
            } else if (keystoreFile != null) {
                this.keystoreFile = new File(keystoreFile.toString());
            }
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public void setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }

        /**
         * 检查是否有任何配置
         */
        public boolean hasAnyConfig() {
            return keystoreFile != null || 
                   keystorePassword != null || 
                   keyAlias != null || 
                   keyPassword != null;
        }

        /**
         * 检查配置是否完整
         */
        public boolean isComplete() {
            return keystoreFile != null && 
                   keystorePassword != null && !keystorePassword.isEmpty() &&
                   keyAlias != null && !keyAlias.isEmpty() &&
                   keyPassword != null && !keyPassword.isEmpty();
        }

        /**
         * 验证签名配置
         * 
         * @throws IllegalStateException 如果配置不完整
         */
        public void validate() throws IllegalStateException {
            if (keystoreFile == null) {
                throw new IllegalStateException("signing.keystoreFile is not configured");
            }
            if (!keystoreFile.exists()) {
                throw new IllegalStateException("signing.keystoreFile does not exist: " + 
                        keystoreFile.getAbsolutePath());
            }
            if (keystorePassword == null || keystorePassword.isEmpty()) {
                throw new IllegalStateException("signing.keystorePassword is not configured");
            }
            if (keyAlias == null || keyAlias.isEmpty()) {
                throw new IllegalStateException("signing.keyAlias is not configured");
            }
            if (keyPassword == null || keyPassword.isEmpty()) {
                throw new IllegalStateException("signing.keyPassword is not configured");
            }
        }
    }
}
