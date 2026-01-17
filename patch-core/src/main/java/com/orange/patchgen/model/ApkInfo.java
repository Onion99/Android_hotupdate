package com.orange.patchgen.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * APK 文件信息
 */
public class ApkInfo {
    private String packageName;
    private int versionCode;
    private String versionName;
    private List<DexInfo> dexFiles;
    private List<ResourceInfo> resources;
    private List<AssetInfo> assets;
    private File extractedDir;

    public ApkInfo() {
        this.dexFiles = new ArrayList<>();
        this.resources = new ArrayList<>();
        this.assets = new ArrayList<>();
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public List<DexInfo> getDexFiles() {
        return dexFiles;
    }

    public void setDexFiles(List<DexInfo> dexFiles) {
        this.dexFiles = dexFiles;
    }

    public void addDexFile(DexInfo dexInfo) {
        if (this.dexFiles == null) {
            this.dexFiles = new ArrayList<>();
        }
        this.dexFiles.add(dexInfo);
    }

    public List<ResourceInfo> getResources() {
        return resources;
    }

    public void setResources(List<ResourceInfo> resources) {
        this.resources = resources;
    }

    public void addResource(ResourceInfo resourceInfo) {
        if (this.resources == null) {
            this.resources = new ArrayList<>();
        }
        this.resources.add(resourceInfo);
    }

    public List<AssetInfo> getAssets() {
        return assets;
    }

    public void setAssets(List<AssetInfo> assets) {
        this.assets = assets;
    }

    public void addAsset(AssetInfo assetInfo) {
        if (this.assets == null) {
            this.assets = new ArrayList<>();
        }
        this.assets.add(assetInfo);
    }

    public File getExtractedDir() {
        return extractedDir;
    }

    public void setExtractedDir(File extractedDir) {
        this.extractedDir = extractedDir;
    }
}
