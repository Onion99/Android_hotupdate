package com.orange.patchgen.callback;

import com.orange.patchgen.model.PatchResult;

/**
 * 生成器回调接口
 * 
 * Requirements: 9.1-9.7
 */
public interface GeneratorCallback {
    
    /**
     * 开始解析 APK
     * @param apkPath APK 文件路径
     */
    void onParseStart(String apkPath);

    /**
     * 解析进度
     * @param current 当前进度
     * @param total 总进度
     */
    void onParseProgress(int current, int total);

    /**
     * 开始比较
     */
    void onCompareStart();

    /**
     * 比较进度
     * @param current 当前进度
     * @param total 总进度
     * @param currentFile 当前处理的文件
     */
    void onCompareProgress(int current, int total, String currentFile);

    /**
     * 开始打包
     */
    void onPackStart();

    /**
     * 打包进度
     * @param current 当前字节数
     * @param total 总字节数
     */
    void onPackProgress(long current, long total);

    /**
     * 开始签名
     */
    void onSignStart();

    /**
     * 完成
     * @param result 生成结果
     */
    void onComplete(PatchResult result);

    /**
     * 错误
     * @param errorCode 错误码
     * @param message 错误信息
     */
    void onError(int errorCode, String message);
}
