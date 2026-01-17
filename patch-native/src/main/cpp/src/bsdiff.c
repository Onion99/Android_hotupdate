/**
 * bsdiff.c
 * 
 * BsDiff 算法实现 - 二进制差异生成
 * 
 * 基于 Colin Percival 的 bsdiff 算法
 * Copyright 2003-2005 Colin Percival
 * 
 * Requirements: 12.1, 12.4
 */

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "bsdiff.h"
#include "compress.h"

/* ============================================================================
 * 后缀数组排序 (Suffix Sorting)
 * 使用 qsufsort 算法
 * ============================================================================ */

static void split(int64_t *I, int64_t *V, int64_t start, int64_t len, int64_t h) {
    int64_t i, j, k, x, tmp, jj, kk;

    if (len < 16) {
        for (k = start; k < start + len; k += j) {
            j = 1;
            x = V[I[k] + h];
            for (i = 1; k + i < start + len; i++) {
                if (V[I[k + i] + h] < x) {
                    x = V[I[k + i] + h];
                    j = 0;
                }
                if (V[I[k + i] + h] == x) {
                    tmp = I[k + j];
                    I[k + j] = I[k + i];
                    I[k + i] = tmp;
                    j++;
                }
            }
            for (i = 0; i < j; i++) V[I[k + i]] = k + j - 1;
            if (j == 1) I[k] = -1;
        }
        return;
    }

    x = V[I[start + len / 2] + h];
    jj = 0;
    kk = 0;
    for (i = start; i < start + len; i++) {
        if (V[I[i] + h] < x) jj++;
        if (V[I[i] + h] == x) kk++;
    }
    jj += start;
    kk += jj;

    i = start;
    j = 0;
    k = 0;
    while (i < jj) {
        if (V[I[i] + h] < x) {
            i++;
        } else if (V[I[i] + h] == x) {
            tmp = I[i];
            I[i] = I[jj + j];
            I[jj + j] = tmp;
            j++;
        } else {
            tmp = I[i];
            I[i] = I[kk + k];
            I[kk + k] = tmp;
            k++;
        }
    }

    while (jj + j < kk) {
        if (V[I[jj + j] + h] == x) {
            j++;
        } else {
            tmp = I[jj + j];
            I[jj + j] = I[kk + k];
            I[kk + k] = tmp;
            k++;
        }
    }

    if (jj > start) split(I, V, start, jj - start, h);

    for (i = 0; i < kk - jj; i++) V[I[jj + i]] = kk - 1;
    if (jj == kk - 1) I[jj] = -1;

    if (start + len > kk) split(I, V, kk, start + len - kk, h);
}


/* qsufsort 算法构建后缀数组 */
static void qsufsort(int64_t *I, int64_t *V, const uint8_t *old, int64_t oldsize) {
    int64_t buckets[256];
    int64_t i, h, len;

    for (i = 0; i < 256; i++) buckets[i] = 0;
    for (i = 0; i < oldsize; i++) buckets[old[i]]++;
    for (i = 1; i < 256; i++) buckets[i] += buckets[i - 1];
    for (i = 255; i > 0; i--) buckets[i] = buckets[i - 1];
    buckets[0] = 0;

    for (i = 0; i < oldsize; i++) I[++buckets[old[i]]] = i;
    I[0] = oldsize;
    for (i = 0; i < oldsize; i++) V[i] = buckets[old[i]];
    V[oldsize] = 0;
    for (i = 1; i < 256; i++) {
        if (buckets[i] == buckets[i - 1] + 1) I[buckets[i]] = -1;
    }
    I[0] = -1;

    for (h = 1; I[0] != -(oldsize + 1); h += h) {
        len = 0;
        for (i = 0; i < oldsize + 1;) {
            if (I[i] < 0) {
                len -= I[i];
                i -= I[i];
            } else {
                if (len) I[i - len] = -len;
                len = V[I[i]] + 1 - i;
                split(I, V, i, len, h);
                i += len;
                len = 0;
            }
        }
        if (len) I[i - len] = -len;
    }

    for (i = 0; i < oldsize + 1; i++) I[V[i]] = i;
}

