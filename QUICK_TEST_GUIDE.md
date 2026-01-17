# 快速测试指南

## 当前状态

✅ 应用已安装到 ADB 设备（`9c18cb30`）
✅ 修复已完成并推送到 GitHub
✅ 测试 APK 已准备好：
- `app-v1.1-debug.apk` - 基准版本（无火焰）
- `app-v1.2-debug-fixed.apk` - 新版本（带火焰）

## 快速测试步骤

### 测试 1：验证密码输入对话框（5 分钟）

1. **打开应用**
2. **选择 APK**：
   - 基准 APK: `/sdcard/Download/app-v1.1-debug.apk`
   - 新 APK: `/sdcard/Download/app-v1.2-debug-fixed.apk`
3. **生成补丁**：
   - 点击「生成补丁」
   - 勾选「🔑 ZIP 密码保护」
   - 输入密码：`test123`
   - 点击「生成」
4. **应用补丁**：
   - 点击「应用补丁」
   - **预期**：弹出密码输入对话框 ✅
   - 输入密码：`test123`
   - 点击「验证并应用」
5. **验证结果**：
   - 显示「🔥 热更新成功!」✅
   - 标题变为「🔥 热更新补丁工具 v1.2」✅

### 测试 2：验证 applied 目录加密（3 分钟）

```bash
# 进入应用私有目录
adb shell
cd /data/data/com.orange.update/files/update/applied

# 查看文件
ls -lh
# 预期：current_patch.zip 存在，大小约 9MB

# 尝试解压（应该失败）
unzip current_patch.zip
# 预期：提示需要密码 ✅
```

### 测试 3：验证自动加载（2 分钟）

1. **关闭应用**（从最近任务中划掉）
2. **重新打开应用**
3. **验证**：
   - 标题显示「🔥 热更新补丁工具 v1.2」✅
   - 热更新测试信息显示「🔥🔥🔥 热更新测试 v1.2 - 补丁已生效！代码已更新！🔥🔥🔥」✅

## 测试结果

| 测试项 | 预期 | 实际 | 状态 |
|--------|------|------|------|
| 密码输入对话框 | 弹出 | | ⬜ |
| 密码验证 | 成功 | | ⬜ |
| applied 目录加密 | 无法解压 | | ⬜ |
| 自动加载 | 成功 | | ⬜ |

## 如果测试失败

### 问题 1：不弹出密码对话框

**检查**：
```bash
adb logcat | grep -E "HotUpdateHelper|onZipPasswordRequired"
```

**预期日志**：
```
HotUpdateHelper: 检测到自定义 ZIP 密码，需要用户输入
```

### 问题 2：applied 目录未加密

**检查**：
```bash
adb shell
cd /data/data/com.orange.update/files/update/applied
unzip -l current_patch.zip
```

**预期**：提示需要密码

### 问题 3：自动加载失败

**检查**：
```bash
adb logcat | grep -E "PatchApplication|decryptZipPatchOnLoad"
```

**预期日志**：
```
PatchApplication: Patch is ZIP password protected, decrypting...
PatchApplication: Using custom ZIP password
PatchApplication: ✓ ZIP password protected patch decrypted
```

## 详细测试指南

如需更详细的测试步骤，请参考：
- `TEST_ZIP_PASSWORD_APPLIED_ENCRYPTION.md` - 完整测试指南
- `ZIP_PASSWORD_STORAGE_PROTECTION.md` - 设计文档
- `PATCH_EFFECT_FIX.md` - 修复总结

## 联系方式

如有问题，请查看日志或参考上述文档。
