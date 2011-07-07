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
import yeti.lang.*;

class ShowTypeFun extends Fun2 {
    Fun showType;
    Fun formatDoc;
    String indentStep = "   ";

    ShowTypeFun() {
        showType = this;
    }

    private void hstr(StringBuffer to, boolean variant,
                      AList fields, String indent) {
        boolean useNL = false;
        AIter i = fields;
        for (int n = 0; i != null; i = i.next())
            if (++n >= 3 || formatDoc != null && ((String) ((Struct)
                                i.first()).get("description")).length() > 0) {
                useNL = true;
                break;
            }

        String indent_ = indent, oldIndent = indent;
        if (useNL) {
            if (!variant)
                indent = indent.concat(indentStep);
            indent_ = indent.concat(indentStep);
        }

        String sep = variant
            ? useNL ? "\n" + indent + "| " : " | "
            : useNL ? ",\n".concat(indent) : ", ";

        for (i = fields; i != null; i = i.next()) {
            Struct field = (Struct) i.first();
            if (i != fields) // not first
                to.append(sep);
            else if (useNL && !variant)
                to.append('\n').append(indent);
            if (formatDoc != null) {
                String doc = (String) field.get("description");
                if (formatDoc != this) {
                    to.append(formatDoc.apply(indent, doc));
                } else if (useNL && doc.length() > 0) {
                    to.append("// ")
                      .append(Core.replace("\n", "\n" + indent + "//", doc))
                      .append('\n')
                      .append(indent);
                }
            }
            if (!variant) {
                if (field.get("mutable") == Boolean.TRUE)
                    to.append("var ");
                to.append(field.get("tag"));
            }
            to.append(field.get("name")).append(variant ? " " : " is ");
            Tag fieldType = (Tag) field.get("type");
            Object tstr = showType.apply(indent_, fieldType);
            if (variant && fieldType.name == "Function")
                to.append('(').append(tstr).append(')');
            else
                to.append(tstr);
        }
        if (useNL && !variant)
            to.append("\n").append(oldIndent);
    }

    public Object apply(Object indent, Object typeObj) {
        Tag type = (Tag) typeObj;
        String typeTag = type.name;
        if (typeTag == "Simple")
            return type.value;
        if (typeTag == "Alias") {
            Struct t = (Struct) type.value;
            return '(' + (String) showType.apply(indent, t.get("type"))
                 + " is " + t.get("alias") + ')';
        }

        AList typeList;
        String typeName = null;
        if (typeTag == "Parametric") {
            Struct t = (Struct) type.value;
            typeName = (String) t.get("type");
            typeList = (AList) t.get("params");
        } else {
            typeList = (AList) type.value;
        }
        if (typeList != null && typeList.isEmpty())
            typeList = null;
        AIter i = typeList;
        StringBuffer to = new StringBuffer();

        if (typeName != null) {
            to.append(typeName).append('<');
            for (; i != null; i = i.next()) {
                if (i != typeList)
                    to.append(", ");
                to.append(showType.apply(indent, i.first()));
            }
            to.append('>');
        } else if (typeTag == "Function") {
            while (i != null) {
                Tag t = (Tag) i.first();
                if (i != typeList)
                    to.append(" -> ");
                i = i.next();
                if (i != null && t.name == "Function")
                    to.append('(')
                      .append(showType.apply(indent, t))
                      .append(')');
                else
                    to.append(showType.apply(indent, t));
            }
        } else if (typeTag == "Struct") {
            to.append('{');
            hstr(to, false, typeList, (String) indent);
            to.append('}');
        } else if (typeTag == "Variant") {
            hstr(to, true, typeList, (String) indent);
        } else {
            throw new IllegalArgumentException("Unknown type kind: " + typeTag);
        }
        return to.toString();
    }
}

class TypeDescr extends YetiType {
    int type;
    String name;
    TypeDescr value;
    TypeDescr prev;
    String alias;
    Map properties;

    TypeDescr(String name_) {
        name = name_;
    }

    Tag force() {
        if (type == 0)
            return new Tag(name, "Simple");
        AList l = null;
        for (TypeDescr i = value; i != null; i = i.prev)
            if (i.properties != null) {
                i.properties.put("type", i.force());
                l = new LList(new GenericStruct(i.properties), l);
            } else {
                l = new LList(i.force(), l);
            }
        Object val = l;
        String tag = null;
        switch (type) {
        case FUN:
            tag = "Function"; break;
        case MAP:
            val = YetiC.pair("params", l, "type", name);
            tag = "Parametric"; break;
        case STRUCT:
            tag = "Struct"; break;
        case VARIANT:
            tag = "Variant"; break;
        }
        Tag res = new Tag(val, tag);
        if (alias == null)
            return res;
        return new Tag(YetiC.pair("alias", alias, "type", res), "Alias");
    }

    static Tag yetiType(YType t) {
        return prepare(t, new HashMap(), new HashMap()).force();
    }

    static void doc(ModuleType m, Fun f) {
        Struct3 st = new Struct3(new String[] { "doc", "name", "type" }, null);
        st._0 = m.topDoc == null ? Core.UNDEF_STR : m.topDoc;
        st._1 = m.name.replace('/', '.');
        st._2 = yetiType(m.type);
        f.apply(st);
    }

