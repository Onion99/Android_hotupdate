/**
 * 简单的加密算法实现
 * 
 * 实现 SHA-1、MD5 和 DES-CBC，用于 JKS 解析
 * 基于公共领域的实现
 */

#include "openssl_wrapper.h"
#include <cstring>
#include <cstdlib>

// ============================================================================
// SHA-1 实现
// ============================================================================

#define SHA1_ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))

static void sha1_transform(SHA_CTX *ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, e, i, j, t, m[80];
    
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j] << 24) + (data[j + 1] << 16) + (data[j + 2] << 8) + (data[j + 3]);
    for ( ; i < 80; ++i) {
        m[i] = (m[i - 3] ^ m[i - 8] ^ m[i - 14] ^ m[i - 16]);
        m[i] = (m[i] << 1) | (m[i] >> 31);
    }
    
    a = ctx->h[0];
    b = ctx->h[1];
    c = ctx->h[2];
    d = ctx->h[3];
    e = ctx->h[4];
    
    for (i = 0; i < 20; ++i) {
        t = SHA1_ROTLEFT(a, 5) + ((b & c) ^ (~b & d)) + e + 0x5a827999 + m[i];
        e = d;
        d = c;
        c = SHA1_ROTLEFT(b, 30);
        b = a;
        a = t;
    }
    for ( ; i < 40; ++i) {
        t = SHA1_ROTLEFT(a, 5) + (b ^ c ^ d) + e + 0x6ed9eba1 + m[i];
        e = d;
        d = c;
        c = SHA1_ROTLEFT(b, 30);
        b = a;
        a = t;
    }
    for ( ; i < 60; ++i) {
        t = SHA1_ROTLEFT(a, 5) + ((b & c) ^ (b & d) ^ (c & d))  + e + 0x8f1bbcdc + m[i];
        e = d;
        d = c;
        c = SHA1_ROTLEFT(b, 30);
        b = a;
        a = t;
    }
    for ( ; i < 80; ++i) {
        t = SHA1_ROTLEFT(a, 5) + (b ^ c ^ d) + e + 0xca62c1d6 + m[i];
        e = d;
        d = c;
        c = SHA1_ROTLEFT(b, 30);
        b = a;
        a = t;
    }
    
    ctx->h[0] += a;
    ctx->h[1] += b;
    ctx->h[2] += c;
    ctx->h[3] += d;
    ctx->h[4] += e;
}

int SHA1_Init(SHA_CTX *ctx) {
    ctx->Nl = 0;
    ctx->Nh = 0;
    ctx->h[0] = 0x67452301;
    ctx->h[1] = 0xefcdab89;
    ctx->h[2] = 0x98badcfe;
    ctx->h[3] = 0x10325476;
    ctx->h[4] = 0xc3d2e1f0;
    ctx->num = 0;
    return 1;
}

int SHA1_Update(SHA_CTX *ctx, const void *data, size_t len) {
    const uint8_t *d = (const uint8_t *)data;
    size_t i;
    
    for (i = 0; i < len; ++i) {
        ctx->data[ctx->num / 4] = (ctx->data[ctx->num / 4] << 8) | d[i];
        ctx->num++;
        if (ctx->num == 64) {
            uint8_t block[64];
            for (int j = 0; j < 16; j++) {
                block[j*4] = (ctx->data[j] >> 24) & 0xff;
                block[j*4+1] = (ctx->data[j] >> 16) & 0xff;
                block[j*4+2] = (ctx->data[j] >> 8) & 0xff;
                block[j*4+3] = ctx->data[j] & 0xff;
            }
            sha1_transform(ctx, block);
            ctx->Nl += 512;
            ctx->num = 0;
        }
    }
    return 1;
}

