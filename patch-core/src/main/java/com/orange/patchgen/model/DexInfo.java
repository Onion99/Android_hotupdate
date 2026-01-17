package com.orange.patchgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Dex 文件信息
 */
public class DexInfo {
    private String fileName;        // classes.dex, classes2.dex, etc.
    private String md5;
    private long size;
    private List<String> classNames;

    public DexInfo() {
        this.classNames = new ArrayList<>();
    }

    public DexInfo(String fileName, String md5, long size) {
        this.fileName = fileName;
        this.md5 = md5;
        this.size = size;
        this.classNames = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public List<String> getClassNames() {
        return classNames;
    }

    public void setClassNames(List<String> classNames) {
        this.classNames = classNames;
    }

    public void addClassName(String className) {
        if (this.classNames == null) {
            this.classNames = new ArrayList<>();
        }
        this.classNames.add(className);
    }
}
