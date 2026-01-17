package com.orange.patchgen.android;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.orange.patchgen.model.PatchResult;

/**
 * Android 端补丁生成回调接口
 * 
 * 扩展了基础回调接口，增加了 Android 特有的回调方法。
 * 所有回调方法都在后台线程调用，如需更新 UI 请自行切换到主线程。
 * 
 * Requirements: 9.6, 9.7
 */
public interface AndroidGeneratorCallback {
    
    /**
     * 生成开始
     */
    void onStart();
    
    /**
     * 开始解析 APK
     * 
     * @param apkPath APK 文件路径
     */
    void onParseStart(@NonNull String apkPath);

    /**
     * 解析进度
     * 
     * @param current 当前进度
     * @param total 总进度
     */
    void onParseProgress(int current, int total);

    /**
     * 开始比较差异
     */
    void onCompareStart();

    /**
     * 比较进度
     * 
     * @param current 当前进度
     * @param total 总进度
     * @param currentFile 当前处理的文件
     */
    void onCompareProgress(int current, int total, @Nullable String currentFile);

    /**
     * 开始打包
     */
    void onPackStart();

    /**
     * 打包进度
     * 
     * @param current 当前字节数
     * @param total 总字节数
     */
    void onPackProgress(long current, long total);

    /**
     * 开始签名
     */
    void onSignStart();
    
    /**
     * 总体进度更新
     * 
     * @param percent 进度百分比 (0-100)
     * @param stage 当前阶段描述
     */
    void onProgress(int percent, @NonNull String stage);

    /**
     * 生成完成
     * 
     * @param result 生成结果
     */
    void onComplete(@NonNull PatchResult result);

    /**
     * 生成失败
     * 
     * @param errorCode 错误码
     * @param message 错误信息
     */
    void onError(int errorCode, @NonNull String message);
    
    /**
     * 生成被取消
     */
    void onCancelled();
}
