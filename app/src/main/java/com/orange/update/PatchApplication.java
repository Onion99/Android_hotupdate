package com.orange.update;

import android.app.Application;
import android.content.Context;

/**
 * 热更新 Application
 *
 * 在 attachBaseContext 中加载补丁，确保：
 * 1. DEX 补丁在类加载前注入
 * 2. 资源补丁在 Activity 创建前加载
 * 3. 所有组件都能使用更新后的代码和资源
 * 
 * 使用方法：
 * 1. 继承此类或在自己的 Application 中调用 HotUpdateHelper.loadPatchIfNeeded()
 * 2. 在 AndroidManifest.xml 中配置：android:name=".PatchApplication"
 */
public class PatchApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // 使用 HotUpdateHelper 加载补丁（推荐方式）
        // 包含完整性验证、签名验证、ZIP密码解密等功能
        // 注意：HotUpdateHelper 使用延迟初始化，在 attachBaseContext 阶段可以安全使用
        HotUpdateHelper helper = new HotUpdateHelper(base);
        helper.loadPatchIfNeeded();
    }
}
