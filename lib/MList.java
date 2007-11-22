// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
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

abstract class AMList extends AList {
    int start;

    abstract int _size();
    abstract Object[] array();

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
        if (!(obj instanceof AList)) {
            return false;
        }
        Object[] array = array();
        AIter j = (AList) obj;
        Object x, y;
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            if (j == null ||
                (x = array[i]) != (y = j.first()) &&
                (x == null || !x.equals(j))) {
                return false;
            }
        }
        return j == null;
    }

    public String toString() {
        Object[] array = array();
        StringBuffer buf = new StringBuffer("[");
        for (int cnt = _size(), i = start; i < cnt; ++i) {
            if (i != 0) {
                buf.append(',');
            }
            buf.append(array[i]);
        }
        buf.append(']');
        return buf.toString();
    }

    public int compareTo(Object o) {
        AIter j = (AIter) o;
        Object[] array = array();
        for (int r, cnt = _size(), i = start; i < cnt; ++i) {
            Object a, b;
            if (j == null) {
                return 1;
            }
            if ((b = j.first()) != (a = array[i])) {
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
            j = j.next();
        }
        return j == null ? 0 : -1;
    }
}

/** Yeti core library - List. */
public class MList extends AMList implements ByKey {
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

        int _size() {
            return size;
        }

        Object[] array() {
            return array;
        }
    }

    private class Iter extends AIter {
        private int i;

        private Iter(int start) {
            i = start;
        }

        public Object first() {
            if (i < size) {
                throw new IllegalStateException(
                    "End of list reached or list has shrunken.");
            }
            return array[i];
        }

        public AIter next() {
            return ++i < size ? this : null;
        }
    }

    public MList() {
        array = new Object[10];
    }

    public MList(Object[] array) {
        this.array = array;
        size = array.length;
    }

    public MList(AIter iter) {
        array = new Object[10];
        while (iter != null) {
            add(iter.first());
            iter = iter.next();
        }
    }

    public void add(Object o) {
        if (size >= array.length) {
            Object[] tmp = new Object[size * 3 / 2];
            System.arraycopy(array, 0, tmp, 0, array.length);
            array = tmp;
        }
        array[size++] = o;
    }

    public Object first() {
        if (size == 0) {
            throw new IllegalStateException("No first element in empty list");
        }
        return array[0];
    }

    public AList rest() {
        return size > 1 ? new SubList(1) : null;
    }

    public AIter next() {
        return size > 1 ? new Iter(1) : null;
    }

    public Object get(Object index) {
        int i;
        if ((i = ((Number) index).intValue()) >= size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        return array[i];
    }

    public Object put(Object index, Object value) {
        int i;
        if ((i = ((Number) index).intValue()) >= size) {
            throw new ArrayIndexOutOfBoundsException(i);
        }
        array[i] = value;
        return null;
    }

    int _size() {
        return size;
    }

    Object[] array() {
        return array;
    }
}
