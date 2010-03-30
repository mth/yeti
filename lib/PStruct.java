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

public class PStruct extends Struct {
    final Object[] properties;

    // expecting interleaved array field1, value1, field2, value2, ...
    public PStruct(Object[] valueMap, Object[] propertyMap) {
        super(valueMap);
        properties = propertyMap;
    }

    // expecting type system to not allow getting nonexisting fields.
    // IndexOutOfBoundsException will happen otherwise, speed matters ;)
    public Object get(String field) {
        Object[] v = values;
        int i = 0, n = v.length;
        for (; i < n && v[i] != field; i += 2);
        if (i < n)
            return v[i + 1];
        v = properties;
        for (i = 0; v[i] != field; i += 3);
        return ((Fun) v[i + 1]).apply(null);
    }

    public void set(String field, Object value) {
        Object[] v = values;
        int i = 0, n = v.length;
        for (; i < n && v[i] != field; i += 2);
        if (i < n) {
            v[i + 1] = value;
            return;
        }
        v = properties;
        for (i = 0; v[i] != field; i += 3);
        ((Fun) v[i + 2]).apply(value);
    }

    MList names() {
        MList l = super.names();
        for (int i = 0; i < properties.length; i += 3)
            l.add(properties[i]);
        return l;
    }
}
