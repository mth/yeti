// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2009 Madis Janson
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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/** Yeti core library - IdentityHash. */
public class CHash extends AbstractMap implements ByKey, Coll {
    static final int IDENTITY = 1;
    static final int CONCURRENT = 2;
    static final int WEAK = 3;
    private final int type;
    private final Fun cons;
    private final Map impl;
    private volatile Fun defaultFun;

    public CHash(int type_, Fun cons_) {
        type = type_;
        cons = cons_;
        switch (type) {
        case 0:
            impl = (Map) cons_.apply(null);
            break;
        case IDENTITY:
            impl = new java.util.IdentityHashMap();
            break;
        case CONCURRENT:
            impl = new java.util.concurrent.ConcurrentHashMap();
            break;
        case WEAK:
            impl = new java.util.WeakHashMap();
            break;
        default:
            throw new IllegalArgumentException("Invalid CHash type " + type_);
        }
    }

    public void clear() {
        impl.clear();
    }

    public Set keySet() {
        return impl.keySet();
    }

    public Set entrySet() {
        return impl.entrySet();
    }

    public boolean isEmpty() {
        return impl.isEmpty();
    }

    public boolean containsKey(Object key) {
        return impl.containsKey(key);
    }

    public Object get(Object key) {
        return impl.get(key);
    }

    public Object put(Object key, Object value) {
        return impl.put(key, value);
    }

    public void putAll(Map m) {
        impl.putAll(m);
    }

    public Object remove(Object key) {
        return impl.remove(key);
    }

    public int hashCode() {
        return impl.hashCode();
    }

    public boolean equals(Object o) {
        return impl.equals(o);
    }

    public Object vget(Object key) {
        Object x;
        if ((x = impl.get(key)) == null && !impl.containsKey(key)) {
            if (defaultFun != null) {
                return defaultFun.apply(key);
            }
            throw new NoSuchKeyException("Key not found (" + key + ")");
        }
        return x;
    }

    public void removeAll(AList keys) {
        if (keys != null && !keys.isEmpty())
            for (AIter i = keys; i != null; i = i.next())
                impl.remove(i.first());
    }

    public int size() {
        return impl.size();
    }

    public long length() {
        return impl.size();
    }

    public AList asList() {
        return new MList(impl.values().toArray());
    }

    public void setDefault(Fun fun) {
        defaultFun = fun;
    }
    
    public Object copy() {
        CHash result = new CHash(type, cons);
        result.putAll(this);
        result.defaultFun = defaultFun;
        return result;
    }
}
