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

import org.objectweb.asm.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import yeti.lang.Num;
import yeti.lang.RatNum;
import yeti.lang.IntNum;
import yeti.lang.BigNum;

interface YetiCode {
    int COND_EQ  = 0;
    int COND_NOT = 1;
    int COND_LT  = 2;
    int COND_GT  = 4;
    int COND_LE  = COND_NOT | COND_GT;
    int COND_GE  = COND_NOT | COND_LT;

    ThreadLocal currentCompileCtx = new ThreadLocal();

    class CompileCtx implements Opcodes {
        private CodeWriter writer;
        private SourceReader reader;
        Map classes = new HashMap();
        Map types = new HashMap();

        CompileCtx(SourceReader reader, CodeWriter writer) {
            this.reader = reader;
            this.writer = writer;
        }

        private void generateModuleFields(Map fields, Ctx ctx) {
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
            for (Iterator i = fields.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                String name = (String) entry.getKey();
                String type = Code.javaType((YetiType.Type) entry.getValue());
                String descr = 'L' + type + ';';
                ctx.cw.visitField(ACC_PUBLIC | ACC_STATIC, name,
                        descr, null, null).visitEnd();
                ctx.m.visitInsn(DUP);
                ctx.m.visitLdcInsn(name);
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                    "get", "(Ljava/lang/String;)Ljava/lang/Object;");
                ctx.m.visitTypeInsn(CHECKCAST, type);
                ctx.m.visitFieldInsn(PUTSTATIC, ctx.className,
                    name, descr);
            }
        }

        String compile(String srcName, int flags) throws Exception {
            char[] src = reader.getSource(srcName);
            int dot = srcName.lastIndexOf('.');
            String className = dot < 0 ? srcName : srcName.substring(0, dot);
            compile(srcName, className, src, flags);
            return className;
        }

