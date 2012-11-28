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

import java.io.Serializable;

/** Yeti core library - List. */
public class LList extends AList implements Serializable {
    private final Object first;
    private final AList rest;

    public LList(Object first, AList rest) {
        this.first = first;
        this.rest = rest;
    }

    public Object first() {
        return first;
    }

    public AList rest() {
        return rest;
    }

    /**
     * Iterators next. Default implementation for lists returns rest.
     * Some lists may have more efficient iterator implementation.
     */
    public AIter next() {
        return rest();
    }

    public int hashCode() {
        int hashCode = 1;
        AIter i = this;
        do {
            Object x = i.first();
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        } while ((i = i.next()) != null);      
        return hashCode;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof AList)) {
            return false;
        }
        AIter i = (AList) obj, j = this;
        Object x, y;
        while (i != null && j != null &&
               ((x = i.first()) == (y = j.first()) ||
                x != null && x.equals(y))) {
            i = i.next();
            j = j.next();
        }
        return i == null && j == null;
    }

    public int compareTo(Object obj) {
        AIter i = this, j = (AIter) obj;
        while (i != null && j != null) {
            int r;
            if ((obj = i.first()) != null) {
                if ((r = ((Comparable) obj).compareTo(j.first())) != 0)
                    return r;
            } else if (j.first() != null) {
                return -1;
            }
            i = i.next();
            j = j.next();
        }
        return i != null ? 1 : j != null ? -1 : 0;
    }

    public AList reverse() {
        AIter i;
        if ((i = next()) == null)
            return this;
        AList l = new LList(first(), null);
        do {
            l = new LList(i.first(), l);
        } while ((i = i.next()) != null);
        return l;
    }

    public Num index(Object v) {
        int n = 0;
        if (v == null) {
            for (AIter i = this; i != null; i = i.next()) {
                if (i.first() == null) {
                    return new IntNum(n);
                }
                ++n;
            }
            return null;
        }
        for (AIter i = this; i != null; i = i.next()) {
            if (v.equals(i.first())) {
                return new IntNum(n);
            }
            ++n;
        }
        return null;
    }

    public AList sort() {
        return new MList(this).asort();
    }

    public long length() {
        return 0;
    }

    public void forEach(Object f) {
    }

    public Object fold(Fun f, Object value) {
        return null;
    }

    public AList smap(Fun f) {
        return null;
    }
    
    public Object copy() {
        return null;
    }

    public AList take(int from, int count) {
        return null;
    }
}