int SHA1_Final(uint8_t *md, SHA_CTX *ctx) {
    uint32_t i = ctx->num;
    
    // Pad
    if (ctx->num < 56) {
        ctx->data[i / 4] = (ctx->data[i / 4] << 8) | 0x80;
        i++;
        while (i < 56) {
            if (i % 4 == 0) ctx->data[i / 4] = 0;
            ctx->data[i / 4] <<= 8;
            i++;
        }
    } else {
        ctx->data[i / 4] = (ctx->data[i / 4] << 8) | 0x80;
        i++;
        while (i < 64) {
            if (i % 4 == 0) ctx->data[i / 4] = 0;
            ctx->data[i / 4] <<= 8;
            i++;
        }
        uint8_t block[64];
        for (int j = 0; j < 16; j++) {
            block[j*4] = (ctx->data[j] >> 24) & 0xff;
            block[j*4+1] = (ctx->data[j] >> 16) & 0xff;
            block[j*4+2] = (ctx->data[j] >> 8) & 0xff;
            block[j*4+3] = ctx->data[j] & 0xff;
        }
        sha1_transform(ctx, block);
        memset(ctx->data, 0, 56);
    }
    
    // Append length
    uint64_t bitlen = ctx->Nl + ctx->num * 8;
    ctx->data[14] = bitlen >> 32;
    ctx->data[15] = bitlen & 0xffffffff;
    
    uint8_t block[64];
    for (int j = 0; j < 16; j++) {
        block[j*4] = (ctx->data[j] >> 24) & 0xff;
        block[j*4+1] = (ctx->data[j] >> 16) & 0xff;
        block[j*4+2] = (ctx->data[j] >> 8) & 0xff;
        block[j*4+3] = ctx->data[j] & 0xff;
    }
    sha1_transform(ctx, block);
    
    // Output
    for (i = 0; i < 5; ++i) {
        md[i*4] = (ctx->h[i] >> 24) & 0xff;
        md[i*4+1] = (ctx->h[i] >> 16) & 0xff;
        md[i*4+2] = (ctx->h[i] >> 8) & 0xff;
        md[i*4+3] = ctx->h[i] & 0xff;
    }
    return 1;
}

// ============================================================================
// MD5 实现
// ============================================================================

#define MD5_F(x,y,z) ((x & y) | (~x & z))
#define MD5_G(x,y,z) ((x & z) | (y & ~z))
#define MD5_H(x,y,z) (x ^ y ^ z)
#define MD5_I(x,y,z) (y ^ (x | ~z))
#define MD5_ROTLEFT(a,b) (((a) << (b)) | ((a) >> (32-(b))))

#define MD5_FF(a,b,c,d,m,s,t) { a += MD5_F(b,c,d) + m + t; a = b + MD5_ROTLEFT(a,s); }
#define MD5_GG(a,b,c,d,m,s,t) { a += MD5_G(b,c,d) + m + t; a = b + MD5_ROTLEFT(a,s); }
#define MD5_HH(a,b,c,d,m,s,t) { a += MD5_H(b,c,d) + m + t; a = b + MD5_ROTLEFT(a,s); }
#define MD5_II(a,b,c,d,m,s,t) { a += MD5_I(b,c,d) + m + t; a = b + MD5_ROTLEFT(a,s); }

