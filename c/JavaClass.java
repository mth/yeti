// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
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

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

final class JavaClass extends CapturingClosure {
    private String className;
    private String[] implement;
    private YetiType.ClassBinding parentClass;
    private List fields = new ArrayList();
    private List methods = new ArrayList();
    private Map fieldNames = new HashMap();
    private JavaExpr superInit;
    private final boolean isPublic;
    private boolean usedForceDirect;
    private int captureCount;
    YetiType.Type classType;
    final Meth constr = new Meth();
    final Binder self;

    static class Arg extends BindRef implements Binder {
        int argn;
        final YetiType.Type javaType;

        Arg(JavaClass c) {
            type = javaType = c.classType;
            binder = this;
        }

        Arg(YetiType.Type type) {
            this.javaType = type;
            this.type = JavaType.convertValueType(type);
            binder = this;
        }

        public BindRef getRef(int line) {
            return this;
        }

        void gen(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, argn);
            if (javaType.javaType.description.charAt(0) == 'L')
                ctx.forceType(javaType.javaType.className());
        }
    }

    static class Meth extends JavaType.Method implements Closure {
        private List args = new ArrayList();
        private AClosure closure = new RootClosure(); // just for closure init
        private int line;
        Capture captures;
        Code code;

        Binder addArg(YetiType.Type type) {
            Arg arg = new Arg(type);
            args.add(arg);
            arg.argn = (access & ACC_STATIC) == 0
                            ? args.size() : args.size() - 1;
            return arg;
        }

        public BindRef refProxy(BindRef code) {
            return code; // method don't capture - this is outer classes job
        }

        public void addVar(BindExpr binder) {
            closure.addVar(binder);
        }
        
        void init() {
            arguments = new YetiType.Type[args.size()];
            for (int i = 0; i < arguments.length; ++i) {
                Arg arg = (Arg) args.get(i);
                arguments[i] = arg.javaType;
            }
            sig = name.concat(super.descr(null));
            descr = null;
        }

        String descr(String extra) {
            if (descr != null)
                return descr;
            StringBuffer additionalArgs = new StringBuffer();
            for (Capture c = captures; c != null; c = c.next)
                additionalArgs.append(c.captureType());
            return super.descr(additionalArgs.toString());
        }

        void convertArgs(Ctx ctx) {
            int n = (access & ACC_STATIC) == 0 ? 1 : 0;
            ctx.localVarCount = args.size() + n;
            for (int i = 0; i < arguments.length; ++i) {
                if (arguments[i].type != YetiType.JAVA)
                    continue;
                String descr = arguments[i].javaType.description;
                if (descr != "Ljava/lang/String;" && descr.charAt(0) == 'L')
                    continue;
                int ins = ILOAD;
                switch (descr.charAt(0)) {
                    case 'D': ins = DLOAD; break;
                    case 'F': ins = FLOAD; break;
                    case 'J': ins = LLOAD; break;
                    case 'C':
                    case 'L': ins = ALOAD; break;
                }
                ctx.visitVarInsn(ins, i + n);
                JavaExpr.convertValue(ctx, arguments[i]);
                ctx.visitVarInsn(ASTORE, i + n);
            }
        }

        void gen(Ctx ctx) {
            ctx = ctx.newMethod(access, name, descr(null));
            if ((access & ACC_ABSTRACT) != 0) {
                ctx.closeMethod();
                return;
            }
            convertArgs(ctx);
            closure.genClosureInit(ctx);
            JavaExpr.convertedArg(ctx, code, returnType, line);
            if (returnType.type == YetiType.UNIT) {
                ctx.visitInsn(POP);
                ctx.visitInsn(RETURN);
            } else {
                int ins = IRETURN;
                switch (returnType.javaType.description.charAt(0)) {
                    case 'D': ins = DRETURN; break;
                    case 'F': ins = FRETURN; break;
                    case 'J': ins = LRETURN; break;
                    case '[':
                    case 'L': ins = ARETURN; break;
                }
                ctx.visitInsn(ins);
            }
            ctx.closeMethod();
        }
    }

    final class Field extends Code implements Binder, CaptureWrapper {
        String name; // mangled name
        private String javaType;
        String descr;
        Code value;
        boolean var;

        Field(String name, Code value, boolean var) {
            this.name = name;
            this.value = value;
            this.var = var;
        }

        public void genPreGet(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, 0);
        }

        public void genGet(Ctx ctx) {
            ctx.visitFieldInsn(GETFIELD, ctx.className, name, descr);
        }

        public void genSet(Ctx ctx, Code value) {
            value.gen(ctx);
            ctx.visitTypeInsn(CHECKCAST, javaType);
            ctx.visitFieldInsn(PUTFIELD, ctx.className, name, descr);
        }

        public Object captureIdentity() {
            return this;
        }

        public String captureType() {
            return descr;
        }

        public BindRef getRef(int line) {
            if (javaType == null) {
                if (name == "_")
                    throw new IllegalStateException("NO _ REF");
                javaType = Code.javaType(value.type);
                descr = 'L' + javaType + ';';
            }
            BindRef ref = new BindRef() {
                void gen(Ctx ctx) {
                    genPreGet(ctx);
                    genGet(ctx);
                }

                Code assign(final Code value) {
                    return var ? new Code() {
                        void gen(Ctx ctx) {
                            genPreGet(ctx);
                            genSet(ctx, value);
                            ctx.visitInsn(ACONST_NULL);
                        }
                    } : null;
                }

                boolean flagop(int fl) {
                    return (fl & ASSIGN) != 0 && var;
                }

                CaptureWrapper capture() {
                    return Field.this;
                }
            };
            ref.type = value.type;
            ref.binder = this;
            return ref;
        }

        void gen(Ctx ctx) {
            if (javaType == null) {
                value.gen(ctx);
                ctx.visitInsn(POP);
                return;
            }
            ctx.cw.visitField(var ? ACC_PRIVATE : ACC_PRIVATE | ACC_FINAL,
                              name, descr, null, null).visitEnd();
            genPreGet(ctx);
            genSet(ctx, value);
        }
    }

    JavaClass(String className, boolean isPublic) {
        type = YetiType.UNIT_TYPE;
        this.className = className;
        classType = new YetiType.Type(YetiType.JAVA, YetiType.NO_PARAM);
        classType.javaType = JavaType.createNewClass(className);
        self = new Arg(this);
        constr.name = "<init>";
        constr.returnType = YetiType.UNIT_TYPE;
        constr.className = className;
        constr.access = isPublic ? ACC_PUBLIC : 0;
        this.isPublic = isPublic;
    }

    void init(YetiType.ClassBinding parentClass, String[] interfaces) {
        this.parentClass = parentClass;
        implement = interfaces;
    }

    Meth addMethod(String name, YetiType.Type returnType,
                   String mod, int line) {
        Meth m = new Meth();
        m.name = name;
        m.returnType = returnType;
        m.className = className;
        m.access = mod == "static-method" ? ACC_PUBLIC + ACC_STATIC
                 : mod == "abstract-method" ? ACC_PUBLIC + ACC_ABSTRACT
                 : ACC_PUBLIC;
        m.line = line;
        methods.add(m);
        return m;
    }

    Binder addField(String name, Code value, boolean var) {
        if (name != "_") {
            String mangled = mangle(name);
            name = mangled;
            int n = fieldNames.size();
            while (fieldNames.containsKey(name)) {
                name = mangled + n++;
            }
        }
        Field field = new Field(name, value, var);
        fieldNames.put(name, null);
        fields.add(field);
        return field;
    }

    public BindRef refProxy(BindRef code) {
        if (code.flagop(DIRECT_BIND))
            return code;
        if (!isPublic)
            return captureRef(code);
        code.forceDirect();
        usedForceDirect = true;
        return code;
    }

    void superInit(JavaType.Method init, Code[] args, int line) {
        superInit = new JavaExpr(null, init, args, line);
    }

    void close() throws JavaClassNotFoundException {
        constr.init();
        JavaTypeReader t = new JavaTypeReader();
        t.constructors.add(constr);
        for (int i = 0, cnt = methods.size(); i < cnt; ++i) {
            Meth m = (Meth) methods.get(i);
            m.init();
            ((m.access & ACC_STATIC) != 0 ? t.staticMethods : t.methods).add(m);
        }
        t.parent = parentClass.type.javaType;
        t.className = className;
        t.interfaces = implement;
        t.access = isPublic ? ACC_PUBLIC : 0;
        classType.javaType.publicMask = ACC_PUBLIC | ACC_PROTECTED;
        classType.javaType.resolve(t);
    }

    // must be called after close
    BindRef[] getCaptures() {
        captureCount = mergeCaptures(null);
        BindRef[] r = new BindRef[captureCount];
        int n = 0;
        for (Capture c = captures; c != null; c = c.next) {
            r[n++] = c.ref;
        }
        return r;
    }

    // called by mergeCaptures
    void captureInit(Ctx fun, Capture c, int n) {
        c.id = "_" + n;
        // for super arguments
        c.localVar = n + constr.args.size() + 1;
    }

    void gen(Ctx ctx) {
        constr.captures = captures;
        ctx.visitInsn(ACONST_NULL);
        Ctx clc = ctx.newClass(classType.javaType.access | ACC_SUPER,
                        className, parentClass.type.javaType.className(),
                        implement);
        clc.fieldCounter = captureCount;
        if (!isPublic)
            clc.markInnerClass(ctx.constants.ctx, ACC_STATIC);
        Ctx init = clc.newMethod(constr.access, "<init>", constr.descr(null));
        constr.convertArgs(init);
        genClosureInit(init);
        init.visitVarInsn(ALOAD, 0); // this.
        superInit.genCall(init, parentClass.getCaptures(), INVOKESPECIAL);
        // extra arguments are used for smugling in captured bindings
        int n = constr.arguments.length;
        for (Capture c = captures; c != null; c = c.next) {
            c.localVar = -1; // reset to using this
            clc.cw.visitField(0, c.id, c.captureType(), null, null).visitEnd();
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, ++n);
            init.visitFieldInsn(PUTFIELD, className, c.id, c.captureType());
        }
        for (int i = 0, cnt = fields.size(); i < cnt; ++i)
            ((Code) fields.get(i)).gen(init);
        init.visitInsn(RETURN);
        init.closeMethod();
        for (int i = 0, cnt = methods.size(); i < cnt; ++i)
            ((Meth) methods.get(i)).gen(clc);
        if (usedForceDirect) {
            Ctx clinit = clc.newMethod(ACC_STATIC, "<clinit>", "()V");
            clinit.visitMethodInsn(INVOKESTATIC, ctx.className,
                                   "eval", "()Ljava/lang/Object;");
            clinit.visitInsn(POP);
            clinit.visitInsn(RETURN);
            clinit.closeMethod();
        }
    }
}
