package com.orange.update;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * SDK 配置类，使用 Builder 模式构建。
 * 包含服务器地址、应用标识、版本信息和超时设置等配置项。
 */
public class UpdateConfig {
    
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 30000;    // 30 seconds
    
    private final String serverUrl;
    private final String appKey;
    private final String appVersion;
    private final int connectTimeout;
    private final int readTimeout;
    private final boolean debugMode;
    
    private UpdateConfig(Builder builder) {
        this.serverUrl = builder.serverUrl;
        this.appKey = builder.appKey;
        this.appVersion = builder.appVersion;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.debugMode = builder.debugMode;
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public String getAppKey() {
        return appKey;
    }
    
    public String getAppVersion() {
        return appVersion;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Builder 类用于构建 UpdateConfig 实例
     */
    public static class Builder {
        private String serverUrl;
        private String appKey;
        private String appVersion;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;
        private boolean debugMode = false;
        
        public Builder() {
        }
        
        /**
         * 设置服务器 URL
         * @param url 服务器地址，必须是有效的 HTTP/HTTPS URL
         * @return Builder 实例
         * @throws IllegalArgumentException 如果 URL 格式无效
         */
        public Builder serverUrl(String url) {
            validateUrl(url);
            this.serverUrl = url;
            return this;
        }
        
        /**
         * 设置应用标识
         * @param key 应用唯一标识
         * @return Builder 实例
         */
        public Builder appKey(String key) {
            this.appKey = key;
            return this;
        }
        
        /**
         * 设置应用版本
         * @param version 应用版本号
         * @return Builder 实例
         */
        public Builder appVersion(String version) {
            this.appVersion = version;
            return this;
        }
        
        /**
         * 设置连接超时时间
         * @param timeout 超时时间（毫秒）
         * @return Builder 实例
         */
        public Builder connectTimeout(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException("Connect timeout must be positive");
            }
            this.connectTimeout = timeout;
            return this;
        }
        
        /**
         * 设置读取超时时间
         * @param timeout 超时时间（毫秒）
         * @return Builder 实例
         */
        public Builder readTimeout(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException("Read timeout must be positive");
            }
            this.readTimeout = timeout;
            return this;
        }
        
        /**
         * 设置调试模式
         * @param debug 是否开启调试模式
         * @return Builder 实例
         */
        public Builder debugMode(boolean debug) {
            this.debugMode = debug;
            return this;
        }
        
        /**
         * 构建 UpdateConfig 实例
         * @return UpdateConfig 实例
         * @throws IllegalStateException 如果必要参数未设置
         */
        public UpdateConfig build() {
            if (serverUrl == null || serverUrl.isEmpty()) {
                throw new IllegalStateException("Server URL is required");
            }
            if (appKey == null || appKey.isEmpty()) {
                throw new IllegalStateException("App key is required");
            }
            if (appVersion == null || appVersion.isEmpty()) {
                throw new IllegalStateException("App version is required");
            }
            return new UpdateConfig(this);
        }
        
        /**
         * 验证 URL 格式
         * @param url 待验证的 URL
         * @throws IllegalArgumentException 如果 URL 格式无效或协议不是 HTTP/HTTPS
         */
        private void validateUrl(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            
            try {
                URL parsedUrl = new URL(url);
                String protocol = parsedUrl.getProtocol().toLowerCase();
                if (!protocol.equals("http") && !protocol.equals("https")) {
                    throw new IllegalArgumentException(
                        "URL must use HTTP or HTTPS protocol, got: " + protocol);
                }
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL format: " + url, e);
            }
        }
    }
}
