package com.orange.patchgen.differ;

import com.orange.patchgen.callback.GeneratorErrorCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 资源差异比较器
 * 
 * 负责比较两个目录的资源文件差异，识别修改、新增、删除的文件。
 * 使用文件 MD5 哈希进行内容比较。
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
public class ResourceDiffer {

    private static final int BUFFER_SIZE = 8192;

    /**
     * 比较两个资源目录 (res/)
     * 
     * @param baseResDir 基准资源目录
     * @param newResDir 新版本资源目录
     * @return 差异比较结果
     * @throws ResourceDiffException 比较失败时抛出
     */
    public ResourceDiffResult compare(File baseResDir, File newResDir) throws ResourceDiffException {
        return compareDirectories(baseResDir, newResDir, "res");
    }

    /**
     * 比较两个 assets 目录
     * 
     * @param baseAssetsDir 基准 assets 目录
     * @param newAssetsDir 新版本 assets 目录
     * @return 差异比较结果
     * @throws ResourceDiffException 比较失败时抛出
     */
    public ResourceDiffResult compareAssets(File baseAssetsDir, File newAssetsDir) throws ResourceDiffException {
        return compareDirectories(baseAssetsDir, newAssetsDir, "assets");
    }

    /**
     * 比较两个目录
     * 
     * @param baseDir 基准目录
     * @param newDir 新版本目录
     * @param dirType 目录类型描述（用于错误消息）
     * @return 差异比较结果
     * @throws ResourceDiffException 比较失败时抛出
     */
    private ResourceDiffResult compareDirectories(File baseDir, File newDir, String dirType) 
            throws ResourceDiffException {
        ResourceDiffResult result = new ResourceDiffResult();

        // 处理目录不存在的情况
        boolean baseExists = baseDir != null && baseDir.exists() && baseDir.isDirectory();
        boolean newExists = newDir != null && newDir.exists() && newDir.isDirectory();

        if (!baseExists && !newExists) {
            // 两个目录都不存在，无变化
            return result;
        }

        if (!baseExists && newExists) {
            // 基准目录不存在，新目录存在 - 所有文件都是新增的
            try {
                Map<String, FileInfo> newFiles = scanDirectory(newDir);
                for (Map.Entry<String, FileInfo> entry : newFiles.entrySet()) {
                    FileInfo info = entry.getValue();
                    result.addAddedFile(FileChange.added(entry.getKey(), info.md5, info.size));
                }
            } catch (IOException e) {
                throw new ResourceDiffException("Failed to scan " + dirType + " directory: " + e.getMessage(),
                        GeneratorErrorCode.ERROR_COMPARE_FAILED, e);
            }
            return result;
        }

        if (baseExists && !newExists) {
            // 基准目录存在，新目录不存在 - 所有文件都是删除的
            try {
                Map<String, FileInfo> baseFiles = scanDirectory(baseDir);
                for (String relativePath : baseFiles.keySet()) {
                    result.addDeletedFile(relativePath);
                }
            } catch (IOException e) {
                throw new ResourceDiffException("Failed to scan " + dirType + " directory: " + e.getMessage(),
                        GeneratorErrorCode.ERROR_COMPARE_FAILED, e);
            }
            return result;
        }

        // 两个目录都存在，进行详细比较
        try {
            Map<String, FileInfo> baseFiles = scanDirectory(baseDir);
            Map<String, FileInfo> newFiles = scanDirectory(newDir);

            compareFileMaps(baseFiles, newFiles, result);

        } catch (IOException e) {
            throw new ResourceDiffException("Failed to compare " + dirType + " directories: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_COMPARE_FAILED, e);
        }

        return result;
    }

    /**
     * 递归扫描目录，收集所有文件的信息
     * 
     * @param dir 要扫描的目录
     * @return 相对路径到文件信息的映射
     * @throws IOException 扫描失败时抛出
     */
    private Map<String, FileInfo> scanDirectory(File dir) throws IOException {
        Map<String, FileInfo> files = new HashMap<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return files;
        }
        scanDirectoryRecursive(dir, dir, files);
        return files;
    }

    /**
     * 递归扫描目录的内部实现
     * 
     * @param rootDir 根目录（用于计算相对路径）
     * @param currentDir 当前扫描的目录
     * @param files 收集文件信息的映射
     * @throws IOException 扫描失败时抛出
     */
    private void scanDirectoryRecursive(File rootDir, File currentDir, Map<String, FileInfo> files) 
            throws IOException {
        File[] children = currentDir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                // 递归处理子目录
                scanDirectoryRecursive(rootDir, child, files);
            } else if (child.isFile()) {
                // 计算相对路径
                String relativePath = getRelativePath(rootDir, child);
                // 计算文件 MD5
                String md5 = calculateFileMd5(child);
                long size = child.length();
                
                files.put(relativePath, new FileInfo(md5, size));
            }
        }
    }

    /**
     * 获取文件相对于根目录的相对路径
     * 
     * @param rootDir 根目录
     * @param file 文件
     * @return 相对路径（使用 / 作为分隔符）
     */
    private String getRelativePath(File rootDir, File file) {
        String rootPath = rootDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        
        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            // 移除开头的分隔符
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            // 统一使用 / 作为路径分隔符
            return relative.replace(File.separatorChar, '/');
        }
        return file.getName();
    }

    /**
     * 比较两个文件映射，生成差异结果
     * 
     * @param baseFiles 基准文件映射
     * @param newFiles 新版本文件映射
     * @param result 差异结果
     */
    private void compareFileMaps(Map<String, FileInfo> baseFiles, Map<String, FileInfo> newFiles,
                                  ResourceDiffResult result) {
        // 收集所有文件路径
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(newFiles.keySet());

        for (String path : allPaths) {
            FileInfo baseInfo = baseFiles.get(path);
            FileInfo newInfo = newFiles.get(path);

            if (baseInfo == null && newInfo != null) {
                // 新增的文件
                result.addAddedFile(FileChange.added(path, newInfo.md5, newInfo.size));
            } else if (baseInfo != null && newInfo == null) {
                // 删除的文件
                result.addDeletedFile(path);
            } else if (baseInfo != null && newInfo != null) {
                // 两边都存在，比较 MD5
                if (!baseInfo.md5.equals(newInfo.md5)) {
                    // 文件被修改
                    result.addModifiedFile(FileChange.modified(
                            path, baseInfo.md5, newInfo.md5, baseInfo.size, newInfo.size));
                }
            }
        }
    }

    /**
     * 计算文件的 MD5 哈希值
     * 
     * @param file 要计算哈希的文件
     * @return MD5 哈希值（32位十六进制字符串）
     * @throws IOException 读取文件失败时抛出
     */
    String calculateFileMd5(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 应该总是可用的
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     * 
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 文件信息内部类
     */
    private static class FileInfo {
        final String md5;
        final long size;

        FileInfo(String md5, long size) {
            this.md5 = md5;
            this.size = size;
        }
    }
}
