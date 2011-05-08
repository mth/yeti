// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti code analyzer.
 * Copyright (c) 2007,2008,2009 Madis Janson
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
    static final class TopLevel {
        Map typeDefs = new HashMap();
        boolean isModule;
    }

    static final String NONSENSE_STRUCT = "No sense in empty struct";

    static void unusedBinding(Bind bind) {
        CompileCtx.current().warn(
            new CompileException(bind, "Unused binding: " + bind.name));
    }

    static XNode shortLambda(BinOp op) {
        return XNode.lambda(new Sym("_").pos(op.line, op.col), op.right, null);
    }

    static XNode asLambda(Node node) {
        BinOp op;
        return node.kind == "lambda" ? (XNode) node
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
            BindExpr self = (BindExpr) r.selfBind;
            if (self.refs == null)
                unusedBinding(bind);
            self.genBind(null); // initialize binding
            return r;
        }
        String kind = node.kind;
        if (kind != null) {
            XNode x = (XNode) node;
            if (kind == "()") {
                return new UnitConstant(null);
            }
            if (kind == "list") {
                return list(x, scope, depth);
            }
            if (kind == "lambda") {
                return lambda(new Function(null), x, scope, depth);
            }
            if (kind == "struct") {
                return structType(x, scope, depth);
            }
            if (kind == "if") {
                return cond(x, scope, depth);
            }
            if (kind == "_") {
                return new Cast(analyze(x.expr[0], scope, depth),
                                UNIT_TYPE, false, node.line);
            }
            if (kind == "concat") {
                return concatStr(x, scope, depth);
            }
            if (kind == "case-of") {
                return caseType(x, scope, depth);
            }
            if (kind == "new") {
                String name = x.expr[0].sym();
                Code[] args = mapArgs(1, x.expr, scope, depth);
                ClassBinding cb = resolveFullClass(name, scope, true, x);
                return new NewExpr(
                    JavaType.resolveConstructor(x, cb.type, args, true)
                            .check(x, scope.ctx.packageName), args, cb, x.line);
            }
            if (kind == "rsection") {
                return rsection(x, scope, depth);
            }
            if (kind == "try") {
                return tryCatch(x, scope, depth);
            }
            if (kind == "load") {
                if ((CompileCtx.current().flags & YetiC.CF_NO_IMPORT)
                     != 0) throw new CompileException(node, "load is disabled");
                String nam = x.expr[0].sym();
                ModuleType mt = YetiTypeVisitor.getType(node, nam, false);
                if (mt.deprecated)
                    CompileCtx.current().warn(new CompileException(node,
                         "Module " + nam.replace('/', '.') + " is deprecated"));
                return new LoadModule(nam, mt);
            }
            if (kind == "new-array")
                return newArray(x, scope, depth);
            if (kind == "classOf") {
                String cn = x.expr[0].sym();
                int arr = 0;
                while (cn.endsWith("[]")) {
                    ++arr;
                    cn = cn.substring(0, cn.length() - 2);
                }
                if (arr != 0)
                    cn = cn.intern();
                YType t = cn != "module" ? null :
                    resolveClass("module", scope, false);
                return new ClassOfExpr(t != null ? t.javaType :
                                resolveFullClass(cn, scope, false, x)
                                    .type.javaType.resolve(x), arr);
            }
        } else if (node instanceof BinOp) {
            BinOp op = (BinOp) node;
            String opop = op.op;
            if (opop == "")
                return apply(node, analyze(op.left, scope, depth),
                             op.right, scope, depth);
            if (opop == FIELD_OP) {
                if (op.right.kind == "list") {
                    return keyRefExpr(analyze(op.left, scope, depth),
                                      (XNode) op.right, scope, depth);
                }
                checkSelectorSym(op, op.right);
                return selectMember(op, (Sym) op.right,
                        analyze(op.left, scope, depth), scope, depth);
            }
            if (opop == ":=")
                return assignOp(op, scope, depth);
            if (opop == "\\")
                return lambda(new Function(null), shortLambda(op),
                              scope, depth);
            if (opop == "is" || opop == "as" || opop == "unsafely_as")
                return isOp(op, ((TypeOp) op).type,
                            analyze(op.right, scope, depth), scope, depth);
            if (opop == "#")
                return objectRef((ObjectRefOp) op, scope, depth);
            if (opop == "loop")
                return loop(op, scope, depth);
            if (opop == "-" && op.left == null)
                return apply(op, resolve("negate", op, scope, depth),
                                 op.right, scope, depth);
            if (opop == "not")
                return apply(op, resolve(opop, op, scope, depth),
                                 op.right, scope, depth);
            if (opop == "throw") {
                Code throwable = analyze(op.right, scope, depth);
                JavaType.checkThrowable(op, throwable.type);
                return new Throw(throwable, new YType(depth));
            }
            if (opop == "with")
                return withStruct(op, scope, depth);
            if (opop == "instanceof") {
                JavaType jt = resolveFullClass(((InstanceOf) op).className,
                                               scope).javaType.resolve(op);
                return new InstanceOfExpr(analyze(op.right, scope, depth), jt);
            }
            if (op.left == null)
                throw new CompileException(op,
                    "Internal error (incomplete operator " + op.op + ")");
            Code opfun = resolve(opop, op, scope, depth);
            if (opop == "^" && opfun instanceof StaticRef &&
                    "yeti/lang/std$$v".equals(((StaticRef) opfun).className)) {
                Code left = analyze(op.left, scope, depth);
                Code right = analyze(op.right, scope, depth);
                if (left instanceof StringConstant &&
                    right instanceof StringConstant) {
                    return new StringConstant(((StringConstant) left).str +
                                              ((StringConstant) right).str);
                }
                return new ConcatStrings(new Code[] { left, right });
            }
            return apply(op.right, apply(op, opfun, op.left, scope, depth),
                         op.right, scope, depth);
        }
        throw new CompileException(node,
            "I think that this " + node + " should not be here.");
    }

    static YType nodeToMembers(int type, TypeNode[] param, Map free,
                              Scope scope, int depth) {
        Map members = new HashMap();
        Map members_ = new HashMap();
        YType[] tp = new YType[param.length];
        for (int i = 0; i < param.length; ++i) {
            tp[i] = nodeToType(param[i].param[0], free, scope, depth);
            tp[i].doc = param[i].doc;
            if (param[i].var) {
                tp[i] = fieldRef(depth, tp[i], FIELD_MUTABLE);
            }
            String name = param[i].name;
            Map m = members;
            if (name.charAt(0) == '.') {
                name = name.substring(1);
                m = members_;
            }
            if (m.put(name, tp[i]) != null) {
                throw new CompileException(param[i], "Duplicate field name "
                                    + name + " in structure type");
            }
        }
        YType result = new YType(type, tp);
        if (type == STRUCT) {
            if (members.isEmpty()) {
                members = null;
            } else if (members_.isEmpty()) {
                members_ = null;
            }
            result.finalMembers = members;
            result.partialMembers = members_;
        } else {
            result.partialMembers = members;
        }
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

    static YType nodeToType(TypeNode node, Map free, Scope scope, int depth) {
        String name = node.name;
        for (int i = PRIMITIVE_TYPE_MAPPING.length; --i >= 0;) {
            if (PRIMITIVE_TYPE_MAPPING[i][0] == name) {
                expectsParam(node, 0);
                return (YType) PRIMITIVE_TYPE_MAPPING[i][1];
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
            YType[] tp = { nodeToType(node.param[0], free, scope, depth),
                          nodeToType(node.param[1], free, scope, depth) };
            return new YType(FUN, tp);
        }
        if (name == "array") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth),
                          NUM_TYPE, LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "list") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth),
                          NO_TYPE, LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "list?") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth),
                          new YType(depth), LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "hash") {
            expectsParam(node, 2);
            YType[] tp = { nodeToType(node.param[1], free, scope, depth),
                          nodeToType(node.param[0], free, scope, depth),
                          MAP_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "map") {
            expectsParam(node, 2);
            YType[] tp = { nodeToType(node.param[1], free, scope, depth),
                          nodeToType(node.param[0], free, scope, depth),
                          new YType(depth) };
            return new YType(MAP, tp);
        }
        if (Character.isUpperCase(name.charAt(0))) {
            return nodeToMembers(VARIANT, new TypeNode[] { node },
                                 free, scope, depth);
        }
        YType t;
        char c = name.charAt(0);
        if (c == '~') {
            expectsParam(node, 0);
            t = JavaType.typeOfName(
                name.substring(1).replace('.', '/').intern(), scope);
        } else if (c == '\'') {
            t = (YType) free.get(name);
            if (t == null)
                free.put(name, t = new YType(depth));
        } else if (c == '^') {
            t = (YType) free.get(name);
            if (t == null) {
                free.put(name, t = new YType(depth));
                t.flags = FL_ORDERED_REQUIRED;
            }
        } else {
            YType[] tp = new YType[node.param.length];
            for (int i = 0; i < tp.length; ++i)
                tp[i] = nodeToType(node.param[i], free, scope, depth);
            t = resolveTypeDef(scope, name, tp, depth, node);
        }
        return t;
    }

    static Code isOp(Node is, TypeNode type, Code value,
                     Scope scope, int depth) {
        YType t = nodeToType(type, new HashMap(), scope, depth).deref();
        YType vt = value.type.deref();
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
        unify(value.type, t, is, "#0 (when checking #1 is #2)");
        return value;
    }

    static Code[] mapArgs(int start, Node[] args, Scope scope, int depth) {
        if (args == null)
            return null;
        Code[] res = new Code[args.length - start];
        for (int i = start; i < args.length; ++i) {
            res[i - start] = analyze(args[i], scope, depth);
        }
        return res;
    }

    static Code objectRef(ObjectRefOp ref, Scope scope, int depth) {
        Code obj = null;
        YType t = null;
        if (ref.right instanceof Sym) {
            String className = ref.right.sym();
            t = resolveClass(className, scope, true);
            if (t == null && Character.isUpperCase(className.charAt(0)) &&
                (CompileCtx.current().flags & YetiC.CF_NO_IMPORT) == 0)
                t = JavaType.typeOfClass(scope.ctx.packageName, className);
        }
        if (t == null) {
            obj = analyze(ref.right, scope, depth);
            t = obj.type;
        }
        if (ref.arguments == null) {
            JavaType.Field f = JavaType.resolveField(ref, t, obj == null);
            f.check(ref, scope.ctx.packageName);
            if (f.constValue instanceof Number) {
                Number n = (Number) f.constValue;
                return new NumericConstant(
                        n instanceof Double || n instanceof Float
                        ? new yeti.lang.FloatNum(n.doubleValue())
                        : (yeti.lang.Num) new yeti.lang.IntNum(n.longValue()));
            }
            return new ClassField(obj, f, ref.line);
        }
        Code[] args = mapArgs(0, ref.arguments, scope, depth);
        return new MethodCall(obj,
                    JavaType.resolveMethod(ref, t, args, obj == null)
                        .check(ref, scope.ctx.packageName), args, ref.line);
    }

    static Code newArray(XNode op, Scope scope, int depth) {
        Code cnt = analyze(op.expr[1], scope, depth);
        unify(cnt.type, NUM_TYPE, op.expr[1],
              "array size must be a number (but here was #1)");
        return new NewArrayExpr(JavaType.typeOfName(op.expr[0].sym(), scope),
                                cnt, op.line);
    }

    static Code tryCatch(XNode t, Scope scope, int depth) {
        TryCatch tc = new TryCatch();
        scope = new Scope(scope, null, null); // closure frame
        scope.closure = tc;
        tc.setBlock(analyze(t.expr[0], scope, depth));
        int lastCatch = t.expr.length - 1;
        if (t.expr[lastCatch].kind != "catch") {
            tc.cleanup = analyze(t.expr[lastCatch], scope, depth);
            expectUnit(tc.cleanup, t.expr[lastCatch],
                      "finally block must have a unit type");
            --lastCatch;
        }
        for (int i = 1; i <= lastCatch; ++i) {
            XNode c = (XNode) t.expr[i];
            YType exception = resolveFullClass(c.expr[0].sym(), scope);
            exception.javaType.resolve(c);
            TryCatch.Catch cc = tc.addCatch(exception);
            String bind = c.expr[1].sym();
            cc.handler = analyze(c.expr[2], bind == "_" ? scope
                                    : new Scope(scope, bind, cc), depth);
            unify(tc.block.type, cc.handler.type, c.expr[2],
                  "This catch has #2 type, while try block was #1");
        }
        return tc;
    }

    static Code apply(Node where, Code fun, Node arg, Scope scope, int depth) {
        // try cast java types on apply
        YType funt = fun.type.deref(),
             funarg = funt.type == FUN ? funt.param[0].deref() : null;
        XNode lambdaArg = asLambda(arg);
        Code argCode = lambdaArg != null // prespecifing the lambda type
                ? lambda(new Function(funarg), lambdaArg, scope, depth)
                : analyze(arg, scope, depth);
        if (funarg != null &&
            JavaType.isSafeCast(where, funarg, argCode.type)) {
            argCode = new Cast(argCode, funarg, true, where.line);
        }
        YType[] applyFun = { argCode.type, new YType(depth) };
        try {
            unify(fun.type, new YType(FUN, applyFun));
        } catch (TypeException ex) {
            if (funt.type == UNIT) {
                throw new CompileException(where,
                            "Missing ; (Cannot apply ())");
            }
            if (funt.type != FUN && funt.type != VAR) {
                throw new CompileException(where,
                            "Too many arguments applied " +
                            "to a function, maybe a missing `;'?" +
                            "\n    (cannot apply " + funt + " to an argument)");
            }
            YType argt = argCode.type.deref();
            String s = "Cannot apply #1 function";
            if (where != arg && where instanceof BinOp) {
                BinOp op = (BinOp) where;
                String name;
                if ((name = op.op) != "" || op.left instanceof Sym
                        && (name = op.left.sym()) != null)
                    s += " `" + name + '\'';
            }
            s += " to #2 argument\n    #0";
            if (funarg != null && funarg.type != FUN && argt.type == FUN) {
                if (argCode instanceof Apply) {
                    s += "\n    Maybe you should apply the function given" +
                              " as an argument to more arguments.";
                } else {
                    s += "\n    Maybe you should apply the function given" +
                              " as an argument to some arguments?";
                }
            }
            throw new CompileException(where, fun.type, argCode.type, s, ex);
        }
        return fun.apply(argCode, applyFun[1], where.line);
    }

    static Code rsection(XNode section, Scope scope, int depth) {
        String sym = section.expr[0].sym();
        if (sym == FIELD_OP) {
            LinkedList parts = new LinkedList();
            Node x = section.expr[1];
            for (BinOp op; x instanceof BinOp; x = op.left) {
                op = (BinOp) x;
                if (op.op != FIELD_OP) {
                    throw new CompileException(op,
                        "Unexpected " + op.op + " in field selector");
                }
                checkSelectorSym(op, op.right);
                parts.addFirst(op.right.sym());
            }
            checkSelectorSym(section, x);
            parts.addFirst(x.sym());
            String[] fields =
                (String[]) parts.toArray(new String[parts.size()]);
            YType res = new YType(depth), arg = res;
            for (int i = fields.length; --i >= 0;) {
                arg = selectMemberType(arg, fields[i], depth);
            }
            return new SelectMemberFun(new YType(FUN, new YType[] { arg, res }),
                                       fields);
        }
        Code fun = resolve(sym, section, scope, depth);
        Code arg = analyze(section.expr[1], scope, depth);
        YType[] r = { new YType(depth), new YType(depth) };
        YType[] afun = { r[0], new YType(FUN, new YType[] { arg.type, r[1] }) };
        unify(fun.type, new YType(FUN, afun), section, fun.type, arg.type,
              "Cannot apply #2 as a 2nd argument to #1\n    #0");
        return fun.apply2nd(arg, new YType(FUN, r), section.line);
    }

    static Code variantConstructor(String name, int depth) {
        YType arg = new YType(depth);
        YType tag = new YType(VARIANT, new YType[] { arg });
        tag.partialMembers = new HashMap();
        tag.partialMembers.put(name, arg);
        YType[] fun = { arg, tag };
        return new VariantConstructor(new YType(FUN, fun), name);
    }

    static void checkSelectorSym(Node op, Node sym) {
        if (!(sym instanceof Sym)) {
            if (sym == null) {
                throw new CompileException(op, "What's that dot doing here?");
            }
            throw new CompileException(sym, "Illegal ." + sym);
        }
    }

    static YType selectMemberType(YType res, String field, int depth) {
        YType arg = new YType(STRUCT, new YType[] { res });
        arg.partialMembers = new HashMap();
        arg.partialMembers.put(field, res);
        return arg;
    }

    static Code selectMember(Node op, Sym member, Code src,
                             Scope scope, int depth) {
        final YType res = new YType(depth);
        final String field = member.sym;
        YType arg = selectMemberType(res, field, depth);
        try {
            unify(arg, src.type);
        } catch (TypeException ex) {
            int t = src.type.deref().type;
            if (t == JAVA) {
                throw new CompileException(member, src.type, null,
                    "Cannot use class #1 as a structure with ." +
                    field + " field\n    " +
                    "(use # instead of . to reference object fields/methods)",
                    ex);
            }
            if (src instanceof VariantConstructor) {
                throw new CompileException(member,
                    "Cannot use variant constructor " +
                    ((VariantConstructor) src).name +
                    " as a structure with ." + field + " field\n    " +
                    "(use # instead of . to reference class fields/methods)");
            }
            if (t != STRUCT && t != VAR) {
                throw new CompileException(member, "Cannot use " + src.type +
                                " as a structure with ." + field + " field");
            }
            throw new CompileException(member, src.type, null,
                        "#1 do not have ." + field + " field", ex);
        }
        boolean poly = src.polymorph && src.type.finalMembers != null &&
            ((YType) src.type.finalMembers.get(field)).field == 0;
        return new SelectMember(res, src, field, op.line, poly) {
            boolean mayAssign() {
                YType t = st.type.deref();
                YType given;
                if (t.finalMembers != null &&
                    (given = (YType) t.finalMembers.get(field)) != null &&
                    (given.field != FIELD_MUTABLE)) {
                    return false;
                }
                YType self = (YType) t.partialMembers.get(field);
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
        YType t = val.type.deref();
        if (t.type == JAVA_ARRAY) {
            unify(key.type, NUM_TYPE, keyList.expr[0],
                  "Array index must be a number (but here was #1)");
            return new JavaArrayRef(t.param[0], val, key, keyList.expr[0].line);
        }
        YType[] param = { new YType(depth), key.type, new YType(depth) };
        unify(val.type, new YType(MAP, param), keyList, val.type, key.type,
              "#1 cannot be referenced by #2 key");
        return new KeyRefExpr(param[0], val, key, keyList.line);
    }

    static Code assignOp(BinOp op, Scope scope, int depth) {
        Code left = analyze(op.left, scope, depth);
        Code right = analyze(op.right, scope, depth);
        unify(left.type, right.type, op, "#0");
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

    static YType mergeIfType(Node where, YType result, YType val) {
        try {
            return mergeOrUnify(result, val);
        } catch (TypeException ex) {
            throw new CompileException(where, val, result,
                "This if branch has a #1 type, while another was a #2", ex);
        }
    }

    static Code cond(XNode condition, Scope scope, int depth) {
        List conds = new ArrayList();
        YType result = null;
        boolean poly = true;
        for (;;) {
            Code cond = analyze(condition.expr[0], scope, depth);
            unify(cond.type, BOOL_TYPE, condition.expr[0],
                  "if condition must have a boolean type (but here was #1)");
            Code val = analyze(condition.expr[1], scope, depth);
            conds.add(new Code[] { val, cond });
            poly &= val.polymorph;
            if (result == null) {
                result = val.type;
            } else {
                result = mergeIfType(condition.expr[1], result, val.type);
            }
            if (condition.expr[2].kind != "if")
                break;
            condition = (XNode) condition.expr[2];
        }
        Code val = analyze(condition.expr[2], scope, depth);
        result = mergeIfType(condition.expr[2], result, val.type);
        conds.add(new Code[] { val });
        Code[][] expr = (Code[][]) conds.toArray(new Code[conds.size()][]);
        return new ConditionalExpr(result, expr, poly && val.polymorph);
    }

    static Code loop(BinOp loop, Scope scope, int depth) {
        Node condNode = loop.left != null ? loop.left : loop.right;
        Code cond = analyze(condNode, scope, depth);
        unify(cond.type, BOOL_TYPE, condNode,
              "Loop condition must have a boolean type (but here was #1)");
        if (loop.left == null) {
            return new LoopExpr(cond, new UnitConstant(null));
        }
        Code body = analyze(loop.right, scope, depth);
        expectUnit(body, loop.right, "Loop body must have a unit type");
        return new LoopExpr(cond, body);
    }

    static Code withStruct(BinOp with, Scope scope, int depth) {
        Code src = analyze(with.left, scope, depth);
        Code override = analyze(with.right, scope, depth);
        YType ot = override.type.deref();
        Map otf = ot.finalMembers;
        if (otf == null || ot.type != STRUCT)
            throw new CompileException(with.right, "Right-hand side of with " +
                            "must be a structure with known member set");
        YType result, st = src.type.deref();
        if (st.type == STRUCT && st.finalMembers != null) {
            Map param = new HashMap(st.finalMembers);
            param.putAll(otf);
            // with ensures override, because type can change and
            // members can be missing in the source structure.
            // Another mechanism is needed "default arguments".
            if (ot.partialMembers == null) {
                ot.partialMembers = otf;
            } else {
                HashMap tmp = new HashMap(otf);
                tmp.keySet().removeAll(ot.partialMembers.keySet());
                ot.partialMembers.putAll(otf);
            }
            // lock used members.
            HashMap tmp = new HashMap(st.finalMembers);
            tmp.keySet().removeAll(otf.keySet());
            if (st.partialMembers != null)
                tmp.putAll(st.partialMembers);
            st.partialMembers = tmp;
            result = new YType(STRUCT, NO_PARAM);
            result.finalMembers = param;
            result.param =
                (YType[]) param.values().toArray(new YType[param.size()]);
        } else {
            result = new YType(STRUCT, NO_PARAM);
            result.partialMembers = new HashMap(otf);
            result.param = ot.param;
            unify(src.type, result, with.right, "Cannot extend #1 with #2");
        }
        return new WithStruct(result, src, override,
                        (String[]) otf.keySet().toArray(new String[otf.size()]));
    }

    static Function singleBind(Bind bind, Scope scope, int depth) {
        if (bind.expr.kind != "lambda") {
            throw new CompileException(bind,
                "Closed binding must be a function binding");
        }
        // recursive binding
        Function lambda = new Function(new YType(depth + 1));
        BindExpr binder = new BindExpr(lambda, bind.var);
        lambda.selfBind = binder;
        if (!bind.noRec)
            scope = new Scope(scope, bind.name, binder);
        lambdaBind(lambda, bind, scope, depth + 1);
        lambda.type.doc = bind.doc;
        return lambda;
    }

    static Scope explodeStruct(Node where, LoadModule m,
                               Scope scope, int depth, boolean noRoot) {
        m.checkUsed = true;
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
                YType t = (YType) e.getValue();
                scope = bindPoly(name, t, m.bindField(name, t), false, depth, scope);
            }
        } else if (m.type.type != UNIT) {
            throw new CompileException(where,
                "Expected module with struct or unit type here (" +
                m.moduleName.replace('/', '.') + " has type " + m.type +
                ", but only structs can be exploded)");
        }
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

    static Scope genericBind(Bind bind, BindExpr binder, boolean evalSeq,
                             Scope scope, int depth) {
        switch (binder.st.polymorph ? VAR : binder.st.type.deref().type) {
        case VAR: case FUN: case MAP: case STRUCT: case VARIANT:
            if (!bind.var) {
                scope = bindPoly(bind.name, binder.st.type, binder,
                                 !binder.st.polymorph, depth, scope);
                break;
            }
        default:
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
                (bind.name = nameNode.sym()) == "_")
                throw new CompileException(nameNode,
                    "Binding name expected, not a " + nameNode);
            Code code = selectMember(fields[j], (Sym) bind.expr,
                          binder.getRef(fields[j].line), scope, depth);
            if (field.type != null) {
                isOp(field, field.type, code, scope, depth);
            }
            BindExpr bindExpr = new BindExpr(code, false);
            scope = genericBind(bind, bindExpr, isEval, scope, depth);
            addSeq(last, bindExpr);
        }
        return scope;
    }

    static Scope bindTypeDef(TypeDef typeDef, Object seqKind, Scope scope) {
        YType self = new YType(-1);
        Scope defScope = new Scope(scope, typeDef.name, null);
        defScope.free = NO_PARAM;
        defScope.typeDef = new YType[] { self };
        YType[] def = new YType[typeDef.param.length + 1];
        // binding typedef arguments
        for (int i = typeDef.param.length; --i >= 0;) {
            YType arg = new YType(-1);
            def[i] = arg;
            defScope = new Scope(defScope, typeDef.param[i], null);
            defScope.typeDef = new YType[] { arg };
            defScope.free = NO_PARAM;
        }
        YType type =
            nodeToType(typeDef.type, new HashMap(), defScope, 1).deref();
        def[def.length - 1] = type;
        // XXX the order of unify arguments matters!
        unify(self, type, typeDef, type, self,
              "Type #~ (type self-binding)\n    #0");
        scope = bindPoly(typeDef.name, type, null, false, 0, scope);
        scope.typeDef = def;
        if (seqKind instanceof TopLevel) {
            ((TopLevel) seqKind).typeDefs.put(typeDef.name, def);
        }
        return scope;
    }

    static void expectUnit(Code value, Node where, String what) {
        if (value.type.type == JAVA || value.type.type == JAVA_ARRAY)
            return; // java is messy, don't try to be strict with it
        try {
            unify(value.type, UNIT_TYPE);
        } catch (TypeException ex) {
            String s = what + ", not a #1";
            YType t = value.type.deref();
            int tt;
            if (t.type == FUN &&
                ((tt = t.param[1].deref().type) == VAR
                    || tt == UNIT || tt == FUN)
                && !(value instanceof BindRef)
                && !(value instanceof Function)) {
                s += "\n    Maybe you should give more arguments"
                   + " to the function?";
            }
            throw new CompileException(where, value.type, null, s, ex);
        }
    }

    static Code analSeq(Seq seq, Scope scope, int depth) {
        Node[] nodes = seq.st;
        BindExpr[] bindings = new BindExpr[nodes.length];
        SeqExpr[] last = { null, null };
        for (int i = 0; i < nodes.length - 1; ++i) {
            if (nodes[i] instanceof Bind) {
                Bind bind = (Bind) nodes[i];
                BindExpr binder;
                if (bind.expr.kind == "lambda") {
                    binder = (BindExpr) singleBind(bind, scope, depth).selfBind;
                } else {
                    Code code = analyze(bind.expr, scope, depth + 1);
                    binder = new BindExpr(code, bind.var);
                    if (bind.type != null) {
                        isOp(bind, bind.type, binder.st, scope, depth);
                    }
                }
                if (bind.doc != null)
                    binder.st.type.doc = bind.doc;
                scope = genericBind(bind, binder, seq.seqKind == Seq.EVAL,
                                    scope, depth);
                bindings[i] = binder;
                addSeq(last, binder);
            } else if (nodes[i].kind == "struct-bind") {
                XNode x = (XNode) nodes[i];
                Code expr = analyze(x.expr[1], scope, depth + 1);
                BindExpr binder = new BindExpr(expr, false);
                addSeq(last, binder);
                scope = bindStruct(binder, (XNode) x.expr[0],
                                   seq.seqKind == Seq.EVAL, scope, depth, last);
            } else if (nodes[i].kind == "load") {
                LoadModule m = (LoadModule) analyze(nodes[i], scope, depth);
                scope = explodeStruct(nodes[i], m, scope, depth - 1, false);
                addSeq(last, new SeqExpr(m));
                Iterator j = m.moduleType.typeDefs.entrySet().iterator();
                while (j.hasNext()) {
                    Map.Entry e = (Map.Entry) j.next();
                    YType[] typeDef = (YType[]) e.getValue();
                    scope = bindPoly((String) e.getKey(),
                                typeDef[typeDef.length - 1], null, false, 0, scope);
                    scope.typeDef = typeDef;
                }
            } else if (nodes[i].kind == "import") {
                if ((CompileCtx.current().flags & YetiC.CF_NO_IMPORT) != 0)
                    throw new CompileException(nodes[i], "import is disabled");
                Node[] imports = ((XNode) nodes[i]).expr;
                for (int j = 0; j < imports.length; ++j) {
                    String name = imports[j].sym();
                    int lastSlash = name.lastIndexOf('/');
                    scope = new Scope(scope, (lastSlash < 0 ? name :
                                 name.substring(lastSlash + 1)).intern(), null);
                    YType classType = new YType("L" + name + ';');
                    scope.importClass = new ClassBinding(classType);
                    if (seq.seqKind == Seq.EVAL)
                        YetiEval.registerImport(scope.name, classType);
                }
            } else if (nodes[i] instanceof TypeDef) {
                scope = bindTypeDef((TypeDef) nodes[i], seq.seqKind, scope);
            } else if (nodes[i].kind == "class") {
                Scope scope_[] = { scope };
                addSeq(last, new SeqExpr(
                    MethodDesc.defineClass((XNode) nodes[i],
                        seq.seqKind instanceof TopLevel &&
                            ((TopLevel) seq.seqKind).isModule, scope_, depth)));
                scope = scope_[0];
            } else {
                Code code = analyze(nodes[i], scope, depth);
                expectUnit(code, nodes[i], "Unit type expected here");
                //code.ignoreValue();
                addSeq(last, new SeqExpr(code));
            }
        }
        Node expr = nodes[nodes.length - 1];
        Code code = expr.kind == "class" && seq.seqKind instanceof TopLevel &&
                    ((TopLevel) seq.seqKind).isModule
            ? MethodDesc.defineClass((XNode) expr, true, new Scope[] { scope },
                                     depth)
            : analyze(expr, scope, depth);
        for (int i = bindings.length; --i >= 0;) {
            if (bindings[i] != null && bindings[i].refs == null &&
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
        return lambda(to, (XNode) bind.expr, scope, depth);
    } 

    static Code lambda(Function to, XNode lambda, Scope scope, int depth) {
        YType expected = to.type == null ? null : to.type.deref();
        to.polymorph = true;
        Scope bodyScope = null;
        SeqExpr[] seq = null;
        Node arg = lambda.expr[0];
        if (arg instanceof Sym) {
            if (expected != null && expected.type == FUN) {
                to.arg.type = expected.param[0];
            } else {
                to.arg.type = new YType(depth);
            }
            String argName = arg.sym();
            if (argName != "_")
                bodyScope = new Scope(scope, argName, to);
        } else if (arg.kind == "()") {
            to.arg.type = UNIT_TYPE;
        } else if (arg.kind == "struct") {
            to.arg.type = new YType(depth);
            seq = new SeqExpr[] { null, null };
            bodyScope = bindStruct(to, (XNode) arg, false, scope, depth, seq);
        } else {
            throw new CompileException(arg, "Bad argument: " + arg);
        }
        if (bodyScope == null)
            bodyScope = new Scope(scope, null, to);
        Scope marker = bodyScope;
        while (marker.outer != scope)
            marker = marker.outer;
        marker.closure = to;
        if (lambda.expr[1].kind == "lambda") {
            Function f = new Function(expected != null && expected.type == FUN
                                      ? expected.param[1] : null);
            // make f to know about its outer scope before processing it
            to.setBody(seq == null || seq[0] == null ? (Code) f : seq[1]);
            lambda(f, (XNode) lambda.expr[1], bodyScope, depth);
            wrapSeq(f, seq);
        } else {
            Code body = analyze(lambda.expr[1], bodyScope, depth);
            YType res; // try casting to expected type
            if (expected != null && expected.type == FUN &&
                JavaType.isSafeCast(lambda, res = expected.param[1].deref(),
                                    body.type)) {
                body = new Cast(body, res, true, lambda.expr[1].line);
            }
            to.setBody(wrapSeq(body, seq));
        }
        YType fun = new YType(FUN, new YType[] { to.arg.type, to.body.type });
        if (to.type != null) {
            unify(fun, to.type, lambda,
                  "Function type #~ (self-binding)\n    #0");
        }
        to.type = fun;
        to.bindName = lambda.expr.length > 2 ? lambda.expr[2].sym() : null;
        to.body.markTail();
        return to;
    }

    private static Bind getField(Node node) {
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
        Scope local = scope, propertyScope = null;
        Map fields = new HashMap(), codeMap = new HashMap();
        Function[] funs = new Function[nodes.length];
        StructConstructor result = new StructConstructor(nodes.length);
        result.polymorph = true;

        // Functions see struct members in their scope
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = getField(nodes[i]);
            Function lambda = !field.noRec && field.expr.kind == "lambda"
                            ? funs[i] = new Function(new YType(depth)) : null;
            Code code = lambda;
            StructField sf = (StructField) codeMap.get(field.name);
            if (field.property) {
                if (sf == null) {
                    sf = new StructField();
                    sf.property = 1;
                    sf.name = field.name;
                    sf.line = field.line;
                    codeMap.put(sf.name, sf);
                    result.add(sf);
                } else if (sf.property == 0 ||
                           (field.var ? sf.setter : sf.value) != null) {
                    duplicateField(field);
                }
                if (code == null) {
                    if (propertyScope == null) {
                        propertyScope = new Scope(scope, null, null);
                        propertyScope.closure = result;
                    }
                    code = analyze(field.expr, propertyScope, depth);
                }
                // get is () -> t, set is t -> ()
                YType t = (YType) fields.get(field.name);
                if (t == null) {
                    t = new YType(depth);
                    t.field = FIELD_NON_POLYMORPHIC;
                    fields.put(field.name, t);
                }
                if (field.var)
                    t.field = FIELD_MUTABLE;
                if (field.doc != null)
                    t.doc = t.doc == null
                        ? field.doc : field.doc + '\n' + t.doc;
                YType f = new YType(FUN, field.var
                                ? new YType[] { t, UNIT_TYPE }
                                : new YType[] { UNIT_TYPE, t });
                try {
                    unify(code.type, f);
                } catch (TypeException ex) {
                    throw new CompileException(nodes[i], code.type, f,
                                (field.var ? "Setter " : "Getter ")
                                + field.name + " type #~", ex);
                }
                if (field.var) {
                    sf.setter = code;
                    sf.mutable = true;
                } else {
                    sf.value = code;
                }
            } else {
                if (sf != null)
                    duplicateField(field);
                if (code == null)
                    code = analyze(field.expr, scope, depth);
                sf = new StructField();
                sf.name = field.name;
                sf.value = code;
                sf.mutable = field.var;
                codeMap.put(field.name, sf);
                result.add(sf);
                boolean poly = code.polymorph || lambda != null;
                /* XXX might be unsafe.
                 * if (!poly && !field.var) {
                    switch (code.type.deref().type) {
                    case VAR: case FUN: case MAP: case STRUCT: case VARIANT:
                        List deny = new ArrayList();
                        List vars = new ArrayList();
                        // XXX uh. depth - 1, should it work?
                        getFreeVar(vars, deny, code.type, true, depth - 1);
                        poly = deny.size() == 0 && vars.size() != 0;
                    }
                }*/
                fields.put(field.name,
                    field.var ? fieldRef(depth, code.type, FIELD_MUTABLE) :
                    poly ? code.type
                        : fieldRef(depth, code.type, FIELD_NON_POLYMORPHIC));
                if (!field.noRec) {
                    Binder bind = result.bind(sf);
                    if (lambda != null)
                        lambda.selfBind = bind;
                    local = new Scope(local, field.name, bind);
                }
            }
            if (field.doc != null)
                code.type.doc = field.doc;
        }
        // property accessors must be proxied so the struct could inline them
        if (result.properties != null) {
            propertyScope = new Scope(local, null, null);
            propertyScope.closure = result;
        }
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = (Bind) nodes[i];
            if (funs[i] != null) {
                lambdaBind(funs[i], field, ((Bind) nodes[i]).property
                                ? propertyScope :  local, depth);
            }
        }
        result.type = new YType(STRUCT,
            (YType[]) fields.values().toArray(new YType[fields.size()]));
        for (StructField i = result.properties; i != null; i = i.nextProperty)
            if (i.value == null)
                throw new CompileException(st,
                    "Property " + i.name + " has no getter");
        result.type.finalMembers = fields;
        result.close();
        return result;
    }

    static final class CaseCompiler {
        CaseExpr exp;
        Scope scope;
        int depth;
        List variants = new ArrayList();
        int submatch; // hack for variants
  
        CaseCompiler(Code val, int depth) {
            exp = new CaseExpr(val);
            exp.polymorph = true;
            this.depth = depth;
        }

        CasePattern toPattern(Node node, YType t) {
            if ((t.flags & FL_ANY_PATTERN) != 0) {
                throw new CompileException(node,
                    "Useless case " + node + " (any value already matched)");
            }
            if (node instanceof Sym) {
                t.flags |= FL_ANY_PATTERN;
                String name = node.sym();
                if (name == "_" || name == "...")
                    return CasePattern.ANY_PATTERN;
                BindPattern binding = new BindPattern(exp, t);
                scope = new Scope(scope, name, binding);
                t = t.deref();
                if (t.type == VARIANT) {
                    t.flags |= FL_ANY_PATTERN;
                }
                return binding;
            }
            if (node.kind == "()") {
                unify(t, UNIT_TYPE, node, "#0");
                return CasePattern.ANY_PATTERN;
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
            if (node.kind == "list") {
                XNode list = (XNode) node;
                YType itemt = new YType(depth);
                YType lt = new YType(MAP,
                        new YType[] { itemt, new YType(depth), LIST_TYPE });
                lt.flags |= FL_PARTIAL_PATTERN;
                if (list.expr == null || list.expr.length == 0) {
                    unify(t, lt, node, "#0");
                    return AListPattern.EMPTY_PATTERN;
                }
                CasePattern[] items = new CasePattern[list.expr.length];
                int anyitem = FL_ANY_PATTERN;
                ++submatch;
                for (int i = 0; i < items.length; ++i) {
                    itemt.flags &= ~FL_ANY_PATTERN;
                    items[i] = toPattern(list.expr[i], itemt);
                    anyitem &= itemt.flags;
                }
                --submatch;
                itemt.flags &= anyitem;
                unify(t, lt, node, "#0");
                return new ListPattern(items);
            }
            if (node instanceof BinOp) {
                BinOp pat = (BinOp) node;
                if (pat.op == "" && pat.left instanceof Sym) {
                    String variant = pat.left.sym();
                    if (!Character.isUpperCase(variant.charAt(0))) {
                        throw new CompileException(pat.left, variant +
                            ": Variant constructor must start with upper case");
                    }
                    t = t.deref();
                    if (t.type != VAR && t.type != VARIANT) {
                        throw new CompileException(node,
                            "Variant " + variant + " ... is not " + t);
                    }
                    t.type = VARIANT;
                    if (t.partialMembers == null) {
                        t.partialMembers = new HashMap();
                        if (submatch == 0) { // XXX hack!!!
                            variants.add(t);
                        }
                    }
                    YType argt = new YType(depth);
                    CasePattern arg = toPattern(pat.right, argt);
                    YType old = (YType) t.partialMembers.put(variant, argt);
                    if (old != null) {
                        // same constructor already. shall be same type.
                        unify(old, argt, pat.right, "#0");
                    }
                    t.param = (YType[]) t.partialMembers.values().toArray(
                                new YType[t.partialMembers.size()]);
                    return new VariantPattern(variant, arg);
                }
                if (pat.op == "::") {
                    YType itemt = new YType(depth);
                    // It must must have the NO_TYPE constraint,
                    // because tail has the same type as the matched
                    // (this could be probably solved by giving tail
                    //  and pattern separate list types, but then
                    //  correct use of pattern flags must be considered)
                    YType lt = new YType(MAP,
                                new YType[] { itemt, NO_TYPE, LIST_TYPE });
                    int flags = t.flags; 
                    unify(t, lt, node, "#0");
                    ++submatch;
                    CasePattern hd = toPattern(pat.left, itemt);
                    CasePattern tl = toPattern(pat.right, t);
                    --submatch;
                    lt.flags = FL_PARTIAL_PATTERN;
                    t.flags = flags;
                    return new ConsPattern(hd, tl);
                }
            }
            if (node.kind == "struct") {
                Node[] fields = ((XNode) node).expr;
                if (fields.length == 0)
                    throw new CompileException(node, NONSENSE_STRUCT);
                String[] names = new String[fields.length];
                CasePattern[] patterns = new CasePattern[fields.length];
                HashMap uniq = new HashMap();
                ++submatch;
                for (int i = 0; i < fields.length; ++i) {
                    Bind field = getField(fields[i]);
                    if (uniq.containsKey(field.name))
                        duplicateField(field);
                    uniq.put(field.name, null);
                    YType ft = new YType(depth);
                    YType part = new YType(STRUCT, new YType[] { ft });
                    HashMap tm = new HashMap();
                    tm.put(field.name, ft);
                    part.partialMembers = tm;
                    unify(t, part, field, "#0");
                    names[i] = field.name;
                    patterns[i] = toPattern(field.expr, ft);
                }
                --submatch;
                return new StructPattern(names, patterns);
            }
            throw new CompileException(node, "Bad case pattern: " + node);
        }

        void finalizeVariants() {
            for (int i = variants.size(); --i >= 0;) {
                YType t = (YType) variants.get(i);
                if (t.type == VARIANT && t.finalMembers == null &&
                    (t.flags & FL_ANY_PATTERN) == 0) {
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
                    exp.type = mergeOrUnify(exp.type, opt.type);
                } catch (TypeException e) {
                    throw new CompileException(node, opt.type, exp.type,
                        "This choice has a #1 type, while another was a #2", e);
                }
            }
            exp.addChoice(pat, opt);
        }
    }

    static String checkPartialMatch(YType t) {
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

    static Code caseType(XNode ex, Scope scope, int depth) {
        Node[] choices = ex.expr;
        if (choices.length <= 1) {
            throw new CompileException(ex, "case expects some option!");
        }
        Code val = analyze(choices[0], scope, depth);
        CaseCompiler cc = new CaseCompiler(val, depth);
        CasePattern[] pats = new CasePattern[choices.length];
        Scope[] scopes = new Scope[choices.length];
        YType argType = new YType(depth);
        for (int i = 1; i < choices.length; ++i) {
            cc.scope = scope;
            pats[i] = cc.toPattern(((XNode) choices[i]).expr[0], argType);
            scopes[i] = cc.scope;
            cc.exp.resetParams();
        }
        String partialError = checkPartialMatch(argType);
        if (partialError != null) {
            throw new CompileException(ex, "Partial match: " + partialError);
        }
        cc.finalizeVariants();
        for (int i = 1; i < choices.length; ++i) {
            if (choices[i].kind != "...") {
                cc.mergeChoice(pats[i], ((XNode) choices[i]).expr[1], scopes[i]);
            }
        }
        unify(val.type, argType, choices[0],
          "Inferred type for case argument is #2, but a #1 is given\n    (#0)");
        return cc.exp;
    }

    static Code list(XNode list, Scope scope, int depth) {
        Node[] items = list.expr == null ? new Node[0] : list.expr;
        Code[] keyItems = null;
        Code[] codeItems = new Code[items.length];
        YType type = null;
        YType keyType = NO_TYPE;
        YType kind = null;
        BinOp bin;
        XNode keyNode = null;
        int n = 0;
        for (int i = 0; i < items.length; ++i, ++n) {
            if (items[i].kind == ":") {
                if (keyNode != null)
                    throw new CompileException(items[i],
                                               "Expecting , here, not :");
                keyNode = (XNode) items[i];
                if (kind == LIST_TYPE) {
                    throw new CompileException(keyNode,
                        "Unexpected : in list" + (i != 1 ? "" :
                        " (or the key is missing on the first item?)"));
                }
                --n;
                continue;
            }
            if (keyNode != null) {
                Code key = analyze(keyNode.expr[0], scope, depth);
                if (kind != MAP_TYPE) {
                    keyType = key.type;
                    kind = MAP_TYPE;
                    keyItems = new Code[items.length / 2];
                } else {
                    unify(keyType, key.type, items[i],
                        "This map element has #2 key, but others have had #1");
                }
                keyItems[n] = key;
                codeItems[n] = analyze(items[i], scope, depth);
                keyNode = null;
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
                    unify(from.type, NUM_TYPE, bin.left,
                          ".. range expects limit to be number, not a #1");
                    unify(to.type, NUM_TYPE, bin.right,
                          ".. range expects limit to be number, not a #1");
                    codeItems[n] = new Range(from, to);
                } else {
                    codeItems[n] = analyze(items[i], scope, depth);
                }
            }
            if (type == null) {
                type = codeItems[n].type;
            } else {
                try {
                    type = mergeOrUnify(type, codeItems[n].type);
                } catch (TypeException ex) {
                    throw new CompileException(items[i], codeItems[n].type,
                        type, (kind == LIST_TYPE ? "This list element is "
                                                 : "This map element is ") +
                         "#1, but others have been #2", ex);
                }
            }
        }
        if (type == null) {
            type = new YType(depth);
        }
        if (kind == null) {
            kind = LIST_TYPE;
        }
        if (list.expr == null) {
            keyType = new YType(depth);
            keyItems = new Code[0];
            kind = MAP_TYPE;
        }
        Code res = kind == LIST_TYPE ? (Code) new ListConstructor(codeItems)
                                     : new MapConstructor(keyItems, codeItems);
        res.type = new YType(MAP, new YType[] { type, keyType, kind });
        res.polymorph = kind == LIST_TYPE;
        return res;
    }

    public static RootClosure toCode(String sourceName, String className,
                                     char[] src, CompileCtx ctx,
                                     String[] preload) {
        TopLevel topLevel = new TopLevel();
        Object oldSrc = currentSrc.get();
        currentSrc.set(src);
        try {
            Parser parser = new Parser(sourceName, src, ctx.flags);
            Node n;
            try {
                n = parser.parse(topLevel);
            } catch (CompileException ex) {
                if (ex.line == 0)
                    ex.line = parser.currentLine();
                throw ex;
            }
            if ((ctx.flags & YetiC.CF_PRINT_PARSE_TREE) != 0) {
                System.err.println(n.str());
            }
            if (parser.moduleName != null) {
                className = parser.moduleName;
            }
            ctx.addClass(className, null);
            RootClosure root = new RootClosure();
            Scope scope = new Scope((ctx.flags & YetiC.CF_NO_IMPORT) == 0
                                ? ROOT_SCOPE_SYS : ROOT_SCOPE, null, null);
            LoadModule[] preloadModules = new LoadModule[preload.length];
            for (int i = 0; i < preload.length; ++i) {
                if (!preload[i].equals(className)) {
                    preloadModules[i] =
                        new LoadModule(preload[i],
                              YetiTypeVisitor.getType(null, preload[i], false));
                    scope = explodeStruct(null, preloadModules[i],
                              scope, 0, "yeti/lang/std".equals(preload[i]));
                }
            }
            if (parser.isModule)
                scope = bindImport("module", className, scope);
            if ((ctx.flags & YetiC.CF_EVAL_RESOLVE) != 0) {
                List binds = YetiEval.get().bindings;
                for (int i = 0, cnt = binds.size(); i < cnt; ++i) {
                    YetiEval.Binding bind = (YetiEval.Binding) binds.get(i);
                    if (bind.isImport) {
                        scope = new Scope(scope, bind.name, null);
                        scope.importClass = new ClassBinding(bind.type);
                        continue;
                    }
                    scope = bindPoly(bind.name, bind.type, new EvalBind(bind),
                                     !bind.polymorph, 0, scope);
                }
            }
            topLevel.isModule = parser.isModule;
            root.preload = preloadModules;
            scope.closure = root;
            scope.ctx = new ScopeCtx();
            scope.ctx.packageName = JavaType.packageOfClass(className);
            scope.ctx.className = className;
            root.code = analyze(n, scope, 0);
            root.type = root.code.type;
            root.moduleType = new ModuleType(root.type, topLevel.typeDefs,
                                             java.util.Collections.EMPTY_MAP);
            root.moduleType.topDoc = parser.topDoc;
            root.moduleType.deprecated = parser.deprecated;
            root.moduleType.name = parser.moduleName;
            root.isModule = parser.isModule;
            if ((ctx.flags & YetiC.CF_COMPILE_MODULE) != 0 || parser.isModule) {
                List free = new ArrayList(), deny = new ArrayList();
                getFreeVar(free, deny, root.type, false, -1);
                if (!deny.isEmpty() ||
                    !free.isEmpty() && !root.code.polymorph) {
                    throw new CompileException(n,
                        "Module type is not fully defined");
                }
            } else if ((ctx.flags & YetiC.CF_EVAL) == 0) {
                expectUnit(root, n, "Program body must have a unit type");
            }
            return root;
        } finally {
            currentSrc.set(oldSrc);
        }
    }
}
