package com.orange.update.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.orange.update.HotUpdateHelper;
import com.orange.update.R;
import com.orange.update.helper.DialogHelper;
import com.orange.update.helper.FilePickerHelper;
import com.orange.update.helper.FormatHelper;
import com.orange.update.viewmodel.PatchApplyViewModel;

import java.io.File;

/**
 * 补丁应用 Fragment
 * 负责补丁应用相关的 UI 和交互
 */
public class PatchApplyFragment extends Fragment {
    
    private PatchApplyViewModel viewModel;
    private FilePickerHelper filePickerHelper;
    private HotUpdateHelper hotUpdateHelper;
    
    // UI 组件
    private TextView tvStatus;
    private TextView tvInfo;
    private ProgressBar progressBar;
    private Button btnSelectPatch;
    private Button btnApply;
    private Button btnClear;
    
    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(PatchApplyViewModel.class);
        
        // 初始化热更新助手
        hotUpdateHelper = new HotUpdateHelper(requireContext());
        viewModel.setHotUpdateHelper(hotUpdateHelper);
        viewModel.setContext(requireContext());
        
        // 初始化文件选择器
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePickerHelper != null) {
                    filePickerHelper.handleResult(result);
                }
            }
        );
        
        filePickerHelper = new FilePickerHelper(requireActivity(), filePickerLauncher);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patch_apply, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupListeners();
        observeViewModel();
        updatePatchInfo();
    }
    
    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);
        tvInfo = view.findViewById(R.id.tv_info);
        progressBar = view.findViewById(R.id.progress_bar);
        btnSelectPatch = view.findViewById(R.id.btn_select_patch);
        btnApply = view.findViewById(R.id.btn_apply);
        btnClear = view.findViewById(R.id.btn_clear);
    }
    
    private void setupListeners() {
        btnSelectPatch.setOnClickListener(v -> selectPatch());
        btnApply.setOnClickListener(v -> applyPatch());
        btnClear.setOnClickListener(v -> clearPatch());
    }
    
    private void observeViewModel() {
        // 观察应用进度
        viewModel.getApplyProgress().observe(getViewLifecycleOwner(), progress -> {
            progressBar.setProgress(progress);
        });
        
        // 观察应用状态
        viewModel.getApplyStatus().observe(getViewLifecycleOwner(), status -> {
            tvStatus.setText(status);
            
            // 处理 AES 密码要求
            if ("AES_PASSWORD_REQUIRED".equals(status)) {
                File patchFile = viewModel.getPatchFile();
                if (patchFile != null) {
                    showAesPasswordDialog(patchFile);
                }
            }
            
            // 处理 ZIP 密码要求
            if ("ZIP_PASSWORD_REQUIRED".equals(status)) {
                File patchFile = viewModel.getPatchFile();
                if (patchFile != null) {
                    showZipPasswordDialog(patchFile);
                }
            }
        });
        
        // 观察安全策略错误
        viewModel.getSecurityPolicyError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showSecurityPolicyError(error);
            }
        });
        
        // 观察应用结果
        viewModel.getApplyResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                if (result.success) {
                    showSuccessResult(result);
                } else {
                    String errorMsg = viewModel.getApplyStatus().getValue();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = "应用失败";
                    }
                    DialogHelper.showErrorDialog(requireContext(), "应用失败", errorMsg);
                }
            }
        });
        
        // 观察应用状态
        viewModel.getIsApplying().observe(getViewLifecycleOwner(), isApplying -> {
            progressBar.setVisibility(isApplying ? View.VISIBLE : View.GONE);
            btnApply.setEnabled(!isApplying && viewModel.canApply());
            btnSelectPatch.setEnabled(!isApplying);
        });
        
        // 观察补丁应用状态
        viewModel.getIsPatchApplied().observe(getViewLifecycleOwner(), isPatchApplied -> {
            btnClear.setVisibility(isPatchApplied ? View.VISIBLE : View.GONE);
            updatePatchInfo();
        });
    }
    
    private void selectPatch() {
        filePickerHelper.pickZipFile(new FilePickerHelper.FilePickerCallback() {
            @Override
            public void onFileSelected(Uri uri, File destFile) {
                viewModel.setPatchFile(destFile);
                String patchInfo = destFile.getName().endsWith(".enc") ? "加密补丁: " : "补丁: ";
                btnSelectPatch.setText(patchInfo + FormatHelper.formatSize(destFile.length()));
                updatePatchInfo();
                updateButtonStates();
            }
            
            @Override
            public void onError(String message) {
                DialogHelper.showToast(requireContext(), message);
            }
        });
    }
    
    private void applyPatch() {
        if (!viewModel.canApply()) {
            DialogHelper.showToast(requireContext(), "请先选择补丁文件");
            return;
        }
        
        viewModel.applyPatch();
    }
    
    private void clearPatch() {
        DialogHelper.showConfirmDialog(requireContext(), "确认", "确定要清除补丁吗？",
            new DialogHelper.ConfirmCallback() {
                @Override
                public void onConfirm() {
                    viewModel.clearPatch();
                    DialogHelper.showToast(requireContext(), "补丁已清除");
                }
                
                @Override
                public void onCancel() {
                    // 用户取消
                }
            });
    }
    
    private void updatePatchInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 补丁信息 ===\n\n");
        
        File patchFile = viewModel.getPatchFile();
        if (patchFile != null) {
            info.append("📋 选择的补丁: ").append(patchFile.getName()).append("\n");
            info.append("📊 大小: ").append(FormatHelper.formatSize(patchFile.length())).append("\n\n");
        } else {
            info.append("📋 补丁: 未选择\n\n");
        }
        
        // 显示热更新状态
        if (hotUpdateHelper.isPatchApplied()) {
            info.append("🔥 热更新状态: 已应用\n");
            info.append("补丁版本: ").append(hotUpdateHelper.getPatchedVersion()).append("\n");
            info.append("DEX 注入: ").append(hotUpdateHelper.isDexInjected() ? "✓" : "✗").append("\n");
            long patchTime = hotUpdateHelper.getPatchTime();
            if (patchTime > 0) {
                info.append("应用时间: ").append(FormatHelper.formatTimestamp(patchTime)).append("\n");
            }
        } else {
            info.append("🔥 热更新状态: 未应用\n");
        }
        
        tvInfo.setText(info.toString());
    }
    
    private void updateButtonStates() {
        btnApply.setEnabled(viewModel.canApply());
    }
    
    private void showSuccessResult(HotUpdateHelper.PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔥 热更新成功 ===\n\n");
        info.append("补丁版本: ").append(result.patchVersion).append("\n");
        info.append("DEX 注入: ").append(result.dexInjected ? "✓" : "✗").append("\n");
        info.append("资源更新: ").append(result.resourcesLoaded ? "✓" : "✗").append("\n");
        info.append("SO 更新: ").append(result.soLoaded ? "✓" : "✗").append("\n");
        
        tvInfo.setText(info.toString());
        tvStatus.setText("🔥 热更新成功！");
        
        // 如果包含资源更新，提示用户重启
        if (result.resourcesLoaded || result.needsRestart) {
            showRestartPrompt();
        } else {
            DialogHelper.showInfoDialog(requireContext(), "成功", "热更新应用成功！");
        }
    }
    
    /**
     * 显示安全策略错误对话框
     */
    private void showSecurityPolicyError(PatchApplyViewModel.SecurityPolicyError error) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ 安全策略限制")
            .setMessage(error.message)
            .setPositiveButton("确定", null)
            .setNeutralButton("安全设置", (d, w) -> {
                // 跳转到系统信息 Fragment 的安全设置
                // 这里可以通过 Navigation 或者 Activity 方法跳转
                if (getActivity() != null) {
                    DialogHelper.showToast(requireContext(), "请在「系统信息」页面修改安全策略");
                }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    /**
     * 显示重启提示对话框
     */
    private void showRestartPrompt() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔥 热更新成功")
            .setMessage("补丁已成功应用！\n\n" +
                       "检测到资源文件更新，建议重启应用以确保资源正确加载。\n\n" +
                       "是否立即重启应用？")
            .setPositiveButton("立即重启", (d, w) -> {
                restartApp();
            })
            .setNegativeButton("稍后重启", (d, w) -> {
                DialogHelper.showToast(requireContext(), "请稍后手动重启应用");
            })
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }
    
    /**
     * 重启应用
     */
    private void restartApp() {
        if (getActivity() != null) {
            Intent intent = requireActivity().getPackageManager()
                .getLaunchIntentForPackage(requireActivity().getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                requireActivity().finish();
                
                // 杀死当前进程，确保完全重启
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }
    
    /**
     * 显示 AES 密码输入对话框
     */
    private void showAesPasswordDialog(File patchFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("🔐 需要密码");
        builder.setMessage("此补丁使用自定义密码加密，请输入密码：");
        builder.setCancelable(false);
        
        // 创建密码输入框
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入密码");
        
        // 添加内边距
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                DialogHelper.showToast(requireContext(), "密码不能为空");
                // 重新显示对话框
                showAesPasswordDialog(patchFile);
                return;
            }
            viewModel.applyPatchWithAesPassword(patchFile, password);
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            DialogHelper.showToast(requireContext(), "已取消应用补丁");
            viewModel.resetApplyStatus();
        });
        
        builder.show();
    }
    
    /**
     * 显示 ZIP 密码输入对话框
     */
    private void showZipPasswordDialog(File patchFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("🔐 需要 ZIP 密码");
        builder.setMessage("此补丁使用 ZIP 密码保护，请输入密码：");
        builder.setCancelable(false);
        
        // 创建密码输入框
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入 ZIP 密码");
        
        // 添加内边距
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                DialogHelper.showToast(requireContext(), "密码不能为空");
                // 重新显示对话框
                showZipPasswordDialog(patchFile);
                return;
            }
            viewModel.applyPatchWithZipPassword(patchFile, password);
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            DialogHelper.showToast(requireContext(), "已取消应用补丁");
            viewModel.resetApplyStatus();
        });
        
        builder.show();
    }
}
