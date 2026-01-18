#include <stdio.h>
#include <string.h>
#include <stdint.h>

// 从 crypto_impl.cpp 复制 MD5 实现
#include "../src/main/cpp/include/openssl_wrapper.h"

int main() {
    // 测试密码 "123123"
    const char* password = "123123";
    
    // 转换为 UTF-16BE
    uint8_t pwd_bytes[12];  // 6 个字符 * 2 字节
    for (int i = 0; i < 6; i++) {
        pwd_bytes[i * 2] = 0;  // 高字节
        pwd_bytes[i * 2 + 1] = password[i];  // 低字节
    }
    
    printf("Password UTF-16BE: ");
    for (int i = 0; i < 12; i++) {
        printf("%02X ", pwd_bytes[i]);
    }
    printf("\n");
    
    // 第一次 MD5
    MD5_CTX ctx;
    uint8_t hash[16];
    
    MD5_Init(&ctx);
    MD5_Update(&ctx, pwd_bytes, 12);
    MD5_Final(hash, &ctx);
    
    printf("MD5(password): ");
    for (int i = 0; i < 16; i++) {
        printf("%02X ", hash[i]);
    }
    printf("\n");
    
    // 迭代 19 次
    for (int i = 0; i < 19; i++) {
        MD5_Init(&ctx);
        MD5_Update(&ctx, hash, 16);
        MD5_Final(hash, &ctx);
    }
    
    printf("After 20 iterations: ");
    for (int i = 0; i < 16; i++) {
        printf("%02X ", hash[i]);
    }
    printf("\n");
    
    printf("Key (first 8 bytes): ");
    for (int i = 0; i < 8; i++) {
        printf("%02X ", hash[i]);
    }
    printf("\n");
    
    printf("IV (last 8 bytes): ");
    for (int i = 8; i < 16; i++) {
        printf("%02X ", hash[i]);
    }
    printf("\n");
    
    return 0;
}
