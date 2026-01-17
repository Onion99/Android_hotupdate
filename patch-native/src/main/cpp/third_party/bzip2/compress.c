/*-------------------------------------------------------------*/
/*--- Compression machinery (not combiner)                  ---*/
/*---                                            compress.c ---*/
/*-------------------------------------------------------------*/

/* ------------------------------------------------------------------
   This file is part of bzip2/libbzip2.
   bzip2/libbzip2 version 1.0.8 of 13 July 2019
   Copyright (C) 1996-2019 Julian Seward <jseward@acm.org>
   ------------------------------------------------------------------ */

#include "bzlib_private.h"

#define BZ_LESSER_ICOST   0
#define BZ_GREATER_ICOST  15

/*---------------------------------------------------*/
/* Bit stream writing */
/*---------------------------------------------------*/

void BZ2_bsInitWrite(EState* s) {
   s->bsLive = 0;
   s->bsBuff = 0;
}

static void bsW(EState* s, Int32 n, UInt32 v) {
   while (s->bsLive >= 8) {
      s->zbits[s->numZ] = (UChar)(s->bsBuff >> 24);
      s->numZ++;
      s->bsBuff <<= 8;
      s->bsLive -= 8;
   }
   s->bsBuff |= (v << (32 - s->bsLive - n));
   s->bsLive += n;
}

static void bsFinishWrite(EState* s) {
   while (s->bsLive > 0) {
      s->zbits[s->numZ] = (UChar)(s->bsBuff >> 24);
      s->numZ++;
      s->bsBuff <<= 8;
      s->bsLive -= 8;
   }
}

/*---------------------------------------------------*/
/* Generate MTF values */
/*---------------------------------------------------*/

static void generateMTFValues(EState* s) {
   UChar yy[256];
   Int32 i, j;
   Int32 zPend;
   Int32 wr;
   Int32 EOB;

   /* Set up initial MTF state */
   for (i = 0; i < 256; i++) yy[i] = (UChar)i;

   wr = 0;
   zPend = 0;
   EOB = s->nInUse + 1;

   for (i = 0; i < s->nblock; i++) {
      UChar ll_i = s->block[s->ptr[i]];
      j = 0;
      while (yy[j] != ll_i) j++;

      if (j == 0) {
         zPend++;
      } else {
         if (zPend > 0) {
            zPend--;
            while (True) {
               if (zPend & 1) {
                  s->mtfv[wr] = BZ_RUNB; wr++;
                  s->mtfFreq[BZ_RUNB]++;
               } else {
                  s->mtfv[wr] = BZ_RUNA; wr++;
                  s->mtfFreq[BZ_RUNA]++;
               }
               if (zPend < 2) break;
               zPend = (zPend - 2) / 2;
            }
            zPend = 0;
         }
         s->mtfv[wr] = j + 1; wr++;
         s->mtfFreq[j + 1]++;

         /* Update MTF state */
         UChar tmp = yy[j];
         while (j > 0) { yy[j] = yy[j - 1]; j--; }
         yy[0] = tmp;
      }
   }

   if (zPend > 0) {
      zPend--;
      while (True) {
         if (zPend & 1) {
            s->mtfv[wr] = BZ_RUNB; wr++;
            s->mtfFreq[BZ_RUNB]++;
         } else {
            s->mtfv[wr] = BZ_RUNA; wr++;
            s->mtfFreq[BZ_RUNA]++;
         }
         if (zPend < 2) break;
         zPend = (zPend - 2) / 2;
      }
   }

   s->mtfv[wr] = EOB; wr++;
   s->mtfFreq[EOB]++;

   s->nMTF = wr;
}


/*---------------------------------------------------*/
/* Compress a block */
/*---------------------------------------------------*/

