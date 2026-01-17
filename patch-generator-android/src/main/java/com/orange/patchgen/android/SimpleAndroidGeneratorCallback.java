package com.orange.patchgen.android;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orange.patchgen.model.PatchResult;

/**
 * AndroidGeneratorCallback 的简单实现基类
 * 
 * 所有方法都是空实现，子类可以选择性地覆盖需要的方法。
 */
public class SimpleAndroidGeneratorCallback implements AndroidGeneratorCallback {
    
    @Override
    public void onStart() {
        // Empty implementation
    }
    
    @Override
    public void onParseStart(@NonNull String apkPath) {
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
    public void onCompareProgress(int current, int total, @Nullable String currentFile) {
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
    public void onProgress(int percent, @NonNull String stage) {
        // Empty implementation
    }

    @Override
    public void onComplete(@NonNull PatchResult result) {
        // Empty implementation
    }

    @Override
    public void onError(int errorCode, @NonNull String message) {
        // Empty implementation
    }
    
    @Override
    public void onCancelled() {
        // Empty implementation
    }
}
