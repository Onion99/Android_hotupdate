/**
 * JKS Parser Implementation
 * 
 * Based on pyjks: https://github.com/kurtbrose/pyjks
 * JKS format specification: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/sun/security/provider/JavaKeyStore.java
 */

#include "jks_parser.h"
#include "openssl_wrapper.h"
#include <fstream>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "JKSParser"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace jks {

JKSParser::JKSParser() {
}

JKSParser::~JKSParser() {
}

bool JKSParser::load(const char* path, const char* store_password) {
    LOGI("Loading JKS file: %s", path);
    
    // 读取文件
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        setError("Failed to open JKS file");
        return false;
    }
    
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    
    std::vector<uint8_t> buffer(size);
    if (!file.read((char*)buffer.data(), size)) {
        setError("Failed to read JKS file");
        return false;
    }
    
    return loads(buffer.data(), buffer.size(), store_password);
}

bool JKSParser::loads(const uint8_t* data, size_t size, const char* store_password) {
    LOGI("Parsing JKS data, size: %zu bytes", size);
    
    if (!data || size < 20) {
        setError("Invalid JKS data");
        return false;
    }
    
    // 解析密钥库
    if (!parseKeyStore(data, size, store_password)) {
        return false;
    }
    
    // 验证签名
    if (!verifySignature(data, size, store_password)) {
        setError("JKS signature verification failed");
        return false;
    }
    
    LOGI("JKS parsed successfully, %zu private keys, %zu certs",
         private_keys.size(), trusted_certs.size());
    
    return true;
}

bool JKSParser::parseKeyStore(const uint8_t* data, size_t size, const char* store_password) {
    const uint8_t* ptr = data;
    const uint8_t* end = data + size;
    
    // 读取魔数
    uint32_t magic = readInt(&ptr);
    if (magic != JKS_MAGIC) {
        setError("Invalid JKS magic number");
        return false;
    }
    
    // 读取版本
    uint32_t version = readInt(&ptr);
    if (version != JKS_VERSION_1 && version != JKS_VERSION_2) {
        setError("Unsupported JKS version");
        return false;
    }
    
    LOGI("JKS version: %u", version);
    
    // 读取条目数量
    uint32_t entry_count = readInt(&ptr);
    LOGI("Entry count: %u", entry_count);
    
    // 读取每个条目
    for (uint32_t i = 0; i < entry_count; i++) {
        if (ptr >= end - 20) {
            setError("Unexpected end of data");
            return false;
        }
        
        // 读取条目类型
        uint32_t entry_type = readInt(&ptr);
        
        // 读取别名
        std::string alias = readUTF(&ptr);
        
        // 读取时间戳
        uint64_t timestamp = readLong(&ptr);
        
        LOGI("Entry %u: type=%u, alias=%s", i, entry_type, alias.c_str());
        
        if (entry_type == ENTRY_TYPE_PRIVATE_KEY) {
            // 私钥条目
            PrivateKeyEntry entry;
            entry.alias = alias;
            entry.timestamp = timestamp;
            
            // 读取加密的私钥数据
            uint32_t key_size = readInt(&ptr);
            entry.encrypted_key = readBytes(&ptr, key_size);
            
            // 读取证书链
            uint32_t cert_count = readInt(&ptr);
            for (uint32_t j = 0; j < cert_count; j++) {
                Certificate cert;
                cert.type = readUTF(&ptr);
                uint32_t cert_size = readInt(&ptr);
                cert.data = readBytes(&ptr, cert_size);
                entry.cert_chain.push_back(cert);
            }
            
            private_keys[alias] = entry;
            
        } else if (entry_type == ENTRY_TYPE_TRUSTED_CERT) {
            // 受信任证书条目
            TrustedCertEntry entry;
            entry.alias = alias;
            entry.timestamp = timestamp;
            
            // 读取证书
            entry.cert.type = readUTF(&ptr);
            uint32_t cert_size = readInt(&ptr);
            entry.cert.data = readBytes(&ptr, cert_size);
            
            trusted_certs[alias] = entry;
            
        } else {
            setError("Unknown entry type");
            return false;
        }
    }
    
    return true;
}