void BZ2_compressBlock(EState* s, Bool is_last_block) {
   Int32 i, j, gs, ge, totc, bt, bc, iter;
   Int32 nSelectors, alphaSize, minLen, maxLen;
   Int32 nGroups;

   if (s->nblock == 0) {
      if (is_last_block) {
         /* Write stream trailer */
         bsW(s, 8, 0x17);
         bsW(s, 8, 0x72);
         bsW(s, 8, 0x45);
         bsW(s, 8, 0x38);
         bsW(s, 8, 0x50);
         bsW(s, 8, 0x90);
         bsW(s, 32, s->combinedCRC);
         bsFinishWrite(s);
      }
      return;
   }

   /* Set up in-use table */
   s->nInUse = 0;
   for (i = 0; i < 256; i++)
      if (s->inUse[i]) {
         s->unseqToSeq[i] = s->nInUse;
         s->nInUse++;
      }

   alphaSize = s->nInUse + 2;

   /* Initialize MTF frequency table */
   for (i = 0; i < BZ_MAX_ALPHA_SIZE; i++) s->mtfFreq[i] = 0;

   /* Generate MTF values */
   generateMTFValues(s);

   /* Decide how many coding tables to use */
   if (s->nMTF < 200)  nGroups = 2; else
   if (s->nMTF < 600)  nGroups = 3; else
   if (s->nMTF < 1200) nGroups = 4; else
   if (s->nMTF < 2400) nGroups = 5; else
                       nGroups = 6;

   /* Generate initial coding tables */
   {
      Int32 nPart, remF, tFreq, aFreq;

      nPart = nGroups;
      remF = s->nMTF;
      gs = 0;
      while (nPart > 0) {
         tFreq = remF / nPart;
         ge = gs - 1;
         aFreq = 0;
         while (aFreq < tFreq && ge < alphaSize - 1) {
            ge++;
            aFreq += s->mtfFreq[ge];
         }

         if (ge > gs && nPart != nGroups && nPart != 1 &&
             ((nGroups - nPart) % 2 == 1)) {
            aFreq -= s->mtfFreq[ge];
            ge--;
         }

         for (i = 0; i < alphaSize; i++)
            if (i >= gs && i <= ge)
               s->len[nPart - 1][i] = BZ_LESSER_ICOST;
            else
               s->len[nPart - 1][i] = BZ_GREATER_ICOST;

         nPart--;
         gs = ge + 1;
         remF -= aFreq;
      }
   }

   /* Iterate to improve coding tables */
   for (iter = 0; iter < BZ_N_ITERS; iter++) {
      for (i = 0; i < nGroups; i++) s->rfreq[i][0] = 0;

      nSelectors = 0;
      totc = 0;
      gs = 0;
      while (True) {
         if (gs >= s->nMTF) break;
         ge = gs + BZ_G_SIZE - 1;
         if (ge >= s->nMTF) ge = s->nMTF - 1;

         /* Find best table for this group */
         bt = 0; bc = 999999999;
         for (i = 0; i < nGroups; i++) {
            Int32 cost = 0;
            for (j = gs; j <= ge; j++)
               cost += s->len[i][s->mtfv[j]];
            if (cost < bc) { bc = cost; bt = i; }
         }
         totc += bc;
         s->selector[nSelectors] = bt;
         nSelectors++;

         for (j = gs; j <= ge; j++)
            s->rfreq[bt][s->mtfv[j]]++;

         gs = ge + 1;
      }

      /* Recompute coding tables */
      for (i = 0; i < nGroups; i++)
         BZ2_hbMakeCodeLengths(&(s->len[i][0]), &(s->rfreq[i][0]), alphaSize, 17);
   }

   /* Assign actual codes */
   for (i = 0; i < nGroups; i++) {
      minLen = 32;
      maxLen = 0;
      for (j = 0; j < alphaSize; j++) {
         if (s->len[i][j] > maxLen) maxLen = s->len[i][j];
         if (s->len[i][j] < minLen) minLen = s->len[i][j];
      }
      BZ2_hbAssignCodes(&(s->code[i][0]), &(s->len[i][0]), minLen, maxLen, alphaSize);
   }

   /* Write block header */
   bsW(s, 8, 0x31);
   bsW(s, 8, 0x41);
   bsW(s, 8, 0x59);
   bsW(s, 8, 0x26);
   bsW(s, 8, 0x53);
   bsW(s, 8, 0x59);

   bsW(s, 32, s->blockCRC);
   bsW(s, 1, 0);
   bsW(s, 24, s->origPtr);

   /* Write in-use bitmap */
   {
      Bool inUse16[16];
      for (i = 0; i < 16; i++) {
         inUse16[i] = False;
         for (j = 0; j < 16; j++)
            if (s->inUse[i * 16 + j]) inUse16[i] = True;
      }
      for (i = 0; i < 16; i++)
         bsW(s, 1, inUse16[i] ? 1 : 0);
      for (i = 0; i < 16; i++)
         if (inUse16[i])
            for (j = 0; j < 16; j++)
               bsW(s, 1, s->inUse[i * 16 + j] ? 1 : 0);
   }

   /* Write number of trees and selectors */
   bsW(s, 3, nGroups);
   bsW(s, 15, nSelectors);

   /* Write selectors */
   {
      UChar pos[BZ_N_GROUPS], ll_i, tmp;
      for (i = 0; i < nGroups; i++) pos[i] = i;
      for (i = 0; i < nSelectors; i++) {
         ll_i = s->selector[i];
         j = 0;
         while (pos[j] != ll_i) j++;
         while (j > 0) { tmp = pos[j - 1]; pos[j - 1] = pos[j]; pos[j] = tmp; j--; }
         for (j = 0; j < (Int32)ll_i + 1; j++) bsW(s, 1, 1);
         bsW(s, 1, 0);
      }
   }

   /* Write coding tables */
   for (i = 0; i < nGroups; i++) {
      Int32 curr = s->len[i][0];
      bsW(s, 5, curr);
      for (j = 0; j < alphaSize; j++) {
         while (curr < s->len[i][j]) { bsW(s, 2, 2); curr++; }
         while (curr > s->len[i][j]) { bsW(s, 2, 3); curr--; }
         bsW(s, 1, 0);
      }
   }

   /* Write compressed data */
   {
      Int32 selCtr = 0;
      gs = 0;
      while (True) {
         if (gs >= s->nMTF) break;
         ge = gs + BZ_G_SIZE - 1;
         if (ge >= s->nMTF) ge = s->nMTF - 1;
         for (i = gs; i <= ge; i++) {
            bsW(s, s->len[s->selector[selCtr]][s->mtfv[i]],
                   s->code[s->selector[selCtr]][s->mtfv[i]]);
         }
         gs = ge + 1;
         selCtr++;
      }
   }

   if (is_last_block) {
      bsW(s, 8, 0x17);
      bsW(s, 8, 0x72);
      bsW(s, 8, 0x45);
      bsW(s, 8, 0x38);
      bsW(s, 8, 0x50);
      bsW(s, 8, 0x90);
      bsW(s, 32, s->combinedCRC);
      bsFinishWrite(s);
   }
}
