// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2008-2013 Madis Janson
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

final class RangeIter extends AIter implements Serializable {
    Num n;
    final Num last;
    final AList rest;
    final int inc;

    RangeIter(Num n, Num last, AList rest, int inc) {
        this.n = n;
        this.last = last;
        this.rest = rest;
        this.inc = inc;
    }

    public Object first() {
        return n;
    }

    public AIter next() {
        return (n = n.add(inc)).compareTo(last) * inc > 0 ? (AIter) rest : this;
    }

    public AIter dup() {
        return new RangeIter(n, last, rest, inc);
    }
}

/** Yeti core library - List. */
public final class ListRange extends AList implements Serializable {
    final Num first;
    final Num last;
    final AList rest;
    final int inc;

    private ListRange(Num first, Num last, AList rest) {
        this.first = first;
        this.last  = last;
        this.rest  = rest;
        this.inc = 1;
    }

    private ListRange(Num first, Num last, AList rest, int inc) {
        this.first = first;
        this.last  = last;
        this.rest  = rest;
        this.inc = inc;
    }

    public static AList range(Object first, Object last, AList rest) {
        Num f;
        return (f = (Num) first).compareTo(last) > 0 ? rest
            : new ListRange(f, (Num) last, rest);
    }

    public Object first() {
        return first;
    }

    public AList rest() {
        Num n;
        if ((n = first.add(inc)).compareTo(last) * inc > 0)
            return rest;
        return new ListRange(n, last, rest, inc);
    }

    public AIter next() {
        Num n = first.add(inc);
        if (n.compareTo(last) * inc > 0)
            return rest;
        return new RangeIter(n, last, rest, inc);
    }

    public AList take(int from, int count) {
        Num n = first;
        if (count == 0)
            return null;
        if (from > 0) {
            n = n.add(inc * from);
            if (n.compareTo(last) * inc > 0) {
                if (rest == null)
                    return null;
                return rest.take(from - (last.sub(first).intValue()*inc + 1), count);
            }
        }
        AList tail = null;
        Num last_;
        if (count < 0) {
            last_ = last;
            tail = rest;
        } else {
            last_ = n.add(inc * (count - 1));
            if (last_.compareTo(last) * inc > 0) { // last_ > last -> tail remains
                if (rest != null)
                    tail = rest.take(0, last_.sub(last).intValue() * inc);
                last_ = last;
            }
        }
        return new ListRange(n, last_, tail, inc);
    }

    public int hashCode() {
        int hashCode = 1;
        for (Num i = first; i.compareTo(last) <= 0; i = i.add(inc))
            hashCode = 31 * hashCode + i.hashCode();
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
            if ((r = ((Comparable) i.first()).compareTo(j.first())) != 0)
                return r;
            i = i.next();
            j = j.next();
        }
        if (i != null)
            return 1;
        if (j != null)
            return -1;
        return 0;
    }

    public void forEach(Object fun) {
        Fun f = (Fun) fun;
        if (inc > 0 && first.rCompare(Integer.MIN_VALUE) < 0 &&
                       last.rCompare(Integer.MAX_VALUE) > 0) {
            if (first.compareTo(last) <= 0)
                for (int i = first.intValue(), e = last.intValue();
                     i <= e; ++i)
                    f.apply(new IntNum(i));
        } else if (inc < 0 && first.rCompare(Integer.MAX_VALUE) > 0 &&
                   last.rCompare(Integer.MIN_VALUE) < 0) {
            if (first.compareTo(last) >= 0)
                for (int i = first.intValue(), e = last.intValue();
                     i >= e; --i)
                    f.apply(new IntNum(i));
        } else {
            for (Num i = first; i.compareTo(last) * inc <= 0; i = i.add(inc))
                f.apply(i);
        }
        if (rest != null)
            rest.forEach(fun);
    }

    public Object fold(Fun f, Object v) {
        if (inc > 0 && first.rCompare(Integer.MIN_VALUE) < 0 &&
                       last.rCompare(Integer.MAX_VALUE) > 0) {
            if (first.compareTo(last) <= 0)
                for (int i = first.intValue(), e = last.intValue();
                     i <= e; ++i)
                    v = f.apply(v, new IntNum(i));
        } else if (inc < 0 && first.rCompare(Integer.MAX_VALUE) > 0 &&
                   last.rCompare(Integer.MIN_VALUE) < 0) {
            if (first.compareTo(last) >= 0)
                for (int i = first.intValue(), e = last.intValue();
                     i >= e; --i)
                    v = f.apply(v, new IntNum(i));
        } else {
            for (Num i = first; i.compareTo(last) * inc <= 0; i = i.add(inc))
                v = f.apply(v, i);
        }
        if (rest == null)
            return v;
        return rest.fold(f, v);
    }

    public AList reverse() {
        AList l = new ListRange(last, first, null, -inc);
        for (AIter i = rest; i != null; i = i.next())
            l = new LList(i.first(), l);
        return l;
    }

    public AList find(Fun pred) {
        Num j;
        if (inc > 0 && first.rCompare(Integer.MIN_VALUE) < 0 &&
                       last.rCompare(Integer.MAX_VALUE) > 0) {
            if (first.compareTo(last) <= 0)
                for (int i = first.intValue(), e = last.intValue();
                     i <= e; ++i) {
                    j = new IntNum(i);
                    if (pred.apply(j) == Boolean.TRUE)
                        return new ListRange(j, last, rest);
                }
        } else {
            for (j = first; j.compareTo(last) * inc <= 0; j = j.add(inc))
                if (pred.apply(j) == Boolean.TRUE)
                    return new ListRange(j, last, rest, inc);
        }
        if (rest == null)
            return null;
        return rest.find(pred);
    }

    public AList smap(Fun f) {
        MList l;
        if (inc > 0 && first.rCompare(Integer.MIN_VALUE) < 0 &&
                       last.rCompare(Integer.MAX_VALUE) > 0) {
            int i = first.intValue(), e = last.intValue();
            if (i > e)
                return rest.smap(f);
            l = new MList();
            l.reserve(e - i + 1);
            while (i <= e)
                l.add(f.apply(new IntNum(i++)));
        } else if (inc < 0 && first.rCompare(Integer.MAX_VALUE) > 0 &&
                   last.rCompare(Integer.MIN_VALUE) < 0) {
            int i = first.intValue(), e = last.intValue();
            if (i < e)
                return rest.smap(f);
            l = new MList();
            l.reserve(i - e + 1);
            while (i >= e)
                l.add(f.apply(new IntNum(i--)));
        } else {
            return new MapList(this, f);
        }
        for (AIter i = rest; i != null; i = i.next())
            l.add(f.apply(i.first()));
        return l;
    }

    public long length() {
        long n = last.sub(first).longValue() / inc + 1;
        if (n < 0)
            n = 0;
        if (rest == null)
            return n;
        return n + rest.length();
    }

    public Num index(Object v) {
        if (inc > 0) {
            if (first.compareTo(v) <= 0 && last.compareTo(v) >= 0)
                return ((Num) v).sub(first);
        } else {
            if (last.compareTo(v) <= 0 && first.compareTo(v) >= 0)
                return ((Num) v).sub(last);
        }
        if (rest == null)
            return null;
        Num res;
        if ((res = rest.index(v)) == null)
            return null;
        long n;
        if ((n = last.sub(first).longValue() / inc) <= 0)
            return res;
        return res.add(n + 1);
    }

    public AList sort() {
        return rest == null ? inc > 0 ? this : reverse()
                            : new MList(this).asort();
    }

    public Object copy() {
        return new ListRange(first, last, rest, inc);
    }
}
