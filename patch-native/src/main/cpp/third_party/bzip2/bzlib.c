/*-------------------------------------------------------------*/
/*--- Library top-level functions.                          ---*/
/*---                                               bzlib.c ---*/
/*-------------------------------------------------------------*/

/* ------------------------------------------------------------------
   This file is part of bzip2/libbzip2.
   bzip2/libbzip2 version 1.0.8 of 13 July 2019
   Copyright (C) 1996-2019 Julian Seward <jseward@acm.org>
   ------------------------------------------------------------------ */

#include "bzlib_private.h"
#include <string.h>

/*---------------------------------------------------*/
/*--- Compression stuff                           ---*/
/*---------------------------------------------------*/

static void* default_bzalloc(void* opaque, int items, int size) {
   (void)opaque;
   return malloc((size_t)items * (size_t)size);
}

static void default_bzfree(void* opaque, void* addr) {
   (void)opaque;
   free(addr);
}

static void prepare_new_block(EState* s) {
   s->nblock = 0;
   s->numZ = 0;
   s->state_out_pos = 0;
   BZ_INITIALISE_CRC(s->blockCRC);
   for (int i = 0; i < 256; i++) s->inUse[i] = False;
   s->blockNo++;
}

static Bool isempty_RL(EState* s) {
   return (s->state_in_ch < 256 && s->state_in_len == 0);
}


/*---------------------------------------------------*/
int BZ2_bzCompressInit(bz_stream* strm, int blockSize100k, int verbosity, int workFactor) {
   EState* s;

   if (strm == NULL || blockSize100k < 1 || blockSize100k > 9 ||
       workFactor < 0 || workFactor > 250)
      return BZ_PARAM_ERROR;

   if (workFactor == 0) workFactor = 30;
   if (strm->bzalloc == NULL) strm->bzalloc = default_bzalloc;
   if (strm->bzfree == NULL) strm->bzfree = default_bzfree;

   s = (EState*)BZALLOC(sizeof(EState));
   if (s == NULL) return BZ_MEM_ERROR;
   s->strm = strm;

   s->arr1 = NULL;
   s->arr2 = NULL;
   s->ftab = NULL;

   int n = 100000 * blockSize100k;
   s->arr1 = (UInt32*)BZALLOC(n * sizeof(UInt32));
   s->arr2 = (UInt32*)BZALLOC((n + BZ_N_OVERSHOOT) * sizeof(UInt32));
   s->ftab = (UInt32*)BZALLOC(65537 * sizeof(UInt32));

   if (s->arr1 == NULL || s->arr2 == NULL || s->ftab == NULL) {
      if (s->arr1 != NULL) BZFREE(s->arr1);
      if (s->arr2 != NULL) BZFREE(s->arr2);
      if (s->ftab != NULL) BZFREE(s->ftab);
      BZFREE(s);
      return BZ_MEM_ERROR;
   }

   s->blockNo = 0;
   s->state = BZ_S_INPUT;
   s->mode = BZ_M_RUNNING;
   s->combinedCRC = 0;
   s->blockSize100k = blockSize100k;
   s->nblockMAX = 100000 * blockSize100k - 19;
   s->verbosity = verbosity;
   s->workFactor = workFactor;

   s->block = (UChar*)s->arr2;
   s->mtfv = (UInt16*)s->arr1;
   s->zbits = NULL;
   s->ptr = (UInt32*)s->arr1;

   strm->state = s;
   strm->total_in_lo32 = 0;
   strm->total_in_hi32 = 0;
   strm->total_out_lo32 = 0;
   strm->total_out_hi32 = 0;
   prepare_new_block(s);
   return BZ_OK;
}


