# Patch Generator Android SDK

Android 端补丁生�?SDK，支持在设备上直接生成热更新补丁�?

## 功能特�?

- **设备端生�?*: �?Android 设备上直接生成补�?
- **自动引擎选择**: 优先使用 Native 引擎，不可用时自动回退�?Java 引擎
- **后台生成**: 支持后台线程生成，不阻塞 UI
- **进度回调**: 实时进度反馈，支持主线程回调
- **存储检�?*: 自动检查可用存储空�?
- **取消支持**: 支持取消正在进行的生成操�?

## 依赖

```groovy
implementation project(':patch-generator-android')
```

## 快速开�?

### 基本用法

```java
import io.github.706412584.patchgen.android.AndroidPatchGenerator;
import io.github.706412584.patchgen.android.SimpleAndroidGeneratorCallback;

AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(new File(getExternalFilesDir(null), "base.apk"))
    .newApk(new File(getExternalFilesDir(null), "new.apk"))
    .output(new File(getExternalFilesDir(null), "patch.zip"))
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onProgress(int percent, String stage) {
            // 更新进度�?
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

// 在后台线程生�?
generator.generateInBackground();
```

### 从已安装应用生成

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseFromInstalled("com.example.myapp")  // 使用已安装版本作为基�?
    .newApk(new File(downloadDir, "new-version.apk"))
    .output(new File(patchDir, "patch.zip"))
    .callback(callback)
    .build();

generator.generateInBackground();
```

### 选择引擎类型

```java
import io.github.706412584.patchgen.config.EngineType;

AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApk)
    .newApk(newApk)
    .output(outputFile)
    .engineType(EngineType.AUTO)    // 自动选择（默认，优先 Native�?
    // .engineType(EngineType.JAVA)  // 强制使用 Java 引擎
    // .engineType(EngineType.NATIVE) // 强制使用 Native 引擎
    .build();
```

### 取消生成

```java
// 取消正在进行的生�?
generator.cancel();

// 检查是否已取消
if (generator.isCancelled()) {
    // 已取�?
}
```

### 检�?Native 引擎可用�?

```java
if (AndroidPatchGenerator.isNativeEngineAvailable()) {
    Log.d(TAG, "Native 引擎可用，将获得更好的性能");
} else {
    Log.d(TAG, "Native 引擎不可用，将使�?Java 引擎");
}
```

### 存储空间检�?

```java
StorageChecker checker = generator.getStorageChecker();

// 获取可用空间
long available = checker.getInternalStorageAvailable();
Log.d(TAG, "可用空间: " + StorageChecker.formatSize(available));

// 检查是否足�?
StorageChecker.StorageCheckResult result = checker.checkStorage(baseApk, newApk, outputDir);
if (!result.isSufficient()) {
    Log.e(TAG, "存储空间不足: " + result.getMessage());
}
```

## 配置选项

| 方法 | 说明 | 默认�?|
|------|------|--------|
| `baseApk(File)` | 设置基准 APK | 必填 |
| `baseFromInstalled(String)` | 从已安装应用获取基准 APK | - |
| `newApk(File)` | 设置新版�?APK | 必填 |
| `output(File)` | 设置输出文件 | 自动生成 |
| `signingConfig(SigningConfig)` | 设置签名配置 | null |
| `engineType(EngineType)` | 设置引擎类型 | AUTO |
| `patchMode(PatchMode)` | 设置补丁模式 | FULL_DEX |
| `callback(Callback)` | 设置回调 | null |
| `checkStorage(boolean)` | 是否检查存储空�?| true |
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
<!-- 读取外部存储（Android 10 以下�?-->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 写入外部存储（Android 10 以下�?-->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 最低要�?

- Android API 21 (Android 5.0) 或更高版�?
- Java 11

## 许可�?

Apache License 2.0



## 补丁加密

`patch-generator-android` 模块提供了 `PatchEncryptor` 类，用于在生成补丁后对其进行加密。

### 功能特性

- **AES-256-GCM 加密**: 使用业界标准的加密算法
- **密码派生**: 使用 PBKDF2 从密码派生密钥
- **Android KeyStore**: 支持使用 Android KeyStore 存储密钥
- **兼容性**: 与 `update` 模块的 `SecurityManager` 完全兼容

### 基本用法

```java
import com.orange.patchgen.android.PatchEncryptor;