        void compile(String sourceName, String name, char[] code, int flags) {
            if (classes.containsKey(name)) {
                throw new RuntimeException(classes.get(name) == null
                    ? "Circular module dependency: " + name
                    : "Duplicate module name: " + name);
            }
            classes.put(name, null);
            boolean module = (flags & YetiC.CF_COMPILE_MODULE) != 0;
            RootClosure codeTree;
            Object oldCompileCtx = currentCompileCtx.get();
            currentCompileCtx.set(this);
            try {
                codeTree = YetiType.toCode(sourceName, code, flags);
            } finally {
                currentCompileCtx.set(oldCompileCtx);
            }
            if (codeTree.moduleName != null) {
                name = codeTree.moduleName;
                module = true;
            }
            Ctx ctx = new Ctx(this, sourceName, null, null)
                .newClass(ACC_PUBLIC, name, null);
            if (module) {
                ctx.cw.visitAttribute(new YetiTypeAttr(codeTree.type));
                ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC, "eval",
                                    "()Ljava/lang/Object;");
                codeTree.gen(ctx);
                if (codeTree.type.type == YetiType.STRUCT) {
                    generateModuleFields(codeTree.type.finalMembers, ctx);
                }
                ctx.m.visitInsn(ARETURN);
                types.put(name, codeTree.type);
            } else {
                ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC, "main",
                                    "([Ljava/lang/String;)V");
                ctx.localVarCount++;
                codeTree.gen(ctx);
                ctx.m.visitInsn(POP);
                ctx.m.visitInsn(RETURN);
            }
            ctx.closeMethod();
        }

        void write() throws Exception {
            Iterator i = classes.values().iterator();
            while (i.hasNext()) {
                Ctx c = (Ctx) i.next();
                writer.writeClass(c.className + ".class", c.cw.toByteArray());
            }
        }
    }

    class Ctx implements Opcodes {
        CompileCtx compilation;
        String sourceName;
        String className;
        ClassWriter cw;
        MethodVisitor m;
        int localVarCount;
        int fieldCounter;
        int lastLine;

        Ctx(CompileCtx compilation, String sourceName,
                ClassWriter writer, String className) {
            this.compilation = compilation;
            this.sourceName = sourceName;
            this.cw = writer;
            this.className = className;
        }

        Ctx newClass(int flags, String name, String extend) {
            Ctx ctx = new Ctx(compilation, sourceName,
                    new ClassWriter(ClassWriter.COMPUTE_MAXS), name);
            ctx.cw.visit(V1_2, flags, name, null,
                    extend == null ? "java/lang/Object" : extend, null);
            ctx.cw.visitSource(sourceName, null);
            if (compilation.classes.put(name, ctx) != null) {
                throw new IllegalStateException("Duplicate class: " + name);
            }
            return ctx;
        }

        Ctx newMethod(int flags, String name, String type) {
            Ctx ctx = new Ctx(compilation, sourceName, cw, className);
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

        void visitLine(int line) {
            if (line != 0 && lastLine != line) {
                Label label = new Label();
                m.visitLabel(label);
                m.visitLineNumber(line, label);
                lastLine = line;
            }
        }
    }

    abstract class Code implements Opcodes {
        YetiType.Type type;
        boolean ignoreValue;
        boolean polymorph;

        /**
         * Generates into ctx a bytecode that (when executed in the JVM)
         * results in a value pushed into stack.
         * That value is of course the value of that code snippet
         * after evaluation.
         */
        abstract void gen(Ctx ctx);

        // Some "functions" may have special kinds of apply
        Code apply(Code arg, YetiType.Type res, int line) {
            return new Apply(res, this, arg, line);
        }

        // Not used currently. Should allow some custom behaviour
        // on binding (possibly useful for inline-optimisations).
        BindRef bindRef() {
            return null;
        }

        // Not used currently. Tells to code snippet, if it's value will be
        // ignored. Probably should be deleted together with that flag.
        void ignoreValue() {
            ignoreValue = true;
        }

        // When the code is a lvalue, then this method returns code that
        // performs the lvalue assigment of the value given as argument.
        Code assign(Code value) {
            return null;
        }

        // Boolean codes have ability to generate jumps.
        void genIf(Ctx ctx, Label to, boolean ifTrue) {
            gen(ctx);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "TRUE", "Ljava/lang/Boolean;");
            ctx.m.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
        }

        // Used to tell that this code is at tail position in a function.
        // Useful for doing tail call optimisations.
        void markTail() {
        }

        // Comparision operators use this for some optimisation.
        boolean isEmptyList() {
            return false;
        }

        static final String javaType(YetiType.Type t) {
            switch (t.deref().type) {
                case YetiType.STR: return "java/lang/String";
                case YetiType.NUM: return "yeti/lang/Num";
                case YetiType.CHAR: return "java/lang/Character";
                case YetiType.FUN: return "yeti/lang/Fun";
                case YetiType.STRUCT: return "yeti/lang/Struct";
                case YetiType.VARIANT: return "yeti/lang/Tag";
            }
            return "Ljava/lang/Object;";
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

    interface DirectBind {
    }

    class StaticRef extends BindRef implements DirectBind {
        private String className;
        private String name;
        private int line;
       
        StaticRef(String className, String fieldName, YetiType.Type type,
                  Binder binder, boolean polymorph, int line) {
            this.type = type;
            this.binder = binder;
            this.className = className;
            this.name = fieldName;
            this.polymorph = polymorph;
            this.line = line;
        }
        
        void gen(Ctx ctx) {
            ctx.visitLine(line);
            ctx.m.visitFieldInsn(GETSTATIC, className, name,
                                 'L' + javaType(type) + ';');
        }
    }

    class NumericConstant extends Code {
        Num num;

        NumericConstant(Num num) {
            type = YetiType.NUM_TYPE;
            this.num = num;
        }

        void gen(Ctx ctx) {
            if (num instanceof RatNum) {
                ctx.m.visitTypeInsn(NEW, "yeti/lang/RatNum");
                ctx.m.visitInsn(DUP);
                RatNum rat = ((RatNum) num).reduce();
                ctx.intConst(rat.numerator());
                ctx.intConst(rat.denominator());
                ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/RatNum",
                                      "<init>", "(II)V");
                return;
            }
            String type, sig;
            Object val;
            if (num instanceof IntNum) {
                type = "yeti/lang/IntNum";
                val = new Long(num.longValue());
                sig = "(J)V";
            } else if (num instanceof BigNum) {
                type = "yeti/lang/BigNum";
                val = num.toString();
                sig = "(Ljava/lang/String;)V";
            } else {
                type = "yeti/lang/FloatNum";
                val = new Double(num.doubleValue());
                sig = "(D)V";
            }
            ctx.m.visitTypeInsn(NEW, type);
            ctx.m.visitInsn(DUP);
            ctx.m.visitLdcInsn(val);
            ctx.m.visitMethodInsn(INVOKESPECIAL, type, "<init>", sig);
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

        public BindRef getRef(int line) {
            return this;
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    val ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
        }

        void genIf(Ctx ctx, Label to, boolean ifTrue) {
            if (val == ifTrue) {
                ctx.m.visitJumpInsn(GOTO, to);
            }
        }
    }

    class ConcatStrings extends Code {
        Code[] param;

        ConcatStrings(Code[] param) {
            type = YetiType.STR_TYPE;
            this.param = param;
        }

        void gen(Ctx ctx) {
            if (param.length == 1) {
                param[0].gen(ctx);
                if (param[0].type.deref().type != YetiType.STR) {
                    ctx.m.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
                }
                return;
            }
            ctx.intConst(param.length);
            ctx.m.visitTypeInsn(ANEWARRAY, "java/lang/String");
            for (int i = 0; i < param.length; ++i) {
                ctx.m.visitInsn(DUP);
                ctx.intConst(i);
                param[i].gen(ctx);
                if (param[i].type.deref().type != YetiType.STR) {
                    ctx.m.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                        "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
                }
                ctx.m.visitInsn(AASTORE);
            }
            ctx.m.visitMethodInsn(INVOKESTATIC, "yeti/lang/Core",
                "concat", "([Ljava/lang/String;)Ljava/lang/String;");
        }
    }

    interface Closure {
        // Closures "wrap" references to the outside world.
        BindRef refProxy(BindRef code);
        void addVar(BindExpr binder);
    }

    abstract class CaptureRef extends BindRef {
        Function capturer;
        BindRef ref;
        Binder[] args;

        class SelfApply extends Apply {
            boolean tail;
            int depth;

            SelfApply(YetiType.Type type, Code f, Code arg,
                      int line, int depth) {
                super(type, f, arg, line);
                this.depth = depth;
            }

            void gen(Ctx ctx) {
                if (!tail || depth != 0 || capturer.varArgs != args ||
                                           capturer.restart == null) {
                    super.gen(ctx);
                    return;
                }
                System.err.println("Tailcall");
                // TODO set all args involved...
                arg.gen(ctx);
                ctx.m.visitVarInsn(ASTORE, 1);
                ctx.m.visitJumpInsn(GOTO, capturer.restart);
            }

            void markTail() {
                tail = true;
            }

            Code apply(Code arg, YetiType.Type res, int line) {
                if (depth > 0) {
                    return new SelfApply(res, this, arg, line, depth - 1);
                }
                if (capturer.varArgs != null) {
                    capturer.varArgs = args;
                }
                return new Apply(res, this, arg, line);
            }
        }

        Code apply(Code arg, YetiType.Type res, int line) {
            if (args != null) {
                return new SelfApply(res, this, arg, line, args.length);
            }
            int n = 0;
            for (Function f = capturer; f != null; ++n, f = f.outer) {
                if (f.selfBind == ref.binder) {
                    System.err.println("Discovered self-apply");
                    args = new Binder[n];
                    f = capturer.outer;
                    for (int i = 0; i < n; ++i, f = f.outer) {
                        args[i] = f;
                    }
                    return new SelfApply(res, this, arg, line, n);
                }
            }
            return new Apply(res, this, arg, line);
        }
    }

    class Capture extends CaptureRef implements CaptureWrapper {
        String id;
        Capture next;
        CaptureWrapper wrapper;
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
        private CaptureRef selfRef;
        Label restart;
        Function outer;
        Binder[] varArgs;

        final BindRef arg = new BindRef() {
            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, 1);
            }
        };

        Function(YetiType.Type type) {
            this.type = type;
            arg.binder = this;
        }

        public BindRef getRef(int line) {
            return arg;
        }

        void setBody(Code body) {
            this.body = body;
            if (body instanceof Function) {
                ((Function) body).outer = this;
            }
        }

        // When function body refers to bindings outside of it,
        // at each closure border on the way out (to the binding),
        // a refProxy (of the ending closure) is called, possibly
        // transforming the BindRef.
        public BindRef refProxy(BindRef code) {
            if (code instanceof DirectBind) {
                return code;
            }
            if (selfBind == code.binder && !code.assign()) {
                if (selfRef == null) {
                    selfRef = new CaptureRef() {
                        void gen(Ctx ctx) {
                            ctx.m.visitVarInsn(ALOAD, 0);
                        }
                    };
                    selfRef.binder = selfBind;
                    selfRef.type = code.type;
                    selfRef.ref = code;
                    selfRef.capturer = this;
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
            c.polymorph = code.polymorph;
            c.ref = code;
            c.wrapper = code.capture();
            c.next = captures;
            c.capturer = this;
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
            apply.m.visitLabel(restart = new Label());
            body.gen(apply);
            restart = null;
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
        String moduleName;

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
        int line;

        Apply(YetiType.Type res, Code fun, Code arg, int line) {
            type = res;
            this.fun = fun;
            this.arg = arg;
            this.line = line;
        }

        void gen(Ctx ctx) {
            fun.gen(ctx);
            // XXX this cast could be optimised away sometimes
            //  - when the fun really is Fun by java types
            ctx.visitLine(line);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
            arg.gen(ctx);
            ctx.visitLine(line);
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

        Code apply(Code arg, YetiType.Type res, int line) {
            Code apply = new Apply(res, this, arg, line);
            apply.polymorph = arg.polymorph;
            return apply;
        }
    }

    abstract class SelectMember extends Code {
        private boolean assigned = false;
        Code st;
        String name;
        int line;

        SelectMember(YetiType.Type type, Code st, String name, int line,
                     boolean polymorph) {
            this.type = type;
            this.polymorph = polymorph;
            this.st = st;
            this.name = name;
            this.line = line;
        }

        void gen(Ctx ctx) {
            st.gen(ctx);
            ctx.visitLine(line);
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
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
                    ctx.m.visitLdcInsn(name);
                    setValue.gen(ctx);
                    ctx.visitLine(line);
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
        int line;

        KeyRefExpr(YetiType.Type type, Code val, Code key, int line) {
            this.type = type;
            this.val = val;
            this.key = key;
            this.line = line;
        }

        void gen(Ctx ctx) {
            val.gen(ctx);
            key.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                    "vget", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }

        Code assign(final Code setValue) {
            return new Code() {
                void gen(Ctx ctx) {
                    val.gen(ctx);
                    key.gen(ctx);
                    setValue.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                            "put", "(Ljava/lang/Object;Ljava/lang/Object;)" +
                            "Ljava/lang/Object;");
                }
            };
        }
    }

    class ConditionalExpr extends Code {
        Code[][] choices;

        ConditionalExpr(YetiType.Type type, Code[][] choices, boolean poly) {
            this.type = type;
            this.choices = choices;
            this.polymorph = poly;
        }

        void gen(Ctx ctx) {
            Label end = new Label();
            for (int i = 0, last = choices.length - 1; i <= last; ++i) {
                Label jmpNext = i < last ? new Label() : end;
                if (choices[i].length == 2) {
                    choices[i][1].genIf(ctx, jmpNext, false); // condition
                    choices[i][0].gen(ctx); // body
                    ctx.m.visitJumpInsn(GOTO, end);
                } else {
                    choices[i][0].gen(ctx);
                }
                ctx.m.visitLabel(jmpNext);
            }
        }

        void markTail() {
            for (int i = choices.length; --i >= 0;) {
                choices[i][0].markTail();
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

        void markTail() {
            result.markTail();
        }
    }

    interface Binder {
        BindRef getRef(int line);
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
        boolean used;

        BindExpr(Code expr, boolean var) {
            super(expr);
            this.var = var;
        }

        void setMVarId(Closure closure, int arrayId, int index) {
            this.closure = closure;
            mvar = arrayId;
            id = index;
        }

        public BindRef getRef(int line) {
            used = true;
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
            res.polymorph = !var && st.polymorph;
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

    class LoadModule extends Code {
        String moduleName;

        LoadModule(String moduleName, YetiType.Type type) {
            this.type = type;
            this.moduleName = moduleName;
            polymorph = true;
        }

        void gen(Ctx ctx) {
            ctx.m.visitMethodInsn(INVOKESTATIC, moduleName,
                "eval", "()Ljava/lang/Object;");
        }

        Binder bindField(final String name, final YetiType.Type type) {
            return new Binder() {
                public BindRef getRef(int line) {
                    return new StaticRef(moduleName, name, type,
                                         this, true, line);
                }
            };
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

                public BindRef getRef(int line) {
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
            public BindRef getRef(int line) {
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

        void markTail() {
            for (int i = choices.size(); --i >= 0;) {
                ((Variant) choices.get(i)).expr.markTail();
            }
        }
    }

    class ListConstructor extends Code {
        Code[] items;

        ListConstructor(Code[] items) {
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

        boolean isEmptyList() {
            return items.length == 0;
        }
    }

    class MapConstructor extends Code {
        Code[] keyItems;
        Code[] items;

        MapConstructor(Code[] keyItems, Code[] items) {
            this.keyItems = keyItems;
            this.items = items;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/Hash");
            ctx.m.visitInsn(DUP);
            if (items.length > 16) {
                ctx.intConst(items.length);
                ctx.m.visitMethodInsn(INVOKESPECIAL,
                        "yeti/lang/Hash", "<init>", "(I)V");
            } else {
                ctx.m.visitMethodInsn(INVOKESPECIAL,
                        "yeti/lang/Hash", "<init>", "()V");
            }
            for (int i = 0; i < items.length; ++i) {
                ctx.m.visitInsn(DUP);
                keyItems[i].gen(ctx);
                items[i].gen(ctx);
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash", "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                ctx.m.visitInsn(POP);
            }
        }

        boolean isEmptyList() {
            return items.length == 0;
        }
    }

    class CoreFun implements Binder {
        private YetiType.Type type;
        private String name;

        CoreFun(YetiType.Type type, String field) {
            this.type = type;
            name = field;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", name,
                                 type, this, true, line);
        }
    }

    class Ignore implements Binder {
        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/Core", "IGNORE",
                        YetiType.A_TO_UNIT, this, true, line) {
                Code apply(final Code arg1, YetiType.Type res, int line) {
                    return new Code() {
                        { type = YetiType.UNIT_TYPE; }

                        void gen(Ctx ctx) {
                            arg1.gen(ctx);
                        }
                    };
                }
            };
        }
    }

    abstract class BinOpRef extends BindRef implements DirectBind {
        boolean markTail2;

        Code apply(final Code arg1, final YetiType.Type res1, int line) {
            return new Code() {
                { type = res1; }

                Code apply(final Code arg2, final YetiType.Type res, int line) {
                    return new Code() {
                        { type = res; }

                        void gen(Ctx ctx) {
                            binGen(ctx, arg1, arg2);
                        }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            binGenIf(ctx, arg1, arg2, to, ifTrue);
                        }

                        void markTail() {
                            if (markTail2) {
                                arg2.markTail();
                            }
                        }
                    };
                }

                void gen(Ctx ctx) {
                    throw new UnsupportedOperationException(
                            "BinOpRef: " + BinOpRef.this.getClass() + ".gen()!");
                }
            };
        }

        void gen(Ctx ctx) {
            throw new UnsupportedOperationException(
                    "BinOpRef: " + this.getClass() + ".gen()");
        }

        abstract void binGen(Ctx ctx, Code arg1, Code arg2);

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            throw new UnsupportedOperationException("binGenIf");
        }
    }

    class ArithOpFun extends BinOpRef implements Binder {
        String method;

        public ArithOpFun(String method, YetiType.Type type) {
            this.type = type;
            this.method = method;
            binder = this;
        }

        public BindRef getRef(int line) {
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

    abstract class BoolBinOp extends BinOpRef {
        void binGen(Ctx ctx, Code arg1, Code arg2) {
            Label label = new Label(), end = new Label();
            binGenIf(ctx, arg1, arg2, label, false);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "TRUE", "Ljava/lang/Boolean;");
            ctx.m.visitJumpInsn(GOTO, end);
            ctx.m.visitLabel(label);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "FALSE", "Ljava/lang/Boolean;");
            ctx.m.visitLabel(end);
        }
    }

    class CompareFun extends BoolBinOp {
        static final int[] OPS = { IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE };
        int op;
        int line;

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            YetiType.Type t = arg1.type.deref();
            int op = this.op;
            if (!ifTrue) {
                op ^= COND_NOT;
            }
            Label nojmp = null;
            if (t.type == YetiType.VAR || t.type == YetiType.MAP &&
                    t.param[2] == YetiType.LIST_TYPE &&
                    t.param[1] != YetiType.NUM_TYPE) {
                Label nonull = new Label();
                nojmp = new Label();
                arg2.gen(ctx);
                arg1.gen(ctx); // 2-1
                ctx.visitLine(line);
                ctx.m.visitInsn(DUP); // 2-1-1
                ctx.m.visitJumpInsn(IFNONNULL, nonull); // 2-1
                // reach here, when 1 was null
                if (op == COND_GT || op == COND_LE ||
                    arg2.isEmptyList() && (op == COND_EQ || op == COND_NOT)) {
                    // null is never greater and always less or equal
                    ctx.m.visitInsn(POP2);
                    ctx.m.visitJumpInsn(GOTO,
                        op == COND_LE || op == COND_EQ ? to : nojmp);
                } else {
                    ctx.m.visitInsn(POP); // 2
                    ctx.m.visitJumpInsn(op == COND_EQ || op == COND_GE
                                        ? IFNULL : IFNONNULL, to);
                    ctx.m.visitJumpInsn(GOTO, nojmp);
                }
                ctx.m.visitLabel(nonull);
                ctx.m.visitInsn(SWAP); // 1-2
            } else {
                arg1.gen(ctx);
                arg2.gen(ctx);
                ctx.visitLine(line);
            }
            if ((op & (COND_LT | COND_GT)) == 0) {
                op ^= COND_NOT;
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                      "equals", "(Ljava/lang/Object;)Z");
            } else {
                ctx.m.visitMethodInsn(INVOKEINTERFACE, "java/lang/Comparable",
                                      "compareTo", "(Ljava/lang/Object;)I");
            }
            ctx.m.visitJumpInsn(OPS[op], to);
            if (nojmp != null) {
                ctx.m.visitLabel(nojmp);
            }
        }
    }

    class Compare implements Binder {
        YetiType.Type type;
        int op;

        public Compare(YetiType.Type type, int op) {
            this.op = op;
            this.type = type;
        }

        public BindRef getRef(int line) {
            CompareFun c = new CompareFun();
            c.binder = this;
            c.type = type;
            c.op = op;
            c.polymorph = true;
            c.line = line;
            return c;
        }
    }

    class BoolOpFun extends BoolBinOp implements Binder {
        boolean orOp;

        BoolOpFun(boolean orOp) {
            this.type = YetiType.BOOLOP_TYPE;
            this.orOp = orOp;
            binder = this;
            markTail2 = true;
        }

        public BindRef getRef(int line) {
            return this;
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            if (arg2 instanceof CompareFun) {
                super.binGen(ctx, arg1, arg2);
            } else {
                Label label = new Label(), end = new Label();
                arg1.genIf(ctx, label, orOp);
                arg2.gen(ctx);
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(label);
                ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                        orOp ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
                ctx.m.visitLabel(end);
            }
        }

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                      Label to, boolean ifTrue) {
            if (orOp == ifTrue) {
                arg1.genIf(ctx, to, orOp);
                arg2.genIf(ctx, to, orOp);
            } else {
                Label noJmp = new Label();
                arg1.genIf(ctx, noJmp, orOp);
                arg2.genIf(ctx, to, !orOp);
                ctx.m.visitLabel(noJmp);
            }
        }
    }

    class Cons implements Binder {
        public BindRef getRef(final int line) {
            return new BinOpRef() {
                {
                    type = YetiType.CONS_TYPE;
                    binder = Cons.this;
                    polymorph = true;
                }

                void binGen(Ctx ctx, Code arg1, Code arg2) {
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/LList");
                    ctx.m.visitInsn(DUP);
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/LList",
                        "<init>", "(Ljava/lang/Object;Lyeti/lang/AList;)V");
                }
            };
        }
    }
}
