package com.orange.update.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 补丁更新检查器
 * 
 * 使用示例：
 * ```kotlin
 * val checker = UpdateChecker(context)
 * checker.checkUpdate { result ->
 *     when (result) {
 *         is UpdateResult.HasUpdate -> {
 *             // 有新版本
 *             downloadPatch(result.patchInfo)
 *         }
 *         is UpdateResult.NoUpdate -> {
 *             // 已是最新版本
 *         }
 *         is UpdateResult.Error -> {
 *             // 检查失败
 *         }
 *     }
 * }
 * ```
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateChecker"
        
        // GitHub 原始 URL
        private const val VERSION_URL = "https://raw.githubusercontent.com/706412584/Android_hotupdate/main/version.json"
        
        // CDN 加速 URL（可选）
        private const val VERSION_URL_CDN = "https://cdn.jsdelivr.net/gh/706412584/Android_hotupdate@main/version.json"
    }
    
    private val client = OkHttpClient()
    
    /**
     * 检查更新
     */
    suspend fun checkUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // 先尝试 CDN，失败后使用原始 URL
            val versionInfo = fetchVersionInfo(VERSION_URL_CDN) 
                ?: fetchVersionInfo(VERSION_URL)
                ?: return@withContext UpdateResult.Error("无法获取版本信息")
            
            // 比较版本
            if (compareVersion(versionInfo.latestVersion, currentVersion) > 0) {
                // 有新版本
                val patch = versionInfo.patches.firstOrNull()
                if (patch != null) {
                    UpdateResult.HasUpdate(patch)
                } else {
                    UpdateResult.Error("补丁信息不完整")
                }
            } else {
                UpdateResult.NoUpdate
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            UpdateResult.Error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 获取版本信息
     */
    private fun fetchVersionInfo(url: String): VersionInfo? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "请求失败: ${response.code}")
                return null
            }
            
            val json = response.body?.string() ?: return null
            parseVersionInfo(json)
        } catch (e: IOException) {
            Log.w(TAG, "网络请求失败: $url", e)
            null
        }
    }
    
    /**
     * 解析版本信息
     */
    private fun parseVersionInfo(json: String): VersionInfo {
        val obj = JSONObject(json)
        val latestVersion = obj.getString("latest_version")
        val minVersion = obj.getString("min_version")
        val updateUrl = obj.getString("update_url")
        
        val patchesArray = obj.getJSONArray("patches")
        val patches = mutableListOf<PatchInfo>()
        
        for (i in 0 until patchesArray.length()) {
            val patchObj = patchesArray.getJSONObject(i)
            patches.add(
                PatchInfo(
                    version = patchObj.getString("version"),
                    patchId = patchObj.getString("patch_id"),
                    baseVersion = patchObj.getString("base_version"),
                    downloadUrl = patchObj.getString("download_url"),
                    md5 = patchObj.getString("md5"),
                    size = patchObj.getLong("size"),
                    description = patchObj.getString("description"),
                    forceUpdate = patchObj.getBoolean("force_update"),
                    createTime = patchObj.getString("create_time")
                )
            )
        }
        
        return VersionInfo(latestVersion, minVersion, updateUrl, patches)
    }
    
    /**
     * 下载补丁
     */
    suspend fun downloadPatch(
        patchInfo: PatchInfo,
        onProgress: (progress: Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(patchInfo.downloadUrl)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("下载失败: ${response.code}")
            }
            
            val body = response.body ?: return@withContext DownloadResult.Error("响应体为空")
            val contentLength = body.contentLength()
            
            // 保存到外部存储
            val patchFile = File(
                context.getExternalFilesDir(null),
                "patch_${patchInfo.version}.zip"
            )
            
            body.byteStream().use { input ->
                patchFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 更新进度
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            
            // 验证 MD5
            val fileMd5 = calculateMD5(patchFile)
            if (fileMd5 != patchInfo.md5) {
                patchFile.delete()
                return@withContext DownloadResult.Error("MD5 校验失败")
            }
            
            DownloadResult.Success(patchFile)
        } catch (e: Exception) {
            Log.e(TAG, "下载补丁失败", e)
            DownloadResult.Error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 比较版本号
     * @return 1: v1 > v2, 0: v1 == v2, -1: v1 < v2
     */
    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            
            when {
                p1 > p2 -> return 1
                p1 < p2 -> return -1
            }
        }
        
        return 0
    }
    
    /**
     * 计算文件 MD5
     */
    private fun calculateMD5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * 版本信息
 */
data class VersionInfo(
    val latestVersion: String,
    val minVersion: String,
    val updateUrl: String,
    val patches: List<PatchInfo>
)

/**
 * 补丁信息
 */
data class PatchInfo(
    val version: String,
    val patchId: String,
    val baseVersion: String,
    val downloadUrl: String,
    val md5: String,
    val size: Long,
    val description: String,
    val forceUpdate: Boolean,
    val createTime: String
)

/**
 * 更新检查结果
 */
sealed class UpdateResult {
    data class HasUpdate(val patchInfo: PatchInfo) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

/**
 * 下载结果
 */
sealed class DownloadResult {
    data class Success(val file: File) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