/* 构建后缀数组的公共接口 */
int bsdiff_build_suffix_array(
    const uint8_t* data,
    int64_t size,
    int64_t* suffixArray
) {
    int64_t* V;
    
    if (data == NULL || suffixArray == NULL || size < 0) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    V = (int64_t*)malloc((size + 1) * sizeof(int64_t));
    if (V == NULL) {
        return PE_ERROR_OUT_OF_MEMORY;
    }
    
    qsufsort(suffixArray, V, data, size);
    
    free(V);
    return PE_SUCCESS;
}


/* ============================================================================
 * 二分搜索最长匹配
 * ============================================================================ */

static int64_t matchlen(const uint8_t *old, int64_t oldsize,
                        const uint8_t *new, int64_t newsize) {
    int64_t i;
    for (i = 0; (i < oldsize) && (i < newsize); i++) {
        if (old[i] != new[i]) break;
    }
    return i;
}

int64_t bsdiff_search(
    const int64_t* I,
    const uint8_t* old,
    int64_t oldsize,
    const uint8_t* new,
    int64_t newsize,
    int64_t st,
    int64_t en,
    int64_t* pos
) {
    int64_t x, y;

    if (en - st < 2) {
        x = matchlen(old + I[st], oldsize - I[st], new, newsize);
        y = matchlen(old + I[en], oldsize - I[en], new, newsize);

        if (x > y) {
            *pos = I[st];
            return x;
        } else {
            *pos = I[en];
            return y;
        }
    }

    x = st + (en - st) / 2;
    if (memcmp(old + I[x], new, (size_t)(oldsize - I[x] < newsize ? oldsize - I[x] : newsize)) < 0) {
        return bsdiff_search(I, old, oldsize, new, newsize, x, en, pos);
    } else {
        return bsdiff_search(I, old, oldsize, new, newsize, st, x, pos);
    }
}

/* ============================================================================
 * 写入偏移量（小端序）
 * ============================================================================ */

static void offtout(int64_t x, uint8_t *buf) {
    int64_t y;

    if (x < 0) y = -x; else y = x;

    buf[0] = y % 256; y -= buf[0];
    y = y / 256; buf[1] = y % 256; y -= buf[1];
    y = y / 256; buf[2] = y % 256; y -= buf[2];
    y = y / 256; buf[3] = y % 256; y -= buf[3];
    y = y / 256; buf[4] = y % 256; y -= buf[4];
    y = y / 256; buf[5] = y % 256; y -= buf[5];
    y = y / 256; buf[6] = y % 256; y -= buf[6];
    y = y / 256; buf[7] = y % 256;

    if (x < 0) buf[7] |= 0x80;
}


/* ============================================================================
 * BsDiff 主函数
 * ============================================================================ */

