package com.orange.patchgen.callback;

import com.orange.patchgen.model.PatchResult;

/**
 * 简单回调实现基类
 * 所有方法提供空实现，子类可选择性覆盖
 * 
 * Requirements: 9.1-9.7
 */
public class SimpleGeneratorCallback implements GeneratorCallback {

    @Override
    public void onParseStart(String apkPath) {
        // Empty implementation
    }

    @Override
    public void onParseProgress(int current, int total) {
        // Empty implementation
    }

    @Override
    public void onCompareStart() {
        // Empty implementation
    }

    @Override
    public void onCompareProgress(int current, int total, String currentFile) {
        // Empty implementation
    }

    @Override
    public void onPackStart() {
        // Empty implementation
    }

    @Override
    public void onPackProgress(long current, long total) {
        // Empty implementation
    }

    @Override
    public void onSignStart() {
        // Empty implementation
    }

    @Override
    public void onComplete(PatchResult result) {
        // Empty implementation
    }

    @Override
    public void onError(int errorCode, String message) {
        // Empty implementation
    }

    /**
     * 计算百分比进度
     */
    protected int calculatePercent(long current, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) (current * 100 / total);
    }
}
