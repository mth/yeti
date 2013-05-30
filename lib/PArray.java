// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2009-2013 Madis Janson
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Arrays;

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

    public AIter dup() {
         PArray slice = slice(start, length);
         slice.iter = true;
         return slice;
    }

    PArray slice(int start, int length) {
        return new PArray(start, length, array);
    }

    public AList rest() {
        int n;
        return (n = start + 1) >= length ? null : slice(n, length);
    }

    public AList take(int from, int count) {
        if (from < 0)
            from = 0;
        from += start;
        if (count < 0 || (count += from) > length)
            count = length;
        if (from >= count)
            return null;
        if (from == start && count == length)
            return this;
        return slice(from, count);
    }

    public long length() {
        return length - start;
    }

    public static AList wrap(byte[] array) {
        return array == null || array.length == 0
            ? null : new ByteArray(0, array.length, array);
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

    public static AList wrap(char[] array) {
        return array == null || array.length == 0
            ? null : new CharArray(0, array.length, array);
    }
}

final class CharArray extends PArray {
    CharArray(int start, int length, Object array) {
        super(start, length, array);
    }

    public Object first() {
        return new String((char[]) array, start, 1);
    }

    PArray slice(int start, int length) {
        return new CharArray(start, length, array);
    }
}

final class FloatArray extends PArray {
    FloatArray(int start, int length, Object array) {
        super(start, length, array);
    }

    public Object first() {
        return new FloatNum(Array.getDouble(array, start));
    }

    PArray slice(int start, int length) {
        return new FloatArray(start, length, array);
    }
}

final class BooleanArray extends PArray {
    BooleanArray(int start, int length, Object array) {
        super(start, length, array);
    }

    public Object first() {
        return ((boolean[]) array)[start] ? Boolean.TRUE : Boolean.FALSE;
    }

    PArray slice(int start, int length) {
        return new BooleanArray(start, length, array);
    }
}

final class ByteArray extends PArray {
    private final byte[] a;

    ByteArray(int start, int length, byte[] a) {
        super(start, length, a);
        this.a = a;
    }

    public Object first() {
        return new IntNum(a[start] & 0xff);
    }

    PArray slice(int start, int length) {
        return new ByteArray(start, length, a);
    }

    public void forEach(Object f_) {
        Fun f = (Fun) f_;
        for (int i = start, e = length; i < e; ++i)
            f.apply(new IntNum(a[i]));
    }

    public Object fold(Fun f_, Object v) {
        Fun f = (Fun) f_;
        for (int i = start, e = length; i < e; ++i)
            v = f.apply(v, new IntNum(a[i]));
        return v;
    }

    public AList reverse() {
        byte[] tmp = new byte[length - start];
        for (int i = 0; i < tmp.length; ++i)
            tmp[tmp.length - i] = a[i + start];
        return new ByteArray(0, tmp.length, tmp);
    }

    public Num index(Object v) {
        int b = ((IntNum) v).intValue();
        for (int i = start, e = length; i < e; ++i)
            if (a[i] == b)
                return new IntNum(i - start);
        return null;
    }

    public AList find(Fun pred) {
        for (int i = start, e = length; i < e; ++i)
            if (pred.apply(new IntNum(a[i])) == Boolean.TRUE)
                return new ByteArray(i, e - i, a);
        return null;
    }

    public AList sort() {
        byte[] tmp = new byte[length - start];
        System.arraycopy(a, start, tmp, 0, tmp.length);
        Arrays.sort(tmp);
        return new ByteArray(0, tmp.length, tmp);
    }

    public long length() {
        return length - start;
    }

    public Object copy() {
        byte[] tmp = new byte[length - start];
        System.arraycopy(a, start, tmp, 0, tmp.length);
        return new ByteArray(0, tmp.length, tmp);
    }

    AIter write(OutputStream out) throws IOException {
        out.write(a, start, length - start);
        return null;
    }

    public AList map(Fun f) {
        return smap(f);
    }

    public AList sort(Fun isLess) {
        return new MList(this).asort(isLess);
    }
}
