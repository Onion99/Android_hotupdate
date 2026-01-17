package com.orange.patchgen.model;

import java.io.File;

/**
 * 补丁生成结果
 */
public class PatchResult {
    private boolean success;
    private File patchFile;
    private PatchInfo patchInfo;
    private long generateTime;      // 生成耗时(ms)
    private long baseApkSize;
    private long newApkSize;
    private long patchSize;
    private float compressionRatio; // 压缩比
    private DiffSummary diffSummary;
    private String errorMessage;
    private int errorCode;

    public PatchResult() {
    }

    public static PatchResult success(File patchFile, PatchInfo patchInfo, DiffSummary diffSummary) {
        PatchResult result = new PatchResult();
        result.success = true;
        result.patchFile = patchFile;
        result.patchInfo = patchInfo;
        result.diffSummary = diffSummary;
        if (patchFile != null) {
            result.patchSize = patchFile.length();
        }
        return result;
    }

    public static PatchResult failure(int errorCode, String errorMessage) {
        PatchResult result = new PatchResult();
        result.success = false;
        result.errorCode = errorCode;
        result.errorMessage = errorMessage;
        return result;
    }

    public static PatchResult noPatchNeeded() {
        PatchResult result = new PatchResult();
        result.success = true;
        result.diffSummary = new DiffSummary();
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public File getPatchFile() {
        return patchFile;
    }

    public void setPatchFile(File patchFile) {
        this.patchFile = patchFile;
    }

    public PatchInfo getPatchInfo() {
        return patchInfo;
    }

    public void setPatchInfo(PatchInfo patchInfo) {
        this.patchInfo = patchInfo;
    }

    public long getGenerateTime() {
        return generateTime;
    }

    public void setGenerateTime(long generateTime) {
        this.generateTime = generateTime;
    }

    public long getBaseApkSize() {
        return baseApkSize;
    }

    public void setBaseApkSize(long baseApkSize) {
        this.baseApkSize = baseApkSize;
    }

    public long getNewApkSize() {
        return newApkSize;
    }

    public void setNewApkSize(long newApkSize) {
        this.newApkSize = newApkSize;
    }

    public long getPatchSize() {
        return patchSize;
    }

    public void setPatchSize(long patchSize) {
        this.patchSize = patchSize;
    }

    public float getCompressionRatio() {
        return compressionRatio;
    }

    public void setCompressionRatio(float compressionRatio) {
        this.compressionRatio = compressionRatio;
    }

    public DiffSummary getDiffSummary() {
        return diffSummary;
    }

    public void setDiffSummary(DiffSummary diffSummary) {
        this.diffSummary = diffSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public boolean hasPatch() {
        return success && patchFile != null && patchFile.exists();
    }

    /**
     * 计算压缩比
     */
    public void calculateCompressionRatio() {
        if (newApkSize > 0 && patchSize > 0) {
            this.compressionRatio = (float) patchSize / newApkSize;
        }
    }
}
