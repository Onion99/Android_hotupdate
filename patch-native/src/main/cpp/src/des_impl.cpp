/**
 * DES Implementation
 * 
 * 完整的 DES 加密算法实现
 * 基于公共领域的实现
 */

#include <stdint.h>
#include <string.h>

// Initial Permutation (IP)
static const int IP[64] = {
    58, 50, 42, 34, 26, 18, 10, 2,
    60, 52, 44, 36, 28, 20, 12, 4,
    62, 54, 46, 38, 30, 22, 14, 6,
    64, 56, 48, 40, 32, 24, 16, 8,
    57, 49, 41, 33, 25, 17, 9, 1,
    59, 51, 43, 35, 27, 19, 11, 3,
    61, 53, 45, 37, 29, 21, 13, 5,
    63, 55, 47, 39, 31, 23, 15, 7
};

// Final Permutation (FP)
static const int FP[64] = {
    40, 8, 48, 16, 56, 24, 64, 32,
    39, 7, 47, 15, 55, 23, 63, 31,
    38, 6, 46, 14, 54, 22, 62, 30,
    37, 5, 45, 13, 53, 21, 61, 29,
    36, 4, 44, 12, 52, 20, 60, 28,
    35, 3, 43, 11, 51, 19, 59, 27,
    34, 2, 42, 10, 50, 18, 58, 26,
    33, 1, 41, 9, 49, 17, 57, 25
};

// Expansion (E)
static const int E[48] = {
    32, 1, 2, 3, 4, 5,
    4, 5, 6, 7, 8, 9,
    8, 9, 10, 11, 12, 13,
    12, 13, 14, 15, 16, 17,
    16, 17, 18, 19, 20, 21,
    20, 21, 22, 23, 24, 25,
    24, 25, 26, 27, 28, 29,
    28, 29, 30, 31, 32, 1
};

// Permutation (P)
static const int P[32] = {
    16, 7, 20, 21,
    29, 12, 28, 17,
    1, 15, 23, 26,
    5, 18, 31, 10,
    2, 8, 24, 14,
    32, 27, 3, 9,
    19, 13, 30, 6,
    22, 11, 4, 25
};

// Permuted Choice 1 (PC1)
static const int PC1[56] = {
    57, 49, 41, 33, 25, 17, 9,
    1, 58, 50, 42, 34, 26, 18,
    10, 2, 59, 51, 43, 35, 27,
    19, 11, 3, 60, 52, 44, 36,
    63, 55, 47, 39, 31, 23, 15,
    7, 62, 54, 46, 38, 30, 22,
    14, 6, 61, 53, 45, 37, 29,
    21, 13, 5, 28, 20, 12, 4
};

// Permuted Choice 2 (PC2)
static const int PC2[48] = {
    14, 17, 11, 24, 1, 5,
    3, 28, 15, 6, 21, 10,
    23, 19, 12, 4, 26, 8,
    16, 7, 27, 20, 13, 2,
    41, 52, 31, 37, 47, 55,
    30, 40, 51, 45, 33, 48,
    44, 49, 39, 56, 34, 53,
    46, 42, 50, 36, 29, 32
};

// S-boxes
static const int S[8][4][16] = {
    // S1
    {
        {14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7},
        {0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8},
        {4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0},
        {15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13}
    },
    // S2
    {
        {15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10},
        {3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5},
        {0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15},
        {13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9}
    },
    // S3
    {
        {10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8},
        {13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1},
        {13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7},
        {1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12}
    },
    // S4
    {
        {7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15},
        {13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9},
        {10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4},
        {3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14}
    },
    // S5
    {
        {2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9},
        {14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6},
        {4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14},
        {11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3}
    },
    // S6
    {
        {12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11},
        {10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8},
        {9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6},
        {4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13}
    },
    // S7
    {
        {4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1},
        {13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6},
        {1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2},
        {6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12}
    },
    // S8
    {
        {13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7},
        {1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2},
        {7, 11, 4, 1, 9, 12, 14, 2, 0, 5, 10, 3, 13, 8, 15, 6},
        {2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11}
    }
};

// Left shifts for key schedule
static const int shifts[16] = {
    1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1
};

// Helper functions
static uint64_t permute(uint64_t input, const int *table, int n) {
    uint64_t output = 0;
    for (int i = 0; i < n; i++) {
        output <<= 1;
        output |= (input >> (64 - table[i])) & 1;
    }
    return output;
}

