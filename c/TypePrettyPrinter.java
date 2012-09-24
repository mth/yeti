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

        Struct field = null;
        for (i = fields; i != null; i = i.next()) {
            field = (Struct) i.first();
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
        try {
            if (field != null && field.get("strip") != null)
                to.append(sep).append("...");
        } catch (Exception ex) {
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
            return '(' + (String) t.get("alias") + " is " + 
                showType.apply(indent, t.get("type")) + ')';
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

class DescrCtx {
    TypePattern defs;
    Map vars = new HashMap();
    Map refs = new HashMap();
    List trace;

    String getVarName(YType t) {
        String v = (String) vars.get(t);
        if (v == null) {
            // 26^7 > 2^32, should be enough ;)
            char[] buf = new char[10];
            int p = buf.length;
            if ((t.flags & YetiType.FL_ERROR_IS_HERE) != 0)
                buf[--p] = '*';
            int n = vars.size() + 1;
            while (n > 26) {
                buf[--p] = (char) ('a' + n % 26);
                n /= 26;
            }
            buf[--p] = (char) (96 + n);
            if ((t.flags & YetiType.FL_TAINTED_VAR) != 0)
                buf[--p] = '_';
            buf[--p] =
                (t.flags & YetiType.FL_ORDERED_REQUIRED) == 0 ? '\'' : '^';
            v = new String(buf, p, buf.length - p);
            vars.put(t, v);
        }
        return v;
    }
}

class TypeDescr extends YetiType {
    private int type;
    private String name;
    private TypeDescr value;
    private TypeDescr prev;
    private String alias;
    private Map properties;

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

    static Tag yetiType(YType t, TypePattern defs, TypeException path) {
        DescrCtx ctx = new DescrCtx();
        ctx.defs = defs;
        if (path != null)
            ctx.trace = path.trace;
        return prepare(t, ctx).force();
    }

    static Tag typeDef(YType[] def, MList param, TypePattern defs) {
        DescrCtx ctx = new DescrCtx();
        ctx.defs = defs;
        for (int i = 0, n = 0; i < def.length - 1; ++i) {
            String name = def[i].doc instanceof String
                ? (String) def[i].doc : "t" + ++n;
            ctx.vars.put(def[i].deref(), name);
            param.add(name);
        }
        return prepare(def[def.length - 1], ctx).force();
    }

    private static void hdescr(TypeDescr descr, YType tt, DescrCtx ctx) {
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
        Object name;
        // Stupid list is used, because normally it shouldn't ever contain
        // over 1 or 2 elements, and it's faster than hash in this case.
        List strip = null;
        if (ctx.trace != null)
            for (int i = 0, last = ctx.trace.size() - 3; i <= last; i += 3)
                if (ctx.trace.get(i + 1) == tt || ctx.trace.get(i + 2) == tt) {
                    if (strip == null)
                        strip = new ArrayList();
                    if (m.containsKey(name = ctx.trace.get(i)) &&
                            !strip.contains(name))
                        strip.add(name);
                }
        if (strip != null && strip.size() >= m.size())
            strip = null; // everything is included, no stripping actually
        for (Iterator i = m.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry) i.next();
            name = e.getKey();
            if (strip != null && !strip.contains(name))
                continue;
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
            it.put("strip", strip);
            TypeDescr field = prepare(t, ctx);
            field.properties = it;
            field.prev = descr.value;
            descr.value = field;
        }
    }
    
    private static TypeDescr prepare(YType t, DescrCtx ctx) {
        final int type = t.type;
        if (type == VAR) {
            if (t.ref != null)
                return prepare(t.ref, ctx);
            return new TypeDescr(ctx.getVarName(t));
        }
        if (type < PRIMITIVES.length)
            return new TypeDescr(TYPE_NAMES[type]);
        if (type == JAVA)
            return new TypeDescr(t.javaType.str());
        if (type == JAVA_ARRAY)
            return new TypeDescr(prepare(t.param[0], ctx).name.concat("[]"));
        TypeDescr descr = (TypeDescr) ctx.refs.get(t), item;
        if (descr != null) {
            if (descr.alias == null)
                descr.alias = ctx.getVarName(t);
            return new TypeDescr(descr.alias);
        }
        final YType tt = t;
        ctx.refs.put(tt, descr = new TypeDescr(null));
        int varcount = ctx.vars.size();
        Map defVars = null;
        TypePattern def = null;
        if (ctx.defs != null &&
              (def = ctx.defs.match(t, defVars = new IdentityHashMap())) != null
              && def.end != null) {
            descr.name = def.end.typename;
            if (def.end.defvars.length == 0)
                return descr;
            descr.type = MAP; // Parametric
            Map param = new HashMap();
            for (Iterator i = defVars.entrySet().iterator(); i.hasNext();) {
                Map.Entry e = (Map.Entry) i.next();
                param.put(e.getValue(), e.getKey());
            }
            for (int i = def.end.defvars.length; --i >= 0; ) {
                t = (YType) param.get(Integer.valueOf(def.end.defvars[i]));
                item = t != null ? prepare(t, ctx) : new TypeDescr("?");
                item.prev = descr.value;
                descr.value = item;
            }
            return descr;
        }
        descr.type = type;
        YType[] param = t.param;
        int n = 1;
        switch (type) {
            case FUN:
                for (; t.type == FUN; param = t.param) {
                    item = prepare(param[0], ctx);
                    item.prev = descr.value;
                    descr.value = item;
                    t = param[1].deref();
                }
                (item = prepare(t, ctx)).prev = descr.value;
                descr.value = item;
                break;
            case STRUCT:
            case VARIANT:
                hdescr(descr, t, ctx);
                t = t.param[0].deref();
                if ((t.flags & FL_ERROR_IS_HERE) != 0)
                    descr.alias = ctx.getVarName(t);
                break;
            case MAP:
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
            default:
                if (type >= OPAQUE_TYPES) {
                    descr.type = MAP;
                    descr.name = "opaque" + (type - OPAQUE_TYPES);
                    n = param.length;
                } else if (type != MAP) {
                    descr.name = "?" + type + '?';
                    break;
                }
                while (--n >= 0) {
                    item = prepare(param[n], ctx);
                    item.prev = descr.value;
                    descr.value = item;
                }
        }
        // don't create ('foo is ...) when there is no free variables in ...
        if (varcount == ctx.vars.size() && descr.alias == null)
            ctx.refs.remove(tt);
        return descr;
    }
}

class TypeWalk implements Comparable {
    int id;
    private YType type;
    private int st;
    private TypeWalk parent;
    private String[] fields;
    private Map fieldMap;
    String field;
    TypePattern pattern;
    String typename;
    YType[] def;
    int[] defvars;

    TypeWalk(YType t, TypeWalk parent, Map tvars, TypePattern p) {
        pattern = p;
        this.parent = parent;
        type = t = t.deref();
        TypePattern tvar = (TypePattern) tvars.get(t);
        if (tvar != null) {
            id = tvar.var;
            if (id > 0)
                tvar.var = id = -id; // mark used
            return;
        }
        id = t.type;
        if (id == YetiType.VAR) {
            if (tvars.containsKey(t)) {
                id = Integer.MAX_VALUE; // typedef parameter - match anything
                if (p != null && p.var >= 0)
                    p.var = -p.var; // parameters must be saved
            } else if (parent != null && parent.type.type == YetiType.MAP &&
                       parent.st > 1 && (parent.st > 2 ||
                            parent.type.param[2] == YetiType.LIST_TYPE)) {
                id = Integer.MAX_VALUE; // map kind - match anything
                return; // and don't associate
            }
            tvars.put(t, p);
        } else if (id >= YetiType.PRIMITIVES.length) {
            tvars.put(t, p);
        }
        if (id == YetiType.STRUCT || id == YetiType.VARIANT) {
            fieldMap = t.finalMembers != null ? t.finalMembers
                                              : t.partialMembers;
            fields = (String[])
                fieldMap.keySet().toArray(new String[fieldMap.size()]);
            Arrays.sort(fields);
        }
    }

    TypeWalk next(Map tvars, TypePattern pattern) {
        if (id < 0 || id == Integer.MAX_VALUE) {
            if (parent != null)
                return parent.next(tvars, pattern);
            if (def != null) {
                pattern.end = this;
                defvars = new int[def.length - 1];
                for (int i = 0; i < defvars.length; ++i)
                    if ((pattern = (TypePattern) tvars.get(def[i])) != null)
                        defvars[i] = pattern.var;
            }
            return null;
        }
        if (fields == null) {
            if (type.param != null && st < type.param.length)
                return new TypeWalk(type.param[st++], this, tvars, pattern);
        } else if (st < fields.length) {
            YType t = (YType) fieldMap.get(fields[st]);
            TypeWalk res = new TypeWalk(t, this, tvars, pattern);
            res.field = fields[st++];
            if (t.field == YetiType.FIELD_MUTABLE)
                res.field = ";".concat(res.field);
            return res;
        }
        field = null;
        id = Integer.MIN_VALUE;
        return this;
    }

    public int compareTo(Object o) {
        TypeWalk tw = (TypeWalk) o;
        if (field == null)
            return tw.field == null ? id - tw.id : 1;
        if (tw.field == null)
            return -1;
        int cmp = field.compareTo(tw.field);
        return cmp == 0 ? id - tw.id : cmp;
    }
}

class TypePattern {
    // Integer.MIN_VALUE is type end marker
    // Integer.MAX_VALUE matches any type
    private int[] idx;
    private TypePattern[] next;
    // struct/variant field match, next[idx.length] when no such field
    private String field;
    private boolean mutable;
    int var; // if var < 0 then match stores type in typeVars as var
    TypeWalk end; // end result

    TypePattern(int var) {
        this.var = var;
    }

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
        if (i < 0) {
            if (idx[i = idx.length - 1] != Integer.MAX_VALUE)
                return null;
            if (var < 0)
                typeVars.put(type, Integer.valueOf(var));
            return next[i];
        }
        if (var < 0)
            typeVars.put(type, Integer.valueOf(var));
        TypePattern pat = next[i];
        if (pat.field == null) {
            YType[] param = type.param;
            if (param != null)
                for (i = 0; i < param.length && pat != null; ++i)
                    pat = pat.match(param[i], typeVars);
        } else {
            // TODO check final/partial if necessary
            Map m = type.finalMembers;
            if (m == null)
                m = type.partialMembers;
            i = m.size();
            while (--i >= 0 && pat != null) {
                if (pat.field == null)
                    return null;
                type = (YType) m.get(pat.field);
                if (type != null &&
                        type.field == YetiType.FIELD_MUTABLE == mutable) {
                    pat = pat.match(type, typeVars);
                } else {
                    pat = pat.next[pat.idx.length];
                    ++i; // was not matched
                }
            }
        }
        // go for type end marker
        if (pat != null && pat.idx[0] == Integer.MIN_VALUE)
            return pat.next[0];
        return null;
    }

    static TypePattern toPattern(Map typedefs) {
        int j = 0, varAlloc = 1;
        TypePattern presult = new TypePattern(varAlloc);
        TypeWalk[] w = new TypeWalk[typedefs.size()];
        Map tvars = new IdentityHashMap();
        for (Iterator i = typedefs.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry) i.next();
            YType[] def = (YType[]) e.getValue();
            YType t = def[def.length - 1].deref();
            if (t.type < YetiType.PRIMITIVES.length)
                continue;
            for (int k = def.length - 1; --k >= 0; )
                tvars.put(def[k].deref(), null); // mark as param
            w[j] = new TypeWalk(t, null, tvars, presult);
            w[j].typename = (String) e.getKey();
            w[j++].def = def;
        }
        if (j == 0)
            return null;
        TypeWalk[] wg = new TypeWalk[j];
        System.arraycopy(w, 0, wg, 0, j);
        int[] ids = new int[j];
        TypePattern[] patterns = new TypePattern[j];
        List walkers = new ArrayList();
        walkers.add(wg); // types
        walkers.add(presult); // resulting pattern
        walkers.add(tvars);
        while (walkers.size() > 0) {
            List current = walkers;
            walkers = new ArrayList();
            for (int i = 0, cnt = current.size(); i < cnt; i += 3) {
                w = (TypeWalk[]) current.get(i);
                Arrays.sort(w);
                // group by different types
                // next - target for group in next cycle
                TypePattern next = new TypePattern(++varAlloc),
                    target = (TypePattern) current.get(i + 1);
                String field = w.length != 0 ? w[0].field : null;
                int start = 0, n = 0, e;
                for (j = 1; j <= w.length; ++j) {
                    if (j < w.length && w[j].id == w[j - 1].id &&
                            (field == w[j].field || field.equals(w[j].field)))
                        continue; // skip until same
                    // add branch
                    tvars = new IdentityHashMap((Map) current.get(i + 2));
                    ids[n] = w[j - 1].id;
                    for (int k = e = start; k < j; ++k)
                        if ((w[e] = w[k].next(tvars, next)) != null)
                            ++e;
                    wg = new TypeWalk[e - start];
                    System.arraycopy(w, start, wg, 0, wg.length);
                    walkers.add(wg);
                    walkers.add(patterns[n++] = next);
                    walkers.add(tvars);
                    next = new TypePattern(++varAlloc);
                    start = j;
                    if (j < w.length &&
                            (field == w[j].field || field.equals(w[j].field)))
                        continue; // continue same pattern
                    target.idx = new int[n];
                    System.arraycopy(ids, 0, target.idx, 0, n);
                    if (field != null) {
                        if (field.charAt(0) == ';') {
                            field = field.substring(1).intern();
                            target.mutable = true;
                        }
                        target.field = field;
                        target.next = new TypePattern[n + 1];
                        System.arraycopy(patterns, 0, target.next, 0, n);
                        if (j < w.length) {
                            field = w[j].field;
                            target.next[n] = next;
                            target = next;
                            next = new TypePattern(++varAlloc);
                        }
                    } else {
                        target.next = new TypePattern[n];
                        System.arraycopy(patterns, 0, target.next, 0, n);
                    }
                    n = 0;
                }
            }
        }
        return presult;
    }

    static TypePattern toPattern(Scope scope) {
        Map typedefs = new HashMap();
        for (; scope != null; scope = scope.outer)
            if (scope.typeDef != null) {
                Object old = typedefs.put(scope.name, scope.typeDef);
                if (old != null)
                    typedefs.put(scope.name, old);
            }
        return toPattern(typedefs);
    }
