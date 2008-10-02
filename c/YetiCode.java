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
import yeti.lang.Fun;
import yeti.lang.Num;
import yeti.lang.RatNum;
import yeti.lang.IntNum;
import yeti.lang.BigNum;

final class Constants implements Opcodes {
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
            sb.visitFieldInsn(PUTSTATIC, ctx.className, name, descr);
            if (key != null)
                constants.put(key, name);
        }
        final String fieldName = name;
        ctx_.visitFieldInsn(GETSTATIC, ctx.className, fieldName, descr);
    }

    void close() {
        if (sb != null) {
            sb.visitInsn(RETURN);
            sb.closeMethod();
        }
    }
}

final class CompileCtx implements Opcodes {
    static final ThreadLocal currentCompileCtx = new ThreadLocal();

    private CodeWriter writer;
    private SourceReader reader;
    private String[] preload;
    private Map compiled = new HashMap();
    private List warnings = new ArrayList();
    private String currentSrc;
    boolean isGCJ;
    ClassFinder classPath;
    Map classes = new HashMap();
    Map types = new HashMap();
    int flags;

    CompileCtx(SourceReader reader, CodeWriter writer,
               String[] preload, ClassFinder finder) {
        this.reader = reader;
        this.writer = writer;
        this.preload = preload;
        this.classPath = finder;
        // GCJ bytecode verifier is overly strict about INVOKEINTERFACE
        isGCJ = System.getProperty("java.vm.name").indexOf("gcj") >= 0;
//            isGCJ = true;
    }

    static CompileCtx current() {
        return (CompileCtx) currentCompileCtx.get();
    }

    void warn(CompileException ex) {
        ex.fn = currentSrc;
        warnings.add(ex);
    }

    CompileCtx setGCJ(boolean gcj) {
        isGCJ |= gcj;
        return this;
    }

    public void enumWarns(Fun f) {
        for (int i = 0, cnt = warnings.size(); i < cnt; ++i) {
            f.apply(warnings.get(i));
        }
    }

    private void generateModuleFields(Map fields, Ctx ctx) {
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        for (Iterator i = fields.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            String name = (String) entry.getKey();
            String jname = Code.mangle(name);
            String type = Code.javaType((YetiType.Type) entry.getValue());
            String descr = 'L' + type + ';';
            ctx.cw.visitField(ACC_PUBLIC | ACC_STATIC, jname,
                    descr, null, null).visitEnd();
            ctx.visitInsn(DUP);
            ctx.visitLdcInsn(name);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                "get", "(Ljava/lang/String;)Ljava/lang/Object;");
            ctx.visitTypeInsn(CHECKCAST, type);
            ctx.visitFieldInsn(PUTSTATIC, ctx.className, jname, descr);
        }
    }

    String compile(String sourceName, int flags) throws Exception {
        String className = (String) compiled.get(sourceName);
        if (className != null) {
            return className;
        }
        String[] srcName = { sourceName };
        char[] src;
        try {
            src = reader.getSource(srcName);
        } catch (IOException ex) {
            throw new CompileException(null, ex.getMessage());
        }
        int dot = srcName[0].lastIndexOf('.');
        className = dot < 0 ? srcName[0] : srcName[0].substring(0, dot);
        dot = className.lastIndexOf('.');
        if (dot >= 0) {
            dot = Math.max(className.indexOf('/', dot),
                           className.indexOf('\\', dot));
            if (dot >= 0)
                className = className.substring(dot + 1);
        }
        compile(srcName[0], className, src, flags);
        className = (String) compiled.get(srcName[0]);
        compiled.put(sourceName, className);
        return className;
    }

    YetiType.Type compile(String sourceName, String name,
                          char[] code, int flags) {
        if (classes.containsKey(name)) {
            throw new RuntimeException(classes.get(name) == null
                ? "Circular module dependency: " + name
                : "Duplicate module name: " + name);
        }
        boolean module = (flags & YetiC.CF_COMPILE_MODULE) != 0;
        RootClosure codeTree;
        Object oldCompileCtx = currentCompileCtx.get();
        currentCompileCtx.set(this);
        currentSrc = sourceName;
        this.flags = flags;
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
        constants.sourceName = sourceName == null ? "<>" : sourceName;
        Ctx ctx = new Ctx(this, constants, null, null)
                        .newClass(ACC_PUBLIC, name,
                    (flags & YetiC.CF_EVAL) != 0 ? "yeti/lang/Fun" : null);
        constants.ctx = ctx;
        if (module) {
            ctx.cw.visitAttribute(
                new YetiTypeAttr(codeTree.type, codeTree.typeDefs));
            ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC, "$",
                              "Ljava/lang/Object;", null, null).visitEnd();
            ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC, "_$", "Z",
                              null, Boolean.FALSE);
            ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED,
                                "eval", "()Ljava/lang/Object;");
            ctx.visitFieldInsn(GETSTATIC, name, "_$", "Z");
            Label eval = new Label();
            ctx.visitJumpInsn(IFEQ, eval);
            ctx.visitFieldInsn(GETSTATIC, name, "$",
                                 "Ljava/lang/Object;");
            ctx.visitInsn(ARETURN);
            ctx.visitLabel(eval);
            codeTree.gen(ctx);
            if (codeTree.type.type == YetiType.STRUCT) {
                generateModuleFields(codeTree.type.finalMembers, ctx);
            }
            ctx.visitInsn(DUP);
            ctx.visitFieldInsn(PUTSTATIC, name, "$",
                                 "Ljava/lang/Object;");
            ctx.intConst(1);
            ctx.visitFieldInsn(PUTSTATIC, name, "_$", "Z");
            ctx.visitInsn(ARETURN);
            types.put(name, new ModuleType(codeTree.type,
                                           codeTree.typeDefs));
        } else if ((flags & YetiC.CF_EVAL) != 0) {
            ctx.createInit(ACC_PUBLIC, "yeti/lang/Fun");
            ctx = ctx.newMethod(ACC_PUBLIC, "apply",
                                "(Ljava/lang/Object;)Ljava/lang/Object;");
            codeTree.gen(ctx);
            ctx.visitInsn(ARETURN);
        } else {
            ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC, "main",
                                "([Ljava/lang/String;)V");
            ctx.localVarCount++;
            ctx.visitVarInsn(ALOAD, 0);
            ctx.visitMethodInsn(INVOKESTATIC, "yeti/lang/Core",
                                "setArgv", "([Ljava/lang/String;)V");
            Label codeStart = new Label(), exitStart = new Label();
            ctx.visitTryCatchBlock(codeStart, exitStart, exitStart,
                                   "yeti/lang/ExitError");
            ctx.visitLabel(codeStart);
            codeTree.gen(ctx);
            ctx.visitInsn(POP);
            ctx.visitInsn(RETURN);
            ctx.visitLabel(exitStart);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/ExitError",
                                "getExitCode", "()I");
            ctx.visitMethodInsn(INVOKESTATIC, "java/lang/System",
                                "exit", "(I)V");
            ctx.visitInsn(RETURN);
        }
        ctx.closeMethod();
        constants.close();
        compiled.put(sourceName, name);
        return codeTree.type;
    }

    void write() throws Exception {
        Iterator i = classes.values().iterator();
        while (i.hasNext()) {
            Ctx c = (Ctx) i.next();
            writer.writeClass(c.className + ".class", c.cw.toByteArray());
        }
    }
}

