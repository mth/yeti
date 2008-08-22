// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti code analyzer.
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

public final class YetiAnalyzer extends YetiType {
    static class TopLevel {
        Map typeDefs = new HashMap();
    }

    static final String NONSENSE_STRUCT = "No sense in empty struct";

    static void unusedBinding(Bind bind) {
        YetiCode.CompileCtx.current().warn(
            new CompileException(bind, "Unused binding: " + bind.name));
    }

    static Lambda shortLambda(BinOp op) {
        return new Lambda(new Sym("_").pos(op.line, op.col), op.right, null);
    }

    static Lambda asLambda(Node node) {
        BinOp op;
        return node instanceof Lambda ? (Lambda) node
                : node instanceof BinOp && (op = (BinOp) node).op == "\\"
                ? shortLambda(op) : null;
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
        if (node instanceof Seq) {
            return analSeq((Seq) node, scope, depth);
        }
        if (node instanceof Bind) {
            Bind bind = (Bind) node;
            Function r = singleBind(bind, scope, depth);
            if (!((BindExpr) r.selfBind).used) {
                unusedBinding(bind);
            }
            return r;
        }
        String kind = node.kind();
        if (kind != null) {
            XNode x = (XNode) node;
            if (kind == "()") {
                return new UnitConstant();
            }
            if (kind == "list") {
                return list(x, scope, depth);
            }
            if (kind == "struct") {
                return structType(x, scope, depth);
            }
            if (kind == "concat") {
                return concatStr(x, scope, depth);
            }
            if (kind == "cond") {
                return cond(x, scope, depth);
            }
            if (kind == "_") {
                return new Cast(analyze(x.expr[0], scope, depth),
                                UNIT_TYPE, false, node.line);
            }
            if (kind == "load") {
                if ((YetiCode.CompileCtx.current().flags & YetiC.CF_NO_IMPORT)
                     != 0) throw new CompileException(node, "load is disabled");
                String nam = ((Sym) x.expr[0]).sym;
                return new LoadModule(nam, YetiTypeVisitor.getType(node, nam));
            }
            throw new CompileException(node,
                "I think that this " + node + " should not be here.");
        }
        if (node instanceof BinOp) {
            BinOp op = (BinOp) node;
            String opop = op.op;
            if (opop == "") {
                if (op.left instanceof ThrowSym) {
                    Code throwable = analyze(op.right, scope, depth);
                    JavaType.checkThrowable(op.left, throwable.type);
                    return new Throw(throwable, new Type(depth));
                }
                return apply(node, analyze(op.left, scope, depth),
                             op.right, scope, depth);
            }
            if (opop == FIELD_OP) {
                if (op.right.kind() == "list") {
                    return keyRefExpr(analyze(op.left, scope, depth),
                                      (XNode) op.right, scope, depth);
                }
                checkSelectorSym(op, op.right);
                return selectMember(op, (Sym) op.right,
                        analyze(op.left, scope, depth), scope, depth);
            }
            if (opop == ":=") {
                return assignOp(op, scope, depth);
            }
            if (opop == "\\") {
                return lambda(new Function(null), shortLambda(op),
                              scope, depth);
            }
            if (opop == "is" || opop == "as" || opop == "unsafely_as") {
                return isOp(op, ((TypeOp) op).type,
                            analyze(op.right, scope, depth), scope, depth);
            }
            if (opop == "#") {
                return objectRef((ObjectRefOp) op, scope, depth);
            }
            if (opop == "loop") {
                return loop(op, scope, depth);
            }
            if (opop == "-" && op.left == null) {
                return apply(op, resolve("negate", op, scope, depth),
                                 op.right, scope, depth);
            }
            if (opop == "instanceof") {
                JavaType jt = resolveFullClass(((InstanceOf) op).className,
                                               scope, op).javaType.resolve(op);
                return new InstanceOfExpr(analyze(op.right, scope, depth), jt);
            }
            if (op.left == null) {
                throw new CompileException(op,
                    "Internal error (incomplete operator " + op.op + ")");
            }
            return apply(op.right,
                         apply(op, resolve(opop, op, scope, depth),
                               op.left, scope, depth),
                         op.right, scope, depth);
        }
        if (node instanceof Lambda) {
            return lambda(new Function(null), (Lambda) node, scope, depth);
        }
        if (node instanceof Case) {
            return caseType((Case) node, scope, depth);
        }
        if (node instanceof RSection) {
            return rsection((RSection) node, scope, depth);
        }
        if (node instanceof NewOp) {
            NewOp op = (NewOp) node;
            Code[] args = mapArgs(op.arguments, scope, depth);
            return new NewExpr(JavaType.resolveConstructor(op,
                                    resolveFullClass(op.name, scope, op), args)
                                .check(op, scope.packageName), args, op.line);
        }
        if (node instanceof Try) {
            return tryCatch((Try) node, scope, depth);
        }
        if (node instanceof ClassOf) {
            ClassOf co = (ClassOf) node;
            Type t = co.className != "module" ? null :
                        resolveClass("module", scope, false);
            return new ClassOfExpr(t != null ? t.javaType :
                resolveFullClass(co.className, scope, co).javaType.resolve(co));
        }
        throw new CompileException(node,
            "I think that this " + node + " should not be here.");
    }

