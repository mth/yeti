// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - Number interface.
 *
 * Copyright (c) 2007,2008 Madis Janson
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package yeti.lang;

import java.math.BigInteger;
import java.math.BigDecimal;

public abstract class Num extends Number implements Comparable {
    public abstract Num add(Num num);
    public abstract Num add(long num);
    public abstract Num add(RatNum num);
    public abstract Num add(BigInteger num);
    public abstract Num mul(Num num);
    public abstract Num mul(long num);
    public abstract Num mul(RatNum num);
    public abstract Num mul(BigInteger num);
    public abstract Num div(Num num);
    public abstract Num div(long num);
    public abstract Num divFrom(long num);
    public abstract Num divFrom(RatNum num);
    public abstract Num intDiv(Num num);
    public abstract Num intDiv(int num);
    public abstract Num intDivFrom(long num);
    public abstract Num intDivFrom(BigInteger num);
    public abstract Num rem(Num num);
    public abstract Num rem(int num);
    public abstract Num remFrom(long num);
    public abstract Num remFrom(BigInteger num);
    public abstract Num sub(Num num);
    public abstract Num sub(long num);
    public abstract Num subFrom(long num);
    public abstract Num subFrom(RatNum num);
    public abstract Num subFrom(BigInteger num);
    public abstract Num and(Num num);
    public abstract Num and(BigInteger num);
    public abstract Num or(Num num);
    public abstract Num or(long num);
    public abstract Num xor(Num num);
    public abstract Num xor(long num);
    public abstract int rCompare(long num);
    public abstract int rCompare(RatNum num);
    public abstract int rCompare(BigInteger num);

    private static final long[] SHL_LIMIT = {
                     Long.MAX_VALUE,      0x4000000000000000L,
                     0x2000000000000000L, 0x1000000000000000L,
                     0x0800000000000000L, 0x0400000000000000L,
                     0x0200000000000000L, 0x0100000000000000L,
                     0x0080000000000000L, 0x0040000000000000L,
                     0x0020000000000000L, 0x0010000000000000L,
                     0x0008000000000000L, 0x0004000000000000L,
                     0x0002000000000000L, 0x0001000000000000L,
        0x800000000000L, 0x400000000000L, 0x200000000000L, 0x100000000000L,
        0x080000000000L, 0x040000000000L, 0x020000000000L, 0x010000000000L,
        0x008000000000L, 0x004000000000L, 0x002000000000L, 0x001000000000L,
        0x000800000000L, 0x000400000000L, 0x000200000000L, 0x000100000000L,
        0x000080000000L, 0x000040000000L, 0x000020000000L, 0x000010000000L,
        0x000008000000L, 0x000004000000L, 0x000002000000L, 0x000001000000L,
        0x000000800000L, 0x000000400000L, 0x000000200000L, 0x000000100000L,
        0x000000080000L, 0x000000040000L, 0x000000020000L, 0x000000010000L,
      0x8000L, 0x4000L, 0x2000L, 0x1000L, 0x0800L, 0x0400L, 0x0200L, 0x0100L,
      0x0080L, 0x0040L, 0x0020L, 0x0010L, 0x0008L, 0x0004L, 0x0002L, 0x0001L,
    };

    public Num shl(int by) {
        if (by < 0) {
            return new IntNum(longValue() >> -by);
        }
        long l, v;
        if (by < 32 && (v = longValue()) < (l = SHL_LIMIT[by]) && v > -l) {
            return new IntNum(v << by);
        }
        return new BigNum(toBigInteger().shiftLeft(by));
    }

    public boolean equals(Object x) {
        return x instanceof Num && compareTo(x) == 0;
    }

    public BigInteger toBigInteger() {
        return BigInteger.valueOf(longValue());
    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(toString());
    }

    public static Num parseNum(String str) {
        return Core.parseNum(str);
    }
}
