# 架构说明

## 核心算法统一性

### 问题：Demo 和 patch-cli 使用同一套算法吗？

**答案：是的！** Demo 应用、patch-cli 命令行工具、patch-server 服务端都使用**完全相同的核心算法**。

## 架构层次

```
┌─────────────────────────────────────────────────────────────┐
│                      应用层 (Application Layer)              │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Demo App   │  │  patch-cli   │  │ patch-server │      │
│  │  (Android)   │  │  (CLI Tool)  │  │   (Web API)  │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └──────────────────┼──────────────────┘              │
│                            │                                 │
├────────────────────────────┼─────────────────────────────────┤
│                   适配层 (Adapter Layer)                     │
├────────────────────────────┼─────────────────────────────────┤
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │        AndroidPatchGenerator (Android 适配器)          │ │
│  │  - 存储空间检查                                         │ │
│  │  - PackageManager 集成                                 │ │
│  │  - 后台线程管理                                         │ │
│  │  - 主线程回调                                           │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                 │
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │         PatchGeneratorCli (CLI 适配器)                 │ │
│  │  - 命令行参数解析                                       │ │
│  │  - 控制台输出格式化                                     │ │
│  │  - 进度条显示                                           │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            │                                 │
├────────────────────────────┼─────────────────────────────────┤
│                   核心层 (Core Layer)                        │
├────────────────────────────┼─────────────────────────────────┤
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │              PatchGenerator (核心生成器)               │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  1. APK 解析 (ApkParser)                         │  │ │
│  │  │     - 解压 APK                                    │  │ │
│  │  │     - 提取 Manifest                               │  │ │
│  │  │     - 识别 Dex/资源/SO/Assets                     │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  2. 差异比较 (DexDiffer, ResourceDiffer)         │  │ │
│  │  │     - Dex 类结构比较                              │  │ │
│  │  │     - 字符串常量比较 ✅ v1.3.2 新增               │  │ │
│  │  │     - 资源文件 MD5 比较                           │  │ │
│  │  │     - SO 库二进制比较                             │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  3. 补丁生成 (PatchPacker)                       │  │ │
│  │  │     - 提取修改的类                                │  │ │
│  │  │     - 打包补丁文件                                │  │ │
│  │  │     - 生成 patch.json 元数据                      │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  4. 签名加密 (PatchSigner)                       │  │ │
│  │  │     - JAR 签名 (apksig)                           │  │ │
│  │  │     - AES 加密 (可选)                             │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                            │                                 │
├────────────────────────────┼─────────────────────────────────┤
│                  引擎层 (Engine Layer)                       │
├────────────────────────────┼─────────────────────────────────┤
│  ┌─────────────────────────▼──────────────────────────────┐ │
│  │              NativePatchEngine (Native 引擎)           │ │
│  │  - BsDiff 算法 (C++)                                   │ │
│  │  - 高性能二进制差异                                     │ │
│  │  - JNI 接口                                            │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 代码调用链

### Demo 应用 (app/MainActivity.java)

```java
// 1. 创建 Android 适配器
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(callback)
    .build();

// 2. Android 适配器内部创建核心生成器
// AndroidPatchGenerator.java:205
coreGenerator = new PatchGenerator.Builder()
    .baseApk(baseApk)
    .newApk(newApk)
    .output(outputFile)
    .signingConfig(signingConfig)
    .engineType(actualEngineType)
    .patchMode(patchMode)
    .config(config)
    .callback(createCoreCallback())
    .build();

// 3. 调用核心生成器
// AndroidPatchGenerator.java:210
result = coreGenerator.generate();
```

### patch-cli (patch-cli/PatchGeneratorCli.java)

```java
// 1. 解析命令行参数
CommandLine cmd = parser.parse(options, args);

// 2. 直接创建核心生成器
// PatchGeneratorCli.java:90
PatchGenerator generator = new PatchGenerator.Builder()
    .baseApk(baseApk)
    .newApk(newApk)
    .output(output)
    .signingConfig(signingConfig)
    .engineType(engineType)
    .patchMode(patchMode)
    .callback(new ConsoleCallback())
    .build();

// 3. 调用核心生成器
// PatchGeneratorCli.java:99
PatchResult result = generator.generate();
```

### patch-server (patch-server/backend/patchGenerator.js)

```javascript
// 1. Node.js 服务调用 Java CLI
const process = spawn('java', [
    '-jar', 
    'patch-cli-1.3.2-all.jar',
    '--base', baseApk,
    '--new', newApk,
    '--output', patchFile
]);

// 2. patch-cli 内部使用核心生成器（同上）
```

## 核心算法一致性

### DexDiffer (patch-core/DexDiffer.java)

所有三个应用层都使用**完全相同**的 `DexDiffer` 类：

```java
// 1. 解析 Dex 文件
Map<String, String> baseClassHashes = parseDexClasses(baseDex);
Map<String, String> newClassHashes = parseDexClasses(newDex);