final class Ctx implements Opcodes {
    CompileCtx compilation;
    String className;
    ClassWriter cw;
    private MethodVisitor m;
    private int lastInsn = -1;
    private String lastType;
    Constants constants;
    int localVarCount;
    int fieldCounter;
    int methodCounter;
    int lastLine;
    int tainted; // you are inside loop, natural laws a broken

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
        visitInsn(-1);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    void createInit(int mod, String parent) {
        MethodVisitor m = cw.visitMethod(mod, "<init>", "()V", null, null);
        m.visitVarInsn(ALOAD, 0); // this.
        m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V"); // super
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    void intConst(int n) {
        if (n >= -1 && n <= 5) {
            visitInsn(n + 3);
        } else {
            visitInsn(-1);
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
        visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                       "TRUE", "Ljava/lang/Boolean;");
        Label end = new Label();
        m.visitJumpInsn(GOTO, end);
        m.visitLabel(label);
        m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                "FALSE", "Ljava/lang/Boolean;");
        m.visitLabel(end);
    }

    void visitInsn(int opcode) {
        if (lastInsn != -1 && lastInsn != -2) {
            if (lastInsn == ACONST_NULL && opcode == POP) {
                lastInsn = -1;
                return;
            }
            m.visitInsn(lastInsn);
        }
        lastInsn = opcode;
    }

    void visitVarInsn(int opcode, int var) {
        visitInsn(-1);
        m.visitVarInsn(opcode, var);
    }

    void visitTypeInsn(int opcode, String type) {
        if (lastInsn == -2 && opcode == CHECKCAST &&
            type.equals(lastType)) {
            return; // no cast necessary
        }
        visitInsn(-1);
        m.visitTypeInsn(opcode, type);
    }

    void captureCast(String type) {
        if (type.charAt(0) == 'L')
            type = type.substring(1, type.length() - 1);
        if (!type.equals("java/lang/Object"))
            visitTypeInsn(CHECKCAST, type);
    }

    void visitInit(String type, String descr) {
        visitInsn(-2);
        m.visitMethodInsn(INVOKESPECIAL, type, "<init>", descr);
        lastType = type;
    }

    void forceType(String type) {
        visitInsn(-2);
        lastType = type;
    }

    void visitFieldInsn(int opcode, String owner,
                              String name, String desc) {
        visitInsn(-1);
        m.visitFieldInsn(opcode, owner, name, desc);
        if ((opcode == GETSTATIC || opcode == GETFIELD) &&
            desc.charAt(0) == 'L') {
            lastInsn = -2;
            lastType = desc.substring(1, desc.length() - 1);
        }
    }

    void visitMethodInsn(int opcode, String owner,
                               String name, String desc) {
        visitInsn(-1);
        m.visitMethodInsn(opcode, owner, name, desc);
    }

    void visitApply(Code arg, int line) {
        arg.gen(this);
        visitInsn(-1);
        visitLine(line);
        m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
    }

    void visitJumpInsn(int opcode, Label label) {
        visitInsn(-1);
        m.visitJumpInsn(opcode, label);
    }

    void visitLabel(Label label) {
        visitInsn(-1);
        m.visitLabel(label);
    }

    void visitLdcInsn(Object cst) {
        visitInsn(-1);
        m.visitLdcInsn(cst);
        if (cst instanceof String) {
            lastInsn = -2;
            lastType = "java/lang/String";
        }
    }
    
    void visitTryCatchBlock(Label start, Label end,
                                  Label handler, String type) {
        visitInsn(-1);
        m.visitTryCatchBlock(start, end, handler, type);
    }
                                  
    void constant(Object key, Code code) {
        constants.registerConstant(key, code, this);
    }

    void popn(int n) {
        if ((n & 1) != 0) {
            visitInsn(POP);
        }
        for (; n >= 2; n -= 2) {
            visitInsn(POP2);
        }
    }
}

abstract class Code implements Opcodes {
    // constants used by flagop
    static final int CONST      = 1;
    static final int PURE       = 2;
    
    // for bindrefs, mark as used lvalue
    static final int ASSIGN     = 4;
    static final int INT_NUM    = 8;

    // Comparision operators use this for some optimisation.
    static final int EMPTY_LIST = 16;

    YetiType.Type type;
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
                ctx.visitTypeInsn(NEW, "yeti/lang/Bind2nd");
                ctx.visitInsn(DUP);
                Code.this.gen(ctx);
                arg2.gen(ctx);
                ctx.visitInit("yeti/lang/Bind2nd",
                              "(Ljava/lang/Object;Ljava/lang/Object;)V");
            }
        };
    }

    // Not used currently. Should allow some custom behaviour
    // on binding (possibly useful for inline-optimisations).
    BindRef bindRef() {
        return null;
    }

    // When the code is a lvalue, then this method returns code that
    // performs the lvalue assigment of the value given as argument.
    Code assign(Code value) {
        return null;
    }

    // Boolean codes have ability to generate jumps.
    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        gen(ctx);
        ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                "TRUE", "Ljava/lang/Boolean;");
        ctx.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
    }

    // Used to tell that this code is at tail position in a function.
    // Useful for doing tail call optimisations.
    void markTail() {
    }

    boolean flagop(int flag) {
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
            case YetiType.MAP: {
                int k = t.param[2].deref().type;
                if (k == YetiType.MAP_MARKER)
                    return "yeti/lang/Hash";
                if (k != YetiType.LIST_MARKER)
                    return "java/lang/Object";
                if (t.param[1].deref().type == YetiType.NUM)
                    return "yeti/lang/MList";
                return "yeti/lang/AList";
            }
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

    // unshare. normally bindrefs are not shared
    // Capture shares refs and therefore has to copy for unshareing
    BindRef unshare() {
        return this;
    }
}

final class BindWrapper extends BindRef {
    private BindRef ref;

    BindWrapper(BindRef ref) {
        this.ref = ref;
        this.binder = ref.binder;
        this.type = ref.type;
        this.polymorph = ref.polymorph;
    }

    CaptureWrapper capture() {
        return ref.capture();
    }

    boolean flagop(int fl) {
        return (fl & (PURE | ASSIGN)) != 0 && ref.flagop(fl);
    }

    void gen(Ctx ctx) {
        ref.gen(ctx);
    }
}

interface DirectBind {
}

class StaticRef extends BindRef implements DirectBind {
    private String className;
    protected String funFieldName;
    int line;
   
