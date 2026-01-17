/*-------------------------------------------------------------*/
/*--- Block sorting machinery                               ---*/
/*---                                           blocksort.c ---*/
/*-------------------------------------------------------------*/

/* ------------------------------------------------------------------
   This file is part of bzip2/libbzip2.
   bzip2/libbzip2 version 1.0.8 of 13 July 2019
   Copyright (C) 1996-2019 Julian Seward <jseward@acm.org>
   ------------------------------------------------------------------ */

#include "bzlib_private.h"

/*---------------------------------------------*/
/* Fallback O(N log(N)^2) sorting algorithm */
/*---------------------------------------------*/

static void fallbackSimpleSort(UInt32* fmap, UInt32* eclass, Int32 lo, Int32 hi) {
   Int32 i, j, tmp;
   UInt32 ec_tmp;

   if (lo == hi) return;

   if (hi - lo > 3) {
      for (i = hi - 4; i >= lo; i--) {
         tmp = fmap[i];
         ec_tmp = eclass[tmp];
         for (j = i + 4; j <= hi && ec_tmp > eclass[fmap[j]]; j += 4)
            fmap[j - 4] = fmap[j];
         fmap[j - 4] = tmp;
      }
   }

   for (i = hi - 1; i >= lo; i--) {
      tmp = fmap[i];
      ec_tmp = eclass[tmp];
      for (j = i + 1; j <= hi && ec_tmp > eclass[fmap[j]]; j++)
         fmap[j - 1] = fmap[j];
      fmap[j - 1] = tmp;
   }
}

#define fswap(zz1, zz2) \
   { Int32 zztmp = zz1; zz1 = zz2; zz2 = zztmp; }

#define fvswap(zzp1, zzp2, zzn)       \
{                                     \
   Int32 yyp1 = (zzp1);               \
   Int32 yyp2 = (zzp2);               \
   Int32 yyn  = (zzn);                \
   while (yyn > 0) {                  \
      fswap(fmap[yyp1], fmap[yyp2]);  \
      yyp1++; yyp2++; yyn--;          \
   }                                  \
}

#define fmin(a,b) ((a) < (b)) ? (a) : (b)

#define fpush(lz,hz) { stackLo[sp] = lz; \
                       stackHi[sp] = hz; \
                       sp++; }

#define fpop(lz,hz) { sp--;              \
                      lz = stackLo[sp];  \
                      hz = stackHi[sp]; }

#define FALLBACK_QSORT_SMALL_THRESH 10
#define FALLBACK_QSORT_STACK_SIZE   100


static void fallbackQSort3(UInt32* fmap, UInt32* eclass, Int32 loSt, Int32 hiSt) {
   Int32 unLo, unHi, ltLo, gtHi, n, m;
   Int32 sp, lo, hi;
   UInt32 med, r, r3;
   Int32 stackLo[FALLBACK_QSORT_STACK_SIZE];
   Int32 stackHi[FALLBACK_QSORT_STACK_SIZE];

   r = 0;
   sp = 0;
   fpush(loSt, hiSt);

   while (sp > 0) {
      AssertH(sp < FALLBACK_QSORT_STACK_SIZE - 1, 1004);

      fpop(lo, hi);
      if (hi - lo < FALLBACK_QSORT_SMALL_THRESH) {
         fallbackSimpleSort(fmap, eclass, lo, hi);
         continue;
      }

      r = ((r * 7621) + 1) % 32768;
      r3 = r % 3;
      if (r3 == 0) med = eclass[fmap[lo]]; else
      if (r3 == 1) med = eclass[fmap[(lo + hi) >> 1]]; else
                   med = eclass[fmap[hi]];

      unLo = ltLo = lo;
      unHi = gtHi = hi;

      while (1) {
         while (1) {
            if (unLo > unHi) break;
            n = (Int32)eclass[fmap[unLo]] - (Int32)med;
            if (n == 0) { fswap(fmap[unLo], fmap[ltLo]); ltLo++; unLo++; continue; }
            if (n > 0) break;
            unLo++;
         }
         while (1) {
            if (unLo > unHi) break;
            n = (Int32)eclass[fmap[unHi]] - (Int32)med;
            if (n == 0) { fswap(fmap[unHi], fmap[gtHi]); gtHi--; unHi--; continue; }
            if (n < 0) break;
            unHi--;
         }
         if (unLo > unHi) break;
         fswap(fmap[unLo], fmap[unHi]); unLo++; unHi--;
      }

      AssertD(unHi == unLo - 1, "fallbackQSort3(2)");

      if (gtHi < ltLo) continue;

      n = fmin(ltLo - lo, unLo - ltLo); fvswap(lo, unLo - n, n);
      m = fmin(hi - gtHi, gtHi - unHi); fvswap(unLo, hi - m + 1, m);

      n = lo + unLo - ltLo - 1;
      m = hi - (gtHi - unHi) + 1;

      if (n - lo > hi - m) {
         fpush(lo, n);
         fpush(m, hi);
      } else {
         fpush(m, hi);
         fpush(lo, n);
      }
   }
}

