// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti type analyzer.
 * Uses Hindley-Milner type inference algorithm
 * with extensions for polymorphic structs and variants.
 * Copyright (c) 2007,2008 Madis Janson
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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class YetiType implements YetiParser, YetiBuiltins {
    static final int VAR  = 0;
    static final int UNIT = 1;
    static final int STR  = 2;
    static final int NUM  = 3;
    static final int BOOL = 4;
    static final int CHAR = 5;
    static final int NONE = 6;
    static final int LIST_MARKER = 7;
    static final int MAP_MARKER  = 8;
    static final int FUN  = 9; // a -> b
    static final int MAP  = 10; // value, index, (LIST | MAP)
    static final int STRUCT = 11;
    static final int VARIANT = 12;
    static final int JAVA = 13;
    static final int JAVA_ARRAY = 14;

    static final int FL_ORDERED_REQUIRED = 1;
    static final int FL_ANY_PATTERN = 0x4000;
    static final int FL_PARTIAL_PATTERN  = 0x8000;

    static final int FIELD_NON_POLYMORPHIC = 1;
    static final int FIELD_MUTABLE = 2;

    static final Type[] NO_PARAM = {};
    static final Type UNIT_TYPE = new Type(UNIT, NO_PARAM);
    static final Type NUM_TYPE  = new Type(NUM,  NO_PARAM);
    static final Type STR_TYPE  = new Type(STR,  NO_PARAM);
    static final Type BOOL_TYPE = new Type(BOOL, NO_PARAM);
    static final Type CHAR_TYPE = new Type(CHAR, NO_PARAM);
    static final Type NO_TYPE   = new Type(NONE, NO_PARAM);
    static final Type LIST_TYPE = new Type(LIST_MARKER, NO_PARAM);
    static final Type MAP_TYPE  = new Type(MAP_MARKER, NO_PARAM);
    static final Type ORDERED = orderedVar(1);
    static final Type A = new Type(1);
    static final Type B = new Type(1);
    static final Type C = new Type(1);
    static final Type EQ_TYPE = fun2Arg(A, A, BOOL_TYPE);
    static final Type LG_TYPE = fun2Arg(ORDERED, ORDERED, BOOL_TYPE);
    static final Type NUMOP_TYPE = fun2Arg(NUM_TYPE, NUM_TYPE, NUM_TYPE);
    static final Type BOOLOP_TYPE = fun2Arg(BOOL_TYPE, BOOL_TYPE, BOOL_TYPE);
    static final Type A_B_LIST_TYPE =
        new Type(MAP, new Type[] { A, B, LIST_TYPE });
    static final Type NUM_LIST_TYPE =
        new Type(MAP, new Type[] { NUM_TYPE, B, LIST_TYPE });
    static final Type A_B_MAP_TYPE =
        new Type(MAP, new Type[] { B, A, MAP_TYPE });
    static final Type A_B_C_MAP_TYPE =
        new Type(MAP, new Type[] { B, A, C });
    static final Type A_LIST_TYPE =
        new Type(MAP, new Type[] { A, NO_TYPE, LIST_TYPE });
    static final Type A_ARRAY_TYPE =
        new Type(MAP, new Type[] { A, NUM_TYPE, LIST_TYPE });
    static final Type C_LIST_TYPE =
        new Type(MAP, new Type[] { C, NO_TYPE, LIST_TYPE });
    static final Type STRING_ARRAY =
        new Type(MAP, new Type[] { STR_TYPE, NUM_TYPE, LIST_TYPE });
    static final Type CONS_TYPE = fun2Arg(A, A_B_LIST_TYPE, A_LIST_TYPE);
    static final Type LAZYCONS_TYPE =
        fun2Arg(A, fun(UNIT_TYPE, A_B_LIST_TYPE), A_LIST_TYPE);
    static final Type A_TO_BOOL = fun(A, BOOL_TYPE);
    static final Type LIST_TO_A = fun(A_B_LIST_TYPE, A);
    static final Type MAP_TO_BOOL = fun(A_B_C_MAP_TYPE, BOOL_TYPE);
    static final Type LIST_TO_LIST = fun(A_B_LIST_TYPE, A_LIST_TYPE);
    static final Type IN_TYPE = fun2Arg(A, A_B_MAP_TYPE, BOOL_TYPE);
    static final Type COMPOSE_TYPE = fun2Arg(fun(B, C), fun(A, B), fun(A, C));
    static final Type BOOL_TO_BOOL = fun(BOOL_TYPE, BOOL_TYPE);
    static final Type NUM_TO_NUM = fun(NUM_TYPE, NUM_TYPE);
    static final Type FOR_TYPE =
        fun2Arg(A_B_LIST_TYPE, fun(A, UNIT_TYPE), UNIT_TYPE);
    static final Type STR2_PRED_TYPE = fun2Arg(STR_TYPE, STR_TYPE, BOOL_TYPE);
    static final Type SYNCHRONIZED_TYPE = fun2Arg(A, fun(UNIT_TYPE, B), B);
    static final Type CLASS_TYPE = new Type("Ljava/lang/Class;");
    static final Type OBJECT_TYPE = new Type("Ljava/lang/Object;");

    static final Type[] PRIMITIVES =
        { null, UNIT_TYPE, STR_TYPE, NUM_TYPE, BOOL_TYPE, CHAR_TYPE,
          NO_TYPE, LIST_TYPE, MAP_TYPE };

    static final String[] TYPE_NAMES =
        { "var", "()", "string", "number", "boolean", "char",
          "none", "list", "hash", "fun", "list", "struct", "variant",
          "object" };

    static final Scope ROOT_SCOPE =
        bindCompare("==", EQ_TYPE, COND_EQ, // equals returns 0 for false
        bindCompare("!=", EQ_TYPE, COND_NOT, // equals returns 0 for false
        bindCompare("<" , LG_TYPE, COND_LT,
        bindCompare("<=", LG_TYPE, COND_LE,
        bindCompare(">" , LG_TYPE, COND_GT,
        bindCompare(">=", LG_TYPE, COND_GE,
        bindPoly("_argv", STRING_ARRAY, new Argv(), 0,
        bindPoly(".", COMPOSE_TYPE, new Compose(), 0,
        bindCore("randomInt", fun(NUM_TYPE, NUM_TYPE), "RANDINT",
        bindPoly("in", IN_TYPE, new InOp(), 0,
        bindPoly("::", CONS_TYPE, new Cons(), 0,
        bindPoly(":.", LAZYCONS_TYPE, new LazyCons(), 0,
        bindPoly("for", FOR_TYPE, new For(), 0,
        bindPoly("nullptr?", A_TO_BOOL, new IsNullPtr(A_TO_BOOL, "nullptr?"), 0,
        bindPoly("defined?", A_TO_BOOL, new IsDefined(), 0,
        bindPoly("empty?", MAP_TO_BOOL, new IsEmpty(), 0,
        bindPoly("same?", EQ_TYPE, new Same(), 0,
        bindPoly("head", LIST_TO_A, new Head(), 0,
        bindPoly("tail", LIST_TO_LIST, new Tail(), 0,
        bindPoly("synchronized", SYNCHRONIZED_TYPE, new Synchronized(), 0,
        bindArith("+", "add", bindArith("-", "sub",
        bindArith("*", "mul", bindArith("/", "div",
        bindArith("%", "rem", bindArith("div", "intDiv",
        bindArith("shl", "shl", bindArith("shr", "shr",
        bindScope("=~", new MatchOpFun(),
        bindScope("not", new NotOp(),
        bindScope("and", new BoolOpFun(false),
        bindScope("or", new BoolOpFun(true),
        bindScope("false", new BooleanConstant(false),
        bindScope("true", new BooleanConstant(true),
        bindScope("negate", new Negate(),
        bindStr("strLength", fun(STR_TYPE, NUM_TYPE), "length", "()I",
        bindStr("strUpper", fun(STR_TYPE, STR_TYPE), "toUpperCase",
                "()Ljava/lang/String;",
        bindStr("strLower", fun(STR_TYPE, STR_TYPE), "toLowerCase",
                "()Ljava/lang/String;",
        bindStr("strTrim", fun(STR_TYPE, STR_TYPE), "trim",
                "()Ljava/lang/String;",
        bindStr("strSlice",
                fun2Arg(STR_TYPE, NUM_TYPE, fun(NUM_TYPE, STR_TYPE)),
                "substring", "(II)Ljava/lang/String;",
        bindStr("strRight", fun2Arg(STR_TYPE, NUM_TYPE, STR_TYPE),
                "substring", "(I)Ljava/lang/String;",
        bindStr("strStarts", fun2Arg(STR_TYPE, STR_TYPE, BOOL_TYPE),
                "startsWith", "(Ljava/lang/String;)Z",
        bindStr("strEnds", fun2Arg(STR_TYPE, STR_TYPE, BOOL_TYPE),
                "endsWith", "(Ljava/lang/String;)Z",
        bindStr("strIndexOf",
                fun2Arg(STR_TYPE, STR_TYPE, fun(NUM_TYPE, NUM_TYPE)),
                "indexOf", "(Ljava/lang/String;I)I",
        bindStr("strLastIndexOf",
                fun2Arg(STR_TYPE, STR_TYPE, fun(NUM_TYPE, NUM_TYPE)),
                "lastIndexOf", "(Ljava/lang/String;I)I",
        bindRegex("strSplit", "yeti/lang/StrSplit",
                  fun2Arg(STR_TYPE, STR_TYPE, STRING_ARRAY), 
        bindRegex("like", "yeti/lang/Like",
                  fun2Arg(STR_TYPE, STR_TYPE, fun(UNIT_TYPE, STRING_ARRAY)), 
        bindRegex("substAll", "yeti/lang/SubstAll",
                  fun2Arg(STR_TYPE, STR_TYPE, fun(STR_TYPE, STR_TYPE)), 
        bindRegex("matchAll", "yeti/lang/MatchAll",
                  fun2Arg(STR_TYPE, fun(STRING_ARRAY, A),
                  fun2Arg(fun(STR_TYPE, A), STR_TYPE, A_ARRAY_TYPE)), 
        bindImport("EmptyArray", "yeti/lang/EmptyArrayException",
        bindImport("NoSuchKey", "yeti/lang/NoSuchKeyException",
        bindImport("Exception", "java/lang/Exception",
        bindImport("Math", "java/lang/Math",
        null)))))))))))))))))))))))))))))))))))))))))))))))))))));

    static final Scope ROOT_SCOPE_SYS =
        bindImport("System", "java/lang/System", ROOT_SCOPE);

    static Scope bindScope(String name, Binder binder, Scope scope) {
        return new Scope(scope, name, binder);
    }

    static Scope bindCompare(String op, Type type, int code, Scope scope) {
        return bindPoly(op, type, new Compare(type, code, op), 0, scope);
    }

    static Scope bindArith(String op, String method, Scope scope) {
        return bindScope(op, new ArithOpFun(op, method, NUMOP_TYPE), scope);
    }

    static Scope bindCore(String name, Type type, String field, Scope scope) {
        return bindPoly(name, type, new CoreFun(type, field), 0, scope);
    }

    static Scope bindStr(String name, Type type, String method, String sig,
                         Scope scope) {
        return bindScope(name, new StrOp(name, method, sig, type), scope);
    }

    static Scope bindRegex(String name, String impl, Type type, Scope scope) {
        return bindPoly(name, type, new Regex(name, impl, type), 0, scope);
    }

    static Scope bindImport(String name, String className, Scope scope) {
        scope = new Scope(scope, name, null);
        scope.importClass = new Type('L' + className + ';');
        return scope;
    }

    static Type fun(Type a, Type res) {
        return new Type(FUN, new Type[] { a, res });
    }

    static Type fun2Arg(Type a, Type b, Type res) {
        return new Type(FUN,
            new Type[] { a, new Type(FUN, new Type[] { b, res }) });
    }

    static Type variantOf(String[] na, Type[] ta) {
        Type t = new Type(VARIANT, ta);
        t.partialMembers = new HashMap();
        for (int i = 0; i < na.length; ++i) {
            t.partialMembers.put(na[i], ta[i]);
        }
        return t;
    }

    static final class Type {
        int type;
        Map partialMembers;
        Map finalMembers;

        Type[] param;
        Type ref;
        int depth;
        int flags;
        int field;
        boolean seen;

        JavaType javaType;

        Type(int depth) {
            this.depth = depth;
        }

        Type(int type, Type[] param) {
            this.type = type;
            this.param = param;
        }

        Type(String javaSig) {
            type = JAVA;
            this.javaType = JavaType.fromDescription(javaSig);
            param = NO_PARAM;
        }

        private String hstr(Map vars, Map refs) {
            StringBuffer res = new StringBuffer();
            boolean variant = type == VARIANT;
            Map m = new java.util.TreeMap();
            if (partialMembers != null)
                m.putAll(partialMembers);
            if (finalMembers != null)
                m.putAll(finalMembers);
            boolean useNL = m.size() >= 10;
            String sep = variant ? " | " : useNL ? ",\n" : ", ";
            Iterator i = m.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                if (res.length() != 0)
                    res.append(sep);
                Type t = (Type) e.getValue();
                if (!variant && t.field == FIELD_MUTABLE)
                    res.append("var ");
                if (!variant && (finalMembers == null ||
                        !finalMembers.containsKey(e.getKey())))
                    res.append('.');
                res.append(e.getKey());
                res.append(variant ? " " : " is ");
                res.append(t.str(vars, refs));
            }
            return res.toString();
        }

        private String getVarName(Map vars) {
            String v = (String) vars.get(this);
            if (v == null) {
                v = (flags & FL_ORDERED_REQUIRED) == 0 ? "'" : "^";
                int n = vars.size();
                do {
                    v += (char) ('a' + n % 26);
                    n /= 26;
                } while (n > 0);
                vars.put(this, v);
            }
            return v;
        }

        String str(Map vars, Map refs) {
            if (ref != null) {
                return ref.str(vars, refs);
            }
            if (type == VAR) {
                return getVarName(vars);
            }
            if (type < PRIMITIVES.length) {
                return TYPE_NAMES[type];
            }
            String[] recRef = (String[]) refs.get(this);
            if (recRef == null) {
                refs.put(this, recRef = new String[1]);
            } else {
                if (recRef[0] == null) {
                    recRef[0] = getVarName(vars);
                }
                return recRef[0];
            }
            String res = null;
            switch (type) {
                case FUN:
                    res = (param[0].deref().type == FUN
                        ? "(" + param[0].str(vars, refs) + ")"
                        : param[0].str(vars, refs))
                        + " -> " + param[1].str(vars, refs);
                    break;
                case STRUCT:
                    res = "{" + hstr(vars, refs) + "}";
                    break;
                case VARIANT:
                    res = hstr(vars, refs);
                    break;
                case MAP:
                    Type p1 = param[1].deref();
                    Type p2 = param[2].deref();
                    res = p2.type == LIST_MARKER
                        ? (p1.type == NONE ? "list<" : p1.type == NUM
                            ? "array<" : "list?<")
                          + param[0].str(vars, refs) + ">"
                        : p2.type == MAP_MARKER ||
                          p1.type != NUM && p1.type != VAR
                            ? "hash<" + p1.str(vars, refs) + ", "
                                      + param[0].str(vars, refs) + ">"
                            :  "map<" + p1.str(vars, refs) + ", "
                                      + param[0].str(vars, refs) + ">";
                    break;
                case JAVA:
                    res = javaType.str(vars, refs, param);
                    break;
                case JAVA_ARRAY:
                    res = param[0].str(vars, refs) + "[]";
                    break;
                default:
                    return TYPE_NAMES[type];
            }
            return recRef[0] == null
                    ? res : "(" + res + " is " + recRef[0] + ")";
        }

        public String toString() {
            return str(new HashMap(), new HashMap());
        }
        
        Type deref() {
            Type res = this;
            while (res.ref != null) {
                res = res.ref;
            }
            for (Type next, type = this; type.ref != null; type = next) {
                next = type.ref;
                type.ref = res;
            }
            return res;
        }

    }

    static Type mutableFieldRef(Type src) {
        Type t = new Type(src.depth);
        t.ref = src.ref;
        t.flags = src.flags;
        t.field = FIELD_MUTABLE;
        return t;
    }

    static Type fieldRef(int depth, Type ref, int kind) {
        Type t = new Type(depth);
        t.ref = ref.deref();
        t.field = kind;
        return t;
    }

    static final class Scope {
        Scope outer;
        String name;
        Binder binder;
        Type[] free;
        Closure closure; // non-null means outer scopes must be proxied
        Type importClass;
        String packageName;

        public Scope(Scope outer, String name, Binder binder) {
            this.outer = outer;
            this.name = name;
            this.binder = binder;
            packageName = outer == null ? null : outer.packageName;
        }
    }

    static Type orderedVar(int maxDepth) {
        Type type = new Type(maxDepth);
        type.flags = FL_ORDERED_REQUIRED;
        return type;
    }

    static void limitDepth(Type type, int maxDepth) {
        type = type.deref();
        if (type.type != VAR) {
            if (type.seen) {
                return;
            }
            type.seen = true;
            for (int i = type.param.length; --i >= 0;) {
                limitDepth(type.param[i], maxDepth);
            }
            type.seen = false;
        } else if (type.depth > maxDepth) {
            type.depth = maxDepth;
        }
    }

    static class TypeException extends Exception {
        boolean special;

        TypeException(String what) {
            super(what);
        }
    }

    static void mismatch(Type a, Type b) throws TypeException {
        throw new TypeException("Type mismatch: " + a + " is not " + b);
    }

    static void finalizeStruct(Type partial, Type src) throws TypeException {
        if (src.finalMembers == null || partial.partialMembers == null /*||
                partial.finalMembers != null*/) {
            return; // nothing to check
        }
        Iterator i = partial.partialMembers.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry) i.next();
            Type ff = (Type) src.finalMembers.get(entry.getKey());
            if (ff == null) {
                throw new TypeException("Type mismatch: " + src + " => "
                       + partial + " (member missing: " + entry.getKey() + ")");
            }
            Type partField = (Type) entry.getValue();
            if (partField.field == FIELD_MUTABLE && ff.field != FIELD_MUTABLE) {
                throw new TypeException("Field '" + entry.getKey()
                    + "' constness mismatch: " + src + " => " + partial);
            }
            unify(partField, ff);
        }
    }

    static void unifyMembers(Type a, Type b) throws TypeException {
        b.ref = a; // just fake ref now to avoid cycles...
        Map ff;
        if (((a.flags ^ b.flags) & FL_ORDERED_REQUIRED) != 0) {
            // VARIANT types are sometimes ordered.
            // when all their variant parameters are ordered types.
            if ((a.flags & FL_ORDERED_REQUIRED) != 0) {
                requireOrdered(b);
            } else {
                requireOrdered(a);
            }
        }
        if (a.finalMembers == null) {
            ff = b.finalMembers;
        } else if (b.finalMembers == null) {
            ff = a.finalMembers;
        } else {
            // unify final members
            ff = new HashMap(a.finalMembers);
            for (Iterator i = ff.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                Type f = (Type) b.finalMembers.get(entry.getKey());
                if (f != null) {
                    Type t = (Type) entry.getValue();
                    unify(f, t);
                    // constness spreads
                    if (t.field != f.field) {
                        if (t.field == 0) {
                            entry.setValue(t = f);
                        }
                        t.field = FIELD_NON_POLYMORPHIC;
                    }
                } else {
                    i.remove();
                }
            }
            if (ff.isEmpty()) {
                mismatch(a, b);
            }
        }
        finalizeStruct(a, b);
        finalizeStruct(b, a);
        if (a.partialMembers == null) {
            a.partialMembers = b.partialMembers;
        } else if (b.partialMembers != null) {
            // join partial members
            Iterator i = a.partialMembers.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry) i.next();
                Type f = (Type) b.partialMembers.get(entry.getKey());
                if (f != null) {
                    unify((Type) entry.getValue(), f);
                    // mutability spreads
                    if (f.field >= FIELD_NON_POLYMORPHIC) {
                        entry.setValue(f);
                    }
                }
            }
            a.partialMembers.putAll(b.partialMembers);
        }
        a.finalMembers = ff;
        if (ff == null) {
            ff = a.partialMembers;
        } else if (a.partialMembers != null) {
            ff = new HashMap(ff);
            ff.putAll(a.partialMembers);
        }
        a.param = (Type[]) ff.values().toArray(new Type[ff.size()]);
        b.type = VAR;
        b.ref = a;
    }

    static void unifyJava(Type jt, Type t) throws TypeException {
        String descr = jt.javaType.description;
        if (t.type != JAVA) {
            if (t.type == UNIT && descr == "V")
                return;
            mismatch(jt, t);
        }
        if (descr == t.javaType.description) {
            return;
        }
        mismatch(jt, t);
    }

    static void requireOrdered(Type type) throws TypeException {
        switch (type.type) {
            case VARIANT:
                if ((type.flags & FL_ORDERED_REQUIRED) == 0) {
                    if (type.partialMembers != null) {
                        Iterator i = type.partialMembers.values().iterator();
                        while (i.hasNext()) {
                            requireOrdered((Type) i.next());
                        }
                    }
                    if (type.finalMembers != null) {
                        Iterator i = type.finalMembers.values().iterator();
                        while (i.hasNext()) {
                            requireOrdered((Type) i.next());
                        }
                        type.flags |= FL_ORDERED_REQUIRED;
                    }
                }
                return;
            case MAP:
                requireOrdered(type.param[2]);
                requireOrdered(type.param[0]);
                return;
            case VAR:
                if (type.ref != null) {
                    requireOrdered(type.ref);
                } else {
                    type.flags |= FL_ORDERED_REQUIRED;
                }
            case NUM:
            case STR:
            case UNIT:
            case LIST_MARKER:
                return;
        }
        TypeException ex = new TypeException(type + " is not an ordered type");
        ex.special = true;
        throw ex;
    }

    static void occursCheck(Type type, Type var) throws TypeException {
        type = type.deref();
        if (type == var) {
            TypeException ex =
                new TypeException("Cyclic types are not allowed");
            ex.special = true;
            throw ex;
        }
        if (type.param != null && type.type != VARIANT) {
            for (int i = type.param.length; --i >= 0;) {
                occursCheck(type.param[i], var);
            }
        }
    }

    static void unifyToVar(Type var, Type from) throws TypeException {
        occursCheck(from, var);
        if ((var.flags & FL_ORDERED_REQUIRED) != 0) {
            requireOrdered(from);
        }
        limitDepth(from, var.depth);
        var.ref = from;
    }

    static void unify(Type a, Type b) throws TypeException {
        a = a.deref();
        b = b.deref();
        if (a == b) {
        } else if (a.type == VAR) {
            unifyToVar(a, b);
        } else if (b.type == VAR) {
            unifyToVar(b, a);
        } else if (a.type == JAVA) {
            unifyJava(a, b);
        } else if (b.type == JAVA) {
            unifyJava(b, a);
        } else if (a.type != b.type) {
            mismatch(a, b);
        } else if (a.type == STRUCT || a.type == VARIANT) {
            unifyMembers(a, b);
        } else {
            for (int i = 0, cnt = a.param.length; i < cnt; ++i) {
                unify(a.param[i], b.param[i]);
            }
        }
    }

    static Map copyTypeMap(Map types, Map free, Map known) {
        Map result = new HashMap();
        for (Iterator i = types.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            result.put(entry.getKey(),
                    copyType((Type) entry.getValue(), free, known));
        }
        return result;
    }

    static Type copyType(Type type_, Map free, Map known) {
        Type type = type_.deref();
        if (type.type == VAR) {
            Type var = (Type) free.get(type);
            return var == null ? type : var;
        }
        if (type.param.length == 0) {
            return type_;
        }
        Type copy = (Type) known.get(type);
        if (copy != null) {
            return copy;
        }
        Type[] param = new Type[type.param.length];
        copy = new Type(type.type, param);
        Type res = copy;
        if (type_.field >= FIELD_NON_POLYMORPHIC) {
            res = mutableFieldRef(type_);
            res.field = type_.field;
            res.ref = copy;
        }
        known.put(type, res);
        for (int i = param.length; --i >= 0;) {
            param[i] = copyType(type.param[i], free, known);
        }
        if (type.partialMembers != null) {
            copy.partialMembers = copyTypeMap(type.partialMembers, free, known);
        }
        if (type.finalMembers != null) {
            copy.finalMembers = copyTypeMap(type.finalMembers, free, known);
        }
        return res;
    }

    static BindRef resolve(String sym, Node where, Scope scope, int depth) {
        for (; scope != null; scope = scope.outer) {
            if (scope.name == sym && scope.binder != null) {
                BindRef ref = scope.binder.getRef(where.line);
                if (scope.free == null) {
                    return ref;
                }
                HashMap vars = new HashMap();
                for (int i = scope.free.length; --i >= 0;) {
                    Type free = new Type(depth);
                    free.flags = scope.free[i].flags;
                    vars.put(scope.free[i], free);
                }
                ref.type = copyType(ref.type, vars, new HashMap());
                return ref;
            }
            if (scope.closure != null) {
                BindRef ref = scope.closure.refProxy(
                                resolve(sym, where, scope.outer, depth));
                return ref;
            }
        }
        throw new CompileException(where, "Unknown identifier: " + sym);
    }

    static Type resolveClass(String name, Scope scope, boolean shadow) {
        if (name.indexOf('/') >= 0) {
            return JavaType.typeOfClass(null, name);
        }
        for (; scope != null; scope = scope.outer) {
            if (scope.name == name && (scope.importClass != null || shadow)) {
                return scope.importClass;
            }
        }
        return null;
    }

    static Type resolveFullClass(String name, Scope scope, Node checkPerm) {
        if (checkPerm != null && name.indexOf('/') >= 0 &&
            (YetiCode.CompileCtx.current().flags & YetiC.CF_NO_IMPORT) != 0)
            throw new CompileException(checkPerm, name + " is not imported");
        Type t = resolveClass(name, scope, false);
        if (t == null && checkPerm != null &&
            (YetiCode.CompileCtx.current().flags & YetiC.CF_NO_IMPORT) != 0)
            throw new CompileException(checkPerm, name + " is not imported");
        return t == null ? JavaType.typeOfClass(scope.packageName, name) : t;
    }

    static void getFreeVar(List vars, List deny, Type type, int depth) {
        if (type.seen) {
            return;
        }
        if (deny != null && type.field >= FIELD_NON_POLYMORPHIC) {
            vars = deny; // anything under mutable field is evil
        }
        Type t = type.deref();
        if (t.type != VAR) {
            if (t.type == FUN) {
                deny = null;
            }
            type.seen = true;
            for (int i = t.param.length; --i >= 0;) {
                getFreeVar(vars, deny, t.param[i], depth);
            }
            type.seen = false;
        } else if (t.depth > depth && vars.indexOf(t) < 0) {
            vars.add(t);
        }
    }

    static Scope bindPoly(String name, Type valueType, Binder value,
                          int depth, Scope scope) {
        List free = new ArrayList(), deny = new ArrayList();
        getFreeVar(free, deny, valueType, depth);
        if (deny.size() != 0) {
            for (int i = free.size(); --i >= 0;) {
                if (deny.indexOf(free.get(i)) >= 0) {
                    free.remove(i);
                }
            }
        }
        scope = new Scope(scope, name, value);
        scope.free = (Type[]) free.toArray(new Type[free.size()]);
        return scope;
    }
}
