package com.orange.patchgen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 补丁变更内容
 */
public class PatchChanges {
    private FileChanges dex;
    private FileChanges resources;
    private FileChanges assets;

    public PatchChanges() {
        this.dex = new FileChanges();
        this.resources = new FileChanges();
        this.assets = new FileChanges();
    }

    public FileChanges getDex() {
        return dex;
    }

    public void setDex(FileChanges dex) {
        this.dex = dex;
    }

    public FileChanges getResources() {
        return resources;
    }

    public void setResources(FileChanges resources) {
        this.resources = resources;
    }

    public FileChanges getAssets() {
        return assets;
    }

    public void setAssets(FileChanges assets) {
        this.assets = assets;
    }

    /**
     * 文件变更列表
     */
    public static class FileChanges {
        private List<String> modified;
        private List<String> added;
        private List<String> deleted;

        public FileChanges() {
            this.modified = new ArrayList<>();
            this.added = new ArrayList<>();
            this.deleted = new ArrayList<>();
        }

        public List<String> getModified() {
            return modified;
        }

        public void setModified(List<String> modified) {
            this.modified = modified;
        }

        public void addModified(String item) {
            if (this.modified == null) {
                this.modified = new ArrayList<>();
            }
            this.modified.add(item);
        }

        public List<String> getAdded() {
            return added;
        }

        public void setAdded(List<String> added) {
            this.added = added;
        }

        public void addAdded(String item) {
            if (this.added == null) {
                this.added = new ArrayList<>();
            }
            this.added.add(item);
        }

        public List<String> getDeleted() {
            return deleted;
        }

        public void setDeleted(List<String> deleted) {
            this.deleted = deleted;
        }

        public void addDeleted(String item) {
            if (this.deleted == null) {
                this.deleted = new ArrayList<>();
            }
            this.deleted.add(item);
        }

        public boolean hasChanges() {
            return (modified != null && !modified.isEmpty()) ||
                   (added != null && !added.isEmpty()) ||
                   (deleted != null && !deleted.isEmpty());
        }
    }
}
