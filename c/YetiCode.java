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

        Ctx newClass(int flags, String name, String extend) {
            Ctx ctx = new Ctx(compilation,
                    new ClassWriter(ClassWriter.COMPUTE_MAXS), name);
            ctx.cw.visit(V1_2, flags, name, null,
                    extend == null ? "java/lang/Object" : extend, null);
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

    abstract class Code implements Opcodes {
        YetiType.Type type;
        boolean ignoreValue;

        abstract void gen(Ctx ctx);

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

        Code assign(Code value) {
            return null;
        }
    }

    abstract class BindRef extends Code {
        Binder binder;

        // some bindrefs care about being captured. most wont.
        CaptureWrapper capture() {
            return null;
        }

        // mark as used lvalue
        boolean assign() {
            return false;
        }
    }

    class NumericConstant extends Code {
        Number num;

        NumericConstant(Number num) {
            type = YetiType.NUM_TYPE;
            this.num = num;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/FloatNum");
            ctx.m.visitInsn(DUP);
            ctx.m.visitLdcInsn(num);
            ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/FloatNum",
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

    interface Closure {
        // Closures "wrap" references to the outside world.
        BindRef refProxy(BindRef code);
        void addVar(BindExpr binder);
    }

    class Capture extends BindRef implements CaptureWrapper {
        String id;
        Capture next;
        CaptureWrapper wrapper;
        BindRef ref;
        Object identity;

        void gen(Ctx ctx) {
            ctx.m.visitVarInsn(ALOAD, 0); // this
            ctx.m.visitFieldInsn(GETFIELD, ctx.className, id,
                    captureType());
            if (wrapper != null) {
                wrapper.genGet(ctx);
            }
        }

        String getId(Ctx ctx) {
            if (id == null) {
                id = "_".concat(Integer.toString(ctx.fieldCounter++));
            }
            return id;
        }

        boolean assign() {
            return ref.assign();
        }

        Code assign(final Code value) {
            if (!ref.assign()) {
                return null;
            }
            return new Code() {
                void gen(Ctx ctx) {
                    genPreGet(ctx);
                    wrapper.genSet(ctx, value);
                    ctx.m.visitInsn(ACONST_NULL);
                }
            };
        }

        public void genPreGet(Ctx ctx) {
            ctx.m.visitVarInsn(ALOAD, 0);
            ctx.m.visitFieldInsn(GETFIELD, ctx.className, id,
                captureType());
        }

        public void genGet(Ctx ctx) {
            wrapper.genGet(ctx);
        }

        public void genSet(Ctx ctx, Code value) {
            wrapper.genSet(ctx, value);
        }

        public CaptureWrapper capture() {
            return wrapper == null ? null : this;
        }

        public Object captureIdentity() {
            return wrapper == null ? this : wrapper.captureIdentity();
        }

        public String captureType() {
            return wrapper == null ? "Ljava/lang/Object;"
                                   : wrapper.captureType();
        }
    }

    abstract class AClosure extends Code implements Closure {
        private List vars = new ArrayList();

        public void addVar(BindExpr binder) {
            vars.add(binder);
        }

        public void genClosureInit(Ctx ctx) {
            int id = -1, mvarcount = 0;
            for (int i = vars.size(); --i >= 0;) {
                BindExpr bind = (BindExpr) vars.get(i);
                if (bind.assigned && bind.captured) {
                    if (id == -1) {
                        id = ctx.localVarCount++;
                    }
                    bind.setMVarId(this, id, mvarcount++);
                }
            }
            if (mvarcount > 0) {
                ctx.intConst(mvarcount);
                ctx.m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                ctx.m.visitVarInsn(ASTORE, id);
            }
        }
    }

    class Function extends AClosure implements Binder {
        private String name;
        Binder selfBind;
        Code body;
        String bindName;
        private Capture captures;
        private BindRef selfRef;

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
            // TODO cruel hack. should define special interface
            // for those special things
            if (code instanceof ArithOpFun ||
                    code instanceof CompareFun) {
                return code;
            }
            if (selfBind == code.binder && !code.assign()) {
                if (selfRef == null) {
                    selfRef = new BindRef() {
                        void gen(Ctx ctx) {
                            ctx.m.visitVarInsn(ALOAD, 0);
                        }
                    };
                    selfRef.binder = selfBind;
                    selfRef.type = code.type;
                }
                return selfRef;
            }
            for (Capture c = captures; c != null; c = c.next) {
                if (c.binder == code.binder) {
                    return c;
                }
            }
            Capture c = new Capture();
            c.binder = code.binder;
            c.type = code.type;
            c.ref = code;
            c.wrapper = code.capture();
            c.next = captures;
            captures = c;
            return c;
        }

        void prepareGen(Ctx ctx) {
            if (bindName == null) {
                bindName = "";
            }
            name = ctx.className + '$' + bindName;
            Map classes = ctx.compilation.classes;
            for (int i = 0; classes.containsKey(name); ++i) {
                name = ctx.className + '$' + bindName + i;
            }
            Ctx fun = ctx.newClass(ACC_STATIC | ACC_FINAL, name,
                    "yeti/lang/Fun");
            Capture prev = null;
        next_capture:
            for (Capture c = captures; c != null; c = c.next) {
                Object identity = c.identity = c.captureIdentity();
                // remove shared captures
                for (Capture i = captures; i != c; i = i.next) {
                    if (i.identity == identity) {
                        c.id = i.id; // copy old one's id
                        prev.next = c.next;
                        continue next_capture;
                    }
                }
                fun.cw.visitField(0, c.getId(fun),
                        c.captureType(), null, null).visitEnd();
                prev = c;
            }
            MethodVisitor init = // constructor
                fun.cw.visitMethod(0, "<init>", "()V", null, null);
            init.visitVarInsn(ALOAD, 0); // this.
            init.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Fun",
                    "<init>", "()V"); // super();
            init.visitInsn(RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();

            Ctx apply = fun.newMethod(ACC_PUBLIC | ACC_FINAL, "apply",
                    "(Ljava/lang/Object;)Ljava/lang/Object;");
            apply.localVarCount = 2; // this, arg
            genClosureInit(apply);
            body.gen(apply);
            apply.m.visitInsn(ARETURN);
            apply.closeMethod();

            ctx.m.visitTypeInsn(NEW, name);
            ctx.m.visitInsn(DUP);
            ctx.m.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V");
        }

        void finishGen(Ctx ctx) {
            // Capture a closure
            for (Capture c = captures; c != null; c = c.next) {
                ctx.m.visitInsn(DUP);
                if (c.wrapper == null) {
                    c.ref.gen(ctx);
                } else {
                    c.wrapper.genPreGet(ctx);
                }
                ctx.m.visitFieldInsn(PUTFIELD, name, c.id,
                        c.captureType());
            }
        }

        void gen(Ctx ctx) {
            prepareGen(ctx);
            finishGen(ctx);
        }
    }

    class RootClosure extends AClosure {
        Code code;

        public BindRef refProxy(BindRef code) {
            return code;
        }

        void gen(Ctx ctx) {
            genClosureInit(ctx);
            code.gen(ctx);
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
            // XXX this cast could be optimised away sometimes
            //  - when the fun really is Fun by java types
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
            arg.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                    "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

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

    abstract class SelectMember extends Code {
        private boolean assigned = false;
        Code st;
        String name;

        SelectMember(YetiType.Type type, Code st, String name) {
            this.type = type;
            this.st = st;
            this.name = name;
        }

        void gen(Ctx ctx) {
            st.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
            ctx.m.visitLdcInsn(name);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                    "get", "(Ljava/lang/String;)Ljava/lang/Object;");
        }

        Code assign(final Code setValue) {
            if (!assigned && !mayAssign()) {
                return null;
            }
            assigned = true;
            return new Code() {
                void gen(Ctx ctx) {
                    st.gen(ctx);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
                    ctx.m.visitLdcInsn(name);
                    setValue.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                            "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
                    ctx.m.visitInsn(ACONST_NULL);
                }
            };
        }

        abstract boolean mayAssign();
    }

    class KeyRefExpr extends Code {
        Code val;
        Code key;

        KeyRefExpr(YetiType.Type type, Code val, Code key) {
            this.type = type;
            this.val = val;
            this.key = key;
        }

        void gen(Ctx ctx) {
            val.gen(ctx);
            key.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                    "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }

        Code assign(final Code setValue) {
            return new Code() {
                void gen(Ctx ctx) {
                    val.gen(ctx);
                    key.gen(ctx);
                    setValue.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                            "put", "(Ljava/lang/Object;Ljava/lang/Object;)" +
                            "Ljava/lang/Object;");
                }
            };
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

    interface CaptureWrapper {
        void genPreGet(Ctx ctx);
        void genGet(Ctx ctx);
        void genSet(Ctx ctx, Code value);
        Object captureIdentity();
        String captureType();
    }

    class BindExpr extends SeqExpr implements Binder, CaptureWrapper {
        private int id;
        private int mvar = -1;
        private final boolean var;
        private Closure closure;
        boolean assigned;
        boolean captured;

        BindExpr(Code expr, boolean var) {
            super(expr);
            this.var = var;
        }

        void setMVarId(Closure closure, int arrayId, int index) {
            this.closure = closure;
            mvar = arrayId;
            id = index;
        }

        public BindRef getRef() {
            BindRef res = st.bindRef();
            if (res == null) {
                res = new BindRef() {
                    void gen(Ctx ctx) {
                        genPreGet(ctx);
                        genGet(ctx);
                    }

                    Code assign(final Code value) {
                        if (!var) {
                            return null;
                        }
                        assigned = true;
                        return new Code() {
                            void gen(Ctx ctx) {
                                genLocalSet(ctx, value);
                                ctx.m.visitInsn(ACONST_NULL);
                            }
                        };
                    }

                    boolean assign() {
                        return var ? assigned = true : false;
                    }

                    CaptureWrapper capture() {
                        captured = true;
                        return var ? BindExpr.this : null;
                    }
                };
                res.binder = this;
                res.type = st.type;
            }
            return res;
        }

        public Object captureIdentity() {
            return mvar == -1 ? (Object) this : closure;
        }

        public String captureType() {
            return mvar == -1 ? "Ljava/lang/Object;" : "[Ljava/lang/Object;";
        }

        public void genPreGet(Ctx ctx) {
            ctx.m.visitVarInsn(ALOAD, mvar == -1 ? id : mvar);
        }

        public void genGet(Ctx ctx) {
            if (mvar != -1) {
                ctx.intConst(id);
                ctx.m.visitInsn(AALOAD);
            }
        }

        public void genSet(Ctx ctx, Code value) {
            ctx.intConst(id);
            value.gen(ctx);
            ctx.m.visitInsn(AASTORE);
        }

        private void genLocalSet(Ctx ctx, Code value) {
            if (mvar == -1) {
                value.gen(ctx);
                ctx.m.visitVarInsn(ASTORE, id);
            } else {
                ctx.m.visitVarInsn(ALOAD, mvar);
                ctx.intConst(id);
                value.gen(ctx);
                ctx.m.visitInsn(AASTORE);
            }
        }

        void gen(Ctx ctx) {
            if (mvar == -1) {
                id = ctx.localVarCount++;
            }
            genLocalSet(ctx, st);
            result.gen(ctx);
        }
    }

    class StructConstructor extends Code {
        String[] names;
        Code[] values;
        Bind[] binds;

        private class Bind extends BindRef
                implements Binder, CaptureWrapper {
            boolean mutable;
            boolean used;
            int var;
            int index;

            public BindRef getRef() {
                used = true;
                return this;
            }

            public CaptureWrapper capture() {
                return mutable ? this : null;
            }

            public boolean assign() {
                return mutable;
            }

            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, var);
            }

            public void genPreGet(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, var);
            }

            public void genGet(Ctx ctx) {
                ctx.intConst(index);
                ctx.m.visitInsn(AALOAD);
            }

            public void genSet(Ctx ctx, Code value) {
                ctx.intConst(index);
                value.gen(ctx);
                ctx.m.visitInsn(AASTORE);
            }

            public Object captureIdentity() {
                return StructConstructor.this;
            }

            public String captureType() {
                return "[Ljava/lang/Object;";
            }
        }

        StructConstructor(String[] names, Code[] values) {
            this.names = names;
            this.values = values;
            binds = new Bind[names.length];
        }

        Binder bind(int num, Code code, boolean mutable) {
            Bind bind = new Bind();
            bind.type = code.type;
            bind.binder = bind;
            bind.mutable = mutable;
            binds[num] = bind;
            return bind;
        }

        void gen(Ctx ctx) {
            int arrayVar = -1;
            for (int i = 0; i < binds.length; ++i) {
                if (binds[i] != null) {
                    if (binds[i].used && !binds[i].mutable) {
                        ((Function) values[i]).prepareGen(ctx);
                        ctx.m.visitVarInsn(ASTORE,
                            binds[i].var = ctx.localVarCount++);
                    } else {
                        if (arrayVar == -1) {
                            arrayVar = ctx.localVarCount++;
                        }
                        binds[i].index = i * 2 + 1;
                        binds[i].var = arrayVar;
                        binds[i] = null;
                    }
                }
            }
            ctx.m.visitTypeInsn(NEW, "yeti/lang/Struct");
            ctx.m.visitInsn(DUP);
            ctx.intConst(names.length * 2);
            ctx.m.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            if (arrayVar != -1) {
                ctx.m.visitVarInsn(ASTORE, arrayVar);
            }
            for (int i = 0, cnt = names.length - 1; i <= cnt; ++i) {
                if (arrayVar != -1) {
                    ctx.m.visitVarInsn(ALOAD, arrayVar);
                } else {
                    ctx.m.visitInsn(DUP);
                }
                ctx.intConst(i * 2);
                ctx.m.visitLdcInsn(names[i]);
                ctx.m.visitInsn(AASTORE);
                if (arrayVar != -1) {
                    ctx.m.visitVarInsn(ALOAD, arrayVar);
                } else {
                    ctx.m.visitInsn(DUP);
                }
                ctx.intConst(i * 2 + 1);
                if (binds[i] != null) {
                    binds[i].gen(ctx);
                    ((Function) values[i]).finishGen(ctx);
                } else {
                    values[i].gen(ctx);
                }
                ctx.m.visitInsn(AASTORE);
            }
            if (arrayVar != -1) {
                ctx.m.visitVarInsn(ALOAD, arrayVar);
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
        Code[] items;

        ListConstructor(YetiType.Type type, Code[] items) {
            this.type = type;
            this.items = items;
        }

        void gen(Ctx ctx) {
            if (items.length == 0) {
                ctx.m.visitInsn(ACONST_NULL);
                return;
            }
            for (int i = 0; i < items.length; ++i) {
                ctx.m.visitTypeInsn(NEW, "yeti/lang/LList");
                ctx.m.visitInsn(DUP);
                items[i].gen(ctx);
            }
            ctx.m.visitInsn(ACONST_NULL);
            for (int i = items.length; --i >= 0;) {
                ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/LList",
                    "<init>", "(Ljava/lang/Object;Lyeti/lang/AList;)V");
            }
        }
    }


    class CoreFun extends BindRef implements Binder {
        String field;

        CoreFun(YetiType.Type type, String field) {
            this.type = type;
            this.field = field;
            binder = this;
        }

        public BindRef getRef() {
            return new CoreFun(type, field);
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core", field,
                                 "Lyeti/lang/Fun;");
        }
    }

    abstract class BinOpRef extends BindRef {
        Code apply(final Code arg1, YetiType.Type res) {
            Code c = new Code() {
                Code apply(final Code arg2, YetiType.Type res) {
                    Code code = new Code() {
                        void gen(Ctx ctx) {
                            binGen(ctx, arg1, arg2);
                        }
                    };
                    code.type = res;
                    return code;
                }

                void gen(Ctx ctx) {
                    throw new UnsupportedOperationException(
                        "BinOpRef: " + BinOpRef.this.getClass() + ".gen()!");
                }
            };
            c.type = res;
            return c;
        }

        void gen(Ctx ctx) {
            throw new UnsupportedOperationException(
                        "BinOpRef: " + this.getClass() + ".gen()");
        }

        abstract void binGen(Ctx ctx, Code arg1, Code arg2);
    }

    class ArithOpFun extends BinOpRef implements Binder {
        String method;

        public ArithOpFun(String method, YetiType.Type type) {
            this.type = type;
            this.method = method;
        }

        public BindRef getRef() {
            return this; // XXX should copy for type?
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            arg1.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            arg2.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
    }

    class CompareFun extends BinOpRef {
        int op;

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            arg1.gen(ctx);
            arg2.gen(ctx);
            if (op == IFEQ || op == IFNE) {
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                      "equals", "(Ljava/lang/Object;)Z");
            } else {
                ctx.m.visitMethodInsn(INVOKEINTERFACE, "java/lang/Comparable",
                                      "compareTo", "(Ljava/lang/Object;)I");
            }
            Label label = new Label(), end = new Label();
            ctx.m.visitJumpInsn(op, label);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                                 "FALSE", "Ljava/lang/Boolean;");
            ctx.m.visitJumpInsn(GOTO, end);
            ctx.m.visitLabel(label);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                                 "TRUE", "Ljava/lang/Boolean;");
            ctx.m.visitLabel(end);
        }
    }

    class Compare implements Binder {
        YetiType.Type type;
        int op;

        public Compare(YetiType.Type type, int op) {
            this.op = op;
            this.type = type;
        }

        public BindRef getRef() {
            CompareFun c = new CompareFun();
            c.binder = this;
            c.type = type;
            c.op = op;
            return c;
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