bool JKSParser::verifySignature(const uint8_t* data, size_t size, const char* store_password) {
    // JKS 签名验证
    // 签名是最后 20 字节（SHA-1 哈希）
    if (size < 20) {
        return false;
    }
    
    const uint8_t* signature = data + size - 20;
    size_t data_size = size - 20;
    
    // 计算哈希
    // JKS 使用: SHA1(password_bytes + "Mighty Aphrodite" + keystore_data)
    SHA_CTX ctx;
    SHA1_Init(&ctx);
    
    // 添加密码（UTF-16BE 编码）
    size_t pwd_len = strlen(store_password);
    for (size_t i = 0; i < pwd_len; i++) {
        uint8_t utf16[2] = {0, (uint8_t)store_password[i]};
        SHA1_Update(&ctx, utf16, 2);
    }
    
    // 添加魔法字符串 "Mighty Aphrodite"
    const char* magic_str = "Mighty Aphrodite";
    SHA1_Update(&ctx, magic_str, strlen(magic_str));
    
    // 添加密钥库数据
    SHA1_Update(&ctx, data, data_size);
    
    uint8_t computed_hash[SHA_DIGEST_LENGTH];
    SHA1_Final(computed_hash, &ctx);
    
    // 比较签名
    if (memcmp(computed_hash, signature, SHA_DIGEST_LENGTH) != 0) {
        LOGE("Signature mismatch");
        return false;
    }
    
    LOGI("Signature verified successfully");
    return true;
}

PrivateKeyEntry* JKSParser::getPrivateKey(const char* alias) {
    auto it = private_keys.find(alias);
    if (it != private_keys.end()) {
        return &it->second;
    }
    return nullptr;
}

