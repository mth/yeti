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

import java.math.BigInteger;

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
    public abstract Num intDivFrom(long num);
    public abstract Num intDivFrom(BigInteger num);
    public abstract Num sub(Num num);
    public abstract Num sub(long num);
    public abstract Num subFrom(long num);
    public abstract Num subFrom(RatNum num);
    public abstract Num subFrom(BigInteger num);
    public abstract int compareTo(long num);
    public abstract int compareTo(RatNum num);
    public abstract int compareTo(BigInteger num);
}
