// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - structure default implementation.
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

public abstract class AStruct implements Struct, Serializable {
    public boolean equals(Object o) {
        Struct st = (Struct) o;
        String[] ans = names(). bns = st.names();
        int i = 0, j = 0;
        while (i < ans.length && j < bns.length) {
            String an, bn;
            if ((an = ans[i]) == (bn = bns[j])) {
                Object a = get(i);
                Object b = st.get(j);
                if (a != b && (a == null || !a.equals(b)))
                    return false;
            } else {
                int cmp = an.compareTo(bn);
                if (cmp > 0) --i;
                if (cmp < 0) --j;
            }
            ++i;
            ++j;
        }
        return true;
    }

    public String toString() {
        String[] names = names();
        StringBuffer sb = new StringBuffer().append('{');
        for (int cnt = names.length, i = 0; i < cnt; ++i) {
            if (i != 0)
                sb.append(", ");
            sb.append(names[i]).append('=').append(Core.show(get(i)));
        }
        sb.append('}');
        return sb.toString();
    }
}
