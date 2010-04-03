// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti type pretty-printer.
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

package yeti.lang.compiler;

import java.util.*;
import yeti.lang.Core;

class TypePrettyPrinter extends YetiType {
    final List to = new ArrayList();
    private Map vars = new HashMap();
    private Map refs = new HashMap();
    
    public String toString() {
        String[] a = new String[to.size()];
        for (int i = 0; i < a.length; ++i)
            a[i] = to.get(i).toString();
        return Core.concat(a);
    }

    private void hstr(YType tt, String indent) {
        boolean variant = tt.type == VARIANT;
        Map m = new java.util.TreeMap();
        if (tt.partialMembers != null)
            m.putAll(tt.partialMembers);
        if (tt.finalMembers != null)
            m.putAll(tt.finalMembers);
        boolean useNL = m.size() >= 3;
        String indent_ = indent, oldIndent = indent;
        if (useNL) {
            if (!variant)
                indent = indent.concat("   ");
            indent_ = indent.concat("   ");
        }
        String sep = variant
            ? useNL ? "\n" + indent + "| " : " | "
            : useNL ? ",\n".concat(indent) : ", ";
        Iterator i = m.entrySet().iterator();
        boolean first = true;
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            if (first) {
                if (useNL && !variant) {
                    to.add("\n");
                    to.add(indent);
                }
                first = false;
            } else {
                to.add(sep);
            } 
            YType t = (YType) e.getValue();
            String doc = useNL ? t.doc() : null;
            if (doc != null) {
                to.add("// ");
                to.add(Core.replace("\n", "\n" + indent + "//", doc));
                to.add("\n");
                to.add(indent);
            }
            if (!variant && t.field == FIELD_MUTABLE)
                to.add("var ");
            if (!variant) {
                if (t.finalMembers == null ||
                        !t.finalMembers.containsKey(e.getKey())) {
                    to.add(".");
                } else if (t.partialMembers != null &&
                           t.partialMembers.containsKey(e.getKey())) {
                    to.add("`");
                }
            }
            to.add(e.getKey());
            to.add(variant ? " " : " is ");
            str(t, indent_);
        }
        if (useNL && !variant) {
            to.add("\n");
            to.add(oldIndent);
        }
    }

    private String getVarName(YType t) {
        String v = (String) vars.get(t);
        if (v == null) {
            // 26^7 > 2^32, should be enough ;)
            char[] buf = new char[8];
            int p = buf.length;
            int n = vars.size() + 1;
            while (n > 26) {
                buf[--p] = (char) ('a' + n % 26);
                n /= 26;
            }
            buf[--p] = (char) (96 + n);
            buf[--p] = (t.flags & FL_ORDERED_REQUIRED) == 0 ? '\'' : '^';
            v = new String(buf, p, buf.length - p);
            vars.put(t, v);
        }
        return v;
    }

    void str(YType t, String indent) {
        final int type = t.type;
        if (type == VAR) {
            if (t.ref != null) {
                str(t.ref, indent);
            } else {
                to.add(getVarName(t));
            }
            return;
        }
        if (type < PRIMITIVES.length) {
            to.add(TYPE_NAMES[type]);
            return;
        }
        if (type == JAVA) {
            to.add(t.javaType.str());
            return;
        }
        class Ref {
            String ref;
            int endIndex;

            public String toString() {
                if (ref == null)
                    return "";
                to.set(endIndex, " is " + ref + ')');
                return "(";
            }
        }
        Ref recRef = (Ref) refs.get(t);
        if (recRef == null) {
            refs.put(t, recRef = new Ref());
            to.add(recRef);
        } else {
            if (recRef.ref == null)
                recRef.ref = getVarName(t);
            to.add(recRef.ref);
            return;
        }
        final YType[] param = t.param;
        switch (type) {
            case FUN:
                if (param[0].deref().type == FUN) {
                    to.add("(");
                    str(param[0], indent);
                    to.add(")");
                } else {
                    str(param[0], indent);
                }
                to.add(" -> ");
                str(param[1], indent);
                break;
            case STRUCT:
                to.add("{");
                hstr(t, indent);
                to.add("}");
                break;
            case VARIANT:
                hstr(t, indent);
                break;
            case MAP:
                YType p1 = param[1].deref();
                YType p2 = param[2].deref();
                if (p2.type == LIST_MARKER) {
                    to.add(p1.type == NONE ? "list<" : p1.type == NUM
                                ? "array<" : "list?<");
                } else {
                    to.add(p2.type == MAP_MARKER || p1.type != NUM
                                && p1.type != VAR ? "hash<" : "map<");
                    str(p1, indent);
                    to.add(", ");
                }
                str(param[0], indent);
                to.add(">");
                break;
            case JAVA_ARRAY:
                str(param[0], indent);
                to.add("[]");
                break;
            default:
                to.add("?" + type + "?");
                break;
        }
        recRef.endIndex = to.size();
        to.add("");
    }
}