#undef fmin
#undef fpush
#undef fpop
#undef fswap
#undef fvswap
#undef FALLBACK_QSORT_SMALL_THRESH
#undef FALLBACK_QSORT_STACK_SIZE


/*---------------------------------------------*/
/* Main block sorting function */
/*---------------------------------------------*/

static void fallbackSort(UInt32* fmap, UInt32* eclass, UInt32* bhtab, Int32 nblock, Int32 verb) {
   Int32 ftab[257];
   Int32 ftabCopy[256];
   Int32 H, i, j, k, l, r, cc, cc1;
   Int32 nNotDone;
   Int32 nBhtab;
   UChar* eclass8 = (UChar*)eclass;

   (void)verb;

   /* Initial 1-char radix sort */
   for (i = 0; i < 257; i++) ftab[i] = 0;
   for (i = 0; i < nblock; i++) ftab[eclass8[i]]++;
   for (i = 0; i < 256; i++) ftabCopy[i] = ftab[i];
   for (i = 1; i < 257; i++) ftab[i] += ftab[i - 1];

   for (i = 0; i < nblock; i++) {
      j = eclass8[i];
      k = ftab[j] - 1;
      ftab[j] = k;
      fmap[k] = i;
   }

   nBhtab = 2 + (nblock / 32);
   for (i = 0; i < nBhtab; i++) bhtab[i] = 0;
   for (i = 0; i < 256; i++) bhtab[(ftab[i]) >> 5] |= (1 << ((ftab[i]) & 31));

   /* Inductively refine the buckets */
   for (H = 1; H <= nblock; H *= 2) {
      j = 0;
      for (i = 0; i < nblock; i++) {
         if ((bhtab[i >> 5] & (1 << (i & 31))) != 0) j = i;
         k = fmap[i] - H; if (k < 0) k += nblock;
         eclass[k] = j;
      }

      nNotDone = 0;
      r = -1;
      while (1) {
         k = r + 1;
         while ((bhtab[k >> 5] & (1 << (k & 31))) != 0 && k < nblock) k++;
         l = k - 1;
         if (l >= nblock) break;
         while ((bhtab[k >> 5] & (1 << (k & 31))) == 0 && k < nblock) k++;
         r = k - 1;
         if (r >= nblock) break;

         nNotDone += (r - l + 1);
         fallbackQSort3(fmap, eclass, l, r);

         cc = -1;
         for (i = l; i <= r; i++) {
            cc1 = eclass[fmap[i]];
            if (cc != cc1) { bhtab[i >> 5] |= (1 << (i & 31)); cc = cc1; }
         }
      }

      if (nNotDone == 0) break;
   }
}


/*---------------------------------------------*/
/* Main entry point for block sorting */
/*---------------------------------------------*/

void BZ2_blockSort(EState* s) {
   UInt32* ptr = s->ptr;
   UChar* block = s->block;
   UInt32* ftab = s->ftab;
   Int32 nblock = s->nblock;
   Int32 verb = s->verbosity;
   Int32 wfact = s->workFactor;
   UInt16* quadrant;
   Int32 budget;
   Int32 budgetInit;
   Int32 i;

   (void)wfact;

   if (nblock < 10000) {
      fallbackSort(s->arr1, s->arr2, ftab, nblock, verb);
   } else {
      /* Use fallback sort for simplicity */
      i = nblock + BZ_N_OVERSHOOT;
      quadrant = (UInt16*)(&(block[i]));
      
      budget = nblock * ((wfact - 1) / 3);
      budgetInit = budget;
      (void)budgetInit;

      fallbackSort(s->arr1, s->arr2, ftab, nblock, verb);
   }

   /* Find the original pointer position */
   s->origPtr = -1;
   for (i = 0; i < nblock; i++)
      if (ptr[i] == 0) { s->origPtr = i; break; }

   AssertH(s->origPtr != -1, 1003);
}
