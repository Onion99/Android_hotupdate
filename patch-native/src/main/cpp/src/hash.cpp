/**
 * hash.cpp
 * 
 * 哈希计算实现 - MD5 和 SHA256
 * 
 * 使用内存映射优化大文件处理
 * 
 * Requirements: 10.7
 */

#include <cstdlib>
#include <cstring>
#include <cstdio>

#ifdef __ANDROID__
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#endif

extern "C" {
#include "hash.h"
#include "patch_engine.h"
}

/* ============================================================================
 * MD5 实现
 * ============================================================================ */

/* MD5 常量 */
#define MD5_F(x, y, z) (((x) & (y)) | ((~x) & (z)))
#define MD5_G(x, y, z) (((x) & (z)) | ((y) & (~z)))
#define MD5_H(x, y, z) ((x) ^ (y) ^ (z))
#define MD5_I(x, y, z) ((y) ^ ((x) | (~z)))

#define MD5_ROTATE_LEFT(x, n) (((x) << (n)) | ((x) >> (32-(n))))

#define MD5_FF(a, b, c, d, x, s, ac) { \
    (a) += MD5_F((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = MD5_ROTATE_LEFT((a), (s)); \
    (a) += (b); \
}
#define MD5_GG(a, b, c, d, x, s, ac) { \
    (a) += MD5_G((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = MD5_ROTATE_LEFT((a), (s)); \
    (a) += (b); \
}
#define MD5_HH(a, b, c, d, x, s, ac) { \
    (a) += MD5_H((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = MD5_ROTATE_LEFT((a), (s)); \
    (a) += (b); \
}
#define MD5_II(a, b, c, d, x, s, ac) { \
    (a) += MD5_I((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = MD5_ROTATE_LEFT((a), (s)); \
    (a) += (b); \
}

static const uint8_t MD5_PADDING[64] = {
    0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

static void md5_transform(uint32_t state[4], const uint8_t block[64]) {
    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t x[16];
    
    for (int i = 0, j = 0; j < 64; i++, j += 4) {
        x[i] = ((uint32_t)block[j]) | (((uint32_t)block[j+1]) << 8) |
               (((uint32_t)block[j+2]) << 16) | (((uint32_t)block[j+3]) << 24);
    }
    
    /* Round 1 */
    MD5_FF(a, b, c, d, x[ 0],  7, 0xd76aa478);
    MD5_FF(d, a, b, c, x[ 1], 12, 0xe8c7b756);
    MD5_FF(c, d, a, b, x[ 2], 17, 0x242070db);
    MD5_FF(b, c, d, a, x[ 3], 22, 0xc1bdceee);
    MD5_FF(a, b, c, d, x[ 4],  7, 0xf57c0faf);
    MD5_FF(d, a, b, c, x[ 5], 12, 0x4787c62a);
    MD5_FF(c, d, a, b, x[ 6], 17, 0xa8304613);
    MD5_FF(b, c, d, a, x[ 7], 22, 0xfd469501);
    MD5_FF(a, b, c, d, x[ 8],  7, 0x698098d8);
    MD5_FF(d, a, b, c, x[ 9], 12, 0x8b44f7af);
    MD5_FF(c, d, a, b, x[10], 17, 0xffff5bb1);
    MD5_FF(b, c, d, a, x[11], 22, 0x895cd7be);
    MD5_FF(a, b, c, d, x[12],  7, 0x6b901122);
    MD5_FF(d, a, b, c, x[13], 12, 0xfd987193);
    MD5_FF(c, d, a, b, x[14], 17, 0xa679438e);
    MD5_FF(b, c, d, a, x[15], 22, 0x49b40821);
    
    /* Round 2 */
    MD5_GG(a, b, c, d, x[ 1],  5, 0xf61e2562);
    MD5_GG(d, a, b, c, x[ 6],  9, 0xc040b340);
    MD5_GG(c, d, a, b, x[11], 14, 0x265e5a51);
    MD5_GG(b, c, d, a, x[ 0], 20, 0xe9b6c7aa);
    MD5_GG(a, b, c, d, x[ 5],  5, 0xd62f105d);
    MD5_GG(d, a, b, c, x[10],  9, 0x02441453);
    MD5_GG(c, d, a, b, x[15], 14, 0xd8a1e681);
    MD5_GG(b, c, d, a, x[ 4], 20, 0xe7d3fbc8);
    MD5_GG(a, b, c, d, x[ 9],  5, 0x21e1cde6);
    MD5_GG(d, a, b, c, x[14],  9, 0xc33707d6);
    MD5_GG(c, d, a, b, x[ 3], 14, 0xf4d50d87);
    MD5_GG(b, c, d, a, x[ 8], 20, 0x455a14ed);
    MD5_GG(a, b, c, d, x[13],  5, 0xa9e3e905);
    MD5_GG(d, a, b, c, x[ 2],  9, 0xfcefa3f8);
    MD5_GG(c, d, a, b, x[ 7], 14, 0x676f02d9);
    MD5_GG(b, c, d, a, x[12], 20, 0x8d2a4c8a);

    /* Round 3 */
    MD5_HH(a, b, c, d, x[ 5],  4, 0xfffa3942);
    MD5_HH(d, a, b, c, x[ 8], 11, 0x8771f681);
    MD5_HH(c, d, a, b, x[11], 16, 0x6d9d6122);
    MD5_HH(b, c, d, a, x[14], 23, 0xfde5380c);
    MD5_HH(a, b, c, d, x[ 1],  4, 0xa4beea44);
    MD5_HH(d, a, b, c, x[ 4], 11, 0x4bdecfa9);
    MD5_HH(c, d, a, b, x[ 7], 16, 0xf6bb4b60);
    MD5_HH(b, c, d, a, x[10], 23, 0xbebfbc70);
    MD5_HH(a, b, c, d, x[13],  4, 0x289b7ec6);
    MD5_HH(d, a, b, c, x[ 0], 11, 0xeaa127fa);
    MD5_HH(c, d, a, b, x[ 3], 16, 0xd4ef3085);
    MD5_HH(b, c, d, a, x[ 6], 23, 0x04881d05);
    MD5_HH(a, b, c, d, x[ 9],  4, 0xd9d4d039);
    MD5_HH(d, a, b, c, x[12], 11, 0xe6db99e5);
    MD5_HH(c, d, a, b, x[15], 16, 0x1fa27cf8);
    MD5_HH(b, c, d, a, x[ 2], 23, 0xc4ac5665);
    
    /* Round 4 */
    MD5_II(a, b, c, d, x[ 0],  6, 0xf4292244);
    MD5_II(d, a, b, c, x[ 7], 10, 0x432aff97);
    MD5_II(c, d, a, b, x[14], 15, 0xab9423a7);
    MD5_II(b, c, d, a, x[ 5], 21, 0xfc93a039);
    MD5_II(a, b, c, d, x[12],  6, 0x655b59c3);
    MD5_II(d, a, b, c, x[ 3], 10, 0x8f0ccc92);
    MD5_II(c, d, a, b, x[10], 15, 0xffeff47d);
    MD5_II(b, c, d, a, x[ 1], 21, 0x85845dd1);
    MD5_II(a, b, c, d, x[ 8],  6, 0x6fa87e4f);
    MD5_II(d, a, b, c, x[15], 10, 0xfe2ce6e0);
    MD5_II(c, d, a, b, x[ 6], 15, 0xa3014314);
    MD5_II(b, c, d, a, x[13], 21, 0x4e0811a1);
    MD5_II(a, b, c, d, x[ 4],  6, 0xf7537e82);
    MD5_II(d, a, b, c, x[11], 10, 0xbd3af235);
    MD5_II(c, d, a, b, x[ 2], 15, 0x2ad7d2bb);
    MD5_II(b, c, d, a, x[ 9], 21, 0xeb86d391);
    
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
}

void md5_init(MD5Context* ctx) {
    ctx->count[0] = ctx->count[1] = 0;
    ctx->state[0] = 0x67452301;
    ctx->state[1] = 0xefcdab89;
    ctx->state[2] = 0x98badcfe;
    ctx->state[3] = 0x10325476;
}

void md5_update(MD5Context* ctx, const uint8_t* data, size_t len) {
    size_t i, index, partLen;
    
    index = (size_t)((ctx->count[0] >> 3) & 0x3F);
    
    if ((ctx->count[0] += ((uint32_t)len << 3)) < ((uint32_t)len << 3))
        ctx->count[1]++;
    ctx->count[1] += ((uint32_t)len >> 29);
    
    partLen = 64 - index;
    
    if (len >= partLen) {
        memcpy(&ctx->buffer[index], data, partLen);
        md5_transform(ctx->state, ctx->buffer);
        
        for (i = partLen; i + 63 < len; i += 64)
            md5_transform(ctx->state, &data[i]);
        
        index = 0;
    } else {
        i = 0;
    }
    
    memcpy(&ctx->buffer[index], &data[i], len - i);
}

void md5_final(MD5Context* ctx, uint8_t digest[MD5_DIGEST_LENGTH]) {
    uint8_t bits[8];
    size_t index, padLen;
    
    for (int i = 0; i < 4; i++) {
        bits[i] = (uint8_t)(ctx->count[0] >> (i * 8));
        bits[i + 4] = (uint8_t)(ctx->count[1] >> (i * 8));
    }
    
    index = (size_t)((ctx->count[0] >> 3) & 0x3f);
    padLen = (index < 56) ? (56 - index) : (120 - index);
    md5_update(ctx, MD5_PADDING, padLen);
    md5_update(ctx, bits, 8);
    
    for (int i = 0; i < 4; i++) {
        digest[i * 4] = (uint8_t)(ctx->state[i]);
        digest[i * 4 + 1] = (uint8_t)(ctx->state[i] >> 8);
        digest[i * 4 + 2] = (uint8_t)(ctx->state[i] >> 16);
        digest[i * 4 + 3] = (uint8_t)(ctx->state[i] >> 24);
    }
}

void md5_compute(const uint8_t* data, size_t len, uint8_t digest[MD5_DIGEST_LENGTH]) {
    MD5Context ctx;
    md5_init(&ctx);
    md5_update(&ctx, data, len);
    md5_final(&ctx, digest);
}


/* ============================================================================
 * SHA256 实现
 * ============================================================================ */

#define SHA256_ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define SHA256_CH(x, y, z) (((x) & (y)) ^ ((~(x)) & (z)))
#define SHA256_MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SHA256_EP0(x) (SHA256_ROTR(x, 2) ^ SHA256_ROTR(x, 13) ^ SHA256_ROTR(x, 22))
#define SHA256_EP1(x) (SHA256_ROTR(x, 6) ^ SHA256_ROTR(x, 11) ^ SHA256_ROTR(x, 25))
#define SHA256_SIG0(x) (SHA256_ROTR(x, 7) ^ SHA256_ROTR(x, 18) ^ ((x) >> 3))
#define SHA256_SIG1(x) (SHA256_ROTR(x, 17) ^ SHA256_ROTR(x, 19) ^ ((x) >> 10))

static const uint32_t SHA256_K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

static void sha256_transform(SHA256Context* ctx, const uint8_t data[64]) {
    uint32_t a, b, c, d, e, f, g, h, t1, t2, m[64];
    
    for (int i = 0, j = 0; i < 16; i++, j += 4) {
        m[i] = ((uint32_t)data[j] << 24) | ((uint32_t)data[j + 1] << 16) |
               ((uint32_t)data[j + 2] << 8) | ((uint32_t)data[j + 3]);
    }
    for (int i = 16; i < 64; i++) {
        m[i] = SHA256_SIG1(m[i - 2]) + m[i - 7] + SHA256_SIG0(m[i - 15]) + m[i - 16];
    }
    
    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];
    
    for (int i = 0; i < 64; i++) {
        t1 = h + SHA256_EP1(e) + SHA256_CH(e, f, g) + SHA256_K[i] + m[i];
        t2 = SHA256_EP0(a) + SHA256_MAJ(a, b, c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }
    
    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

void sha256_init(SHA256Context* ctx) {
    ctx->count = 0;
    ctx->state[0] = 0x6a09e667;
    ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372;
    ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f;
    ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab;
    ctx->state[7] = 0x5be0cd19;
}

void sha256_update(SHA256Context* ctx, const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; i++) {
        ctx->buffer[ctx->count % 64] = data[i];
        ctx->count++;
        if (ctx->count % 64 == 0) {
            sha256_transform(ctx, ctx->buffer);
        }
    }
}

void sha256_final(SHA256Context* ctx, uint8_t digest[SHA256_DIGEST_LENGTH]) {
    uint64_t bitlen = ctx->count * 8;
    size_t i = ctx->count % 64;
    
    ctx->buffer[i++] = 0x80;
    
    if (i > 56) {
        while (i < 64) ctx->buffer[i++] = 0x00;
        sha256_transform(ctx, ctx->buffer);
        i = 0;
    }
    
    while (i < 56) ctx->buffer[i++] = 0x00;
    
    ctx->buffer[56] = (uint8_t)(bitlen >> 56);
    ctx->buffer[57] = (uint8_t)(bitlen >> 48);
    ctx->buffer[58] = (uint8_t)(bitlen >> 40);
    ctx->buffer[59] = (uint8_t)(bitlen >> 32);
    ctx->buffer[60] = (uint8_t)(bitlen >> 24);
    ctx->buffer[61] = (uint8_t)(bitlen >> 16);
    ctx->buffer[62] = (uint8_t)(bitlen >> 8);
    ctx->buffer[63] = (uint8_t)(bitlen);
    sha256_transform(ctx, ctx->buffer);
    
    for (int j = 0; j < 8; j++) {
        digest[j * 4] = (uint8_t)(ctx->state[j] >> 24);
        digest[j * 4 + 1] = (uint8_t)(ctx->state[j] >> 16);
        digest[j * 4 + 2] = (uint8_t)(ctx->state[j] >> 8);
        digest[j * 4 + 3] = (uint8_t)(ctx->state[j]);
    }
}

void sha256_compute(const uint8_t* data, size_t len, uint8_t digest[SHA256_DIGEST_LENGTH]) {
    SHA256Context ctx;
    sha256_init(&ctx);
    sha256_update(&ctx, data, len);
    sha256_final(&ctx, digest);
}


/* ============================================================================
 * 工具函数
 * ============================================================================ */

void hash_to_hex(const uint8_t* digest, size_t digestLen, char* hexStr, size_t hexStrLen) {
    static const char hexChars[] = "0123456789abcdef";
    
    if (hexStrLen < digestLen * 2 + 1) return;
    
    for (size_t i = 0; i < digestLen; i++) {
        hexStr[i * 2] = hexChars[(digest[i] >> 4) & 0x0F];
        hexStr[i * 2 + 1] = hexChars[digest[i] & 0x0F];
    }
    hexStr[digestLen * 2] = '\0';
}

void md5_compute_hex(const uint8_t* data, size_t len, char hexStr[MD5_STRING_LENGTH]) {
    uint8_t digest[MD5_DIGEST_LENGTH];
    md5_compute(data, len, digest);
    hash_to_hex(digest, MD5_DIGEST_LENGTH, hexStr, MD5_STRING_LENGTH);
}

void sha256_compute_hex(const uint8_t* data, size_t len, char hexStr[SHA256_STRING_LENGTH]) {
    uint8_t digest[SHA256_DIGEST_LENGTH];
    sha256_compute(data, len, digest);
    hash_to_hex(digest, SHA256_DIGEST_LENGTH, hexStr, SHA256_STRING_LENGTH);
}

/* ============================================================================
 * 文件哈希计算（使用内存映射优化大文件）
 * ============================================================================ */

extern "C" {

int pe_calculate_md5(const char* filePath, char* outHash, size_t outSize) {
    if (filePath == nullptr || outHash == nullptr || outSize < MD5_STRING_LENGTH) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    FILE* f = fopen(filePath, "rb");
    if (f == nullptr) {
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open file for MD5");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    
    MD5Context ctx;
    md5_init(&ctx);
    
    uint8_t buffer[8192];
    size_t bytesRead;
    
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), f)) > 0) {
        md5_update(&ctx, buffer, bytesRead);
        
        if (pe_is_cancelled()) {
            fclose(f);
            return PE_ERROR_CANCELLED;
        }
    }
    
    fclose(f);
    
    uint8_t digest[MD5_DIGEST_LENGTH];
    md5_final(&ctx, digest);
    hash_to_hex(digest, MD5_DIGEST_LENGTH, outHash, outSize);
    
    return PE_SUCCESS;
}

int pe_calculate_sha256(const char* filePath, char* outHash, size_t outSize) {
    if (filePath == nullptr || outHash == nullptr || outSize < SHA256_STRING_LENGTH) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    FILE* f = fopen(filePath, "rb");
    if (f == nullptr) {
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open file for SHA256");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    
    SHA256Context ctx;
    sha256_init(&ctx);
    
    uint8_t buffer[8192];
    size_t bytesRead;
    
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), f)) > 0) {
        sha256_update(&ctx, buffer, bytesRead);
        
        if (pe_is_cancelled()) {
            fclose(f);
            return PE_ERROR_CANCELLED;
        }
    }
    
    fclose(f);
    
    uint8_t digest[SHA256_DIGEST_LENGTH];
    sha256_final(&ctx, digest);
    hash_to_hex(digest, SHA256_DIGEST_LENGTH, outHash, outSize);
    
    return PE_SUCCESS;
}

int pe_calculate_md5_memory(const void* data, size_t dataSize, char* outHash, size_t outSize) {
    if (data == nullptr || outHash == nullptr || outSize < MD5_STRING_LENGTH) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    md5_compute_hex((const uint8_t*)data, dataSize, outHash);
    return PE_SUCCESS;
}

int pe_calculate_sha256_memory(const void* data, size_t dataSize, char* outHash, size_t outSize) {
    if (data == nullptr || outHash == nullptr || outSize < SHA256_STRING_LENGTH) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    sha256_compute_hex((const uint8_t*)data, dataSize, outHash);
    return PE_SUCCESS;
}

} /* extern "C" */