    StaticRef(String className, String fieldName, YetiType.Type type,
              Binder binder, boolean polymorph, int line) {
        this.type = type;
        this.binder = binder;
        this.className = className;
        this.funFieldName = fieldName;
        this.polymorph = polymorph;
        this.line = line;
    }
    
    void gen(Ctx ctx) {
        ctx.visitLine(line);
        ctx.visitFieldInsn(GETSTATIC, className, funFieldName,
                             'L' + javaType(type) + ';');
    }
}

final class NumericConstant extends Code {
    Num num;

    NumericConstant(Num num) {
        type = YetiType.NUM_TYPE;
        this.num = num;
    }

    boolean flagop(int fl) {
        return ((fl & INT_NUM) != 0 && num instanceof IntNum) ||
               (fl & (PURE | CONST | INT_NUM)) != 0;
    }

    boolean genInt(Ctx ctx, boolean small) {
        if (!(num instanceof IntNum)) {
            return false;
        }
        long n = num.longValue();
        if (!small) {
            ctx.visitLdcInsn(new Long(n));
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
            ctx.visitTypeInsn(NEW, jtype);
            ctx.visitInsn(DUP);
            ctx.visitLdcInsn(val);
            ctx.visitInit(jtype, sig);
        }
    }

    void gen(Ctx ctx) {
        if (num instanceof RatNum) {
            ctx.constant(num, new Code() {
                { type = YetiType.NUM_TYPE; }
                void gen(Ctx ctx) {
                    ctx.visitTypeInsn(NEW, "yeti/lang/RatNum");
                    ctx.visitInsn(DUP);
                    RatNum rat = ((RatNum) num).reduce();
                    ctx.intConst(rat.numerator());
                    ctx.intConst(rat.denominator());
                    ctx.visitInit("yeti/lang/RatNum", "(II)V");
                }
            });
            return;
        }
        Impl v = new Impl();
        if (num instanceof IntNum) {
            v.jtype = "yeti/lang/IntNum";
            if (IntNum.__1.compareTo(num) <= 0 &&
                IntNum._9.compareTo(num) >= 0) {
                ctx.visitFieldInsn(GETSTATIC, v.jtype,
                    IntNum.__1.equals(num) ? "__1" :
                    IntNum.__2.equals(num) ? "__2" : "_" + num,
                    "Lyeti/lang/IntNum;");
                ctx.forceType("yeti/lang/Num");
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

final class StringConstant extends Code {
    String str;

    StringConstant(String str) {
        type = YetiType.STR_TYPE;
        this.str = str;
    }

    boolean flagop(int fl) {
        return (fl & (CONST | PURE)) != 0;
    }

    void gen(Ctx ctx) {
        ctx.visitLdcInsn(str);
    }
}

final class UnitConstant extends Code {
    UnitConstant() {
        type = YetiType.UNIT_TYPE;
    }

    boolean flagop(int fl) {
        return (fl & (CONST | PURE)) != 0;
    }

    void gen(Ctx ctx) {
        ctx.visitInsn(ACONST_NULL);
    }
}

final class BooleanConstant extends BindRef implements Binder, DirectBind {
    boolean val;

    BooleanConstant(boolean val) {
        binder = this;
        type = YetiType.BOOL_TYPE;
        this.val = val;
    }

    public BindRef getRef(int line) {
        return this;
    }

    boolean flagop(int fl) {
        return (fl & (CONST | PURE)) != 0;
    }

    void gen(Ctx ctx) {
        ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                val ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        if (val == ifTrue) {
            ctx.visitJumpInsn(GOTO, to);
        }
    }
}

final class ConcatStrings extends Code {
    Code[] param;

    ConcatStrings(Code[] param) {
        type = YetiType.STR_TYPE;
        this.param = param;
    }

    void gen(Ctx ctx) {
        if (param.length == 1) {
            param[0].gen(ctx);
            if (param[0].type.deref().type != YetiType.STR) {
                ctx.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
                ctx.forceType("java/lang/String");
            }
            return;
        }
        ctx.intConst(param.length);
        ctx.visitTypeInsn(ANEWARRAY, "java/lang/String");
        for (int i = 0; i < param.length; ++i) {
            ctx.visitInsn(DUP);
            ctx.intConst(i);
            param[i].gen(ctx);
            if (param[i].type.deref().type != YetiType.STR) {
                ctx.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
            }
            ctx.visitInsn(AASTORE);
        }
        ctx.visitMethodInsn(INVOKESTATIC, "yeti/lang/Core",
            "concat", "([Ljava/lang/String;)Ljava/lang/String;");
        ctx.forceType("java/lang/String");
    }
}

final class NewExpr extends JavaExpr {
    NewExpr(JavaType.Method init, Code[] args, int line) {
        super(null, init, args, line);
        type = init.classType;
    }

    void gen(Ctx ctx) {
        String name = method.classType.javaType.className();
        ctx.visitTypeInsn(NEW, name);
        ctx.visitInsn(DUP);
        genCall(ctx, INVOKESPECIAL);
        ctx.forceType(name);
    }
}

final class MethodCall extends JavaExpr {
    MethodCall(Code object, JavaType.Method method, Code[] args, int line) {
        super(object, method, args, line);
        type = method.convertedReturnType();
    }

    void gen(Ctx ctx) {
        int ins = object == null ? INVOKESTATIC :
                    method.classType.javaType.isInterface()
                        ? INVOKEINTERFACE : INVOKEVIRTUAL;
        if (object != null) {
            object.gen(ctx);
            if (ctx.compilation.isGCJ || ins != INVOKEINTERFACE) {
                ctx.visitTypeInsn(CHECKCAST,
                    method.classType.javaType.className());
            }
        }
        genCall(ctx, ins);
        convertValue(ctx, method.returnType);
    }
}

final class Throw extends Code {
    Code throwable;

    Throw(Code throwable, YetiType.Type type) {
        this.type = type;
        this.throwable = throwable;
    }

    void gen(Ctx ctx) {
        throwable.gen(ctx);
        ctx.visitInsn(ATHROW);
    }
}

final class ClassField extends JavaExpr {
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
        String className = field.classType.javaType.className();
        if (object != null) {
            ctx.visitTypeInsn(CHECKCAST, className);
        }
        ctx.visitFieldInsn(object == null ? GETSTATIC : GETFIELD,
                             className, field.name,
                             JavaType.descriptionOf(field.type));
        convertValue(ctx, field.type);
    }

    Code assign(final Code setValue) {
        if ((field.access & ACC_FINAL) != 0) {
            return null;
        }
        return new Code() {
            void gen(Ctx ctx) {
                String className = field.classType.javaType.className();
                if (object != null) {
                    object.gen(ctx);
                    ctx.visitTypeInsn(CHECKCAST, className);
                }
                genValue(ctx, setValue, field.type, line);
                String descr = JavaType.descriptionOf(field.type);
                ctx.visitTypeInsn(CHECKCAST,
                    field.type.type == YetiType.JAVA
                        ? field.type.javaType.className() : descr);
                ctx.visitFieldInsn(object == null ? PUTSTATIC : PUTFIELD,
                                     className, field.name, descr);
                ctx.visitInsn(ACONST_NULL);
            }
        };
    }
}

final class Cast extends JavaExpr {
    boolean convert;

    Cast(Code code, YetiType.Type type, boolean convert, int line) {
        super(code, null, null, line);
        this.type = type;
        this.line = line;
        this.convert = convert;
    }

    void gen(Ctx ctx) {
        if (convert) {
            convertedArg(ctx, object, type, line);
        } else {
            object.gen(ctx);
        }
    }
}

// Since the stupid JVM discards local stack when catching exceptions,
// try catch blocks have to be converted into fucking closures
// (at least for the generic case).
final class TryCatch extends CapturingClosure {
    private List catches = new ArrayList();
    private int exVar;
    Code block;
    Code cleanup;

    final class Catch extends BindRef implements Binder {
        Code handler;
        Label start = new Label();
        Label end;

        public BindRef getRef(int line) {
            return this;
        }

        void gen(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, exVar);
        }
    }

    void setBlock(Code block) {
        this.type = block.type;
        this.block = block;
    }

    Catch addCatch(YetiType.Type ex) {
        Catch c = new Catch();
        c.type = ex;
        catches.add(c);
        return c;
    }

    void captureInit(Ctx ctx, Capture c, int n) {
        c.localVar = n;
        if (c.wrapper == null) {
            c.ref.gen(ctx);
            ctx.captureCast(c.captureType());
        } else {
            c.wrapper.genPreGet(ctx);
        }
    }

    void gen(Ctx ctx) {
        int argc = mergeCaptures(ctx);
        StringBuffer sigb = new StringBuffer("(");
        for (Capture c = captures; c != null; c = c.next) {
            sigb.append(c.captureType());
        }
        sigb.append(")Ljava/lang/Object;");
        String sig = sigb.toString();
        String name = "_" + ctx.methodCounter++;
        ctx.visitMethodInsn(INVOKESTATIC, ctx.className, name, sig);
        Ctx mc = ctx.newMethod(ACC_PRIVATE | ACC_STATIC, name, sig);
        mc.localVarCount = argc;

        Label codeStart = new Label(), codeEnd = new Label();
        Label cleanupStart = cleanup == null ? null : new Label();
        Label cleanupEntry = cleanup == null ? null : new Label();
        int catchCount = catches.size();
        for (int i = 0; i < catchCount; ++i) {
            Catch c = (Catch) catches.get(i);
            mc.visitTryCatchBlock(codeStart, codeEnd, c.start,
                                 c.type.javaType.className());
            if (cleanupStart != null) {
                c.end = new Label();
                mc.visitTryCatchBlock(c.start, c.end, cleanupStart, null);
            }
        }
        genClosureInit(mc);
        int retVar = -1;
        if (cleanupStart != null) {
            retVar = mc.localVarCount++;
            mc.visitTryCatchBlock(codeStart, codeEnd, cleanupStart, null);
            mc.visitInsn(ACONST_NULL);
            mc.visitVarInsn(ASTORE, retVar); // silence the JVM verifier...
        }
        mc.visitLabel(codeStart);
        block.gen(mc);
        mc.visitLabel(codeEnd);
        exVar = mc.localVarCount++;
        if (cleanupStart != null) {
            Label goThrow = new Label();
            mc.visitLabel(cleanupEntry);
            mc.visitVarInsn(ASTORE, retVar);
            mc.visitInsn(ACONST_NULL);
            mc.visitLabel(cleanupStart);
            mc.visitVarInsn(ASTORE, exVar);
            cleanup.gen(mc);
            mc.visitInsn(POP); // cleanup's null
            mc.visitVarInsn(ALOAD, exVar);
            mc.visitJumpInsn(IFNONNULL, goThrow);
            mc.visitVarInsn(ALOAD, retVar);
            mc.visitInsn(ARETURN);
            mc.visitLabel(goThrow);
            mc.visitVarInsn(ALOAD, exVar);
            mc.visitInsn(ATHROW);
        } else {
            mc.visitInsn(ARETURN);
        }
        for (int i = 0; i < catchCount; ++i) {
            Catch c = (Catch) catches.get(i);
            mc.visitLabel(c.start);
            mc.visitVarInsn(ASTORE, exVar);
            c.handler.gen(mc);
            if (c.end != null) {
                mc.visitLabel(c.end);
                mc.visitJumpInsn(GOTO, cleanupEntry);
            } else {
                mc.visitInsn(ARETURN);
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

    final class SelfApply extends Apply {
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
            ctx.visitVarInsn(ASTORE, capturer.argCount);
            if (argCaptures != null) {
                for (int i = argCaptures.length; --i >= 0;) {
                    ctx.visitVarInsn(ASTORE, argCaptures[i].localVar);
                }
            }
            ctx.visitJumpInsn(GOTO, capturer.restart);
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

final class Capture extends CaptureRef implements CaptureWrapper {
    String id;
    Capture next;
    CaptureWrapper wrapper;
    Object identity;
    int localVar = -1; // -1 - use this - not copied
    boolean uncaptured;
    boolean ignoreGet;
    private String refType;

    void gen(Ctx ctx) {
        if (uncaptured) {
            ref.gen(ctx);
            return;
        }
        genPreGet(ctx);
        genGet(ctx);
    }

    String getId(Ctx ctx) {
        if (id == null) {
            id = "_".concat(Integer.toString(ctx.fieldCounter++));
        }
        return id;
    }

    boolean flagop(int fl) {
        return (fl & (PURE | ASSIGN)) != 0 && ref.flagop(fl);
    }

    Code assign(final Code value) {
        if (!ref.flagop(ASSIGN)) {
            return null;
        }
        return new Code() {
            void gen(Ctx ctx) {
                if (uncaptured) {
                    ref.assign(value).gen(ctx);
                } else {
                    genPreGet(ctx);
                    wrapper.genSet(ctx, value);
                    ctx.visitInsn(ACONST_NULL);
                }
            }
        };
    }

    public void genPreGet(Ctx ctx) {
        if (localVar == -1) {
            ctx.visitVarInsn(ALOAD, 0);
            ctx.visitFieldInsn(GETFIELD, ctx.className, id,
                               captureType());
        } else {
            ctx.visitVarInsn(ALOAD, localVar);
        }
    }

    public void genGet(Ctx ctx) {
        if (wrapper != null && !ignoreGet) {
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
        if (refType == null) {
            refType = wrapper == null ? 'L' + javaType(ref.type) + ';'
                : wrapper.captureType();
        }
        return refType;
    }

    BindRef unshare() {
        return new BindWrapper(this);
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
            ctx.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            ctx.visitVarInsn(ASTORE, id);
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

    public BindRef refProxy(BindRef code) {
        return code instanceof DirectBind ? code : captureRef(code);
    }

    // Called by mergeCaptures to initialize a capture.
    // It must be ok to copy capture after that.
    abstract void captureInit(Ctx fun, Capture c, int n);

    int mergeCaptures(Ctx ctx) {
        int counter = 0;
        Capture prev = null;
    next_capture:
        for (Capture c = captures; c != null; c = c.next) {
            Object identity = c.identity = c.captureIdentity();
            if (c.uncaptured)
                continue;
            // remove shared captures
            for (Capture i = captures; i != c; i = i.next) {
                if (i.identity == identity) {
                    c.id = i.id; // copy old one's id
                    c.localVar = i.localVar;
                    prev.next = c.next;
                    continue next_capture;
                }
            }
            captureInit(ctx, c, counter++);
            prev = c;
        }
        return counter;
    }
}

final class Function extends CapturingClosure implements Binder {
    private static final Code NEVER = new Code() {
        void gen(Ctx ctx) {
            throw new UnsupportedOperationException();
        }
    };

    private String name;
    Binder selfBind;
    Code body;
    String bindName;
    private CaptureRef selfRef;
    Label restart;
    Function outer;
    Capture[] argCaptures;
    private Code uncaptureArg;
    int argCount = 1; // Used by CaptureRef
    private boolean merged;
    private int argUsed;
    private boolean constFun;

    final BindRef arg = new BindRef() {
        void gen(Ctx ctx) {
            if (uncaptureArg != null) {
                uncaptureArg.gen(ctx);
            } else {
                ctx.visitVarInsn(ALOAD, argCount);
                // inexact nulling...
                if (--argUsed == 0 && ctx.tainted == 0) {
                    ctx.visitInsn(ACONST_NULL);
                    ctx.visitVarInsn(ASTORE, argCount);
                }
            }
        }

        boolean flagop(int fl) {
            return (fl & PURE) != 0;
        }
    };

    Function(YetiType.Type type) {
        this.type = type;
        arg.binder = this;
    }

    public BindRef getRef(int line) {
        ++argUsed;
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
            Function bodyFun = (Function) body;
            bodyFun.outer = this;
            if (argCount == 1 && bodyFun.selfRef == null) {
                merged = true;
                ++bodyFun.argCount;
            }
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
        if (selfBind == code.binder && !code.flagop(ASSIGN)) {
            if (selfRef == null) {
                selfRef = new CaptureRef() {
                    void gen(Ctx ctx) {
                        ctx.visitVarInsn(ALOAD, 0);
                    }
                };
                selfRef.binder = selfBind;
                selfRef.type = code.type;
                selfRef.ref = code;
                selfRef.capturer = this;
            }
            return selfRef;
        }
        if (merged) {
            return code;
        }
        Capture c = captureRef(code);
        c.capturer = this;
        //expecting max 2 merged
        if (outer != null && outer.merged &&
            (code == outer.selfRef || code == outer.arg)) {
            /*
             * It's actually simple - because nested functions are merged,
             * the parent argument is now real argument that can be
             * directly accessed. Therefore capture proxy would only
             * fuck things up - and so that proxy is marked uncaptured.
             * Same goes for the parent-self-ref - it is now our this.
             *
             * Only problem is that tail-rec optimisation generates code,
             * that wants to store into the "captured" variables copy
             * before jumping back into the start of the function.
             * The optimiser sets argCaptures which should be copied
             * into local vars by function class generator, but this
             * coping is skipped as pointless for uncaptured ones.
             *
             * Therefore the captures localVar is simply set here to 1,
             * which happens to be parent args register (and is ignored
             * by selfRefs). Probable alternative would be to set it
             * when the copy code generation is skipped.
             */
            c.localVar = 1; // really evil hack for tail-recursion.
            c.uncaptured = true;
        }
        return c;
    }

    // called by mergeCaptures
    void captureInit(Ctx fun, Capture c, int n) {
        // c.getId() initialises the captures id as a side effect
        fun.cw.visitField(0, c.getId(fun), c.captureType(),
                          null, null).visitEnd();
    }

    void prepareGen(Ctx ctx, boolean makeConst) {
        if (merged) {
            // 2 nested lambdas have been optimised into 1
            ((Function) body).bindName = bindName;
            ((Function) body).prepareGen(ctx, makeConst);
            return;
        }
        if (bindName == null) {
            bindName = "";
        }
        String nameBase = name = ctx.className + '$' + mangle(bindName);
        Map classes = ctx.compilation.classes;
        for (int i = 0; classes.containsKey(name); ++i) {
            name = nameBase + i;
        }

        String funClass =
            argCount == 2 ? "yeti/lang/Fun2" : "yeti/lang/Fun";
        Ctx fun = ctx.newClass(ACC_STATIC | ACC_FINAL, name, funClass);

        mergeCaptures(fun);
        fun.createInit(0, funClass);

        Ctx apply = argCount == 2
            ? fun.newMethod(ACC_PUBLIC | ACC_FINAL, "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
            : fun.newMethod(ACC_PUBLIC | ACC_FINAL, "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
        apply.localVarCount = argCount + 1; // this, arg
        
        if (argCaptures != null) {
            // Tail recursion needs all args to be in local registers
            // - otherwise it couldn't modify them safely before restarting
            for (int i = 0; i < argCaptures.length; ++i) {
                Capture c = argCaptures[i];
                if (!c.uncaptured) {
                    c.gen(apply);
                    c.localVar = apply.localVarCount;
                    c.ignoreGet = true;
                    apply.visitVarInsn(ASTORE, apply.localVarCount++);
                }
            }
        }
        genClosureInit(apply);
        apply.visitLabel(restart = new Label());
        body.gen(apply);
        restart = null;
        apply.visitInsn(ARETURN);
        apply.closeMethod();

        Ctx valueCtx =
            makeConst ? fun.newMethod(ACC_STATIC, "<clinit>", "()V") : ctx;
        valueCtx.visitTypeInsn(NEW, name);
        valueCtx.visitInsn(DUP);
        valueCtx.visitInit(name, "()V");
        if (makeConst) {
            fun.cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                              "_", "Lyeti/lang/Fun;", null, null).visitEnd();
            valueCtx.visitFieldInsn(PUTSTATIC, name, "_", "Lyeti/lang/Fun;");
            valueCtx.visitInsn(RETURN);
            valueCtx.closeMethod();
            ctx.visitFieldInsn(GETSTATIC, name, "_", "Lyeti/lang/Fun;");
        }
    }

    void finishGen(Ctx ctx) {
        if (merged) {
            ((Function) body).finishGen(ctx);
            return;
        }
        // Capture a closure
        for (Capture c = captures; c != null; c = c.next) {
            if (c.uncaptured)
                continue;
            ctx.visitInsn(DUP);
            if (c.wrapper == null) {
                c.ref.gen(ctx);
                ctx.captureCast(c.captureType());
            } else {
                c.wrapper.genPreGet(ctx);
            }
            ctx.visitFieldInsn(PUTFIELD, name, c.id, c.captureType());
        }
        ctx.forceType("yeti/lang/Fun");
    }

    boolean flagop(int fl) {
        return (fl & (PURE | CONST)) != 0 && captures == null &&
                (!merged || ((Function) body).captures == null);
    }

    void gen(Ctx ctx) {
        if (constFun || argUsed == 0 && body.flagop(PURE) && uncapture(NEVER)) {
            if (flagop(CONST) && ctx.constants.ctx.cw != ctx.cw) {
                constFun = true;
                ctx.constant(null, this);
                return;
            }
            ctx.visitTypeInsn(NEW, "yeti/lang/Const");
            ctx.visitInsn(DUP);
            try {
                body.gen(ctx);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                throw ex;
            }
            ctx.visitInit("yeti/lang/Const", "(Ljava/lang/Object;)V");
            ctx.forceType("yeti/lang/Fun");
        } else if (flagop(CONST)) {
            prepareGen(ctx, true);
        } else {
            prepareGen(ctx, false);
            finishGen(ctx);
        }
    }
}

final class RootClosure extends AClosure {
    Code code;
    String[] preload;
    String moduleName;
    boolean isModule;
    Map typeDefs;

    public BindRef refProxy(BindRef code) {
        return code;
    }

    void gen(Ctx ctx) {
        genClosureInit(ctx);
        for (int i = 0; i < preload.length; ++i) {
            if (!preload[i].equals(ctx.className)) {
                ctx.visitMethodInsn(INVOKESTATIC, preload[i],
                    "eval", "()Ljava/lang/Object;");
                ctx.visitInsn(POP);
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
            ctx.visitVarInsn(ALOAD, var);
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
                ctx.visitVarInsn(ASTORE, arg_.var);
                f.body.gen(ctx);
                return;
            }
        }
        fun.gen(ctx);
        // XXX this cast could be optimised away sometimes
        //  - when the fun really is Fun by java types
        ctx.visitLine(line);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
        ctx.visitApply(arg, line);
    }

    Code apply(final Code arg2, final YetiType.Type res,
               final int line2) {
        if (fun instanceof Function) // hopefully will be inlined.
            return super.apply(arg2, res, line2);
        return new Code() {
            { type = res; }

            void gen(Ctx ctx) {
                fun.gen(ctx);
                ctx.visitLine(line);
                ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
                arg.gen(ctx);
                arg2.gen(ctx);
                ctx.visitLine(line2);
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun", "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            }
        };
    }
}

final class VariantConstructor extends Code {
    String name;

    VariantConstructor(YetiType.Type type, String name) {
        this.type = type;
        this.name = name;
    }

    void gen(Ctx ctx) {
        ctx.constant("TAG:".concat(name), new Code() {
            { type = VariantConstructor.this.type; }
            void gen(Ctx ctx) {
                ctx.visitTypeInsn(NEW, "yeti/lang/TagCon");
                ctx.visitInsn(DUP);
                ctx.visitLdcInsn(name);
                ctx.visitInit("yeti/lang/TagCon", "(Ljava/lang/String;)V");
            }
        });
    }

    Code apply(Code arg, YetiType.Type res, int line) {
        Code apply = new Apply(res, this, arg, line) {
            void gen(Ctx ctx) {
                ctx.visitTypeInsn(NEW, "yeti/lang/Tag");
                ctx.visitInsn(DUP);
                arg.gen(ctx);
                ctx.visitLdcInsn(name);
                ctx.visitInit("yeti/lang/Tag",
                              "(Ljava/lang/Object;Ljava/lang/String;)V");
            }
        };
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
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        ctx.visitLdcInsn(name);
        ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
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
                ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
                ctx.visitLdcInsn(name);
                setValue.gen(ctx);
                ctx.visitLine(line);
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct",
                        "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
                ctx.visitInsn(ACONST_NULL);
            }
        };
    }

    abstract boolean mayAssign();
}

final class SelectMemberFun extends Code {
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
                    ctx.visitTypeInsn(NEW, "yeti/lang/Selector");
                    ctx.visitInsn(DUP);
                    ctx.visitLdcInsn(names[0]);
                    ctx.visitInit("yeti/lang/Selector",
                                  "(Ljava/lang/String;)V");
                    return;
                }
                ctx.visitTypeInsn(NEW, "yeti/lang/Selectors");
                ctx.visitInsn(DUP);
                ctx.intConst(names.length);
                ctx.visitTypeInsn(ANEWARRAY, "java/lang/String");
                for (int i = 0; i < names.length; ++i) {
                    ctx.visitInsn(DUP);
                    ctx.intConst(i);
                    ctx.visitLdcInsn(names[i]);
                    ctx.visitInsn(AASTORE);
                }
                ctx.visitInit("yeti/lang/Selectors",
                              "([Ljava/lang/String;)V");
            }
        });
    }
}

final class KeyRefExpr extends Code {
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
        if (ctx.compilation.isGCJ) {
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/ByKey");
        }
        key.gen(ctx);
        ctx.visitLine(line);
        ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey", "vget",
                              "(Ljava/lang/Object;)Ljava/lang/Object;");
    }

    Code assign(final Code setValue) {
        return new Code() {
            void gen(Ctx ctx) {
                val.gen(ctx);
                if (ctx.compilation.isGCJ) {
                    ctx.visitTypeInsn(CHECKCAST, "yeti/lang/ByKey");
                }
                key.gen(ctx);
                setValue.gen(ctx);
                ctx.visitLine(line);
                ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                        "put", "(Ljava/lang/Object;Ljava/lang/Object;)" +
                        "Ljava/lang/Object;");
            }
        };
    }
}

