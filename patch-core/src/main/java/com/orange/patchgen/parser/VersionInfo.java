package com.orange.patchgen.parser;

/**
 * APK 版本信息
 */
public class VersionInfo {
    private String packageName;
    private int versionCode;
    private String versionName;
    private int minSdkVersion;
    private int targetSdkVersion;

    public VersionInfo() {
    }

    public VersionInfo(String packageName, int versionCode, String versionName) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
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

    public int getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @Override
    public String toString() {
        return "VersionInfo{" +
                "packageName='" + packageName + '\'' +
                ", versionCode=" + versionCode +
                ", versionName='" + versionName + '\'' +
                ", minSdkVersion=" + minSdkVersion +
                ", targetSdkVersion=" + targetSdkVersion +
                '}';
    }
}