/*---------------------------------------------------*/
int BZ2_bzCompress(bz_stream* strm, int action) {
   EState* s;
   if (strm == NULL) return BZ_PARAM_ERROR;
   s = (EState*)strm->state;
   if (s == NULL) return BZ_PARAM_ERROR;
   if (s->strm != strm) return BZ_PARAM_ERROR;

   switch (s->mode) {
      case BZ_M_IDLE:
         return BZ_SEQUENCE_ERROR;

      case BZ_M_RUNNING:
         if (action == BZ_RUN) {
            /* Add input to block */
            while (strm->avail_in > 0 && s->nblock < s->nblockMAX) {
               UChar ch = (UChar)(*strm->next_in);
               strm->next_in++;
               strm->avail_in--;
               strm->total_in_lo32++;
               if (strm->total_in_lo32 == 0) strm->total_in_hi32++;
               
               s->block[s->nblock] = ch;
               s->nblock++;
               s->inUse[ch] = True;
               BZ_UPDATE_CRC(s->blockCRC, ch);
            }
            return BZ_RUN_OK;
         }
         else if (action == BZ_FLUSH) {
            s->mode = BZ_M_FLUSHING;
            goto flush;
         }
         else if (action == BZ_FINISH) {
            s->mode = BZ_M_FINISHING;
            goto finish;
         }
         return BZ_PARAM_ERROR;

      case BZ_M_FLUSHING:
      flush:
         if (s->nblock > 0) {
            BZ_FINALISE_CRC(s->blockCRC);
            s->combinedCRC = (s->combinedCRC << 1) | (s->combinedCRC >> 31);
            s->combinedCRC ^= s->blockCRC;
            BZ2_blockSort(s);
            BZ2_compressBlock(s, False);
            prepare_new_block(s);
         }
         s->mode = BZ_M_RUNNING;
         return BZ_FLUSH_OK;

      case BZ_M_FINISHING:
      finish:
         if (s->nblock > 0) {
            BZ_FINALISE_CRC(s->blockCRC);
            s->combinedCRC = (s->combinedCRC << 1) | (s->combinedCRC >> 31);
            s->combinedCRC ^= s->blockCRC;
            BZ2_blockSort(s);
            BZ2_compressBlock(s, True);
         }
         return BZ_STREAM_END;
   }
   return BZ_OK;
}

/*---------------------------------------------------*/
int BZ2_bzCompressEnd(bz_stream* strm) {
   EState* s;
   if (strm == NULL) return BZ_PARAM_ERROR;
   s = (EState*)strm->state;
   if (s == NULL) return BZ_PARAM_ERROR;
   if (s->strm != strm) return BZ_PARAM_ERROR;

   if (s->arr1 != NULL) BZFREE(s->arr1);
   if (s->arr2 != NULL) BZFREE(s->arr2);
   if (s->ftab != NULL) BZFREE(s->ftab);
   BZFREE(s);

   strm->state = NULL;
   return BZ_OK;
}


/*---------------------------------------------------*/
/*--- Decompression stuff                         ---*/
/*---------------------------------------------------*/

int BZ2_bzDecompressInit(bz_stream* strm, int verbosity, int small) {
   DState* s;

   if (strm == NULL) return BZ_PARAM_ERROR;
   if (small != 0 && small != 1) return BZ_PARAM_ERROR;
   if (verbosity < 0 || verbosity > 4) return BZ_PARAM_ERROR;

   if (strm->bzalloc == NULL) strm->bzalloc = default_bzalloc;
   if (strm->bzfree == NULL) strm->bzfree = default_bzfree;

   s = (DState*)BZALLOC(sizeof(DState));
   if (s == NULL) return BZ_MEM_ERROR;
   s->strm = strm;
   s->state = BZ_X_MAGIC_1;
   s->bsLive = 0;
   s->bsBuff = 0;
   s->calculatedCombinedCRC = 0;
   strm->state = s;
   strm->total_in_lo32 = 0;
   strm->total_in_hi32 = 0;
   strm->total_out_lo32 = 0;
   strm->total_out_hi32 = 0;
   s->smallDecompress = (Bool)small;
   s->ll4 = NULL;
   s->ll16 = NULL;
   s->tt = NULL;
   s->currBlockNo = 0;
   s->verbosity = verbosity;

   return BZ_OK;
}

/*---------------------------------------------------*/
int BZ2_bzDecompress(bz_stream* strm) {
   DState* s;
   if (strm == NULL) return BZ_PARAM_ERROR;
   s = (DState*)strm->state;
   if (s == NULL) return BZ_PARAM_ERROR;
   if (s->strm != strm) return BZ_PARAM_ERROR;

   while (True) {
      if (s->state == BZ_X_IDLE) return BZ_SEQUENCE_ERROR;
      if (s->state == BZ_X_OUTPUT) {
         /* Output decompressed data */
         while (strm->avail_out > 0 && s->nblock_used < s->nblock_used) {
            *strm->next_out = s->state_out_ch;
            strm->next_out++;
            strm->avail_out--;
            strm->total_out_lo32++;
            if (strm->total_out_lo32 == 0) strm->total_out_hi32++;
         }
         if (s->nblock_used >= s->nblock_used) {
            return BZ_STREAM_END;
         }
         return BZ_OK;
      }
      
      /* Need more input */
      if (strm->avail_in == 0) return BZ_OK;
      
      int ret = BZ2_decompress(s);
      if (ret == True) return BZ_STREAM_END;
      if (ret != False) return ret;
   }
}