final class ConditionalExpr extends Code {
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
                ctx.visitJumpInsn(GOTO, end);
            } else {
                choices[i][0].gen(ctx);
            }
            ctx.visitLabel(jmpNext);
        }
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        Label end = new Label();
        for (int i = 0, last = choices.length - 1; i <= last; ++i) {
            Label jmpNext = i < last ? new Label() : end;
            if (choices[i].length == 2) {
                choices[i][1].genIf(ctx, jmpNext, false); // condition
                choices[i][0].genIf(ctx, to, ifTrue); // body
                ctx.visitJumpInsn(GOTO, end);
            } else {
                choices[i][0].genIf(ctx, to, ifTrue);
            }
            ctx.visitLabel(jmpNext);
        }
    }

    void markTail() {
        for (int i = choices.length; --i >= 0;) {
            choices[i][0].markTail();
        }
    }
}

final class LoopExpr extends Code {
    Code cond, body;

    LoopExpr(Code cond, Code body) {
        this.type = YetiType.UNIT_TYPE;
        this.cond = cond;
        this.body = body;
    }

    void gen(Ctx ctx) {
        Label start = new Label();
        Label end = new Label();
        ctx.visitLabel(start);
        ++ctx.tainted;
        cond.genIf(ctx, end, false);
        body.gen(ctx);
        --ctx.tainted;
        ctx.visitInsn(POP);
        ctx.visitJumpInsn(GOTO, start);
        ctx.visitLabel(end);
        ctx.visitInsn(ACONST_NULL);
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
        ctx.visitInsn(POP); // ignore the result of st expr
        result.gen(ctx);
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        st.gen(ctx);
        ctx.visitInsn(POP); // ignore the result of st expr
        result.genIf(ctx, to, ifTrue);
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

final class BindExpr extends SeqExpr implements Binder, CaptureWrapper {
    private int id;
    private int mvar = -1;
    private final boolean var;
    private String javaType;
    private String javaDescr;
    private Closure closure;
    boolean assigned;
    boolean captured;
    boolean used;
    int evalId = -1;

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
                            ctx.visitInsn(ACONST_NULL);
                        }
                    };
                }

                boolean flagop(int fl) {
                    if ((fl & ASSIGN) != 0)
                        return var ? assigned = true : false;
                    return (fl & PURE) != 0 && !var;
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
        return mvar == -1 ? javaDescr : "[Ljava/lang/Object;";
    }

    public void genPreGet(Ctx ctx) {
        if (mvar == -1) {
            ctx.visitVarInsn(ALOAD, id);
            ctx.forceType(javaType);
        } else {
            ctx.visitVarInsn(ALOAD, mvar);
        }
    }

    public void genGet(Ctx ctx) {
        if (mvar != -1) {
            ctx.intConst(id);
            ctx.visitInsn(AALOAD);
        }
    }

    public void genSet(Ctx ctx, Code value) {
        ctx.intConst(id);
        value.gen(ctx);
        ctx.visitInsn(AASTORE);
    }

    private void genLocalSet(Ctx ctx, Code value) {
        if (mvar == -1) {
            value.gen(ctx);
            if (!javaType.equals("java/lang/Object"))
                ctx.visitTypeInsn(CHECKCAST, javaType);
            ctx.visitVarInsn(ASTORE, id);
        } else {
            ctx.visitVarInsn(ALOAD, mvar);
            ctx.intConst(id);
            value.gen(ctx);
            ctx.visitInsn(AASTORE);
        }
    }

    private void genBind(Ctx ctx) {
        if (mvar == -1) {
            id = ctx.localVarCount++;
        }
        javaType = javaType(st.type);
        javaDescr = 'L' + javaType + ';';
        genLocalSet(ctx, st);
        if (evalId != -1) {
            ctx.intConst(evalId);
            genPreGet(ctx);
            if (mvar != -1) {
                ctx.intConst(id);
            }
            ctx.visitMethodInsn(INVOKESTATIC,
                "yeti/lang/compiler/YetiEval", "setBind",
                mvar == -1 ? "(ILjava/lang/Object;)V"
                           : "(I[Ljava/lang/Object;I)V");
        }
    }

    void gen(Ctx ctx) {
        genBind(ctx);
        result.gen(ctx);
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        genBind(ctx);
        result.genIf(ctx, to, ifTrue);
    }
}

