package com.orange.patchgen.model;

/**
 * Assets 文件信息
 */
public class AssetInfo {
    private String relativePath;
    private String md5;
    private long size;

    public AssetInfo() {
    }

    public AssetInfo(String relativePath, String md5, long size) {
        this.relativePath = relativePath;
        this.md5 = md5;
        this.size = size;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