// 创建 PatchEncryptor 实例
PatchEncryptor encryptor = new PatchEncryptor(context);

// 使用自定义密码加密补丁
String password = "your_secure_password";
File encryptedPatch = encryptor.encryptPatchWithPassword(patchFile, password);

// 使用 Android KeyStore 加密（需要 API 23+）
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    File encryptedPatch = encryptor.encryptPatch(patchFile);
}
```

### 与 SecurityManager 的关系

- **PatchEncryptor**（`patch-generator-android` 模块）：用于**生成补丁时加密**
- **SecurityManager**（`update` 模块）：用于**应用补丁时解密**
- 两个类使用相同的加密算法（AES-256-GCM + PBKDF2），确保兼容性
- 这种设计使得补丁生成和应用功能完全解耦，用户可以只依赖 `update` 模块来应用补丁

### 加密方式

#### 1. 使用自定义密码（推荐）

```java
PatchEncryptor encryptor = new PatchEncryptor(context);
String password = "your_secure_password";
File encryptedPatch = encryptor.encryptPatchWithPassword(patchFile, password);
```

**优点**：
- 不依赖设备，可以在任何设备上解密
- 适合服务端生成、客户端应用的场景
- 密码可以通过安全通道传输

#### 2. 使用 Android KeyStore（设备绑定）

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    PatchEncryptor encryptor = new PatchEncryptor(context);
    File encryptedPatch = encryptor.encryptPatch(patchFile);
}
```

**优点**：
- 密钥存储在硬件安全模块中
- 最高安全级别
- 密钥无法导出

**缺点**：
- 只能在生成补丁的设备上解密
- 不适合跨设备场景

### 完整示例

```java
// 生成并加密补丁
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                // 补丁生成成功，进行加密
                try {
                    PatchEncryptor encryptor = new PatchEncryptor(context);
                    String password = "your_secure_password";
                    File encryptedPatch = encryptor.encryptPatchWithPassword(
                        result.getPatchFile(), 
                        password
                    );
                    
                    Log.i(TAG, "补丁加密成功: " + encryptedPatch.getPath());
                    
                    // 删除未加密的补丁文件
                    result.getPatchFile().delete();
                    
                } catch (Exception e) {
                    Log.e(TAG, "补丁加密失败", e);
                }
            }
        }
    })
    .build();

generator.generateInBackground();
```

### API 参考

#### PatchEncryptor

```java
// 构造函数
public PatchEncryptor(Context context)

// 使用密码加密补丁
public File encryptPatchWithPassword(File patchFile, String password)

// 使用 KeyStore 加密补丁（需要 API 23+）
@RequiresApi(api = Build.VERSION_CODES.M)
public File encryptPatch(File patchFile)

// 加密字节数组
@RequiresApi(api = Build.VERSION_CODES.M)
public byte[] encrypt(byte[] data)

// 密钥管理
@RequiresApi(api = Build.VERSION_CODES.M)
public SecretKey getOrCreateEncryptionKey()
public boolean deleteEncryptionKey()
public boolean hasEncryptionKey()
```

### 安全建议

1. **使用强密码**: 密码长度至少 16 字符，包含大小写字母、数字和特殊字符
2. **安全存储密码**: 不要在代码中硬编码密码，使用安全的密钥管理方案
3. **传输安全**: 通过 HTTPS 传输加密补丁和密码
4. **定期更换**: 定期更换加密密码
5. **组合使用**: 结合 APK 签名验证和加密，提供最高安全级别

### 最低要求

- Android API 21 (Android 5.0) 或更高版本
- 使用 KeyStore 加密需要 API 23 (Android 6.0) 或更高版本
