package com.orange.patchgen.differ;

import java.util.ArrayList;
import java.util.List;

/**
 * Dex 差异比较结果
 * 
 * 包含两个 dex 文件之间的差异信息：修改、新增、删除的类。
 * 
 * Requirements: 2.2, 2.3, 2.4
 */
public class DexDiffResult {
    private String dexName;
    private List<String> modifiedClasses;
    private List<String> addedClasses;
    private List<String> deletedClasses;
    private boolean hasChanges;

    public DexDiffResult() {
        this.modifiedClasses = new ArrayList<>();
        this.addedClasses = new ArrayList<>();
        this.deletedClasses = new ArrayList<>();
        this.hasChanges = false;
    }

    public DexDiffResult(String dexName) {
        this();
        this.dexName = dexName;
    }

    public String getDexName() {
        return dexName;
    }

    public void setDexName(String dexName) {
        this.dexName = dexName;
    }

    public List<String> getModifiedClasses() {
        return modifiedClasses;
    }

    public void setModifiedClasses(List<String> modifiedClasses) {
        this.modifiedClasses = modifiedClasses;
        updateHasChanges();
    }

    public void addModifiedClass(String className) {
        if (this.modifiedClasses == null) {
            this.modifiedClasses = new ArrayList<>();
        }
        this.modifiedClasses.add(className);
        this.hasChanges = true;
    }

    public List<String> getAddedClasses() {
        return addedClasses;
    }

    public void setAddedClasses(List<String> addedClasses) {
        this.addedClasses = addedClasses;
        updateHasChanges();
    }

    public void addAddedClass(String className) {
        if (this.addedClasses == null) {
            this.addedClasses = new ArrayList<>();
        }
        this.addedClasses.add(className);
        this.hasChanges = true;
    }

    public List<String> getDeletedClasses() {
        return deletedClasses;
    }

    public void setDeletedClasses(List<String> deletedClasses) {
        this.deletedClasses = deletedClasses;
        updateHasChanges();
    }

    public void addDeletedClass(String className) {
        if (this.deletedClasses == null) {
            this.deletedClasses = new ArrayList<>();
        }
        this.deletedClasses.add(className);
        this.hasChanges = true;
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public void setHasChanges(boolean hasChanges) {
        this.hasChanges = hasChanges;
    }

    private void updateHasChanges() {
        this.hasChanges = (modifiedClasses != null && !modifiedClasses.isEmpty()) ||
                          (addedClasses != null && !addedClasses.isEmpty()) ||
                          (deletedClasses != null && !deletedClasses.isEmpty());
    }

    /**
     * 获取所有变更类的总数
     */
    public int getTotalChanges() {
        int count = 0;
        if (modifiedClasses != null) count += modifiedClasses.size();
        if (addedClasses != null) count += addedClasses.size();
        if (deletedClasses != null) count += deletedClasses.size();
        return count;
    }

    @Override
    public String toString() {
        return "DexDiffResult{" +
                "dexName='" + dexName + '\'' +
                ", modifiedClasses=" + (modifiedClasses != null ? modifiedClasses.size() : 0) +
                ", addedClasses=" + (addedClasses != null ? addedClasses.size() : 0) +
                ", deletedClasses=" + (deletedClasses != null ? deletedClasses.size() : 0) +
                ", hasChanges=" + hasChanges +
                '}';
    }
}
