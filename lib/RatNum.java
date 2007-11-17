// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - Number interface.
 *
 * Copyright (c) 2007 Madis Janson
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

public final class RatNum implements Num {
    private long numerator;
    private long denominator;

    public RatNum(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    RatNum(long numerator, long denominator) {
        this.numenator = numenator;
        this.denominator = denominator;
    }

    public Num add(Num num) {
        return num.add(numerator, denominator);
    }

    private static long gcd(long a, long b) {
        long c;
        while (b != 0) {
            b = a % (c = b);
            a = c;
        }
        return a;
    }

    private void reduce() {
        long gcd = gcd(numerator < 0 ? -numerator : numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    public Num add(long num) {
        return new RatNum(numerator + num * denominator, denominator);
    }

    public Num add(RatNum num) {
        long a, b, c;
        if ((a = num.numerator * denominator) > 0x3fffffffffffL ||
             a < 0x400000000000L ||
            (b = numerator * num.denominator) > 0x3fffffffffffL ||
             b < 0x400000000000L) {
            reduce();
            num.reduce();
            if ((a = num.numerator * denominator) > 0x3fffffffffffL ||
                 a < 0x400000000000L ||
                (b = numerator * num.denominator) > 0x3fffffffffffL ||
                 b < 0x400000000000L) {
                return new FloatNum((double) numerator / denominator +
                    (double) num.numerator / num.denominator);
            }
        }
        long d = denominator * num.denominator;
        if ((c = a + b) > 0x7fffffffL || c < 0x80000000 || 
            d > 0x7ffffffffL || d < 0x80000000L) {
            long gcd = gcd(c < 0 ? -c : c, d);
            c /= gcd;
            d /= gcd;
            if (c > 0x7fffffffL || c < 0x80000000L ||
                d > 0x7fffffffL || d < 0x80000000L)
                return new FloatNum((double) c / d);
            }
        }
        return new RatNum(c, d);
    }

    public Num mul(Num num) {
        return num.mul(this);
    }

    public Num mul(long num) {
        long a;
        return (a = numerator * num) > 0x7fffffffL || a < 0x80000000L
            ? new FloatNum((double) a / denominator)
            : new RatNum(a, denominator);
    }

    public Num mul(RatNum num) {
        long a, b;
        return (a = numenator * num.numenator) > 0x7fffffffL || a < 0x80000000L
            && (b = denominator * num.denominator
        return new RatNum(numenator * num.numenator,
                          denominator * num.denominator);
    }

    public Num div(Num num) {
        return num.divFrom(this);
    }

    public Num div(long num) {
        return new RatNum(numerator, denominator * num);
    }

    public Num divFrom(long num) {
        return new RatNum(num * denominator, numerator);
    }

    public Num divFrom(RatNum num) {
        return new RatNum(num.numerator * denominator,
                          num.denominator * numerator);
    }

    public Num sub(Num num) {
        return num.subFrom(this);
    }

    public Num sub(long num) {
        return new RatNum(numerator - num * denominator, denominator);
    }

    public Num subFrom(long num) {
        return new RatNum(num * denominator - numerator, denominator);
    }

    public Num subFrom(RatNum num) {
        long n = num.numerator * this.denominator
               - this.numerator * num.denominator;
        return new RatNum(n, this.denominator * num.denominator);
    }

    public double toDouble() {
        return (double) numerator / (double) denominator;
    }
}