/*
    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (var < 0)
            sb.append(var).append(':');
        if (field != null)
            sb.append(field).append(' ');
        sb.append('{');
        if (next == null)
            sb.append('-');
        else for (int i = 0; i < next.length; ++i) {
            if (i != 0)
                sb.append(", ");
            if (i >= idx.length) {
                sb.append('!');
            } else if (idx[i] == Integer.MIN_VALUE) {
                sb.append('.');
            } else if (idx[i] == Integer.MAX_VALUE) {
                sb.append('_');
            } else {
                sb.append(idx[i]);
            }
            sb.append(" => ").append(next[i]);
        }
        sb.append('}');
        if (end != null) {
            sb.append(':').append(end.typename).append('<');
            for (int i = 0; i < end.defvars.length; ++i) {
                if (i != 0)
                    sb.append(',');
                sb.append(end.defvars[i]);
            }
            sb.append('>');
        }
        return sb.toString();
    }

    static String showres(TypePattern res, Map vars) {
        if (res == null)
            return "FAIL";
        Map rvars = new HashMap();
        for (Iterator i = vars.entrySet().iterator(); i.hasNext();) {
            Map.Entry e = (Map.Entry) i.next();
            rvars.put(e.getValue(), e.getKey());
        }
        StringBuffer r = new StringBuffer(res.end.typename).append('<');
        for (int i = 0; i < res.end.defvars.length; ++i) {
            if (i != 0)
                r.append(", ");
            YType t = (YType) rvars.get(Integer.valueOf(res.end.defvars[i]));
            if (t != null)
                r.append(t);
            else
                r.append("t" + i);
        }
        return r + ">";
    }

    public static void main(String[] _) {
        YType st = new YType(YetiType.STRUCT, null);
        st.finalMembers = new HashMap();
        st.finalMembers.put("close", YetiType.fun(YetiType.UNIT_TYPE, YetiType.UNIT_TYPE));
        st.finalMembers.put("read", YetiType.fun(YetiType.A, YetiType.STR_TYPE));
        YType st2 = new YType(YetiType.STRUCT, null);
        st2.finalMembers = new HashMap();
        st2.finalMembers.put("close", YetiType.fun(YetiType.UNIT_TYPE, YetiType.UNIT_TYPE));
        st2.finalMembers.put("write", YetiType.fun(YetiType.STR_TYPE, YetiType.UNIT_TYPE));
        //YType[] types = {YetiType.CONS_TYPE, st};
        Map defs = new HashMap();
        defs.put("cons", new YType[] { YetiType.A, YetiType.CONS_TYPE });
        defs.put("str_pred", new YType[] { YetiType.STR2_PRED_TYPE });
        defs.put("str_array", new YType[] { YetiType.STRING_ARRAY });
        defs.put("my_struct", new YType[] { YetiType.A, st });
        defs.put("a_struct", new YType[] { st2 });
        TypePattern res, pat = toPattern(defs);
        System.err.println(pat);
        for (Iterator i = defs.values().iterator(); i.hasNext(); ) {
            YType[] def = (YType[]) i.next();
            YType t = def[def.length - 1];
            Map vars = new IdentityHashMap();
            System.out.println(t + " " + showres(pat.match(t, vars), vars));
        }
        YType intlist = new YType(YetiType.MAP, new YType[] {
            YetiType.NUM_TYPE, YetiType.NO_TYPE, YetiType.LIST_TYPE });
        YType il2il = YetiType.fun2Arg(YetiType.NUM_TYPE, intlist, intlist);
        Map vars = new IdentityHashMap();
        res = pat.match(il2il, vars);
        System.out.println(il2il + " " + showres(pat.match(il2il, vars), vars));
    }*/
}
