// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti language compiler java bytecode generator.
 *
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

import org.objectweb.asm.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

interface YetiCode {

    class CompileCtx {
        Map classes = new HashMap();
    }

    class Ctx implements Opcodes {
        CompileCtx compilation;
        String className;
        ClassWriter cw;
        MethodVisitor m;
        int localVarCount;
        int fieldCounter;

        Ctx(CompileCtx compilation, ClassWriter writer, String className) {
            this.compilation = compilation;
            this.cw = writer;
            this.className = className;
        }

        Ctx newClass(int flags, String name, String[] interfaces) {
            Ctx ctx = new Ctx(compilation,
                new ClassWriter(ClassWriter.COMPUTE_MAXS), name);
            ctx.cw.visit(V1_2, flags, name, null,
                         "java/lang/Object", interfaces);
            if (compilation.classes.put(name, ctx) != null) {
                throw new IllegalStateException("Duplicate class: " + name);
            }
            return ctx;
        }

        Ctx newMethod(int flags, String name, String type) {
            Ctx ctx = new Ctx(compilation, cw, className);
            ctx.m = cw.visitMethod(flags, name, type, null, null);
            ctx.m.visitCode();
            return ctx;
        }

        void closeMethod() {
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        
        void intConst(int n) {
            if (n >= -1 && n <= 5) {
                m.visitInsn(n + 3);
            } else {
                m.visitLdcInsn(new Integer(n));
            }
        }
    }

    class Code implements Opcodes {
        YetiType.Type type;
        boolean ignoreValue;

        void gen(Ctx ctx) {
            throw new UnsupportedOperationException(
                "gen not implemented in " + getClass());
        }

        // Some "functions" may have special kinds of apply
        Code apply(Code arg, YetiType.Type res) {
            return new Apply(res, this, arg);
        }

        BindRef bindRef() {
            return null;
        }

        void ignoreValue() {
            ignoreValue = true;
        }
    }

    class BindRef extends Code {
        Binder binder;
    }

    class NumericConstant extends Code {
        Number num;

        NumericConstant(Number num) {
            type = YetiType.NUM_TYPE;
            this.num = num;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "java/lang/Double");
            ctx.m.visitInsn(DUP);
            ctx.m.visitLdcInsn(num);
            ctx.m.visitMethodInsn(INVOKESPECIAL, "java/lang/Double",
                                  "<init>", "(D)V");
        }
    }

    class StringConstant extends Code {
        String str;

        StringConstant(String str) {
            type = YetiType.STR_TYPE;
            this.str = str;
        }

        void gen(Ctx ctx) {
            ctx.m.visitLdcInsn(str);
        }
    }

    class UnitConstant extends Code {
        UnitConstant() {
            type = YetiType.UNIT_TYPE;
        }

        void gen(Ctx ctx) {
            ctx.m.visitInsn(ACONST_NULL);
        }
    }

    class BooleanConstant extends BindRef implements Binder {
        boolean val;

        BooleanConstant(boolean val) {
            binder = this;
            type = YetiType.BOOL_TYPE;
            this.val = val;
        }

        public BindRef getRef() {
            return this;
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                                 val ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
        }
    }

    class EqBinder implements Binder {
        public BindRef getRef() {
            BindRef c = new BindRef();
            c.binder = this;
            c.type = YetiType.EQ_TYPE;
            return c;
        }
    }

    class LocalBind extends BindRef implements Binder {
        Code code;

        LocalBind(Code code) {
            this.code = code;
            type = code.type;
            binder = this;
        }

        public BindRef getRef() {
            return this;
        }
    }

    String[] FUN_INTERFACE = { "yeti/lang/Fun" };

    interface Closure {
        // Closures "wrap" references to the outside world.
        BindRef refProxy(BindRef code);
    }

    class Capture extends BindRef {
        private String id;
        Capture next;
        BindRef ref;

        void gen(Ctx ctx) {
            ctx.m.visitVarInsn(ALOAD, 0); // this
            ctx.m.visitFieldInsn(GETFIELD, ctx.className, getId(ctx),
                                 "Ljava/lang/Object;");
        }

        String getId(Ctx ctx) {
            if (id == null) {
                id = "_".concat(Integer.toString(ctx.fieldCounter++));
            }
            return id;
        }
    }

    class Function extends Code implements Binder, Closure {
        Code body;
        String bindName;
        Capture captures;

