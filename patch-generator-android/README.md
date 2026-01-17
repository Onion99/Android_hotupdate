# Patch Generator Android SDK

Android 端补丁生成 SDK，支持在设备上直接生成热更新补丁。

## 功能特性

- **设备端生成**: 在 Android 设备上直接生成补丁
- **自动引擎选择**: 优先使用 Native 引擎，不可用时自动回退到 Java 引擎
- **后台生成**: 支持后台线程生成，不阻塞 UI
- **进度回调**: 实时进度反馈，支持主线程回调
- **存储检查**: 自动检查可用存储空间
- **取消支持**: 支持取消正在进行的生成操作

## 依赖

```groovy
implementation project(':patch-generator-android')
```

## 快速开始

### 基本用法

```java
import com.orange.patchgen.android.AndroidPatchGenerator;
import com.orange.patchgen.android.SimpleAndroidGeneratorCallback;

AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(new File(getExternalFilesDir(null), "base.apk"))
    .newApk(new File(getExternalFilesDir(null), "new.apk"))
    .output(new File(getExternalFilesDir(null), "patch.zip"))
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onProgress(int percent, String stage) {
            // 更新进度条
            progressBar.setProgress(percent);
            statusText.setText(stage);
        }
        
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Toast.makeText(context, "补丁生成成功!", Toast.LENGTH_SHORT).show();
            }
        }
        
        @Override
        public void onError(int errorCode, String message) {
            Toast.makeText(context, "错误: " + message, Toast.LENGTH_SHORT).show();
        }
    })
    .build();

// 在后台线程生成
generator.generateInBackground();
```

### 从已安装应用生成

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseFromInstalled("com.example.myapp")  // 使用已安装版本作为基线
    .newApk(new File(downloadDir, "new-version.apk"))
    .output(new File(patchDir, "patch.zip"))
    .callback(callback)
    .build();

generator.generateInBackground();
```

### 选择引擎类型

```java
import com.orange.patchgen.config.EngineType;

AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApk)
    .newApk(newApk)
    .output(outputFile)
    .engineType(EngineType.AUTO)    // 自动选择（默认，优先 Native）
    // .engineType(EngineType.JAVA)  // 强制使用 Java 引擎
    // .engineType(EngineType.NATIVE) // 强制使用 Native 引擎
    .build();
```

### 取消生成

```java
// 取消正在进行的生成
generator.cancel();

// 检查是否已取消
if (generator.isCancelled()) {
    // 已取消
}
```

### 检查 Native 引擎可用性

```java
if (AndroidPatchGenerator.isNativeEngineAvailable()) {
    Log.d(TAG, "Native 引擎可用，将获得更好的性能");
} else {
    Log.d(TAG, "Native 引擎不可用，将使用 Java 引擎");
}
```

### 存储空间检查

```java
StorageChecker checker = generator.getStorageChecker();

// 获取可用空间
long available = checker.getInternalStorageAvailable();
Log.d(TAG, "可用空间: " + StorageChecker.formatSize(available));

// 检查是否足够
StorageChecker.StorageCheckResult result = checker.checkStorage(baseApk, newApk, outputDir);
if (!result.isSufficient()) {
    Log.e(TAG, "存储空间不足: " + result.getMessage());
}
```

## 配置选项

| 方法 | 说明 | 默认值 |
|------|------|--------|
| `baseApk(File)` | 设置基准 APK | 必填 |
| `baseFromInstalled(String)` | 从已安装应用获取基准 APK | - |
| `newApk(File)` | 设置新版本 APK | 必填 |
| `output(File)` | 设置输出文件 | 自动生成 |
| `signingConfig(SigningConfig)` | 设置签名配置 | null |
| `engineType(EngineType)` | 设置引擎类型 | AUTO |
| `patchMode(PatchMode)` | 设置补丁模式 | FULL_DEX |
| `callback(Callback)` | 设置回调 | null |
| `checkStorage(boolean)` | 是否检查存储空间 | true |
| `callbackOnMainThread(boolean)` | 回调是否在主线程 | true |

## 回调接口

```java
public interface AndroidGeneratorCallback {
    void onStart();
    void onParseStart(String apkPath);
    void onParseProgress(int current, int total);
    void onCompareStart();
    void onCompareProgress(int current, int total, String currentFile);
    void onPackStart();
    void onPackProgress(long current, long total);
    void onSignStart();
    void onProgress(int percent, String stage);
    void onComplete(PatchResult result);
    void onError(int errorCode, String message);
    void onCancelled();
}
```

## 权限要求

```xml
<!-- 读取外部存储（Android 10 以下） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 写入外部存储（Android 10 以下） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 最低要求

- Android API 21 (Android 5.0) 或更高版本
- Java 11

## 许可证

Apache License 2.0