/*---------------------------------------------------*/
int BZ2_bzDecompressEnd(bz_stream* strm) {
   DState* s;
   if (strm == NULL) return BZ_PARAM_ERROR;
   s = (DState*)strm->state;
   if (s == NULL) return BZ_PARAM_ERROR;
   if (s->strm != strm) return BZ_PARAM_ERROR;

   if (s->tt != NULL) BZFREE(s->tt);
   if (s->ll16 != NULL) BZFREE(s->ll16);
   if (s->ll4 != NULL) BZFREE(s->ll4);
   BZFREE(s);
   strm->state = NULL;
   return BZ_OK;
}


/*---------------------------------------------------*/
/*--- High level interface                        ---*/
/*---------------------------------------------------*/

int BZ2_bzBuffToBuffCompress(
      char* dest, unsigned int* destLen,
      char* source, unsigned int sourceLen,
      int blockSize100k, int verbosity, int workFactor) {
   bz_stream strm;
   int ret;

   if (dest == NULL || destLen == NULL || source == NULL ||
       blockSize100k < 1 || blockSize100k > 9 ||
       verbosity < 0 || verbosity > 4 ||
       workFactor < 0 || workFactor > 250)
      return BZ_PARAM_ERROR;

   if (workFactor == 0) workFactor = 30;
   strm.bzalloc = NULL;
   strm.bzfree = NULL;
   strm.opaque = NULL;
   ret = BZ2_bzCompressInit(&strm, blockSize100k, verbosity, workFactor);
   if (ret != BZ_OK) return ret;

   strm.next_in = source;
   strm.next_out = dest;
   strm.avail_in = sourceLen;
   strm.avail_out = *destLen;

   ret = BZ2_bzCompress(&strm, BZ_FINISH);

   if (ret == BZ_FINISH_OK) goto output;
   if (ret != BZ_STREAM_END) goto errhandler;

output:
   *destLen -= strm.avail_out;
   BZ2_bzCompressEnd(&strm);
   return BZ_OK;

errhandler:
   BZ2_bzCompressEnd(&strm);
   return ret;
}

/*---------------------------------------------------*/
int BZ2_bzBuffToBuffDecompress(
      char* dest, unsigned int* destLen,
      char* source, unsigned int sourceLen,
      int small, int verbosity) {
   bz_stream strm;
   int ret;

   if (dest == NULL || destLen == NULL || source == NULL ||
       (small != 0 && small != 1) ||
       verbosity < 0 || verbosity > 4)
      return BZ_PARAM_ERROR;

   strm.bzalloc = NULL;
   strm.bzfree = NULL;
   strm.opaque = NULL;
   ret = BZ2_bzDecompressInit(&strm, verbosity, small);
   if (ret != BZ_OK) return ret;

   strm.next_in = source;
   strm.next_out = dest;
   strm.avail_in = sourceLen;
   strm.avail_out = *destLen;

   ret = BZ2_bzDecompress(&strm);

   if (ret == BZ_OK) goto output;
   if (ret != BZ_STREAM_END) goto errhandler;

output:
   *destLen -= strm.avail_out;
   BZ2_bzDecompressEnd(&strm);
   return BZ_OK;

errhandler:
   BZ2_bzDecompressEnd(&strm);
   return ret;
}

/*---------------------------------------------------*/
const char* BZ2_bzlibVersion(void) {
   return BZ_VERSION;
}

/*---------------------------------------------------*/
#ifndef BZ_NO_STDIO
void BZ2_bz__AssertH__fail(int errcode) {
   fprintf(stderr, "\n\nbzip2/libbzip2: internal error number %d.\n", errcode);
   exit(3);
}
#endif

void bz_internal_error(int errcode) {
   (void)errcode;
}
