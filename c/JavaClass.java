// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
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

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

final class JavaClass extends CapturingClosure {
    private String className;
    private String parentClass = "java/lang/Object";
    private List fields = new ArrayList();
    private List methods = new ArrayList();
    private Map fieldNames = new HashMap();
    private boolean isPublic;
    private int captureCount;
    JavaType javaType;
    final Meth constr = new Meth() {
        Binder addArg(YetiType.Type type, String name) {
            return addField(name, super.addArg(type, name).getRef(0),
                            false);
        }
    };

    static class Arg extends BindRef implements Binder {
        int argn;
        final YetiType.Type javaType;

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
        }
    }

    static class Meth extends JavaType.Method {
        private List args = new ArrayList();
        private int line;
        Capture captures;
        Code code;

        Binder addArg(YetiType.Type type, String name) {
            Arg arg = new Arg(type);
            args.add(arg);
            arg.argn = (access & ACC_STATIC) == 0
                            ? args.size() : args.size() - 1;
            return arg;
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
                String descr = arguments[i].javaType.description;
                if (descr != "Ljava/lang/String;" && descr.charAt(0) == 'L')
                    continue;
                int ins = ILOAD;
                switch (descr.charAt(0)) {
                    case 'D': ins = DLOAD; break;
                    case 'F': ins = FLOAD; break;
                    case 'J': ins = LLOAD; break;
                    case '[':
                    case 'L': ins = ALOAD; break;
                }
                ctx.visitVarInsn(ins, i + n);
                JavaExpr.convertValue(ctx, arguments[i]);
                ctx.visitVarInsn(ASTORE, i + n);
            }
        }

        void gen(Ctx ctx) {
            ctx = ctx.newMethod(access, name, descr(null));
            convertArgs(ctx);
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

    static final class Field extends Code
            implements Binder, CaptureWrapper {
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

    JavaClass(String className, JavaType parentClass, boolean isPublic) {
        type = YetiType.UNIT_TYPE;
        this.className = className;
        constr.name = "<init>";
        constr.returnType = YetiType.UNIT_TYPE;
        constr.className = className;
        constr.access = isPublic ? ACC_PUBLIC : 0;
        this.isPublic = isPublic;
        if (parentClass != null)
            this.parentClass = parentClass.className();
    }

    Meth addMethod(String name, YetiType.Type returnType,
                   boolean static_, int line) {
        Meth m = new Meth();
        m.name = name;
        m.returnType = returnType;
        m.className = className;
        m.access = static_ ? ACC_PUBLIC | ACC_STATIC : ACC_PUBLIC;
        m.line = line;
        methods.add(m);
        return m;
    }

    Binder addField(String name, Code value, boolean var) {
        name = mangle(name);
        String fname = name;
        int n = fieldNames.size();
        while (fieldNames.containsKey(fname)) {
            fname = name + n++;
        }
        Field field = new Field(fname, value, var);
        fields.add(field);
        return field;
    }

    void close() throws JavaClassNotFoundException {
        captureCount = mergeCaptures(null);
        constr.captures = captures;
        constr.init();
        JavaTypeReader t = new JavaTypeReader();
        t.constructors.add(constr);
        for (int i = 0, cnt = methods.size(); i < cnt; ++i) {
            Meth m = (Meth) methods.get(i);
            m.init();
            ((m.access & ACC_STATIC) != 0 ? t.staticMethods : t.methods).add(m);
        }
        t.parent = parentClass;
        t.className = className;
        t.access = isPublic ? ACC_PUBLIC : 0;
        javaType = new JavaType(t);
    }

    // must be called after close
    BindRef[] getCaptures() {
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
    }

    void gen(Ctx ctx) {
        ctx.visitInsn(ACONST_NULL);
        Ctx clc = ctx.newClass(ACC_STATIC | ACC_PUBLIC | ACC_SUPER,
                               className, parentClass);
        clc.fieldCounter = captureCount;
        for (Capture c = captures; c != null; c = c.next) {
            clc.cw.visitField(0, c.id, c.captureType(), null, null).visitEnd();
        }
        Ctx init = clc.newMethod(ACC_PUBLIC, "<init>", constr.descr(null));
        init.visitVarInsn(ALOAD, 0); // this.
        init.visitMethodInsn(INVOKESPECIAL, parentClass, "<init>", "()V");
        // extra arguments are used for smugling in captured bindings
        int n = constr.arguments.length;
        for (Capture c = captures; c != null; c = c.next) {
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, ++n);
            init.visitFieldInsn(PUTFIELD, className, c.id, c.captureType());
        }
        constr.convertArgs(init);
        for (int i = 0, cnt = fields.size(); i < cnt; ++i) {
            ((Code) fields.get(i)).gen(init);
        }
        init.visitInsn(RETURN);
        init.closeMethod();
        for (int i = 0, cnt = methods.size(); i < cnt; ++i) {
            ((Meth) methods.get(i)).gen(clc);
        }
    }
}
