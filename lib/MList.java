// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
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

import java.util.Arrays;

abstract class AMList extends AList implements ListIter {
    int start;

    abstract int _size();
    abstract Object[] array();

    public long length() {
        int l = _size() - start;
        return l > 0 ? l : 0;
    }

    public int hashCode() {
        int hashCode = 1;
        Object[] array = array();
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            Object x = array[i];
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        }
        return hashCode;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return _size() <= start;
        }
        if (obj instanceof AMList) {
            AMList o = (AMList) obj;
            int cnt = _size();
            if (cnt - start != o._size() - o.start) {
                return false;
            }
            Object[] arr = array(), arr_ = o.array();
            for (int i = start, j = o.start; i < cnt; ++i, ++j) {
                Object a = arr[i], b = arr_[j];
                if (a != b && (a == null || !a.equals(b))) {
                    return false;
                }
            }
            return true;
        }
        if (!(obj instanceof AList)) {
            return false;
        }
        Object[] array = array();
        AIter j = (AList) obj;
        Object x, y;
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            if (j == null ||
                (x = array[i]) != (y = j.first()) &&
                (x == null || !x.equals(y))) {
                return false;
            }
            j = j.next();
        }
        return j == null;
    }

    public String toString() {
        Object[] array = array();
        StringBuffer buf = new StringBuffer("[");
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            if (i > start) {
                buf.append(',');
            }
            buf.append(Core.show(array[i]));
        }
        buf.append(']');
        return buf.toString();
    }

    public void forEach(Object fun) {
        Fun f = (Fun) fun;
        Object[] array = array();
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            f.apply(array[i]);
        }
    }

    public ListIter iter() {
        return this;
    }

    public Object fold(Fun f, Object v, AIter _) {
        Object[] array = array();
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            v = f.apply(v, array[i]);
        }
        return v;
    }

    public Num index(Object v) {
        Object[] array = array();
        int cnt = _size();
        if (v == null) {
            for (int i = start; i < cnt; ++i) {
                if (array[i] == null) {
                    return new IntNum(i - start);
                }
            }
            return null;
        }
        for (int i = start; i < cnt; ++i) {
            if (v.equals(array[i])) {
                return new IntNum(i - start);
            }
        }
        return null;
    }

    public AList map(Fun f) {
        int cnt = _size();
        if (start >= cnt)
            return null;
        Object[] array = array();
        Object[] result = new Object[cnt - start];
        for (int i = start; i < cnt; ++i) {
            result[i - start] = f.apply(array[i]);
        }
        return new MList(result);
    }

    public int compareTo(Object other) {
        AMList o = (AMList) other;
        Object[] array = array();
        Object[] array_ = o.array();
        int cnt = _size(), cnt_ = o._size();
        for (int r, i = start, j = o.start; i < cnt && j < cnt_; ++i, ++j) {
            Object a, b;
            if ((b = array_[j]) != (a = array[i])) {
                if (a == null) {
                    return -1;
                }
                if (b == null) {
                    return 1;
                }
                if ((r = ((Comparable) a).compareTo(b)) != 0) {
                    return r;
                }
            }
        }
        return cnt < cnt_ ? -1 : cnt > cnt_ ? 1 : 0;
    }

    // TODO ineffective
    public AList reverse() {
        AList l = null;
        for (AIter i = this; i != null; i = i.next()) {
            l = new LList(i.first(), l);
        }
        return l;
    }
}

/** Yeti core library - List. */
public class MList extends AMList implements ByKey {
    private static final Object[] EMPTY = {}; 
    private Object[] array;
    private int size;

    private class SubList extends AMList {
        Object first;

        private SubList(int start) {
            this.first = array[start];
            this.start = start;
        }

        public Object first() {
            return start < size ? array[start] : first;
        }

        public AList rest() {
            int p;
            return (p = start + 1) < size ? new SubList(p) : null;
        }

        public AIter next() {
            int p;
            return (p = start + 1) < size ? new Iter(p) : null;
        }

        public boolean isEmpty() {
            return start >= size;
        }

        int _size() {
            return size;
        }

        Object[] array() {
            return array;
        }

        public AList find(Fun pred) {
            for (int cnt = size, i = start; i < cnt; ++i) {
                if (pred.apply(array[i]) == Boolean.TRUE) {
                    return new SubList(i);
                }
            }
            return null;
        }

