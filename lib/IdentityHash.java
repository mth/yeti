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

import java.util.Map;

/** Yeti core library - IdentityHash. */
public class IdentityHash
        extends java.util.IdentityHashMap implements ByKey, Coll {
    private Fun defaultFun;

    public IdentityHash() {
    }

    public IdentityHash(Map map) {
        super(map);
    }

    public Object vget(Object key) {
        Object x;
        if ((x = get(key)) == null && !containsKey(key)) {
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
                remove(i.first());
    }

    public long length() {
        return size();
    }

    public AList asList() {
        return new MList(values().toArray());
    }

    public void setDefault(Fun fun) {
        defaultFun = fun;
    }
    
    public Object copy() {
        IdentityHash result = new IdentityHash(this);
        result.defaultFun = defaultFun;
        return result;
    }
}