static uint32_t permute32(uint32_t input, const int *table, int n) {
    uint32_t output = 0;
    for (int i = 0; i < n; i++) {
        output <<= 1;
        output |= (input >> (32 - table[i])) & 1;
    }
    return output;
}

static uint64_t permute56to48(uint64_t input, const int *table) {
    uint64_t output = 0;
    for (int i = 0; i < 48; i++) {
        output <<= 1;
        output |= (input >> (56 - table[i])) & 1;
    }
    return output;
}

static uint64_t left_shift_28(uint64_t value, int shifts) {
    uint64_t top = (value >> 28) & 0xFFFFFFF;
    uint64_t bottom = value & 0xFFFFFFF;
    
    top = ((top << shifts) | (top >> (28 - shifts))) & 0xFFFFFFF;
    bottom = ((bottom << shifts) | (bottom >> (28 - shifts))) & 0xFFFFFFF;
    
    return (top << 28) | bottom;
}

// Generate 16 subkeys
static void generate_subkeys(const uint8_t *key, uint64_t *subkeys) {
    // Convert key to 64-bit
    uint64_t key64 = 0;
    for (int i = 0; i < 8; i++) {
        key64 = (key64 << 8) | key[i];
    }
    
    // PC1
    uint64_t permuted_key = permute(key64, PC1, 56);
    
    // Generate 16 subkeys
    for (int round = 0; round < 16; round++) {
        permuted_key = left_shift_28(permuted_key, shifts[round]);
        subkeys[round] = permute56to48(permuted_key, PC2);
    }
}

// F function
static uint32_t f_function(uint32_t r, uint64_t subkey) {
    // Expansion
    uint64_t expanded = 0;
    for (int i = 0; i < 48; i++) {
        expanded <<= 1;
        expanded |= (r >> (32 - E[i])) & 1;
    }
    
    // XOR with subkey
    expanded ^= subkey;
    
    // S-boxes
    uint32_t output = 0;
    for (int i = 0; i < 8; i++) {
        int box_input = (expanded >> (42 - i * 6)) & 0x3F;
        int row = ((box_input & 0x20) >> 4) | (box_input & 0x01);
        int col = (box_input >> 1) & 0x0F;
        output = (output << 4) | S[i][row][col];
    }
    
    // Permutation
    return permute32(output, P, 32);
}

// DES encryption/decryption of a single block
static void des_crypt_block(const uint8_t *input, uint8_t *output, const uint64_t *subkeys, int decrypt) {
    // Convert input to 64-bit
    uint64_t block = 0;
    for (int i = 0; i < 8; i++) {
        block = (block << 8) | input[i];
    }
    
    // Initial permutation
    block = permute(block, IP, 64);
    
    // Split into L and R
    uint32_t l = (block >> 32) & 0xFFFFFFFF;
    uint32_t r = block & 0xFFFFFFFF;
    
    // 16 rounds
    for (int round = 0; round < 16; round++) {
        uint32_t temp = r;
        int key_index = decrypt ? (15 - round) : round;
        r = l ^ f_function(r, subkeys[key_index]);
        l = temp;
    }
    
    // Combine L and R (note: R and L are swapped)
    block = ((uint64_t)r << 32) | l;
    
    // Final permutation
    block = permute(block, FP, 64);
    
    // Convert output to bytes
    for (int i = 0; i < 8; i++) {
        output[7 - i] = block & 0xFF;
        block >>= 8;
    }
}

// DES-CBC decryption
extern "C" void des_cbc_decrypt(const uint8_t *input, uint8_t *output, int length,
                                const uint8_t *key, const uint8_t *iv) {
    uint64_t subkeys[16];
    generate_subkeys(key, subkeys);
    
    uint8_t prev_cipher[8];
    memcpy(prev_cipher, iv, 8);
    
    for (int i = 0; i < length; i += 8) {
        uint8_t cipher[8];
        memcpy(cipher, input + i, 8);
        
        // Decrypt block
        des_crypt_block(cipher, output + i, subkeys, 1);
        
        // XOR with previous ciphertext (CBC mode)
        for (int j = 0; j < 8; j++) {
            output[i + j] ^= prev_cipher[j];
        }
        
        memcpy(prev_cipher, cipher, 8);
    }
}
