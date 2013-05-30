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
    /**
     * Return rest of the list. Must not modify the current list.
     */
    public abstract AList rest();

    public abstract void forEach(Object f);

    public abstract Object fold(Fun f, Object v);

    public abstract AList reverse();

    public abstract Num index(Object v);

    public abstract AList sort();
    
    public abstract AList smap(Fun f);

    public abstract AList take(int from, int count);

    public AList map(Fun f) {
        return new MapList(this, f);
    }

    public AList find(Fun pred) {
        AList l = this;
        while (l != null && pred.apply(l.first()) != Boolean.TRUE)
            l = l.rest();
        return l;
    }

    public AList sort(Fun isLess) {
        return new MList(this).asort(isLess);
    }

    public AList asList() {
        return this;
    }

    public void removeAll(AList keys) {
    }

    public String toString() {
        StringBuffer buf = new StringBuffer("[");
        buf.append(Core.show(first()));
        for (AIter i = rest(); i != null; i = i.next()) {
            buf.append(',');
            buf.append(Core.show(i.first()));
        }
        buf.append(']');
        return buf.toString();
    }
}
