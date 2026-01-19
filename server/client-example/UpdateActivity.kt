package com.orange.update.example

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orange.update.HotUpdateHelper
import kotlinx.coroutines.launch

/**
 * 更新检查示例 Activity
 */
class UpdateActivity : AppCompatActivity() {
    
    private lateinit var updateChecker: UpdateChecker
    private lateinit var hotUpdateHelper: HotUpdateHelper
    
    private lateinit var tvVersion: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnCheck: Button
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        updateChecker = UpdateChecker(this)
        hotUpdateHelper = HotUpdateHelper(this)
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        // 初始化视图（示例代码）
        tvVersion = findViewById(R.id.tv_version)
        tvStatus = findViewById(R.id.tv_status)
        btnCheck = findViewById(R.id.btn_check_update)
        progressBar = findViewById(R.id.progress_bar)
        
        // 显示当前版本
        tvVersion.text = "当前版本: ${getCurrentVersion()}"
    }
    
    private fun setupListeners() {
        btnCheck.setOnClickListener {
            checkForUpdate()
        }
    }
    
    /**
     * 检查更新
     */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            btnCheck.isEnabled = false
            tvStatus.text = "正在检查更新..."
            
            val currentVersion = getCurrentVersion()
            val result = updateChecker.checkUpdate(currentVersion)
            
            when (result) {
                is UpdateResult.HasUpdate -> {
                    // 有新版本
                    tvStatus.text = "发现新版本: ${result.patchInfo.version}"
                    showUpdateDialog(result.patchInfo)
                }
                
                is UpdateResult.NoUpdate -> {
                    // 已是最新版本
                    tvStatus.text = "已是最新版本"
                    Toast.makeText(this@UpdateActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                }
                
                is UpdateResult.Error -> {
                    // 检查失败
                    tvStatus.text = "检查失败: ${result.message}"
                    Toast.makeText(this@UpdateActivity, "检查失败: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            btnCheck.isEnabled = true
        }
    }
    
    /**
     * 显示更新对话框
     */
    private fun showUpdateDialog(patchInfo: PatchInfo) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("发现新版本")
            .setMessage("""
                版本: ${patchInfo.version}
                大小: ${formatSize(patchInfo.size)}
                
                更新说明:
                ${patchInfo.description}
            """.trimIndent())
            .setPositiveButton("立即更新") { _, _ ->
                downloadAndApplyPatch(patchInfo)
            }
            .setNegativeButton("稍后更新", null)
            .setCancelable(!patchInfo.forceUpdate)
            .show()
    }
    
    /**
     * 下载并应用补丁
     */
    private fun downloadAndApplyPatch(patchInfo: PatchInfo) {
        lifecycleScope.launch {
            progressBar.visibility = android.view.View.VISIBLE
            tvStatus.text = "正在下载补丁..."
            
            val result = updateChecker.downloadPatch(patchInfo) { progress ->
                progressBar.progress = progress
                tvStatus.text = "正在下载补丁... $progress%"
            }
            
            when (result) {
                is DownloadResult.Success -> {
                    tvStatus.text = "下载完成，正在应用补丁..."
                    applyPatch(result.file)
                }
                
                is DownloadResult.Error -> {
                    tvStatus.text = "下载失败: ${result.message}"
                    Toast.makeText(this@UpdateActivity, "下载失败: ${result.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }
    
    /**
     * 应用补丁
     */
    private fun applyPatch(patchFile: java.io.File) {
        hotUpdateHelper.applyPatch(patchFile, object : HotUpdateHelper.Callback {
            override fun onProgress(percent: Int, message: String) {
                runOnUiThread {
                    progressBar.progress = percent
                    tvStatus.text = message
                }
            }
            
            override fun onSuccess(result: HotUpdateHelper.PatchResult) {
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    tvStatus.text = "补丁应用成功！"
                    
                    // 显示重启对话框
                    androidx.appcompat.app.AlertDialog.Builder(this@UpdateActivity)
                        .setTitle("更新成功")
                        .setMessage("补丁已应用，需要重启应用生效")
                        .setPositiveButton("立即重启") { _, _ ->
                            restartApp()
                        }
                        .setNegativeButton("稍后重启", null)
                        .setCancelable(false)
                        .show()
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    tvStatus.text = "补丁应用失败: $message"
                    Toast.makeText(this@UpdateActivity, "补丁应用失败: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    
    /**
     * 重启应用
     */
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
    
    /**
     * 获取当前版本
     */
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / 1024 / 1024} MB"
        }
    }
}