bool JKSParser::decryptPrivateKey(PrivateKeyEntry* entry, const char* key_password) {
    if (!entry) {
        setError("Invalid entry");
        return false;
    }
    
    if (entry->is_decrypted) {
        return true;  // 已经解密
    }
    
    LOGI("Decrypting private key: %s", entry->alias.c_str());
    
    // JKS 私钥格式是一个 ASN.1 结构：
    // SEQUENCE {
    //     algorithmIdentifier AlgorithmIdentifier,
    //     encryptedData OCTET STRING
    // }
    // 我们需要跳过算法标识符，直接提取加密数据
    
    const uint8_t* data = entry->encrypted_key.data();
    size_t data_size = entry->encrypted_key.size();
    
    if (data_size < 20) {
        setError("Encrypted key data too small");
        return false;
    }
    
    // 跳过外层 SEQUENCE 标签和长度
    size_t offset = 0;
    if (data[offset] != 0x30) {  // SEQUENCE tag
        setError("Invalid ASN.1 structure: expected SEQUENCE");
        return false;
    }
    offset++;
    
    // 读取长度（可能是短格式或长格式）
    if (data[offset] & 0x80) {
        // 长格式：第一个字节的低7位表示长度字节数
        int len_bytes = data[offset] & 0x7F;
        offset += 1 + len_bytes;
    } else {
        // 短格式
        offset++;
    }
    
    // 跳过算法标识符 SEQUENCE
    if (data[offset] != 0x30) {
        setError("Invalid ASN.1 structure: expected algorithm SEQUENCE");
        return false;
    }
    offset++;
    
    // 读取算法标识符长度并跳过
    int alg_len = data[offset];
    if (alg_len & 0x80) {
        int len_bytes = alg_len & 0x7F;
        alg_len = 0;
        for (int i = 0; i < len_bytes; i++) {
            alg_len = (alg_len << 8) | data[offset + 1 + i];
        }
        offset += 1 + len_bytes + alg_len;
    } else {
        offset += 1 + alg_len;
    }
    
    // 现在应该是 OCTET STRING 标签
    if (data[offset] != 0x04) {  // OCTET STRING tag
        setError("Invalid ASN.1 structure: expected OCTET STRING");
        return false;
    }
    offset++;
    
    // 读取加密数据长度
    size_t encrypted_len = 0;
    if (data[offset] & 0x80) {
        int len_bytes = data[offset] & 0x7F;
        offset++;
        for (int i = 0; i < len_bytes; i++) {
            encrypted_len = (encrypted_len << 8) | data[offset++];
        }
    } else {
        encrypted_len = data[offset++];
    }
    
    // 现在 data + offset 指向真正的加密数据
    const uint8_t* encrypted_data = data + offset;
    
    LOGI("ASN.1 structure parsed: encrypted data starts at offset %zu, length %zu", offset, encrypted_len);
    
    uint8_t* decrypted_data = nullptr;
    size_t decrypted_size = 0;
    
    if (!decryptKey(encrypted_data, encrypted_len,
                   key_password, &decrypted_data, &decrypted_size)) {
        return false;
    }
    
    LOGI("Private key decrypted successfully, size: %zu bytes", decrypted_size);
    
    // 打印更多字节以便调试
    LOGI("Decrypted data (first 64 bytes):");
    for (size_t i = 0; i < std::min((size_t)64, decrypted_size); i += 16) {
        char hex_str[128];
        int offset_hex = 0;
        for (size_t j = i; j < std::min(i + 16, decrypted_size); j++) {
            offset_hex += sprintf(hex_str + offset_hex, "%02X ", decrypted_data[j]);
        }
        LOGI("  %s", hex_str);
    }
    
    // 根据 pyjks 和 OpenJDK 源码，JKS 解密后的数据格式是：
    // 1. 前20字节：SHA-1 哈希（用于完整性检查）
    // 2. 剩余部分：PKCS#8 PrivateKeyInfo DER 编码
    //
    // 让我们跳过前20字节，看看是否能找到 PKCS#8 结构
    
    if (decrypted_size > 20) {
        // 检查第20字节开始是否是 SEQUENCE 标签
        if (decrypted_data[20] == 0x30) {
            LOGI("Found PKCS#8 structure after 20-byte hash");
            size_t pkcs8_size = decrypted_size - 20;
            entry->decrypted_key.assign(decrypted_data + 20, 
                                       decrypted_data + 20 + pkcs8_size);
            entry->is_decrypted = true;
            delete[] decrypted_data;
            
            // 打印 PKCS#8 数据的前16字节
            LOGI("PKCS#8 data first 16 bytes:");
            char hex_str[128];
            int offset_hex = 0;
            for (size_t i = 0; i < std::min((size_t)16, pkcs8_size); i++) {
                offset_hex += sprintf(hex_str + offset_hex, "%02X ", decrypted_data[20 + i]);
            }
            LOGI("  %s", hex_str);
            
            return true;
        }
    }
    
    // 查找 SEQUENCE 标签 (0x30) 在数据中的位置
    size_t seq_offset = 0;
    bool found_sequence = false;
    for (size_t i = 0; i < decrypted_size - 10; i++) {
        if (decrypted_data[i] == 0x30 && (decrypted_data[i+1] == 0x82 || decrypted_data[i+1] == 0x81)) {
            // 找到可能的 SEQUENCE 标签（长格式长度）
            seq_offset = i;
            found_sequence = true;
            LOGI("Found SEQUENCE tag at offset %zu", i);
            break;
        }
    }
    
    if (found_sequence && seq_offset > 0) {
        // 数据前面有额外的字节，跳过它们
        LOGI("Skipping %zu bytes before SEQUENCE", seq_offset);
        size_t actual_key_size = decrypted_size - seq_offset;
        entry->decrypted_key.assign(decrypted_data + seq_offset, 
                                   decrypted_data + seq_offset + actual_key_size);
        entry->is_decrypted = true;
        delete[] decrypted_data;
        return true;
    }
    
    // 检查解密后的数据是否已经是 PKCS#8 格式
    if (decrypted_size > 0 && decrypted_data[0] == 0x30) {
        // 已经是 PKCS#8 格式（以 SEQUENCE 标签开始）
        LOGI("Decrypted key is already in PKCS#8 format");
        entry->decrypted_key.assign(decrypted_data, decrypted_data + decrypted_size);
    } else {
        // 不是 PKCS#8 格式，需要包装
        // 根据 OpenJDK 源码，JKS 解密后的数据应该是 PKCS#8 PrivateKeyInfo
        // 但实际上可能是 PKCS#1 RSAPrivateKey（以 0x30 开头）或其他格式
        // 
        // 如果第一个字节不是 0x30，可能是因为：
        // 1. 数据被额外包装了
        // 2. 数据格式不是标准的 PKCS#8
        // 
        // 让我们尝试查找 SEQUENCE 标签
        LOGI("Wrapping raw private key into PKCS#8 format");
        
        // 构建 PKCS#8 PrivateKeyInfo 结构：
        // SEQUENCE {
        //     version         INTEGER (0),
        //     algorithm       AlgorithmIdentifier (RSA),
        //     privateKey      OCTET STRING
        // }
        
        // RSA 算法标识符 (OID 1.2.840.113549.1.1.1)
        const uint8_t rsa_algorithm[] = {
            0x30, 0x0D,  // SEQUENCE, length 13
            0x06, 0x09,  // OID, length 9
            0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01,  // RSA OID
            0x05, 0x00   // NULL
        };
        
        // 计算 PKCS#8 结构的总大小
        // version (INTEGER 0): 3 bytes
        // algorithm: sizeof(rsa_algorithm)
        // privateKey (OCTET STRING): 1 + length_bytes + decrypted_size
        
        size_t version_size = 3;  // 0x02 0x01 0x00
        size_t algorithm_size = sizeof(rsa_algorithm);
        
        // 计算 OCTET STRING 的长度编码
        size_t octet_string_len_bytes = 1;
        if (decrypted_size > 127) {
            if (decrypted_size <= 0xFF) {
                octet_string_len_bytes = 2;  // 0x81 len
            } else {
                octet_string_len_bytes = 3;  // 0x82 len_hi len_lo
            }
        }
        size_t privatekey_size = 1 + octet_string_len_bytes + decrypted_size;
        
        size_t content_size = version_size + algorithm_size + privatekey_size;
        
        // 计算外层 SEQUENCE 的长度编码
        size_t seq_len_bytes = 1;
        if (content_size > 127) {
            if (content_size <= 0xFF) {
                seq_len_bytes = 2;
            } else {
                seq_len_bytes = 3;
            }
        }
        
        size_t total_size = 1 + seq_len_bytes + content_size;
        
        // 分配缓冲区
        std::vector<uint8_t> pkcs8_data;
        pkcs8_data.reserve(total_size);
        
        // 写入外层 SEQUENCE
        pkcs8_data.push_back(0x30);  // SEQUENCE tag
        if (seq_len_bytes == 1) {
            pkcs8_data.push_back(content_size);
        } else if (seq_len_bytes == 2) {
            pkcs8_data.push_back(0x81);
            pkcs8_data.push_back(content_size);
        } else {
            pkcs8_data.push_back(0x82);
            pkcs8_data.push_back((content_size >> 8) & 0xFF);
            pkcs8_data.push_back(content_size & 0xFF);
        }
        
        // 写入 version (INTEGER 0)
        pkcs8_data.push_back(0x02);  // INTEGER tag
        pkcs8_data.push_back(0x01);  // length 1
        pkcs8_data.push_back(0x00);  // value 0
        
        // 写入 algorithm (RSA)
        pkcs8_data.insert(pkcs8_data.end(), rsa_algorithm, rsa_algorithm + sizeof(rsa_algorithm));
        
        // 写入 privateKey (OCTET STRING)
        pkcs8_data.push_back(0x04);  // OCTET STRING tag
        if (octet_string_len_bytes == 1) {
            pkcs8_data.push_back(decrypted_size);
        } else if (octet_string_len_bytes == 2) {
            pkcs8_data.push_back(0x81);
            pkcs8_data.push_back(decrypted_size);
        } else {
            pkcs8_data.push_back(0x82);
            pkcs8_data.push_back((decrypted_size >> 8) & 0xFF);
            pkcs8_data.push_back(decrypted_size & 0xFF);
        }
        pkcs8_data.insert(pkcs8_data.end(), decrypted_data, decrypted_data + decrypted_size);
        
        entry->decrypted_key = pkcs8_data;
        
        LOGI("PKCS#8 wrapping complete, total size: %zu bytes", pkcs8_data.size());
        LOGI("PKCS#8 first 16 bytes: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
             pkcs8_data[0], pkcs8_data[1], pkcs8_data[2], pkcs8_data[3],
             pkcs8_data[4], pkcs8_data[5], pkcs8_data[6], pkcs8_data[7],
             pkcs8_data[8], pkcs8_data[9], pkcs8_data[10], pkcs8_data[11],
             pkcs8_data[12], pkcs8_data[13], pkcs8_data[14], pkcs8_data[15]);
    }
    
    entry->is_decrypted = true;
    delete[] decrypted_data;
    
    return true;
}