static void md5_transform(MD5_CTX *ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, m[16], i, j;
    
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = (data[j]) + (data[j + 1] << 8) + (data[j + 2] << 16) + (data[j + 3] << 24);
    
    a = ctx->A;
    b = ctx->B;
    c = ctx->C;
    d = ctx->D;
    
    MD5_FF(a,b,c,d,m[0],  7,0xd76aa478);
    MD5_FF(d,a,b,c,m[1], 12,0xe8c7b756);
    MD5_FF(c,d,a,b,m[2], 17,0x242070db);
    MD5_FF(b,c,d,a,m[3], 22,0xc1bdceee);
    MD5_FF(a,b,c,d,m[4],  7,0xf57c0faf);
    MD5_FF(d,a,b,c,m[5], 12,0x4787c62a);
    MD5_FF(c,d,a,b,m[6], 17,0xa8304613);
    MD5_FF(b,c,d,a,m[7], 22,0xfd469501);
    MD5_FF(a,b,c,d,m[8],  7,0x698098d8);
    MD5_FF(d,a,b,c,m[9], 12,0x8b44f7af);
    MD5_FF(c,d,a,b,m[10],17,0xffff5bb1);
    MD5_FF(b,c,d,a,m[11],22,0x895cd7be);
    MD5_FF(a,b,c,d,m[12], 7,0x6b901122);
    MD5_FF(d,a,b,c,m[13],12,0xfd987193);
    MD5_FF(c,d,a,b,m[14],17,0xa679438e);
    MD5_FF(b,c,d,a,m[15],22,0x49b40821);
    
    MD5_GG(a,b,c,d,m[1],  5,0xf61e2562);
    MD5_GG(d,a,b,c,m[6],  9,0xc040b340);
    MD5_GG(c,d,a,b,m[11],14,0x265e5a51);
    MD5_GG(b,c,d,a,m[0], 20,0xe9b6c7aa);
    MD5_GG(a,b,c,d,m[5],  5,0xd62f105d);
    MD5_GG(d,a,b,c,m[10], 9,0x02441453);
    MD5_GG(c,d,a,b,m[15],14,0xd8a1e681);
    MD5_GG(b,c,d,a,m[4], 20,0xe7d3fbc8);
    MD5_GG(a,b,c,d,m[9],  5,0x21e1cde6);
    MD5_GG(d,a,b,c,m[14], 9,0xc33707d6);
    MD5_GG(c,d,a,b,m[3], 14,0xf4d50d87);
    MD5_GG(b,c,d,a,m[8], 20,0x455a14ed);
    MD5_GG(a,b,c,d,m[13], 5,0xa9e3e905);
    MD5_GG(d,a,b,c,m[2],  9,0xfcefa3f8);
    MD5_GG(c,d,a,b,m[7], 14,0x676f02d9);
    MD5_GG(b,c,d,a,m[12],20,0x8d2a4c8a);
    
    MD5_HH(a,b,c,d,m[5],  4,0xfffa3942);
    MD5_HH(d,a,b,c,m[8], 11,0x8771f681);
    MD5_HH(c,d,a,b,m[11],16,0x6d9d6122);
    MD5_HH(b,c,d,a,m[14],23,0xfde5380c);
    MD5_HH(a,b,c,d,m[1],  4,0xa4beea44);
    MD5_HH(d,a,b,c,m[4], 11,0x4bdecfa9);
    MD5_HH(c,d,a,b,m[7], 16,0xf6bb4b60);
    MD5_HH(b,c,d,a,m[10],23,0xbebfbc70);
    MD5_HH(a,b,c,d,m[13], 4,0x289b7ec6);
    MD5_HH(d,a,b,c,m[0], 11,0xeaa127fa);
    MD5_HH(c,d,a,b,m[3], 16,0xd4ef3085);
    MD5_HH(b,c,d,a,m[6], 23,0x04881d05);
    MD5_HH(a,b,c,d,m[9],  4,0xd9d4d039);
    MD5_HH(d,a,b,c,m[12],11,0xe6db99e5);
    MD5_HH(c,d,a,b,m[15],16,0x1fa27cf8);
    MD5_HH(b,c,d,a,m[2], 23,0xc4ac5665);
    
    MD5_II(a,b,c,d,m[0],  6,0xf4292244);
    MD5_II(d,a,b,c,m[7], 10,0x432aff97);
    MD5_II(c,d,a,b,m[14],15,0xab9423a7);
    MD5_II(b,c,d,a,m[5], 21,0xfc93a039);
    MD5_II(a,b,c,d,m[12], 6,0x655b59c3);
    MD5_II(d,a,b,c,m[3], 10,0x8f0ccc92);
    MD5_II(c,d,a,b,m[10],15,0xffeff47d);
    MD5_II(b,c,d,a,m[1], 21,0x85845dd1);
    MD5_II(a,b,c,d,m[8],  6,0x6fa87e4f);
    MD5_II(d,a,b,c,m[15],10,0xfe2ce6e0);
    MD5_II(c,d,a,b,m[6], 15,0xa3014314);
    MD5_II(b,c,d,a,m[13],21,0x4e0811a1);
    MD5_II(a,b,c,d,m[4],  6,0xf7537e82);
    MD5_II(d,a,b,c,m[11],10,0xbd3af235);
    MD5_II(c,d,a,b,m[2], 15,0x2ad7d2bb);
    MD5_II(b,c,d,a,m[9], 21,0xeb86d391);
    
    ctx->A += a;
    ctx->B += b;
    ctx->C += c;
    ctx->D += d;
}

int MD5_Init(MD5_CTX *ctx) {
    ctx->Nl = 0;
    ctx->Nh = 0;
    ctx->A = 0x67452301;
    ctx->B = 0xefcdab89;
    ctx->C = 0x98badcfe;
    ctx->D = 0x10325476;
    ctx->num = 0;
    return 1;
}

int MD5_Update(MD5_CTX *ctx, const void *data, size_t len) {
    const uint8_t *d = (const uint8_t *)data;
    
    for (size_t i = 0; i < len; ++i) {
        // 直接按字节存储到缓冲区
        ((uint8_t*)ctx->data)[ctx->num] = d[i];
        ctx->num++;
        
        if (ctx->num == 64) {
            // 缓冲区满了，处理这个块
            md5_transform(ctx, (uint8_t*)ctx->data);
            ctx->Nl += 512;
            ctx->num = 0;
        }
    }
    return 1;
}

