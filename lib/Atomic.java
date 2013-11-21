// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - atomic reference.
 *
 * Copyright (c) 2012 Madis Janson
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

class AtomSet extends Fun2 {
    private final Atomic a;
    private final boolean cmp;

    AtomSet(Atomic a_, boolean cmp_) {
        a = a_;
        cmp = cmp_;
    }

    public Object apply(Object arg) {
        if (cmp)
            return new Fun2_(this, arg);
        return a.getAndSet(arg);
    }

    public Object apply(Object expect, Object update) {
        return a.compareAndSet(expect, update) ? Boolean.TRUE : Boolean.FALSE;
    }
}

class Atomic extends java.util.concurrent.atomic.AtomicReference
        implements Struct {
    public Atomic(Object value) {
        super(value);
    }

    public Object get(String field) {
        if (field == "value")
            return get();
        if (field == "compareAndSet")
            return get(0);
        return get(1); // swap
    }

    public Object get(int field) {
        switch (field) {
            case 0:  return new AtomSet(this, true);  // compareAndSet
            case 1:  return new AtomSet(this, false); // swap
            default: return get();
        }
    }

    public void set(String field, Object value) {
        if (field == "value")
            set(value);
    }

    public int count() {
        return 3;
    }

    public String name(int field) {
        switch (field) {
            case 0: return "compareAndSet";
            case 1: return "swap";
            default: return "value";
        }
    }

    public String eqName(int field) {
        return field == 2 ? "value" : "";
    }

    public Object ref(int field, int[] index, int at) {
        index[at + 1] = 0;
        if (field == 2) { // value
            index[at] = field;
            return this;
        }
        index[at] = -1;
        return get(field);
    }
}
