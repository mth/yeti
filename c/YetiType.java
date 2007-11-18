// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti type analyzer.
 * Uses Hindley-Milner type inference algorithm
 * with extensions for polymorphic structs and variants.
 * Copyright (c) 2007 Madis Janson
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

public final class YetiType implements YetiParser, YetiCode {
    static final int VAR  = 0;
    static final int UNIT = 1;
    static final int STR  = 2;
    static final int NUM  = 3;
    static final int BOOL = 4;
    static final int FUN  = 5;
    static final int LIST = 6;
    static final int STRUCT = 7;
    static final int VARIANT = 8;

    static final Type[] NO_PARAM = {};
    static final Type UNIT_TYPE = new Type(UNIT, NO_PARAM);
    static final Type NUM_TYPE  = new Type(NUM,  NO_PARAM);
    static final Type STR_TYPE  = new Type(STR,  NO_PARAM);
    static final Type BOOL_TYPE = new Type(BOOL, NO_PARAM);
    static final Type A = new Type(1);
    static final Type A_TO_UNIT = new Type(FUN, new Type[] { A, UNIT_TYPE });
    static final Type EQ_TYPE = new Type(FUN,
                new Type[] { A, new Type(FUN, new Type[] { A, BOOL_TYPE }) });
    static final Type NUMOP_TYPE = new Type(FUN,
            new Type[] { NUM_TYPE, new Type(FUN, new Type[] { NUM_TYPE,
                                                              NUM_TYPE }) });

    static final String[] TYPE_NAMES =
        { "var", "unit", "string", "number", "bool", "fun", "list", "struct",
          "variant" };
    
    static final Scope ROOT_SCOPE =
        bindPoly("==", EQ_TYPE, new EqBinder(), 0,
        bindPoly("println", A_TO_UNIT, new PrintlnFun(), 0,
        bindScope("+", new ArithOpFun("add", NUMOP_TYPE),
        bindScope("-", new ArithOpFun("sub", NUMOP_TYPE),
        bindScope("*", new ArithOpFun("mul", NUMOP_TYPE),
        bindScope("/", new ArithOpFun("div", NUMOP_TYPE),
        bindScope("false", new BooleanConstant(false),
        bindScope("true", new BooleanConstant(true), null))))))));

    static Scope bindScope(String name, Binder binder, Scope scope) {
        return new Scope(scope, name, binder);
    }

    static final class Type {
        int type;
        Map partialMembers;
        Map finalMembers;

        Type[] param;
        Type ref;
        int depth;

        Type(int depth) {
            this.depth = depth;
        }

        Type(int type, Type[] param) {
            this.type = type;
            this.param = param;
        }

        public String toString() {
            if (ref != null) {
                return ref.toString();
            }
            switch (type) {
                case VAR:
                    return '\'' + Integer.toString(hashCode(), 36);
                case FUN:
                    return param[0] + " -> " + param[1];
                case STRUCT:
                    return "{" + partialMembers + " < " + finalMembers + "}";
                case VARIANT:
                    return "[" + partialMembers + " > " + finalMembers + "]";
            }
            StringBuffer result = new StringBuffer();
            for (int i = 0; i < param.length; ++i) {
                result.append(param[i]);
                result.append(' ');
            }
            result.append(TYPE_NAMES[type]);
            return result.toString();
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

    static void limitDepth(Type type, int maxDepth) {
        if (type.type != VAR) {
            for (int i = type.param.length; --i >= 0;) {
                limitDepth(type.param[i], maxDepth);
            }
        } else if (type.depth > maxDepth) {
            type.depth = maxDepth;
        }
    }

    static void mismatch(Type a, Type b) {
        throw new RuntimeException("Type mismatch: " + a + " <> " + b);
    }

    static void finalizeStruct(Type partial, Type src) {
        if (src.finalMembers == null || partial.partialMembers == null ||
                partial.finalMembers != null) {
            return; // nothing to check
        }
        Iterator i = partial.partialMembers.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry) i.next();
            Type ff = (Type) src.finalMembers.get(entry.getKey());
            if (ff == null) {
                throw new RuntimeException("Type mismatch: " + src + " => "
                        + partial + " (field missing: " + entry.getKey() + ")");
            }
            unify((Type) entry.getValue(), ff);
        }
    }