// 2. 计算类哈希（包含字符串常量）
String calculateClassHash(ClassDef classDef) {
    StringBuilder sb = new StringBuilder();
    
    // 类基本信息
    sb.append(classDef.getType());
    sb.append("|");
    sb.append(classDef.getAccessFlags());
    
    // 字段签名
    for (Field field : classDef.getFields()) {
        sb.append(getFieldSignature(field));
    }
    
    // 方法签名（包含字符串常量）✅ v1.3.2
    for (Method method : classDef.getMethods()) {
        sb.append(getMethodSignature(method));
    }
    
    return md5(sb.toString());
}

// 3. 提取字符串常量 ✅ v1.3.2 新增
private String getImplementationHash(MethodImplementation impl) {
    for (Instruction instruction : impl.getInstructions()) {
        sb.append(instruction.getOpcode().name);
        
        // 提取字符串引用
        if (instruction instanceof ReferenceInstruction) {
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof StringReference) {
                sb.append("[STR:");
                sb.append(((StringReference) ref).getString());
                sb.append("]");
            }
        }
    }
    return md5(sb.toString());
}
```

### 验证方法

你可以通过以下方式验证算法一致性：

1. **使用 Demo 生成补丁**：
   ```
   选择相同的 base.apk 和 new.apk
   生成 patch-demo.zip
   ```

2. **使用 patch-cli 生成补丁**：
   ```bash
   java -jar patch-cli-1.3.2-all.jar \
     --base base.apk \
     --new new.apk \
     --output patch-cli.zip
   ```

3. **比较结果**：
   ```bash
   # 解压两个补丁
   unzip patch-demo.zip -d demo/
   unzip patch-cli.zip -d cli/
   
   # 比较 patch.json
   diff demo/patch.json cli/patch.json
   
   # 比较 Dex 文件
   diff demo/classes.dex cli/classes.dex
   ```

**结果**：两个补丁的内容应该**完全相同**（除了时间戳和补丁 ID）。

## 依赖关系

```
app (Demo)
  └─> patch-generator-android:1.3.2
        └─> patch-core:1.3.2
              ├─> patch-native:1.3.2
              └─> dexlib2:2.5.2

patch-cli
  └─> patch-core:1.3.2
        ├─> patch-native:1.3.2
        └─> dexlib2:2.5.2

patch-server
  └─> patch-cli-1.3.2-all.jar
        └─> patch-core:1.3.2
              ├─> patch-native:1.3.2
              └─> dexlib2:2.5.2
```

## 版本一致性保证

### 构建配置

所有模块使用统一的版本号：

```gradle
// gradle.properties
VERSION_NAME=1.3.2
VERSION_CODE=132

// 所有模块的 build.gradle
version = VERSION_NAME
```

### 发布流程

1. **更新核心算法** → `patch-core/DexDiffer.java`
2. **编译所有模块**：
   ```bash
   ./gradlew clean build
   ```
3. **生成 CLI JAR**：
   ```bash
   ./gradlew :patch-cli:fatJar
   ```
4. **测试一致性**：
   - Demo 应用测试
   - CLI 工具测试
   - 服务端测试
5. **发布到 Maven Central**：
   ```bash
   ./gradlew publish
   ```

## 字符串检测修复的影响

### v1.3.2 修复

修改了 `patch-core/DexDiffer.java` 的 `getImplementationHash` 方法，**所有使用此核心库的应用都自动获得修复**：

- ✅ Demo 应用 - 重新编译后自动修复
- ✅ patch-cli - 重新编译后自动修复
- ✅ patch-server - 使用新的 CLI JAR 后自动修复
- ✅ 第三方集成 - 升级依赖到 1.3.2 后自动修复

### 测试验证

使用相同的测试 APK（只有字符串差异）：

| 工具 | v1.3.1 结果 | v1.3.2 结果 |
|------|------------|------------|
| Demo 应用 | NO CHANGES ❌ | 1 modified class ✅ |
| patch-cli | NO CHANGES ❌ | 1 modified class ✅ |
| patch-server | NO CHANGES ❌ | 1 modified class ✅ |

## 总结

1. ✅ **算法统一**：Demo、CLI、Server 使用完全相同的核心算法
2. ✅ **代码共享**：所有应用层都依赖 `patch-core` 核心库
3. ✅ **版本同步**：统一的版本号管理
4. ✅ **修复同步**：核心修复自动影响所有应用层
5. ✅ **测试一致**：相同输入产生相同输出

**结论**：Demo 应用和 patch-cli 使用的是**完全相同的算法**，只是封装层不同（Android 适配器 vs CLI 适配器）。

---

**文档版本**: 1.0  
**最后更新**: 2026-01-19  
**相关文件**:
- `patch-core/src/main/java/com/orange/patchgen/PatchGenerator.java`
- `patch-core/src/main/java/com/orange/patchgen/differ/DexDiffer.java`
- `patch-generator-android/src/main/java/com/orange/patchgen/android/AndroidPatchGenerator.java`
- `patch-cli/src/main/java/com/orange/patchgen/cli/PatchGeneratorCli.java`