bool JKSParser::decryptKey(const uint8_t* encrypted_data, size_t encrypted_size,
                           const char* password,
                           uint8_t** decrypted_data, size_t* decrypted_size) {
    // JKS 使用自定义的 PBE 算法
    // 算法来自 sun.security.provider.KeyProtector
    // 
    // 密钥派生算法（参考 OpenJDK 源码）:
    // 1. 将密码转换为 UTF-16BE (char[] -> byte[])
    // 2. xorKey = MD5(password)  // 第一次
    // 3. 迭代19次: xorKey = MD5(xorKey)  // 后续迭代
    // 4. 使用 xorKey 的前8字节作为 DES key
    // 5. 使用 xorKey 的后8字节作为 IV
    
    if (encrypted_size < 8) {
        setError("Encrypted data too small");
        return false;
    }
    
    LOGI("Encrypted data size: %zu, first bytes: %02X %02X %02X %02X...", 
         encrypted_size, encrypted_data[0], encrypted_data[1], encrypted_data[2], encrypted_data[3]);
    
    // 打印更多字节以便调试
    LOGI("Encrypted data (first 32 bytes):");
    for (size_t i = 0; i < std::min((size_t)32, encrypted_size); i += 16) {
        char hex_str[128];
        int offset = 0;
        for (size_t j = i; j < std::min(i + 16, encrypted_size); j++) {
            offset += sprintf(hex_str + offset, "%02X ", encrypted_data[j]);
        }
        LOGI("  %s", hex_str);
    }
    
    // 将密码转换为 UTF-16BE (char[] -> byte[])
    // Java: char[] password -> byte[] (每个char占2字节，big-endian)
    size_t pwd_len = strlen(password);
    std::vector<uint8_t> pwd_bytes;
    for (size_t i = 0; i < pwd_len; i++) {
        pwd_bytes.push_back(0);  // 高字节（ASCII字符的高字节为0）
        pwd_bytes.push_back((uint8_t)password[i]); // 低字节
    }
    
    // 密钥派生: xorKey = MD5(password)，然后迭代19次 MD5(xorKey)
    const int iterations = 20;
    uint8_t xorKey[MD5_DIGEST_LENGTH];
    
    // 第一次迭代: MD5(password)
    MD5_CTX md5_ctx;
    MD5_Init(&md5_ctx);
    MD5_Update(&md5_ctx, pwd_bytes.data(), pwd_bytes.size());
    MD5_Final(xorKey, &md5_ctx);
    
    // 剩余19次迭代: MD5(xorKey)
    for (int i = 1; i < iterations; i++) {
        MD5_Init(&md5_ctx);
        MD5_Update(&md5_ctx, xorKey, MD5_DIGEST_LENGTH);
        MD5_Final(xorKey, &md5_ctx);
    }
    
    // 使用前8字节作为DES密钥，后8字节作为IV
    uint8_t key[8];
    uint8_t iv[8];
    memcpy(key, xorKey, 8);
    memcpy(iv, xorKey + 8, 8);
    
    LOGI("Key: %02X %02X %02X %02X %02X %02X %02X %02X",
         key[0], key[1], key[2], key[3], key[4], key[5], key[6], key[7]);
    LOGI("IV: %02X %02X %02X %02X %02X %02X %02X %02X",
         iv[0], iv[1], iv[2], iv[3], iv[4], iv[5], iv[6], iv[7]);
    
    // 使用 DES/CBC 解密
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        setError("Failed to create cipher context");
        return false;
    }
    
    // 初始化解密 - 禁用自动padding，因为JKS使用自定义padding
    if (EVP_DecryptInit_ex(ctx, EVP_des_cbc(), nullptr, key, iv) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        setError("Failed to initialize decryption");
        return false;
    }
    
    // 禁用自动padding
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    
    // 分配输出缓冲区
    *decrypted_data = new uint8_t[encrypted_size];
    int out_len = 0;
    
    // 解密
    if (EVP_DecryptUpdate(ctx, *decrypted_data, &out_len, encrypted_data, encrypted_size) != 1) {
        EVP_CIPHER_CTX_free(ctx);
        delete[] *decrypted_data;
        setError("Decryption failed");
        return false;
    }
    
    *decrypted_size = out_len;
    
    // 手动移除 PKCS#5 padding
    if (*decrypted_size > 0) {
        uint8_t padding_len = (*decrypted_data)[*decrypted_size - 1];
        if (padding_len > 0 && padding_len <= 8 && padding_len <= *decrypted_size) {
            // 验证padding是否正确
            bool valid_padding = true;
            for (int i = 0; i < padding_len; i++) {
                if ((*decrypted_data)[*decrypted_size - 1 - i] != padding_len) {
                    valid_padding = false;
                    break;
                }
            }
            if (valid_padding) {
                *decrypted_size -= padding_len;
                LOGI("Removed PKCS#5 padding: %d bytes", padding_len);
            }
        }
    }
    
    EVP_CIPHER_CTX_free(ctx);
    
    LOGI("Decrypted data first 16 bytes: %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X",
         (*decrypted_data)[0], (*decrypted_data)[1], (*decrypted_data)[2], (*decrypted_data)[3],
         (*decrypted_data)[4], (*decrypted_data)[5], (*decrypted_data)[6], (*decrypted_data)[7],
         (*decrypted_data)[8], (*decrypted_data)[9], (*decrypted_data)[10], (*decrypted_data)[11],
         (*decrypted_data)[12], (*decrypted_data)[13], (*decrypted_data)[14], (*decrypted_data)[15]);
    
    return true;
}

