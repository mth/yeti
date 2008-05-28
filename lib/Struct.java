// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - structure interface.
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

public class Struct {
    final protected Object[] values;

    // expecting interleaved array field1, value1, field2, value2, ...
    public Struct(Object[] valueMap) {
        values = valueMap;
    }

    // expecting type system to not allow getting nonexisting fields.
    // IndexOutOfBoundsException will happen otherwise, speed matters ;)
    public Object get(String field) {
        Object[] v = values;
        int i = 0;
        for (; v[i] != field; i += 2);
        return v[i + 1];
    }

    public void set(String field, Object value) {
        Object[] v = values;
        int i = 0;
        for (; v[i] != field; i += 2);
        v[i + 1] = value;
    }

    public String toString() {
        int cnt = values.length, p = 1;
        if (cnt == 0)
            return "{}";
        String[] v = new String[cnt * 2 + 1];
        v[0] = "{";
        for (int i = 0; i < cnt; i += 2, p += 4) {
            v[p] = (String) values[i];
            v[p + 1] = "=";
            v[p + 2] = Core.show(values[i + 1]);
            v[p + 3] = ", ";
        }
        v[p - 1] = "}";
        return Core.concat(v);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Struct))
            return false;
        Object[] a = ((Struct) o).values;
        int i = a.length;
        if (i != values.length)
            return false;
        Object[] b = new Object[i];
        System.arraycopy(values, 0, b, 0, i);
    ok: while ((i -= 2) >= 0) {
            for (int j = i; j >= 0; j -= 2) {
                if (a[i] == b[j]) {
                    Object x, y;
                    if ((x = a[i + 1]) == (y = b[j + 1]) ||
                        x != null && x.equals(y)) {
                        if (i != j) {
                            b[j] = b[i];
                            b[j + 1] = b[i + 1];
                        }
                        continue ok;
                    }
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    public int hashCode() {
        int res = 0;
        for (int i = values.length; (i -= 2) >= 0;) {
            res ^= values[i].hashCode() + values[i + 1].hashCode();
        }
        return res;
    }
}
