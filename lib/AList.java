// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2007-2013 Madis Janson
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
public abstract class AList extends AIter implements Comparable, Coll {
    /** Return rest of the list. Must not modify the current list. */
    public abstract AList rest();

    /** Calls {@link Fun} f for each list value. */
    public abstract void forEach(Object f);

    /** Calculates left fold over f. */
    public abstract Object fold(Fun f, Object v);

    /** Gives reversed copy of list. */
    public abstract AList reverse();

    /** Gives numeric index of v in list or null if no element is equal to v. */
    public abstract Num index(Object v);

    /** Gives sorted copy of list. */
    public abstract AList sort();

    /** Creates strict mapping by f of the list. */
    public abstract AList smap(Fun f);

    /** Gives sublist of count elements starting at from index. */
    public abstract AList take(int from, int count);

    /** Creates mapping by of the list (lazy, if reasonable). */
    public AList map(Fun f) {
        return new MapList(this, f);
    }

    /** Gives sublist starting from first element,
     *  where predicate application returns true. */
    public AList find(Fun predicate) {
        AList l = this;
        while (l != null && predicate.apply(l.first()) != Boolean.TRUE)
            l = l.rest();
        return l;
    }

    /** Gives copy of list sorted by given isLess function. */
    public AList sort(Fun isLess) {
        return new MList(this).asort(isLess);
    }

    @Override
    public AList asList() {
        return this;
    }

    @Override
    public void removeAll(AList keys) {
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("[");
        buf.append(Core.show(first()));
        for (AIter i = rest(); i != null; i = i.next()) {
            buf.append(',');
            buf.append(Core.show(i.first()));
        }
        buf.append(']');
        return buf.toString();
    }
}
