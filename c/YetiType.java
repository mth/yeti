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
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public final class YetiType implements YetiParser, YetiBuiltins {
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
    static final Type C_B_LIST_TYPE =
        new Type(MAP, new Type[] { C, B, LIST_TYPE });
    static final Type NUM_LIST_TYPE =
        new Type(MAP, new Type[] { NUM_TYPE, B, LIST_TYPE });
    static final Type A_B_MAP_TYPE =
        new Type(MAP, new Type[] { B, A, MAP_TYPE });
    static final Type A_B_C_MAP_TYPE =
        new Type(MAP, new Type[] { B, A, C });
    static final Type A_LIST_TYPE =
        new Type(MAP, new Type[] { A, NO_TYPE, LIST_TYPE });
    static final Type C_LIST_TYPE =
        new Type(MAP, new Type[] { C, NO_TYPE, LIST_TYPE });
    static final Type A_MLIST_TYPE =
        new Type(MAP, new Type[] { A, NUM_TYPE, LIST_TYPE });
    static final Type CONS_TYPE = fun2Arg(A, A_B_LIST_TYPE, A_LIST_TYPE);
    static final Type A_TO_UNIT = fun(A, UNIT_TYPE);
    static final Type IN_TYPE = fun2Arg(A, A_B_MAP_TYPE, BOOL_TYPE);
    static final Type COMPOSE_TYPE = fun2Arg(fun(B, C), fun(A, B), fun(A, C));
    static final Type BOOL_TO_BOOL = fun(BOOL_TYPE, BOOL_TYPE);
    static final Type FOR_TYPE =
        fun2Arg(A_B_LIST_TYPE, fun(A, UNIT_TYPE), UNIT_TYPE);
    static final Type STR2_PRED_TYPE = fun2Arg(STR_TYPE, STR_TYPE, BOOL_TYPE);

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
        bindPoly(".", COMPOSE_TYPE, new Compose(), 0,
        bindCore("id", fun(A, A), "ID",
        bindCore("const", fun2Arg(A, B, A), "CONST",
        bindCore("flip", fun(fun2Arg(A, B, C), fun2Arg(B, A, C)), "FLIP",
        bindCore("print", A_TO_UNIT, "PRINT",
        bindCore("println", A_TO_UNIT, "PRINTLN",
        bindCore("readln", fun(UNIT_TYPE, STR_TYPE), "READLN",
        bindCore("number", fun(STR_TYPE, NUM_TYPE), "NUM",
        bindCore("randomInt", fun(NUM_TYPE, NUM_TYPE), "RANDINT",
        bindCore("array", fun(A_B_LIST_TYPE, A_MLIST_TYPE), "ARRAY",
        bindCore("reverse", fun(A_B_LIST_TYPE, A_LIST_TYPE), "REVERSE",
        bindCore("head", fun(A_B_LIST_TYPE, A), "HEAD",
        bindCore("tail", fun(A_B_LIST_TYPE, A_LIST_TYPE), "TAIL",
        bindCore("forHash",
            fun2Arg(A_B_MAP_TYPE, fun2Arg(A, B, UNIT_TYPE), UNIT_TYPE),
            "FORHASH",
        bindCore("map",
            fun2Arg(fun(A, C), A_B_LIST_TYPE, C_B_LIST_TYPE), "MAP",
        bindCore("mapHash",
            fun2Arg(fun2Arg(A, B, C), A_B_MAP_TYPE, C_LIST_TYPE), "MAPHASH",
        bindCore("fold",
            fun2Arg(fun2Arg(C, A, C), C, fun(A_B_LIST_TYPE, C)), "FOLD",
        bindCore("filter",
            fun2Arg(fun(A, BOOL_TYPE), A_B_LIST_TYPE, A_LIST_TYPE), "FILTER",
        bindCore("find",
            fun2Arg(fun(A, BOOL_TYPE), A_B_LIST_TYPE, A_LIST_TYPE), "FIND",
        bindCore("contains", fun2Arg(A, A_B_LIST_TYPE, BOOL_TYPE), "CONTAINS",
        bindCore("any",
            fun2Arg(fun(A, BOOL_TYPE), A_B_LIST_TYPE, BOOL_TYPE), "ANY",
        bindCore("all",
            fun2Arg(fun(A, BOOL_TYPE), A_B_LIST_TYPE, BOOL_TYPE), "ALL",
        bindCore("index", fun2Arg(A, A_B_LIST_TYPE, NUM_TYPE), "INDEX",
        bindCore("sum", fun(NUM_LIST_TYPE, NUM_TYPE), "SUM",
        bindCore("setHashDefault",
            fun2Arg(A_B_MAP_TYPE, fun(A, B), UNIT_TYPE), "SET_HASH_DEFAULT",
        bindCore("at", fun2Arg(A_B_C_MAP_TYPE, A, B), "AT",
        bindCore("empty?", fun(A_B_LIST_TYPE, BOOL_TYPE), "EMPTY",
        bindPoly("in", IN_TYPE, new InOp(), 0,
        bindPoly("::", CONS_TYPE, new Cons(), 0,
        bindPoly("ignore", A_TO_UNIT, new Ignore(), 0,
        bindPoly("for", FOR_TYPE, new For(), 0,
        bindScope("+", new ArithOpFun("add", NUMOP_TYPE),
        bindScope("-", new ArithOpFun("sub", NUMOP_TYPE),
        bindScope("*", new ArithOpFun("mul", NUMOP_TYPE),
        bindScope("/", new ArithOpFun("div", NUMOP_TYPE),
        bindScope("%", new ArithOpFun("rem", NUMOP_TYPE),
        bindScope("div", new ArithOpFun("intDiv", NUMOP_TYPE),
        bindScope("shl", new ArithOpFun("shl", NUMOP_TYPE),
        bindScope("shr", new ArithOpFun("shr", NUMOP_TYPE),
        bindScope("=~", new MatchOpFun(),
        bindScope("not", new NotOp(),
        bindScope("and", new BoolOpFun(false),
        bindScope("or", new BoolOpFun(true),
        bindScope("false", new BooleanConstant(false),
        bindScope("true", new BooleanConstant(true),
        null)))))))))))))))))))))))))))))))))))))))))))))))))));

    static Scope bindScope(String name, Binder binder, Scope scope) {
        return new Scope(scope, name, binder);
    }

    static Scope bindCompare(String op, Type type, int code, Scope scope) {
        return bindPoly(op, type, new Compare(type, code), 0, scope);
    }

    static Scope bindCore(String name, Type type, String field, Scope scope) {
        return bindPoly(name, type, new CoreFun(type, field), 0, scope);
    }

    static Type fun(Type a, Type res) {
        return new Type(FUN, new Type[] { a, res });
    }

    static Type fun2Arg(Type a, Type b, Type res) {
        return new Type(FUN,
            new Type[] { a, new Type(FUN, new Type[] { b, res }) });
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
            if (partialMembers != null) {
                for (Iterator i = partialMembers.entrySet().iterator();
                     i.hasNext();) {
                    Map.Entry e = (Map.Entry) i.next();
                    if (res.length() != 0) {
                        res.append(variant ? " | " : "; ");
                    }
                    res.append(e.getKey());
                    res.append(variant ? " " : " is ");
                    res.append(((Type) e.getValue()).str(vars, refs));
                }
            }
            if (finalMembers != null) {
                for (Iterator i = finalMembers.entrySet().iterator();
                     i.hasNext();) {
                    Map.Entry e = (Map.Entry) i.next();
                    if (partialMembers != null &&
                        partialMembers.containsKey(e.getKey())) {
                        continue;
                    }
                    if (res.length() != 0) {
                        res.append(variant ? " | " : "; .");
                    }
                    res.append(e.getKey());
                    res.append(variant ? " " : " is ");
                    res.append(((Type) e.getValue()).str(vars, refs));
                }
            }
            return res.toString();
        }

        private String getVarName(Map vars) {
            String v = (String) vars.get(this);
            if (v == null) {
                v = "'";
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
                    res = (param[0].type == FUN
                        ? "(" + param[0].str(vars, refs) + ")"
                        : param[0].str(vars, refs)) + " -> " +
                          param[1].str(vars, refs);
                    break;
                case STRUCT:
                    res = "{" + hstr(vars, refs) + "}";
                    break;
                case VARIANT:
                    res = hstr(vars, refs);
                    break;
                case MAP:
                    res = param[2].type == LIST_MARKER
                        ? (param[1].type == NONE ? "list<" :
                                param[1].type == NUM ? "array<" : "list?<")
                          + param[0].str(vars, refs) + ">"
                        : param[2].type == MAP_MARKER ||
                          param[1].type != NUM && param[1].type != VAR
                            ? "hash<" + param[1].str(vars, refs) + ", "
                                      + param[0].str(vars, refs) + ">"
                            :  "map<" + param[1].str(vars, refs) + ", "
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

        public Scope(Scope outer, String name, Binder binder) {
            this.outer = outer;
            this.name = name;
            this.binder = binder;
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
            TypeException ex = new TypeException("Cyclic type");
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

    static Type copyType(Type type, Map free, Map known) {
        type = type.deref();
        if (type.type == VAR) {
            Type var = (Type) free.get(type);
            return var == null ? type : var;
        }
        if (type.param.length == 0) {
            return type;
        }
        Type copy = (Type) known.get(type);
        if (copy != null) {
            return copy;
        }
        Type[] param = new Type[type.param.length];
        copy = new Type(type.type, param);
        known.put(type, copy);
        for (int i = param.length; --i >= 0;) {
            param[i] = copyType(type.param[i], free, known);
        }
        if (type.partialMembers != null) {
            copy.partialMembers = copyTypeMap(type.partialMembers, free, known);
        }
        if (type.finalMembers != null) {
            copy.finalMembers = copyTypeMap(type.finalMembers, free, known);
        }
        return copy;
    }

    static BindRef resolve(String sym, Node where, Scope scope, int depth) {
        for (; scope != null; scope = scope.outer) {
            if (scope.name == sym) {
                BindRef ref = scope.binder.getRef(where.line);
                if (scope.free == null || scope.free.length == 0) {
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

    static void unusedBinding(Bind bind) {
        throw new CompileException(bind, "Unused binding: " + bind.name);
    }

    static Code analyze(Node node, Scope scope, int depth) {
        if (node instanceof Sym) {
            String sym = ((Sym) node).sym;
            if (Character.isUpperCase(sym.charAt(0))) {
                return variantConstructor(sym, depth);
            }
            return resolve(sym, node, scope, depth);
        }
        if (node instanceof NumLit) {
            return new NumericConstant(((NumLit) node).num);
        }
        if (node instanceof Str) {
            return new StringConstant(((Str) node).str);
        }
        if (node instanceof UnitLiteral) {
            return new UnitConstant();
        }
        if (node instanceof Seq) {
            return analSeq(((Seq) node).st, scope, depth);
        }
        if (node instanceof Bind) {
            Function r = singleBind((Bind) node, scope, depth);
            if (!((BindExpr) r.selfBind).used) {
                unusedBinding((Bind) node);
            }
            return r;
        }
        if (node instanceof BinOp) {
            BinOp op = (BinOp) node;
            if (op.op == "") {
                return apply(node, analyze(op.left, scope, depth),
                             analyze(op.right, scope, depth), depth);
            }
            if (op.op == FIELD_OP) {
                if (op.right instanceof NList) {
                    return keyRefExpr(analyze(op.left, scope, depth),
                                      (NList) op.right, scope, depth);
                }
                return selectMember(op, scope, depth);
            }
            if (op.op == ":=") {
                return assignOp(op, scope, depth);
            }
            if (op.op == "\\") {
                return lambda(new Function(null),
                              new Lambda(new Sym("_").pos(op.line, op.col),
                                         op.right, null), scope, depth);
            }
            if (op.op == "is") {
                return isOp(op, ((IsOp) op).type,
                            analyze(op.right, scope, depth), depth);
            }
            if (op.op == "#") {
                return objectRef((ObjectRefOp) op, scope, depth);
            }
            if (op.op == "loop") {
                return loop(op, scope, depth);
            }
            // TODO: unary -
            return apply(op.right,
                         apply(op, resolve(op.op, op, scope, depth),
                               analyze(op.left, scope, depth), depth),
                         analyze(op.right, scope, depth), depth);
        }
        if (node instanceof Condition) {
            return cond((Condition) node, scope, depth);
        }
        if (node instanceof Struct) {
            return structType((Struct) node, scope, depth);
        }
        if (node instanceof NList) {
            return list((NList) node, scope, depth);
        }
        if (node instanceof Lambda) {
            return lambda(new Function(null), (Lambda) node, scope, depth);
        }
        if (node instanceof Case) {
            return caseType((Case) node, scope, depth);
        }
        if (node instanceof ConcatStr) {
            return concatStr((ConcatStr) node, scope, depth);
        }
        if (node instanceof Load) {
            String name = ((Load) node).moduleName;
            return new LoadModule(name, YetiTypeVisitor.getType(node, name));
        }
        if (node instanceof RSection) {
            return rsection((RSection) node, scope, depth);
        }
        if (node instanceof NewOp) {
            NewOp op = (NewOp) node;
            Code[] args = mapArgs(op.arguments, scope, depth);
            return new NewExpr(JavaType.resolveConstructor(op, args, scope),
                               args, op.line);
        }
        throw new CompileException(node,
            "I think that this " + node + " should not be here.");
    }

    static Type nodeToMembers(int type, TypeNode[] param, Map free, int depth) {
        Map members = new HashMap();
        Type[] tp = new Type[param.length];
        for (int i = 0; i < param.length; ++i) {
            tp[i] = nodeToType(param[i].param[0], free, depth);
            if (members.put(param[i].name, tp[i]) != null) {
                throw new CompileException(param[i], "Duplicate field name "
                                    + param[i].name + " in structure type");
            }
        }
        Type result = new Type(type, tp);
        result.partialMembers = members;
        result.finalMembers = new HashMap(members);
        return result;
    }

    static void expectsParam(TypeNode t, int count) {
        if (t.param == null ? count != 0 : t.param.length != count) {
            throw new CompileException(t, "type " + t.name + " expects "
                                          + count + " parameters");
        }
    }

    static final Object[][] PRIMITIVE_TYPE_MAPPING = {
        { "()",      UNIT_TYPE },
        { "boolean", BOOL_TYPE },
        { "char",    CHAR_TYPE },
        { "number",  NUM_TYPE  },
        { "string",  STR_TYPE  }
    };

    static Type nodeToType(TypeNode node, Map free, int depth) {
        String name = node.name;
        for (int i = PRIMITIVE_TYPE_MAPPING.length; --i >= 0;) {
            if (PRIMITIVE_TYPE_MAPPING[i][0] == name) {
                expectsParam(node, 0);
                return (Type) PRIMITIVE_TYPE_MAPPING[i][1];
            }
        }
        if (name == "") {
            return nodeToMembers(STRUCT, node.param, free, depth);
        }
        if (name == "|") {
            return nodeToMembers(VARIANT, node.param, free, depth);
        }
        if (name == "->") {
            expectsParam(node, 2);
            Type[] tp = { nodeToType(node.param[0], free, depth),
                          nodeToType(node.param[1], free, depth) };
            return new Type(FUN, tp);
        }
        if (name == "array") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, depth),
                          NUM_TYPE, LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "list") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, depth),
                          NO_TYPE, LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "list?") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, depth),
                          new Type(depth), LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "hash") {
            expectsParam(node, 2);
            Type[] tp = { nodeToType(node.param[1], free, depth),
                          nodeToType(node.param[0], free, depth), MAP_TYPE };
            return new Type(MAP, tp);
        }
        if (Character.isUpperCase(name.charAt(0))) {
            return nodeToMembers(VARIANT, new TypeNode[] { node }, free, depth);
        }
        if (name.startsWith("'")) {
            Type t = (Type) free.get(name);
            if (t == null) {
                free.put(name, t = new Type(depth));
            }
            return t;
        }
        throw new CompileException(node, "Unknown type: " + name);
    }

    static Code isOp(Node is, TypeNode type, Code value, int depth) {
        Type t = nodeToType(type, new HashMap(), depth);
        try {
            unify(value.type, t);
        } catch (TypeException ex) {
            throw new CompileException(is, ex.getMessage() +
                        " (when checking " + value.type + " is " + t + ")");
        }
        return value;
    }

    static Code[] mapArgs(Node[] args, Scope scope, int depth) {
        if (args == null)
            return null;
        Code[] res = new Code[args.length];
        for (int i = 0; i < args.length; ++i) {
            res[i] = analyze(args[i], scope, depth);
        }
        return res;
    }

    static Code objectRef(ObjectRefOp ref, Scope scope, int depth) {
        return null;
    }

    static Code apply(Node where, Code fun, Code arg, int depth) {
        Type[] applyFun = { arg.type, new Type(depth) };
        try {
            unify(fun.type, new Type(FUN, applyFun));
        } catch (TypeException ex) {
            throw new CompileException(where,
                "Cannot apply " + arg.type + " to " + fun.type + "\n    " +
                ex.getMessage());
        }
        return fun.apply(arg, applyFun[1], where.line);
    }

    static Code rsection(RSection section, Scope scope, int depth) {
        if (section.sym == FIELD_OP) {
            LinkedList parts = new LinkedList();
            Node x = section.arg;
            for (BinOp op; x instanceof BinOp; x = op.left) {
                op = (BinOp) x;
                if (op.op != FIELD_OP) {
                    throw new CompileException(op,
                        "Unexpected " + op.op + " in field selector");
                }
                checkSelectorSym(op, op.right);
                parts.addFirst(((Sym) op.right).sym);
            }
            checkSelectorSym(section, x);
            parts.addFirst(((Sym) x).sym);
            String[] fields =
                (String[]) parts.toArray(new String[parts.size()]);
            Type res = new Type(depth), arg = res;
            for (int i = fields.length; --i >= 0;) {
                arg = selectMemberType(arg, fields[i], depth);
            }
            return new SelectMemberFun(new Type(FUN, new Type[] { arg, res }),
                                       fields);
        }
        Code fun = resolve(section.sym, section, scope, depth);
        Code arg = analyze(section.arg, scope, depth);
        Type[] r = { new Type(depth), new Type(depth) };
        Type[] afun = { r[0], new Type(FUN, new Type[] { arg.type, r[1] }) };
        try {
            unify(fun.type, new Type(FUN, afun));
        } catch (TypeException ex) {
            throw new CompileException(section,
                "Cannot apply " + arg.type + " as a 2nd argument to " +
                fun.type + "\n    " + ex.getMessage());
        }
        return fun.apply2nd(arg, new Type(FUN, r), section.line);
    }

    static Code variantConstructor(String name, int depth) {
        Type arg = new Type(depth);
        Type tag = new Type(VARIANT, new Type[] { arg });
        tag.partialMembers = new HashMap();
        tag.partialMembers.put(name, arg);
        Type[] fun = { arg, tag };
        return new VariantConstructor(new Type(FUN, fun), name);
    }

    static void checkSelectorSym(Node op, Node sym) {
        if (!(sym instanceof Sym)) {
            if (sym == null) {
                throw new CompileException(op, "What's that dot doing here?");
            }
            throw new CompileException(sym, "Illegal ." + sym);
        }
    }

    static Type selectMemberType(Type res, String field, int depth) {
        Type arg = new Type(STRUCT, new Type[] { res });
        arg.partialMembers = new HashMap();
        arg.partialMembers.put(field, res);
        return arg;
    }

    static Code selectMember(BinOp op, Scope scope, int depth) {
        final Type res = new Type(depth);
        checkSelectorSym(op, op.right);
        final String field = ((Sym) op.right).sym;
        Type arg = selectMemberType(res, field, depth);
        Code src = analyze(op.left, scope, depth);
        try {
            unify(arg, src.type);
        } catch (TypeException ex) {
            throw new CompileException(op.right,
                src.type + " do not have ." + field + " field\n", ex);
        }
        boolean poly = src.polymorph && src.type.finalMembers != null &&
            ((Type) src.type.finalMembers.get(field)).field == 0;
        return new SelectMember(res, src, field, op.line, poly) {
            boolean mayAssign() {
                Type t = st.type.deref();
                Type given;
                if (t.finalMembers != null &&
                    (given = (Type) t.finalMembers.get(field)) != null &&
                    (given.field != FIELD_MUTABLE)) {
                    return false;
                }
                Type self = (Type) t.partialMembers.get(field);
                if (self.field != FIELD_MUTABLE) {
                    // XXX couldn't we get along with res.field = FIELD_MUTABLE?
                    t.partialMembers.put(field, mutableFieldRef(res));
                }
                return true;
            }
        };
    }

    static Code keyRefExpr(Code val, NList keyList, Scope scope, int depth) {
        if (keyList.items == null || keyList.items.length == 0) {
            throw new CompileException(keyList, ".[] - missing key expression");
        }
        if (keyList.items.length != 1) {
            throw new CompileException(keyList, "Unexpected , inside .[]");
        }
        Code key = analyze(keyList.items[0], scope, depth);
        Type[] param = { new Type(depth), key.type, new Type(depth) };
        try {
            unify(val.type, new Type(MAP, param));
        } catch (TypeException ex) {
            throw new CompileException(keyList, val.type +
                " cannot be referenced by " + key.type + " key", ex);
        }
        return new KeyRefExpr(param[0], val, key, keyList.line);
    }

    static Code assignOp(BinOp op, Scope scope, int depth) {
        Code left = analyze(op.left, scope, depth);
        Code right = analyze(op.right, scope, depth);
        try {
            unify(left.type, right.type);
        } catch (TypeException ex) {
            throw new CompileException(op, ex.getMessage());
        }
        Code assign = left.assign(right);
        if (assign == null) {
            throw new CompileException(op,
                "Non-mutable expression on the left of the assign operator :=");
        }
        assign.type = UNIT_TYPE;
        return assign;
    }

    static Code concatStr(ConcatStr concat, Scope scope, int depth) {
        Code[] parts = new Code[concat.param.length];
        for (int i = 0; i < parts.length; ++i) {
            parts[i] = analyze(concat.param[i], scope, depth);
        }
        return new ConcatStrings(parts);
    }

    static Code cond(Condition condition, Scope scope, int depth) {
        Node[][] choices = condition.choices;
        Code[][] conds = new Code[choices.length][];
        Type result = null;
        boolean poly = true;
        for (int i = 0; i < choices.length; ++i) {
            Node[] choice = choices[i];
            Code val = analyze(choice[0], scope, depth);
            if (choice.length == 1) {
                conds[i] = new Code[] { val };
            } else {
                Code cond = analyze(choice[1], scope, depth);
                try {
                    unify(BOOL_TYPE, cond.type);
                } catch (TypeException ex) {
                    throw new CompileException(choice[1],
                        "if condition must have a boolean type (but here was "
                        + cond.type + ")");
                }
                conds[i] = new Code[] { val, cond };
            }
            poly &= val.polymorph;
            if (result == null) {
                result = val.type;
            } else {
                try {
                    unify(result, val.type);
                } catch (TypeException ex) {
                    throw new CompileException(choice[0],
                        "This if branch has a " + val.type +
                        " type, while another was a " + result, ex);
                }
            }
        }
        return new ConditionalExpr(result, conds, poly);
    }

    static Code loop(BinOp loop, Scope scope, int depth) {
        Code cond = analyze(loop.left != null ? loop.left : loop.right,
                            scope, depth);
        try {
            unify(BOOL_TYPE, cond.type);
        } catch (TypeException ex) {
            throw new CompileException(loop.left,
                "Loop condition must have a boolean type (but here was "
                + cond.type + ")");
        }
        if (loop.left == null) {
            return new LoopExpr(cond, new UnitConstant());
        }
        Code body = analyze(loop.right, scope, depth);
        try {
            unify(body.type, UNIT_TYPE);
        } catch (TypeException ex) {
            throw new CompileException(loop.right,
                "Loop body must have a unit type, not " + body.type, ex);
        }
        return new LoopExpr(cond, body);
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

    static void registerVar(BindExpr binder, Scope scope) {
        while (scope != null) {
            if (scope.closure != null) {
                scope.closure.addVar(binder);
                return;
            }
            scope = scope.outer;
        }
    }

    static Function singleBind(Bind bind, Scope scope, int depth) {
        if (!(bind.expr instanceof Lambda)) {
            throw new CompileException(bind,
                "Closed binding must be a function binding");
        }
        // recursive binding
        Function lambda = new Function(new Type(depth + 1));
        BindExpr binder = new BindExpr(lambda, bind.var);
        lambda.selfBind = binder;
        lambdaBind(lambda, bind,
                   new Scope(scope, bind.name, binder), depth + 1);
        return lambda;
    }

    static Code analSeq(Node[] nodes, Scope scope, int depth) {
        BindExpr[] bindings = new BindExpr[nodes.length];
        SeqExpr result = null, last = null, cur;
        for (int i = 0; i < nodes.length - 1; ++i) {
            if (nodes[i] instanceof Bind) {
                Bind bind = (Bind) nodes[i];
                BindExpr binder;
                if (bind.expr instanceof Lambda) {
                    binder = (BindExpr) singleBind(bind, scope, depth).selfBind;
                } else {
                    Code code = analyze(bind.expr, scope, depth + 1);
                    binder = new BindExpr(code, bind.var);
                    if (bind.type != null) {
                        isOp(bind, bind.type, binder.st, depth);
                    }
                }
                if (binder.st.polymorph && !bind.var) {
                    scope = bindPoly(bind.name, binder.st.type, binder,
                                     depth, scope);
                } else {
                    scope = new Scope(scope, bind.name, binder);
                }
                if (bind.var) {
                    registerVar(binder, scope.outer);
                }
                bindings[i] = binder;
                cur = binder;
            } else if (nodes[i] instanceof Load) {
                LoadModule m = (LoadModule) analyze(nodes[i], scope, depth);
                if (m.type.type == STRUCT) {
                    Iterator j = m.type.finalMembers.entrySet().iterator();
                    while (j.hasNext()) {
                        Map.Entry e = (Map.Entry) j.next();
                        String name = ((String) e.getKey()).intern();
                        Type t = (Type) e.getValue();
                        scope = bindPoly(name, t, m.bindField(name, t),
                                         depth, scope);
                    }
                } else if (m.type.type != UNIT) {
                    throw new CompileException(nodes[i],
                        "Expected module with struct or unit type here (" +
                        ((Load) nodes[i]).moduleName + " has type " + m.type +
                        ", but only structs can be exploded)");
                }
                cur = new SeqExpr(m);
            } else {
                Code code = analyze(nodes[i], scope, depth);
                try {
                    unify(UNIT_TYPE, code.type);
                } catch (TypeException ex) {
                    throw new CompileException(nodes[i],
                        "Unit type expected here, not a " + code.type);
                }
                code.ignoreValue();
                cur = new SeqExpr(code);
            }
            if (last == null) {
                result = last = cur;
            } else {
                last.result = cur;
                last = cur;
            }
        }
        Code code = analyze(nodes[nodes.length - 1], scope, depth);
        for (int i = bindings.length; --i >= 0;) {
            if (bindings[i] != null && !bindings[i].used) {
                unusedBinding((Bind) nodes[i]);
            }
        }
        if (last == null) {
            return code;
        }
        for (cur = result; cur != null; cur = (SeqExpr) cur.result) {
            cur.type = code.type;
        }
        last.result = code;
        result.polymorph = code.polymorph;
        return result;
    }

    static Code lambdaBind(Function to, Bind bind, Scope scope, int depth) {
        if (bind.type != null) {
            isOp(bind, bind.type, to, depth);
        }
        return lambda(to, (Lambda) bind.expr, scope, depth);
    } 

    static Code lambda(Function to, Lambda lambda, Scope scope, int depth) {
        Type expected = to.type == null ? null : to.type.deref();
        to.polymorph = true;
        Scope bodyScope;
        if (lambda.arg instanceof Sym) {
            if (expected != null && expected.type == FUN) {
                to.arg.type = expected.param[0];
            } else {
                to.arg.type = new Type(depth);
            }
            bodyScope = new Scope(scope, ((Sym) lambda.arg).sym, to);
        } else if (lambda.arg instanceof UnitLiteral) {
            to.arg.type = UNIT_TYPE;
            bodyScope = new Scope(scope, null, to);
        } else {
            throw new CompileException(lambda.arg,
                                       "Bad argument: " + lambda.arg);
        }
        bodyScope.closure = to;
        if (lambda.expr instanceof Lambda) {
            Function f = new Function(expected != null && expected.type == FUN
                                      ? expected.param[1] : null);
            // make f to know about its outer scope before processing it
            to.setBody(f);
            lambda(f, (Lambda) lambda.expr, bodyScope, depth);
        } else {
            to.setBody(analyze(lambda.expr, bodyScope, depth));
        }
        Type fun = new Type(FUN, new Type[] { to.arg.type, to.body.type });
        if (to.type != null) {
            try {
                unify(fun, to.type);
            } catch (TypeException ex) {
                throw new CompileException(lambda,
                        "Function type " + fun + " is not " + to.type
                        + " (self-binding)\n    " + ex.getMessage());
            }
        }
        to.type = fun;
        to.bindName = lambda.bindName;
        to.body.markTail();
        return to;
    }

    static Code structType(Struct st, Scope scope, int depth) {
        Node[] nodes = st.fields;
        if (nodes.length == 0) {
            throw new CompileException(st, "No sense in empty struct");
        }
        Scope local = scope;
        Map fields = new HashMap();
        Map codeFields = new HashMap();
        String[] names = new String[nodes.length];
        Code[] values  = new Code[nodes.length];
        StructConstructor result = new StructConstructor(names, values);
        result.polymorph = true;
        // Functions see struct members in their scope
        for (int i = 0; i < nodes.length; ++i) {
            if (!(nodes[i] instanceof Bind)) {
                throw new CompileException(nodes[i],
                    "Unexpected beast in the structure (" + nodes[i] +
                    "), please give me some field binding.");
            }
            Bind field = (Bind) nodes[i];
            if (fields.containsKey(field.name)) {
                throw new CompileException(field, "Duplicate field "
                    + field.name + " in the structure");
            }
            Code code = values[i] = field.expr instanceof Lambda
                        ? new Function(new Type(depth))
                        : analyze(field.expr, scope, depth);
            names[i] = field.name;
            fields.put(field.name,
                field.var ? fieldRef(depth, code.type, FIELD_MUTABLE) :
                code.polymorph || field.expr instanceof Lambda ? code.type
                        : fieldRef(depth, code.type, FIELD_NON_POLYMORPHIC));
            local = new Scope(local, field.name,
                              result.bind(i, code, field.var));
        }
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = (Bind) nodes[i];
            if (field.expr instanceof Lambda) {
                lambdaBind((Function) values[i], field, local, depth);
            }
        }
        result.type = new Type(STRUCT,
            (Type[]) fields.values().toArray(new Type[fields.size()]));
        result.type.finalMembers = fields;
        return result;
    }

    static Scope badPattern(Node pattern) {
        throw new CompileException(pattern, "Bad case pattern: " + pattern);
    }

    // oh holy fucking shit, this code sucks. horrible abuse of BinOps...
    static Code caseType(Case ex, Scope scope, int depth) {
        Node[] choices = ex.choices;
        if (choices.length == 0) {
            throw new CompileException(ex, "case expects some option!");
        }
        Code val = analyze(ex.value, scope, depth);
        Map variants = new HashMap();
        CaseExpr result = new CaseExpr(val);
        result.polymorph = true;
        for (int i = 0; i < choices.length; ++i) {
            Scope local = scope;
            BinOp choice;
            if (!(choices[i] instanceof BinOp) ||
                (choice = (BinOp) choices[i]).op != ":") {
                throw new CompileException(choices[i],
                    "Expecting option, not a " + choices[i]);
            }
            if (!(choice.left instanceof BinOp)) {
                badPattern(choice.left); // TODO
            }

            // binop. so try to extract a damn variant constructor.
            BinOp pat = (BinOp) choice.left;
            String variant = null;
            if (pat.op != "" || !(pat.left instanceof Sym) ||
                !Character.isUpperCase(
                    (variant = ((Sym) pat.left).sym).charAt(0))) {
                badPattern(pat); // binop pat should be a variant
            }
            Choice caseChoice = result.addVariantChoice(variant);
            Type variantArg = null;
            if (!(pat.right instanceof Sym)) {
                if (pat.right instanceof UnitLiteral) {
                    variantArg = UNIT_TYPE;
                } else {
                    badPattern(pat); // TODO
                }
            } else {
                variantArg = new Type(depth);
                // got some constructor, store to map.
                local = new Scope(local, ((Sym) pat.right).sym,
                                  caseChoice.bindParam(variantArg));
            }
            Type old = (Type) variants.put(variant, variantArg);
            if (old != null) { // same constructor already. shall be same type.
                try {
                    unify(old, variantArg);
                } catch (TypeException e) {
                    throw new CompileException(pat.right, e.getMessage());
                }
            }
            
            // nothing intresting, just get option expr and merge to result
            Code opt = analyze(choice.right, local, depth);
            result.polymorph &= opt.polymorph;
            if (result.type == null) {
                result.type = opt.type;
            } else {
                try {
                    unify(result.type, opt.type);
                } catch (TypeException e) {
                    throw new CompileException(choice.right,
                        "This choice has a " + opt.type +
                        " type, while another was a " + result.type, e);
                }
            }
            caseChoice.setExpr(opt);
        }
        Type variantType = new Type(VARIANT,
            (Type[]) variants.values().toArray(new Type[variants.size()]));
        variantType.finalMembers = variants;
        try {
            unify(val.type, variantType);
        } catch (TypeException e) {
            throw new CompileException(ex.value,
                "Inferred type for case argument is " + variantType +
                ", but a " + val.type + " is given\n    (" +
                e.getMessage() + ")");
        }
        return result;
    }

    static Code list(NList list, Scope scope, int depth) {
        Node[] items = list.items == null ? new Node[0] : list.items;
        Code[] keyItems = null;
        Code[] codeItems = new Code[items.length];
        Type type = null;
        Type keyType = NO_TYPE;
        Type kind = null;
        BinOp bin;
        for (int i = 0; i < items.length; ++i) {
            if (items[i] instanceof BinOp &&
                (bin = (BinOp) items[i]).op == ":") {
                Code key = analyze(bin.left, scope, depth);
                if (kind != MAP_TYPE) {
                    if (kind != null) {
                        throw new CompileException(bin,
                            "Unexpected : in list" + (i != 1 ? "" :
                            " (or the key is missing on the first item?)"));
                    }
                    keyType = key.type;
                    kind = MAP_TYPE;
                    keyItems = new Code[items.length];
                } else {
                    try {
                        unify(keyType, key.type);
                    } catch (TypeException ex) {
                        throw new CompileException(items[i],
                            "This map element has " + keyType +
                            "key, but others have had " + keyType, ex);
                    }
                }
                keyItems[i] = key;
                codeItems[i] = analyze(bin.right, scope, depth);
            } else {
                if (kind == MAP_TYPE) {
                    throw new CompileException(items[i],
                                "Map item is missing a key");
                }
                kind = LIST_TYPE;
                if (items[i] instanceof BinOp &&
                    (bin = (BinOp) items[i]).op == "..") {
                    Code from = analyze(bin.left, scope, depth);
                    Code to = analyze(bin.right, scope, depth);
                    Node n = null; Type t = null;
                    try {
                        n = bin.left;
                        unify(t = from.type, NUM_TYPE);
                        n = bin.right;
                        unify(t = to.type, NUM_TYPE);
                    } catch (TypeException ex) {
                        throw new CompileException(n, ".. range expects " +
                                    "limit to be number, not a " + t, ex);
                    }
                    codeItems[i] = new Range(from, to);
                } else {
                    codeItems[i] = analyze(items[i], scope, depth);
                }
            }
            if (type == null) {
                type = codeItems[i].type;
            } else {
                try {
                    unify(type, codeItems[i].type);
                } catch (TypeException ex) {
                    throw new CompileException(items[i], (kind == LIST_TYPE
                         ? "This list element is " : "This map element is ") +
                         codeItems[i].type + ", but others have been " + type,
                        ex);
                }
            }
        }
        if (type == null) {
            type = new Type(depth);
        }
        if (kind == null) {
            kind = LIST_TYPE;
        }
        if (list.items == null) {
            keyType = new Type(depth);
            kind = MAP_TYPE;
        }
        Code res = kind == LIST_TYPE ? (Code) new ListConstructor(codeItems)
                                     : new MapConstructor(keyItems, codeItems);
        res.type = new Type(MAP, new Type[] { type, keyType, kind });
        res.polymorph = kind == LIST_TYPE;
        return res;
    }

    public static RootClosure toCode(String sourceName, char[] src, int flags) {
        Object oldSrc = currentSrc.get();
        currentSrc.set(src);
        try {
            Parser parser = new Parser(sourceName, src, flags);
            Node n = parser.parse();
            if ((flags & YetiC.CF_PRINT_PARSE_TREE) != 0) {
                System.err.println(n.show());
            }
            RootClosure root = new RootClosure();
            Scope scope = new Scope(ROOT_SCOPE, null, null);
            scope.closure = root;
            root.code = analyze(n, scope, 0);
            root.type = root.code.type;
            root.moduleName = parser.moduleName;
            if ((flags & YetiC.CF_COMPILE_MODULE) == 0 &&
                parser.moduleName == null) {
                try {
                    unify(root.type, UNIT_TYPE);
                } catch (TypeException ex) {
                    throw new CompileException(n,
                        "Program body must have a unit type, not "
                        + root.type, ex);
                }
            } else { // MODULE
                List free = new ArrayList(), deny = new ArrayList();
                getFreeVar(free, deny, root.type, -1);
                //System.err.println("checked module type, free are " + free
                //        + ", deny " + deny);
                if (!deny.isEmpty() ||
                    !free.isEmpty() && !root.code.polymorph) {
                    throw new CompileException(n,
                        "Module type is not fully defined");
                }
            }
            return root;
        } catch (CompileException ex) {
            if (ex.fn == null) {
                ex.fn = sourceName;
            }
            throw ex;
        } finally {
            currentSrc.set(oldSrc);
        }
    }
}