    static Type nodeToMembers(int type, TypeNode[] param, Map free,
                              Scope scope, int depth) {
        Map members = new HashMap();
        Type[] tp = new Type[param.length];
        for (int i = 0; i < param.length; ++i) {
            tp[i] = nodeToType(param[i].param[0], free, scope, depth);
            if (param[i].var) {
                tp[i] = fieldRef(depth, tp[i], FIELD_MUTABLE);
            }
            if (members.put(param[i].name, tp[i]) != null) {
                throw new CompileException(param[i], "Duplicate field name "
                                    + param[i].name + " in structure type");
            }
        }
        Type result = new Type(type, tp);
        result.partialMembers = members;
//        result.finalMembers = new HashMap(members);
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

    static Type typeOfClass(String className, Scope scope) {
        if (className.indexOf('/') > 0)
            return JavaType.typeOfClass(null, className);
        Type t = resolveClass(className, scope, false);
        if (t != null)
            return t;
        return JavaType.typeOfClass(scope.packageName, className);
    }

    static Type nodeToType(TypeNode node, Map free, Scope scope, int depth) {
        String name = node.name;
        for (int i = PRIMITIVE_TYPE_MAPPING.length; --i >= 0;) {
            if (PRIMITIVE_TYPE_MAPPING[i][0] == name) {
                expectsParam(node, 0);
                return (Type) PRIMITIVE_TYPE_MAPPING[i][1];
            }
        }
        if (name == "") {
            return nodeToMembers(STRUCT, node.param, free, scope, depth);
        }
        if (name == "|") {
            return nodeToMembers(VARIANT, node.param, free, scope, depth);
        }
        if (name == "->") {
            expectsParam(node, 2);
            Type[] tp = { nodeToType(node.param[0], free, scope, depth),
                          nodeToType(node.param[1], free, scope, depth) };
            return new Type(FUN, tp);
        }
        if (name == "array") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, scope, depth),
                          NUM_TYPE, LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "list") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, scope, depth),
                          NO_TYPE, LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "list?") {
            expectsParam(node, 1);
            Type[] tp = { nodeToType(node.param[0], free, scope, depth),
                          new Type(depth), LIST_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "hash") {
            expectsParam(node, 2);
            Type[] tp = { nodeToType(node.param[1], free, scope, depth),
                          nodeToType(node.param[0], free, scope, depth),
                          MAP_TYPE };
            return new Type(MAP, tp);
        }
        if (name == "map") {
            expectsParam(node, 2);
            Type[] tp = { nodeToType(node.param[1], free, scope, depth),
                          nodeToType(node.param[0], free, scope, depth),
                          new Type(depth) };
            return new Type(MAP, tp);
        }
        if (Character.isUpperCase(name.charAt(0))) {
            return nodeToMembers(VARIANT, new TypeNode[] { node },
                                 free, scope, depth);
        }
        int arrays = 0;
        while (name.endsWith("[]")) {
            ++arrays;
            name = name.substring(0, name.length() - 2);
        }
        Type t;
        char c = name.charAt(0);
        if (c == '~') {
            expectsParam(node, 0);
            String cn = name.substring(1).replace('.', '/').intern();
//            Type[] tp = new Type[node.param.length];
//            for (int i = tp.length; --i >= 0;)
//                tp[i] = nodeToType(node.param[i], free, scope, depth);
            t = typeOfClass(cn, scope);
//            t.param = tp;
        } else if (c == '\'') {
            t = (Type) free.get(name);
            if (t == null)
                free.put(name, t = new Type(depth));
        } else if (c == '^') {
            t = (Type) free.get(name);
            if (t == null) {
                free.put(name, t = new Type(depth));
                t.flags = FL_ORDERED_REQUIRED;
            }
        } else {
            Type[] tp = new Type[node.param.length];
            for (int i = 0; i < tp.length; ++i)
                tp[i] = nodeToType(node.param[i], free, scope, depth);
            t = resolveTypeDef(scope, name, tp, depth, node);
        }
        while (--arrays >= 0) {
            t = new Type(JAVA_ARRAY, new Type[] { t });
        }
        return t;
    }

    static Code isOp(Node is, TypeNode type, Code value,
                     Scope scope, int depth) {
        Type t = nodeToType(type, new HashMap(), scope, depth).deref();
        Type vt = value.type.deref();
        String s;
        if (is instanceof BinOp && (s = ((BinOp) is).op) != "is") {
            // () is class is a way for writing null constant
            if ((t.type == JAVA || t.type == JAVA_ARRAY) &&
                value instanceof UnitConstant) {
                return new Cast(value, t, false, is.line);
            }
            if (s == "unsafely_as" && (vt.type != VAR || t.type != VAR)) {
                JavaType.checkUnsafeCast(is, vt, t);
            } else if (s == "as" &&
                       JavaType.isAssignable(is, t, vt, true) < 0) {
                throw new CompileException(is, "impossible cast from " +
                                               vt + " to " + t);
            }
            return new Cast(value, t, s == "as", is.line);
        }
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
        Code obj = null;
        Type t = null;
        if (ref.right instanceof Sym) {
            String className = ((Sym) ref.right).sym;
            t = resolveClass(className, scope, true);
            if (t == null && Character.isUpperCase(className.charAt(0)) &&
                (YetiCode.CompileCtx.current().flags & YetiC.CF_NO_IMPORT) == 0)
                t = JavaType.typeOfClass(scope.packageName, className);
        }
        if (t == null) {
            obj = analyze(ref.right, scope, depth);
            t = obj.type;
        }
        if (ref.arguments == null) {
            JavaType.Field f = JavaType.resolveField(ref, t, obj == null);
            f.check(ref, scope.packageName);
            return new ClassField(obj, f, ref.line);
        }
        Code[] args = mapArgs(ref.arguments, scope, depth);
        return new MethodCall(obj,
                    JavaType.resolveMethod(ref, t, args, obj == null)
                        .check(ref, scope.packageName), args, ref.line);
    }

    static Code tryCatch(Try t, Scope scope, int depth) {
        TryCatch tc = new TryCatch();
        scope = new Scope(scope, null, null); // closure frame
        scope.closure = tc;
        tc.setBlock(analyze(t.block, scope, depth));
        if (t.cleanup != null) {
            tc.cleanup = analyze(t.cleanup, scope, depth);
            try {
                unify(tc.cleanup.type, UNIT_TYPE);
            } catch (TypeException ex) {
                throw new CompileException(t.cleanup,
                                "finally block must have a unit type, not "
                                + tc.cleanup.type, ex);
            }
        }
        for (int i = 0; i < t.catches.length; ++i) {
            Catch c = t.catches[i];
            Type exception = resolveFullClass(c.exception, scope, null);
            exception.javaType.resolve(c);
            TryCatch.Catch cc = tc.addCatch(exception);
            cc.handler = analyze(c.handler,
                c.bind == null ? scope : new Scope(scope, c.bind, cc), depth);
            try {
                unify(tc.block.type, cc.handler.type);
            } catch (TypeException ex) {
                throw new CompileException(c.handler,
                            "This catch has " + cc.handler.type +
                            " type, while try block was " + tc.block.type, ex);
            }
        }
        return tc;
    }

    static Code apply(Node where, Code fun, Node arg, Scope scope, int depth) {
        // try cast java types on apply
        Type funt = fun.type.deref(),
             funarg = funt.type == FUN ? funt.param[0].deref() : null;
        Lambda lambdaArg = asLambda(arg);
        Code argCode = lambdaArg != null // prespecifing the lambda type
                ? lambda(new Function(funarg), lambdaArg, scope, depth)
                : analyze(arg, scope, depth);
        if (funarg != null &&
            JavaType.isSafeCast(where, funarg, argCode.type)) {
            argCode = new Cast(argCode, funarg, funarg.javaType != null,
                               where.line);
        }
        Type[] applyFun = { argCode.type, new Type(depth) };
        try {
            unify(fun.type, new Type(FUN, applyFun));
        } catch (TypeException ex) {
            throw new CompileException(where,
                "Cannot apply " + argCode.type + " to " + fun.type +
                "\n    " + ex.getMessage());
        }
        return fun.apply(argCode, applyFun[1], where.line);
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

    static Code selectMember(Node op, Sym member, Code src,
                             Scope scope, int depth) {
        final Type res = new Type(depth);
        final String field = member.sym;
        Type arg = selectMemberType(res, field, depth);
        try {
            unify(arg, src.type);
        } catch (TypeException ex) {
            throw new CompileException(member,
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

    static Code keyRefExpr(Code val, XNode keyList, Scope scope, int depth) {
        if (keyList.expr == null || keyList.expr.length == 0) {
            throw new CompileException(keyList, ".[] - missing key expression");
        }
        if (keyList.expr.length != 1) {
            throw new CompileException(keyList, "Unexpected , inside .[]");
        }
        Code key = analyze(keyList.expr[0], scope, depth);
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

    static Code concatStr(XNode concat, Scope scope, int depth) {
        Code[] parts = new Code[concat.expr.length];
        for (int i = 0; i < parts.length; ++i) {
            parts[i] = analyze(concat.expr[i], scope, depth);
        }
        return new ConcatStrings(parts);
    }

    static Code cond(XNode condition, Scope scope, int depth) {
        Node[] choices = condition.expr;
        Code[][] conds = new Code[(choices.length + 1) / 2][];
        Type result = null;
        boolean poly = true;
        for (int i = 0; i < conds.length; ++i) {
            int j = i * 2;
            Code val = analyze(choices[j], scope, depth);
            if (j + 1 == choices.length) {
                conds[i] = new Code[] { val };
            } else {
                Code cond = analyze(choices[j + 1], scope, depth);
                try {
                    unify(BOOL_TYPE, cond.type);
                } catch (TypeException ex) {
                    throw new CompileException(choices[j + 1],
                        "if condition must have a boolean type (but here was "
                        + cond.type + ")");
                }
                conds[i] = new Code[] { val, cond };
            }
            poly &= val.polymorph;
            if (result == null) {
                result = val.type;
            } else {
                Type t = JavaType.mergeTypes(result, val.type);
                if (t != null) {
                    result = t;
                    continue;
                }
                try {
                    unify(result, val.type);
                } catch (TypeException ex) {
                    throw new CompileException(choices[j],
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
        if (!bind.noRec)
            scope = new Scope(scope, bind.name, binder);
        lambdaBind(lambda, bind, scope, depth + 1);
        return lambda;
    }

    static Scope explodeStruct(Node where, LoadModule m,
                               Scope scope, int depth, boolean noRoot) {
        if (m.type.type == STRUCT) {
            Iterator j = m.type.finalMembers.entrySet().iterator();
        members:
            while (j.hasNext()) {
                Map.Entry e = (Map.Entry) j.next();
                String name = ((String) e.getKey()).intern();
                if (noRoot)
                    for (Scope i = ROOT_SCOPE; i != null; i = i.outer)
                        if (i.name == name)
                            continue members;
                Type t = (Type) e.getValue();
                scope = bindPoly(name, t, m.bindField(name, t), depth, scope);
            }
        } else if (m.type.type != UNIT) {
            throw new CompileException(where,
                "Expected module with struct or unit type here (" +
                m.moduleName.replace('/', '.') + " has type " + m.type +
                ", but only structs can be exploded)");
        }
        return scope;
    }

    static Scope genericBind(Bind bind, BindExpr binder, boolean evalSeq,
                             Scope scope, int depth) {
        if (binder.st.polymorph && !bind.var) {
            scope = bindPoly(bind.name, binder.st.type, binder,
                             depth, scope);
        } else {
            scope = new Scope(scope, bind.name, binder);
        }
        if (bind.var) {
            registerVar(binder, scope.outer);
        }
        if (evalSeq) {
            binder.evalId = YetiEval.registerBind(bind.name,
                        binder.st.type, bind.var, binder.st.polymorph);
        }
        return scope;
    }

    static void addSeq(SeqExpr[] last, SeqExpr expr) {
        if (last[0] == null) {
            last[1] = expr;
        } else {
            last[0].result = expr;
        }
        last[0] = expr;
    }

    static Scope bindStruct(Binder binder, XNode st, boolean isEval,
                            Scope scope, int depth, SeqExpr[] last) {
        Node[] fields = st.expr;
        if (fields.length == 0)
            throw new CompileException(st, NONSENSE_STRUCT);
        for (int j = 0; j < fields.length; ++j) {
            Bind bind = new Bind();
            if (!(fields[j] instanceof Bind)) {
                throw new CompileException(fields[j],
                    "Expected field pattern, not a " + fields[j]);
            }
            Bind field = (Bind) fields[j];
            if (field.var || field.property) {
                throw new CompileException(field, "Structure " +
                    "field pattern may not have modifiers");
            }
            bind.expr = new Sym(field.name);
            bind.expr.pos(bind.line, bind.col);
            Node nameNode = field.expr;
            if (!(nameNode instanceof Sym) ||
                (bind.name = ((Sym) nameNode).sym) == "_")
                throw new CompileException(nameNode,
                    "Binding name expected, not a " + nameNode);
            Code code = selectMember(fields[j], (Sym) bind.expr,
                          binder.getRef(fields[j].line), scope, depth);
            BindExpr bindExpr = new BindExpr(code, false);
            scope = genericBind(bind, bindExpr, isEval, scope, depth);
            addSeq(last, bindExpr);
        }
        return scope;
    }

    static Scope bindTypeDef(TypeDef typeDef, Object seqKind, Scope scope) {
        Scope defScope = scope;
        Type[] def = new Type[typeDef.param.length + 1];

        // binding typedef arguments
        for (int i = typeDef.param.length; --i >= 0;) {
            Type arg = new Type(-1);
            def[i] = arg;
            defScope = new Scope(defScope, typeDef.param[i], null);
            defScope.typeDef = new Type[] { arg };
            defScope.free = NO_PARAM;
        }
        Type type =
            nodeToType(typeDef.type, new HashMap(), defScope, 1).deref();
        def[def.length - 1] = type;
        scope = bindPoly(typeDef.name, type, null, 0, scope);
        scope.typeDef = def;
        if (seqKind instanceof TopLevel) {
            ((TopLevel) seqKind).typeDefs.put(typeDef.name, def);
        }
        return scope;
    }

    static Code analSeq(Seq seq, Scope scope, int depth) {
        Node[] nodes = seq.st;
        BindExpr[] bindings = new BindExpr[nodes.length];
        SeqExpr[] last = { null, null };
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
                        isOp(bind, bind.type, binder.st, scope, depth);
                    }
                }
                scope = genericBind(bind, binder, seq.seqKind == Seq.EVAL,
                                    scope, depth);
                bindings[i] = binder;
                addSeq(last, binder);
            } else if (nodes[i].kind() == "struct-bind") {
                XNode x = (XNode) nodes[i];
                Code expr = analyze(x.expr[1], scope, depth + 1);
                BindExpr binder = new BindExpr(expr, false);
                addSeq(last, binder);
                scope = bindStruct(binder, (XNode) x.expr[0],
                                   seq.seqKind == Seq.EVAL, scope, depth, last);
            } else if (nodes[i].kind() == "load") {
                LoadModule m = (LoadModule) analyze(nodes[i], scope, depth);
                scope = explodeStruct(nodes[i], m, scope, depth - 1, false);
                addSeq(last, new SeqExpr(m));
                Iterator j = m.moduleType.typeDefs.entrySet().iterator();
                while (j.hasNext()) {
                    Map.Entry e = (Map.Entry) j.next();
                    Type[] typeDef = (Type[]) e.getValue();
                    scope = bindPoly((String) e.getKey(),
                                typeDef[typeDef.length - 1], null, 0, scope);
                    scope.typeDef = typeDef;
                }
            } else if (nodes[i].kind() == "import") {
                if ((YetiCode.CompileCtx.current().flags
                        & YetiC.CF_NO_IMPORT) != 0)
                    throw new CompileException(nodes[i], "import is disabled");
                String name = ((Sym) ((XNode) nodes[i]).expr[0]).sym;
                int lastSlash = name.lastIndexOf('/');
                scope = new Scope(scope, (lastSlash < 0 ? name
                              : name.substring(lastSlash + 1)).intern(), null);
                scope.importClass = new Type("L" + name + ';');
            } else if (nodes[i] instanceof TypeDef) {
                scope = bindTypeDef((TypeDef) nodes[i], seq.seqKind, scope);
            } else {
                Code code = analyze(nodes[i], scope, depth);
                try {
                    unify(UNIT_TYPE, code.type);
                } catch (TypeException ex) {
                    throw new CompileException(nodes[i],
                        "Unit type expected here, not a " + code.type);
                }
                code.ignoreValue();
                addSeq(last, new SeqExpr(code));
            }
        }
        Code code = analyze(nodes[nodes.length - 1], scope, depth);
        for (int i = bindings.length; --i >= 0;) {
            if (bindings[i] != null && !bindings[i].used &&
                seq.seqKind != Seq.EVAL) {
                unusedBinding((Bind) nodes[i]);
            }
        }
        return wrapSeq(code, last);
    }

    static Code wrapSeq(Code code, SeqExpr[] seq) {
        if (seq == null || seq[0] == null)
            return code;
        for (SeqExpr cur = seq[1]; cur != null; cur = (SeqExpr) cur.result) {
            cur.type = code.type;
        }
        seq[0].result = code;
        seq[1].polymorph = code.polymorph;
        return seq[1];
    }

    static Code lambdaBind(Function to, Bind bind, Scope scope, int depth) {
        if (bind.type != null) {
            isOp(bind, bind.type, to, scope, depth);
        }
        return lambda(to, (Lambda) bind.expr, scope, depth);
    } 

    static Code lambda(Function to, Lambda lambda, Scope scope, int depth) {
        Type expected = to.type == null ? null : to.type.deref();
        to.polymorph = true;
        Scope bodyScope = null;
        SeqExpr[] seq = null;
        if (lambda.arg instanceof Sym) {
            if (expected != null && expected.type == FUN) {
                to.arg.type = expected.param[0];
            } else {
                to.arg.type = new Type(depth);
            }
            String argName = ((Sym) lambda.arg).sym;
            if (argName != "_")
                bodyScope = new Scope(scope, argName, to);
        } else if (lambda.arg.kind() == "()") {
            to.arg.type = UNIT_TYPE;
        } else if (lambda.arg.kind() == "struct") {
            to.arg.type = new Type(depth);
            seq = new SeqExpr[] { null, null };
            bodyScope = bindStruct(to, (XNode) lambda.arg,
                                   false, scope, depth, seq);
        } else {
            throw new CompileException(lambda.arg,
                                       "Bad argument: " + lambda.arg);
        }
        if (bodyScope == null)
            bodyScope = new Scope(scope, null, to);
        Scope marker = bodyScope;
        while (marker.outer != scope)
            marker = marker.outer;
        marker.closure = to;
        if (lambda.expr instanceof Lambda) {
            Function f = new Function(expected != null && expected.type == FUN
                                      ? expected.param[1] : null);
            // make f to know about its outer scope before processing it
            to.setBody(seq == null || seq[0] == null ? (Code) f : seq[1]);
            lambda(f, (Lambda) lambda.expr, bodyScope, depth);
            wrapSeq(f, seq);
        } else {
            Code body = analyze(lambda.expr, bodyScope, depth);
            Type res; // try casting to expected type
            if (expected != null && expected.type == FUN &&
                JavaType.isSafeCast(lambda, res = expected.param[1].deref(),
                                    body.type)) {
                body = new Cast(body, res, true, lambda.expr.line);
            }
            to.setBody(wrapSeq(body, seq));
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

    private static Bind getField(Node node, Map fields) {
        if (!(node instanceof Bind)) {
            throw new CompileException(node,
                "Unexpected beast in the structure (" + node +
                "), please give me some field binding.");
        }
        return (Bind) node;
    }

    private static void duplicateField(Bind field) {
        throw new CompileException(field,
                    "Duplicate field " + field.name + " in the structure");
    }

    static Code structType(XNode st, Scope scope, int depth) {
        Node[] nodes = st.expr;
        if (nodes.length == 0)
            throw new CompileException(st, NONSENSE_STRUCT);
        Scope local = scope;
        Map fields = new HashMap();
        Map codeMap = new HashMap();
        List values = new ArrayList();
        List props = new ArrayList();
        Function[] funs = new Function[nodes.length];
        StructConstructor result = new StructConstructor(nodes.length);
        result.polymorph = true;
        // Functions see struct members in their scope
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = getField(nodes[i], fields);
            Function lambda = !field.noRec && field.expr instanceof Lambda
                            ? funs[i] = new Function(new Type(depth)) : null;
            Code code =
                lambda != null ? lambda : analyze(field.expr, scope, depth);
            StructField sf = (StructField) codeMap.get(field.name);
            if (field.property) {
                if (sf == null) {
                    sf = new StructField();
                    sf.property = true;
                    sf.name = field.name;
                    codeMap.put(sf.name, sf);
                    props.add(sf);
                } else if (!sf.property ||
                           (field.var ? sf.setter : sf.value) != null) {
                    duplicateField(field);
                }
                // get is () -> t, set is t -> ()
                Type t = (Type) fields.get(field.name);
                if (t == null) {
                    t = new Type(depth);
                    t.field = FIELD_NON_POLYMORPHIC;
                    fields.put(field.name, t);
                }
                if (field.var)
                    t.field = FIELD_MUTABLE;
                Type f = new Type(FUN, field.var ? new Type[] { t, UNIT_TYPE }
                                                 : new Type[] { UNIT_TYPE, t });
                try {
                    unify(code.type, f);
                } catch (TypeException ex) {
                    throw new CompileException(nodes[i],
                        (field.var ? "Setter " : "Getter ") + field.name +
                        " type " + code.type + " is not " + f);
                }
                if (field.var) {
                    sf.setter = code;
                } else {
                    sf.value = code;
                }
            } else {
                if (sf != null)
                    duplicateField(field);
                sf = new StructField();
                sf.name = field.name;
                sf.value = code;
                codeMap.put(field.name, sf);
                int n = values.size();
                values.add(sf);
                fields.put(field.name,
                    field.var ? fieldRef(depth, code.type, FIELD_MUTABLE) :
                    code.polymorph || lambda != null ? code.type
                        : fieldRef(depth, code.type, FIELD_NON_POLYMORPHIC));
                if (!field.noRec) {
                    Binder bind = result.bind(n, code, field.var);
                    if (lambda != null)
                        lambda.selfBind = bind;
                    local = new Scope(local, field.name, bind);
                }
            }
        }
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = (Bind) nodes[i];
            if (funs[i] != null) {
                lambdaBind(funs[i], field, local, depth);
            }
        }
        StructField[] properties = 
            (StructField[]) props.toArray(new StructField[props.size()]);
        result.fields =
            (StructField[]) values.toArray(new StructField[values.size()]);
        result.properties = properties;
        result.type = new Type(STRUCT,
            (Type[]) fields.values().toArray(new Type[fields.size()]));
        for (int i = result.properties.length; --i >= 0; )
            if (properties[i].value == null)
                throw new CompileException(st,
                    "Property " + properties[i].name + " has no getter");
        result.type.finalMembers = fields;
        return result;
    }

    static void patUnify(Node node, Type a, Type b) {
        try {
            unify(a, b);
        } catch (TypeException e) {
            throw new CompileException(node, e.getMessage());
        }
    }

    static class CaseCompiler {
        CaseExpr exp;
        Scope scope;
        int depth;
        List variants = new ArrayList();
  
        CaseCompiler(Code val, int depth) {
            exp = new CaseExpr(val);
            exp.polymorph = true;
            depth = depth;
        }

        CasePattern toPattern(Node node, Type t) {
            if (node instanceof Sym) {
                t.flags |= FL_ANY_PATTERN;
                String name = ((Sym) node).sym;
                if (name == "_")
                    return ANY_PATTERN;
                BindPattern binding = new BindPattern(exp, t);
                scope = new Scope(scope, name, binding);
                return binding;
            }
            if (node.kind() == "()") {
                patUnify(node, t, UNIT_TYPE);
                return ANY_PATTERN;
            }
            if (node instanceof NumLit || node instanceof Str) {
                Code c = analyze(node, scope, depth);
                t = t.deref();
                if (t.type == VAR) {
                    t.type = c.type.type;
                    t.param = NO_PARAM;
                    t.flags = FL_PARTIAL_PATTERN;
                } else if (t.type != c.type.type) {
                    throw new CompileException(node,
                        "Pattern type mismatch: " + c.type + " is not " + t);
                }
                return new ConstPattern(c);
            }
            if (node.kind() == "list") {
                XNode list = (XNode) node;
                Type itemt = new Type(depth);
                Type lt = new Type(MAP,
                        new Type[] { itemt, new Type(depth), LIST_TYPE });
                lt.flags |= FL_PARTIAL_PATTERN;
                if (list.expr == null || list.expr.length == 0) {
                    patUnify(node, t, lt);
                    return EMPTY_PATTERN;
                }
                CasePattern[] items = new CasePattern[list.expr.length];
                for (int i = 0; i < items.length; ++i) {
                    items[i] = toPattern(list.expr[i], itemt);
                }
                patUnify(node, t, lt);
                return new ListPattern(items);
            }
            if (node instanceof BinOp) {
                BinOp pat = (BinOp) node;
                if (pat.op == "" && pat.left instanceof Sym) {
                    String variant = ((Sym) pat.left).sym;
                    if (!Character.isUpperCase(variant.charAt(0))) {
                        throw new CompileException(pat.left, variant +
                            ": Variant constructor must start with upper case");
                    }
                    if ((t.type != VAR || t.ref != null) && t.type != VARIANT) {
                        throw new CompileException(node,
                            "Variant " + variant + " ... is not " + t);
                    }
                    t.type = VARIANT;
                    if (t.partialMembers == null) {
                        t.partialMembers = new HashMap();
                        variants.add(t);
                    }
                    Type argt = new Type(depth);
                    CasePattern arg = toPattern(pat.right, argt);
                    Type old = (Type) t.partialMembers.put(variant, argt);
                    if (old != null) {
                        // same constructor already. shall be same type.
                        patUnify(pat.right, old, argt);
                    }
                    t.param = (Type[]) t.partialMembers.values().toArray(
                                new Type[t.partialMembers.size()]);
                    return new VariantPattern(variant, arg);
                }
                if (pat.op == "::") {
                    Type itemt = new Type(depth);
                    Type lt = new Type(MAP,
                                new Type[] { itemt, NO_TYPE, LIST_TYPE });
                    int flags = t.flags; 
                    patUnify(node, t, lt);
                    CasePattern hd = toPattern(pat.left, itemt);
                    CasePattern tl = toPattern(pat.right, t);
                    lt.flags = FL_PARTIAL_PATTERN;
                    t.flags = flags;
                    return new ConsPattern(hd, tl);
                }
            }
            if (node.kind() == "struct") {
                Node[] fields = ((XNode) node).expr;
                if (fields.length == 0)
                    throw new CompileException(node, NONSENSE_STRUCT);
                String[] names = new String[fields.length];
                CasePattern[] patterns = new CasePattern[fields.length];
                HashMap uniq = new HashMap();
                for (int i = 0; i < fields.length; ++i) {
                    Bind field = getField(fields[i], uniq);
                    if (uniq.containsKey(field.name))
                        duplicateField(field);
                    uniq.put(field.name, null);
                    Type ft = new Type(depth);
                    Type part = new Type(STRUCT, new Type[] { ft });
                    HashMap tm = new HashMap();
                    tm.put(field.name, ft);
                    part.partialMembers = tm;
                    patUnify(field, t, part);
                    names[i] = field.name;
                    patterns[i] = toPattern(field.expr, ft);
                }
                return new StructPattern(names, patterns);
            }
            throw new CompileException(node, "Bad case pattern: " + node);
        }

        void finalizeVariants() {
            for (int i = variants.size(); --i >= 0;) {
                Type t = (Type) variants.get(i);
                if (t.type == VARIANT && t.finalMembers == null) {
                    t.finalMembers = t.partialMembers;
                    t.partialMembers = null;
                }
            }
        }

        void mergeChoice(CasePattern pat, Node node, Scope scope) {
            Code opt = analyze(node, scope, depth);
            exp.polymorph &= opt.polymorph;
            if (exp.type == null) {
                exp.type = opt.type;
            } else {
                try {
                    unify(exp.type, opt.type);
                } catch (TypeException e) {
                    throw new CompileException(node, "This choice has a " +
                        opt.type + " type, while another was a " + exp.type, e);
                }
            }
            exp.addChoice(pat, opt);
        }
    }

    static String checkPartialMatch(Type t) {
        if (t.seen || (t.flags & FL_ANY_PATTERN) != 0)
            return null;
        if ((t.flags & FL_PARTIAL_PATTERN) != 0) {
            return t.type == MAP ? "[]" : t.toString();
        }
        if (t.type != VAR) {
            t.seen = true;
            for (int i = t.param.length; --i >= 0;) {
                String s = checkPartialMatch(t.param[i]);
                if (s != null) {
                    t.seen = false;
                    if (t.type == MAP)
                        return "(" + s + ")::_";
                    if (t.type == VARIANT || t.type == STRUCT) {
                        Iterator j = t.partialMembers.entrySet().iterator();
                        while (j.hasNext()) {
                            Map.Entry e = (Map.Entry) j.next();
                            if (e.getValue() == t.param[i])
                                return (t.type == STRUCT ? "." : "") +
                                            e.getKey() + " (" + s + ")";
                        }
                    }
                    return s;
                }
            }
            t.seen = false;
        } else if (t.ref != null) {
            return checkPartialMatch(t.ref);
        }
        return null;
    }

    static Code caseType(Case ex, Scope scope, int depth) {
        Node[] choices = ex.choices;
        if (choices.length == 0) {
            throw new CompileException(ex, "case expects some option!");
        }
        Code val = analyze(ex.value, scope, depth);
        CaseCompiler cc = new CaseCompiler(val, depth);
        CasePattern[] pats = new CasePattern[choices.length];
        Scope[] scopes = new Scope[choices.length];
        Type argType = new Type(depth);
        for (int i = 0; i < choices.length; ++i) {
            cc.scope = scope;
            BinOp choice;
            if (!(choices[i] instanceof BinOp) ||
                (choice = (BinOp) choices[i]).op != ":") {
                throw new CompileException(choices[i],
                    "Expecting option, not a " + choices[i]);
            }
            pats[i] = cc.toPattern(choice.left, argType);
            scopes[i] = cc.scope;
            cc.exp.resetParams();
        }
        String partialError = checkPartialMatch(argType);
        if (partialError != null) {
            throw new CompileException(ex, "Partial match: " + partialError);
        }
        cc.finalizeVariants();
        for (int i = 0; i < choices.length; ++i) {
            cc.mergeChoice(pats[i], ((BinOp) choices[i]).right, scopes[i]);
        }
        try {
            unify(val.type, argType);
        } catch (TypeException e) {
            throw new CompileException(ex.value,
                "Inferred type for case argument is " + argType +
                ", but a " + val.type + " is given\n    (" +
                e.getMessage() + ")");
        }
        return cc.exp;
    }

    static Code list(XNode list, Scope scope, int depth) {
        Node[] items = list.expr == null ? new Node[0] : list.expr;
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
                Type t = JavaType.mergeTypes(type, codeItems[i].type);
                if (t != null) {
                    type = t;
                    continue;
                }
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
        if (list.expr == null) {
            keyType = new Type(depth);
            kind = MAP_TYPE;
        }
        Code res = kind == LIST_TYPE ? (Code) new ListConstructor(codeItems)
                                     : new MapConstructor(keyItems, codeItems);
        res.type = new Type(MAP, new Type[] { type, keyType, kind });
        res.polymorph = kind == LIST_TYPE;
        return res;
    }

    public static RootClosure toCode(String sourceName, String className,
                                     char[] src, int flags, Map classes,
                                     String[] preload) {
        TopLevel topLevel = new TopLevel();
        Object oldSrc = currentSrc.get();
        currentSrc.set(src);
        try {
            Parser parser = new Parser(sourceName, src, flags);
            Node n = parser.parse(topLevel);
            if ((flags & YetiC.CF_PRINT_PARSE_TREE) != 0) {
                System.err.println(n.show());
            }
            if (parser.moduleName != null) {
                className = parser.moduleName;
            }
            classes.put(className, null);
            RootClosure root = new RootClosure();
            Scope scope = new Scope((flags & YetiC.CF_NO_IMPORT) == 0
                                ? ROOT_SCOPE_SYS : ROOT_SCOPE, null, null);
            for (int i = 0; i < preload.length; ++i) {
                if (!preload[i].equals(className)) {
                    scope = explodeStruct(null, new LoadModule(preload[i],
                          YetiTypeVisitor.getType(null, preload[i])),
                          scope, 0, "yeti/lang/std".equals(preload[i]));
                }
            }
            if (parser.isModule)
                scope = bindImport("module", className, scope);
            if ((flags & YetiC.CF_EVAL_BIND) != 0) {
                List binds = YetiEval.get().bindings;
                for (int i = 0, cnt = binds.size(); i < cnt; ++i) {
                    YetiEval.Binding bind = (YetiEval.Binding) binds.get(i);
                    scope = bind.polymorph ? bindPoly(bind.name, bind.type,
                                                new EvalBind(bind), 0, scope)
                        : new Scope(scope, bind.name, new EvalBind(bind));
                }
            }
            root.preload = preload;
            scope.closure = root;
            scope.packageName = JavaType.packageOfClass(className);
            root.code = analyze(n, scope, 0);
            root.type = root.code.type;
            root.moduleName = parser.moduleName;
            root.isModule = parser.isModule;
            root.typeDefs = topLevel.typeDefs;
            if ((flags & YetiC.CF_COMPILE_MODULE) != 0 || parser.isModule) {
                List free = new ArrayList(), deny = new ArrayList();
                getFreeVar(free, deny, root.type, -1);
                if (!deny.isEmpty() ||
                    !free.isEmpty() && !root.code.polymorph) {
                    throw new CompileException(n,
                        "Module type is not fully defined");
                }
            } else if ((flags & YetiC.CF_EVAL) == 0) {
                try {
                    unify(root.type, UNIT_TYPE);
                } catch (TypeException ex) {
                    throw new CompileException(n,
                        "Program body must have a unit type, not "
                        + root.type, ex);
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
