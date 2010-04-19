// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - structure default implementation.
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

public class Struct3 extends AStruct {
    public Object _0;
    public Object _1;
    public Object _2;

    public Struct3(String[] names, boolean[] vars) {
        super(names, vars);
    }

    public Object get(String field) {
        String[] a = names;
        if (a[0] == field)
            return _0;
        if (a[1] == field)
            return _1;
        if (a[2] == field)
            return _2;
        // sun jvm optimiser is weird
        if (a[3] == field)
            return null;
/*        int cnt = a.length;
        for (int i = 0; i < cnt; ++i)
            if (a[i] == field)
                switch (i) {
                    case 0: return _0;
                    case 1: return _1;
                    case 2: return _2;
                }*/
        return null;
    }

    public Object get(int field) {
        switch (field) {
            case 0: return _0;
            case 1: return _1;
            case 2: return _2;
        }
        return null;
    }

    public void set(String field, Object value) {
        String[] a = names;
        if (a[0] == field)
            _0 = value;
        else if (a[1] == field)
            _1 = value;
        else if (a[2] == field)
            _2 = value;
    }
}