final class LoadModule extends Code {
    String moduleName;
    ModuleType moduleType;

    LoadModule(String moduleName, ModuleType type) {
        this.type = type.type;
        this.moduleName = moduleName;
        moduleType = type;
        polymorph = true;
    }

    void gen(Ctx ctx) {
        ctx.visitMethodInsn(INVOKESTATIC, moduleName,
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

final class StructField {
    boolean property;
    String name;
    Code value;
    Code setter;
}

final class StructConstructor extends Code {
    StructField[] fields;
    StructField[] properties;
    Bind[] binds;

    private class Bind extends BindRef implements Binder, CaptureWrapper {
        boolean mutable;
        boolean used;
        boolean fun;
        int var;
        int index;

        public BindRef getRef(int line) {
            used = true;
            return this;
        }

        public CaptureWrapper capture() {
            return !fun || mutable ? this : null;
        }

        public boolean flagop(int fl) {
            return (fl & ASSIGN) != 0 && mutable;
        }

        void gen(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, var);
        }

        public void genPreGet(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, var);
        }

        public void genGet(Ctx ctx) {
            ctx.intConst(index);
            ctx.visitInsn(AALOAD);
        }

        public void genSet(Ctx ctx, Code value) {
            ctx.intConst(index);
            value.gen(ctx);
            ctx.visitInsn(AASTORE);
        }

        public Object captureIdentity() {
            return StructConstructor.this;
        }

        public String captureType() {
            return "[Ljava/lang/Object;";
        }
    }

    StructConstructor(int maxBinds) {
        binds = new Bind[maxBinds];
    }

    Binder bind(int num, Code code, boolean mutable) {
        Bind bind = new Bind();
        bind.type = code.type;
        bind.binder = bind;
        bind.mutable = mutable;
        bind.fun = code instanceof Function;
        binds[num] = bind;
        return bind;
    }

    void gen(Ctx ctx) {
        int arrayVar = -1;
        for (int i = 0; i < binds.length; ++i) {
            if (binds[i] != null) {
                if (binds[i].used && !binds[i].mutable && binds[i].fun) {
                    ((Function) fields[i].value).prepareGen(ctx, false);
                    ctx.visitVarInsn(ASTORE,
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
        ctx.visitTypeInsn(NEW, properties.length == 0
            ? "yeti/lang/Struct" : "yeti/lang/PStruct");
        ctx.visitInsn(DUP);
        ctx.intConst(fields.length * 2);
        ctx.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        if (arrayVar != -1) {
            ctx.visitVarInsn(ASTORE, arrayVar);
        }
        for (int i = 0, cnt = fields.length; i < cnt; ++i) {
            if (arrayVar != -1) {
                ctx.visitVarInsn(ALOAD, arrayVar);
            } else {
                ctx.visitInsn(DUP);
            }
            ctx.intConst(i * 2);
            ctx.visitLdcInsn(fields[i].name);
            ctx.visitInsn(AASTORE);
            if (arrayVar != -1) {
                ctx.visitVarInsn(ALOAD, arrayVar);
            } else {
                ctx.visitInsn(DUP);
            }
            ctx.intConst(i * 2 + 1);
            if (binds[i] != null) {
                binds[i].gen(ctx);
                ((Function) fields[i].value).finishGen(ctx);
            } else {
                fields[i].value.gen(ctx);
            }
            ctx.visitInsn(AASTORE);
        }
        if (arrayVar != -1) {
            ctx.visitVarInsn(ALOAD, arrayVar);
        }
        if (properties.length == 0) {
            ctx.visitInit("yeti/lang/Struct", "([Ljava/lang/Object;)V");
            return;
        }
        ctx.intConst(properties.length * 3);
        ctx.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int i = 0, cnt = properties.length; i < cnt; ++i) {
            ctx.visitInsn(DUP);
            ctx.intConst(i * 3);
            ctx.visitLdcInsn(properties[i].name);
            ctx.visitInsn(AASTORE);
            ctx.visitInsn(DUP);
            ctx.intConst(i * 3 + 1);
            properties[i].value.gen(ctx);
            ctx.visitInsn(AASTORE);
            if (properties[i].setter != null) {
                ctx.visitInsn(DUP);
                ctx.intConst(i * 3 + 2);
                properties[i].setter.gen(ctx);
                ctx.visitInsn(AASTORE);
            }
        }
        ctx.visitInit("yeti/lang/PStruct",
                      "([Ljava/lang/Object;[Ljava/lang/Object;)V");
    }
}

final class Range extends Code {
    private Code from;
    private Code to;

    Range(Code from, Code to) {
        type = YetiType.NUM_TYPE;
        this.from = from;
        this.to = to;
    }

    void gen(Ctx ctx) {
        from.gen(ctx);
        to.gen(ctx);
    }
}

final class ListConstructor extends Code {
    Code[] items;

    ListConstructor(Code[] items) {
        this.items = items;
    }

    void gen(Ctx ctx) {
        if (items.length == 0) {
            ctx.visitInsn(ACONST_NULL);
            return;
        }
        for (int i = 0; i < items.length; ++i) {
            if (!(items[i] instanceof Range)) {
                ctx.visitTypeInsn(NEW, "yeti/lang/LList");
                ctx.visitInsn(DUP);
            }
            items[i].gen(ctx);
        }
        ctx.visitInsn(ACONST_NULL);
        for (int i = items.length; --i >= 0;) {
            if (items[i] instanceof Range) {
                ctx.visitMethodInsn(INVOKESTATIC, "yeti/lang/ListRange",
                        "range", "(Ljava/lang/Object;Ljava/lang/Object;"
                                + "Lyeti/lang/AList;)Lyeti/lang/AList;");
            } else {
                ctx.visitInit("yeti/lang/LList",
                              "(Ljava/lang/Object;Lyeti/lang/AList;)V");
            }
        }
        ctx.forceType("yeti/lang/AList");
    }

    boolean flagop(int fl) {
        return (fl & (CONST | PURE)) != 0 ||
               (fl & EMPTY_LIST) != 0 && items.length == 0;
    }
}

final class MapConstructor extends Code {
    Code[] keyItems;
    Code[] items;

    MapConstructor(Code[] keyItems, Code[] items) {
        this.keyItems = keyItems;
        this.items = items;
    }

    void gen(Ctx ctx) {
        ctx.visitTypeInsn(NEW, "yeti/lang/Hash");
        ctx.visitInsn(DUP);
        if (items.length > 16) {
            ctx.intConst(items.length);
            ctx.visitInit("yeti/lang/Hash", "(I)V");
        } else {
            ctx.visitInit("yeti/lang/Hash", "()V");
        }
        for (int i = 0; i < items.length; ++i) {
            ctx.visitInsn(DUP);
            keyItems[i].gen(ctx);
            items[i].gen(ctx);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            ctx.visitInsn(POP);
        }
        ctx.forceType("yeti/lang/Hash");
    }

    boolean flagop(int fl) {
        return (fl & EMPTY_LIST) != 0 && items.length == 0;
    }
}

final class EvalBind implements Binder, CaptureWrapper, Opcodes {
    YetiEval.Binding bind;

    EvalBind(YetiEval.Binding bind) {
        this.bind = bind;
    }

    public BindRef getRef(int line) {
        return new BindRef() {
            {
                type = bind.type;
                binder = EvalBind.this;
            }

            void gen(Ctx ctx) {
                genPreGet(ctx);
                genGet(ctx);
            }

            Code assign(final Code value) {
                return bind.mutable ? new Code() {
                    void gen(Ctx ctx) {
                        genPreGet(ctx);
                        genSet(ctx, value);
                        ctx.visitInsn(ACONST_NULL);
                    }
                } : null;
            }

            boolean flagop(int fl) {
                return (fl & ASSIGN) != 0 && bind.mutable;
            }

            CaptureWrapper capture() {
                return EvalBind.this;
            }
        };
    }

    public void genPreGet(Ctx ctx) {
        ctx.intConst(bind.bindId);
        ctx.visitMethodInsn(INVOKESTATIC, "yeti/lang/compiler/YetiEval",
                            "getBind", "(I)[Ljava/lang/Object;");
    }

    public void genGet(Ctx ctx) {
        ctx.intConst(bind.index);
        ctx.visitInsn(AALOAD);
    }

    public void genSet(Ctx ctx, Code value) {
        ctx.intConst(bind.index);
        value.gen(ctx);
        ctx.visitInsn(AASTORE);
    }

    public Object captureIdentity() {
        return this;
    }

    public String captureType() {
        return "[Ljava/lang/Object;";
    }
}

final class JavaClass extends CapturingClosure {
    private String className;
    private String parentClass = "java/lang/Object";
    private List fields = new ArrayList();
    private List methods = new ArrayList();
    private Map fieldNames = new HashMap();
    final Meth constr = new Meth() {
        Binder addArg(YetiType.Type type, String name) {
            return addField(name, super.addArg(type, name).getRef(0),
                            false);
        }
    };

    private static class Arg extends BindRef implements Binder {
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
        private String descr;
        private int line;
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
            sig = name.concat(descr = descr());
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
            init();
            ctx = ctx.newMethod(access, name, descr);
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

    private static final class Field extends Code
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

    JavaClass(String className) {
        type = YetiType.UNIT_TYPE;
        this.className = className;
        constr.name = "<init>";
        constr.returnType = YetiType.UNIT_TYPE;
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

    // called by mergeCaptures
    void captureInit(Ctx fun, Capture c, int n) {
        // c.getId() initialises the captures id as a side effect
        fun.cw.visitField(0, c.getId(fun), c.captureType(),
                          null, null).visitEnd();
    }

    void gen(Ctx ctx) {
        ctx.visitInsn(ACONST_NULL);
        Ctx c = ctx.newClass(ACC_STATIC | ACC_PUBLIC,
                             className, parentClass);
        constr.init();
        mergeCaptures(c);
        Ctx init = c.newMethod(ACC_PUBLIC, "<init>", constr.descr);
        init.visitVarInsn(ALOAD, 0); // this.
        init.visitMethodInsn(INVOKESPECIAL, parentClass, "<init>", "()V");
        constr.convertArgs(init);
        for (int i = 0, cnt = fields.size(); i < cnt; ++i) {
            ((Code) fields.get(i)).gen(init);
        }
        init.visitInsn(RETURN);
        init.closeMethod();
        for (int i = 0, cnt = methods.size(); i < cnt; ++i) {
            ((Meth) methods.get(i)).gen(c);
        }
    }
}
