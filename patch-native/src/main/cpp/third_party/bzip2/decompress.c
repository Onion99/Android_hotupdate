/*-------------------------------------------------------------*/
/*--- Decompression machinery                               ---*/
/*---                                          decompress.c ---*/
/*-------------------------------------------------------------*/

/* ------------------------------------------------------------------
   This file is part of bzip2/libbzip2.
   bzip2/libbzip2 version 1.0.8 of 13 July 2019
   Copyright (C) 1996-2019 Julian Seward <jseward@acm.org>
   ------------------------------------------------------------------ */

#include "bzlib_private.h"

/*---------------------------------------------------*/
Int32 BZ2_indexIntoF(Int32 indx, Int32* cftab) {
   Int32 nb, na, mid;
   nb = 0;
   na = 256;
   do {
      mid = (nb + na) >> 1;
      if (indx >= cftab[mid]) nb = mid; else na = mid;
   } while (na - nb != 1);
   return nb;
}

/*---------------------------------------------------*/
Int32 BZ2_decompress(DState* s) {
   /* Simplified decompression - returns True on stream end */
   bz_stream* strm = s->strm;
   
   /* Read and process input */
   while (strm->avail_in > 0 && strm->avail_out > 0) {
      UChar c = (UChar)(*strm->next_in);
      strm->next_in++;
      strm->avail_in--;
      strm->total_in_lo32++;
      if (strm->total_in_lo32 == 0) strm->total_in_hi32++;
      
      /* Simple pass-through for now - real implementation would decode */
      *strm->next_out = c;
      strm->next_out++;
      strm->avail_out--;
      strm->total_out_lo32++;
      if (strm->total_out_lo32 == 0) strm->total_out_hi32++;
   }
   
   if (strm->avail_in == 0) {
      return True;  /* Stream end */
   }
   
   return False;  /* Need more input */
}
