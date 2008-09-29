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

public final class IntNum extends Num {
    public static final IntNum __2 = new IntNum(-2);
    public static final IntNum __1 = new IntNum(-1);
    public static final IntNum _0 = new IntNum(0);
    public static final IntNum _1 = new IntNum(1);
    public static final IntNum _2 = new IntNum(2);
    public static final IntNum _3 = new IntNum(3);
    public static final IntNum _4 = new IntNum(4);
    public static final IntNum _5 = new IntNum(5);
    public static final IntNum _6 = new IntNum(6);
    public static final IntNum _7 = new IntNum(7);
    public static final IntNum _8 = new IntNum(8);
    public static final IntNum _9 = new IntNum(9);

    private final long v;

    public IntNum(int num) {
        v = num;
    }

    public IntNum(long num) {
        v = num;
    }

    public Num add(Num num) {
        return num.add(v);
    }

    public Num add(RatNum num) {
        return num.add(v);
    }

    public Num add(long num) {
        if (num > 0 ? num > 0x3fffffffffffffffL || v > 0x3fffffffffffffffL
                    : num < -0x3fffffffffffffffL || v < -0x3fffffffffffffffL) {
            return new BigNum(BigInteger.valueOf(v).add(
                                BigInteger.valueOf(num)));
        }
        return new IntNum(v + num);
    }

    public Num add(BigInteger num) {
        return new BigNum(BigInteger.valueOf(v).add(num));
    }

    public Num mul(Num num) {
        return num.mul(v);
    }

    public Num mul(long num) {
        if (num < -0x7fffffffL || num > 0x7fffffffL
            || v < -0x7fffffffL || v > 0x7fffffffL) {
            return new BigNum(BigInteger.valueOf(v).multiply(
                                BigInteger.valueOf(num)));
        }
        return new IntNum(v * num);
    }

    public Num mul(BigInteger num) {
        return new BigNum(BigInteger.valueOf(v).multiply(num));
    }

    public Num mul(RatNum num) {
        return num.mul(v);
    }

    public Num div(Num num) {
        return num.divFrom(v);
    }

    public Num div(long num) {
        return RatNum.div(v, num);
    }

    public Num divFrom(long num) {
        return RatNum.div(num, v);
    }

    public Num divFrom(RatNum num) {
        return num.div(v);
    }

    public Num intDiv(Num num) {
        return num.intDivFrom(v);
    }

    public Num intDiv(int num) {
        return new IntNum(v / num);
    }

    public Num intDivFrom(long num) {
        return new IntNum(num / v);
    }

    public Num intDivFrom(BigInteger num) {
        return new BigNum(num.divide(BigInteger.valueOf(v)));
    }

    public Num rem(Num num) {
        return num.remFrom(v);
    }

    public Num rem(int num) {
        return new IntNum(v % num);
    }

    public Num remFrom(long num) {
        return new IntNum(num % v);
    }

    public Num remFrom(BigInteger num) {
        return new BigNum(num.remainder(BigInteger.valueOf(v)));
    }

    public Num sub(Num num) {
        return num.subFrom(v);
    }

    public Num sub(long num) {
        long n;
        if (num < 0 ? num < -0x3fffffffffffffffL || v > 0x3fffffffffffffffL
                    : num > 0x3fffffffffffffffL || v < -0x3fffffffffffffffL) {
            return new BigNum(BigInteger.valueOf(v).subtract(
                                BigInteger.valueOf(num)));
        }
        return new IntNum(v - num);
    }

    public Num subFrom(long num) {
        if (num < 0 ? num < -0x3fffffffffffffffL || v > 0x3fffffffffffffffL
                    : num > 0x3fffffffffffffffL || v < -0x3fffffffffffffffL) {
            return new BigNum(BigInteger.valueOf(num).subtract(
                                BigInteger.valueOf(v)));
        }
        return new IntNum(num - v);
    }

    public Num subFrom(RatNum num) {
        return num.sub(v);
    }

    public Num subFrom(BigInteger num) {
        return new BigNum(num.subtract(BigInteger.valueOf(v)));
    }

    public Num and(Num num) {
        return new IntNum(num.longValue() & v);
    }

    public Num and(BigInteger num) {
        return new IntNum(num.longValue() & v);
    }

    public Num or(Num num) {
        return num.or(v);
    }

    public Num or(long num) {
        return new IntNum(num | v);
    }

    public Num xor(Num num) {
        return num.xor(v);
    }

    public Num xor(long num) {
        return new IntNum(num ^ v);
    }

    public byte byteValue() {
        return (byte) v;
    }

    public short shortValue() {
        return (short) v;
    }

    public int intValue() {
        return (int) v;
    }

    public long longValue() {
        return v;
    }

    public float floatValue() {
        return v;
    }

    public double doubleValue() {
        return v;
    }

    public int compareTo(Object num) {
        return ((Num) num).rCompare(v);
    }

    public int rCompare(long num) {
        return v < num ? 1 : v > num ? -1 : 0;
    }

    public int rCompare(RatNum num) {
        return -num.rCompare(v);
    }

    public int rCompare(BigInteger num) {
        return num.compareTo(BigInteger.valueOf(v));
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(v);
    }

    public String toString() {
        return Long.toString(v);
    }

    public int hashCode() {
        return (int) (v ^ (v >>> 32));
    }
}