    static void unifyMembers(Type a, Type b) {
        Map ff;
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
                    unify(f, (Type) entry.getValue());
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

    static void unify(Type a, Type b) {
        a = a.deref();
        b = b.deref();
        if (a == b) {
        } else if (a.type == VAR) {
            limitDepth(b, a.depth);
            a.ref = b;
        } else if (b.type == VAR) {
            limitDepth(a, b.depth);
            b.ref = a;
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
        for (int i = param.length; --i >= 0;) {
            param[i] = copyType(type.param[i], free, known);
        }
        copy = new Type(type.type, param);
        if (type.partialMembers != null) {
            copy.partialMembers = copyTypeMap(type.partialMembers, free, known);
        }
        if (type.finalMembers != null) {
            copy.finalMembers = copyTypeMap(type.finalMembers, free, known);
        }
        return copy;
    }

    static BindRef resolve(String sym, Scope scope, int depth) {
        for (; scope != null; scope = scope.outer) {
            if (scope.name == sym) {
                BindRef ref = scope.binder.getRef();
                if (scope.free == null || scope.free.length == 0) {
                    return ref;
                }
                HashMap vars = new HashMap();
                for (int i = scope.free.length; --i >= 0;) {
                    vars.put(scope.free[i], new Type(depth));
                }
                ref.type = copyType(ref.type, vars, new HashMap());
                return ref;
            }
            if (scope.closure != null) {
                return scope.closure.refProxy(resolve(sym, scope.outer, depth));
            }
        }
        throw new RuntimeException("Unknown identifier: " + sym);
    }

    static Code analyze(Node node, Scope scope, int depth) {
        if (node instanceof Sym) {
            String sym = ((Sym) node).sym;
            if (Character.isUpperCase(sym.charAt(0))) {
                return variantConstructor(sym, depth);
            }
            return resolve(sym, scope, depth);
        }
        if (node instanceof Num) {
            return new NumericConstant(((Num) node).num);
        }
        if (node instanceof Str) {
            return new StringConstant(((Str) node).str);
        }
        if (node instanceof Seq) {
            Seq seq = (Seq) node;
            if (seq.st.length == 0) {
                return new UnitConstant();
            }
            return analSeq(seq.st, scope, depth);
        }
        if (node instanceof BinOp) {
            BinOp op = (BinOp) node;
            if (op.op == "") {
                return apply(analyze(op.left, scope, depth),
                             analyze(op.right, scope, depth), depth);
            }
            if (op.op == ".") {
                return selectMember(op, scope, depth);
            }
            // TODO: unary -
            return apply(apply(resolve(op.op, scope, depth),
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
        throw new IllegalArgumentException("Don't know what to do with "
                        + node);
    }

    static Code apply(Code fun, Code arg, int depth) {
        Type[] applyFun = { arg.type, new Type(depth) };
        unify(fun.type, new Type(FUN, applyFun));
        return fun.apply(arg, applyFun[1]);
    }

    static Code variantConstructor(String name, int depth) {
        Type arg = new Type(depth);
        Type tag = new Type(VARIANT, new Type[] { arg });
        tag.partialMembers = new HashMap();
        tag.partialMembers.put(name, arg);
        Type[] fun = { arg, tag };
        return new VariantConstructor(new Type(FUN, fun), name);
    }

    static Code selectMember(BinOp op, Scope scope, int depth) {
        if (!(op.right instanceof Sym)) {
            throw new RuntimeException("Illegal ." + op.right.show());
        }
        String field = ((Sym) op.right).sym;
        Type res = new Type(depth);
        Type arg = new Type(STRUCT, new Type[] { res });
        arg.partialMembers = new HashMap();
        arg.partialMembers.put(field, res);
        Code src = analyze(op.left, scope, depth);
        unify(arg, src.type);
        return new SelectMember(res, src, field);
    }

    static Code cond(Condition condition, Scope scope, int depth) {
        Node[][] choices = condition.choices;
        Code[][] conds = new Code[choices.length][];
        Type result = null;
        for (int i = 0; i < choices.length; ++i) {
            Node[] choice = choices[i];
            Code val = analyze(choice[0], scope, depth);
            if (choice.length == 1) {
                conds[i] = new Code[] { val };
            } else {
                Code cond = analyze(choice[1], scope, depth);
                unify(BOOL_TYPE, cond.type);
                conds[i] = new Code[] { val, cond };
            }
            if (result == null) {
                result = val.type;
            } else {
                unify(result, val.type);
            }
        }
        return new ConditionalExpr(result, conds);
    }

    static void getFreeVar(List vars, Type type, int depth) {
        type = type.deref();
        if (type.type != VAR) {
            for (int i = type.param.length; --i >= 0;) {
                getFreeVar(vars, type.param[i], depth);
            }
        } else if (type.depth > depth && vars.indexOf(type) < 0) {
            vars.add(type);
        }
    }

    static Scope bindPoly(String name, Type valueType, Binder value,
                   int depth, Scope scope) {
        List free = new ArrayList();
        getFreeVar(free, valueType, depth);
        scope = new Scope(scope, name, value);
        scope.free = (Type[]) free.toArray(new Type[free.size()]);
        return scope;
    }

    static Code analSeq(Node[] nodes, Scope scope, int depth) {
        SeqExpr result = null, last = null, cur;
        for (int i = 0; i < nodes.length - 1; ++i) {
            if (nodes[i] instanceof Bind) {
                Bind bind = (Bind) nodes[i];
                if (bind.expr instanceof Lambda) {
                    // recursive binding
                    Function lambda = new Function(new Type(depth + 1));
                    BindExpr binder = new BindExpr(lambda);
                    lambda.selfBind = binder;
                    lambda(lambda, (Lambda) bind.expr,
                               new Scope(scope, bind.name, binder), depth + 1);
                    scope = bindPoly(bind.name, lambda.type, binder,
                                     depth, scope);
                    cur = binder;
                } else {
                    Code code = analyze(bind.expr, scope, depth /* + 1 */);
                    BindExpr binder = new BindExpr(code);
                    scope = new Scope(scope, bind.name, binder);
                    cur = binder;
                }
            } else {
                Code code = analyze(nodes[i], scope, depth);
                unify(UNIT_TYPE, code.type);
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
        if (last == null) {
            return code;
        }
        for (cur = result; cur != null; cur = (SeqExpr) cur.result) {
            cur.type = code.type;
        }
        last.result = code;
        return result;
    }

    static Code lambda(Function to, Lambda lambda, Scope scope, int depth) {
        if (!(lambda.arg instanceof Sym)) {
            throw new RuntimeException("Bad argument: " + lambda.arg);
        }
        to.arg.type = new Type(depth);
        Scope bodyScope = new Scope(scope, ((Sym) lambda.arg).sym, to);
        bodyScope.closure = to;
        to.body = analyze(lambda.expr, bodyScope, depth);
        Type fun = new Type(FUN, new Type[] { to.arg.type, to.body.type });
        if (to.type != null) {
            unify(fun, to.type);
        }
        to.type = fun;
        to.bindName = lambda.bindName;
        return to;
    }

    static Code structType(Struct st, Scope scope, int depth) {
        Node[] nodes = st.fields;
        if (nodes.length == 0) {
            throw new RuntimeException("No sense in empty struct");
        }
        Scope local = scope;
        Map fields = new HashMap();
        Map codeFields = new HashMap();
        String[] names = new String[nodes.length];
        Code[] values  = new Code[nodes.length];
        // Functions see struct members in their scope
        for (int i = 0; i < nodes.length; ++i) {
            if (!(nodes[i] instanceof Bind)) {
                throw new RuntimeException("Unexpected beast in the structure ("
                    + nodes[i] + "), please give me some field binding.");
            }
            Bind field = (Bind) nodes[i];
            if (fields.containsKey(field.name)) {
                throw new RuntimeException("Duplicate field " + field.name
                    + " in the structure");
            }
            Code code = values[i] = field.expr instanceof Lambda
                        ? new Function(new Type(depth))
                        : analyze(field.expr, scope, depth);
            names[i] = field.name;
            fields.put(field.name, code.type);
            local = new Scope(local, field.name, new LocalBind(code));
        }
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = (Bind) nodes[i];
            if (field.expr instanceof Lambda) {
                lambda((Function) values[i], (Lambda) field.expr, local, depth);
            }
        }
        Type result = new Type(STRUCT,
            (Type[]) fields.values().toArray(new Type[fields.size()]));
        result.finalMembers = fields;
        return new StructConstructor(result, names, values);
    }

    static Scope badPattern(Node pattern) {
        throw new RuntimeException("Bad case pattern: " + pattern.show());
    }

    // oh holy fucking shit, this code sucks. horrible abuse of BinOps...
    static Code caseType(Case ex, Scope scope, int depth) {
        Node[] choices = ex.choices;
        if (choices.length == 0) {
            throw new RuntimeException("case expects some option!");
        }
        Code val = analyze(ex.value, scope, depth);
        Map variants = new HashMap();
        CaseExpr result = new CaseExpr(val);
        for (int i = 0; i < choices.length; ++i) {
            Scope local = scope;
            BinOp choice;
            if (!(choices[i] instanceof BinOp) ||
                (choice = (BinOp) choices[i]).op != ":") {
                throw new RuntimeException("Expecting option, not a "
                                           + choices[i].show());
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
            if (!(pat.right instanceof Sym)) {
                badPattern(pat); // TODO
            }

            Choice caseChoice = result.addVariantChoice(variant);
            Type variantArg = new Type(depth);

            // got some constructor, store to map.
            local = new Scope(local, ((Sym) pat.right).sym,
                              caseChoice.bindParam(variantArg));
            Type old = (Type) variants.put(variant, variantArg);
            if (old != null) { // same constructor already. shall be same type.
                unify(old, variantArg);
            }
            
            // nothing intresting, just get option expr and merge to result
            Code opt = analyze(choice.right, local, depth);
            if (result.type == null) {
                result.type = opt.type;
            } else {
                unify(result.type, opt.type);
            }
            caseChoice.setExpr(opt);
        }
        Type variantType = new Type(VARIANT,
            (Type[]) variants.values().toArray(new Type[variants.size()]));
        variantType.finalMembers = variants;
        unify(val.type, variantType);
        return result;
    }

    static Code list(NList list, Scope scope, int depth) {
        Node[] items = list.items;
        Code[] codeItems = new Code[items.length];
        Type type = null;
        for (int i = 0; i < items.length; ++i) {
            codeItems[i] = analyze(items[i], scope, depth);
            if (type == null) {
                type = codeItems[i].type;
            } else {
                unify(type, codeItems[i].type);
            }
        }
        if (type == null) {
            type = new Type(depth);
        }
        return new ListConstructor(new Type(LIST, new Type[] { type }),
                                   codeItems);
    }

    public static Code toCode(char[] src) {
        Node n = new Parser(src).readSeq(' ');
        System.err.println(n.show());
        return analyze(n, ROOT_SCOPE, 0);
    }

    public static void main(String[] args) {
        Node n = new Parser(args[0].toCharArray()).readSeq(' ');
        System.err.println(n.show());
        Code t = analyze(n, ROOT_SCOPE, 0);
        System.err.println(t.type);
    }
}
