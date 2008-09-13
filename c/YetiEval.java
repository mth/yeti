// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler eval helper.
 *
 * Copyright (c) 2008 Madis Janson
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
package yeti.lang.compiler;

import java.util.*;

public class YetiEval {
    private static ThreadLocal instance = new ThreadLocal();
    List bindings = new ArrayList();

    static class Binding {
        private Object[] value;
        int index;
        int bindId;
        String name;
        YetiType.Type type;
        boolean mutable;
        boolean polymorph;

        Object val() {
            return value[index];
        }
    }

    static YetiEval get() {
        return (YetiEval) instance.get();
    }

    public static YetiEval set(YetiEval eval) {
        YetiEval old = (YetiEval) instance.get();
        instance.set(eval);
        return old;
    }

    static int registerBind(String name, YetiType.Type type,
                            boolean mutable, boolean polymorph) {
        Binding bind = new Binding();
        bind.name = name;
        bind.type = type;
        bind.mutable = mutable;
        bind.polymorph = polymorph && !mutable;
        List bindings = get().bindings;
        bind.bindId = bindings.size();
        bindings.add(bind);
        return bind.bindId;
    }

    public static void setBind(int binding, Object[] value, int index) {
        Binding bind = ((Binding) get().bindings.get(binding));
        bind.value = value;
        bind.index = index;
    }

    public static void setBind(int binding, Object value) {
        setBind(binding, new Object[] { value }, 0);
    }

    public static Object[] getBind(int binding) {
        return ((Binding) get().bindings.get(binding)).value;
    }
}
