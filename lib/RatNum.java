// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - rational numbers.
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

public final class RatNum extends Num {
    private final long numerator;
    private final long denominator;

    public RatNum(int numerator, int denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException(numerator + "/0");
        }
        if (denominator < 0) {
            this.numerator = -(long) numerator;
            this.denominator = -(long) denominator;
        } else {
            this.numerator = numerator;
            this.denominator = denominator;
        }
    }

    private RatNum(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public Num add(Num num) {
        return num.add(this);
    }

    private static long gcd(long a, long b) {
        long c;
        while (b != 0) {
            b = a % (c = b);
            a = c;
        }
        return a;
    }

    public Num add(long num) {
        long a, c, gcd;
        if (num > 0x7fffffffL || num < -0x7fffffffL ||
            (a = num * denominator) > 0x7fffffff7fffffffL ||
             a < -0x7fffffff7fffffffL) {
            return new FloatNum((double) numerator / denominator + num);
        }
        if ((c = a + numerator) > 0x7fffffffL || c < -0x7fffffff) {
            long d = denominator / (gcd = gcd(c < 0 ? -c : c, denominator));
            if ((c /= gcd) > 0x7fffffffL || c < -0x7fffffffL) {
                return new FloatNum((double) c / d);
            }
            return new RatNum(c, d);
        }
        return new RatNum(c, denominator);
    }

    public Num add(RatNum num) {
        long a, b = numerator * num.denominator, c, gcd;
        if ((a = num.numerator * denominator) > 0
                ? a > 0x3fffffffffffffffL || b > 0x3fffffffffffffffL
                : a < -0x3fffffffffffffffL || b < -0x3fffffffffffffffL) {
            return new FloatNum((double) numerator / denominator +
                (double) num.numerator / num.denominator);
        }
        long d = denominator * num.denominator;
        if ((c = a + b) > 0x7fffffffL || c < -0x7fffffff || 
            d > 0x7ffffffffL || d < -0x7fffffffL) {
            d /= gcd = gcd(c < 0 ? -c : c, d);
            if ((c /= gcd) > 0x7fffffffL || c < -0x7fffffffL ||
                d > 0x7fffffffL || d < -0x7fffffffL) {
                return new FloatNum((double) c / d);
            }
        }
        return new RatNum(c, d);
    }

    public Num add(BigInteger num) {
        return new FloatNum((double) numerator / denominator
                            + num.doubleValue());
    }

    public Num mul(Num num) {
        return num.mul(this);
    }

    public Num mul(long num) {
        long a;
        if (num > 0x7fffffffL || num < -0x7fffffffL) {
            return new FloatNum((double) numerator / denominator * num);
        }
        if ((a = numerator * num) > 0x7fffffffL || a < -0x7fffffffL) {
            long gcd, b = denominator / (gcd = gcd(a, denominator));
            if ((a /= gcd) > 0x7fffffffL || a < -0x7fffffffL) {
                return new FloatNum((double) a / b);
            }
            return new RatNum(a, b);
        }
        return new RatNum(a, denominator);
    }

    public Num mul(RatNum num) {
        long a, b = denominator * num.denominator, gcd;
        if ((a = numerator * num.numerator) > 0x7fffffffL
            || a < -0x7fffffffL || b > 0x7fffffffL || b < -0x7fffffff) {
            b /= gcd = gcd(a, b);
            if ((a /= gcd) > 0x7fffffffL || a < -0x7fffffffL ||
                b > 0x7fffffffL || b < -0x7fffffffL) {
                return new FloatNum((double) a / b);
            }
        }
        return new RatNum(a, b);
    }

    public Num mul(BigInteger num) {
        return new FloatNum((double) numerator / denominator
                            * num.doubleValue());
    }

    public Num div(Num num) {
        return num.divFrom(this);
    }

    public Num div(long num) {
        long a;
        if (num > 0x7fffffffL || num < -0x7fffffffL) {
            return new FloatNum((double) numerator /
                        ((double) denominator * num));
        }
        if ((a = denominator * num) > 0x7fffffffL || a < -0x7fffffffL) {
            long gcd, b = numerator / (gcd = gcd(a, numerator));
            if ((a /= gcd) > 0x7fffffffL || a < -0x7fffffffL) {
                return new FloatNum((double) b / a);
            }
            return new RatNum(b, a);
        }
        return new RatNum(numerator, a);
    }

    // num / this
    public Num divFrom(long num) {
        long a;
        if (num > 0x7fffffffL || num < -0x7fffffffL) {
            return new FloatNum((double) num / numerator * denominator);
        }
        if ((a = denominator * num) > 0x7fffffffL || a < -0x7fffffffL) {
            long gcd, b = numerator / (gcd = gcd(a, numerator));
            if ((a /= gcd) > 0x7fffffffL || a < -0x7fffffffL) {
                return new FloatNum((double) a / b);
            }
            return new RatNum(a, b);
        }
        return new RatNum(a, numerator);
    }

    public Num divFrom(RatNum num) {
        long a, b = numerator * num.denominator, gcd;
        if ((a = denominator * num.numerator) > 0x7fffffffL
            || a < -0x7fffffffL || b > 0x7fffffffL || b < -0x7fffffff) {
            b /= gcd = gcd(a, b);
            if ((a /= gcd) > 0x7fffffffL || a < -0x7fffffffL ||
                b > 0x7fffffffL || b < -0x7fffffffL) {
                return new FloatNum((double) a / b);
            }
        }
        return new RatNum(a, b);
    }

    public Num intDiv(Num num) {
        return num.intDivFrom(numerator / denominator);
    }

    public Num intDiv(int num) {
        return new IntNum(numerator / denominator / num);
    }

    public Num intDivFrom(long num) {
        return new IntNum(num / (numerator / denominator));
    }

    public Num intDivFrom(BigInteger num) {
        return new BigNum(num.divide(
                            BigInteger.valueOf(numerator / denominator)));
    }

    public Num rem(Num num) {
        return num.remFrom(numerator / denominator);
    }

    public Num rem(int num) {
        return new IntNum((numerator / denominator) % num);
    }

    public Num remFrom(long num) {
        return new IntNum(num % (numerator / denominator));
    }

    public Num remFrom(BigInteger num) {
        return new BigNum(num.remainder(
                            BigInteger.valueOf(numerator / denominator)));
    }

    public Num sub(Num num) {
        return num.subFrom(this);
    }

    public Num sub(long num) {
        return add(-num);
    }

    public Num subFrom(long num) {
        long a, c, gcd;
        if (num > 0x7fffffffL || num < -0x7fffffffL ||
            (a = num * denominator) > 0x7fffffff7fffffffL ||
             a < -0x7fffffff7fffffffL) {
            return new FloatNum((double) num -
                (double) numerator / denominator);
        }
        if ((c = a - numerator) > 0x7fffffffL || c < -0x7fffffff) {
            long d = denominator / (gcd = gcd(c < 0 ? -c : c, denominator));
            if ((c /= gcd) > 0x7fffffffL || c < -0x7fffffffL) {
                return new FloatNum((double) c / d);
            }
            return new RatNum(c, d);
        }
        return new RatNum(c, denominator);
    }

    public Num subFrom(RatNum num) {
        long a, b = numerator * num.denominator, c, gcd;
        if ((a = num.numerator * denominator) > 0
                ? a > 0x3fffffffffffffffL || b < -0x3fffffffffffffffL
                : a < -0x3fffffffffffffffL || b > 0x3fffffffffffffffL) {
            return new FloatNum((double) numerator / denominator +
                (double) num.numerator / num.denominator);
        }
        long d = denominator * num.denominator;
        if ((c = a - b) > 0x7fffffffL || c < -0x7fffffff || 
            d > 0x7ffffffffL || d < -0x7fffffffL) {
            d /= gcd = gcd(c < 0 ? -c : c, d);
            if ((c /= gcd) > 0x7fffffffL || c < -0x7fffffffL ||
                d > 0x7fffffffL || d < -0x7fffffffL) {
                return new FloatNum((double) c / d);
            }
        }
        return new RatNum(c, d);
    }

    public Num and(Num num) {
        return new IntNum(num.longValue() & (numerator / denominator));
    }

    public Num and(BigInteger num) {
        return new IntNum(num.longValue() & (numerator / denominator));
    }

    public Num or(Num num) {
        return num.or(numerator / denominator);
    }

    public Num or(long num) {
        return new IntNum(num | (numerator / denominator));
    }

    public Num xor(Num num) {
        return num.xor(numerator / denominator);
    }

    public Num xor(long num) {
        return new IntNum(num ^ (numerator / denominator));
    }

    public RatNum reduce() {
        long gcd = gcd(numerator, denominator);
        return new RatNum(numerator / gcd, denominator / gcd);
    }

    public byte byteValue() {
        return (byte) (numerator / denominator);
    }

    public short shortValue() {
        return (short) (numerator / denominator);
    }

    public int intValue() {
        return (int) (numerator / denominator);
    }

    public long longValue() {
        return numerator / denominator;
    }

    public float floatValue() {
        return (float) ((double) numerator / denominator);
    }

    public double doubleValue() {
        return (double) numerator / (double) denominator;
    }

    public static Num div(long numerator, long denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("division by zero");
        }
        if (numerator > 0x7fffffff || numerator < -0x7fffffff ||
            denominator > 0x7fffffff || denominator < -0x7fffffff) {
            long gcd;
            denominator /= gcd = gcd(numerator, denominator);
            if ((numerator /= gcd) > 0x7fffffff || numerator < -0x7fffffff ||
                denominator > 0x7fffffff || denominator < -0x7fffffff) {
                return new FloatNum((double) numerator / denominator);
            }
        }
        return denominator < 0 ? new RatNum(-numerator, -denominator)
                               : new RatNum(numerator, denominator);
    }

    public Num subFrom(BigInteger num) {
        return new FloatNum((double) numerator / denominator
                            - num.doubleValue());
    }

    public int numerator() {
        return (int) numerator;
    }

    public int denominator() {
        return (int) denominator;
    }

    public int compareTo(Object num) {
        return ((Num) num).rCompare(this);
    }

    public int rCompare(long num) {
        if (-0x7fffffff <= num && num <= 0x7fffffff) {
            long x = num * denominator;
            return numerator < x ? 1 : numerator > x ? -1 : 0;
        }
        return (double) numerator / denominator < (double) num ? 1 : -1;
    }

    public int rCompare(RatNum num) {
        long a = numerator * num.denominator;
        long b = num.numerator * denominator;
        return a < b ? 1 : a > b ? -1 : 0;
    }

    public int rCompare(BigInteger num) {
        if (numerator % denominator == 0 &&
            BigInteger.valueOf(numerator / denominator).equals(num)) {
            return 0;
        }
        double a = (double) numerator / denominator, b = num.doubleValue();
        return a < b ? 1 : a > b ? -1 : 0;
    }

    public String toString() {
        if (numerator % denominator == 0) {
            return Integer.toString((int) numerator / (int) denominator);
        }
        return Double.toString((double) numerator / (double) denominator);
    }

    public int hashCode() {
        // compatibility with IntNum and FloatNum...
        long x = numerator / denominator;
        long d = Double.doubleToLongBits((double) numerator / denominator - x);
        if (d != 0x8000000000000000L) {
            x ^= d;
        }
        return (int) (x ^ (x >>> 32));
    }
}