    private static void hdescr(TypeDescr descr, YType tt, Map vars, Map refs) {
        Map m = new java.util.TreeMap();
        if (tt.partialMembers != null)
            m.putAll(tt.partialMembers);
        if (tt.finalMembers != null) {
            Iterator i = tt.finalMembers.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                YType t = (YType) e.getValue();
                Object v = m.put(e.getKey(), t);
                if (v != null && t.doc == null)
                    t.doc = v;
            }
        }
        for (Iterator i = m.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry) i.next();
            Object name = e.getKey();
            YType t = (YType) e.getValue();
            Map it = new IdentityHashMap(5);
            String doc = t.doc();
            it.put("name", name);
            it.put("description", doc == null ? Core.UNDEF_STR : doc);
            it.put("mutable", Boolean.valueOf(t.field == FIELD_MUTABLE));
            it.put("tag",
                tt.finalMembers == null || !tt.finalMembers.containsKey(name)
                    ? "." :
                tt.partialMembers != null && tt.partialMembers.containsKey(name)
                    ? "`" : "");
            TypeDescr field = prepare(t, vars, refs);
            field.properties = it;
            field.prev = descr.value;
            descr.value = field;
        }
    }

    private static String getVarName(YType t, Map vars) {
        String v = (String) vars.get(t);
        if (v == null) {
            // 26^7 > 2^32, should be enough ;)
            char[] buf = new char[9];
            int p = buf.length;
            int n = vars.size() + 1;
            while (n > 26) {
                buf[--p] = (char) ('a' + n % 26);
                n /= 26;
            }
            buf[--p] = (char) (96 + n);
            if ((t.flags & FL_TAINTED_VAR) != 0)
                buf[--p] = '_';
            buf[--p] = (t.flags & FL_ORDERED_REQUIRED) == 0 ? '\'' : '^';
            v = new String(buf, p, buf.length - p);
            vars.put(t, v);
        }
        return v;
    }
    
    private static TypeDescr prepare(YType t, Map vars, Map refs) {
        final int type = t.type;
        if (type == VAR) {
            if (t.ref != null)
                return prepare(t.ref, vars, refs);
            return new TypeDescr(getVarName(t, vars));
        }
        if (type < PRIMITIVES.length)
            return new TypeDescr(TYPE_NAMES[type]);
        if (type == JAVA)
            return new TypeDescr(t.javaType.str());
        if (type == JAVA_ARRAY)
            return new TypeDescr(prepare(t.param[0], vars, refs)
                                    .name.concat("[]"));
        TypeDescr descr = (TypeDescr) refs.get(t), item;
        if (descr != null) {
            if (descr.alias == null)
                descr.alias = getVarName(t, vars);
            return new TypeDescr(descr.alias);
        }
        refs.put(t, descr = new TypeDescr(null));
        descr.type = type;
        YType[] param = t.param;
        switch (type) {
            case FUN:
                for (; t.type == FUN; param = t.param) {
                    (item = prepare(param[0], vars, refs)).prev = descr.value;
                    descr.value = item;
                    t = param[1].deref();
                }
                (item = prepare(t, vars, refs)).prev = descr.value;
                descr.value = item;
                break;
            case STRUCT:
            case VARIANT:
                hdescr(descr, t, vars, refs);
                break;
            case MAP:
                int n = 1;
                YType p1 = param[1].deref();
                YType p2 = param[2].deref();
                if (p2.type == LIST_MARKER) {
                    descr.name = p1.type == NONE ? "list" : p1.type == NUM
                                    ? "array" : "list?";
                } else {
                    descr.name = p2.type == MAP_MARKER || p1.type != NUM
                                    && p1.type != VAR ? "hash" : "map";
                    n = 2;
                }
                while (--n >= 0) {
                    (item = prepare(param[n], vars, refs)).prev = descr.value;
                    descr.value = item;
                }
                break;
            default:
                descr.name = "?" + type + '?';
                break;
        }
        return descr;
    }
}

class TypePattern {
    int[] idx; // if next is longer than idx, then it has wildcard path
    TypePattern[] next;
    String field; // struct/variant field match, next[0] when no such field
    int var; // if var != 0 then match stores type in typeVars as var
    YetiType.Scope end;

    TypePattern match(YType type, Map typeVars) {
        int i;

        type = type.deref();
        Object tv = typeVars.get(type);
        if (tv != null) {
            i = Arrays.binarySearch(idx, ((Integer) tv).intValue());
            if (i >= 0)
                return next[i];
        }
        i = Arrays.binarySearch(idx, type.type);
        if (i < 0 && (i = idx.length) >= next.length)
            return null;
        if (var != 0)
            typeVars.put(type, Integer.valueOf(var));
        TypePattern pat = next[i];
        if (pat.field == null) {
            YType[] param = type.param;
            if (param != null)
                for (i = 0; i < param.length && pat != null; ++i)
                    pat = pat.match(param[i], typeVars);
        } else {
            // TODO check var/non-var field
            // TODO check final/partial if necessary
            Map m = type.finalMembers;
            if (m == null)
                m = type.partialMembers;
            i = m.size();
            while (--i >= 0 && pat != null) {
                if (pat.field == null)
                    return null;
                type = (YType) m.get(pat.field);
                if (type != null)
                    pat = pat.match(type, typeVars);
                else // TODO can't to so simply - end marker can be here!
                    pat = pat.next[0];
            }
        }
        // go for type end marker
        if (pat != null && pat.idx[0] == Integer.MIN_VALUE)
            return pat.next[0];
        return null;
    }
}
