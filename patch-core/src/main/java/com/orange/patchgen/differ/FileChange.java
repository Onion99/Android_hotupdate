package com.orange.patchgen.differ;

/**
 * 文件变更信息
 * 
 * 记录单个文件的变更详情，包括路径、哈希值和大小变化。
 * 
 * Requirements: 3.2, 3.3, 3.4
 */
public class FileChange {
    private String relativePath;
    private String oldMd5;
    private String newMd5;
    private long oldSize;
    private long newSize;

    public FileChange() {
    }

    public FileChange(String relativePath) {
        this.relativePath = relativePath;
    }

    public FileChange(String relativePath, String oldMd5, String newMd5, long oldSize, long newSize) {
        this.relativePath = relativePath;
        this.oldMd5 = oldMd5;
        this.newMd5 = newMd5;
        this.oldSize = oldSize;
        this.newSize = newSize;
    }

    /**
     * 创建一个表示新增文件的 FileChange
     */
    public static FileChange added(String relativePath, String md5, long size) {
        FileChange change = new FileChange(relativePath);
        change.setNewMd5(md5);
        change.setNewSize(size);
        return change;
    }

    /**
     * 创建一个表示修改文件的 FileChange
     */
    public static FileChange modified(String relativePath, String oldMd5, String newMd5, 
                                       long oldSize, long newSize) {
        return new FileChange(relativePath, oldMd5, newMd5, oldSize, newSize);
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getOldMd5() {
        return oldMd5;
    }

    public void setOldMd5(String oldMd5) {
        this.oldMd5 = oldMd5;
    }

    public String getNewMd5() {
        return newMd5;
    }

    public void setNewMd5(String newMd5) {
        this.newMd5 = newMd5;
    }

    public long getOldSize() {
        return oldSize;
    }

    public void setOldSize(long oldSize) {
        this.oldSize = oldSize;
    }

    public long getNewSize() {
        return newSize;
    }

    public void setNewSize(long newSize) {
        this.newSize = newSize;
    }

    @Override
    public String toString() {
        return "FileChange{" +
                "relativePath='" + relativePath + '\'' +
                ", oldMd5='" + oldMd5 + '\'' +
                ", newMd5='" + newMd5 + '\'' +
                ", oldSize=" + oldSize +
                ", newSize=" + newSize +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileChange that = (FileChange) o;
        return relativePath != null ? relativePath.equals(that.relativePath) : that.relativePath == null;
    }

    @Override
    public int hashCode() {
        return relativePath != null ? relativePath.hashCode() : 0;
    }
}
