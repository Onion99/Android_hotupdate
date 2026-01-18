/**
 * OpenSSL Wrapper for Android
 * 
 * 提供 OpenSSL 的基本加密功能，使用 Android 系统的 libcrypto.so
 * 这个包装器避免了直接依赖 OpenSSL 头文件
 */

#ifndef OPENSSL_WRAPPER_H
#define OPENSSL_WRAPPER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// SHA-1 相关
#define SHA_DIGEST_LENGTH 20

typedef struct {
    uint32_t h[5];
    uint32_t Nl, Nh;
    uint32_t data[16];
    unsigned int num;
} SHA_CTX;

int SHA1_Init(SHA_CTX *c);
int SHA1_Update(SHA_CTX *c, const void *data, size_t len);
int SHA1_Final(uint8_t *md, SHA_CTX *c);

// MD5 相关
#define MD5_DIGEST_LENGTH 16

typedef struct {
    uint32_t A, B, C, D;
    uint32_t Nl, Nh;
    uint32_t data[16];
    unsigned int num;
} MD5_CTX;

int MD5_Init(MD5_CTX *c);
int MD5_Update(MD5_CTX *c, const void *data, size_t len);
int MD5_Final(uint8_t *md, MD5_CTX *c);

// EVP 相关（对称加密）
typedef struct evp_cipher_ctx_st EVP_CIPHER_CTX;
typedef struct evp_cipher_st EVP_CIPHER;

EVP_CIPHER_CTX *EVP_CIPHER_CTX_new(void);
void EVP_CIPHER_CTX_free(EVP_CIPHER_CTX *ctx);

const EVP_CIPHER *EVP_des_cbc(void);

int EVP_DecryptInit_ex(EVP_CIPHER_CTX *ctx, const EVP_CIPHER *type,
                       void *impl, const uint8_t *key, const uint8_t *iv);
int EVP_DecryptUpdate(EVP_CIPHER_CTX *ctx, uint8_t *out, int *outl,
                      const uint8_t *in, int inl);
int EVP_DecryptFinal_ex(EVP_CIPHER_CTX *ctx, uint8_t *outm, int *outl);
int EVP_CIPHER_CTX_set_padding(EVP_CIPHER_CTX *ctx, int pad);

int EVP_CIPHER_block_size(const EVP_CIPHER *cipher);

#ifdef __cplusplus
}
#endif

#endif // OPENSSL_WRAPPER_H
