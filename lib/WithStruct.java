// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - with structure implementation.
 *
 * Copyright (c) 2010 Madis Janson
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

public class WithStruct extends AStruct {
    private final Object[] values;
    private final int[] index;
    private final int size;

    public WithStruct(Struct src, Struct override,
                      String[] names, boolean allowNew) {
        super(null, null);
        int ac = src.count(), bc = override.count();
        index = new int[(allowNew ? ac + names.length : ac) << 1];
        values = new Object[index.length];
        int i = 0, j = -1, k = 0, n = 0;
        String an = src.name(0), bn;
        while ((bn = override.name(++j)) != names[0]);
        while (an != null || bn != null) {
            int c = an == null ? 1 : bn == null ? -1 : an.compareTo(bn);
            if (c >= 0) { // src >= override - take override
                values[n] = bn;
                values[n + 1] = override.ref(j, index, n);
                if (++k >= names.length)
                    bn = null;
                else
                    while ((bn = override.name(++j)) != names[k]);
            } else { // src < override - take super
                values[n] = an;
                values[n + 1] = src.ref(i, index, n);
            }
            if (c <= 0)
                an = ++i >= ac ? null : src.name(i);
            n += 2;
        }
        size = n >>> 1;
    }

    public int count() {
        return size;
    }

    public String name(int i) {
        return values[i << 1].toString();
    }

    public String eqName(int i) {
        return index[(i <<= 1) + 1] == 0 ? values[i].toString() : "";
    }

    public Object get(int i) {
        return values[(i << 1) + 1];
    }

    public Object get(String field) {
        int id, i = -2;
        while (values[i += 2] != field);
        if ((id = index[i]) < 0)
            return values[i + 1];
        return ((Struct) values[i + 1]).get(id);
    }

    public void set(String field, Object value) {
        int i = -2;
        while (values[i += 2] != field);
        ((Struct) values[i + 1]).set(field, value);
    }

    public Object ref(int field, int[] index, int at) {
        index[at] = this.index[field <<= 1];
        index[at + 1] = this.index[++field];
        return values[field];
    }
}
