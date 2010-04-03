// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - structure interface.
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
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.Iterator;

public class Struct implements Serializable {
    final protected Object[] values;

    // expecting interleaved array field1, value1, field2, value2, ...
    public Struct(Object[] valueMap) {
        values = valueMap;
    }

    // expecting type system to not allow getting nonexisting fields.
    // IndexOutOfBoundsException will happen otherwise, speed matters ;)
    public Object get(String field) {
        int i = -2;
        while (values[i += 2] != field);
        return values[i + 1];
    }

    public void set(String field, Object value) {
        int i = -2;
        while (values[i += 2] != field);
        values[i + 1] = value;
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

    MList names() {
        Object[] s = new Object[values.length / 2];
        for (int i = 0; i < s.length; ++i)
            s[i] = values[i * 2];
        return new MList(s);
    }

    Object[] properties() {
        return null;
    }

    public Struct with(Object extender_, String[] names) {
        Struct extender = (Struct) extender_;
        int i;
        HashMap vm = new HashMap();
        Object[] val = values;
        for (i = val.length; (i -= 2) >= 0; )
            vm.put(val[i], val[i + 1]);
        Object[] props = properties();
        Object[] extProp = extender.properties();
        HashMap pm = null;
        if (props != null || extProp != null) {
            pm = new HashMap();
            if (props != null)
                for (i = props.length; (i -= 3) >= 0; )
                    pm.put(props[i], new Object[] {props[i + 1], props[i + 2]});
        }
        Object[] extVal = extender.values;
        HashMap filter = null;
        if (names != null) {
            filter = new HashMap();
            for (i = 0; i < names.length; ++i)
                filter.put(names[i], null);
        }
        for (i = extVal.length; (i -= 2) >= 0; ) {
            Object name = extVal[i];
            if (filter != null && !filter.containsKey(name))
                continue;
            vm.put(name, extVal[i + 1]);
            if (pm != null)
                pm.remove(name);
        }
        if (extProp != null)
            for (i = extProp.length; (i -= 3) >= 0; ) {
                Object name = extProp[i];
                if (filter != null && !filter.containsKey(name))
                    continue;
                pm.put(name, new Object[] { extProp[i + 1], extProp[i + 2]});
                vm.remove(name);
            }
        val = new Object[vm.size() * 2];
        i = 0;
        for (Iterator j = vm.entrySet().iterator(); j.hasNext(); ++i) {
            Entry e = (Entry) j.next();
            val[i] = e.getKey();
            val[++i] = e.getValue();
        }
        if (pm == null || pm.size() == 0)
            return new Struct(val);
        props = new Object[pm.size() * 3];
        i = 0;
        for (Iterator j = pm.entrySet().iterator(); j.hasNext(); ++i) {
            Entry e = (Entry) j.next();
            Object[] v = (Object[]) e.getValue();
            props[i] = e.getKey();
            props[++i] = v[0];
            props[++i] = v[1];
        }
        return new PStruct(val, props);
    }

    public int hashCode() {
        int res = 0;
        for (int i = values.length; (i -= 2) >= 0;) {
            Object v = values[i + 1];
            res ^= values[i].hashCode() + (v == null ? 0 : v.hashCode());
        }
        return res;
    }
}
