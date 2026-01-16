package com.orange.update;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 服务器接口类，封装与服务器的 HTTP 通信。
 * 负责检查更新和解析服务器响应。
 */
public class ServerApi {
    
    private static final String TAG = "ServerApi";
    
    // 服务器响应 JSON 字段
    private static final String KEY_CODE = "code";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_DATA = "data";
    private static final String KEY_HAS_UPDATE = "hasUpdate";
    private static final String KEY_PATCH_INFO = "patchInfo";
    
    // 成功响应码
    private static final int RESPONSE_CODE_SUCCESS = 0;
    
    private final UpdateConfig config;
    
    /**
     * 构造函数
     * @param config SDK 配置
     */
    public ServerApi(UpdateConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        this.config = config;
    }
    
    /**
     * 检查更新接口
     * @param appKey 应用标识
     * @param appVersion 应用版本
     * @param patchVersion 当前补丁版本，可为 null
     * @return 如果有更新返回 PatchInfo，否则返回 null
     * @throws UpdateException 如果请求失败或响应格式错误
     */
    public PatchInfo checkUpdate(String appKey, String appVersion, String patchVersion) 
            throws UpdateException {
        HttpURLConnection connection = null;
        
        try {
            // 构建请求 URL
            String requestUrl = buildCheckUpdateUrl();
            URL url = new URL(requestUrl);
            
            connection = (HttpURLConnection) url.openConnection();
            configureConnection(connection);
            
            // 构建请求体
            String requestBody = buildRequestBody(appKey, appVersion, patchVersion);
            
            // 发送请求
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 获取响应
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseBody = readResponse(connection.getInputStream());
                return parseCheckUpdateResponse(responseBody);
            } else {
                String errorBody = readResponse(connection.getErrorStream());
                Log.e(TAG, "Server returned error: " + responseCode + ", body: " + errorBody);
                throw new UpdateException(UpdateErrorCode.ERROR_SERVER_ERROR,
                        "Server returned error code: " + responseCode);
            }
            
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Request timeout", e);
            throw new UpdateException(UpdateErrorCode.ERROR_TIMEOUT, "Request timeout", e);
        } catch (IOException e) {
            Log.e(TAG, "Network error", e);
            throw new UpdateException(UpdateErrorCode.ERROR_NETWORK_UNAVAILABLE, 
                    "Network error: " + e.getMessage(), e);
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error", e);
            throw new UpdateException(UpdateErrorCode.ERROR_INVALID_RESPONSE,
                    "Invalid response format: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 构建检查更新的 URL
     */
    private String buildCheckUpdateUrl() {
        String baseUrl = config.getServerUrl();
        // 确保 URL 以 / 结尾
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "api/v1/check-update";
    }
    
    /**
     * 配置 HTTP 连接
     */
    private void configureConnection(HttpURLConnection connection) throws IOException {
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(config.getConnectTimeout());
        connection.setReadTimeout(config.getReadTimeout());
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
    }
    
    /**
     * 构建请求体 JSON
     */
    private String buildRequestBody(String appKey, String appVersion, String patchVersion) 
            throws JSONException {
        JSONObject json = new JSONObject();
        json.put("appKey", appKey);
        json.put("appVersion", appVersion);
        if (patchVersion != null && !patchVersion.isEmpty()) {
            json.put("patchVersion", patchVersion);
        }
        return json.toString();
    }
    
    /**
     * 读取响应内容
     */
    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
    
    /**
     * 解析检查更新响应
     * @param responseBody 响应体 JSON 字符串
     * @return 如果有更新返回 PatchInfo，否则返回 null
     * @throws JSONException 如果 JSON 格式错误
     * @throws UpdateException 如果服务器返回错误
     */
    private PatchInfo parseCheckUpdateResponse(String responseBody) 
            throws JSONException, UpdateException {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new UpdateException(UpdateErrorCode.ERROR_INVALID_RESPONSE, 
                    "Empty response from server");
        }
        
        JSONObject response = new JSONObject(responseBody);
        
        // 检查响应码
        int code = response.optInt(KEY_CODE, -1);
        if (code != RESPONSE_CODE_SUCCESS) {
            String message = response.optString(KEY_MESSAGE, "Unknown error");
            throw new UpdateException(UpdateErrorCode.ERROR_SERVER_ERROR,
                    "Server error: " + message);
        }
        
        // 解析 data 字段
        if (!response.has(KEY_DATA) || response.isNull(KEY_DATA)) {
            return null;
        }
        
        JSONObject data = response.getJSONObject(KEY_DATA);
        
        // 检查是否有更新
        boolean hasUpdate = data.optBoolean(KEY_HAS_UPDATE, false);
        if (!hasUpdate) {
            return null;
        }
        
        // 解析补丁信息
        if (!data.has(KEY_PATCH_INFO) || data.isNull(KEY_PATCH_INFO)) {
            return null;
        }
        
        JSONObject patchInfoJson = data.getJSONObject(KEY_PATCH_INFO);
        return PatchInfo.fromJson(patchInfoJson.toString());
    }
    
    /**
     * 更新异常类
     */
    public static class UpdateException extends Exception {
        private final int errorCode;
        
        public UpdateException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public UpdateException(int errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}
