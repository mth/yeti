// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - generic structure implementation.
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

import java.util.Arrays;
import java.util.Map;
import java.util.IdentityHashMap;

// GenericStruct can be useful in Java code and for very large Yeti
// structures, where pointer scan by field names becomes slow.
public class GenericStruct extends AStruct {
    private final Map impl;
    private final boolean allMutable;

    private static String[] getNames(Map values) {
        String[] result =
            (String[]) values.keySet().toArray(new String[values.size()]);
        Arrays.sort(result);
        return result;
    }

    /**
     * Construct a structure from Java standard Map.
     * Defaults to all fields being mutable.
     */
    public GenericStruct(Map values) {
        super(getNames(values), null);
        this.impl = values;
        this.allMutable = true; // we don't know, use safe default.
    }

    /**
     * Construct a structure from Java standard Map.
     */
    public GenericStruct(Map values, boolean[] vars) {
        super(getNames(values), vars);
        this.impl = values;
        allMutable = false;
    }

    /**
     * Construct a structure with given fields.
     * Values must be initialized using set.
     */
    public GenericStruct(String[] names, boolean[] vars) {
        super(names, vars);
        impl = new IdentityHashMap(names.length);
        allMutable = false;
    }

    public Object get(String field) {
        return impl.get(field);
    }

    public Object get(int field) {
        return impl.get(name(field));
    }

    public void set(String field, Object value) {
        impl.put(field, value);
    }
    
    public Object ref(int field, int[] index, int at) {
        if (!allMutable)
            return super.ref(field, index, at);
        index[at] = field;
        index[at + 1] = 0;
        return this;
    }
}
