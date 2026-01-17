/**
 * compress.cpp
 * 
 * 压缩/解压缩实现 - bzip2 支持
 * 
 * Requirements: 12.5
 */

#include <cstdlib>
#include <cstring>
#include <cstdio>

extern "C" {
#include "compress.h"
#include "patch_engine.h"
#include "bzlib.h"
}

/* ============================================================================
 * bzip2 压缩
 * ============================================================================ */

int compress_bzip2(
    const void* src,
    size_t srcSize,
    void* dst,
    size_t* dstSize,
    int level
) {
    if (src == nullptr || dst == nullptr || dstSize == nullptr) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    if (level < 1 || level > 9) {
        level = COMPRESS_LEVEL_DEFAULT;
    }
    
    unsigned int destLen = (unsigned int)*dstSize;
    
    int ret = BZ2_bzBuffToBuffCompress(
        (char*)dst,
        &destLen,
        (char*)src,
        (unsigned int)srcSize,
        level,      /* blockSize100k */
        0,          /* verbosity */
        30          /* workFactor */
    );
    
    if (ret == BZ_OK) {
        *dstSize = destLen;
        return PE_SUCCESS;
    } else if (ret == BZ_OUTBUFF_FULL) {
        pe_set_error(PE_ERROR_COMPRESS_FAILED, "Output buffer too small");
        return PE_ERROR_COMPRESS_FAILED;
    } else if (ret == BZ_MEM_ERROR) {
        return PE_ERROR_OUT_OF_MEMORY;
    } else {
        pe_set_error(PE_ERROR_COMPRESS_FAILED, "bzip2 compression failed");
        return PE_ERROR_COMPRESS_FAILED;
    }
}

/* ============================================================================
 * bzip2 解压
 * ============================================================================ */

int decompress_bzip2(
    const void* src,
    size_t srcSize,
    void* dst,
    size_t* dstSize
) {
    if (src == nullptr || dst == nullptr || dstSize == nullptr) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    unsigned int destLen = (unsigned int)*dstSize;
    
    int ret = BZ2_bzBuffToBuffDecompress(
        (char*)dst,
        &destLen,
        (char*)src,
        (unsigned int)srcSize,
        0,          /* small */
        0           /* verbosity */
    );
    
    if (ret == BZ_OK) {
        *dstSize = destLen;
        return PE_SUCCESS;
    } else if (ret == BZ_OUTBUFF_FULL) {
        pe_set_error(PE_ERROR_DECOMPRESS_FAILED, "Output buffer too small");
        return PE_ERROR_DECOMPRESS_FAILED;
    } else if (ret == BZ_MEM_ERROR) {
        return PE_ERROR_OUT_OF_MEMORY;
    } else if (ret == BZ_DATA_ERROR || ret == BZ_DATA_ERROR_MAGIC) {
        pe_set_error(PE_ERROR_DECOMPRESS_FAILED, "Invalid compressed data");
        return PE_ERROR_DECOMPRESS_FAILED;
    } else {
        pe_set_error(PE_ERROR_DECOMPRESS_FAILED, "bzip2 decompression failed");
        return PE_ERROR_DECOMPRESS_FAILED;
    }
}


/* ============================================================================
 * 估算压缩后最大大小
 * ============================================================================ */

size_t compress_bzip2_bound(size_t srcSize) {
    /* bzip2 最坏情况下可能略微增大
     * 估算公式: srcSize + srcSize/100 + 600
     */
    return srcSize + srcSize / 100 + 600;
}

/* ============================================================================
 * 流式压缩到文件
 * ============================================================================ */

int compress_bzip2_to_file(
    const void* src,
    size_t srcSize,
    const char* filePath,
    int level
) {
    if (src == nullptr || filePath == nullptr) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    if (level < 1 || level > 9) {
        level = COMPRESS_LEVEL_DEFAULT;
    }
    
    /* 估算压缩后大小并分配缓冲区 */
    size_t compressedSize = compress_bzip2_bound(srcSize);
    void* compressedData = malloc(compressedSize);
    if (compressedData == nullptr) {
        return PE_ERROR_OUT_OF_MEMORY;
    }
    
    /* 压缩数据 */
    int ret = compress_bzip2(src, srcSize, compressedData, &compressedSize, level);
    if (ret != PE_SUCCESS) {
        free(compressedData);
        return ret;
    }
    
    /* 写入文件 */
    FILE* f = fopen(filePath, "wb");
    if (f == nullptr) {
        free(compressedData);
        pe_set_error(PE_ERROR_FILE_WRITE, "Cannot create output file");
        return PE_ERROR_FILE_WRITE;
    }
    
    size_t written = fwrite(compressedData, 1, compressedSize, f);
    fclose(f);
    free(compressedData);
    
    if (written != compressedSize) {
        pe_set_error(PE_ERROR_FILE_WRITE, "Failed to write compressed data");
        return PE_ERROR_FILE_WRITE;
    }
    
    return PE_SUCCESS;
}

/* ============================================================================
 * 从文件流式解压
 * ============================================================================ */

int decompress_bzip2_from_file(
    const char* filePath,
    void* dst,
    size_t* dstSize
) {
    if (filePath == nullptr || dst == nullptr || dstSize == nullptr) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    /* 读取压缩文件 */
    FILE* f = fopen(filePath, "rb");
    if (f == nullptr) {
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open input file");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    
    /* 获取文件大小 */
    fseek(f, 0, SEEK_END);
    long fileSize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    if (fileSize <= 0) {
        fclose(f);
        pe_set_error(PE_ERROR_FILE_READ, "Invalid file size");
        return PE_ERROR_FILE_READ;
    }
    
    /* 读取压缩数据 */
    void* compressedData = malloc((size_t)fileSize);
    if (compressedData == nullptr) {
        fclose(f);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    
    size_t bytesRead = fread(compressedData, 1, (size_t)fileSize, f);
    fclose(f);
    
    if (bytesRead != (size_t)fileSize) {
        free(compressedData);
        pe_set_error(PE_ERROR_FILE_READ, "Failed to read compressed data");
        return PE_ERROR_FILE_READ;
    }
    
    /* 解压数据 */
    int ret = decompress_bzip2(compressedData, bytesRead, dst, dstSize);
    free(compressedData);
    
    return ret;
}

/* ============================================================================
 * Patch Engine 压缩接口
 * ============================================================================ */

extern "C" {

int pe_compress_bzip2(const void* src, size_t srcSize, void* dst, size_t* dstSize) {
    return compress_bzip2(src, srcSize, dst, dstSize, COMPRESS_LEVEL_DEFAULT);
}

int pe_decompress_bzip2(const void* src, size_t srcSize, void* dst, size_t* dstSize) {
    return decompress_bzip2(src, srcSize, dst, dstSize);
}

} /* extern "C" */