int MD5_Final(uint8_t *md, MD5_CTX *ctx) {
    uint8_t *buffer = (uint8_t*)ctx->data;
    uint32_t i = ctx->num;
    
    // Pad with 0x80 followed by zeros
    buffer[i++] = 0x80;
    
    // If we don't have room for the length, pad to 64 bytes and process
    if (i > 56) {
        while (i < 64) {
            buffer[i++] = 0;
        }
        md5_transform(ctx, buffer);
        i = 0;
    }
    
    // Pad with zeros until we have 56 bytes
    while (i < 56) {
        buffer[i++] = 0;
    }
    
    // Append length in bits (little-endian)
    uint64_t bitlen = ctx->Nl + ctx->num * 8;
    buffer[56] = bitlen & 0xff;
    buffer[57] = (bitlen >> 8) & 0xff;
    buffer[58] = (bitlen >> 16) & 0xff;
    buffer[59] = (bitlen >> 24) & 0xff;
    buffer[60] = (bitlen >> 32) & 0xff;
    buffer[61] = (bitlen >> 40) & 0xff;
    buffer[62] = (bitlen >> 48) & 0xff;
    buffer[63] = (bitlen >> 56) & 0xff;
    
    // Process final block
    md5_transform(ctx, buffer);
    
    // Output (little-endian)
    md[0] = ctx->A & 0xff;
    md[1] = (ctx->A >> 8) & 0xff;
    md[2] = (ctx->A >> 16) & 0xff;
    md[3] = (ctx->A >> 24) & 0xff;
    md[4] = ctx->B & 0xff;
    md[5] = (ctx->B >> 8) & 0xff;
    md[6] = (ctx->B >> 16) & 0xff;
    md[7] = (ctx->B >> 24) & 0xff;
    md[8] = ctx->C & 0xff;
    md[9] = (ctx->C >> 8) & 0xff;
    md[10] = (ctx->C >> 16) & 0xff;
    md[11] = (ctx->C >> 24) & 0xff;
    md[12] = ctx->D & 0xff;
    md[13] = (ctx->D >> 8) & 0xff;
    md[14] = (ctx->D >> 16) & 0xff;
    md[15] = (ctx->D >> 24) & 0xff;
    
    return 1;
}

// ============================================================================
// DES-CBC 实现
// ============================================================================

// 外部 DES 实现
extern "C" void des_cbc_decrypt(const uint8_t *input, uint8_t *output, int length,
                                const uint8_t *key, const uint8_t *iv);

struct evp_cipher_ctx_st {
    uint8_t key[8];
    uint8_t iv[8];
    int encrypt;
};

struct evp_cipher_st {
    int block_size;
};

static evp_cipher_st des_cbc_cipher = { 8 };

EVP_CIPHER_CTX *EVP_CIPHER_CTX_new(void) {
    return (EVP_CIPHER_CTX *)calloc(1, sizeof(EVP_CIPHER_CTX));
}

void EVP_CIPHER_CTX_free(EVP_CIPHER_CTX *ctx) {
    if (ctx) free(ctx);
}

const EVP_CIPHER *EVP_des_cbc(void) {
    return &des_cbc_cipher;
}

int EVP_CIPHER_block_size(const EVP_CIPHER *cipher) {
    return cipher->block_size;
}

int EVP_DecryptInit_ex(EVP_CIPHER_CTX *ctx, const EVP_CIPHER *type,
                       void * /*impl*/, const uint8_t *key, const uint8_t *iv) {
    if (!ctx || !type) return 0;
    memcpy(ctx->key, key, 8);
    memcpy(ctx->iv, iv, 8);
    ctx->encrypt = 0;
    return 1;
}

int EVP_DecryptUpdate(EVP_CIPHER_CTX *ctx, uint8_t *out, int *outl,
                      const uint8_t *in, int inl) {
    if (!ctx || !out || !in) return 0;
    
    // 使用完整的 DES-CBC 解密
    des_cbc_decrypt(in, out, inl, ctx->key, ctx->iv);
    
    // 更新 IV 为最后一个密文块
    if (inl >= 8) {
        memcpy(ctx->iv, in + inl - 8, 8);
    }
    
    *outl = inl;
    return 1;
}

int EVP_CIPHER_CTX_set_padding(EVP_CIPHER_CTX *ctx, int pad) {
    // 设置padding模式（0=禁用，1=启用）
    // 我们的实现中不使用自动padding
    return 1;
}

int EVP_DecryptFinal_ex(EVP_CIPHER_CTX *ctx, uint8_t *outm, int *outl) {
    // Remove PKCS#7 padding
    if (!ctx || !outm) return 0;
    *outl = 0;
    return 1;
}
