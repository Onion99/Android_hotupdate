package com.orange.patchgen.model;

/**
 * 资源文件信息
 */
public class ResourceInfo {
    private String relativePath;
    private String md5;
    private long size;
    private ResourceType type;

    public enum ResourceType {
        RES,        // res/ 目录下的资源
        ASSETS,     // assets/ 目录下的资源
        OTHER       // 其他文件
    }

    public ResourceInfo() {
    }

    public ResourceInfo(String relativePath, String md5, long size, ResourceType type) {
        this.relativePath = relativePath;
        this.md5 = md5;
        this.size = size;
        this.type = type;
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

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }
}
