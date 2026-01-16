package com.orange.update;

/**
 * 版本比较工具类。
 * 支持语义化版本号（Semantic Versioning）的比较。
 * 版本格式：major.minor.patch（如 1.0.0, 1.2.3）
 */
public final class VersionUtils {
    
    private VersionUtils() {
        // 防止实例化
    }
    
    /**
     * 比较两个版本号
     * @param version1 第一个版本号
     * @param version2 第二个版本号
     * @return 如果 version1 > version2 返回正数，
     *         如果 version1 < version2 返回负数，
     *         如果相等返回 0
     * @throws IllegalArgumentException 如果版本号格式无效
     */
    public static int compareVersion(String version1, String version2) {
        if (version1 == null || version2 == null) {
            throw new IllegalArgumentException("Version cannot be null");
        }
        
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int v1 = i < parts1.length ? parseVersionPart(parts1[i], version1) : 0;
            int v2 = i < parts2.length ? parseVersionPart(parts2[i], version2) : 0;
            
            if (v1 != v2) {
                return v1 - v2;
            }
        }
        
        return 0;
    }
    
    /**
     * 判断服务器版本是否比本地版本新
     * @param serverVersion 服务器版本号
     * @param localVersion 本地版本号
     * @return 如果服务器版本更新返回 true，否则返回 false
     * @throws IllegalArgumentException 如果版本号格式无效
     */
    public static boolean isNewerVersion(String serverVersion, String localVersion) {
        return compareVersion(serverVersion, localVersion) > 0;
    }
    
    /**
     * 解析版本号的单个部分
     * @param part 版本号部分字符串
     * @param fullVersion 完整版本号（用于错误信息）
     * @return 解析后的整数值
     * @throws IllegalArgumentException 如果无法解析为整数
     */
    private static int parseVersionPart(String part, String fullVersion) {
        if (part == null || part.isEmpty()) {
            return 0;
        }
        
        try {
            int value = Integer.parseInt(part.trim());
            if (value < 0) {
                throw new IllegalArgumentException(
                    "Version part cannot be negative: " + part + " in " + fullVersion);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid version format: " + fullVersion + 
                " (cannot parse '" + part + "' as integer)");
        }
    }
    
    /**
     * 验证版本号格式是否有效
     * @param version 版本号
     * @return 如果格式有效返回 true，否则返回 false
     */
    public static boolean isValidVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        
        String[] parts = version.split("\\.");
        if (parts.length == 0) {
            return false;
        }
        
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value < 0) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
}
