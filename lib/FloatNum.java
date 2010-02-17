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

public final class FloatNum extends Num {
    private final double v;

    public FloatNum(double num) {
        v = num;
    }

    public Num add(Num num) {
        return new FloatNum(v + num.doubleValue());
    }

    public Num add(long num) {
        return new FloatNum(v + num);
    }

    public Num add(RatNum num) {
        return new FloatNum(v + num.doubleValue());
    }

    public Num add(BigInteger num) {
        return new FloatNum(v + num.doubleValue());
    }

    public Num mul(Num num) {
        return new FloatNum(v * num.doubleValue());
    }

    public Num mul(long num) {
        return new FloatNum(v * num);
    }

    public Num mul(RatNum num) {
        return new FloatNum(v * num.doubleValue());
    }

    public Num mul(BigInteger num) {
        return new FloatNum(v * num.doubleValue());
    }

    public Num div(Num num) {
        return new FloatNum(v / num.doubleValue());
    }

    public Num div(long num) {
        return new FloatNum(v / num);
    }

    public Num divFrom(long num) {
        return new FloatNum(num / v);
    }

    public Num divFrom(RatNum num) {
        return new FloatNum(num.doubleValue() / v);
    }

    public Num intDiv(Num num) {
        double res = (v >= 0 ? Math.floor(v) : Math.ceil(v)) /
                     num.doubleValue();
        return res > 2147483647.0 || res < -2147483647.0
            ? new FloatNum(res >= 0 ? Math.floor(res) : Math.ceil(res))
            : (Num) new IntNum((long) res);
    }

    public Num intDiv(int num) {
        double res = (v >= 0 ? Math.floor(v) : Math.ceil(v)) / num;
        return res > 2147483647.0 || res < -2147483647.0
            ? new FloatNum(res >= 0 ? Math.floor(res) : Math.ceil(res))
            : (Num) new IntNum((long) res);
    }

    public Num intDivFrom(long num) {
        return new IntNum((long)
            (num / (v >= 0 ? Math.floor(v) : Math.ceil(v))));
    }

    public Num intDivFrom(BigInteger num) {
        // XXX
        double res = num.doubleValue() /
                    (v >= 0 ? Math.floor(v) : Math.ceil(v));
        return res > 2147483647.0 || res < -2147483647.0
            ? new FloatNum(res >= 0 ? Math.floor(res) : Math.ceil(res))
            : (Num) new IntNum((long) res);
    }

    public Num rem(Num num) {
        return new IntNum((long) v % num.longValue());
    }

    public Num rem(int num) {
        return new IntNum((long) v % num);
    }

    public Num remFrom(long num) {
        return new IntNum(num % (long) v);
    }

    public Num remFrom(BigInteger num) {
        // XXX
        double res = num.doubleValue() %
                    (v >= 0 ? Math.floor(v) : Math.ceil(v));
        return res > 2147483647.0 || res < -2147483647.0
            ? new FloatNum(res >= 0 ? Math.floor(res) : Math.ceil(res))
            : (Num) new IntNum((long) res);
    }

    public Num sub(Num num) {
        return new FloatNum(v - num.doubleValue());
    }

    public Num sub(long num) {
        return new FloatNum(v - num);
    }

    public Num subFrom(long num) {
        return new FloatNum(num - v);
    }

    public Num subFrom(RatNum num) {
        return new FloatNum(num.doubleValue() - v);
    }

    public Num subFrom(BigInteger num) {
        return new FloatNum(num.doubleValue() - v);
    }

    public Num and(Num num) {
        return new IntNum(num.longValue() & (long) v);
    }

    public Num and(BigInteger num) {
        return new IntNum(num.longValue() & (long) v);
    }

    public Num or(Num num) {
        return num.or((long) v);
    }

    public Num or(long num) {
        return new IntNum(num | (long) v);
    }

    public Num xor(Num num) {
        return num.xor((long) v);
    }

    public Num xor(long num) {
        return new IntNum(num ^ (long) v);
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
        return (long) v;
    }

    public float floatValue() {
        return (float) v;
    }

    public double doubleValue() {
        return v;
    }

    public int compareTo(Object num) {
        double x = ((Number) num).doubleValue();
        return v < x ? -1 : v > x ? 1 : 0;
    }

    public int rCompare(long num) {
        return v < num ? 1 : v > num ? -1 : 0;
    }

    public int rCompare(RatNum num) {
        double x = num.doubleValue();
        return v < x ? 1 : v > x ? -1 : 0;
    }

    public int rCompare(BigInteger num) {
        double x = num.doubleValue();
        return v < x ? 1 : v > x ? -1 : 0;
    }

    public String toString() {
        return Double.toString(v);
    }

    public int hashCode() {
        // hashCode must be same when equals is same
        // a bit rough, but hopefully it satisfies that condition ;)
        long x = (long) v;
        long d = Double.doubleToLongBits(v - x);
        if (d != 0x8000000000000000L) {
            x ^= d;
        }
        return (int) (x ^ (x >>> 32));
    }

    public boolean equals(Object num) {
        // It returns false when comparing two NaNs, however this should still
        // follow the hashCode/equals contract as it is allowed to have same
        // hashCode for values not equal. A bit weirdness can happen, like not
        // founding NaN from hash containing it, but fixing it isn't probably
        // worth the performance overhead of making equals more complicated.
        // Just avoid using NaN's, they are fucked up in IEEE (and therefore
        // JVM) floats.
        return num instanceof Num && v == ((Num) num).doubleValue();
    }
}
