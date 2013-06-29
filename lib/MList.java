// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2007,2008,2009 Madis Janson
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

abstract class AMList extends AList implements Serializable {
    int start;

    abstract int _size();
    abstract Object[] array();

    // used by length
    public long length() {
        int l = _size() - start;
        return l > 0 ? l : 0;
    }

    // used by copy
    public Object copy() {
        Object[] a = new Object[_size()];
        System.arraycopy(array(), start, a, 0, a.length);
        return new MList(a);
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
        if (obj == null)
            return _size() <= start;
        if (obj instanceof AMList) {
            AMList o = (AMList) obj;
            int cnt = _size();
            if (cnt - start != o._size() - o.start)
                return false;
            Object[] arr = array(), arr_ = o.array();
            for (int i = start, j = o.start; i < cnt; ++i, ++j) {
                Object a = arr[i], b = arr_[j];
                if (a != b && (a == null || !a.equals(b)))
                    return false;
            }
            return true;
        }
        if (!(obj instanceof AList))
            return false;
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
            if (i > start)
                buf.append(',');
            buf.append(Core.show(array[i]));
        }
        buf.append(']');
        return buf.toString();
    }

    public void forEach(Object fun) {
        Fun f = (Fun) fun;
        Object[] array = array();
        for (int cnt = _size(), i = start; i < cnt; ++i)
            f.apply(array[i]);
    }

    public Object fold(Fun f, Object v) {
        Object[] array = array();
        for (int cnt = _size(), i = start; i < cnt; ++i)
            v = f.apply(v, array[i]);
        return v;
    }

    public Num index(Object v) {
        Object[] array = array();
        int cnt = _size();
        if (v == null) {
            for (int i = start; i < cnt; ++i)
                if (array[i] == null)
                    return new IntNum(i - start);
            return null;
        }
        for (int i = start; i < cnt; ++i)
            if (v.equals(array[i]))
                return new IntNum(i - start);
        return null;
    }

    public AList map(Fun f) {
        int cnt = _size();
        if (start >= cnt)
            return null;
        Object[] array = array();
        Object[] result = new Object[cnt - start];
        for (int i = start; i < cnt; ++i)
            result[i - start] = f.apply(array[i]);
        return new MList(result);
    }

    public AList smap(Fun f) {
        return map(f);
    }

    public int compareTo(Object obj) {
        Object[] array = array();
        int cnt = _size(), r, i = start;
        if (!(obj instanceof AMList)) {
            AIter j = (AIter) obj;
            for (; i < cnt && j != null; ++i) {
                if ((obj = array[i]) != null) {
                    if ((r = ((Comparable) obj).compareTo(j.first())) != 0)
                        return r;
                } else if (j.first() != null) {
                    return -1;
                }
                j = j.next();
            }
            return j != null ? -1 : i < cnt ? 1 : 0;
        }
        AMList o = (AMList) obj;
        Object[] array_ = o.array();
        int cnt_ = o._size();
        for (int j = o.start; i < cnt && j < cnt_; ++i, ++j) {
            if ((obj = array[i]) != null) {
                if ((r = ((Comparable) obj).compareTo(array_[j])) != 0) {
                    return r;
                }
            } else if (array_[j] != null) {
                return -1;
            }
        }
        return cnt < cnt_ ? -1 : cnt > cnt_ ? 1 : 0;
    }

    public AList reverse() {
        Object[] array = array();
        int end = _size();
        if (end <= start)
            return null;
        Object[] r = new Object[end - start];
        --end;
        for (int i = 0; i < r.length; ++i)
            r[i] = array[end - i];
        return new MList(r);
    }

    public AList sort() {
        int len;
        if ((len = _size() - start) <= 0)
            return null;
        Object[] a;
        System.arraycopy(array(), start, a = new Object[len], 0, len);
        Arrays.sort(a);
        return new MList(a);
    }

    public AList sort(Fun isLess) {
        int len;
        if ((len = _size() - start) <= 0)
            return null;
        Object[] a;
        System.arraycopy(array(), start, a = new Object[len], 0, len);
        return new MList(a).asort(isLess);
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

        public AList take(int from, int count) {
            if (from < 0)
                from = 0;
            from += start;
            if (count < 0)
                return from < size ? from == start
                        ? this : new SubList(from) : null;
            if ((count += start) > size)
                count = size;
            MList res = new MList(array);
            res.start = from;
            res.size = count;
            return res;
        }

        public AList find(Fun pred) {
            for (int cnt = size, i = start; i < cnt; ++i) {
                if (pred.apply(array[i]) == Boolean.TRUE) {
                    return new SubList(i);
                }
            }
            return null;
        }
    }

    private class Iter extends AIter implements Serializable {
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

        public AIter dup() {
            return new Iter(i);
        }

        AIter write(OutputStream out) throws IOException {
            if (i < size) {
                byte[] tmp = new byte[size - i];
                for (int off = i, j = 0; j < tmp.length; ++j)
                    tmp[j] = ((Number) array[j + off]).byteValue();
                out.write(tmp);
            }
            return null;
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

    // used by setArrayCapacity
    public void reserve(int n) {
        if (n > array.length) {
            Object[] tmp = new Object[n];
            System.arraycopy(array, 0, tmp, 0, size);
            array = tmp;
        }
    }

    // used by push
    public void add(Object o) {
        if (size >= array.length) {
            Object[] tmp = new Object[size == 0 ? 10 : size * 3 / 2 + 1];
            System.arraycopy(array, 0, tmp, 0, array.length);
            array = tmp;
        }
        array[size++] = o;
    }

    // used by shift
    public Object shift() {
        if (start >= size)
            throw new EmptyArrayException("No first element in empty array");
        return array[start++];
    }

    // used by pop
    public Object pop() {
        if (start >= size)
            throw new EmptyArrayException("Cannot pop from an empty array");
        return array[--size];
    }

    // used by clear
    public void clear() {
        start = size = 0;
    }

    // used by head
    public Object first() {
        if (start >= size)
            throw new EmptyArrayException("No first element in empty array");
        return array[start];
    }

    // used by tail
    public AList rest() {
        int p;
        return (p = start + 1) < size ? new SubList(p) : null;
    }

    // used for iterating lists
    public AIter next() {
        int p;
        return (p = start + 1) < size ? new Iter(p) : null;
    }

    // used by compiler for i in a
    public boolean containsKey(Object index) {
        int i;
        return  (i = ((Number) index).intValue()) >= 0 && i + start < size;
    }

    // used by compiler for a[i]
    public Object vget(Object index) {
        int i;
        if ((i = ((Number) index).intValue()) < 0)
            throw new NoSuchKeyException(i, size - start);
        if ((i += start) >= size)
            throw new NoSuchKeyException(i - start, size - start);
        return array[i];
    }

    // used by compiler for a[number]
    public Object get(int index) {
        int i;
        if (index < 0 || (i = index + start) >= size)
            throw new NoSuchKeyException(index, size - start);
        return array[i];
    }

    // used by compiler for a[i] := x
    public Object put(Object index, Object value) {
        int i;
        if ((i = ((Number) index).intValue()) < 0)
            throw new NoSuchKeyException(i, size - start);
        if ((i += start) >= size)
            throw new NoSuchKeyException(i - start, size - start);
        array[i] = value;
        return null;
    }

    // used delete
    public Object remove(Object index) {
        int i, n;
        if ((i = ((Number) index).intValue()) < 0)
            throw new NoSuchKeyException(i, size - start);
        if ((i += start) >= size)
            throw new NoSuchKeyException(i - start, size - start);
        if ((n = --size - i) > 0)
            System.arraycopy(array, i + 1, array, i, n);
        return null;
    }

    private void removeRange(ListRange range) {
        int from = range.first.intValue(),
            to = range.last.intValue();
        if (range.inc < 0) {
            int tmp = from;
            from = to;
            to = tmp;
        }
        int n = size - start;
        if (from <= to) {
            if (from < 0 || from >= n)
                throw new NoSuchKeyException(from, n);
            if (to < 0 || to >= n)
                throw new NoSuchKeyException(to, n);
            if (++to < n)
                System.arraycopy(array, to + start,
                                 array, from + start, n - to);
            size -= to - from;
        }
    }

    // used by deleteAll
    public void removeAll(AList keys) {
        // a common use case is removing a single range
        if (keys instanceof ListRange) {
            ListRange range = (ListRange) keys;
            if (range.rest == null) {
                removeRange(range);
                return;
            }
        }
        if (keys == null || keys.isEmpty())
            return;
        // latter elements must be removed first, so have to sort it
        MList kl = new MList();
        while (keys != null) {
            kl.add(keys);
            if (keys instanceof ListRange)
                keys = ((ListRange) keys).rest;
            else
                keys = keys.rest();
        }
        Object[] ka = kl.asort().array;
        Object last = null;
        for (int i = kl.size; --i >= 0;) {
            if (ka[i] instanceof ListRange) {
                removeRange((ListRange) ka[i]);
            } else {
                Object index = ((AList) ka[i]).first();
                if (!index.equals(last)) {
                    remove(index);
                    last = index;
                }
            }
        }
    }

    // used by slice
    public MList copy(int from, int to) {
        int n = size - start;
        if (from < 0 || from > n)
            throw new NoSuchKeyException(from, n);
        if (to > n)
            throw new NoSuchKeyException("Copy range " + from + " to " + to +
                                         " exceeds array length " + n);
        if (from >= to)
            return new MList();
        Object[] subArray = new Object[to - from];
        System.arraycopy(array, start + from, subArray, 0, subArray.length);
        return new MList(subArray);
    }

    // used by take
    public AList take(int from, int count) {
        if (from < 0)
            from = 0;
        from += start;
        if (count < 0)
            return from < size ? from == start
                    ? this : (AList) new SubList(from) : null;
        if ((count += start) > size)
            count = size;
        MList res = new MList(array);
        res.start = from;
        res.size = count;
        return res;
    }

    // used by find
    public AList find(Fun pred) {
        for (int cnt = size, i = start; i < cnt; ++i) {
            if (pred.apply(array[i]) == Boolean.TRUE) {
                return new SubList(i);
            }
        }
        return null;
    }

    // used by empty?
    public boolean isEmpty() {
        return start >= size;
    }

    final int _size() {
        return size;
    }

    final Object[] array() {
        return array;
    }

    final MList asort() {
        Arrays.sort(array, start, size);
        return this;
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

    public void setDefault(Fun fun) {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray(Object[] to) {
        System.arraycopy(array, start, to, 0, size - start);
        return to;
    }

    public static MList ofList(AList list) {
        if (list instanceof MList)
            return (MList) list;
        return new MList(list);
    }

    public static MList ofStrArray(Object[] array) {
        Object[] tmp = new Object[array.length];
        for (int i = 0; i < tmp.length; ++i)
            tmp[i] = array[i] == null ? Core.UNDEF_STR : array[i];
        return new MList(tmp);
    }
}
