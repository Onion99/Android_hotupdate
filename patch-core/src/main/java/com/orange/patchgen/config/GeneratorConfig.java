package com.orange.patchgen.config;

import java.io.File;

/**
 * 生成器配置
 * 
 * Requirements: 7.3-7.5
 */
public class GeneratorConfig {
    private EngineType engineType;
    private PatchMode patchMode;
    private int threadCount;
    private long maxMemory;
    private boolean verbose;
    private File tempDir;

    private GeneratorConfig(Builder builder) {
        this.engineType = builder.engineType;
        this.patchMode = builder.patchMode;
        this.threadCount = builder.threadCount;
        this.maxMemory = builder.maxMemory;
        this.verbose = builder.verbose;
        this.tempDir = builder.tempDir;
    }

    public EngineType getEngineType() {
        return engineType;
    }

    public PatchMode getPatchMode() {
        return patchMode;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public File getTempDir() {
        return tempDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EngineType engineType = EngineType.AUTO;
        private PatchMode patchMode = PatchMode.FULL_DEX;
        private int threadCount = Runtime.getRuntime().availableProcessors();
        private long maxMemory = Runtime.getRuntime().maxMemory();
        private boolean verbose = false;
        private File tempDir = new File(System.getProperty("java.io.tmpdir"));

        public Builder engineType(EngineType type) {
            this.engineType = type;
            return this;
        }

        public Builder patchMode(PatchMode mode) {
            this.patchMode = mode;
            return this;
        }

        public Builder threadCount(int count) {
            this.threadCount = count;
            return this;
        }

        public Builder maxMemory(long bytes) {
            this.maxMemory = bytes;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder tempDir(File dir) {
            this.tempDir = dir;
            return this;
        }

        public GeneratorConfig build() {
            return new GeneratorConfig(this);
        }
    }
}