        final BindRef arg = new BindRef() {
            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, 1);
            }
        };

        Function(YetiType.Type type) {
            this.type = type;
            arg.binder = this;
        }

        public BindRef getRef() {
            return arg;
        }

        public BindRef refProxy(BindRef code) {
            for (Capture c = captures; c != null; c = c.next) {
                if (c.binder == code.binder) {
                    return c;
                }
            }
            Capture c = new Capture();
            c.binder = code.binder;
            c.type = code.type;
            c.ref = code;
            c.next = captures;
            captures = c;
            return c;
        }

        void gen(Ctx ctx) {
            if (bindName == null) {
                bindName = "";
            }
            String name = ctx.className + '$' + bindName;
            Map classes = ctx.compilation.classes;
            for (int i = 0; classes.containsKey(name); ++i) {
                name = ctx.className + '$' + bindName + i;
            }
            Ctx fun = ctx.newClass(ACC_STATIC | ACC_FINAL, name, FUN_INTERFACE);

            StringBuffer carg = new StringBuffer("(");
            for (Capture c = captures; c != null; c = c.next) {
                carg.append("Ljava/lang/Object;");
                fun.cw.visitField(ACC_PRIVATE | ACC_FINAL, c.getId(fun),
                                  "Ljava/lang/Object;", null, null).visitEnd();
            }
            carg.append(")V");
            String cargt = carg.toString();

            MethodVisitor init = // constructor
                fun.cw.visitMethod(0, "<init>", cargt, null, null);
            init.visitVarInsn(ALOAD, 0); // this.
            init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object",
                                 "<init>", "()V"); // super();
            int n = 0; // copy constructor arguments to fields
            for (Capture c = captures; c != null; c = c.next) {
                init.visitVarInsn(ALOAD, 0);
                init.visitVarInsn(ALOAD, ++n);
                init.visitFieldInsn(PUTFIELD, fun.className, c.getId(fun),
                                     "Ljava/lang/Object;");
            }
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();

            Ctx apply = fun.newMethod(ACC_PUBLIC | ACC_FINAL, "apply",
                                      "(Ljava/lang/Object;)Ljava/lang/Object;");
            apply.localVarCount = 2; // this, arg
            body.gen(apply);
            apply.m.visitInsn(ARETURN);
            apply.closeMethod();

            ctx.m.visitTypeInsn(NEW, name);
            ctx.m.visitInsn(DUP);
            // Capture a closure
            for (Capture c = captures; c != null; c = c.next) {
                c.ref.gen(ctx);
            }
            ctx.m.visitMethodInsn(INVOKESPECIAL, name, "<init>", cargt);
        }
    }

    class Apply extends Code {
        Code fun, arg;

        Apply(YetiType.Type res, Code fun, Code arg) {
            type = res;
            this.fun = fun;
            this.arg = arg;
        }

        void gen(Ctx ctx) {
            fun.gen(ctx);
            arg.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Fun",
                "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

/*    class Argument extends Code {
    }
*/
    class VariantConstructor extends Code {
        String name;

        VariantConstructor(YetiType.Type type, String name) {
            this.type = type;
            this.name = name;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/TagCon");
            ctx.m.visitInsn(DUP);
            ctx.m.visitLdcInsn(name);
            ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/TagCon",
                                  "<init>", "(Ljava/lang/String;)V");
        }
    }

    class SelectMember extends Code {
        Code st;
        String name;

        SelectMember(YetiType.Type type, Code st, String name) {
            this.type = type;
            this.st = st;
            this.name = name;
        }

        void gen(Ctx ctx) {
            st.gen(ctx);
            ctx.m.visitLdcInsn(name);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                "get", "(Ljava/lang/String;)Ljava/lang/Object;");
        }
    }

    class ConditionalExpr extends Code {
        Code[][] choices;

        ConditionalExpr(YetiType.Type type, Code[][] choices) {
            this.type = type;
            this.choices = choices;
        }

        void gen(Ctx ctx) {
            Label end = new Label();
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                                 "TRUE", "Ljava/lang/Boolean;");
            for (int i = 0, last = choices.length - 1; i <= last; ++i) {
                Label jmpNext = i < last ? new Label() : end;
                if (choices[i].length == 2) {
                    boolean dup = i < last && choices[i + 1].length != 1;
                    if (dup) {
                        ctx.m.visitInsn(DUP); // copy TRUE value
                    }
                    choices[i][1].gen(ctx); // condition
                    ctx.m.visitJumpInsn(IF_ACMPNE, jmpNext);
                    if (dup) {
                        ctx.m.visitInsn(POP);
                    }
                    choices[i][0].gen(ctx); // body
                    ctx.m.visitJumpInsn(GOTO, end);
                } else {
                    choices[i][0].gen(ctx);
                }
                ctx.m.visitLabel(jmpNext);
            }
        }
    }

    class SeqExpr extends Code {
        Code st;
        Code result;

        SeqExpr(Code statement) {
            st = statement;
        }

        void gen(Ctx ctx) {
            st.gen(ctx);
            ctx.m.visitInsn(POP); // ignore the result of st expr
            result.gen(ctx);
        }
    }

    interface Binder {
        BindRef getRef();
    }

    class BindExpr extends SeqExpr implements Binder {
        private int id;

        BindExpr(Code expr) {
            super(expr);
        }

        public BindRef getRef() {
            BindRef res = st.bindRef();
            if (res == null) {
                res = new BindRef() {
                    void gen(Ctx ctx) {
                        ctx.m.visitVarInsn(ALOAD, id);
                    }
                };
                res.binder = this;
                res.type = st.type;
            }
            return res;
        }

        void gen(Ctx ctx) {
            id = ctx.localVarCount++;
            st.gen(ctx);
            ctx.m.visitVarInsn(ASTORE, id);
            result.gen(ctx);
        }
    }

    class StructConstructor extends Code {
        String[] names;
        Code[] values;

        StructConstructor(YetiType.Type type, String[] names, Code[] values) {
            this.type = type;
            this.names = names;
            this.values = values;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/Struct");
            ctx.m.visitInsn(DUP);
            ctx.intConst(names.length * 2);
            ctx.m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            for (int i = 0, cnt = names.length - 1; i <= cnt; ++i) {
                ctx.m.visitInsn(DUP);
                ctx.intConst(i * 2);
                ctx.m.visitLdcInsn(names[i]);
                ctx.m.visitInsn(AASTORE);
                ctx.m.visitInsn(DUP);
                ctx.intConst(i * 2 + 1);
                values[i].gen(ctx);
                ctx.m.visitInsn(AASTORE);
            }
            ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Struct",
                                  "<init>", "([Ljava/lang/Object;)V");
        }
    }

    interface Choice {
        Binder bindParam(YetiType.Type type);
        void setExpr(Code expr);
    }

    class CaseExpr extends Code {
        Code caseValue;
        List choices = new ArrayList();
        int id;

        CaseExpr(Code caseValue) {
            this.caseValue = caseValue;
        }

        class Bind extends BindRef implements Binder {
            public BindRef getRef() {
                binder = this;
                return this;
            }

            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, id);
            }
        }

        class Variant implements Choice {
            String tag;
            Code expr;

            public Binder bindParam(YetiType.Type type) {
                Bind bind = new Bind();
                bind.type = type;
                return bind;
            }

            public void setExpr(Code expr) {
                this.expr = expr;
            }
        }

        Choice addVariantChoice(String tag) {
            Variant variant = new Variant();
            variant.tag = tag;
            choices.add(variant);
            return variant;
        }

        void gen(Ctx ctx) {
            caseValue.gen(ctx);
            id = ctx.localVarCount++;
            Label end = new Label();
            // stupid variant case checker.
            // TODO other kinds of case patterns...

            // stupid jvm GETFIELD wants to be sure it is really a Tag... ;)
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Tag");
            ctx.m.visitInsn(DUP);
            ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "value",
                                 "Ljava/lang/Object;");
            ctx.m.visitVarInsn(ASTORE, id);
            ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "name",
                                 "Ljava/lang/String;");
            for (int last = choices.size() - 1, i = 0; i <= last; ++i) {
                Variant v = (Variant) choices.get(i);
                if (i < last) {
                    ctx.m.visitInsn(DUP); // save tag name
                }
                ctx.m.visitLdcInsn(v.tag);
                Label next = new Label();
                ctx.m.visitJumpInsn(IF_ACMPNE, next);
                if (i < last) {
                    ctx.m.visitInsn(POP); // remove tag name
                }
                v.expr.gen(ctx);
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(next);
            }
            ctx.m.visitInsn(ACONST_NULL);
            ctx.m.visitLabel(end);
        }
    }

    class ListConstructor extends Code {
        ListConstructor(YetiType.Type type, Code[] items) {
            this.type = type;
        }
    }

    class PrintlnFun extends BindRef implements Binder {
        PrintlnFun() {
            this.type = YetiType.A_TO_UNIT;
            binder = this;
        }

        public BindRef getRef() {
            return new PrintlnFun();
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core", "PRINTLN",
                    "Lyeti/lang/Fun;");
        }
    }

    class Test implements Opcodes {
        public static void main(String[] argv) throws Exception {
            Code codeTree = YetiType.toCode(argv[0].toCharArray());
            CompileCtx compilation = new CompileCtx();
            Ctx ctx = new Ctx(compilation, null, null)
                .newClass(ACC_PUBLIC, "Test", null)
                .newMethod(ACC_PUBLIC | ACC_STATIC, "main",
                        "([Ljava/lang/String;)V");
            ctx.localVarCount++;
            codeTree.gen(ctx);
            /*            m.visitFieldInsn(GETSTATIC, "java/lang/System",
                          "out", "Ljava/io/PrintStream;");
                          m.visitLdcInsn("Hello world!");
                          m.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream",
                          "println", "(Ljava/lang/String;)V"); */
            ctx.m.visitInsn(RETURN);
            ctx.closeMethod();

            Iterator i = compilation.classes.values().iterator();
            while (i.hasNext()) {
                Ctx c = (Ctx) i.next();
                byte[] code = c.cw.toByteArray();
                FileOutputStream out =
                    new FileOutputStream(c.className + ".class");
                out.write(code);
                out.close();
            }
        }
    }
}
