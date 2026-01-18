/**
 * JKS (Java KeyStore) Parser
 * 
 * Native implementation of JKS keystore parsing for Android.
 * Based on the pyjks Python library implementation.
 * 
 * Supports:
 * - JKS format (Java KeyStore)
 * - Private key extraction
 * - Certificate chain extraction
 * - Password-based encryption (PBE)
 */

#ifndef JKS_PARSER_H
#define JKS_PARSER_H

#include <stdint.h>
#include <stddef.h>
#include <string>
#include <vector>
#include <map>

namespace jks {

// JKS 文件魔数
const uint32_t JKS_MAGIC = 0xFEEDFEED;
const uint32_t JKS_VERSION_1 = 0x01;
const uint32_t JKS_VERSION_2 = 0x02;

// 条目类型
enum EntryType {
    ENTRY_TYPE_PRIVATE_KEY = 1,
    ENTRY_TYPE_TRUSTED_CERT = 2
};

/**
 * 证书数据
 */
struct Certificate {
    std::string type;           // 证书类型（通常是 "X.509"）
    std::vector<uint8_t> data;  // 证书数据（DER 编码）
};

/**
 * 私钥条目
 */
struct PrivateKeyEntry {
    std::string alias;                      // 别名
    uint64_t timestamp;                     // 时间戳
    std::vector<uint8_t> encrypted_key;     // 加密的私钥数据
    std::vector<uint8_t> decrypted_key;     // 解密后的私钥数据（PKCS#8 格式）
    std::vector<Certificate> cert_chain;    // 证书链
    bool is_decrypted;                      // 是否已解密
    
    PrivateKeyEntry() : timestamp(0), is_decrypted(false) {}
};

/**
 * 受信任证书条目
 */
struct TrustedCertEntry {
    std::string alias;          // 别名
    uint64_t timestamp;         // 时间戳
    Certificate cert;           // 证书
    
    TrustedCertEntry() : timestamp(0) {}
};

/**
 * JKS 解析器
 */
class JKSParser {
public:
    JKSParser();
    ~JKSParser();
    
    /**
     * 加载 JKS 文件
     * 
     * @param path JKS 文件路径
     * @param store_password 密钥库密码
     * @return 成功返回 true，失败返回 false
     */
    bool load(const char* path, const char* store_password);
    
    /**
     * 从内存加载 JKS 数据
     * 
     * @param data JKS 数据
     * @param size 数据大小
     * @param store_password 密钥库密码
     * @return 成功返回 true，失败返回 false
     */
    bool loads(const uint8_t* data, size_t size, const char* store_password);
    
    /**
     * 获取私钥条目
     * 
     * @param alias 别名
     * @return 私钥条目指针，不存在返回 nullptr
     */
    PrivateKeyEntry* getPrivateKey(const char* alias);
    
    /**
     * 解密私钥条目
     * 
     * @param entry 私钥条目
     * @param key_password 密钥密码
     * @return 成功返回 true，失败返回 false
     */
    bool decryptPrivateKey(PrivateKeyEntry* entry, const char* key_password);
    
    /**
     * 获取受信任证书条目
     * 
     * @param alias 别名
     * @return 证书条目指针，不存在返回 nullptr
     */
    TrustedCertEntry* getTrustedCert(const char* alias);
    
    /**
     * 获取所有私钥别名
     */
    std::vector<std::string> getPrivateKeyAliases() const;
    
    /**
     * 获取所有证书别名
     */
    std::vector<std::string> getTrustedCertAliases() const;
    
    /**
     * 获取错误信息
     */
    const char* getError() const { return error_msg.c_str(); }
    
private:
    std::map<std::string, PrivateKeyEntry> private_keys;
    std::map<std::string, TrustedCertEntry> trusted_certs;
    std::string error_msg;
    
    // 解析函数
    bool parseKeyStore(const uint8_t* data, size_t size, const char* store_password);
    bool verifySignature(const uint8_t* data, size_t size, const char* store_password);
    
    // 读取函数
    uint8_t readByte(const uint8_t** ptr);
    uint16_t readShort(const uint8_t** ptr);
    uint32_t readInt(const uint8_t** ptr);
    uint64_t readLong(const uint8_t** ptr);
    std::string readUTF(const uint8_t** ptr);
    std::vector<uint8_t> readBytes(const uint8_t** ptr, size_t length);
    
    // 解密函数
    bool decryptKey(const uint8_t* encrypted_data, size_t encrypted_size,
                   const char* password,
                   uint8_t** decrypted_data, size_t* decrypted_size);
    
    // 辅助函数
    void setError(const char* msg);
};

} // namespace jks

#endif // JKS_PARSER_H
