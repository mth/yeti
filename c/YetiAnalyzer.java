// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti code analyzer.
 * Copyright (c) 2007-2013 Madis Janson
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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import yeti.lang.Num;
import yeti.lang.FloatNum;
import yeti.lang.IntNum;

public final class YetiAnalyzer extends YetiType {
    static final class TopLevel {
        Map typeDefs = new HashMap();
        Scope typeScope;
        boolean isModule;
    }

    static final String NONSENSE_STRUCT = "No sense in empty struct";

    static void unusedBinding(Scope scope, Bind bind) {
        scope.ctx.compiler.warn(
            new CompileException(bind, "Unused binding: " + bind.name));
    }

    static XNode shortLambda(BinOp op) {
        Sym arg;
        Node[] cases;
        if (op.right.kind == "case-of" &&
            (cases = ((XNode) op.right).expr)[0].kind == "()" &&
            ((XNode) cases[0]).expr != null) {
            cases[0] = arg = new Sym(String.valueOf(cases.hashCode()));
        } else {
            arg = new Sym("_");
        }
        return XNode.lambda(arg.pos(op.line, op.col), op.right, null);
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
            if (Character.isUpperCase(sym.charAt(0)))
                return variantConstructor(sym, depth);
            return resolve(sym, node, scope, depth);
        }
        if (node instanceof NumLit)
            return new NumericConstant(((NumLit) node).num);
        if (node instanceof Str)
            return new StringConstant(((Str) node).str);
        if (node instanceof Seq)
            return analSeq((Seq) node, scope, depth);
        if (node instanceof Bind) {
            Bind bind = (Bind) node;
            Function r = singleBind(bind, scope, depth);
            BindExpr self = (BindExpr) r.selfBind;
            if (self.refs == null)
                unusedBinding(scope, bind);
            self.genBind(null); // initialize binding
            return r;
        }
        String kind = node.kind;
        if (kind != null) {
            if (kind == "listop") {
                ObjectRefOp l = (ObjectRefOp) node;
                if (l.right == null)
                    return list(l, l.arguments, scope, depth);
                return keyRefExpr(analyze(l.right, scope, depth),
                                  l, scope, depth);
            }
            XNode x = (XNode) node;
            if (kind == "()")
                return new UnitConstant(null);
            if (kind == "list")
                return list(x, x.expr, scope, depth);
            if (kind == "lambda")
                return lambda(new Function(null), x, scope, depth);
            if (kind == "struct")
                return structType(x, scope, depth);
            if (kind == "if")
                return cond(x, scope, depth);
            if (kind == "_")
                return new Cast(analyze(x.expr[0], scope, depth),
                                UNIT_TYPE, false, node.line);
            if (kind == "concat")
                return concatStr(x, scope, depth);
            if (kind == "case-of")
                return CaseCompiler.caseType(x, scope, depth);
            if (kind == "new") {
                String name = x.expr[0].sym();
                Code[] args = mapArgs(1, x.expr, scope, depth);
                ClassBinding cb = resolveFullClass(name, scope, true, x);
                //TODO: handle SAM here too, and test
                return new NewExpr(
                    JavaType.resolveConstructor(x, cb.type, args, true)
                            .check(x, scope.ctx.packageName, 0),
                                   args, cb, x.line);
            }
            if (kind == "rsection")
                return rsection(x, scope, depth);
            if (kind == "try")
                return tryCatch(x, scope, depth);
            if (kind == "load") {
                String nam = x.expr[0].sym();
                ModuleType mt = (ModuleType) x.expr[1];
                if (mt.deprecated)
                    scope.ctx.compiler.warn(new CompileException(node,
                         "Module " + nam.replace('/', '.') + " is deprecated"));
                return new LoadModule(nam, mt, depth);
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
                return apply(op, analyze(op.left, scope, depth),
                             op.right, scope, depth);
            if (opop == FIELD_OP) {
                if (op.right.kind == "listop") {
                    scope.ctx.compiler.warn(new CompileException(node,
                        "Old-style .[] array/hash reference is deprecated" +
                        " (use [] instead)"));
                    return keyRefExpr(analyze(op.left, scope, depth),
                                      (ObjectRefOp) op.right, scope, depth);
                }
                return selectMember(op, getSelectorSym(op, op.right),
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
            if (opop == "^" && StaticRef.std(opfun, "$v")) {
                Code left = analyze(op.left, scope, depth);
                unify(left.type, STR_TYPE, op.left, scope, "#0");
                Code right = analyze(op.right, scope, depth);
                unify(right.type, STR_TYPE, op.right, scope, "#0");
                if (left instanceof StringConstant &&
                    right instanceof StringConstant) {
                    return new StringConstant(((StringConstant) left).str +
                                              ((StringConstant) right).str);
                }
                return new ConcatStrings(new Code[] { left, right });
            }
            if (opop == "|>" && StaticRef.std(opfun, "$I$g"))
                return apply(op, analyze(op.right, scope, depth),
                             op.left, scope, depth);
            return apply(op.right, apply(op, opfun, op.left, scope, depth),
                         op.right, scope, depth);
        }
        throw new CompileException(node,
            node.kind == "class" ? "Missing ; after class definition"
                : "I think that this " + node + " should not be here.");
    }

    static YType nodeToMembers(int type, TypeNode[] param, Map free,
                               Scope scope, int depth, int def) {
        Map members = new IdentityHashMap(param.length);
        Map members_ = new IdentityHashMap(param.length);
        YType[] tp = new YType[param.length + 1];
        tp[0] = new YType(depth);
        for (int i = 1; i <= param.length; ++i) {
            TypeNode arg = param[i - 1];
            tp[i] = withDoc(nodeToType(arg.param[0], free, scope, depth, def),
                            arg.doc);
            if (arg.var)
                tp[i] = fieldRef(depth, tp[i], FIELD_MUTABLE);
            String name = arg.name;
            Map m = members;
            if (name.charAt(0) == '.') {
                name = name.substring(1).intern();
                m = members_;
            }
            if (m.put(name, tp[i]) != null)
                throw new CompileException(arg, "Duplicate field name "
                                    + name + " in structure type");
        }
        YType result = new YType(type, tp);
        if (members.isEmpty())
            members = null;
        else if (members_.isEmpty())
            members_ = null;
        if (members != null && members_ != null)
            for (Iterator i = members_.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry) i.next();
                // anything in requiredMembers MUST be also in allowedMembers
                if (!members.containsKey(e.getKey()))
                    members.put(e.getKey(), e.getValue());
            }
        result.allowedMembers = members;
        result.requiredMembers = members_;
        if (def == 0 && (members == null || members_ == null))
            result.flags |= FL_FLEX_TYPEDEF; // for normal typedef
        return result;
    }

    static void expectsParam(TypeNode t, int count) {
        if (t.param == null ? count != 0 : t.param.length != count)
            throw new CompileException(t, "type " + t.name + " expects "
                                          + count + " parameters");
    }

    static final Object[][] PRIMITIVE_TYPE_MAPPING = {
        { "()",      UNIT_TYPE },
        { "boolean", BOOL_TYPE },
        { "char",    CHAR_TYPE },
        { "number",  NUM_TYPE  },
        { "string",  STR_TYPE  }
    };

    static YType nodeToType(TypeNode node, Map free, Scope scope,
                            int depth, int def) {
        String name = node.name;
        for (int i = PRIMITIVE_TYPE_MAPPING.length; --i >= 0;) {
            if (PRIMITIVE_TYPE_MAPPING[i][0] == name) {
                expectsParam(node, 0);
                return (YType) PRIMITIVE_TYPE_MAPPING[i][1];
            }
        }
        if (name == "") {
            if (node.param.length == 0)
                throw new CompileException(node,
                                           "Empty structure type not allowed");
            return nodeToMembers(STRUCT, node.param, free, scope, depth, def);
        }
        if (name == "|") {
            return nodeToMembers(VARIANT, node.param, free, scope, depth, def);
        }
        if (name == "->") {
            expectsParam(node, 2);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth, def),
                           nodeToType(node.param[1], free, scope, depth, def) };
            return new YType(FUN, tp);
        }
        if (name == "array") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth, def),
                           NUM_TYPE, LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "list") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth, def),
                           NO_TYPE, LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "list?") {
            expectsParam(node, 1);
            YType[] tp = { nodeToType(node.param[0], free, scope, depth, def),
                           new YType(depth), LIST_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "hash") {
            expectsParam(node, 2);
            YType[] tp = { nodeToType(node.param[1], free, scope, depth, def),
                           nodeToType(node.param[0], free, scope, depth, def),
                           MAP_TYPE };
            return new YType(MAP, tp);
        }
        if (name == "map") {
            expectsParam(node, 2);
            YType[] tp = { nodeToType(node.param[1], free, scope, depth, def),
                           nodeToType(node.param[0], free, scope, depth, def),
                           new YType(depth) };
            return new YType(MAP, tp);
        }
        if (Character.isUpperCase(name.charAt(0)))
            return nodeToMembers(VARIANT, new TypeNode[] { node },
                                 free, scope, depth, def);
        YType t;
        char c = name.charAt(0);
        if (c == '~') {
            expectsParam(node, 0);
            t = JavaType.typeOfName(
                name.substring(1).replace('.', '/').intern(), scope);
        } else if (c == '\'' || c == '^') {
            t = (YType) free.get(name);
            if (t == null) {
                t = new YType(depth);
                free.put(name, t = new YType(depth));
                if (c == '^')
                    t.flags = FL_ORDERED_REQUIRED;
                if (name.charAt(1) == '_')
                    t.flags |= FL_TAINTED_VAR;
            }
        } else {
            YType[] tp = new YType[node.param.length];
            for (int i = 0; i < tp.length; ++i)
                tp[i] = nodeToType(node.param[i], free, scope, depth, def);
            t = resolveTypeDef(scope, name, tp, depth, node, def);
        }
        return t;
    }

    static Code isOp(Node is, TypeNode type, Code value,
                     Scope scope, int depth) {
        YType t = nodeToType(type != null ? type : ((Bind) is).type,
                             new HashMap(), scope, depth + 1, -1).deref();
        if (type == null)
            normalizeFlexType(t, true);
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
                       !JavaType.isSafeCast(scope, is, t, vt, true)) {
                try {
                    t = opaqueCast(vt, t, scope);
                } catch (TypeException ex) {
                    String msg = "Impossible cast from #1 to #2";
                    if (t.type != JAVA && t.type != JAVA_ARRAY &&
                        vt.type != JAVA && vt.type != JAVA_ARRAY)
                        msg += "\n    #0";
                    throw new CompileException(is, scope, vt, t, msg, ex);
                }
                s = "is"; // don't convert
            }
            return new Cast(value, t, s == "as", is.line);
        }
        unify(value.type, t, is, scope, "#0 (when checking #1 is #2)");
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

    static JavaType.Method wrapSamArgs(JavaType.Method m, Code[] args, Scope scope) {
        //TODO: maybe some day this could actually be done on every assignment etc, not only for method calls
        for (int i = 0; i < args.length; ++i) {
            YType marg = m.arguments[i];
            if (args[i].type.type == YetiType.FUN &&
                marg.type == YetiType.JAVA &&
                marg.javaType.description != "Lyeti/lang/Fun;") {
                JavaType.Method sam = marg.javaType.getSAM();
                Node[] argnodes = new Node[sam.arguments.length * 2];
                for (int j = 0; j < sam.arguments.length; ++j) {
                    argnodes[2 * j] = new Sym(
                        sam.arguments[j].javaType.str().replace("~", "").replace(".", "/"));
                    argnodes[2 * j + 1] = new Sym(("arg" + j).intern());
                }
                Node call = new Seq(null, args[i]);
                for (int j = 0; j < sam.arguments.length; ++j) {
                    BinOp op = new BinOp("", 2, true);
                    op.left = call;
                    op.right = new Sym(("arg" + j).intern());
                    //TODO: op.parent = ???
                    call = op;
                }
                if (sam.arguments.length == 0) {
                    //FIXME: verify if this is ok
                    BinOp op = new BinOp("", 2, true);
                    op.left = call;
                    op.right = new XNode("()");
                    //TODO: op.parent = ???
                    call = op;
                }

                Node c = new XNode("class", new Node[] {
                    //TODO: use generated class name/ID, here and below in 'new'
                    new Sym("MCDBG$GENERATED$ID"),
                    new XNode("argument-list", new Node[0]),
                    new XNode("extends", new Node[] {
                        new Sym(marg.javaType.str().replace("~", "").replace(".", "/")),
                        new XNode("arguments") }),
                    new XNode("method", new Node[] {
                        new Sym(sam.returnType.javaType.str().replace("~", "").replace(".", "/")),
                        new Sym(sam.name),
                        new XNode("argument-list", argnodes),
                        call }) });
                Node cnew = new Seq(new Node[] {c, new XNode("new", new Node[] {
                        new Sym("MCDBG$GENERATED$ID") }) }, null);

                //FIXME: what to use for 'depth' argument below, instead of 99?
                args[i] = YetiAnalyzer.analyze(cnew, scope, 99);
            }
        }
        return m;
    }

    static Code objectRef(ObjectRefOp ref, Scope scope, int depth) {
        Code obj = null;
        YType t = null;
        if (ref.right instanceof Sym) {
            String className = ref.right.sym();
            t = resolveClass(className, scope, true);
            if (t == null && Character.isUpperCase(className.charAt(0)) &&
                (scope.ctx.compiler.globalFlags & Compiler.GF_NO_IMPORT) == 0)
                t = JavaType.typeOfClass(scope.ctx.packageName, className);
            // a terrible hack - tell super ref that it's used for call
            if (className == "super")
                ref.right.line = ref.right.line > 0 ? -ref.right.line : -1;
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
                        ? new FloatNum(n.doubleValue())
                        : (Num) new IntNum(n.longValue()));
            }
            return new ClassField(obj, f, ref.line);
        }
        Code[] args = mapArgs(0, ref.arguments, scope, depth);
        //TODO: add Yeti tests for wrapSamArgs
        JavaType.Method m = JavaType.resolveMethod(ref, t, args, obj == null)
                                    .check(ref, scope.ctx.packageName, 0);
        return new MethodCall(obj, wrapSamArgs(m, args, scope), args, ref.line);
    }

    static Code newArray(XNode op, Scope scope, int depth) {
        Code cnt = analyze(op.expr[1], scope, depth);
        unify(cnt.type, NUM_TYPE, op.expr[1], scope,
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
            expectUnit(tc.cleanup, t.expr[lastCatch], scope,
                      "finally block must have a unit type", null);
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
            unify(tc.block.type, cc.handler.type, c.expr[2], scope,
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
            JavaType.isSafeCast(scope, where, funarg, argCode.type, false)) {
            argCode = new Cast(argCode, funarg, true, where.line);
        }
        YType[] applyFun = { argCode.type, new YType(depth + 1) };
        try {
            unify(fun.type, new YType(FUN, applyFun));
        } catch (TypeException ex) {
            if (funt.type == UNIT)
                throw new CompileException(where,
                            "Missing ; (Cannot apply ())");
            if (funt.type != FUN && funt.type != VAR) {
                if (fun instanceof Apply ||
                        fun.getClass().getName().indexOf('$') > 0)
                    throw new CompileException(where,
                                "Too many arguments applied " +
                                "to a function, maybe a missing `;'?" +
                                "\n    (cannot apply " +
                                funt.toString(scope, null) +
                                " to an argument)");
                throw new CompileException(where, scope, fun.type, null,
                                           "Cannot use #1 as a function", ex);
            }
            YType argt = argCode.type.deref();
            String s = "Cannot apply #1 function";
            if (where != arg && where instanceof BinOp) {
                BinOp op = (BinOp) where;
                String name;
                Node f = (name = op.op) == "" ? op.left :
                         name == "|>" ? op.right : null;
                if (f == null || f instanceof Sym && (name = f.sym()) != null)
                    s += " (" + name + ')';
            }
            s += " to #2 argument\n    #0";
            if (funarg != null && funarg.type != FUN && argt.type == FUN) {
                if (argCode instanceof Apply)
                    s += "\n    Maybe you should apply the function given" +
                              " as an argument to more arguments.";
                else
                    s += "\n    Maybe you should apply the function given" +
                              " as an argument to some arguments?";
            }
            throw new CompileException(where, scope, fun.type, argCode.type, s, ex);
        }
        return fun.apply(argCode, applyFun[1], where.line);
    }

    static Code rsection(XNode section, Scope scope, int depth) {
        String sym = section.expr[0].sym();
        if (sym == FIELD_OP) {
            ++depth;
            LinkedList parts = new LinkedList();
            Node x = section.expr[1];
            for (BinOp op; x instanceof BinOp; x = op.left) {
                op = (BinOp) x;
                if (op.op != FIELD_OP)
                    throw new CompileException(op,
                        "Unexpected " + op.op + " in field selector");
                parts.addFirst(getSelectorSym(op, op.right).sym);
            }
            
            parts.addFirst(getSelectorSym(section, x).sym);
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
        unify(fun.type, new YType(FUN, afun), section, scope, fun.type,
              arg.type, "Cannot apply #2 as a 2nd argument to #1\n    #0");
        return fun.apply2nd(arg, new YType(FUN, r), section.line);
    }

    static Code variantConstructor(String name, int depth) {
        YType arg = new YType(++depth);
        YType tag = new YType(VARIANT, new YType[] { new YType(depth), arg });
        tag.requiredMembers = new IdentityHashMap();
        tag.requiredMembers.put(name, arg);
        YType[] fun = { arg, tag };
        return new VariantConstructor(new YType(FUN, fun), name);
    }

    static Sym getSelectorSym(Node op, Node sym) {
        if (!(sym instanceof Sym)) {
            if (sym == null)
                throw new CompileException(op, "What's that dot doing here?");
            if (sym.kind != "``")
                throw new CompileException(sym, "Illegal ." + sym);
            sym = ((XNode) sym).expr[0];
        }
        return (Sym) sym;
    }

    static YType selectMemberType(YType res, String field, int depth) {
        YType arg = new YType(STRUCT, new YType[] { new YType(depth), res });
        arg.requiredMembers = new IdentityHashMap();
        arg.requiredMembers.put(field, res);
        return arg;
    }

    static Code selectMember(Node op, Sym member, Code src,
                             Scope scope, int depth) {
        final YType res = new YType(++depth);
        final String field = member.sym;
        YType arg = selectMemberType(res, field, depth);
        try {
            unify(arg, src.type);
        } catch (TypeException ex) {
            int t = src.type.deref().type;
            if (t == JAVA)
                throw new CompileException(member, scope, src.type, null,
                    "Cannot use class #1 as a structure with ." +
                    field + " field\n    " +
                    "(use # instead of . to reference object fields/methods)",
                    ex);
            if (src instanceof VariantConstructor)
                throw new CompileException(member,
                    "Cannot use variant constructor " +
                    ((VariantConstructor) src).name +
                    " as a structure with ." + field + " field\n    " +
                    "(use # instead of . to reference class fields/methods)");
            if (t != STRUCT && t != VAR)
                throw new CompileException(member, "Cannot use " +
                                src.type.toString(scope, null) +
                                " as a structure with ." + field + " field");
            throw new CompileException(member, scope, src.type, null,
                        "#1 does not have ." + field + " field", ex);
        }
        limitDepth(res, arg.deref().param[0].deref().depth, 0);
        boolean poly = src.polymorph && src.type.allowedMembers != null &&
            ((YType) src.type.allowedMembers.get(field)).field == 0;
        return new SelectMember(res, src, field, op.line, poly) {
            boolean mayAssign() {
                YType t = st.type.deref();
                YType given;
                if (t.allowedMembers != null &&
                    (given = (YType) t.allowedMembers.get(field)) != null &&
                    (given.field != FIELD_MUTABLE)) {
                    return false;
                }
                YType self = (YType) t.requiredMembers.get(field);
                if (self.field != FIELD_MUTABLE)
                    // XXX couldn't we get along with res.field = FIELD_MUTABLE?
                    t.requiredMembers.put(field, mutableFieldRef(res));
                return true;
            }
        };
    }

    static Code keyRefExpr(Code val, ObjectRefOp keyList, Scope scope, int depth) {
        Code key = analyze(keyList.arguments[0], scope, depth);
        YType t = val.type.deref();
        if (t.type == JAVA_ARRAY) {
            unify(key.type, NUM_TYPE, keyList.arguments[0], scope,
                  "Array index must be a number (but here was #1)");
            return new JavaArrayRef(t.param[0], val, key, keyList.arguments[0].line);
        }
        YType[] param = { new YType(depth), key.type, new YType(depth) };
        unify(val.type, new YType(MAP, param), keyList, scope,
              val.type, key.type, "#1 cannot be referenced by #2 key");
        return new KeyRefExpr(param[0], val, key, keyList.line);
    }

    static Code assignOp(BinOp op, Scope scope, int depth) {
        Code left;
        try {
            left = analyze(op.left, scope, depth);
        } catch (CompileException ex) {
            if (ex.cause == null || ex.cause.kind != "var")
                throw ex;
            throw new CompileException(op, "Assignment operator := " +
                    "not expected in variable binding (use = instead)");
        }
        Code right = analyze(op.right, scope, depth);
        unify(left.type, right.type, op, scope, "#0");
        Code assign = left.assign(right);
        if (assign == null)
            throw new CompileException(op,
                "Non-mutable expression on the left of the assign operator :=");
        assign.type = UNIT_TYPE;
        return assign;
    }

    static Code concatStr(XNode concat, Scope scope, int depth) {
        Code[] parts = new Code[concat.expr.length];
        for (int i = 0; i < parts.length; ++i)
            parts[i] = analyze(concat.expr[i], scope, depth);
        return new ConcatStrings(parts);
    }

    static YType mergeIfType(Node where, Scope scope, YType result, YType val) {
        try {
            return mergeOrUnify(result, val);
        } catch (TypeException ex) {
            throw new CompileException(where, scope, val, result,
                "This if branch has a #1 type, while another was a #2", ex);
        }
    }

    static Code cond(XNode condition, Scope scope, int depth) {
        List conds = new ArrayList();
        YType result = null;
        boolean poly = true;
        for (;;) {
            Code cond = analyze(condition.expr[0], scope, depth);
            unify(cond.type, BOOL_TYPE, condition.expr[0], scope,
                  "if condition must have a boolean type (but here was #1)");
            Code val = analyze(condition.expr[1], scope, depth);
            conds.add(new Code[] { val, cond });
            poly &= val.polymorph;
            if (result == null)
                result = val.type;
            else
                result =
                    mergeIfType(condition.expr[1], scope, result, val.type);
            if (condition.expr[2].kind != "if")
                break;
            condition = (XNode) condition.expr[2];
        }
        Code val =
            condition.expr[2].kind != "fi" ?
                analyze(condition.expr[2], scope, depth) :
            result.deref() == STR_TYPE ?
                BuiltIn.undef_str(null, condition.line) :
                new UnitConstant(null);
        result = mergeIfType(condition.expr[2], scope, result, val.type);
        conds.add(new Code[] { val });
        Code[][] expr = (Code[][]) conds.toArray(new Code[conds.size()][]);
        return new ConditionalExpr(result, expr, poly && val.polymorph);
    }

    static Code loop(BinOp node, Scope scope, int depth) {
        LoopExpr loop = new LoopExpr();
        scope = new Scope(scope, null, null);
        scope.closure = loop;
        Node condNode = node.left != null ? node.left : node.right;
        loop.cond = analyze(condNode, scope, depth);
        unify(loop.cond.type, BOOL_TYPE, condNode, scope,
              "Loop condition must have a boolean type (but here was #1)");
        if (node.left == null) {
            loop.body = new UnitConstant(null);
            return loop;
        }
        loop.body = analyze(node.right, scope, depth);
        expectUnit(loop.body, node.right, scope,
                   "Loop body must have a unit type", null);
        return loop;
    }

    static Code withStruct(BinOp with, Scope scope, int depth) {
        Code src = analyze(with.left, scope, depth);
        Code override = analyze(with.right, scope, depth);
        YType ot = override.type.deref();
        Map otf = ot.allowedMembers;
        if (otf == null || ot.type != STRUCT)
            throw new CompileException(with.right, "Right-hand side of with " +
                            "must be a structure with known member set");
        YType result, st = src.type.deref();
        if (st.type == STRUCT && st.allowedMembers != null) {
            unify(st.param[0], ot.param[0], with, scope,
                  "Internal error (withStruct depth unify)");
            Map param = new IdentityHashMap(st.allowedMembers);
            param.putAll(otf);
            // with ensures override, because type can change and
            // members can be missing in the source structure.
            // Another mechanism is needed "default arguments".
            if (ot.requiredMembers == null) {
                ot.requiredMembers = otf;
            } else {
                IdentityHashMap tmp = new IdentityHashMap(otf);
                tmp.keySet().removeAll(ot.requiredMembers.keySet());
                ot.requiredMembers.putAll(otf);
            }
            // lock used members.
            IdentityHashMap tmp = new IdentityHashMap(st.allowedMembers);
            tmp.keySet().removeAll(otf.keySet());
            if (st.requiredMembers != null)
                tmp.putAll(st.requiredMembers);
            st.requiredMembers = tmp;
            result = new YType(STRUCT, null);
            result.allowedMembers = param;
            structParam(result, param, st.param[0].deref());
        } else {
            result = new YType(STRUCT, null);
            result.requiredMembers = new IdentityHashMap(otf);
            result.param = ot.param;
            unify(src.type, result, with.right, scope,
                  "Cannot extend #1 with #2");
        }
        return new WithStruct(result, src, override,
                        (String[]) otf.keySet().toArray(new String[otf.size()]));
    }

    static Function singleBind(Bind bind, Scope scope, int depth) {
        if (bind.expr.kind != "lambda")
            throw new CompileException(bind, "Closed binding must be a" +
                " function binding.\n    Maybe you meant := or ==" +
                " instead of =, or have missed ; somewhere.");
        // recursive binding
        Function lambda = new Function(new YType(depth + 1));
        BindExpr binder = new BindExpr(lambda, bind.var);
        lambda.selfBind = binder;
        if (!bind.noRec)
            scope = new Scope(scope, bind.name, binder);
        lambdaBind(lambda, bind, scope, depth);
        return lambda;
    }

    static Scope explodeStruct(Node where, LoadModule m, Scope scope,
                               String prefix, int depth, boolean noRoot) {
        if (prefix == null) {
            m.checkUsed = true;
            if (m.type.type == STRUCT) {
                Iterator j = m.type.allowedMembers.entrySet().iterator();
            members:
                while (j.hasNext()) {
                    Map.Entry e = (Map.Entry) j.next();
                    String name = ((String) e.getKey()).intern();
                    if (noRoot)
                        for (Scope i = ROOT_SCOPE; i != null; i = i.outer)
                            if (i.name == name)
                                continue members;
                    YType t = (YType) e.getValue();
                    scope = bind(name, t, m.bindField(name, t),
                                 RESTRICT_POLY, depth, scope);
                }
            } else if (m.type.type != UNIT) {
                throw new CompileException(where,
                    "Expected module with struct or unit type here (" +
                    m.moduleName.replace('/', '.') + " has type " +
                    m.type.toString(scope, null) +
                    ", but only structs can be exploded)");
            }
        }
        Iterator j = m.moduleType.typeDefs.entrySet().iterator();
        while (j.hasNext()) {
            Map.Entry e = (Map.Entry) j.next();
            YType[] typeDef = (YType[]) e.getValue();
            String name = (String) e.getKey();
            if (prefix != null)
                name = prefix.concat(name).intern();
            ArrayList vars = new ArrayList();
            getAllTypeVar(vars, null, typeDef[typeDef.length - 1], false);
            scope = new TypeScope(scope, name, typeDef, m);
            scope.free = (YType[]) vars.toArray(new YType[vars.size()]);
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
        limitDepth(binder.st.type, bind.var ? depth : depth + 1, 0);
        switch (binder.st.type.deref().type) {
        case VAR: case FUN: case MAP: case STRUCT: case VARIANT:
            scope = bind(bind.name, binder.st.type, binder,
                         bind.var ? RESTRICT_ALL : binder.st.polymorph
                            ? RESTRICT_POLY : 0, depth, scope);
            break;
        default:
            scope = new Scope(scope, bind.name, binder);
        }
        if (bind.var)
            registerVar(binder, scope.outer);
        if (evalSeq)
            binder.evalId = YetiEval.registerBind(bind.name,
                        binder.st.type, bind.var, binder.st.polymorph);
        return scope;
    }

    static void addSeq(SeqExpr[] last, SeqExpr expr) {
        if (last[0] == null)
            last[1] = expr;
        else
            last[0].result = expr;
        last[0] = expr;
    }

    static Scope bindStruct(Binder binder, XNode st, boolean isEval,
                            Scope scope, int depth, SeqExpr[] last) {
        Node[] fields = st.expr;
        if (fields.length == 0)
            throw new CompileException(st, NONSENSE_STRUCT);
        for (int j = 0; j < fields.length; ++j) {
            Bind bind = new Bind();
            if (!(fields[j] instanceof Bind))
                throw new CompileException(fields[j],
                    "Expected field pattern, not a " + fields[j]);
            Bind field = (Bind) fields[j];
            if (field.var || field.property)
                throw new CompileException(field, "Structure " +
                    "field pattern may not have modifiers");
            bind.expr = new Sym(field.name);
            bind.expr.pos(field.line, field.col);
            Node nameNode = field.expr;
            if (!(nameNode instanceof Sym) ||
                    (bind.name = nameNode.sym()) == "_")
                throw new CompileException(nameNode,
                    "Binding name expected, not a " + nameNode);
            Code code = selectMember(fields[j], (Sym) bind.expr,
                          binder.getRef(fields[j].line), scope, depth + 1);
            if (field.type != null)
                isOp(field, null, code, scope, depth);
            BindExpr bindExpr = new BindExpr(code, false);
            scope = genericBind(bind, bindExpr, isEval, scope, depth);
            addSeq(last, bindExpr);
        }
        return scope;
    }

    static Scope bindTypeDef(TypeDef typeDef, Object seqKind, Scope scope) {
        YType self = new YType(0);
        Scope defScope = scope;
        if (typeDef.kind != TypeDef.UNSHARE)
            defScope = new TypeScope(scope, typeDef.name,
                                     new YType[] { self }, null);
        YType[] def = new YType[typeDef.param.length + 1];
        // binding typedef arguments
        for (int i = typeDef.param.length; --i >= 0;) {
            YType arg = new YType(0);
            arg.doc = defScope.name;
            def[i] = arg;
            defScope = new TypeScope(defScope, typeDef.param[i],
                                     new YType[] { arg }, null);
        }
        boolean opaque = typeDef.kind == TypeDef.OPAQUE;
        YType type = nodeToType(typeDef.type, new HashMap(),
                                defScope, 1, typeDef.kind).deref();
        // XXX the order of unify arguments matters!
        unify(self, type, typeDef, scope, type, self,
              "Type #~ (type self-binding)\n    #0");

        scope = new TypeScope(scope, typeDef.name, def, null);
        List structs = opaque ? new ArrayList() : null;
        if (typeDef.kind != TypeDef.SHARED) {
            ArrayList vars = new ArrayList();
            if (typeDef.kind == TypeDef.UNSHARE) {
                getAllTypeVar(vars, null, type, false);
                type = copyType(type, createFreeVars((YType[]) vars.toArray(
                                        NO_PARAM), 1), new IdentityHashMap());
                vars.clear();
                stripFlexTypes(type, false);
            }
            // nothing mutable in typedef
            getAllTypeVar(vars, structs, type, opaque);
            scope.free = (YType[]) vars.toArray(new YType[vars.size()]);
        } else {
            scope.free = null;
        }

        boolean override = false;
        if (opaque) {
            self = type;
            for (int i = scope.free.length; --i >= 0; ) {
                int j = def.length - 1;
                while (--j >= 0 && scope.free[i] != def[j]);
                if (j < 0) {
                    scope.free[i].flags |= FL_ERROR_IS_HERE;
                    throw new CompileException(typeDef, scope, self, null,
                        "typedef opaque " + typeDef.name +
                        " contains free type variable in #1", null);
                }
            }
            synchronized (scope.ctx.opaqueTypes) {
                type = new YType(scope.ctx.opaqueTypes.size() + OPAQUE_TYPES,
                                 new YType[def.length - 1]);
                System.arraycopy(def, 0, type.param, 0, type.param.length);
                String idstr = scope.ctx.className + ':' + typeDef.name;
                if (!(seqKind instanceof TopLevel))
                    idstr = idstr + '#' + (type.type - OPAQUE_TYPES);
                type.allowedMembers = Collections.singletonMap("", self);
                type.requiredMembers = Collections.singletonMap(idstr, NO_TYPE);
                override = scope.ctx.opaqueTypes.put(idstr, type) != null;
            }
            if (structs.size() == 0) {
                scope.free = type.param;
            } else {
                scope.free = new YType[structs.size() + type.param.length];
                System.arraycopy(def, 0, structs.toArray(scope.free),
                                 structs.size(), type.param.length);
            }
        }

        def[def.length - 1] = type;
        if (typeDef.name.charAt(0) != '_' && typeDef.kind != TypeDef.SHARED &&
            seqKind instanceof TopLevel) {
            if (((TopLevel) seqKind).typeDefs.put(typeDef.name, def) != null &&
                    override)
                throw new CompileException(typeDef, "Overriding typedef opaque "
                          + typeDef.name + "<> at module scope is not allowed");
            ((TopLevel) seqKind).typeScope = scope;
        }
        return scope;
    }

    static void expectUnit(Code value, Node where, Scope scope,
                           String what, String hint) {
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
            } else if (hint != null) {
                s += hint;
            }
            throw new CompileException(where, scope, value.type, null, s, ex);
        }
    }

    static Code analSeq(Seq seq, Scope scope, int depth) {
        if (seq.seqKind instanceof Code)
            return (Code) seq.seqKind;
        Node[] nodes = seq.st;
        BindExpr[] bindings = new BindExpr[nodes.length];
        SeqExpr[] last = { null, null };
        for (int i = 0; i < nodes.length - 1; ++i) {
            if (nodes[i] instanceof Bind) {
                Bind bind = (Bind) nodes[i];
                BindExpr binder;
                XNode lambda;
                if ((lambda = asLambda(bind.expr)) != null) {
                    bind.expr = lambda;
                    binder = (BindExpr) singleBind(bind, scope, depth).selfBind;
                } else {
                    Code code = analyze(bind.expr, scope, depth);
                    binder = new BindExpr(code, bind.var);
                    if (code instanceof LoadModule)
                        scope = explodeStruct(bind, (LoadModule) code, scope,
                                    bind.name.concat("."), depth - 1, false);
                    if (bind.type != null)
                        isOp(bind, null, binder.st, scope, depth);
                }
                if (bind.doc != null)
                    binder.st.type = withDoc(binder.st.type, bind.doc);
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
                scope = explodeStruct(nodes[i], m, scope, null,
                                      depth - 1, false);
                addSeq(last, new SeqExpr(m));
                if (seq.seqKind instanceof TopLevel)
                    ((TopLevel) seq.seqKind).typeScope = scope;
            } else if (nodes[i].kind == "import") {
                if ((scope.ctx.compiler.globalFlags & Compiler.GF_NO_IMPORT)
                        != 0)
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
                expectUnit(code, nodes[i], scope, "Unit type expected here",
                    seq.seqKind != "{}" ? null :
                    "\n    (use , instead of ; to separate structure fields)");
                addSeq(last, new SeqExpr(code));
            }
        }
        Node expr = nodes[nodes.length - 1];
        Code code = expr.kind == "class" && seq.seqKind instanceof TopLevel &&
                    ((TopLevel) seq.seqKind).isModule
            ? MethodDesc.defineClass((XNode) expr, true, new Scope[] { scope },
                                     depth)
            : analyze(expr, scope, depth);
        for (int i = bindings.length; --i >= 0;)
            if (bindings[i] != null && bindings[i].refs == null &&
                    seq.seqKind != Seq.EVAL &&
                !(bindings[i].st instanceof LoadModule &&
                  ((LoadModule) bindings[i].st).typedefUsed))
                unusedBinding(scope, (Bind) nodes[i]);
        return wrapSeq(code, last);
    }

    static Code wrapSeq(Code code, SeqExpr[] seq) {
        if (seq == null || seq[0] == null)
            return code;
        for (SeqExpr cur = seq[1]; cur != null; cur = (SeqExpr) cur.result)
            cur.type = code.type;
        seq[0].result = code;
        seq[1].polymorph = code.polymorph;
        return seq[1];
    }

    static Code lambdaBind(Function to, Bind bind, Scope scope, int depth) {
        if (bind.type != null)
            isOp(bind, null, to, scope, depth);
        return lambda(to, (XNode) bind.expr, scope, depth);
    } 

    static Code lambda(Function to, XNode lambda, Scope scope, int depth) {
        ++depth;
        YType expected = to.type == null ? null : to.type.deref();
        to.polymorph = true;
        Scope bodyScope = null;
        SeqExpr[] seq = null;
        Node arg = lambda.expr[0];

        if (arg.kind == "()") {
            to.arg.type = UNIT_TYPE;
        } else {
            to.arg.type = expected != null && expected.type == FUN
                ? expected.param[0] : new YType(depth);
            if (arg instanceof Sym) {
                String argName = arg.sym();
                if (argName != "_")
                    bodyScope = new Scope(scope, argName, to);
            } else if (arg.kind == "struct") {
                seq = new SeqExpr[] { null, null };
                bodyScope =
                    bindStruct(to, (XNode) arg, false, scope, depth, seq);
            } else {
                throw new CompileException(arg, "Bad argument: " + arg);
            }
        }
        if (bodyScope == null)
            bodyScope = new Scope(scope, null, to);
        Scope marker = bodyScope;
        while (marker.outer != scope)
            marker = marker.outer;
        marker.closure = to;
        XNode bodyLambda = asLambda(lambda.expr[1]);
        if (bodyLambda != null) {
            Function f = new Function(expected != null && expected.type == FUN
                                      ? expected.param[1] : null);
            // make f to know about its outer scope before processing it
            to.setBody(seq == null || seq[0] == null ? (Code) f : seq[1]);
            lambda(f, bodyLambda, bodyScope, depth);
            wrapSeq(f, seq);
        } else {
            Code body = analyze(lambda.expr[1], bodyScope, depth);
            YType res; // try casting to expected type
            if (expected != null && expected.type == FUN &&
                JavaType.isSafeCast(scope, lambda,
                                    res = expected.param[1].deref(),
                                    body.type, false)) {
                body = new Cast(body, res, true, lambda.expr[1].line);
            }
            to.setBody(wrapSeq(body, seq));
        }
        YType fun = new YType(FUN, new YType[] { to.arg.type, to.body.type });
        if (expected != null)
            unify(fun, expected, lambda, scope,
                  "Function type #~ (self-binding)\n    #0");

        restrictArg(to.arg.type, depth, false);

        to.type = fun;
        to.bindName = lambda.expr.length > 2 ? lambda.expr[2].sym() : null;
        to.body.markTail();
        return to;
    }

    static Bind getField(Node node) {
        if (!(node instanceof Bind))
            throw new CompileException(node,
                    "Unexpected beast in the structure (" + node +
                    "), please give me some field binding.");
        return (Bind) node;
    }

    static void duplicateField(Bind field) {
        throw new CompileException(field,
                "Duplicate field " + field.name + " in the structure");
    }

    static void propertyField(Bind field, Code code, StructField sf, Map fields,
                    Node where, Scope scope, Scope propertyScope, int depth) {
        YType f;
        if (code == null) {
            code = analyze(field.expr, propertyScope, depth);
        } else if (!field.var) {
            XNode xf = (XNode) field.expr;
            // disable merging with the get lambda
            if (xf.expr[1].kind == "lambda")
                xf.expr[1] = new Seq(new Node[] { new XNode("()"),
                              xf.expr[1] }, null).pos(xf.line, xf.col);
        }
        // get is () -> t, set is t -> ()
        YType t = (YType) fields.get(field.name);
        if (t == null) {
            t = new YType(depth);
            t.field = FIELD_NON_POLYMORPHIC;
            fields.put(field.name, t);
        }
        if (field.type != null) {
            f = nodeToType(field.type, new HashMap(), scope, depth, -1);
            normalizeFlexType(f, true);
            unify(t, f, field, scope, "#0 (when checking #1 is #2)");
        }
        if (field.var)
            t.field = FIELD_MUTABLE;
        if (field.doc != null) {
            if (t.doc == null)
                t = withDoc(t, field.doc);
            else
                t.doc = field.doc + '\n' + t.doc;
        }
        f = new YType(FUN, field.var ? new YType[] { t, UNIT_TYPE }
                                     : new YType[] { UNIT_TYPE, t });
        try {
            unify(code.type, f);
        } catch (TypeException ex) {
            throw new CompileException(where, scope, code.type, f,
                        (field.var ? "Setter " : "Getter ")
                        + field.name + " type #~", ex);
        }
        if (field.var) {
            sf.setter = code;
            sf.mutable = true;
        } else {
            sf.value = code;
        }
    }

    static Code structType(XNode st, Scope scope, int depth) {
        Node[] nodes = st.expr;
        if (nodes.length == 0)
            throw new CompileException(st, NONSENSE_STRUCT);
        Scope local = scope, propertyScope = null;
        Map fields = new IdentityHashMap(nodes.length),
            codeMap = new IdentityHashMap(nodes.length);
        Function[] funs = new Function[nodes.length];
        StructConstructor result = new StructConstructor(nodes.length);
        result.polymorph = true;

        // Functions see struct members in their scope
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = getField(nodes[i]);
            Function lambda = !field.noRec && field.expr.kind == "lambda"
                        ? funs[i] = new Function(new YType(depth + 1)) : null;
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
                if (code == null && propertyScope == null) {
                    propertyScope = new Scope(scope, null, null);
                    propertyScope.closure = result;
                }
                propertyField(field, code, sf, fields, nodes[i],
                              scope, propertyScope, depth);
            } else {
                if (sf != null)
                    duplicateField(field);
                if (code == null) {
                    code = analyze(field.expr, scope, depth);
                    if (field.type != null)
                        isOp(field, null, code, scope, depth);
                }
                sf = new StructField();
                sf.name = field.name;
                sf.value = code;
                sf.mutable = field.var;
                codeMap.put(field.name, sf);
                result.add(sf);
                boolean poly = code.polymorph || lambda != null;
                YType t = code.type;
                if (!poly && !field.var) {
                    switch (t.deref().type) {
                    case VAR: case FUN: case MAP: case STRUCT: case VARIANT:
                        Map all = new IdentityHashMap();
                        YType[] vars = getFreeVar(all, t, 0, depth);
                        if (vars.length != 0 &&
                                !(poly = vars.length == all.size())) {
                            removeStructs(t, all.keySet());
                            poly = vars.length >= all.size();
                        }
                    }
                }
                if (field.var)
                    t = fieldRef(depth, t, FIELD_MUTABLE);
                else if (!poly)
                    t = fieldRef(depth, t, FIELD_NON_POLYMORPHIC);
                fields.put(field.name, withDoc(t, field.doc));
                if (!field.noRec) {
                    Binder bind = result.bind(sf);
                    if (lambda != null)
                        lambda.selfBind = bind;
                    local = new Scope(local, field.name, bind);
                }
            }
        }
        // property accessors must be proxied so the struct could inline them
        if (result.properties != null) {
            propertyScope = new Scope(local, null, null);
            propertyScope.closure = result;
        }
        for (int i = 0; i < nodes.length; ++i) {
            Bind field = (Bind) nodes[i];
            if (funs[i] != null)
                lambdaBind(funs[i], field, ((Bind) nodes[i]).property
                                ? propertyScope : local, depth);
        }
        result.type = new YType(STRUCT, null);
        for (StructField i = result.properties; i != null; i = i.nextProperty)
            if (i.value == null)
                throw new CompileException(st,
                    "Property " + i.name + " has no getter");
        structParam(result.type, fields, new YType(depth + 1));
        result.type.allowedMembers = fields;
        result.close();
        return result;
    }

    static Code list(Node list, Node[] items, Scope scope, int depth) {
        boolean emptyMap = items == null;
        if (emptyMap)
            items = new Node[0];
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
                if (kind == LIST_TYPE)
                    throw new CompileException(keyNode,
                        "Unexpected : in list" + (i != 1 ? "" :
                        " (or the key is missing on the first item?)"));
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
                    unify(keyType, key.type, items[i], scope,
                        "This map element has #2 key, but others have had #1");
                }
                keyItems[n] = key;
                codeItems[n] = analyze(items[i], scope, depth);
                keyNode = null;
            } else {
                if (kind == MAP_TYPE)
                    throw new CompileException(items[i],
                                "Map item is missing a key");
                kind = LIST_TYPE;
                if (items[i] instanceof BinOp &&
                    (bin = (BinOp) items[i]).op == "..") {
                    Code from = analyze(bin.left, scope, depth);
                    Code to = analyze(bin.right, scope, depth);
                    unify(from.type, NUM_TYPE, bin.left, scope,
                          ".. range expects limit to be number, not a #1");
                    unify(to.type, NUM_TYPE, bin.right, scope,
                          ".. range expects limit to be number, not a #1");
                    codeItems[n] = new Range(from, to);
                } else {
                    codeItems[n] = analyze(items[i], scope, depth);
                }
            }
            if (type == null)
                type = codeItems[n].type;
            else
                try {
                    type = mergeOrUnify(type, codeItems[n].type);
                } catch (TypeException ex) {
                    throw new CompileException(items[i], scope,
                        codeItems[n].type, type,
                        (kind == LIST_TYPE ? "This list element is "
                                           : "This map element is ") +
                         "#1, but others have been #2", ex);
                }
        }
        if (type == null)
            type = new YType(depth);
        if (kind == null)
            kind = LIST_TYPE;
        if (emptyMap) {
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

    void checkModuleFree(Node n, RootClosure root) {
        YType t = root.type.deref();
        YType[] free = getFreeVar(new IdentityHashMap(), t,
                                  root.body.polymorph ? RESTRICT_POLY : 0, 0);
        Map fields = null;
        while (n instanceof Seq) {
            Seq seq = (Seq) n;
            n = seq.st[seq.st.length - 1];
        }
        Node[] nodes = n.kind == "struct" ? ((XNode) n).expr : null;
        if (nodes == null || t.type != STRUCT)
            fields = Collections.singletonMap(null, t);
        else if (t.requiredMembers != null)
            fields = t.requiredMembers;
        else
            fields = t.allowedMembers;
        Node errorLocation = null;
        boolean bad = false;
        for (Iterator f = fields.entrySet().iterator(); f.hasNext(); ) {
            Map.Entry e = (Map.Entry) f.next();
            List all = new ArrayList(), structs = new ArrayList();
            t = (YType) e.getValue();
            getAllTypeVar(all, structs, t, false);
            for (int i = 0; i < free.length; ++i)
                all.remove(free[i]);
            if (all.isEmpty())
                continue; // no non-free typevars in this field
            // clean up structs, to fix MAP marker errors
            removeStructs(t, all);
            if (all.isEmpty())
                continue;
            bad = true;
            for (int i = all.size(); --i >= 0; ) // mark errors
                ((YType) all.get(i)).deref().flags |= FL_ERROR_IS_HERE;
            if (e.getKey() == null)
                continue;
            for (int i = 0; i < nodes.length; ++i)
                if (nodes[i] instanceof Bind &&
                        e.getKey().equals(((Bind) nodes[i]).name)) {
                    throw new CompileException(nodes[i],
                        "Module type is not fully defined in field " +
                        e.getKey() + ":\n    " + t +
                        "\n    (offending type variables are marked with *)");
                }
        }
        if (bad)
            throw new CompileException(n, root.body.type +
                "\nModule type is not fully defined " +
                "(offending type variables are marked with *)");
    }

    int flags; // compilation flags
    String canonicalFile; // canonical source name, used by ctx.deriveName
    String sourceName; // source file path as given to compile
    String sourceFile; // sourceName without directory path
    String sourceDir; // sourcePath entry used to find it
    long targetTime;  // target's lastModified
    File targetFile;  // set by deriveName
    Compiler compiler;
    String[] preload;
    long depsModifiedTime;
    long sourceTime;
    String topDoc; // used to return module doc, when no compilation is done
    ModuleType resolvedType; // used by Compiler.readSource()

    RootClosure toCode(char[] src) {
        TopLevel topLevel = new TopLevel();
        Object oldSrc = currentSrc.get();
        currentSrc.set(src);
        try {
            Parser parser =
                new Parser(sourceName, src, flags | compiler.globalFlags);
            Node n;
            try {
                n = parser.parse(topLevel);
            } catch (CompileException ex) {
                if (ex.line == 0)
                    ex.line = parser.currentLine();
                throw ex;
            } finally {
                if (parser.sourceName != null &&
                    !parser.sourceName.equals(sourceName)) {
                    sourceName = parser.sourceName;
                    sourceFile = null;
                }
            }
            if ((flags & Compiler.CF_PRINT_PARSE_TREE) != 0)
                System.err.println(n.str());
            if ((flags & (Compiler.CF_EXPECT_MODULE |
                          Compiler.CF_EXPECT_PROGRAM)) != 0 &&
                (flags & Compiler.CF_EXPECT_MODULE) != 0 != parser.isModule)
                throw new CompileException(parser.moduleNameLine, 0,
                            (flags & Compiler.CF_EXPECT_MODULE) != 0
                            ? "Expected module" : "Expected program");
            compiler.deriveName(parser, this);
            final String className = parser.moduleName;
            compiler.addClass(className, null, parser.moduleNameLine);
            while (parser.loads != null) {
                XNode l = parser.loads;
                if ((compiler.globalFlags & Compiler.GF_NO_IMPORT) != 0)
                    throw new CompileException(l, "load is disabled");
                parser.loads = (XNode) l.expr[1];
                ModuleType t = 
                   YetiTypeVisitor.getType(compiler, l, l.expr[0].sym(), false);
                l.expr[1] = t;
                if (depsModifiedTime < t.lastModified)
                    depsModifiedTime = t.lastModified;
            }
            RootClosure root = new RootClosure();
            Scope scope =
                new Scope((compiler.globalFlags & Compiler.GF_NO_IMPORT) == 0
                                ? ROOT_SCOPE_SYS : ROOT_SCOPE, null, null);
            LoadModule[] preloadModules = new LoadModule[preload.length];
            for (int i = 0; i < preload.length; ++i) {
                if (!preload[i].equals(className)) {
                    ModuleType t = YetiTypeVisitor.getType(compiler,
                                        null, preload[i], false);
                    preloadModules[i] = new LoadModule(preload[i], t, -1);
                    scope = explodeStruct(null, preloadModules[i], scope,
                                null, 0, "yeti/lang/std".equals(preload[i]));
                    if (depsModifiedTime < t.lastModified)
                        depsModifiedTime = t.lastModified;
                    else if (t.lastModified == 0)
                        depsModifiedTime = Long.MAX_VALUE;
                }
            }
            //System.err.println(sourceName + ": target:" + targetTime +
            //    " source:" + sourceTime + " depend:" + depsModifiedTime);
            if (targetTime > sourceTime && sourceTime != 0 &&
                    targetTime >= depsModifiedTime && targetFile != null) {
                topDoc = parser.topDoc;
                if (!parser.isModule) {
                    resolvedType = new ModuleType(UNIT_TYPE, null, true, -1);
                    resolvedType.name = className;
                }
                return null;
            }
            if (parser.isModule)
                scope = bindImport("module", className, scope);
            if ((flags & Compiler.CF_EVAL_RESOLVE) != 0) {
                List binds = YetiEval.get().bindings;
                for (int i = 0, cnt = binds.size(); i < cnt; ++i) {
                    YetiEval.Binding bind = (YetiEval.Binding) binds.get(i);
                    if (bind.isImport) {
                        scope = new Scope(scope, bind.name, null);
                        scope.importClass = new ClassBinding(bind.type);
                        continue;
                    }
                    scope = bind(bind.name, bind.type, new EvalBind(bind),
                                 bind.mutable ? RESTRICT_ALL : bind.polymorph
                                    ? RESTRICT_POLY : 0, 0, scope);
                }
            }
            topLevel.isModule = parser.isModule;
            topLevel.typeScope = scope;
            root.preload = preloadModules;
            root.line = parser.moduleNameLine;
            scope.closure = root;
            scope.ctx = new ScopeCtx(className, compiler);
            root.body = analyze(n, scope, 0);
            root.type = root.body.type.deref();
            ModuleType mt = new ModuleType(root.type, topLevel.typeDefs, true,
                                           parser.isModule ? 1 : -1);
            for (Iterator i = mt.typeDefs.values().iterator(); i.hasNext(); ) {
                YType[] t = (YType[]) i.next(); // hide implementation type
                if (t[t.length - 1].type >= OPAQUE_TYPES)
                    t[t.length - 1].allowedMembers = null;
            }
            root.moduleType = mt;
            mt.topDoc = parser.topDoc;
            mt.deprecated = parser.deprecated;
            mt.name = className;
            mt.typeScope = topLevel.typeScope;
            mt.lastModified = sourceTime;
            mt.hasSource = true;
            if (mt.lastModified < depsModifiedTime)
                mt.lastModified = depsModifiedTime;
            root.isModule = parser.isModule;
            if (parser.isModule)
                checkModuleFree(n, root);
            else if ((flags & Compiler.CF_EVAL) == 0)
                expectUnit(root, n, topLevel.typeScope,
                           "Program body must have a unit type", null);
            return root;
        } finally {
            currentSrc.set(oldSrc);
        }
    }
}
