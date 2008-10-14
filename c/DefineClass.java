// ex: set sts=4 sw=4 expandtab:

/**
 * Class definition analyzer.
 * Copyright (c) 2008 Madis Janson
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

final class MethodDesc extends YetiType {
    Binder[] arguments;
    String[] names;
    JavaClass.Meth method;
    Node[] m;
    boolean isStatic;

    MethodDesc(JavaClass.Meth method, Node argList, Scope scope) {
        this.method = method;
        Node[] args = ((XNode) argList).expr;
        arguments = new Binder[args.length / 2];
        names = new String[arguments.length];
        for (int i = 0, j = 0; i < arguments.length; ++i, j += 2) {
            String name = args[j + 1].sym();
            for (int k = 0; k < i; ++k)
                if (name == names[k]) {
                    throw new CompileException(args[j + 1],
                                "Duplicate argument name (" + name + ")");
                }
            names[i] = name;
            arguments[i] = method.addArg(JavaType.typeOfName(args[j], scope));
        }
    }

    Scope bindScope(Scope scope, JavaClass regField, Scope fields[]) {
        for (int i = 0; i < arguments.length; ++i) {
            scope = new Scope(scope, names[i], arguments[i]);
            if (regField != null) {
                Binder field = regField.addField(names[i],
                                            arguments[i].getRef(0), false);
                fields[0] = new Scope(fields[0], names[i], field);
            }
        }
        return scope;
    }

    void init(Scope mscope, int depth) {
        Scope bodyScope = bindScope(mscope, null, null);
        if (bodyScope == mscope)
            bodyScope = new Scope(bodyScope, null, null);
        bodyScope.closure = method; // for bind var collection
        method.code = YetiAnalyzer.analyze(m[3], bodyScope, depth);
        if (JavaType.isAssignable(m[3], method.returnType,
                                  method.code.type, true) < 0) {
            throw new CompileException(m[3], "Cannot return " +
                        method.code.type + " as " + method.returnType);
        }
    }
}

final class DefineClass extends YetiType {
    static final class LocalClassBinding extends ClassBinding {
        private List proxies;
        private LocalClassBinding next;
        private BindRef[] captures;
        
        LocalClassBinding(Type classType) {
            super(classType);
        }

        BindRef[] getCaptures() {
            if (captures == null)
                throw new IllegalStateException("Captures not initialized");
            return captures;
        }

        ClassBinding dup(List proxies) {
            LocalClassBinding r = new LocalClassBinding(type);
            r.proxies = proxies;
            if (captures != null) {
                r.proxy(captures);
            } else {
                r.next = next;
                next = r;
            }
            return r;
        }

        private void proxy(BindRef[] captures) {
            if (captures.length == 0 || proxies.size() == 0) {
                this.captures = captures;
                return;
            }
            BindRef[] ca = new BindRef[captures.length];
            System.arraycopy(captures, 0, ca, 0, captures.length);
            // proxys were collected in reverse order (inner-first)
            for (int i = proxies.size(); --i >= 0;) {
                Closure c = (Closure) proxies.get(i);
                for (int j = 0; j < ca.length; ++j) {
                    ca[j] = c.refProxy(ca[j]);
                }
            }
            this.captures = ca;
            proxies = null;
        }

        void init(BindRef[] captures) {
            LocalClassBinding cb = this;
            while ((cb = cb.next) != null)
                cb.proxy(captures);
            this.captures = captures;
        }
    }

    /*
     * (:class Foo (:argument-list int x) (:extends Object (:arguments))
     * (foo = x)
     * (:method String getBlaah (:argument-list int y) (:concat (x + y))))
     */
    static JavaClass def(XNode cl, boolean topLevel,
                         Scope[] scope_, int depth) {
        Scope scope = scope_[0];
        JavaType parentClass = null;
        Node[] extend = ((XNode) cl.expr[2]).expr;
        Node[] superArgs = null;
        Node superNode = cl;
        List interfaces = new ArrayList();
        for (int i = 0; i < extend.length; i += 2) {
            JavaType t = resolveFullClass(extend[i].sym(), scope)
                            .javaType.resolve(extend[i]);
            Node[] args = ((XNode) extend[i + 1]).expr;
            if (t.isInterface()) {
                if (args != null)
                    throw new CompileException(extend[i + 1],
                        "Cannot give arguments to interface");
                interfaces.add(t.className());
            } else if (parentClass != null) {
                throw new CompileException(extend[i],
                    "Cannot extend multiple non-interface classes (" +
                        parentClass.dottedName() +
                        " and " + t.dottedName() + ')');
            } else {
                parentClass = t;
                superArgs = args;
                superNode = extend[i];
            }
        }
        String className = cl.expr[0].sym();
        if (scope.packageName != null && scope.packageName.length() != 0)
            className = scope.packageName + '/' + className;
        JavaClass c = new JavaClass(className, parentClass,
                                    (String[]) interfaces.toArray(
                                        new String[interfaces.size()]),
                                    topLevel);
        Type cType = new Type(JAVA, NO_PARAM);
        cType.javaType = c.javaType;
        scope = new Scope(scope, cl.expr[0].sym(), null);
        LocalClassBinding binding = new LocalClassBinding(cType);
        scope.importClass = binding;
        scope_[0] = scope;
        MethodDesc consDesc = new MethodDesc(c.constr, cl.expr[1], scope);

        // method defs
        List methods = new ArrayList();
        for (int i = 3; i < cl.expr.length; ++i) {
            String kind = cl.expr[i].kind;
            if (kind != "method" && kind != "static-method")
                continue;
            Node[] m = ((XNode) cl.expr[i]).expr;
            Type returnType = m[0].sym() == "void" ? UNIT_TYPE :
                                JavaType.typeOfName(m[0], scope);
            MethodDesc md = 
                new MethodDesc(c.addMethod(m[1].sym(), returnType,
                                           kind != "method", m[3].line),
                               m[2], scope);
            md.m = m;
            md.isStatic = kind != "method";
            methods.add(md);
        }

        try {
            c.close();
        } catch (JavaClassNotFoundException ex) {
            throw new CompileException(cl, ex);
        }

        Scope consScope = new Scope(scope, null, null);
        consScope.closure = c;
        Scope[] localRef = { consScope };
        consScope = consDesc.bindScope(consScope, c, localRef);
        Scope local = localRef[0];

        if (parentClass == null)
            parentClass = JavaType.fromDescription("Ljava/lang/Object;");
        if (superArgs == null)
            superArgs = new Node[0];
        Type parentType = new Type(JAVA, NO_PARAM);
        parentType.javaType = parentClass;

        Code[] initArgs = YetiAnalyzer.mapArgs(0, superArgs, consScope, depth);
        JavaType.Method superCons =
            JavaType.resolveConstructor(superNode, parentType, initArgs, false)
                    .check(superNode, scope.packageName);
        c.superInit(superCons, initArgs, superNode.line);

        // field defs
        for (int i = 3; i < cl.expr.length; ++i) {
            if (cl.expr[i] instanceof Bind) {
                Bind bind = (Bind) cl.expr[i];
                if (bind.property) {
                    throw new CompileException(bind,
                        "Class field cannot be a property");
                }
                Binder binder;
                Code code;
                if (bind.expr.kind == "lambda") {
                    Function lambda = new Function(new Type(depth + 1));
                    lambda.selfBind = binder =
                        c.addField(bind.name, lambda, bind.var);
                    YetiAnalyzer.lambdaBind(lambda, bind, bind.noRec ? local :
                            new Scope(local, bind.name, binder), depth + 1);
                    code = lambda;
                } else {
                    code = YetiAnalyzer.analyze(bind.expr, local, depth + 1);
                    binder = c.addField(bind.name, code, bind.var);
                    if (bind.type != null) {
                        YetiAnalyzer.isOp(bind, bind.type, code, scope, depth);
                    }
                }
                if (code.polymorph && !bind.var) {
                    local = bindPoly(bind.name, code.type, binder,
                                     depth, local);
                } else {
                    local = new Scope(local, bind.name, binder);
                }
            }
        }

        for (int i = 0, cnt = methods.size(); i < cnt; ++i) {
            MethodDesc md = (MethodDesc) methods.get(i);
            md.init(md.isStatic ? scope : local, depth);
        }

        binding.init(c.getCaptures());
        return c;
    }
}