        public AList sort() {
            if (start >= size)
                return null;
            Object[] tmp = new Object[size - start];
            System.arraycopy(array, start, tmp, 0, tmp.length);
            return new MList(tmp);
        }
    }

    private class Iter extends AIter {
        private int i;

        private Iter(int start) {
            i = start;
        }

        public Object first() {
            if (i >= size) {
                throw new IllegalStateException(
                    "End of list reached or list has shrunken.");
            }
            return array[i];
        }

        public AIter next() {
            return ++i < size ? this : null;
        }

        public boolean isEmpty() {
            return i >= size;
        }
    }

    public MList() {
        array = EMPTY;
    }

    public MList(Object[] array) {
        this.array = array;
        size = array.length;
    }

    public MList(AIter iter) {
        if (iter == null || iter.isEmpty()) {
            array = EMPTY;
        } else {
            array = new Object[10];
            while (iter != null) {
                add(iter.first());
                iter = iter.next();
            }
        }
    }

    public void reserve(int n) {
        if (n > array.length) {
            Object[] tmp = new Object[n];
            System.arraycopy(array, 0, tmp, 0, size);
            array = tmp;
        }
    }

    public void add(Object o) {
        if (size >= array.length) {
            Object[] tmp = new Object[size == 0 ? 10 : size * 3 / 2];
            System.arraycopy(array, 0, tmp, 0, array.length);
            array = tmp;
        }
        array[size++] = o;
    }

    public Object shift() {
        if (start >= size) {
            throw new EmptyArrayException("No first element in empty array");
        }
        return array[start++];
    }

    public Object pop() {
        if (start >= size) {
            throw new EmptyArrayException("Cannot pop from an empty array");
        }
        return array[--size];
    }

    public Object first() {
        if (start >= size) {
            throw new EmptyArrayException("No first element in empty array");
        }
        return array[start];
    }

    public AList rest() {
        int p;
        return (p = start + 1) < size ? new SubList(p) : null;
    }

    public AIter next() {
        int p;
        return (p = start + 1) < size ? new Iter(p) : null;
    }

    public Object vget(Object index) {
        int i;
        if ((i = ((Number) index).intValue()) < 0) {
            throw new NoSuchKeyException(i, size);
        }
        if ((i += start) >= size) {
            throw new NoSuchKeyException(i - start, size);
        }
        return array[i];
    }

    public Object put(Object index, Object value) {
        int i;
        if ((i = ((Number) index).intValue()) < 0) {
            throw new NoSuchKeyException(i, size);
        }
        if ((i += start) >= size) {
            throw new NoSuchKeyException(i - start, size);
        }
        array[i] = value;
        return null;
    }

    public AList find(Fun pred) {
        for (int cnt = size, i = start; i < cnt; ++i) {
            if (pred.apply(array[i]) == Boolean.TRUE) {
                return new SubList(i);
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return start >= size;
    }

    int _size() {
        return size;
    }

    Object[] array() {
        return array;
    }

    MList asort() {
        Arrays.sort(array, start, size);
        return this;
    }

    public AList sort() {
        if (start >= size)
            return null;
        Object[] tmp = new Object[size - start];
        System.arraycopy(array, start, tmp, 0, tmp.length);
        return new MList(tmp);
    }

    // java sort don't know wtf the Fun is
    private static void sort(Object[] a, Object[] tmp,
                             int from, int to, Fun isLess) {
        int split = (from + to) / 2;
        if (split - from > 1)
            sort(tmp, a, from, split, isLess);
        if (to - split > 1)
            sort(tmp, a, split, to, isLess);
        int i = from, j = split;
        while (i < split && j < to) {
            if (isLess.apply(tmp[i], tmp[j]) == Boolean.TRUE)
                a[from] = tmp[i++];
            else
                a[from] = tmp[j++];
            ++from;
        }
        if (i < split)
            System.arraycopy(tmp, i, a, from, split - i);
        else if (j < to)
            System.arraycopy(tmp, j, a, from, to - j);
    }

    MList asort(Fun isLess) {
        if (size - start > 1) {
            Object[] tmp = new Object[size];
            System.arraycopy(array, start, tmp, start, size - start);
            sort(array, tmp, start, size, isLess);
        }
        return this;
    }

    public AList sort(Fun isLess) {
        int len;
        if ((len = size - start) <= 0)
            return null;
        MList l = new MList();
        System.arraycopy(array, start, l.array = new Object[len], 0, len);
        l.size = len;
        return l.asort(isLess);
    }
}
