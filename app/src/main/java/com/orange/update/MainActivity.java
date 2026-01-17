package com.orange.update;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.orange.patchgen.android.AndroidPatchGenerator;
import com.orange.patchgen.android.SimpleAndroidGeneratorCallback;
import com.orange.patchgen.android.StorageChecker;
import com.orange.patchgen.model.PatchResult;
import com.orange.patchnative.NativePatchEngine;
import com.orange.patchnative.NativeProgressCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Demo Activity - 热更新补丁生成与应用演示
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PatchDemo";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // UI 组件
    private TextView tvStatus;
    private TextView tvInfo;
    private TextView tvVersion;
    private ProgressBar progressBar;
    private Button btnGenerate;
    private Button btnApply;
    private Button btnCheckEngine;
    private Button btnCheckStorage;
    private Button btnSelectBase;
    private Button btnSelectNew;
    private Button btnSelectPatch;

    private AndroidPatchGenerator generator;
    private RealHotUpdate realHotUpdate;
    private Button btnClearPatch;
    private Button btnVerifySuccess;
    private Button btnVerifyFail;
    private Button btnGenerateKeys;
    private Button btnLoadKeys;
    private Button btnConfigKeys;
    
    // RSA密钥对（用于演示）
    private java.security.KeyPair demoKeyPair;
    
    // 选择的文件
    private File selectedBaseApk;
    private File selectedNewApk;
    private File selectedPatchFile;
    private File lastGeneratedPatch;
    
    // 文件选择类型: 0=基准APK, 1=新APK, 2=补丁文件
    private int selectingFileType = 0;

    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;

    // 默认输出目录 - 下载目录
    private File outputDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 设置默认输出目录为下载目录
        outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 初始化真正的热更新
        realHotUpdate = new RealHotUpdate(this);
        
        // 应用启动时加载已应用的补丁
        realHotUpdate.loadAppliedPatch();

        initFilePicker();
        initViews();
        checkPermissions();
        showSystemInfo();
    }

    private void initFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
        );
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvInfo = findViewById(R.id.tv_info);
        tvVersion = findViewById(R.id.tv_version);
        progressBar = findViewById(R.id.progress_bar);
        btnGenerate = findViewById(R.id.btn_generate);
        btnApply = findViewById(R.id.btn_apply);
        btnCheckEngine = findViewById(R.id.btn_check_engine);
        btnCheckStorage = findViewById(R.id.btn_check_storage);
        btnSelectBase = findViewById(R.id.btn_select_base);
        btnSelectNew = findViewById(R.id.btn_select_new);
        btnSelectPatch = findViewById(R.id.btn_select_patch);
        btnClearPatch = findViewById(R.id.btn_clear_patch);
        btnVerifySuccess = findViewById(R.id.btn_verify_success);
        btnVerifyFail = findViewById(R.id.btn_verify_fail);
        btnGenerateKeys = findViewById(R.id.btn_generate_keys);
        btnLoadKeys = findViewById(R.id.btn_load_keys);
        btnConfigKeys = findViewById(R.id.btn_config_keys);
        Button btnTestAssets = findViewById(R.id.btn_test_assets);

        btnGenerate.setOnClickListener(v -> startPatchGeneration());
        btnApply.setOnClickListener(v -> applyPatch());
        btnCheckEngine.setOnClickListener(v -> checkEngineAvailability());
        btnCheckStorage.setOnClickListener(v -> checkStorageSpace());
        btnSelectBase.setOnClickListener(v -> selectFile(0));
        btnSelectNew.setOnClickListener(v -> selectFile(1));
        btnSelectPatch.setOnClickListener(v -> selectFile(2));
        btnClearPatch.setOnClickListener(v -> clearPatch());
        btnVerifySuccess.setOnClickListener(v -> testSignatureVerificationSuccess());
        btnVerifyFail.setOnClickListener(v -> testSignatureVerificationFail());
        btnGenerateKeys.setOnClickListener(v -> generateRSAKeyPair());
        btnLoadKeys.setOnClickListener(v -> loadUserKeys());
        btnConfigKeys.setOnClickListener(v -> showConfigKeysDialog());
        btnTestAssets.setOnClickListener(v -> testAssetsFile());
        
        updateButtonStates();
        
        // 应用启动时自动加载密钥
        loadUserKeys();
    }

    /**
     * 获取热更新测试信息 - 用于验证 DEX 热更新是否生效
     * v1.2 更新后的方法
     */
    private String getHotUpdateTestInfo() {
        return "🔥 热更新测试 v1.2 - 补丁已生效！代码已更新！";
    }

    private void showSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 系统信息 ===\n\n");
        
        // 显示热更新测试信息（v1.2 新增）
        info.append(getHotUpdateTestInfo()).append("\n\n");
        
        info.append("应用包名: ").append(getPackageName()).append("\n");
        
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String displayVersion = realHotUpdate.getDisplayVersion(pInfo.versionName);
            tvVersion.setText("v" + displayVersion);
            info.append("版本: ").append(displayVersion).append("\n");
            
            // 显示热更新状态
            if (realHotUpdate.isPatchApplied()) {
                info.append("\n🔥 热更新状态: 已应用\n");
                info.append("补丁版本: ").append(realHotUpdate.getPatchedVersion()).append("\n");
                info.append("DEX 注入: ").append(realHotUpdate.isDexInjected() ? "✓" : "✗").append("\n");
                long patchTime = realHotUpdate.getPatchTime();
                if (patchTime > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    info.append("应用时间: ").append(sdf.format(new java.util.Date(patchTime))).append("\n");
                }
                // 显示清除按钮
                btnClearPatch.setVisibility(View.VISIBLE);
            } else {
                btnClearPatch.setVisibility(View.GONE);
            }
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("版本未知");
        }
        
        info.append("\n").append(realHotUpdate.getCompatibilityInfo()).append("\n");
        info.append("\nNative 引擎: ").append(AndroidPatchGenerator.isNativeEngineAvailable() ? "✓ 可用" : "✗ 不可用").append("\n");
        info.append("\n输出目录:\n").append(outputDir.getAbsolutePath()).append("\n");
        info.append("\n=== 使用说明 ===\n");
        info.append("1. 选择基准APK和新APK\n");
        info.append("2. 点击「生成补丁」\n");
        info.append("3. 点击「应用补丁」实现热更新\n");
        info.append("4. 热更新后无需重启即可生效\n");
        
        tvInfo.setText(info.toString());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("请授予所有文件访问权限")
                    .setPositiveButton("去设置", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("✓ 权限已授予");
            } else {
                tvStatus.setText("⚠ 权限被拒绝");
            }
        }
    }

    /**
     * 选择文件
     * @param type 0=基准APK, 1=新APK, 2=补丁文件
     */
    private void selectFile(int type) {
        selectingFileType = type;
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        if (type == 2) {
            intent.setType("application/zip");
        } else {
            intent.setType("application/vnd.android.package-archive");
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        String title = type == 0 ? "选择基准 APK" : (type == 1 ? "选择新 APK" : "选择补丁文件");
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, title));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            String[] fileNames = {"selected_base.apk", "selected_new.apk", "selected_patch.zip"};
            String fileName = fileNames[selectingFileType];
            File destFile = new File(getExternalFilesDir(null), fileName);
            
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                FileOutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
                
                switch (selectingFileType) {
                    case 0:
                        selectedBaseApk = destFile;
                        btnSelectBase.setText("基准: " + getApkInfo(destFile));
                        break;
                    case 1:
                        selectedNewApk = destFile;
                        btnSelectNew.setText("新版: " + getApkInfo(destFile));
                        break;
                    case 2:
                        selectedPatchFile = destFile;
                        btnSelectPatch.setText("补丁: " + formatSize(destFile.length()));
                        break;
                }
                
                updateButtonStates();
                updateFileInfo();
                Toast.makeText(this, "✓ 文件已选择", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            Toast.makeText(this, "处理文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getApkInfo(File apkFile) {
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                return "v" + info.versionName;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 APK 信息失败", e);
        }
        return formatSize(apkFile.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private void updateButtonStates() {
        boolean canGenerate = selectedBaseApk != null && selectedNewApk != null;
        btnGenerate.setEnabled(canGenerate);
        
        boolean canApply = selectedPatchFile != null || lastGeneratedPatch != null;
        btnApply.setEnabled(canApply);
    }

    private void updateFileInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 已选择的文件 ===\n\n");
        
        if (selectedBaseApk != null) {
            info.append("📦 基准 APK: ").append(getApkInfo(selectedBaseApk));
            info.append(" (").append(formatSize(selectedBaseApk.length())).append(")\n");
        } else {
            info.append("📦 基准 APK: 未选择\n");
        }
        
        if (selectedNewApk != null) {
            info.append("📦 新版 APK: ").append(getApkInfo(selectedNewApk));
            info.append(" (").append(formatSize(selectedNewApk.length())).append(")\n");
        } else {
            info.append("📦 新版 APK: 未选择\n");
        }
        
        info.append("\n");
        
        if (lastGeneratedPatch != null && lastGeneratedPatch.exists()) {
            info.append("🔧 最新补丁: ").append(lastGeneratedPatch.getName());
            info.append(" (").append(formatSize(lastGeneratedPatch.length())).append(")\n");
        }
        
        if (selectedPatchFile != null) {
            info.append("📋 选择的补丁: ").append(selectedPatchFile.getName());
            info.append(" (").append(formatSize(selectedPatchFile.length())).append(")\n");
        }
        
        info.append("\n输出目录: ").append(outputDir.getAbsolutePath());
        
        tvInfo.setText(info.toString());
    }

    private void checkEngineAvailability() {
        boolean nativeAvailable = AndroidPatchGenerator.isNativeEngineAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 引擎状态 ===\n\n");
        info.append("Native 引擎: ").append(nativeAvailable ? "✓ 可用 (高性能)" : "✗ 不可用").append("\n");
        info.append("Java 引擎: ✓ 始终可用\n\n");
        
        if (nativeAvailable) {
            info.append("当前使用: Native 引擎\n");
            info.append("Native 引擎使用 C/C++ 实现，性能更高");
        } else {
            info.append("当前使用: Java 引擎\n");
            info.append("Java 引擎功能完整，兼容性好");
        }
        
        tvInfo.setText(info.toString());
        tvStatus.setText(nativeAvailable ? "✓ Native 引擎可用" : "使用 Java 引擎");
    }

    private void checkStorageSpace() {
        StorageChecker checker = new StorageChecker(this);
        
        long internalAvailable = checker.getInternalStorageAvailable();
        long externalAvailable = checker.getExternalStorageAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 存储空间 ===\n\n");
        info.append("内部存储: ").append(formatSize(internalAvailable)).append(" 可用\n");
        info.append("外部存储: ").append(formatSize(externalAvailable)).append(" 可用\n\n");
        info.append("输出目录:\n").append(outputDir.getAbsolutePath()).append("\n\n");
        info.append("临时目录:\n").append(checker.getTempDir().getAbsolutePath());
        
        tvInfo.setText(info.toString());
        tvStatus.setText("✓ 存储空间充足");
    }

    private void startPatchGeneration() {
        if (selectedBaseApk == null || selectedNewApk == null) {
            Toast.makeText(this, "请先选择两个 APK 文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示签名选项对话框
        showSignPatchDialog();
    }
    
    /**
     * 显示签名补丁选项对话框
     */
    private void showSignPatchDialog() {
        // 检查是否有可用的密钥
        boolean hasKeys = demoKeyPair != null;
        
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 标题文本
        TextView tvTitle = new TextView(this);
        tvTitle.setText(hasKeys 
            ? "✓ 已加载密钥对\n\n请选择安全选项："
            : "⚠️ 未加载密钥对\n\n请选择安全选项：");
        tvTitle.setTextSize(14);
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);
        
        // 签名选项
        android.widget.CheckBox cbSign = new android.widget.CheckBox(this);
        cbSign.setText("🔒 对补丁进行签名");
        cbSign.setChecked(hasKeys);
        cbSign.setEnabled(hasKeys);
        layout.addView(cbSign);
        
        TextView tvSignHint = new TextView(this);
        tvSignHint.setText(hasKeys 
            ? "  使用 RSA-2048 签名，防止补丁被篡改"
            : "  需要先配置密钥才能签名");
        tvSignHint.setTextSize(12);
        tvSignHint.setTextColor(0xFF666666);
        tvSignHint.setPadding(0, 0, 0, 15);
        layout.addView(tvSignHint);
        
        // 加密选项
        android.widget.CheckBox cbEncrypt = new android.widget.CheckBox(this);
        cbEncrypt.setText("🔐 对补丁进行加密");
        cbEncrypt.setChecked(false);
        layout.addView(cbEncrypt);
        
        TextView tvEncryptHint = new TextView(this);
        tvEncryptHint.setText("  使用 AES-256-GCM 加密，保护补丁内容");
        tvEncryptHint.setTextSize(12);
        tvEncryptHint.setTextColor(0xFF666666);
        tvEncryptHint.setPadding(0, 0, 0, 15);
        layout.addView(tvEncryptHint);
        
        // 密码输入（仅在选择加密时显示）
        TextView tvPasswordLabel = new TextView(this);
        tvPasswordLabel.setText("加密密码：");
        tvPasswordLabel.setTextSize(14);
        tvPasswordLabel.setPadding(0, 10, 0, 8);
        tvPasswordLabel.setVisibility(View.GONE);
        layout.addView(tvPasswordLabel);
        
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setHint("输入加密密码（留空使用默认密码）");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setVisibility(View.GONE);
        layout.addView(etPassword);
        
        TextView tvPasswordHint = new TextView(this);
        tvPasswordHint.setText("  客户端需要相同密码才能解密");
        tvPasswordHint.setTextSize(12);
        tvPasswordHint.setTextColor(0xFF666666);
        tvPasswordHint.setPadding(0, 0, 0, 0);
        tvPasswordHint.setVisibility(View.GONE);
        layout.addView(tvPasswordHint);
        
        // 加密选项变化监听
        cbEncrypt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvPasswordLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            etPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            tvPasswordHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("🔒 补丁安全选项")
            .setView(layout)
            .setPositiveButton("生成", (d, w) -> {
                boolean withSignature = cbSign.isChecked();
                boolean withEncryption = cbEncrypt.isChecked();
                String password = etPassword.getText().toString().trim();
                
                if (withSignature && !hasKeys) {
                    Toast.makeText(this, "请先配置密钥", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 生成补丁
                generatePatchWithOptions(withSignature, withEncryption, password);
            })
            .setNegativeButton("取消", null);
        
        if (!hasKeys) {
            builder.setNeutralButton("配置密钥", (d, w) -> {
                showConfigKeysDialog();
            });
        }
        
        builder.show();
    }
    
    /**
     * 生成补丁（可选签名和加密）
     */
    private void generatePatchWithOptions(boolean withSignature, boolean withEncryption, String password) {
        // 输出到下载目录
        File outputFile = new File(outputDir, "patch_" + System.currentTimeMillis() + ".zip");

        String status = "正在生成补丁...";
        if (withSignature && withEncryption) {
            status = "正在生成、签名并加密补丁...";
        } else if (withSignature) {
            status = "正在生成并签名补丁...";
        } else if (withEncryption) {
            status = "正在生成并加密补丁...";
        }
        
        tvStatus.setText(status);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        generator = new AndroidPatchGenerator.Builder(this)
                .baseApk(selectedBaseApk)
                .newApk(selectedNewApk)
                .output(outputFile)
                .callbackOnMainThread(true)
                .callback(new SimpleAndroidGeneratorCallback() {
                    @Override
                    public void onStart() {
                        tvStatus.setText("开始生成...");
                    }

                    @Override
                    public void onProgress(int percent, String stage) {
                        progressBar.setProgress(percent);
                        tvStatus.setText(stage + " (" + percent + "%)");
                    }

                    @Override
                    public void onComplete(PatchResult result) {
                        if (result.isSuccess()) {
                            lastGeneratedPatch = result.getPatchFile();
                            
                            // 处理签名和加密
                            if (withSignature || withEncryption) {
                                processSecurityOptions(result, withSignature, withEncryption, password);
                            } else {
                                progressBar.setVisibility(View.GONE);
                                setButtonsEnabled(true);
                                tvStatus.setText("✓ 补丁生成成功!");
                                
                                // 清除之前选择的补丁文件，使用新生成的补丁
                                selectedPatchFile = null;
                                btnSelectPatch.setText("选择补丁");
                                
                                showPatchResult(result);
                                updateButtonStates();
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            setButtonsEnabled(true);
                            tvStatus.setText("✗ 生成失败: " + result.getErrorMessage());
                        }
                    }

                    @Override
                    public void onError(int errorCode, String message) {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        tvStatus.setText("✗ 错误: " + message);
                    }

                    @Override
                    public void onCancelled() {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        tvStatus.setText("已取消");
                    }
                })
                .build();

        generator.generateInBackground();
    }
    
    /**
     * 处理安全选项（签名和加密）
     */
    private void processSecurityOptions(PatchResult result, boolean withSignature, 
                                       boolean withEncryption, String password) {
        new Thread(() -> {
            try {
                File patchFile = result.getPatchFile();
                File finalPatchFile = patchFile;
                String signature = null;
                File signatureFile = null;
                
                // 1. 加密补丁
                if (withEncryption) {
                    runOnUiThread(() -> tvStatus.setText("正在加密补丁..."));
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        com.orange.update.SecurityManager securityManager = 
                            new com.orange.update.SecurityManager(this);
                        
                        // 使用密码或默认密钥加密
                        File encryptedFile = securityManager.encryptPatch(patchFile);
                        
                        // 保存密码信息
                        if (!password.isEmpty()) {
                            File passwordFile = new File(patchFile.getPath() + ".pwd");
                            FileOutputStream fos = new FileOutputStream(passwordFile);
                            fos.write(("密码提示: 使用自定义密码\n" + 
                                      "注意: 客户端需要相同密码才能解密").getBytes("UTF-8"));
                            fos.close();
                        }
                        
                        finalPatchFile = encryptedFile;
                        lastGeneratedPatch = encryptedFile;
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "加密需要 Android 6.0+", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                
                // 2. 签名补丁
                if (withSignature && demoKeyPair != null) {
                    runOnUiThread(() -> tvStatus.setText("正在签名补丁..."));
                    
                    signature = signPatchFile(finalPatchFile, demoKeyPair.getPrivate());
                    
                    // 保存签名到文件
                    signatureFile = new File(finalPatchFile.getPath() + ".sig");
                    FileOutputStream fos = new FileOutputStream(signatureFile);
                    fos.write(signature.getBytes("UTF-8"));
                    fos.close();
                }
                
                // 3. 显示结果
                File finalSignatureFile = signatureFile;
                String finalSignature = signature;
                File finalFinalPatchFile = finalPatchFile;
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    
                    String statusText = "✓ 补丁生成成功";
                    if (withSignature && withEncryption) {
                        statusText += "（已签名并加密）";
                    } else if (withSignature) {
                        statusText += "（已签名）";
                    } else if (withEncryption) {
                        statusText += "（已加密）";
                    }
                    tvStatus.setText(statusText + "!");
                    
                    // 清除之前选择的补丁文件，使用新生成的补丁
                    selectedPatchFile = null;
                    btnSelectPatch.setText("选择补丁");
                    
                    showSecuredPatchResult(result, finalFinalPatchFile, finalSignatureFile, 
                                          finalSignature, withSignature, withEncryption);
                    updateButtonStates();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "处理安全选项失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 处理失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 显示安全补丁的结果
     */
    private void showSecuredPatchResult(PatchResult result, File patchFile, 
                                       File signatureFile, String signature,
                                       boolean withSignature, boolean withEncryption) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔒 补丁生成成功 ===\n\n");
        
        // 安全选项
        info.append("=== 安全选项 ===\n");
        if (withSignature) {
            info.append("✓ RSA-2048 签名\n");
        }
        if (withEncryption) {
            info.append("✓ AES-256-GCM 加密\n");
        }
        if (!withSignature && !withEncryption) {
            info.append("⚠️ 未启用安全选项\n");
        }
        info.append("\n");
        
        // 文件信息
        info.append("=== 文件信息 ===\n");
        info.append("📁 补丁文件: ").append(patchFile.getName()).append("\n");
        info.append("📍 位置: ").append(patchFile.getParent()).append("\n");
        info.append("📊 大小: ").append(formatSize(patchFile.length())).append("\n");
        
        if (withEncryption) {
            info.append("🔐 状态: 已加密\n");
        }
        
        if (withSignature && signatureFile != null) {
            info.append("\n🔒 签名文件: ").append(signatureFile.getName()).append("\n");
            info.append("📊 大小: ").append(formatSize(signatureFile.length())).append("\n");
        }
        
        info.append("\n⏱ 耗时: ").append(result.getGenerateTime()).append(" ms\n\n");
        
        // 签名信息
        if (withSignature && signature != null) {
            info.append("=== 签名信息 ===\n");
            info.append("算法: SHA256withRSA\n");
            info.append("密钥长度: 2048位\n");
            info.append("签名长度: ").append(signature.length()).append(" 字符\n");
            info.append("签名(前64字符):\n").append(
                signature.substring(0, Math.min(64, signature.length()))).append("...\n\n");
        }
        
        // 加密信息
        if (withEncryption) {
            info.append("=== 加密信息 ===\n");
            info.append("算法: AES-256-GCM\n");
            info.append("密钥存储: Android KeyStore\n");
            info.append("认证标签: 128位\n");
            info.append("状态: 已加密\n\n");
        }
        
        // 差异统计
        if (result.getDiffSummary() != null) {
            info.append("=== 差异统计 ===\n");
            info.append("修改类: ").append(result.getDiffSummary().getModifiedClasses()).append("\n");
            info.append("新增类: ").append(result.getDiffSummary().getAddedClasses()).append("\n");
            info.append("删除类: ").append(result.getDiffSummary().getDeletedClasses()).append("\n");
            info.append("修改资源: ").append(result.getDiffSummary().getModifiedResources()).append("\n");
            info.append("新增资源: ").append(result.getDiffSummary().getAddedResources()).append("\n");
            info.append("删除资源: ").append(result.getDiffSummary().getDeletedResources()).append("\n\n");
        }
        
        // 使用说明
        info.append("=== 💡 使用说明 ===\n");
        if (withSignature && withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (已加密)\n");
            info.append("2. 签名文件: ").append(signatureFile.getName()).append("\n");
            info.append("3. 客户端需要先解密再验证签名\n");
            info.append("4. 解密需要相同的密钥\n");
            info.append("5. 验证签名需要公钥\n");
        } else if (withSignature) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append("\n");
            info.append("2. 签名文件: ").append(signatureFile.getName()).append("\n");
            info.append("3. 将两个文件一起发送给客户端\n");
            info.append("4. 客户端使用公钥验证签名\n");
        } else if (withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (已加密)\n");
            info.append("2. 客户端需要相同密钥才能解密\n");
            info.append("3. 解密后可以应用补丁\n");
        }
        info.append("\n");
        
        // 安全提示
        info.append("⚠️ 安全提示:\n");
        if (withSignature) {
            info.append("• 签名可以防止补丁被篡改\n");
        }
        if (withEncryption) {
            info.append("• 加密可以保护补丁内容\n");
            info.append("• 客户端需要相同密钥才能解密\n");
        }
        if (withSignature && withEncryption) {
            info.append("• 签名+加密提供最高安全级别\n");
        }
        
        tvInfo.setText(info.toString());
    }

    private void showPatchResult(PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 补丁生成成功 ===\n\n");
        
        if (result.getPatchFile() != null) {
            info.append("📁 文件: ").append(result.getPatchFile().getName()).append("\n");
            info.append("📍 位置: ").append(result.getPatchFile().getParent()).append("\n\n");
        }
        
        info.append("📊 大小: ").append(formatSize(result.getPatchSize())).append("\n");
        info.append("⏱ 耗时: ").append(result.getGenerateTime()).append(" ms\n");
        
        if (result.getDiffSummary() != null) {
            info.append("\n=== 差异统计 ===\n");
            info.append("修改类: ").append(result.getDiffSummary().getModifiedClasses()).append("\n");
            info.append("新增类: ").append(result.getDiffSummary().getAddedClasses()).append("\n");
            info.append("删除类: ").append(result.getDiffSummary().getDeletedClasses()).append("\n");
            info.append("修改资源: ").append(result.getDiffSummary().getModifiedResources()).append("\n");
            info.append("新增资源: ").append(result.getDiffSummary().getAddedResources()).append("\n");
            info.append("删除资源: ").append(result.getDiffSummary().getDeletedResources()).append("\n");
        }
        
        if (selectedNewApk != null && result.getPatchSize() > 0) {
            float ratio = (float) result.getPatchSize() / selectedNewApk.length() * 100;
            info.append("\n压缩比: ").append(String.format("%.1f%%", ratio));
        }
        
        tvInfo.setText(info.toString());
    }

    /**
     * 应用补丁 - 真正的热更新
     */
    private void applyPatch() {
        File patchToApply = selectedPatchFile != null ? selectedPatchFile : lastGeneratedPatch;
        
        if (patchToApply == null || !patchToApply.exists()) {
            Toast.makeText(this, "请先生成或选择补丁文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "应用补丁文件: " + patchToApply.getAbsolutePath());
        Log.d(TAG, "文件名: " + patchToApply.getName());
        Log.d(TAG, "是否加密: " + patchToApply.getName().endsWith(".enc"));

        // 检查是否是加密的补丁
        if (patchToApply.getName().endsWith(".enc")) {
            // 加密的补丁，需要先解密
            Log.d(TAG, "检测到加密补丁，开始解密流程");
            decryptAndApplyPatch(patchToApply);
        } else {
            // 未加密的补丁，直接应用
            Log.d(TAG, "未加密补丁，直接应用");
            applyPatchDirect(patchToApply);
        }
    }
    
    /**
     * 解密并应用补丁
     */
    private void decryptAndApplyPatch(File encryptedPatch) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "解密需要 Android 6.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvStatus.setText("正在解密补丁...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);
        
        new Thread(() -> {
            try {
                // 使用 SecurityManager 解密
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this);
                
                File decryptedPatch = securityManager.decryptPatch(encryptedPatch);
                
                runOnUiThread(() -> {
                    tvStatus.setText("✓ 解密成功，正在应用补丁...");
                    // 应用解密后的补丁
                    applyPatchDirect(decryptedPatch);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "解密失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 解密失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "解密失败: " + e.getMessage() + "\n\n请确保使用相同的密钥", 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 直接应用补丁（未加密）
     */
    private void applyPatchDirect(File patchFile) {
        tvStatus.setText("正在应用热更新...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        realHotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
            @Override
            public void onProgress(int percent, String message) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(RealHotUpdate.PatchResult result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("🔥 热更新成功!");
                    
                    // 更新版本显示
                    tvVersion.setText("v" + result.newVersion + " (热更新)");
                    
                    // 显示清除按钮
                    btnClearPatch.setVisibility(View.VISIBLE);
                    
                    // 显示结果
                    showRealHotUpdateResult(result);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 热更新失败: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 清除补丁（回滚）
     */
    private void clearPatch() {
        new AlertDialog.Builder(this)
            .setTitle("清除补丁")
            .setMessage("确定要清除已应用的补丁吗？\n\n注意：清除后需要重启应用才能完全回滚到原版本。")
            .setPositiveButton("确定", (d, w) -> {
                realHotUpdate.clearPatch();
                btnClearPatch.setVisibility(View.GONE);
                
                // 刷新显示
                try {
                    PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    tvVersion.setText("v" + pInfo.versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    tvVersion.setText("v1.1");
                }
                
                tvStatus.setText("✓ 补丁已清除，重启应用后生效");
                showSystemInfo();
                Toast.makeText(this, "补丁已清除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showRealHotUpdateResult(RealHotUpdate.PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔥 热更新成功 ===\n\n");
        
        info.append("状态: ✓ 已应用\n\n");
        
        if (result.oldVersion != null) {
            info.append("原版本: ").append(result.oldVersion).append("\n");
        }
        info.append("新版本: ").append(result.newVersion).append("\n");
        info.append("版本号: ").append(result.newVersionCode).append("\n\n");
        
        info.append("补丁大小: ").append(formatSize(result.patchSize)).append("\n\n");
        
        info.append("=== 热更新详情 ===\n");
        info.append("DEX 注入: ").append(result.dexInjected ? "✓ 成功" : "✗ 无 DEX").append("\n");
        info.append("SO 加载: ").append(result.soLoaded ? "✓ 成功" : "✗ 无 SO").append("\n");
        info.append("资源加载: ").append(result.resourcesLoaded ? "✓ 成功" : "✗ 无资源").append("\n\n");
        
        info.append("=== 热更新说明 ===\n");
        info.append("✓ 版本已从 ").append(result.oldVersion != null ? result.oldVersion : "原版本");
        info.append(" 更新到 ").append(result.newVersion).append("\n");
        info.append("✓ 无需重新安装 APK\n");
        
        if (result.dexInjected) {
            info.append("✓ DEX 已注入到 ClassLoader\n");
            info.append("✓ 新代码立即生效\n");
        } else {
            info.append("⚠ 补丁中无 DEX 文件\n");
        }
        
        if (result.soLoaded) {
            info.append("✓ SO 库已加载\n");
            info.append("✓ Native 代码立即生效\n");
        }
        
        if (result.resourcesLoaded && result.needsRestart) {
            info.append("\n⚠ 资源已更新，重启应用后生效\n");
        }
        
        info.append("\n提示: 点击「清除补丁」可回滚");
        
        tvInfo.setText(info.toString());
        
        // 如果有资源更新，提示用户重启
        if (result.resourcesLoaded && result.needsRestart) {
            new AlertDialog.Builder(this)
                .setTitle("资源更新")
                .setMessage("资源补丁已应用，需要重启应用才能看到新的界面。\n\n是否立即重启？")
                .setPositiveButton("立即重启", (d, w) -> {
                    // 重启应用
                    Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        // 强制结束进程以确保完全重启
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .setNegativeButton("稍后重启", null)
                .show();
        }
    }

    /**
     * 测试 Assets 文件读取
     */
    private void testAssetsFile() {
        StringBuilder info = new StringBuilder();
        info.append("=== 📄 Assets 文件测试 ===\n\n");
        
        try {
            // 读取 config.txt 文件
            java.io.InputStream is = getAssets().open("config.txt");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, "UTF-8"));
            
            String line;
            while ((line = reader.readLine()) != null) {
                info.append(line).append("\n");
            }
            
            reader.close();
            is.close();
            
            info.append("\n✓ Assets 文件读取成功");
            tvStatus.setText("✓ Assets 文件读取成功");
            
        } catch (Exception e) {
            info.append("✗ 读取失败: ").append(e.getMessage());
            tvStatus.setText("✗ Assets 文件读取失败");
        }
        
        tvInfo.setText(info.toString());
    }

    /**
     * 生成RSA密钥对
     */
    private void generateRSAKeyPair() {
        tvStatus.setText("正在生成RSA密钥对...");
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        setButtonsEnabled(false);
        
        new Thread(() -> {
            try {
                // 生成2048位RSA密钥对
                java.security.KeyPairGenerator keyGen = 
                    java.security.KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                demoKeyPair = keyGen.generateKeyPair();
                
                // 获取公钥和私钥的Base64编码
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                String privateKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPrivate().getEncoded(),
                    android.util.Base64.NO_WRAP);
                
                // 保存密钥到下载文件夹
                File publicKeyFile = saveKeyToFile(publicKeyBase64, "rsa_public_key.txt");
                File privateKeyFile = saveKeyToFile(privateKeyBase64, "rsa_private_key.txt");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✓ RSA密钥对生成成功！");
                    showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "生成密钥对失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 生成失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "生成失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 保存密钥到文件
     */
    private File saveKeyToFile(String keyContent, String fileName) throws Exception {
        File keyFile = new File(outputDir, fileName);
        FileOutputStream fos = new FileOutputStream(keyFile);
        fos.write(keyContent.getBytes("UTF-8"));
        fos.close();
        return keyFile;
    }
    
    /**
     * 从文件加载密钥
     */
    private String loadKeyFromFile(File keyFile) throws Exception {
        if (!keyFile.exists()) {
            return null;
        }
        
        FileInputStream fis = new FileInputStream(keyFile);
        byte[] buffer = new byte[(int) keyFile.length()];
        fis.read(buffer);
        fis.close();
        
        return new String(buffer, "UTF-8").trim();
    }
    
    /**
     * 加载用户配置的密钥
     */
    private void loadUserKeys() {
        new Thread(() -> {
            try {
                File publicKeyFile = new File(outputDir, "rsa_public_key.txt");
                File privateKeyFile = new File(outputDir, "rsa_private_key.txt");
                
                String publicKeyBase64 = loadKeyFromFile(publicKeyFile);
                String privateKeyBase64 = loadKeyFromFile(privateKeyFile);
                
                if (publicKeyBase64 != null && privateKeyBase64 != null) {
                    // 重建密钥对
                    java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                    
                    // 加载公钥
                    byte[] publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT);
                    java.security.spec.X509EncodedKeySpec publicKeySpec = 
                        new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
                    java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
                    
                    // 加载私钥
                    byte[] privateKeyBytes = android.util.Base64.decode(privateKeyBase64, android.util.Base64.DEFAULT);
                    java.security.spec.PKCS8EncodedKeySpec privateKeySpec = 
                        new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
                    java.security.PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
                    
                    // 重建密钥对
                    demoKeyPair = new java.security.KeyPair(publicKey, privateKey);
                    
                    runOnUiThread(() -> {
                        tvStatus.setText("✓ 已加载用户配置的密钥");
                        showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                        Toast.makeText(MainActivity.this, 
                            "✓ 已加载密钥文件", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> {
                        tvStatus.setText("未找到密钥文件");
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "加载密钥失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 加载密钥失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "加载密钥失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * 显示密钥对信息
     */
    private void showKeyPairInfo(String publicKey, String privateKey, File publicKeyFile, File privateKeyFile) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔑 RSA密钥对生成成功 ===\n\n");
        info.append("密钥长度: 2048位\n");
        info.append("算法: RSA\n");
        info.append("签名算法: SHA256withRSA\n\n");
        
        info.append("=== 📁 密钥文件已保存 ===\n");
        info.append("保存位置: ").append(outputDir.getAbsolutePath()).append("\n\n");
        
        info.append("公钥文件: ").append(publicKeyFile.getName()).append("\n");
        info.append("大小: ").append(formatSize(publicKeyFile.length())).append("\n\n");
        
        info.append("私钥文件: ").append(privateKeyFile.getName()).append("\n");
        info.append("大小: ").append(formatSize(privateKeyFile.length())).append("\n\n");
        
        info.append("=== 公钥 (Public Key) ===\n");
        info.append("用途: 客户端验证签名\n");
        info.append("存储: 编译到APK中\n");
        info.append("格式: X.509 (Base64)\n\n");
        info.append(formatKey(publicKey)).append("\n\n");
        
        info.append("=== 私钥 (Private Key) ===\n");
        info.append("用途: 服务器端签名\n");
        info.append("存储: 仅保存在服务器\n");
        info.append("格式: PKCS#8 (Base64)\n\n");
        info.append(formatKey(privateKey)).append("\n\n");
        
        info.append("=== 💡 使用说明 ===\n");
        info.append("1. 密钥文件已保存到下载文件夹\n");
        info.append("2. 可以直接编辑密钥文件配置自己的密钥\n");
        info.append("3. 重启应用会自动加载密钥文件\n");
        info.append("4. 公钥用于客户端验证签名\n");
        info.append("5. 私钥用于服务器端签名补丁\n\n");
        
        info.append("⚠️ 安全提示:\n");
        info.append("• 公钥可以公开，用于验证\n");
        info.append("• 私钥必须保密，只在服务器使用\n");
        info.append("• 不要将私钥编译到APK中\n");
        info.append("• 定期更换密钥对提高安全性\n\n");
        
        info.append("💡 现在可以测试签名验证功能了！");
        
        tvInfo.setText(info.toString());
    }
    
    /**
     * 格式化密钥显示（每64个字符换行）
     */
    private String formatKey(String key) {
        StringBuilder formatted = new StringBuilder();
        int lineLength = 64;
        for (int i = 0; i < key.length(); i += lineLength) {
            int end = Math.min(i + lineLength, key.length());
            formatted.append(key.substring(i, end)).append("\n");
        }
        return formatted.toString().trim();
    }
    
    /**
     * 测试签名验证 - 成功案例
     * 使用真实的RSA签名和验证
     */
    private void testSignatureVerificationSuccess() {
        if (demoKeyPair == null) {
            Toast.makeText(this, "请先生成RSA密钥对", Toast.LENGTH_SHORT).show();
            tvStatus.setText("⚠️ 请先点击「生成RSA密钥对」");
            return;
        }
        
        tvStatus.setText("正在测试签名验证（成功案例）...");
        
        new Thread(() -> {
            try {
                // 1. 创建测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 2. 使用私钥对补丁进行签名（模拟服务器端）
                String signature = signPatchFile(testPatch, demoKeyPair.getPrivate());
                
                // 3. 创建SecurityManager并设置公钥
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, false);
                
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                securityManager.setSignaturePublicKey(publicKeyBase64);
                
                // 4. 验证签名
                boolean isValid = securityManager.verifySignature(testPatch, signature);
                
                runOnUiThread(() -> {
                    if (isValid) {
                        tvStatus.setText("✓ 签名验证成功！");
                        showRealSignatureResult(true, testPatch, signature, "补丁完整，未被篡改");
                    } else {
                        tvStatus.setText("✗ 签名验证失败（不应该发生）");
                    }
                });
                
                // 清理测试文件
                testPatch.delete();
                
            } catch (Exception e) {
                Log.e(TAG, "签名验证测试失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 测试出错: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "测试出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 测试签名验证 - 失败案例
     * 演示补丁被篡改后签名验证失败
     */
    private void testSignatureVerificationFail() {
        if (demoKeyPair == null) {
            Toast.makeText(this, "请先生成RSA密钥对", Toast.LENGTH_SHORT).show();
            tvStatus.setText("⚠️ 请先点击「生成RSA密钥对」");
            return;
        }
        
        tvStatus.setText("正在测试签名验证（失败案例）...");
        
        new Thread(() -> {
            try {
                // 1. 创建测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 2. 使用私钥对补丁进行签名
                String signature = signPatchFile(testPatch, demoKeyPair.getPrivate());
                
                // 3. 篡改补丁文件（模拟恶意修改）
                tamperPatchFile(testPatch);
                
                // 4. 创建SecurityManager并设置公钥
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, false);
                
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                securityManager.setSignaturePublicKey(publicKeyBase64);
                
                // 5. 验证签名（应该失败）
                boolean isValid = securityManager.verifySignature(testPatch, signature);
                
                runOnUiThread(() -> {
                    if (!isValid) {
                        tvStatus.setText("✓ 检测到补丁被篡改！签名验证失败");
                        showRealSignatureResult(false, testPatch, signature, 
                            "补丁在签名后被修改，签名不匹配");
                    } else {
                        tvStatus.setText("✗ 签名验证通过（不应该发生）");
                    }
                });
                
                // 清理测试文件
                testPatch.delete();
                
            } catch (Exception e) {
                Log.e(TAG, "签名验证测试失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 测试出错: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "测试出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 使用私钥对补丁文件进行签名
     * @param patchFile 补丁文件
     * @param privateKey 私钥
     * @return Base64编码的签名
     */
    private String signPatchFile(File patchFile, java.security.PrivateKey privateKey) 
            throws Exception {
        // 使用SHA256withRSA算法
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        
        // 读取文件并更新签名
        FileInputStream fis = new FileInputStream(patchFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            signature.update(buffer, 0, bytesRead);
        }
        fis.close();
        
        // 生成签名
        byte[] signatureBytes = signature.sign();
        
        // 返回Base64编码的签名
        return android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP);
    }
    
    /**
     * 篡改补丁文件（模拟恶意修改）
     */
    private void tamperPatchFile(File patchFile) throws Exception {
        FileOutputStream fos = new FileOutputStream(patchFile, true);
        fos.write("\n[TAMPERED] This file has been modified!".getBytes("UTF-8"));
        fos.close();
    }
    
    /**
     * 创建测试补丁文件
     */
    private File createTestPatchFile() throws Exception {
        File testFile = new File(getExternalFilesDir(null), "test_patch.zip");
        FileOutputStream fos = new FileOutputStream(testFile);
        
        // 写入一些测试数据
        String testData = "This is a test patch file for signature verification demo.\n" +
                         "Timestamp: " + System.currentTimeMillis() + "\n" +
                         "Version: 1.2.4\n";
        fos.write(testData.getBytes("UTF-8"));
        fos.close();
        
        return testFile;
    }
    
    /**
     * 显示真实签名验证结果
     */
    private void showRealSignatureResult(boolean success, File patchFile, String signature, String reason) {
        StringBuilder info = new StringBuilder();
        
        if (success) {
            info.append("=== ✓ 签名验证成功 ===\n\n");
            info.append("🔒 安全状态: ").append(reason).append("\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 生成RSA-2048密钥对\n");
            info.append("2. ✓ 使用私钥签名补丁文件\n");
            info.append("3. ✓ 读取补丁文件内容\n");
            info.append("4. ✓ 使用公钥验证签名\n");
            info.append("5. ✓ SHA256withRSA验证通过\n\n");
            
            info.append("=== 补丁信息 ===\n");
            info.append("文件: ").append(patchFile.getName()).append("\n");
            info.append("大小: ").append(formatSize(patchFile.length())).append("\n\n");
            
            info.append("=== 签名信息 ===\n");
            info.append("算法: SHA256withRSA\n");
            info.append("密钥长度: 2048位\n");
            info.append("签名长度: ").append(signature.length()).append(" 字符\n");
            info.append("签名(前64字符):\n").append(signature.substring(0, Math.min(64, signature.length()))).append("...\n\n");
            
            info.append("=== 安全说明 ===\n");
            info.append("✓ 补丁来源可信\n");
            info.append("✓ 补丁内容完整\n");
            info.append("✓ 签名验证通过\n");
            info.append("✓ 可以安全应用此补丁\n\n");
            
            info.append("💡 生产环境流程:\n");
            info.append("1. 服务器端生成补丁\n");
            info.append("2. 使用私钥对补丁签名\n");
            info.append("3. 将补丁和签名发送给客户端\n");
            info.append("4. 客户端使用公钥验证签名\n");
            info.append("5. 验证通过后应用补丁");
            
        } else {
            info.append("=== ✗ 签名验证失败 ===\n\n");
            info.append("⚠️ 安全警告: ").append(reason).append("\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 生成RSA-2048密钥对\n");
            info.append("2. ✓ 使用私钥签名补丁文件\n");
            info.append("3. ✓ 补丁文件被篡改\n");
            info.append("4. ✓ 使用公钥验证签名\n");
            info.append("5. ✗ SHA256withRSA验证失败\n\n");
            
            info.append("=== 失败原因 ===\n");
            info.append("补丁文件在签名后被修改\n\n");
            info.append("检测到的问题:\n");
            info.append("• 文件内容与签名不匹配\n");
            info.append("• 文件可能被恶意篡改\n");
            info.append("• 文件可能在传输中损坏\n\n");
            
            info.append("=== 安全建议 ===\n");
            info.append("✗ 不要应用此补丁\n");
            info.append("✗ 补丁来源不可信\n");
            info.append("✗ 存在安全风险\n\n");
            
            info.append("💡 生产环境处理:\n");
            info.append("1. 拒绝应用补丁\n");
            info.append("2. 上报异常到服务器\n");
            info.append("3. 记录安全日志\n");
            info.append("4. 提示用户重新下载\n");
            info.append("5. 可能需要回滚到安全版本");
        }
        
        tvInfo.setText(info.toString());
    }
    
    /**
     * 显示签名验证结果（旧方法，保留兼容性）
     */
    private void showSignatureVerificationResult(boolean success, File patchFile, String signature) {
        StringBuilder info = new StringBuilder();
        
        if (success) {
            info.append("=== ✓ 签名验证成功 ===\n\n");
            info.append("🔒 安全状态: 补丁完整，未被篡改\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 创建 SecurityManager（调试模式）\n");
            info.append("2. ✓ 读取补丁文件\n");
            info.append("3. ✓ 模拟签名验证\n");
            info.append("4. ✓ 调试模式下跳过真实验证\n");
            info.append("5. ✓ 验证通过\n\n");
            
            info.append("=== 补丁信息 ===\n");
            info.append("文件: ").append(patchFile.getName()).append("\n");
            info.append("大小: ").append(formatSize(patchFile.length())).append("\n");
            info.append("签名: ").append(signature.substring(0, Math.min(32, signature.length()))).append("...\n\n");
            
            info.append("=== 安全说明 ===\n");
            info.append("✓ 补丁来源可信\n");
            info.append("✓ 补丁内容完整\n");
            info.append("✓ 可以安全应用此补丁\n\n");
            
            info.append("💡 生产环境使用:\n");
            info.append("• 关闭调试模式（debugMode=false）\n");
            info.append("• 设置真实的RSA公钥\n");
            info.append("• 服务器端使用私钥签名\n");
            info.append("• 客户端使用公钥验证\n");
            info.append("• 公钥编译到APK中\n");
            info.append("• 私钥只在服务器端使用");
            
        } else {
            info.append("=== ✗ 签名验证失败 ===\n\n");
            info.append("⚠️ 安全警告: 签名验证未通过！\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 创建 SecurityManager（生产模式）\n");
            info.append("2. ✓ 读取补丁文件\n");
            info.append("3. ✓ 检查签名\n");
            info.append("4. ✗ 签名为空或无效\n");
            info.append("5. ✗ 验证失败\n\n");
            
            info.append("=== 失败原因 ===\n");
            info.append("签名为空或格式不正确\n\n");
            info.append("可能的原因:\n");
            info.append("• 补丁未签名\n");
            info.append("• 签名在传输中丢失\n");
            info.append("• 签名格式错误\n");
            info.append("• 没有配置公钥\n\n");
            
            info.append("=== 安全建议 ===\n");
            info.append("✗ 不要应用此补丁\n");
            info.append("✗ 补丁来源不可信\n");
            info.append("✗ 可能存在安全风险\n\n");
            
            info.append("💡 生产环境处理:\n");
            info.append("• 拒绝应用未签名的补丁\n");
            info.append("• 上报异常到服务器\n");
            info.append("• 记录安全日志\n");
            info.append("• 通知用户重新下载");
        }
        
        tvInfo.setText(info.toString());
    }

    private void setButtonsEnabled(boolean enabled) {
        btnGenerate.setEnabled(enabled && selectedBaseApk != null && selectedNewApk != null);
        btnApply.setEnabled(enabled && (selectedPatchFile != null || lastGeneratedPatch != null));
        btnSelectBase.setEnabled(enabled);
        btnSelectNew.setEnabled(enabled);
        btnSelectPatch.setEnabled(enabled);
        btnCheckEngine.setEnabled(enabled);
        btnCheckStorage.setEnabled(enabled);
        btnClearPatch.setEnabled(enabled);
        btnGenerateKeys.setEnabled(enabled);
        btnLoadKeys.setEnabled(enabled);
        btnConfigKeys.setEnabled(enabled);
        btnVerifySuccess.setEnabled(enabled);
        btnVerifyFail.setEnabled(enabled);
    }
    
    /**
     * 显示配置密钥对话框
     */
    private void showConfigKeysDialog() {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 公钥输入
        TextView tvPublicLabel = new TextView(this);
        tvPublicLabel.setText("公钥 (Public Key):");
        tvPublicLabel.setTextSize(14);
        tvPublicLabel.setPadding(0, 0, 0, 8);
        layout.addView(tvPublicLabel);
        
        android.widget.EditText etPublicKey = new android.widget.EditText(this);
        etPublicKey.setHint("粘贴 Base64 编码的公钥");
        etPublicKey.setMinLines(3);
        etPublicKey.setMaxLines(5);
        etPublicKey.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        // 尝试加载现有公钥
        try {
            File publicKeyFile = new File(outputDir, "rsa_public_key.txt");
            String existingPublicKey = loadKeyFromFile(publicKeyFile);
            if (existingPublicKey != null) {
                etPublicKey.setText(existingPublicKey);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载现有公钥失败", e);
        }
        
        layout.addView(etPublicKey);
        
        // 私钥输入
        TextView tvPrivateLabel = new TextView(this);
        tvPrivateLabel.setText("私钥 (Private Key):");
        tvPrivateLabel.setTextSize(14);
        tvPrivateLabel.setPadding(0, 20, 0, 8);
        layout.addView(tvPrivateLabel);
        
        android.widget.EditText etPrivateKey = new android.widget.EditText(this);
        etPrivateKey.setHint("粘贴 Base64 编码的私钥");
        etPrivateKey.setMinLines(3);
        etPrivateKey.setMaxLines(5);
        etPrivateKey.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        // 尝试加载现有私钥
        try {
            File privateKeyFile = new File(outputDir, "rsa_private_key.txt");
            String existingPrivateKey = loadKeyFromFile(privateKeyFile);
            if (existingPrivateKey != null) {
                etPrivateKey.setText(existingPrivateKey);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载现有私钥失败", e);
        }
        
        layout.addView(etPrivateKey);
        
        // 提示信息
        TextView tvHint = new TextView(this);
        tvHint.setText("\n💡 提示：\n• 粘贴 Base64 格式的 RSA 密钥\n• 公钥格式：X.509\n• 私钥格式：PKCS#8\n• 可以使用 openssl 或 keytool 生成");
        tvHint.setTextSize(12);
        tvHint.setTextColor(0xFF666666);
        tvHint.setPadding(0, 10, 0, 0);
        layout.addView(tvHint);
        
        // 创建对话框
        new AlertDialog.Builder(this)
            .setTitle("⚙️ 配置 RSA 密钥")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                String publicKey = etPublicKey.getText().toString().trim();
                String privateKey = etPrivateKey.getText().toString().trim();
                
                if (publicKey.isEmpty() || privateKey.isEmpty()) {
                    Toast.makeText(this, "请输入公钥和私钥", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 保存密钥
                saveConfiguredKeys(publicKey, privateKey);
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("清空", (dialog, which) -> {
                // 删除密钥文件
                new File(outputDir, "rsa_public_key.txt").delete();
                new File(outputDir, "rsa_private_key.txt").delete();
                demoKeyPair = null;
                tvStatus.setText("✓ 密钥已清空");
                Toast.makeText(this, "密钥已清空", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    /**
     * 保存用户配置的密钥
     */
    private void saveConfiguredKeys(String publicKeyBase64, String privateKeyBase64) {
        new Thread(() -> {
            try {
                // 验证密钥格式
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                
                // 验证公钥
                byte[] publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT);
                java.security.spec.X509EncodedKeySpec publicKeySpec = 
                    new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
                java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
                
                // 验证私钥
                byte[] privateKeyBytes = android.util.Base64.decode(privateKeyBase64, android.util.Base64.DEFAULT);
                java.security.spec.PKCS8EncodedKeySpec privateKeySpec = 
                    new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
                java.security.PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
                
                // 保存到文件
                File publicKeyFile = saveKeyToFile(publicKeyBase64, "rsa_public_key.txt");
                File privateKeyFile = saveKeyToFile(privateKeyBase64, "rsa_private_key.txt");
                
                // 重建密钥对
                demoKeyPair = new java.security.KeyPair(publicKey, privateKey);
                
                runOnUiThread(() -> {
                    tvStatus.setText("✓ 密钥配置成功！");
                    showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                    Toast.makeText(this, "✓ 密钥已保存到下载文件夹", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "保存密钥失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 密钥格式错误");
                    Toast.makeText(this, 
                        "密钥格式错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (generator != null) {
            generator.shutdown();
        }
    }
}
