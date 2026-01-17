package com.orange.patchgen.config;

/**
 * 引擎类型
 */
public enum EngineType {
    AUTO,       // 自动选择（优先 Native）
    JAVA,       // 强制使用 Java 引擎
    NATIVE      // 强制使用 Native 引擎
}
