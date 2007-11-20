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

/** Yeti core library - List. */
public class MList extends AList {
    private Object[] array;
    private int size;

    private class SubList extends AList {
        Object first;
        int start;

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

    public int hashCode() {
        int hashCode = 1;
        for (int i = 0; i < size; ++i) {
            Object x = array[i];
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        }
        return hashCode;
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return size == 0;
        }
        if (!(obj instanceof AList)) {
            return false;
        }
        AIter j = (AList) obj;
        Object x, y;
        for (int i = 0; i < size; ++i) {
            if (j == null ||
                (x = array[i]) != (y = j.first()) &&
                (x == null || !x.equals(j))) {
                return false;
            }
        }
        return j == null;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer("[");
        for (int i = 0; i < size; ++i) {
            if (i != 0) {
                buf.append(',');
            }
            buf.append(array[i]);
        }
        buf.append(']');
        return buf.toString();
    }
}
