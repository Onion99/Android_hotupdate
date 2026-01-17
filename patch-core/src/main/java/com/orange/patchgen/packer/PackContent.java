package com.orange.patchgen.packer;

import com.orange.patchgen.model.PatchInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 补丁打包内容
 * 
 * 包含需要打包到补丁文件中的所有内容。
 * 
 * Requirements: 4.1-4.6
 */
public class PackContent {
    private PatchInfo patchInfo;
    private List<File> dexFiles;
    private File resDir;
    private File assetsDir;
    private File resourcesArsc;  // resources.arsc 文件
    private List<File> bsdiffFiles;  // BsDiff 模式下的差异文件

    public PackContent() {
        this.dexFiles = new ArrayList<>();
        this.bsdiffFiles = new ArrayList<>();
    }

    public PatchInfo getPatchInfo() {
        return patchInfo;
    }

    public void setPatchInfo(PatchInfo patchInfo) {
        this.patchInfo = patchInfo;
    }

    public List<File> getDexFiles() {
        return dexFiles;
    }

    public void setDexFiles(List<File> dexFiles) {
        this.dexFiles = dexFiles;
    }

    public void addDexFile(File dexFile) {
        if (this.dexFiles == null) {
            this.dexFiles = new ArrayList<>();
        }
        this.dexFiles.add(dexFile);
    }

    public File getResDir() {
        return resDir;
    }

    public void setResDir(File resDir) {
        this.resDir = resDir;
    }

    public File getAssetsDir() {
        return assetsDir;
    }

    public void setAssetsDir(File assetsDir) {
        this.assetsDir = assetsDir;
    }

    public File getResourcesArsc() {
        return resourcesArsc;
    }

    public void setResourcesArsc(File resourcesArsc) {
        this.resourcesArsc = resourcesArsc;
    }

    public List<File> getBsdiffFiles() {
        return bsdiffFiles;
    }

    public void setBsdiffFiles(List<File> bsdiffFiles) {
        this.bsdiffFiles = bsdiffFiles;
    }

    public void addBsdiffFile(File bsdiffFile) {
        if (this.bsdiffFiles == null) {
            this.bsdiffFiles = new ArrayList<>();
        }
        this.bsdiffFiles.add(bsdiffFile);
    }

    /**
     * 检查是否有 dex 变更
     */
    public boolean hasDexChanges() {
        return dexFiles != null && !dexFiles.isEmpty();
    }

    /**
     * 检查是否有资源变更
     */
    public boolean hasResourceChanges() {
        return resDir != null && resDir.exists() && resDir.isDirectory();
    }

    /**
     * 检查是否有 resources.arsc
     */
    public boolean hasResourcesArsc() {
        return resourcesArsc != null && resourcesArsc.exists();
    }

    /**
     * 检查是否有 assets 变更
     */
    public boolean hasAssetChanges() {
        return assetsDir != null && assetsDir.exists() && assetsDir.isDirectory();
    }

    /**
     * 检查是否有 bsdiff 文件
     */
    public boolean hasBsdiffFiles() {
        return bsdiffFiles != null && !bsdiffFiles.isEmpty();
    }

    /**
     * 验证内容是否有效
     */
    public boolean isValid() {
        return patchInfo != null && patchInfo.isValid();
    }

    public static class Builder {
        private final PackContent content;

        public Builder() {
            this.content = new PackContent();
        }

        public Builder patchInfo(PatchInfo patchInfo) {
            content.setPatchInfo(patchInfo);
            return this;
        }

        public Builder dexFiles(List<File> dexFiles) {
            content.setDexFiles(dexFiles);
            return this;
        }

        public Builder addDexFile(File dexFile) {
            content.addDexFile(dexFile);
            return this;
        }

        public Builder resDir(File resDir) {
            content.setResDir(resDir);
            return this;
        }

        public Builder assetsDir(File assetsDir) {
            content.setAssetsDir(assetsDir);
            return this;
        }

        public Builder resourcesArsc(File resourcesArsc) {
            content.setResourcesArsc(resourcesArsc);
            return this;
        }

        public Builder bsdiffFiles(List<File> bsdiffFiles) {
            content.setBsdiffFiles(bsdiffFiles);
            return this;
        }

        public Builder addBsdiffFile(File bsdiffFile) {
            content.addBsdiffFile(bsdiffFile);
            return this;
        }

        public PackContent build() {
            return content;
        }
    }
}
