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
    ThreadLocal currentCompileCtx = new ThreadLocal();

    class Constants implements Opcodes {
        private Map constants = new HashMap();
        private Ctx sb;
        String sourceName;
        Ctx ctx;

        void registerConstant(Object key, final Code code, Ctx ctx_) {
            final String descr = 'L' + Code.javaType(code.type) + ';';
            String name = (String) constants.get(key);
            if (name == null) {
                if (sb == null) {
                    sb = ctx.newMethod(ACC_STATIC, "<clinit>", "()V");
                }
                name = "_".concat(Integer.toString(ctx.fieldCounter++));
                ctx.cw.visitField(ACC_STATIC | ACC_FINAL, name, descr,
                                  null, null).visitEnd();
                code.gen(sb);
                sb.m.visitFieldInsn(PUTSTATIC, ctx.className, name, descr);
                constants.put(key, name);
            }
            final String fieldName = name;
            ctx_.m.visitFieldInsn(GETSTATIC, ctx.className, fieldName, descr);
        }

        void close() {
            if (sb != null) {
                sb.m.visitInsn(RETURN);
                sb.closeMethod();
            }
        }
    }

    class CompileCtx implements Opcodes {
        private CodeWriter writer;
        private SourceReader reader;
        private String[] preload;
        ClassFinder classPath;
        Map classes = new HashMap();
        Map types = new HashMap();

        CompileCtx(SourceReader reader, CodeWriter writer,
                   String[] preload, ClassFinder finder) {
            this.reader = reader;
            this.writer = writer;
            this.preload = preload;
            this.classPath = finder;
        }

        private void generateModuleFields(Map fields, Ctx ctx) {
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
            for (Iterator i = fields.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                String name = (String) entry.getKey();
                String jname = Code.mangle(name);
                String type = Code.javaType((YetiType.Type) entry.getValue());
                String descr = 'L' + type + ';';
                ctx.cw.visitField(ACC_PUBLIC | ACC_STATIC, jname,
                        descr, null, null).visitEnd();
                ctx.m.visitInsn(DUP);
                ctx.m.visitLdcInsn(name);
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                    "get", "(Ljava/lang/String;)Ljava/lang/Object;");
                ctx.m.visitTypeInsn(CHECKCAST, type);
                ctx.m.visitFieldInsn(PUTSTATIC, ctx.className, jname, descr);
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
            boolean module = (flags & YetiC.CF_COMPILE_MODULE) != 0;
            RootClosure codeTree;
            Object oldCompileCtx = currentCompileCtx.get();
            currentCompileCtx.set(this);
            try {
                codeTree = YetiAnalyzer.toCode(sourceName, name, code, flags,
                                               classes, preload);
            } finally {
                currentCompileCtx.set(oldCompileCtx);
            }
            if (codeTree.moduleName != null) {
                name = codeTree.moduleName;
            }
            module = module || codeTree.isModule;
            Constants constants = new Constants();
            constants.sourceName = sourceName;
            Ctx ctx = new Ctx(this, constants, null, null)
                .newClass(ACC_PUBLIC, name, null);
            constants.ctx = ctx;
            if (module) {
                ctx.cw.visitAttribute(new YetiTypeAttr(codeTree.type));
                ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC, "$",
                                  "Ljava/lang/Object;", null, null).visitEnd();
                ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC, "_$", "Z",
                                  null, Boolean.FALSE);
                ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED,
                                    "eval", "()Ljava/lang/Object;");
                ctx.m.visitFieldInsn(GETSTATIC, name, "_$", "Z");
                Label eval = new Label();
                ctx.m.visitJumpInsn(IFEQ, eval);
                ctx.m.visitFieldInsn(GETSTATIC, name, "$",
                                     "Ljava/lang/Object;");
                ctx.m.visitInsn(ARETURN);
                ctx.m.visitLabel(eval);
                codeTree.gen(ctx);
                if (codeTree.type.type == YetiType.STRUCT) {
                    generateModuleFields(codeTree.type.finalMembers, ctx);
                }
                ctx.m.visitInsn(DUP);
                ctx.m.visitFieldInsn(PUTSTATIC, name, "$",
                                     "Ljava/lang/Object;");
                ctx.intConst(1);
                ctx.m.visitFieldInsn(PUTSTATIC, name, "_$", "Z");
                ctx.m.visitInsn(ARETURN);
                types.put(name, codeTree.type);
            } else {
                ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC, "main",
                                    "([Ljava/lang/String;)V");
                ctx.localVarCount++;
                ctx.m.visitVarInsn(ALOAD, 0);
                ctx.m.visitMethodInsn(INVOKESTATIC, "yeti/lang/Core",
                                      "setArgv", "([Ljava/lang/String;)V");
                codeTree.gen(ctx);
                ctx.m.visitInsn(POP);
                ctx.m.visitInsn(RETURN);
            }
            ctx.closeMethod();
            constants.close();
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
        String className;
        ClassWriter cw;
        MethodVisitor m;
        Constants constants;
        int localVarCount;
        int fieldCounter;
        int methodCounter;
        int lastLine;

        Ctx(CompileCtx compilation, Constants constants,
                ClassWriter writer, String className) {
            this.compilation = compilation;
            this.constants = constants;
            this.cw = writer;
            this.className = className;
        }

        Ctx newClass(int flags, String name, String extend) {
            Ctx ctx = new Ctx(compilation, constants,
                    new ClassWriter(ClassWriter.COMPUTE_MAXS), name);
            ctx.cw.visit(V1_2, flags, name, null,
                    extend == null ? "java/lang/Object" : extend, null);
            ctx.cw.visitSource(constants.sourceName, null);
            if (compilation.classes.put(name, ctx) != null) {
                throw new IllegalStateException("Duplicate class: " + name);
            }
            return ctx;
        }

        Ctx newMethod(int flags, String name, String type) {
            Ctx ctx = new Ctx(compilation, constants, cw, className);
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

        void genBoolean(Label label) {
            m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "TRUE", "Ljava/lang/Boolean;");
            Label end = new Label();
            m.visitJumpInsn(GOTO, end);
            m.visitLabel(label);
            m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "FALSE", "Ljava/lang/Boolean;");
            m.visitLabel(end);
        }

        void constant(Object key, Code code) {
            constants.registerConstant(key, code, this);
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

        Code apply2nd(final Code arg2, final YetiType.Type t, int line) {
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/Bind2nd");
                    ctx.m.visitInsn(DUP);
                    Code.this.gen(ctx);
                    arg2.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Bind2nd",
                        "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
                }
            };
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

        boolean isIntNum() {    
            return false;
        }

        static final String javaType(YetiType.Type t) {
            t = t.deref();
            switch (t.type) {
                case YetiType.STR: return "java/lang/String";
                case YetiType.NUM: return "yeti/lang/Num";
                case YetiType.CHAR: return "java/lang/Character";
                case YetiType.FUN: return "yeti/lang/Fun";
                case YetiType.STRUCT: return "yeti/lang/Struct";
                case YetiType.VARIANT: return "yeti/lang/Tag";
                case YetiType.JAVA: return t.javaType.className();
            }
            return "java/lang/Object";
        }

        static final char[] mangle =
            "jQh$oBz  apCmds          cSlegqt".toCharArray();

        static final String mangle(String s) {
            char[] a = s.toCharArray();
            char[] to = new char[a.length * 2];
            int l = 0;
            for (int i = 0, cnt = a.length; i < cnt; ++i, ++l) {
                char c = a[i];
                if (c > ' ' && c < 'A' && (to[l + 1] = mangle[c - 33]) != ' ') {
                } else if (c == '^') {
                    to[l + 1] = 'v';
                } else if (c == '|') {
                    to[l + 1] = 'I';
                } else if (c == '~') {
                    to[l + 1] = '_';
                } else {
                    to[l] = c;
                    continue;
                }
                to[l++] = '$';
            }
            return new String(to, 0, l);
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
        int line;
       
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

        boolean isIntNum() {
            return num instanceof IntNum;
        }

        boolean genInt(Ctx ctx, boolean small) {
            if (!(num instanceof IntNum)) {
                return false;
            }
            long n = num.longValue();
            if (!small) {
                ctx.m.visitLdcInsn(new Long(n));
            } else if (n >= (long) Integer.MIN_VALUE &&
                       n <= (long) Integer.MAX_VALUE) {
                ctx.intConst((int) n);
            } else {
                return false;
            }
            return true;
        }

        private static final class Impl extends Code {
            String jtype, sig;
            Object val;

            void gen(Ctx ctx) {
                ctx.m.visitTypeInsn(NEW, jtype);
                ctx.m.visitInsn(DUP);
                ctx.m.visitLdcInsn(val);
                ctx.m.visitMethodInsn(INVOKESPECIAL, jtype, "<init>", sig);
            }
        }

        void gen(Ctx ctx) {
            if (num instanceof RatNum) {
                ctx.constant(num, new Code() {
                    { type = YetiType.NUM_TYPE; }
                    void gen(Ctx ctx) {
                        ctx.m.visitTypeInsn(NEW, "yeti/lang/RatNum");
                        ctx.m.visitInsn(DUP);
                        RatNum rat = ((RatNum) num).reduce();
                        ctx.intConst(rat.numerator());
                        ctx.intConst(rat.denominator());
                        ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/RatNum",
                                              "<init>", "(II)V");
                    }
                });
                return;
            }
            Impl v = new Impl();
            if (num instanceof IntNum) {
                v.jtype = "yeti/lang/IntNum";
                if (IntNum.__1.compareTo(num) <= 0 &&
                    IntNum._9.compareTo(num) >= 0) {
                    ctx.m.visitFieldInsn(GETSTATIC, v.jtype,
                        IntNum.__1.equals(num) ? "__1" :
                        IntNum.__2.equals(num) ? "__2" : "_" + num,
                        "Lyeti/lang/IntNum;");
                    return;
                }
                v.val = new Long(num.longValue());
                v.sig = "(J)V";
            } else if (num instanceof BigNum) {
                v.jtype = "yeti/lang/BigNum";
                v.val = num.toString();
                v.sig = "(Ljava/lang/String;)V";
            } else {
                v.jtype = "yeti/lang/FloatNum";
                v.val = new Double(num.doubleValue());
                v.sig = "(D)V";
            }
            v.type = YetiType.NUM_TYPE;
            ctx.constant(num, v);
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

    class NewExpr extends JavaExpr {
        NewExpr(JavaType.Method init, Code[] args, int line) {
            super(null, init, args, line);
            type = init.classType;
        }

        void gen(Ctx ctx) {
            String name = method.classType.javaType.className();
            ctx.m.visitTypeInsn(NEW, name);
            ctx.m.visitInsn(DUP);
            genCall(ctx, INVOKESPECIAL);
        }
    }

    class MethodCall extends JavaExpr {
        MethodCall(Code object, JavaType.Method method, Code[] args, int line) {
            super(object, method, args, line);
            type = method.convertedReturnType();
        }

        void gen(Ctx ctx) {
            if (object != null) {
                object.gen(ctx);
                ctx.m.visitTypeInsn(CHECKCAST,
                    method.classType.javaType.className());
            }
            genCall(ctx, object == null ? INVOKESTATIC :
                        method.classType.javaType.isInterface()
                            ? INVOKEINTERFACE : INVOKEVIRTUAL);
            convertValue(ctx, method.returnType);
        }
    }

    class Throw extends Code {
        Code throwable;

        Throw(Code throwable, YetiType.Type type) {
            this.type = type;
            this.throwable = throwable;
        }

        void gen(Ctx ctx) {
            throwable.gen(ctx);
            ctx.m.visitInsn(ATHROW);
        }
    }

    class ClassField extends JavaExpr {
        private JavaType.Field field;

        ClassField(Code object, JavaType.Field field, int line) {
            super(object, null, null, line);
            this.type = field.convertedType();
            this.field = field;
        }

        void gen(Ctx ctx) {
            if (object != null) {
                object.gen(ctx);
            }
            ctx.visitLine(line);
            String descr = JavaType.descriptionOf(field.type);
            char what = descr.charAt(0);
            if (object == null) {
            } else if (what == '[') {
                ctx.m.visitTypeInsn(CHECKCAST, descr);
            } else if (what == 'L') {
                ctx.m.visitTypeInsn(CHECKCAST, field.type.javaType.className());
            }
            ctx.m.visitFieldInsn(object == null ? GETSTATIC : GETFIELD,
                                 field.classType.javaType.className(),
                                 field.name, descr);
            convertValue(ctx, field.type);
        }

        Code assign(final Code setValue) {
            if ((field.access & ACC_FINAL) != 0) {
                return null;
            }
            return new Code() {
                void gen(Ctx ctx) {
                    if (object != null) {
                        object.gen(ctx);
                    }
                    genValue(ctx, setValue, field.type, line);
                    ctx.m.visitFieldInsn(object == null ? PUTSTATIC : PUTFIELD,
                                         field.classType.javaType.className(),
                                         field.name,
                                         JavaType.descriptionOf(field.type));
                    ctx.m.visitInsn(ACONST_NULL);
                }
            };
        }
    }

    class Cast extends Code {
        Code code;

        Cast(Code code, YetiType.Type type) {
            this.type = type;
            this.code = code;
        }

        void gen(Ctx ctx) {
            code.gen(ctx);
        }
    }

    // Since the stupid JVM discards local stack when catching exceptions,
    // try catch blocks have to be converted into fucking closures
    // (at least for the generic case).
    class TryCatch extends CapturingClosure {
        private List catches = new ArrayList();
        private int exVar;
        Code block;
        Code cleanup;

        class Catch extends BindRef implements Binder {
            Code handler;
            Label start = new Label();
            Label end;

            public BindRef getRef(int line) {
                return this;
            }

            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, exVar);
            }
        }

        void setBlock(Code block) {
            this.type = block.type;
            this.block = block;
        }

        public BindRef refProxy(BindRef code) {
            return code instanceof DirectBind ? code : captureRef(code);
        }

        Catch addCatch(YetiType.Type ex) {
            Catch c = new Catch();
            c.type = ex;
            catches.add(c);
            return c;
        }

        void gen(Ctx ctx) {
            StringBuffer sigb = new StringBuffer("(");
            int argc = 0;
            Capture prev = null;
        next_capture: // copy-paste from Function :(
            for (Capture c = captures; c != null; c = c.next) {
                Object identity = c.identity = c.captureIdentity();
                for (Capture i = captures; i != c; i = i.next) {
                    if (i.identity == identity) {
                        c.localVar = i.localVar; // copy old one's var
                        prev.next = c.next;
                        continue next_capture;
                    }
                }
                c.localVar = argc++;
                sigb.append(c.captureType());
                if (c.wrapper == null) {
                    c.ref.gen(ctx);
                } else {
                    c.wrapper.genPreGet(ctx);
                }
                prev = c;
            }
            sigb.append(")Ljava/lang/Object;");
            String sig = sigb.toString();
            String name = "_" + ctx.methodCounter++;
            ctx.m.visitMethodInsn(INVOKESTATIC, ctx.className, name, sig);
            Ctx mc = ctx.newMethod(ACC_PRIVATE | ACC_STATIC, name, sig);
            mc.localVarCount = argc;
            MethodVisitor m = mc.m;

            Label codeStart = new Label(), codeEnd = new Label();
            Label cleanupStart = cleanup == null ? null : new Label();
            Label cleanupEntry = cleanup == null ? null : new Label();
            int catchCount = catches.size();
            for (int i = 0; i < catchCount; ++i) {
                Catch c = (Catch) catches.get(i);
                m.visitTryCatchBlock(codeStart, codeEnd, c.start,
                                     c.type.javaType.className());
                if (cleanupStart != null) {
                    c.end = new Label();
                    m.visitTryCatchBlock(c.start, c.end, cleanupStart, null);
                }
            }
            genClosureInit(mc);
            int retVar = -1;
            if (cleanupStart != null) {
                retVar = mc.localVarCount++;
                m.visitTryCatchBlock(codeStart, codeEnd, cleanupStart, null);
                m.visitInsn(ACONST_NULL);
                m.visitVarInsn(ASTORE, retVar); // silence the JVM verifier...
            }
            m.visitLabel(codeStart);
            block.gen(mc);
            m.visitLabel(codeEnd);
            exVar = mc.localVarCount++;
            if (cleanupStart != null) {
                Label goThrow = new Label();
                m.visitLabel(cleanupEntry);
                m.visitVarInsn(ASTORE, retVar);
                m.visitInsn(ACONST_NULL);
                m.visitLabel(cleanupStart);
                m.visitVarInsn(ASTORE, exVar);
                cleanup.gen(mc);
                m.visitInsn(POP); // cleanup's null
                m.visitVarInsn(ALOAD, exVar);
                m.visitJumpInsn(IFNONNULL, goThrow);
                m.visitVarInsn(ALOAD, retVar);
                m.visitInsn(ARETURN);
                m.visitLabel(goThrow);
                m.visitVarInsn(ALOAD, exVar);
                m.visitInsn(ATHROW);
            } else {
                m.visitInsn(ARETURN);
            }
            for (int i = 0; i < catchCount; ++i) {
                Catch c = (Catch) catches.get(i);
                m.visitLabel(c.start);
                m.visitVarInsn(ASTORE, exVar);
                c.handler.gen(mc);
                if (c.end != null) {
                    m.visitLabel(c.end);
                    m.visitJumpInsn(GOTO, cleanupEntry);
                } else {
                    m.visitInsn(ARETURN);
                }
            }
            mc.closeMethod();
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
        Capture[] argCaptures;

        class SelfApply extends Apply {
            boolean tail;
            int depth;

            SelfApply(YetiType.Type type, Code f, Code arg,
                      int line, int depth) {
                super(type, f, arg, line);
                this.depth = depth;
            }

            void genArg(Ctx ctx, int i) {
                if (i > 0) {
                    ((SelfApply) fun).genArg(ctx, i - 1);
                }
                arg.gen(ctx);
            }

            void gen(Ctx ctx) {
                if (!tail || depth != 0 ||
                    capturer.argCaptures != argCaptures ||
                    capturer.restart == null) {
                    super.gen(ctx);
                    return;
                }
                genArg(ctx, argCaptures == null ? 0 : argCaptures.length);
                ctx.m.visitVarInsn(ASTORE, 1);
                if (argCaptures != null) {
                    for (int i = argCaptures.length; --i >= 0;) {
                        ctx.m.visitVarInsn(ASTORE, argCaptures[i].localVar);
                    }
                }
                ctx.m.visitJumpInsn(GOTO, capturer.restart);
            }

            void markTail() {
                tail = true;
            }

            Code apply(Code arg, YetiType.Type res, int line) {
                if (depth < 0) {
                    return new Apply(res, this, arg, line);
                }
                if (depth == 1) {
                    if (capturer.argCaptures == null) {
                        argCaptures = new Capture[args.length];
                        for (Capture c = capturer.captures; c != null;
                             c = c.next) {
                            for (int i = args.length; --i >= 0;) {
                                if (c.binder == args[i]) {
                                    argCaptures[i] = c;
                                    break;
                                }
                            }
                        }
                        capturer.argCaptures = argCaptures;
                    }
                }
                return new SelfApply(res, this, arg, line, depth - 1);
            }
        }

        Code apply(Code arg, YetiType.Type res, int line) {
            if (args != null) {
                return new SelfApply(res, this, arg, line, args.length);
            }
            int n = 0;
            for (Function f = capturer; f != null; ++n, f = f.outer) {
                if (f.selfBind == ref.binder) {
                    args = new Binder[n];
                    f = capturer.outer;
                    for (int i = n; --i >= 0; f = f.outer) {
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
        int localVar = -1; // -1 - use this - not copied
        boolean uncaptured;

        void gen(Ctx ctx) {
            if (uncaptured) {
                ref.gen(ctx);
                return;
            }
            genPreGet(ctx);
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
                    if (uncaptured) {
                        ref.assign(value).gen(ctx);
                    } else {
                        genPreGet(ctx);
                        wrapper.genSet(ctx, value);
                        ctx.m.visitInsn(ACONST_NULL);
                    }
                }
            };
        }

        public void genPreGet(Ctx ctx) {
            if (localVar == -1) {
                ctx.m.visitVarInsn(ALOAD, 0);
                ctx.m.visitFieldInsn(GETFIELD, ctx.className, id,
                    captureType());
            } else {
                ctx.m.visitVarInsn(ALOAD, localVar);
            }
        }

        public void genGet(Ctx ctx) {
            if (localVar == -1) {
                wrapper.genGet(ctx);
            }
        }

        public void genSet(Ctx ctx, Code value) {
            wrapper.genSet(ctx, value);
        }

        public CaptureWrapper capture() {
            if (uncaptured) {
                return ref.capture();
            }
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

    abstract class CapturingClosure extends AClosure {
        Capture captures;

        Capture captureRef(BindRef code) {
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
            captures = c;
            return c;
        }
    }

    class Function extends CapturingClosure implements Binder {
        private String name;
        Binder selfBind;
        Code body;
        String bindName;
        private CaptureRef selfRef;
        Label restart;
        Function outer;
        Capture[] argCaptures;
        private Code uncaptureArg;

        final BindRef arg = new BindRef() {
            void gen(Ctx ctx) {
                if (uncaptureArg != null) {
                    uncaptureArg.gen(ctx);
                } else {
                    ctx.m.visitVarInsn(ALOAD, 1);
                }
            }
        };

        Function(YetiType.Type type) {
            this.type = type;
            arg.binder = this;
        }

        public BindRef getRef(int line) {
            return arg;
        }

        // uncaptures captured variables if possible
        // useful for function inlineing, don't work with self-refs
        boolean uncapture(Code arg) {
            if (selfRef != null)
                return false;
            for (Capture c = captures; c != null; c = c.next) {
                c.uncaptured = true;
            }
            uncaptureArg = arg;
            return true;
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
            Capture c = captureRef(code);
            c.capturer = this;
            return c;
        }

        void prepareGen(Ctx ctx) {
            if (bindName == null) {
                bindName = "";
            }
            name = ctx.className + '$' + mangle(bindName);
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
            if (argCaptures != null) {
                for (int i = 0; i < argCaptures.length; ++i) {
                    argCaptures[i].gen(apply);
                    argCaptures[i].localVar = apply.localVarCount;
                    apply.m.visitVarInsn(ASTORE, apply.localVarCount++);
                }
            }
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
        String[] preload;
        String moduleName;
        boolean isModule;

        public BindRef refProxy(BindRef code) {
            return code;
        }

        void gen(Ctx ctx) {
            genClosureInit(ctx);
            for (int i = 0; i < preload.length; ++i) {
                if (!preload[i].equals(ctx.className)) {
                    ctx.m.visitMethodInsn(INVOKESTATIC, preload[i],
                        "eval", "()Ljava/lang/Object;");
                    ctx.m.visitInsn(POP);
                }
            }
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

        class SpecialArg extends Code {
            int var;

            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, var);
            }
        }

        void gen(Ctx ctx) {
            if (fun instanceof Function) {
                Function f = (Function) fun;
                SpecialArg arg_ = new SpecialArg();
                // inline direct calls
                // TODO: constants don't need a temp variable
                if (f.uncapture(arg_)) {
                    arg.gen(ctx);
                    arg_.var = ctx.localVarCount++;
                    ctx.m.visitVarInsn(ASTORE, arg_.var);
                    f.body.gen(ctx);
                    return;
                }
            }
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
            ctx.constant("TAG:".concat(name), new Code() {
                { type = VariantConstructor.this.type; }
                void gen(Ctx ctx) {
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/TagCon");
                    ctx.m.visitInsn(DUP);
                    ctx.m.visitLdcInsn(name);
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/TagCon",
                            "<init>", "(Ljava/lang/String;)V");
                }
            });
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

    class SelectMemberFun extends Code {
        String[] names;
        
        SelectMemberFun(YetiType.Type type, String[] names) {
            this.type = type;
            this.names = names;
            this.polymorph = true;
        }

        void gen(Ctx ctx) {
            StringBuffer buf = new StringBuffer("SELECTMEMBER");
            for (int i = 0; i < names.length; ++i) {
                buf.append(':');
                buf.append(names[i]);
            }
            ctx.constant(buf.toString(), new Code() {
                { type = SelectMemberFun.this.type; }
                void gen(Ctx ctx) {
                    if (names.length == 1) {
                        ctx.m.visitTypeInsn(NEW, "yeti/lang/Selector");
                        ctx.m.visitInsn(DUP);
                        ctx.m.visitLdcInsn(names[0]);
                        ctx.m.visitMethodInsn(INVOKESPECIAL,
                                "yeti/lang/Selector", "<init>",
                                "(Ljava/lang/String;)V");
                        return;
                    }
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/Selectors");
                    ctx.m.visitInsn(DUP);
                    ctx.intConst(names.length);
                    ctx.m.visitTypeInsn(ANEWARRAY, "java/lang/String");
                    for (int i = 0; i < names.length; ++i) {
                        ctx.m.visitInsn(DUP);
                        ctx.intConst(i);
                        ctx.m.visitLdcInsn(names[i]);
                        ctx.m.visitInsn(AASTORE);
                    }
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Selectors",
                                          "<init>", "([Ljava/lang/String;)V");
                }
            });
        }
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

        void genIf(Ctx ctx, Label to, boolean ifTrue) {
            Label end = new Label();
            for (int i = 0, last = choices.length - 1; i <= last; ++i) {
                Label jmpNext = i < last ? new Label() : end;
                if (choices[i].length == 2) {
                    choices[i][1].genIf(ctx, jmpNext, false); // condition
                    choices[i][0].genIf(ctx, to, ifTrue); // body
                    ctx.m.visitJumpInsn(GOTO, end);
                } else {
                    choices[i][0].genIf(ctx, to, ifTrue);
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

    class LoopExpr extends Code {
        Code cond, body;

        LoopExpr(Code cond, Code body) {
            this.type = YetiType.UNIT_TYPE;
            this.cond = cond;
            this.body = body;
        }

        void gen(Ctx ctx) {
            Label start = new Label();
            Label end = new Label();
            ctx.m.visitLabel(start);
            cond.genIf(ctx, end, false);
            body.gen(ctx);
            ctx.m.visitInsn(POP);
            ctx.m.visitJumpInsn(GOTO, start);
            ctx.m.visitLabel(end);
            ctx.m.visitInsn(ACONST_NULL);
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
                    return new StaticRef(moduleName, mangle(name), type,
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

    class Range extends Code {
        private Code from;
        private Code to;

        Range(Code from, Code to) {
            type = YetiType.NUM_TYPE;
            this.from = from;
            this.to = to;
        }

        void gen(Ctx ctx) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/ListRange");
            ctx.m.visitInsn(DUP);
            from.gen(ctx);
            to.gen(ctx);
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
                if (!(items[i] instanceof Range)) {
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/LList");
                    ctx.m.visitInsn(DUP);
                }
                items[i].gen(ctx);
            }
            ctx.m.visitInsn(ACONST_NULL);
            for (int i = items.length; --i >= 0;) {
                if (items[i] instanceof Range) {
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/ListRange",
                            "<init>", "(Ljava/lang/Object;Ljava/lang/Object;"
                                    + "Lyeti/lang/AList;)V");
                } else {
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/LList",
                            "<init>", "(Ljava/lang/Object;Lyeti/lang/AList;)V");
                }
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
}