TrustedCertEntry* JKSParser::getTrustedCert(const char* alias) {
    auto it = trusted_certs.find(alias);
    if (it != trusted_certs.end()) {
        return &it->second;
    }
    return nullptr;
}

std::vector<std::string> JKSParser::getPrivateKeyAliases() const {
    std::vector<std::string> aliases;
    for (const auto& pair : private_keys) {
        aliases.push_back(pair.first);
    }
    return aliases;
}

std::vector<std::string> JKSParser::getTrustedCertAliases() const {
    std::vector<std::string> aliases;
    for (const auto& pair : trusted_certs) {
        aliases.push_back(pair.first);
    }
    return aliases;
}

// 读取函数实现

uint8_t JKSParser::readByte(const uint8_t** ptr) {
    uint8_t value = **ptr;
    (*ptr)++;
    return value;
}

uint16_t JKSParser::readShort(const uint8_t** ptr) {
    uint16_t value = ((*ptr)[0] << 8) | (*ptr)[1];
    (*ptr) += 2;
    return value;
}

uint32_t JKSParser::readInt(const uint8_t** ptr) {
    uint32_t value = ((*ptr)[0] << 24) | ((*ptr)[1] << 16) | 
                     ((*ptr)[2] << 8) | (*ptr)[3];
    (*ptr) += 4;
    return value;
}

uint64_t JKSParser::readLong(const uint8_t** ptr) {
    uint64_t value = ((uint64_t)(*ptr)[0] << 56) | ((uint64_t)(*ptr)[1] << 48) |
                     ((uint64_t)(*ptr)[2] << 40) | ((uint64_t)(*ptr)[3] << 32) |
                     ((uint64_t)(*ptr)[4] << 24) | ((uint64_t)(*ptr)[5] << 16) |
                     ((uint64_t)(*ptr)[6] << 8) | (uint64_t)(*ptr)[7];
    (*ptr) += 8;
    return value;
}

std::string JKSParser::readUTF(const uint8_t** ptr) {
    uint16_t length = readShort(ptr);
    std::string str((const char*)*ptr, length);
    (*ptr) += length;
    return str;
}

std::vector<uint8_t> JKSParser::readBytes(const uint8_t** ptr, size_t length) {
    std::vector<uint8_t> bytes(*ptr, *ptr + length);
    (*ptr) += length;
    return bytes;
}

void JKSParser::setError(const char* msg) {
    error_msg = msg;
    LOGE("%s", msg);
}

} // namespace jks
