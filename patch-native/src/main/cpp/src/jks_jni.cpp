/**
 * JKS Parser JNI Interface
 */

#include <jni.h>
#include <android/log.h>
#include "jks_parser.h"

#define LOG_TAG "JKS_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace jks;

// 全局 JKS 解析器实例（简化实现，生产环境应该使用更好的管理方式）
static JKSParser* g_parser = nullptr;

extern "C" {

/**
 * 加载 JKS 文件
 * 
 * @param path JKS 文件路径
 * @param storePassword 密钥库密码
 * @return 成功返回 true，失败返回 false
 */
JNIEXPORT jboolean JNICALL
Java_com_orange_patchgen_signer_JKSNative_loadKeyStore(
    JNIEnv* env,
    jclass clazz,
    jstring path,
    jstring storePassword) {
    
    const char* path_str = env->GetStringUTFChars(path, nullptr);
    const char* pwd_str = env->GetStringUTFChars(storePassword, nullptr);
    
    LOGI("Loading JKS: %s", path_str);
    
    // 创建新的解析器实例
    if (g_parser) {
        delete g_parser;
    }
    g_parser = new JKSParser();
    
    bool result = g_parser->load(path_str, pwd_str);
    
    if (!result) {
        LOGE("Failed to load JKS: %s", g_parser->getError());
    }
    
    env->ReleaseStringUTFChars(path, path_str);
    env->ReleaseStringUTFChars(storePassword, pwd_str);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

/**
 * 获取私钥数据（PKCS#8 格式）
 * 
 * @param alias 密钥别名
 * @param keyPassword 密钥密码
 * @return 私钥数据（byte[]），失败返回 null
 */
JNIEXPORT jbyteArray JNICALL
Java_com_orange_patchgen_signer_JKSNative_getPrivateKey(
    JNIEnv* env,
    jclass clazz,
    jstring alias,
    jstring keyPassword) {
    
    if (!g_parser) {
        LOGE("Parser not initialized");
        return nullptr;
    }
    
    const char* alias_str = env->GetStringUTFChars(alias, nullptr);
    const char* pwd_str = env->GetStringUTFChars(keyPassword, nullptr);
    
    LOGI("Getting private key: %s", alias_str);
    
    // 获取私钥条目
    PrivateKeyEntry* entry = g_parser->getPrivateKey(alias_str);
    if (!entry) {
        LOGE("Private key not found: %s", alias_str);
        env->ReleaseStringUTFChars(alias, alias_str);
        env->ReleaseStringUTFChars(keyPassword, pwd_str);
        return nullptr;
    }
    
    // 解密私钥
    if (!entry->is_decrypted) {
        if (!g_parser->decryptPrivateKey(entry, pwd_str)) {
            LOGE("Failed to decrypt private key: %s", g_parser->getError());
            env->ReleaseStringUTFChars(alias, alias_str);
            env->ReleaseStringUTFChars(keyPassword, pwd_str);
            return nullptr;
        }
    }
    
    // 返回解密后的私钥数据
    jbyteArray result = env->NewByteArray(entry->decrypted_key.size());
    env->SetByteArrayRegion(result, 0, entry->decrypted_key.size(),
                           (const jbyte*)entry->decrypted_key.data());
    
    env->ReleaseStringUTFChars(alias, alias_str);
    env->ReleaseStringUTFChars(keyPassword, pwd_str);
    
    LOGI("Private key retrieved successfully, size: %zu bytes", entry->decrypted_key.size());
    return result;
}

/**
 * 获取证书链
 * 
 * @param alias 密钥别名
 * @return 证书链数组（byte[][]），失败返回 null
 */
JNIEXPORT jobjectArray JNICALL
Java_com_orange_patchgen_signer_JKSNative_getCertificateChain(
    JNIEnv* env,
    jclass clazz,
    jstring alias) {
    
    if (!g_parser) {
        LOGE("Parser not initialized");
        return nullptr;
    }
    
    const char* alias_str = env->GetStringUTFChars(alias, nullptr);
    
    LOGI("Getting certificate chain: %s", alias_str);
    
    // 获取私钥条目
    PrivateKeyEntry* entry = g_parser->getPrivateKey(alias_str);
    if (!entry) {
        LOGE("Private key not found: %s", alias_str);
        env->ReleaseStringUTFChars(alias, alias_str);
        return nullptr;
    }
    
    // 创建证书数组
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(entry->cert_chain.size(), byteArrayClass, nullptr);
    
    for (size_t i = 0; i < entry->cert_chain.size(); i++) {
        const Certificate& cert = entry->cert_chain[i];
        jbyteArray certData = env->NewByteArray(cert.data.size());
        env->SetByteArrayRegion(certData, 0, cert.data.size(),
                               (const jbyte*)cert.data.data());
        env->SetObjectArrayElement(result, i, certData);
        env->DeleteLocalRef(certData);
    }
    
    env->ReleaseStringUTFChars(alias, alias_str);
    
    LOGI("Certificate chain retrieved successfully, count: %zu", entry->cert_chain.size());
    return result;
}

/**
 * 获取所有私钥别名
 * 
 * @return 别名数组（String[]）
 */
JNIEXPORT jobjectArray JNICALL
Java_com_orange_patchgen_signer_JKSNative_getPrivateKeyAliases(
    JNIEnv* env,
    jclass clazz) {
    
    if (!g_parser) {
        LOGE("Parser not initialized");
        return nullptr;
    }
    
    std::vector<std::string> aliases = g_parser->getPrivateKeyAliases();
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(aliases.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < aliases.size(); i++) {
        jstring alias = env->NewStringUTF(aliases[i].c_str());
        env->SetObjectArrayElement(result, i, alias);
        env->DeleteLocalRef(alias);
    }
    
    LOGI("Retrieved %zu private key aliases", aliases.size());
    return result;
}

/**
 * 释放资源
 */
JNIEXPORT void JNICALL
Java_com_orange_patchgen_signer_JKSNative_release(
    JNIEnv* env,
    jclass clazz) {
    
    if (g_parser) {
        delete g_parser;
        g_parser = nullptr;
        LOGI("JKS parser released");
    }
}

/**
 * 获取错误信息
 * 
 * @return 错误信息字符串
 */
JNIEXPORT jstring JNICALL
Java_com_orange_patchgen_signer_JKSNative_getError(
    JNIEnv* env,
    jclass clazz) {
    
    if (!g_parser) {
        return env->NewStringUTF("Parser not initialized");
    }
    
    return env->NewStringUTF(g_parser->getError());
}

} // extern "C"
