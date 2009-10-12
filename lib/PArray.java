// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2009 Madis Janson
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

import java.lang.reflect.Array;

public class PArray extends LList {
    int start;
    final int length;
    final Object array;
    private boolean iter;

    PArray(int start, int length, Object array) {
        super(null, null);
        this.start = start;
        this.length = length;
        this.array = array;
    }

    public Object first() {
        return new IntNum(Array.getLong(array, start));
    }

    public AIter next() {
        if (iter)
            return ++start >= length ? null : this;
        PArray rest = (PArray) rest();
        if (rest != null)
            rest.iter = true;
        return rest;
    }

    public AList rest() {
        int n;
        return (n = start + 1) >= length ? null : new PArray(n, length, array);
    }

    public long length() {
        return length;
    }

    public static AList wrap(byte[] array) {
        return array == null || array.length == 0
            ? null : new PArray(0, array.length, array);
    }

    public static AList wrap(short[] array) {
        return array == null || array.length == 0
            ? null : new PArray(0, array.length, array);
    }

    public static AList wrap(int[] array) {
        return array == null || array.length == 0
            ? null : new PArray(0, array.length, array);
    }

    public static AList wrap(long[] array) {
        return array == null || array.length == 0
            ? null : new PArray(0, array.length, array);
    }

    public static AList wrap(float[] array) {
        return array == null || array.length == 0
            ? null : new FloatArray(0, array.length, array);
    }

    public static AList wrap(double[] array) {
        return array == null || array.length == 0
            ? null : new FloatArray(0, array.length, array);
    }

    public static AList wrap(boolean[] array) {
        return array == null || array.length == 0
            ? null : new BooleanArray(0, array.length, array);
    }
}

final class FloatArray extends PArray {
    FloatArray(int start, int length, Object array) {
        super(start, length, array);
    }

    public Object first() {
        return new FloatNum(Array.getDouble(array, start));
    }

    public AList rest() {
        int n;
        return (n = start + 1) >= length ? null : new FloatArray(n, length, array);
    }
}

final class BooleanArray extends PArray {
    BooleanArray(int start, int length, Object array) {
        super(start, length, array);
    }

    public Object first() {
        return ((boolean[]) array)[start] ? Boolean.TRUE : Boolean.FALSE;
    }

    public AList rest() {
        int n;
        return (n = start + 1) >= length ? null : new BooleanArray(n, length, array);
    }
}
