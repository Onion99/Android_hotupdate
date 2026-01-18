/**
 * JKS Parser Test Program
 * 
 * 独立测试程序，用于验证JKS解析和私钥解密
 */

#include <iostream>
#include <fstream>
#include <vector>
#include <cstring>
#include <iomanip>

// 简化的日志宏
#define LOGI(fmt, ...) printf("[INFO] " fmt "\n", ##__VA_ARGS__)
#define LOGE(fmt, ...) printf("[ERROR] " fmt "\n", ##__VA_ARGS__)

// 包含JKS解析器的头文件和实现
#include "src/main/cpp/include/jks_parser.h"
#include "src/main/cpp/include/openssl_wrapper.h"

// 声明外部函数
extern "C" {
    int SHA1_Init(SHA_CTX *c);
    int SHA1_Update(SHA_CTX *c, const void *data, size_t len);
    int SHA1_Final(uint8_t *md, SHA_CTX *c);
    
    int MD5_Init(MD5_CTX *c);
    int MD5_Update(MD5_CTX *c, const void *data, size_t len);
    int MD5_Final(uint8_t *md, MD5_CTX *c);
    
    void des_cbc_decrypt(const uint8_t *input, uint8_t *output, int length,
                        const uint8_t *key, const uint8_t *iv);
}

// 辅助函数：打印十六进制数据
void print_hex(const char* label, const uint8_t* data, size_t len) {
    std::cout << label << ": ";
    for (size_t i = 0; i < len && i < 32; i++) {
        std::cout << std::hex << std::setw(2) << std::setfill('0') 
                  << (int)data[i] << " ";
    }
    if (len > 32) {
        std::cout << "... (" << std::dec << len << " bytes total)";
    }
    std::cout << std::dec << std::endl;
}

int main(int argc, char* argv[]) {
    if (argc < 4) {
        std::cerr << "Usage: " << argv[0] << " <jks_file> <store_password> <key_alias> [key_password]" << std::endl;
        std::cerr << "Example: " << argv[0] << " smlieapp.jks 123123 smlieapp 123123" << std::endl;
        return 1;
    }
    
    const char* jks_file = argv[1];
    const char* store_password = argv[2];
    const char* key_alias = argv[3];
    const char* key_password = (argc > 4) ? argv[4] : argv[2];
    
    std::cout << "=== JKS Parser Test ===" << std::endl;
    std::cout << "JKS File: " << jks_file << std::endl;
    std::cout << "Store Password: " << store_password << std::endl;
    std::cout << "Key Alias: " << key_alias << std::endl;
    std::cout << "Key Password: " << key_password << std::endl;
    std::cout << std::endl;
    
    // 创建JKS解析器
    jks::JKSParser parser;
    
    // 加载JKS文件
    std::cout << "Step 1: Loading JKS file..." << std::endl;
    if (!parser.load(jks_file, store_password)) {
        std::cerr << "Failed to load JKS file: " << parser.getError() << std::endl;
        return 1;
    }
    std::cout << "✓ JKS file loaded successfully" << std::endl;
    std::cout << std::endl;
    
    // 列出所有私钥别名
    std::cout << "Step 2: Listing private key aliases..." << std::endl;
    std::vector<std::string> aliases = parser.getPrivateKeyAliases();
    std::cout << "Found " << aliases.size() << " private key(s):" << std::endl;
    for (const auto& alias : aliases) {
        std::cout << "  - " << alias << std::endl;
    }
    std::cout << std::endl;
    
    // 获取指定的私钥
    std::cout << "Step 3: Getting private key '" << key_alias << "'..." << std::endl;
    jks::PrivateKeyEntry* entry = parser.getPrivateKey(key_alias);
    if (!entry) {
        std::cerr << "Private key not found: " << key_alias << std::endl;
        return 1;
    }
    std::cout << "✓ Private key found" << std::endl;
    std::cout << "  Alias: " << entry->alias << std::endl;
    std::cout << "  Encrypted key size: " << entry->encrypted_key.size() << " bytes" << std::endl;
    std::cout << "  Certificate chain length: " << entry->cert_chain.size() << std::endl;
    print_hex("  Encrypted key (first 32 bytes)", entry->encrypted_key.data(), entry->encrypted_key.size());
    std::cout << std::endl;
    
    // 解密私钥
    std::cout << "Step 4: Decrypting private key..." << std::endl;
    if (!parser.decryptPrivateKey(entry, key_password)) {
        std::cerr << "Failed to decrypt private key: " << parser.getError() << std::endl;
        return 1;
    }
    std::cout << "✓ Private key decrypted successfully" << std::endl;
    std::cout << "  Decrypted key size: " << entry->decrypted_key.size() << " bytes" << std::endl;
    print_hex("  Decrypted key (first 32 bytes)", entry->decrypted_key.data(), entry->decrypted_key.size());
    std::cout << std::endl;
    
    // 验证PKCS#8格式
    std::cout << "Step 5: Validating PKCS#8 format..." << std::endl;
    if (entry->decrypted_key.size() < 2) {
        std::cerr << "✗ Decrypted key too small" << std::endl;
        return 1;
    }
    
    // PKCS#8 PrivateKeyInfo 应该以 SEQUENCE 标签开始 (0x30)
    if (entry->decrypted_key[0] == 0x30) {
        std::cout << "✓ Decrypted key starts with SEQUENCE tag (0x30)" << std::endl;
        
        // 检查长度编码
        if (entry->decrypted_key[1] == 0x82) {
            std::cout << "✓ Length is encoded in long form (0x82)" << std::endl;
            uint16_t length = (entry->decrypted_key[2] << 8) | entry->decrypted_key[3];
            std::cout << "  SEQUENCE length: " << length << " bytes" << std::endl;
        } else if (entry->decrypted_key[1] & 0x80) {
            std::cout << "✓ Length is encoded in long form" << std::endl;
        } else {
            std::cout << "✓ Length is encoded in short form" << std::endl;
        }
        
        std::cout << "✓ Decrypted key appears to be valid PKCS#8 format!" << std::endl;
    } else {
        std::cerr << "✗ Decrypted key does NOT start with SEQUENCE tag" << std::endl;
        std::cerr << "  Expected: 0x30, Got: 0x" << std::hex << (int)entry->decrypted_key[0] << std::dec << std::endl;
        std::cerr << "  This indicates the decryption algorithm is still incorrect" << std::endl;
        return 1;
    }
    std::cout << std::endl;
    
    // 获取证书链
    std::cout << "Step 6: Getting certificate chain..." << std::endl;
    for (size_t i = 0; i < entry->cert_chain.size(); i++) {
        const auto& cert = entry->cert_chain[i];
        std::cout << "  Certificate " << (i + 1) << ":" << std::endl;
        std::cout << "    Type: " << cert.type << std::endl;
        std::cout << "    Size: " << cert.data.size() << " bytes" << std::endl;
        print_hex("    Data (first 32 bytes)", cert.data.data(), cert.data.size());
    }
    std::cout << std::endl;
    
    std::cout << "=== Test Completed Successfully ===" << std::endl;
    return 0;
}
