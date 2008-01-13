// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2008 Madis Janson
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
public class ListRange extends AList {
    Num first;
    Num last;
    AList rest;
    int inc = 1;

    public ListRange(Object first, Object last, AList rest) {
        this.first = (Num) first;
        this.last  = (Num) last;
        this.rest  = rest;
    }

    public Object first() {
        return first.compareTo(last) * inc > 0 ? rest.first() : first;
    }

    public AList rest() {
        int n = first.compareTo(last) * inc;
        return n > 0 ? rest.rest() : n == 0 ? rest :
               new ListRange(first.add(inc), last, rest);
    }

    public AIter next() {
        return rest(); // TODO
    }

    public int hashCode() {
        int hashCode = 1;
        for (Num i = first; i.compareTo(last) <= 0; i = i.add(inc)) {
            hashCode = 31 * hashCode + i.hashCode();
        }
        for (AIter i = rest; i != null; i = i.next()) {
            Object x = i.first();
            hashCode = 31 * hashCode + (x == null ? 0 : x.hashCode());
        }
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
                x != null && x.equals(j))) {
            i = i.next();
            j = j.next();
        }
        return i == null && j == null;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer("[");
        boolean f = true;
        for (Num i = first; i.compareTo(last) * inc <= 0; i = i.add(inc)) {
            if (f) {
                f = false;
            } else {
                buf.append(',');
            }
            buf.append(i);
        }
        for (AIter i = rest; i != null; i = i.next()) {
            if (f) {
                f = false;
            } else {
                buf.append(',');
            }
            buf.append(i.first());
        }
        buf.append(']');
        return buf.toString();
    }

    public int compareTo(Object obj) {
        AIter i = this, j = (AIter) obj;
        while (i != null && j != null) {
            int r;
            if ((r = ((Comparable) i.first()).compareTo(j.first())) != 0) {
                return r;
            }
            i = i.next();
            j = j.next();
        }
        return i != null ? 1 : j != null ? -1 : 0;
    }

    public void forEach(Fun f) {
        for (Num i = first; i.compareTo(last) * inc <= 0; i = i.add(inc)) {
            f.apply(i);
        }
        for (AIter i = rest; i != null; i = i.next()) {
            f.apply(i.first());
        }
    }

    public AList reverse() {
        ListRange r = new ListRange(last, first, rest);
        r.inc = -inc;
        AList l = r;
        for (AIter i = rest; i != null; i = i.next()) {
            l = new LList(i.first(), l);
        }
        return l;
    }
}
