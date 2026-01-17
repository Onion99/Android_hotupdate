package com.orange.patchgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 差异比较摘要
 */
public class DiffSummary {
    private int modifiedClasses;
    private int addedClasses;
    private int deletedClasses;
    private int modifiedResources;
    private int addedResources;
    private int deletedResources;
    private int modifiedAssets;
    private int addedAssets;
    private int deletedAssets;
    private List<String> modifiedFiles;

    public DiffSummary() {
        this.modifiedFiles = new ArrayList<>();
    }

    public int getModifiedClasses() {
        return modifiedClasses;
    }

    public void setModifiedClasses(int modifiedClasses) {
        this.modifiedClasses = modifiedClasses;
    }

    public int getAddedClasses() {
        return addedClasses;
    }

    public void setAddedClasses(int addedClasses) {
        this.addedClasses = addedClasses;
    }

    public int getDeletedClasses() {
        return deletedClasses;
    }

    public void setDeletedClasses(int deletedClasses) {
        this.deletedClasses = deletedClasses;
    }

    public int getModifiedResources() {
        return modifiedResources;
    }

    public void setModifiedResources(int modifiedResources) {
        this.modifiedResources = modifiedResources;
    }

    public int getAddedResources() {
        return addedResources;
    }

    public void setAddedResources(int addedResources) {
        this.addedResources = addedResources;
    }

    public int getDeletedResources() {
        return deletedResources;
    }

    public void setDeletedResources(int deletedResources) {
        this.deletedResources = deletedResources;
    }

    public int getModifiedAssets() {
        return modifiedAssets;
    }

    public void setModifiedAssets(int modifiedAssets) {
        this.modifiedAssets = modifiedAssets;
    }

    public int getAddedAssets() {
        return addedAssets;
    }

    public void setAddedAssets(int addedAssets) {
        this.addedAssets = addedAssets;
    }

    public int getDeletedAssets() {
        return deletedAssets;
    }

    public void setDeletedAssets(int deletedAssets) {
        this.deletedAssets = deletedAssets;
    }

    public List<String> getModifiedFiles() {
        return modifiedFiles;
    }

    public void setModifiedFiles(List<String> modifiedFiles) {
        this.modifiedFiles = modifiedFiles;
    }

    public void addModifiedFile(String file) {
        if (this.modifiedFiles == null) {
            this.modifiedFiles = new ArrayList<>();
        }
        this.modifiedFiles.add(file);
    }

    public boolean hasChanges() {
        return modifiedClasses > 0 || addedClasses > 0 || deletedClasses > 0 ||
               modifiedResources > 0 || addedResources > 0 || deletedResources > 0 ||
               modifiedAssets > 0 || addedAssets > 0 || deletedAssets > 0;
    }

    public int getTotalChanges() {
        return modifiedClasses + addedClasses + deletedClasses +
               modifiedResources + addedResources + deletedResources +
               modifiedAssets + addedAssets + deletedAssets;
    }
}