int bsdiff_generate(
    const uint8_t* old,
    int64_t oldsize,
    const uint8_t* new,
    int64_t newsize,
    const char* patchPath,
    ProgressCallback callback,
    void* userData
) {
    int64_t *I, *V;
    int64_t scan, pos, len;
    int64_t lastscan, lastpos, lastoffset;
    int64_t oldscore, scsc;
    int64_t s, Sf, lenf, Sb, lenb;
    int64_t overlap, Ss, lens;
    int64_t i;
    uint8_t *db, *eb;
    uint8_t buf[8];
    uint8_t header[32];
    int64_t dblen, eblen;
    FILE *pf;
    
    /* 参数检查 */
    if (old == NULL || new == NULL || patchPath == NULL) {
        return PE_ERROR_INVALID_PARAM;
    }
    
    /* 检查取消标志 */
    if (pe_is_cancelled()) {
        return PE_ERROR_CANCELLED;
    }

    /* 分配内存 */
    I = (int64_t*)malloc((oldsize + 1) * sizeof(int64_t));
    V = (int64_t*)malloc((oldsize + 1) * sizeof(int64_t));
    if (I == NULL || V == NULL) {
        free(I);
        free(V);
        return PE_ERROR_OUT_OF_MEMORY;
    }

    /* 构建后缀数组 */
    if (callback) callback(0, 100, userData);
    qsufsort(I, V, old, oldsize);
    free(V);
    
    if (pe_is_cancelled()) {
        free(I);
        return PE_ERROR_CANCELLED;
    }
    if (callback) callback(30, 100, userData);

    /* 分配差异和额外数据缓冲区 */
    db = (uint8_t*)malloc(newsize + 1);
    eb = (uint8_t*)malloc(newsize + 1);
    if (db == NULL || eb == NULL) {
        free(I);
        free(db);
        free(eb);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    dblen = 0;
    eblen = 0;

    /* 打开输出文件 */
    pf = fopen(patchPath, "wb");
    if (pf == NULL) {
        free(I);
        free(db);
        free(eb);
        return PE_ERROR_FILE_WRITE;
    }

    /* 写入头部占位符 */
    memcpy(header, BSDIFF_MAGIC, 8);
    offtout(0, header + 8);   /* ctrl block size placeholder */
    offtout(0, header + 16);  /* diff block size placeholder */
    offtout(newsize, header + 24);
    if (fwrite(header, 1, 32, pf) != 32) {
        fclose(pf);
        free(I);
        free(db);
        free(eb);
        return PE_ERROR_FILE_WRITE;
    }

    /* 扫描并生成差异 */
    scan = 0;
    len = 0;
    pos = 0;
    lastscan = 0;
    lastpos = 0;
    lastoffset = 0;
    
    while (scan < newsize) {
        if (pe_is_cancelled()) {
            fclose(pf);
            free(I);
            free(db);
            free(eb);
            return PE_ERROR_CANCELLED;
        }
        
        oldscore = 0;

        for (scsc = scan += len; scan < newsize; scan++) {
            len = bsdiff_search(I, old, oldsize, new + scan, newsize - scan,
                               0, oldsize, &pos);

            for (; scsc < scan + len; scsc++) {
                if ((scsc + lastoffset < oldsize) &&
                    (old[scsc + lastoffset] == new[scsc]))
                    oldscore++;
            }

            if (((len == oldscore) && (len != 0)) ||
                (len > oldscore + 8)) break;

            if ((scan + lastoffset < oldsize) &&
                (old[scan + lastoffset] == new[scan]))
                oldscore--;
        }

        if ((len != oldscore) || (scan == newsize)) {
            s = 0;
            Sf = 0;
            lenf = 0;
            for (i = 0; (lastscan + i < scan) && (lastpos + i < oldsize);) {
                if (old[lastpos + i] == new[lastscan + i]) s++;
                i++;
                if (s * 2 - i > Sf * 2 - lenf) {
                    Sf = s;
                    lenf = i;
                }
            }

            lenb = 0;
            if (scan < newsize) {
                s = 0;
                Sb = 0;
                for (i = 1; (scan >= lastscan + i) && (pos >= i); i++) {
                    if (old[pos - i] == new[scan - i]) s++;
                    if (s * 2 - i > Sb * 2 - lenb) {
                        Sb = s;
                        lenb = i;
                    }
                }
            }

            if (lastscan + lenf > scan - lenb) {
                overlap = (lastscan + lenf) - (scan - lenb);
                s = 0;
                Ss = 0;
                lens = 0;
                for (i = 0; i < overlap; i++) {
                    if (new[lastscan + lenf - overlap + i] ==
                        old[lastpos + lenf - overlap + i]) s++;
                    if (new[scan - lenb + i] == old[pos - lenb + i]) s--;
                    if (s > Ss) {
                        Ss = s;
                        lens = i + 1;
                    }
                }

                lenf += lens - overlap;
                lenb -= lens;
            }

            /* 写入控制数据 */
            for (i = 0; i < lenf; i++)
                db[dblen + i] = new[lastscan + i] - old[lastpos + i];
            for (i = 0; i < (scan - lenb) - (lastscan + lenf); i++)
                eb[eblen + i] = new[lastscan + lenf + i];

            dblen += lenf;
            eblen += (scan - lenb) - (lastscan + lenf);

            /* 写入控制块 */
            offtout(lenf, buf);
            fwrite(buf, 1, 8, pf);
            offtout((scan - lenb) - (lastscan + lenf), buf);
            fwrite(buf, 1, 8, pf);
            offtout((pos - lenb) - (lastpos + lenf), buf);
            fwrite(buf, 1, 8, pf);

            lastscan = scan - lenb;
            lastpos = pos - lenb;
            lastoffset = pos - scan;
        }
        
        /* 更新进度 */
        if (callback && newsize > 0) {
            int64_t progress = 30 + (scan * 60 / newsize);
            callback(progress, 100, userData);
        }
    }

    /* 写入差异块和额外块 */
    if (dblen > 0) fwrite(db, 1, (size_t)dblen, pf);
    if (eblen > 0) fwrite(eb, 1, (size_t)eblen, pf);

    /* 更新头部 */
    fseek(pf, 0, SEEK_SET);
    memcpy(header, BSDIFF_MAGIC, 8);
    offtout(0, header + 8);      /* ctrl size - simplified */
    offtout(dblen, header + 16); /* diff size */
    offtout(newsize, header + 24);
    fwrite(header, 1, 32, pf);

    fclose(pf);
    free(I);
    free(db);
    free(eb);

    if (callback) callback(100, 100, userData);
    return PE_SUCCESS;
}


/* ============================================================================
 * 从文件生成 BsDiff 补丁
 * ============================================================================ */

int bsdiff_generate_file(
    const char* oldPath,
    const char* newPath,
    const char* patchPath,
    ProgressCallback callback,
    void* userData
) {
    FILE *f;
    uint8_t *old, *new;
    int64_t oldsize, newsize;
    int result;

    /* 参数检查 */
    if (oldPath == NULL || newPath == NULL || patchPath == NULL) {
        return PE_ERROR_INVALID_PARAM;
    }

    /* 读取旧文件 */
    f = fopen(oldPath, "rb");
    if (f == NULL) {
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open old file");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    fseek(f, 0, SEEK_END);
    oldsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    old = (uint8_t*)malloc((size_t)(oldsize + 1));
    if (old == NULL) {
        fclose(f);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    if (fread(old, 1, (size_t)oldsize, f) != (size_t)oldsize) {
        fclose(f);
        free(old);
        return PE_ERROR_FILE_READ;
    }
    fclose(f);

    /* 读取新文件 */
    f = fopen(newPath, "rb");
    if (f == NULL) {
        free(old);
        pe_set_error(PE_ERROR_FILE_NOT_FOUND, "Cannot open new file");
        return PE_ERROR_FILE_NOT_FOUND;
    }
    fseek(f, 0, SEEK_END);
    newsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    new = (uint8_t*)malloc((size_t)(newsize + 1));
    if (new == NULL) {
        fclose(f);
        free(old);
        return PE_ERROR_OUT_OF_MEMORY;
    }
    if (fread(new, 1, (size_t)newsize, f) != (size_t)newsize) {
        fclose(f);
        free(old);
        free(new);
        return PE_ERROR_FILE_READ;
    }
    fclose(f);

    /* 生成补丁 */
    result = bsdiff_generate(old, oldsize, new, newsize, patchPath, callback, userData);

    free(old);
    free(new);
    return result;
}
