// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
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

import yeti.renamed.asm3.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import yeti.lang.Num;
import yeti.lang.RatNum;
import yeti.lang.IntNum;
import yeti.lang.BigNum;

final class Constants implements Opcodes {
    final Map constants = new HashMap();
    private Ctx sb;
    Map structClasses = new HashMap();
    int anonymousClassCounter;
    String sourceName;
    Ctx ctx;
    List unstoredClasses = new ArrayList();

    Constants(String sourceName, String sourceFile) {
        if (sourceFile != null) {
        } else if (sourceName != null) {
            int p = sourceName.lastIndexOf('/');
            if (p < 0)
                p = sourceName.lastIndexOf('\\');
            sourceFile = p < 0 ? sourceName : sourceName.substring(p + 1);
        } else {
            sourceFile = "<>";
        }
        this.sourceName = sourceFile;
    }

    private void constField(int mode, String name, Code code, String descr) {
        ctx.cw.visitField(mode, name, descr, null, null).visitEnd();
        if (sb == null)
            sb = ctx.newMethod(ACC_STATIC, "<clinit>", "()V");
        code.gen(sb);
        sb.fieldInsn(PUTSTATIC, ctx.className, name, descr);
    }

    void registerConstant(Object key, Code code, Ctx ctx_) {
        String descr = 'L' + Code.javaType(code.type.deref()) + ';';
        String name = (String) constants.get(key);
        if (name == null) {
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            constField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                       name, code, descr);
            constants.put(key, name);
        }
        ctx_.fieldInsn(GETSTATIC, ctx.className, name, descr);
    }

    void close() {
        if (sb != null) {
            sb.insn(RETURN);
            sb.closeMethod();
        }
    }

    // first value in array must be empty
    void stringArray(Ctx ctx_, String[] array) {
        if (sb == null)
            sb = ctx.newMethod(ACC_STATIC, "<clinit>", "()V");
        array[0] = "Strings";
        List key = Arrays.asList(array);
        String name = (String) constants.get(key);
        if (name == null) {
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, name,
                              "[Ljava/lang/String;", null, null).visitEnd();
            sb.intConst(array.length - 1);
            sb.typeInsn(ANEWARRAY, "java/lang/String");
            for (int i = 1; i < array.length; ++i) {
                sb.insn(DUP);
                sb.intConst(i - 1);
                sb.ldcInsn(array[i]);
                sb.insn(AASTORE);
            }
            sb.fieldInsn(PUTSTATIC, ctx.className, name,
                             "[Ljava/lang/String;");
            constants.put(key, name);
        }
        ctx_.fieldInsn(GETSTATIC, ctx.className, name,
                            "[Ljava/lang/String;");
    }

    // generates [Ljava/lang/String;[Z into stack, using constant cache
    void structInitArg(Ctx ctx_, StructField[] fields,
                       int fieldCount, boolean nomutable) {
        String[] fieldNameArr = new String[fieldCount + 1];
        char[] mutableArr = new char[fieldNameArr.length];
        mutableArr[0] = '@';
        int i, mutableCount = 0;
        for (i = 1; i < fieldNameArr.length; ++i) {
            StructField f = fields[i - 1];
            fieldNameArr[i] = f.name;
            if (f.mutable || f.property > 0) {
                mutableArr[i] = '\001';
                ++mutableCount;
            }
        }
        stringArray(ctx_, fieldNameArr);
        if (nomutable || mutableCount == 0) {
            ctx_.insn(ACONST_NULL);
            return;
        }
        String key = new String(mutableArr);
        String name = (String) constants.get(key);
        if (name == null) {
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                              name, "[Z", null, null).visitEnd();
            sb.intConst(fieldCount);
            sb.visitIntInsn(NEWARRAY, T_BOOLEAN);
            for (i = 0; i < fieldCount; ++i) {
                sb.insn(DUP);
                sb.intConst(i);
                sb.intConst(mutableArr[i + 1]);
                sb.insn(BASTORE);
            }
            sb.fieldInsn(PUTSTATIC, ctx.className, name, "[Z");
            constants.put(key, name);
        }
        ctx_.fieldInsn(GETSTATIC, ctx.className, name, "[Z");
    }
}

final class Ctx implements Opcodes {
    Compiler compilation;
    String className;
    ClassWriter cw;
    private MethodVisitor m;
    private int lastInsn = -1;
    private String lastType;
    Constants constants;
    Map usedMethodNames;
    int localVarCount;
    int fieldCounter;
    int lastLine;
    int tainted; // you are inside loop, natural laws a broken

    Ctx(Compiler compilation, Constants constants,
            ClassWriter writer, String className) {
        this.compilation = compilation;
        this.constants = constants;
        this.cw = writer;
        this.className = className;
    }

    Ctx newClass(int flags, String name, String extend,
                 String[] interfaces, int line) {
        Ctx ctx = new Ctx(compilation, constants,
                          new YClassWriter(compilation.classWriterFlags), name);
        ctx.usedMethodNames = new HashMap();
        ctx.cw.visit(V1_4, flags, name, null,
                extend == null ? "java/lang/Object" : extend, interfaces);
        ctx.cw.visitSource(constants.sourceName, null);
        compilation.addClass(name, ctx, line);
        return ctx;
    }

    String methodName(String name) {
        Map used = usedMethodNames;
        if (name == null)
            name = "_" + used.size();
        else if (used.containsKey(name) || name.startsWith("_"))
            name += used.size();
        used.put(name, null);
        return name;
    }

    Ctx newMethod(int flags, String name, String type) {
        Ctx ctx = new Ctx(compilation, constants, cw, className);
        ctx.usedMethodNames = usedMethodNames;
        ctx.m = cw.visitMethod(flags, name, type, null, null);
        ctx.m.visitCode();
        return ctx;
    }

    void markInnerClass(Ctx outer, int access) {
        String fn = className.substring(outer.className.length() + 1);
        outer.cw.visitInnerClass(className, outer.className, fn, access);
        cw.visitInnerClass(className, outer.className, fn, access);
    }

    void closeMethod() {
        insn(-1);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    void createInit(int mod, String parent) {
        MethodVisitor m = cw.visitMethod(mod, "<init>", "()V", null, null);
        // super()
        m.visitVarInsn(ALOAD, 0);
        m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V");
        m.visitInsn(RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    void intConst(int n) {
        if (n >= -1 && n <= 5) {
            insn(n + 3);
        } else {
            insn(-1);
            if (n >= -32768 && n <= 32767) {
                m.visitIntInsn(n >= -128 && n <= 127 ? BIPUSH : SIPUSH, n);
            } else {
                m.visitLdcInsn(new Integer(n));
            }
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
        fieldInsn(GETSTATIC, "java/lang/Boolean",
                       "TRUE", "Ljava/lang/Boolean;");
        Label end = new Label();
        m.visitJumpInsn(GOTO, end);
        m.visitLabel(label);
        m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                         "FALSE", "Ljava/lang/Boolean;");
        m.visitLabel(end);
    }

    void insn(int opcode) {
        if (lastInsn != -1 && lastInsn != -2) {
            if (lastInsn == ACONST_NULL && opcode == POP) {
                lastInsn = -1;
                return;
            }
            m.visitInsn(lastInsn);
        }
        lastInsn = opcode;
    }

    void varInsn(int opcode, int var) {
        insn(-1);
        m.visitVarInsn(opcode, var);
    }

    Ctx load(int var) {
        insn(-1);
        m.visitVarInsn(ALOAD, var);
        return this;
    }

    void visitIntInsn(int opcode, int param) {
        insn(-1);
        if (opcode != IINC)
            m.visitIntInsn(opcode, param);
        else
            m.visitIincInsn(param, -1);
    }

    void typeInsn(int opcode, String type) {
        if (opcode == CHECKCAST &&
            (lastInsn == -2 && type.equals(lastType) ||
             lastInsn == ACONST_NULL)) {
            return; // no cast necessary
        }
        insn(-1);
        m.visitTypeInsn(opcode, type);
    }

    void captureCast(String type) {
        if (type.charAt(0) == 'L')
            type = type.substring(1, type.length() - 1);
        if (!type.equals("java/lang/Object"))
            typeInsn(CHECKCAST, type);
    }

    void visitInit(String type, String descr) {
        insn(-2);
        m.visitMethodInsn(INVOKESPECIAL, type, "<init>", descr);
        lastType = type;
    }

    void forceType(String type) {
        insn(-2);
        lastType = type;
    }

    void fieldInsn(int opcode, String owner, String name, String desc) {
        if (owner == null || name == null || desc == null)
            throw new IllegalArgumentException("fieldInsn(" + opcode +
                        ", " + owner + ", " + name + ", " + desc + ")");
        insn(-1);
        m.visitFieldInsn(opcode, owner, name, desc);
        if ((opcode == GETSTATIC || opcode == GETFIELD) &&
            desc.charAt(0) == 'L') {
            lastInsn = -2;
            lastType = desc.substring(1, desc.length() - 1);
        }
    }

    void methodInsn(int opcode, String owner, String name, String desc) {
        insn(-1);
        m.visitMethodInsn(opcode, owner, name, desc);
    }

    void visitApply(Code arg, int line) {
        arg.gen(this);
        insn(-1);
        visitLine(line);
        m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
    }

    void jumpInsn(int opcode, Label label) {
        insn(-1);
        m.visitJumpInsn(opcode, label);
    }

    void visitLabel(Label label) {
        if (lastInsn != -2)
            insn(-1);
        m.visitLabel(label);
    }

    void ldcInsn(Object cst) {
        insn(-1);
        m.visitLdcInsn(cst);
        if (cst instanceof String) {
            lastInsn = -2;
            lastType = "java/lang/String";
        }
    }
    
    void tryCatchBlock(Label start, Label end, Label handler, String type) {
        insn(-1);
        m.visitTryCatchBlock(start, end, handler, type);
    }

    void switchInsn(int min, int max, Label dflt,
                         int[] keys, Label[] labels) {
        insn(-1);
        if (keys == null) {
            m.visitTableSwitchInsn(min, max, dflt, labels);
        } else {
            m.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    void constant(Object key, Code code) {
        constants.registerConstant(key, code, this);
    }

    void popn(int n) {
        if ((n & 1) != 0) {
            insn(POP);
        }
        for (; n >= 2; n -= 2) {
            insn(POP2);
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
    static final int EMPTY_LIST = 0x10;

    // Check for no capturing. XXX WARNING.
    // You should really do DIRECT_BIND queries only BEFORE using those
    // bindings (that's, at the start of closure gen or earlier) OR at
    // least ensure having the capture set copied, as Capture instances
    // mark themselves uncaptured when they discover being a direct binding
    // during flagop. This could be a problem when the binding ref was
    // generated as captured earlier AND the call site omits later
    // the ones that became uncaptured by DIRECT_BIND flagop. If it should be
    // discovered to be unavoidable, a special "almost-uncaptured" Capture
    // state could be introduced that would affect only Capture.gen().
    // Note that most closures will do DIRECT_BIND query by mergeCaptures.
    static final int DIRECT_BIND = 0x20;

    // normal constant is also pure and don't need capturing
    static final int STD_CONST = CONST | PURE | DIRECT_BIND;
    
    // this which is not captured
    static final int DIRECT_THIS = 0x40;

    // capture that requires bounding function to initialize its module
    static final int MODULE_REQUIRED = 0x80;

    // code object is a list range
    static final int LIST_RANGE = 0x100;

    YType type;
    boolean polymorph;

    /**
     * Generates into ctx a bytecode that (when executed in the JVM)
     * results in a value pushed into stack.
     * That value is of course the value of that code snippet
     * after evaluation.
     */
    abstract void gen(Ctx ctx);

    // Some "functions" may have special kinds of apply
    Code apply(Code arg, YType res, int line) {
        return new Apply(res, this, arg, line);
    }

    Code apply2nd(final Code arg2, final YType t, int line) {
        return new Code() {
            { type = t; }

            void gen(Ctx ctx) {
                ctx.typeInsn(NEW, "yeti/lang/Bind2nd");
                ctx.insn(DUP);
                Code.this.gen(ctx);
                arg2.gen(ctx);
                ctx.visitInit("yeti/lang/Bind2nd",
                              "(Ljava/lang/Object;Ljava/lang/Object;)V");
            }
        };
    }

    // Not used currently. Should allow some custom behaviour
    // on binding (possibly useful for inline-optimisations).
    /*BindRef bindRef() {
        return null;
    }*/

    // When the code is a lvalue, then this method returns code that
    // performs the lvalue assigment of the value given as argument.
    Code assign(Code value) {
        return null;
    }

    // Boolean codes have ability to generate jumps.
    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        gen(ctx);
        ctx.fieldInsn(GETSTATIC, "java/lang/Boolean",
                "TRUE", "Ljava/lang/Boolean;");
        ctx.jumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
    }

    // should be used, if only int is ever needed
    void genInt(Ctx ctx, int line, boolean longValue) {
        gen(ctx);
        ctx.visitLine(line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
        if (longValue)
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num", "longValue", "()J");
        else
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num", "intValue", "()I");
    }

    // Used to tell that this code is at tail position in a function.
    // Useful for doing tail call optimisations.
    void markTail() {
    }

    boolean flagop(int flag) {
        return false;
    }

    // Used for sharing embedded constant objects
    Object valueKey() {
        return this;
    }

    // Called by bind for direct bindings
    // bindings can use this for "preparation"
    boolean prepareConst(Ctx ctx) {
        return flagop(CONST);
    }

    static final String javaType(YType t) {
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

interface CodeGen {
    void gen2(Ctx ctx, Code param, int line);
}

class SimpleCode extends Code {
    private Code param;
    private int line;
    private CodeGen impl;

    SimpleCode(CodeGen impl, Code param, YType type, int line) {
        this.impl = impl;
        this.param = param;
        this.line = line;
        this.type = type == null ? YetiType.UNIT_TYPE : type;
    }

    void gen(Ctx ctx) {
        impl.gen2(ctx, param, line);
    }
}

abstract class BindRef extends Code {
    Binder binder;
    BindExpr.Ref origin;

    // some bindrefs care about being captured. most wont.
    CaptureWrapper capture() {
        return null;
    }

    // As what java types the values of this should be captured.
    // Same as CaptureWrapper.captureType()
    String captureType() {
        if (origin != null)
            return ((BindExpr) binder).captureType();
        return 'L' + javaType(type) + ';';
    }

    // unshare. normally bindrefs are not shared
    // Capture shares refs and therefore has to copy for unshareing
    BindRef unshare() {
        return this;
    }

    Code unref(boolean force) {
        return null;
    }

    // Some bindings can be forced into direct mode
    void forceDirect() {
        throw new UnsupportedOperationException();
    }

    Code apply(Code arg, YType res, int line) {
        Apply a = new Apply(res, this, arg, line);
        if ((a.ref = origin) != null)
            origin.arity = 1;
        return a;
    }
}

final class BindWrapper extends BindRef {
    private BindRef ref;

    BindWrapper(BindRef ref) {
        this.ref = ref;
        this.binder = ref.binder;
        this.type = ref.type;
        this.polymorph = ref.polymorph;
        this.origin = ref.origin;
    }

    CaptureWrapper capture() {
        return ref.capture();
    }

    boolean flagop(int fl) {
        return (fl & (PURE | ASSIGN | DIRECT_BIND)) != 0 && ref.flagop(fl);
    }

    void gen(Ctx ctx) {
        ref.gen(ctx);
    }
}

class StaticRef extends BindRef {
    String className;
    private String fieldName;
    private int line;
    boolean method;
   
    StaticRef(String className, String fieldName, YType type,
              Binder binder, boolean polymorph, int line) {
        this.type = type;
        this.binder = binder;
        this.className = className;
        this.fieldName = fieldName;
        this.polymorph = polymorph;
        this.line = line;
    }
    
    void gen(Ctx ctx) {
        ctx.visitLine(line);
        String t = javaType(type);
        if (method) {
            ctx.methodInsn(INVOKESTATIC, className, fieldName,
                           "()L" + t + ';');
            ctx.forceType(t);
        } else {
            ctx.fieldInsn(GETSTATIC, className, fieldName, 'L' + t + ';');
        }
    }

    Object valueKey() {
        return (method ? "MREF:" : "SREF:") + className + '.' + fieldName;
    }

    boolean flagop(int fl) {
        return (fl & (DIRECT_BIND | CONST)) != 0;
    }
}

final class NumericConstant extends Code implements CodeGen {
    Num num;

    NumericConstant(Num num) {
        type = YetiType.NUM_TYPE;
        this.num = num;
    }

    boolean flagop(int fl) {
        return ((fl & INT_NUM) != 0 && num instanceof IntNum) ||
               (fl & STD_CONST) != 0;
    }

    void genInt(Ctx ctx, int lineno, boolean longValue) {
        if (longValue)
            ctx.ldcInsn(new Long(num.longValue()));
        else
            ctx.intConst(num.intValue());
    }

    private static final class Impl extends Code {
        String jtype, sig;
        Object val;

        void gen(Ctx ctx) {
            ctx.typeInsn(NEW, jtype);
            ctx.insn(DUP);
            ctx.ldcInsn(val);
            if (val instanceof String) {
                ctx.intConst(10);
            }
            ctx.visitInit(jtype, sig);
        }
    }

    public void gen2(Ctx ctx, Code param, int line) {
        ctx.typeInsn(NEW, "yeti/lang/RatNum");
        ctx.insn(DUP);
        RatNum rat = ((RatNum) num).reduce();
        ctx.intConst(rat.numerator());
        ctx.intConst(rat.denominator());
        ctx.visitInit("yeti/lang/RatNum", "(II)V");
    }

    void gen(Ctx ctx) {
        if (ctx.constants.constants.containsKey(num)) {
            ctx.constant(num, this);
            return;
        }
        if (num instanceof RatNum) {
            ctx.constant(num, new SimpleCode(this, null, YetiType.NUM_TYPE, 0));
            return;
        }
        Impl v = new Impl();
        if (num instanceof IntNum) {
            v.jtype = "yeti/lang/IntNum";
            if (IntNum.__1.compareTo(num) <= 0 &&
                IntNum._9.compareTo(num) >= 0) {
                ctx.fieldInsn(GETSTATIC, v.jtype,
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
            v.sig = "(Ljava/lang/String;I)V";
        } else {
            v.jtype = "yeti/lang/FloatNum";
            v.val = new Double(num.doubleValue());
            v.sig = "(D)V";
        }
        v.type = YetiType.NUM_TYPE;
        ctx.constant(num, v);
    }

    Object valueKey() {
        return num;
    }
}

final class StringConstant extends Code {
    String str;

    StringConstant(String str) {
        type = YetiType.STR_TYPE;
        this.str = str;
    }

    void gen(Ctx ctx) {
        ctx.ldcInsn(str);
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }

    Object valueKey() {
        return str;
    }
}

final class UnitConstant extends BindRef {
    private final Object NULL = new Object();

    UnitConstant(YType type) {
        this.type = type == null ? YetiType.UNIT_TYPE : type;
    }

    void gen(Ctx ctx) {
        ctx.insn(ACONST_NULL);
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }

    Object valueKey() {
        return NULL;
    }
}

final class BooleanConstant extends BindRef {
    boolean val;

    BooleanConstant(boolean val) {
        type = YetiType.BOOL_TYPE;
        this.val = val;
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }

    void gen(Ctx ctx) {
        ctx.fieldInsn(GETSTATIC, "java/lang/Boolean",
                val ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        if (val == ifTrue) {
            ctx.jumpInsn(GOTO, to);
        }
    }

    Object valueKey() {
        return Boolean.valueOf(val);
    }
}

final class ConcatStrings extends Code {
    Code[] param;

    ConcatStrings(Code[] param) {
        type = YetiType.STR_TYPE;
        this.param = param;
    }

    void gen(Ctx ctx) {
        boolean arr = false;
        if (param.length > 2) {
            arr = true;
            ctx.intConst(param.length);
            ctx.typeInsn(ANEWARRAY, "java/lang/String");
        }
        for (int i = 0; i < param.length; ++i) {
            if (arr) {
                ctx.insn(DUP);
                ctx.intConst(i);
            }
            param[i].gen(ctx);
            boolean valueOf = param[i].type.deref().type != YetiType.STR;
            if (valueOf)
                ctx.methodInsn(INVOKESTATIC, "java/lang/String",
                    "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");
            if (arr)
                ctx.insn(AASTORE);
            else if (!valueOf)
                ctx.typeInsn(CHECKCAST, "java/lang/String");
        }
        if (arr) {
            ctx.methodInsn(INVOKESTATIC, "yeti/lang/Core",
                           "concat", "([Ljava/lang/String;)Ljava/lang/String;");
        } else if (param.length == 2) {
            ctx.methodInsn(INVOKEVIRTUAL, "java/lang/String",
                           "concat", "(Ljava/lang/String;)Ljava/lang/String;");
        }
        ctx.forceType("java/lang/String");
    }
}

final class NewExpr extends JavaExpr {
    private YetiType.ClassBinding extraArgs;

    NewExpr(JavaType.Method init, Code[] args,
            YetiType.ClassBinding extraArgs, int line) {
        super(null, init, args, line);
        type = init.classType;
        this.extraArgs = extraArgs;
    }

    void gen(Ctx ctx) {
        String name = method.classType.javaType.className();
        ctx.typeInsn(NEW, name);
        ctx.insn(DUP);
        genCall(ctx, extraArgs.getCaptures(), INVOKESPECIAL);
        ctx.forceType(name);
    }
}

final class NewArrayExpr extends Code {
    private Code count;
    private int line;

    NewArrayExpr(YType type, Code count, int line) {
        this.type = type;
        this.count = count;
        this.line = line;
    }

    void gen(Ctx ctx) {
        if (count != null)
            count.genInt(ctx, line, false);
        ctx.visitLine(line);
        if (type.param[0].type != YetiType.JAVA) { // array of arrays
            ctx.typeInsn(ANEWARRAY, JavaType.descriptionOf(type.param[0]));
            return;
        }
        JavaType jt = type.param[0].javaType;
        int t;
        switch (jt.description.charAt(0)) {
        case 'B': t = T_BYTE; break;
        case 'C': t = T_CHAR; break;
        case 'D': t = T_DOUBLE; break;
        case 'F': t = T_FLOAT; break;
        case 'I': t = T_INT; break;
        case 'J': t = T_LONG; break;
        case 'S': t = T_SHORT; break;
        case 'Z': t = T_BOOLEAN; break;
        case 'L':
            ctx.typeInsn(ANEWARRAY, jt.className());
            return;
        default:
            throw new IllegalStateException("ARRAY<" + jt.description + '>');
        }
        ctx.visitIntInsn(NEWARRAY, t);
    }
}

final class MethodCall extends JavaExpr {
    private JavaType classType;
    private boolean useAccessor, invokeSuper;

    MethodCall(Code object, JavaType.Method method, Code[] args, int line) {
        super(object, method, args, line);
        type = method.convertedReturnType();
    }

    void visitInvoke(Ctx ctx, int invokeInsn) {
        String descr = method.descr(null);
        String name = method.name;
        if (useAccessor) {
            if (invokeInsn == INVOKEINTERFACE)
                invokeInsn = INVOKEVIRTUAL;
            name = classType.implementation.getAccessor(method, descr,
                                                        invokeSuper);
        }
        ctx.methodInsn(invokeInsn, classType.className(), name, descr);
    }

    private void _gen(Ctx ctx) {
        classType = method.classType.javaType;
        int ins = object == null ? INVOKESTATIC : classType.isInterface()
                                 ? INVOKEINTERFACE : INVOKEVIRTUAL;
        if (object != null) {
            object.gen(ctx);
            if (ins != INVOKEINTERFACE)
                classType = object.type.deref().javaType;
        }
        if (classType.implementation != null) {
            JavaType ct = classType.implementation.classType.deref().javaType;
            invokeSuper = classType != ct;
            // XXX: not checking for package access. shouldn't matter.
            useAccessor = (invokeSuper || (method.access & ACC_PROTECTED) != 0)
                && !object.flagop(DIRECT_THIS);
            if (useAccessor) {
                classType = ct;
            } else if (ins == INVOKEVIRTUAL && invokeSuper) {
                ins = INVOKESPECIAL;
            }
        }
        if (object != null &&
                (ins != INVOKEINTERFACE || ctx.compilation.isGCJ)) {
            ctx.typeInsn(CHECKCAST, classType.className());
        }
        genCall(ctx, null, ins);
    }

    void gen(Ctx ctx) {
        _gen(ctx);
        if (method.returnType.type == YetiType.UNIT) {
            ctx.insn(ACONST_NULL);
        } else {
            convertValue(ctx, method.returnType);
        }
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        if (method.returnType.javaType != null &&
                method.returnType.javaType.description == "Z") {
            _gen(ctx);
            ctx.jumpInsn(ifTrue ? IFNE : IFEQ, to);
        } else {
            super.genIf(ctx, to, ifTrue);
        }
    }
}

final class Throw extends Code {
    Code throwable;

    Throw(Code throwable, YType type) {
        this.type = type;
        this.throwable = throwable;
    }

    void gen(Ctx ctx) {
        throwable.gen(ctx);
        JavaType t = throwable.type.deref().javaType;
        if (t == null)
            throw new CompileException(null,
                    "Internal error - throw argument type is unknown");
        ctx.typeInsn(CHECKCAST, t.className());
        ctx.insn(ATHROW);
    }
}

final class ClassField extends JavaExpr implements CodeGen {
    private JavaType.Field field;

    ClassField(Code object, JavaType.Field field, int line) {
        super(object, null, null, line);
        this.type = field.convertedType();
        this.field = field;
    }

    void gen(Ctx ctx) {
        JavaType classType = field.classType.javaType;
        if (object != null) {
            object.gen(ctx);
            classType = object.type.deref().javaType;
        }
        ctx.visitLine(line);
        String descr = JavaType.descriptionOf(field.type);
        String className = classType.className();
        if (object != null)
            ctx.typeInsn(CHECKCAST, className);
        // XXX: not checking for package access. shouldn't matter.
        if ((field.access & ACC_PROTECTED) != 0
                && classType.implementation != null
                && !object.flagop(DIRECT_THIS)) {
            // XXX WTF: object can't be null - !object.flagop in condition
            descr = (object == null ? "()" : '(' + classType.description + ')')
                    + descr;
            String name = classType.implementation
                                   .getAccessor(field, descr, false);
            ctx.methodInsn(INVOKESTATIC, className, name, descr);
        } else {
            ctx.fieldInsn(object == null ? GETSTATIC : GETFIELD,
                                 className, field.name, descr);
        }
        convertValue(ctx, field.type);
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
        JavaType classType = field.classType.javaType;
        String className = classType.className();
        if (object != null) {
            object.gen(ctx);
            ctx.typeInsn(CHECKCAST, className);
            classType = object.type.deref().javaType;
        }
        genValue(ctx, setValue, field.type, line);
        String descr = JavaType.descriptionOf(field.type);
        if (descr.length() > 1) {
            ctx.typeInsn(CHECKCAST,
                field.type.type == YetiType.JAVA
                    ? field.type.javaType.className() : descr);
        }
        
        if ((field.access & ACC_PROTECTED) != 0
                && classType.implementation != null
                && !object.flagop(DIRECT_THIS)) {
            // XXX WTF: object can't be null - !object.flagop in condition
            descr = (object != null ? "(".concat(classType.description)
                                    : "(") + descr + ")V";
            String name = classType.implementation
                                   .getAccessor(field, descr, true);
            ctx.methodInsn(INVOKESTATIC, className, name, descr);
        } else {
            ctx.fieldInsn(object == null ? PUTSTATIC : PUTFIELD,
                                 className, field.name, descr);
        }
        ctx.insn(ACONST_NULL);
    }

    Code assign(final Code setValue) {
        if ((field.access & ACC_FINAL) != 0)
            return null;
        return new SimpleCode(this, setValue, null, 0);
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0 && field.constValue != null;
    }
}

final class Cast extends JavaExpr {
    boolean convert;

    Cast(Code code, YType type, boolean convert, int line) {
        super(code, null, null, line);
        this.type = type;
        this.line = line;
        this.convert = convert;
    }

    void gen(Ctx ctx) {
        if (convert) {
            convertedArg(ctx, object, type.deref(), line);
            return;
        }
        object.gen(ctx);
        if (type.deref().type == YetiType.UNIT) {
            ctx.insn(POP);
            ctx.insn(ACONST_NULL);
        }
    }

    boolean prepareConst(Ctx ctx) {
        return object.prepareConst(ctx);
    }

    boolean flagop(int fl) {
        return ((fl & CONST) != 0 ? !convert : (fl & PURE) != 0) &&
               object.flagop(fl);
    }
}

final class LoadVar extends Code {
    int var;

    void gen(Ctx ctx) {
        ctx.varInsn(ALOAD, var);
    }
}

final class VariantConstructor extends Code implements CodeGen {
    String name;

    VariantConstructor(YType type, String name) {
        this.type = type;
        this.name = name;
    }

    public void gen2(Ctx ctx, Code param, int line) {
        ctx.typeInsn(NEW, "yeti/lang/TagCon");
        ctx.insn(DUP);
        ctx.ldcInsn(name);
        ctx.visitInit("yeti/lang/TagCon", "(Ljava/lang/String;)V");
    }

    void gen(Ctx ctx) {
        ctx.constant("TAG:".concat(name), new SimpleCode(this, null, type, 0));
    }

    Code apply(final Code arg, YType res, int line) {
        class Tag extends Code implements CodeGen {
            Object key;

            public void gen2(Ctx ctx, Code param, int line_) {
                ctx.typeInsn(NEW, "yeti/lang/Tag");
                ctx.insn(DUP);
                arg.gen(ctx);
                ctx.ldcInsn(name);
                ctx.visitInit("yeti/lang/Tag",
                              "(Ljava/lang/Object;Ljava/lang/String;)V");
            }

            void gen(Ctx ctx) {
                if (key != null)
                    ctx.constant(key, new SimpleCode(this, null, type, 0));
                else
                    gen2(ctx, null, 0);
            }

            boolean flagop(int fl) {
               return (fl & STD_CONST) != 0 && key != null;
            }

            Object valueKey() {
                return key == null ? this : key;
            }
        };
        Tag tag = new Tag();
        tag.type = res;
        tag.polymorph = arg.polymorph;
        if (arg.flagop(CONST)) {
            Object[] key = {"TAG", name, arg.valueKey()};
            tag.key = Arrays.asList(key);
        }
        return tag;
    }
}

abstract class SelectMember extends BindRef implements CodeGen {
    private boolean assigned = false;
    Code st;
    String name;
    int line;

    SelectMember(YType type, Code st, String name, int line,
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
        if (ctx.compilation.isGCJ)
            ctx.typeInsn(CHECKCAST, "yeti/lang/Struct");
        ctx.ldcInsn(name);
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                "get", "(Ljava/lang/String;)Ljava/lang/Object;");
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
        st.gen(ctx);
        ctx.visitLine(line);
        if (ctx.compilation.isGCJ)
            ctx.typeInsn(CHECKCAST, "yeti/lang/Struct");
        ctx.ldcInsn(name);
        setValue.gen(ctx);
        ctx.visitLine(line);
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
        ctx.insn(ACONST_NULL);
    }

    Code assign(final Code setValue) {
        if (!assigned && !mayAssign()) {
            return null;
        }
        assigned = true;
        return new SimpleCode(this, setValue, null, 0);
    }

    abstract boolean mayAssign();
}

final class SelectMemberFun extends Code implements CodeGen {
    String[] names;
    
    SelectMemberFun(YType type, String[] names) {
        this.type = type;
        this.names = names;
        this.polymorph = true;
    }

    public void gen2(Ctx ctx, Code param, int line) {
        for (int i = 1; i < names.length; ++i) {
            ctx.typeInsn(NEW, "yeti/lang/Compose");
            ctx.insn(DUP);
        }
        for (int i = names.length; --i >= 0;) {
            ctx.typeInsn(NEW, "yeti/lang/Selector");
            ctx.insn(DUP);
            ctx.ldcInsn(names[i]);
            ctx.visitInit("yeti/lang/Selector",
                          "(Ljava/lang/String;)V");
            if (i + 1 != names.length)
                ctx.visitInit("yeti/lang/Compose",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V");
        }
    }

    void gen(Ctx ctx) {
        StringBuffer buf = new StringBuffer("SELECTMEMBER");
        for (int i = 0; i < names.length; ++i) {
            buf.append(':');
            buf.append(names[i]);
        }
        ctx.constant(buf.toString(), new SimpleCode(this, null, type, 0));
    }
}

final class KeyRefExpr extends Code implements CodeGen {
    Code val;
    Code key;
    int line;

    KeyRefExpr(YType type, Code val, Code key, int line) {
        this.type = type;
        this.val = val;
        this.key = key;
        this.line = line;
    }

    void gen(Ctx ctx) {
        val.gen(ctx);
        if (val.type.deref().param[2] == YetiType.LIST_TYPE) {
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "yeti/lang/MList");
            key.genInt(ctx, line, false);
            ctx.visitLine(line);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/MList", "get",
                           "(I)Ljava/lang/Object;");
            return;
        }
        if (ctx.compilation.isGCJ)
            ctx.typeInsn(CHECKCAST, "yeti/lang/ByKey");
        key.gen(ctx);
        ctx.visitLine(line);
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/ByKey", "vget",
                       "(Ljava/lang/Object;)Ljava/lang/Object;");
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
        val.gen(ctx);
        if (ctx.compilation.isGCJ) {
            ctx.typeInsn(CHECKCAST, "yeti/lang/ByKey");
        }
        key.gen(ctx);
        setValue.gen(ctx);
        ctx.visitLine(line);
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                "put", "(Ljava/lang/Object;Ljava/lang/Object;)" +
                "Ljava/lang/Object;");
    }

    Code assign(final Code setValue) {
        return new SimpleCode(this, setValue, null, 0);
    }
}

final class ConditionalExpr extends Code {
    Code[][] choices;

    ConditionalExpr(YType type, Code[][] choices, boolean poly) {
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
                ctx.jumpInsn(GOTO, end);
            } else {
                choices[i][0].gen(ctx);
            }
            ctx.visitLabel(jmpNext);
        }
        ctx.insn(-1); // reset type
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        Label end = new Label();
        for (int i = 0, last = choices.length - 1; i <= last; ++i) {
            Label jmpNext = i < last ? new Label() : end;
            if (choices[i].length == 2) {
                choices[i][1].genIf(ctx, jmpNext, false); // condition
                choices[i][0].genIf(ctx, to, ifTrue); // body
                ctx.jumpInsn(GOTO, end);
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

class SeqExpr extends Code {
    Code st;
    Code result;

    SeqExpr(Code statement) {
        st = statement;
    }

    void gen(Ctx ctx) {
        st.gen(ctx);
        ctx.insn(POP); // ignore the result of st expr
        result.gen(ctx);
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        st.gen(ctx);
        ctx.insn(POP); // ignore the result of st expr
        result.genIf(ctx, to, ifTrue);
    }

    void markTail() {
        result.markTail();
    }
}

interface Binder {
    BindRef getRef(int line);
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
    Ref refs;
    int evalId = -1;
    private boolean directBind;
    private String directField;
    private String myClass;
    private int bindingUsed;

    class Ref extends BindRef {
        int arity;
        Ref next;

        void gen(Ctx ctx) {
            if (directBind) {
                st.gen(ctx);
            } else {
                --bindingUsed;
                genPreGet(ctx);
                genGet(ctx);
            }
        }

        Code assign(final Code value) {
            if (!var) {
                return null;
            }
            assigned = true;
            return new Code() {
                void gen(Ctx ctx) {
                    genLocalSet(ctx, value);
                    ctx.insn(ACONST_NULL);
                }
            };
        }

        boolean flagop(int fl) {
            if ((fl & ASSIGN) != 0)
                return var ? assigned = true : false;
            if ((fl & CONST) != 0)
                return directBind;
            if ((fl & DIRECT_BIND) != 0)
                return directBind || directField != null;
            if ((fl & MODULE_REQUIRED) != 0)
                return directField != null;
            return (fl & PURE) != 0 && !var;
        }

        CaptureWrapper capture() {
            captured = true;
            if (!var)
                return null;
            ++bindingUsed; // reference through wrapper
            return BindExpr.this;
        }

        Code unref(boolean force) {
            return force || directBind ? st : null;
        }

        void forceDirect() {
            directField = "";
        }
    }

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
        //BindRef res = st.bindRef();
        //if (res == null)
        Ref res = new Ref();
        res.binder = this;
        res.type = st.type;
        res.polymorph = !var && st.polymorph;
        res.next = refs;
        if (st instanceof Function)
            res.origin = res;
        ++bindingUsed;
        return refs = res;
    }

    public Object captureIdentity() {
        return mvar == -1 ? (Object) this : closure;
    }

    public String captureType() {
        if (javaDescr == null)
            throw new IllegalStateException(toString());
        return mvar == -1 ? javaDescr : "[Ljava/lang/Object;";
    }

    public void genPreGet(Ctx ctx) {
        if (mvar == -1) {
            if (directField == null) {
                ctx.load(id);
                int t;
                // garbage collect infinite lists
                if (bindingUsed == 0 && ctx.tainted == 0 &&
                      ((t = st.type.deref().type) == 0 || t == YetiType.MAP)) {
                    ctx.insn(ACONST_NULL);
                    ctx.varInsn(ASTORE, id);
                }
                ctx.forceType(javaType);
            } else {
                ctx.fieldInsn(GETSTATIC, myClass, directField, javaDescr);
            }
        } else {
            ctx.load(mvar).forceType("[Ljava/lang/Object;");
        }
    }

    public void genGet(Ctx ctx) {
        if (mvar != -1) {
            ctx.typeInsn(CHECKCAST, "[Ljava/lang/Object;");
            ctx.intConst(id);
            ctx.insn(AALOAD);
        }
    }

    public void genSet(Ctx ctx, Code value) {
        if (directField == null) {
            ctx.typeInsn(CHECKCAST, "[Ljava/lang/Object;");
            ctx.intConst(id);
            value.gen(ctx);
            ctx.insn(AASTORE);
        } else {
            value.gen(ctx);
            ctx.fieldInsn(PUTSTATIC, myClass, directField, javaDescr);
        }
    }

    private void genLocalSet(Ctx ctx, Code value) {
        if (mvar == -1) {
            value.gen(ctx);
            if (!javaType.equals("java/lang/Object"))
                ctx.typeInsn(CHECKCAST, javaType);
            if (directField == null) {
                ctx.varInsn(ASTORE, id);
            } else {
                ctx.fieldInsn(PUTSTATIC, myClass, directField, javaDescr);
            }
        } else {
            ctx.load(mvar).intConst(id);
            value.gen(ctx);
            ctx.insn(AASTORE);
        }
    }
    
    // called by Function.prepareConst when this bastard mutates into method
    void setCaptureType(String type) {
        javaType = type;
        javaDescr = type.charAt(0) == '[' ? type : 'L' + type + ';';
    }

    void genBind(Ctx ctx) {
        setCaptureType(javaType(st.type));
        if (ctx == null)
            return; // named lambdas use genBind for initializing the expr
        if (!var && st.prepareConst(ctx) && evalId == -1) {
            directBind = true;
            return;
        }
        if (directField == "") { // forceDirect, JavaClass does it
            myClass = ctx.className;
            directField =
                "$".concat(Integer.toString(ctx.constants.ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_SYNTHETIC | ACC_VOLATILE,
                              directField, javaDescr, null, null).visitEnd();
        } else if (mvar == -1) {
            id = ctx.localVarCount++;
        }
        genLocalSet(ctx, st);
        if (evalId != -1) {
            ctx.intConst(evalId);
            genPreGet(ctx);
            if (mvar != -1)
                ctx.intConst(id);
            ctx.methodInsn(INVOKESTATIC,
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
    boolean checkUsed;
    boolean typedefUsed;
    private boolean used;

    LoadModule(String moduleName, ModuleType type, int depth) {
        this.type = type.copy(depth, null);
        this.moduleName = moduleName.toLowerCase();
        moduleType = type;
        polymorph = true;
    }

    void gen(Ctx ctx) {
        if (checkUsed && !used)
            ctx.insn(ACONST_NULL);
        else
            ctx.methodInsn(INVOKESTATIC, moduleName,
                           "eval", "()Ljava/lang/Object;");
    }

    Binder bindField(final String name, final YType type) {
        return new Binder() {
            public BindRef getRef(final int line) {
                final boolean mutable = type.field == YetiType.FIELD_MUTABLE;
                if (!mutable && moduleType.directFields) {
                    String fname = name.equals("eval") ? "eval$" : mangle(name);
                    StaticRef r = new StaticRef(moduleName, fname, type,
                                                this, true, line);
                    r.method = true;
                    return r;
                }

                // property or mutable field
                // XXX in threory reading properties could be done directly
                used = true;
                return new SelectMember(type, LoadModule.this,
                                        name, line, !mutable) {
                    boolean mayAssign() {
                        return mutable;
                    }

                    boolean flagop(int fl) {
                        return (fl & DIRECT_BIND) != 0 ||
                               (fl & ASSIGN) != 0 && mutable;
                    }
                };
            }
        };
    }
}

final class Range extends Code {
    final Code from;
    final Code to;

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

final class ListConstructor extends Code implements CodeGen {
    private Code[] items;
    private List key;

    ListConstructor(Code[] items) {
        int i;
        this.items = items;
        for (i = 0; i < items.length; ++i)
            if (!items[i].flagop(CONST))
                return;
        // good, got constant list
        Object[] ak = new Object[items.length + 1];
        ak[0] = "LIST";
        for (i = 0; i < items.length; ++i)
            ak[i + 1] = items[i].valueKey();
        key = Arrays.asList(ak);
    }

    public void gen2(Ctx ctx, Code param, int line) {
        for (int i = 0; i < items.length; ++i) {
            if (!(items[i] instanceof Range)) {
                ctx.typeInsn(NEW, "yeti/lang/LList");
                ctx.insn(DUP);
            }
            items[i].gen(ctx);
        }
        ctx.insn(ACONST_NULL);
        for (int i = items.length; --i >= 0;) {
            if (items[i] instanceof Range) {
                ctx.methodInsn(INVOKESTATIC, "yeti/lang/ListRange",
                        "range", "(Ljava/lang/Object;Ljava/lang/Object;"
                                + "Lyeti/lang/AList;)Lyeti/lang/AList;");
            } else {
                ctx.visitInit("yeti/lang/LList",
                              "(Ljava/lang/Object;Lyeti/lang/AList;)V");
            }
        }
    }

    void gen(Ctx ctx) {
        if (items.length == 0) {
            ctx.insn(ACONST_NULL);
            return;
        }
        if (key == null) {
            gen2(ctx, null, 0);
        } else {
            ctx.constant(key, new SimpleCode(this, null, type, 0));
        }
        ctx.forceType("yeti/lang/AList");
    }

    Object valueKey() {
        return key;
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0 && (key != null || items.length == 0) ||
               (fl & EMPTY_LIST) != 0 && items.length == 0 ||
               (fl & LIST_RANGE) != 0 && items.length != 0
                    && items[0] instanceof Range;
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
        ctx.typeInsn(NEW, "yeti/lang/Hash");
        ctx.insn(DUP);
        if (keyItems.length > 16) {
            ctx.intConst(keyItems.length);
            ctx.visitInit("yeti/lang/Hash", "(I)V");
        } else {
            ctx.visitInit("yeti/lang/Hash", "()V");
        }
        for (int i = 0; i < keyItems.length; ++i) {
            ctx.insn(DUP);
            keyItems[i].gen(ctx);
            items[i].gen(ctx);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Hash", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            ctx.insn(POP);
        }
    }

    boolean flagop(int fl) {
        return (fl & EMPTY_LIST) != 0 && keyItems.length == 0;
    }
}

final class EvalBind implements Binder, CaptureWrapper, Opcodes, CodeGen {
    YetiEval.Binding bind;

    EvalBind(YetiEval.Binding bind) {
        this.bind = bind;
    }

    public void gen2(Ctx ctx, Code value, int line) {
        genPreGet(ctx);
        genSet(ctx, value);
        ctx.insn(ACONST_NULL);
    }

    public BindRef getRef(int line) {
        return new BindRef() {
            {
                type = bind.type;
                binder = EvalBind.this;
                polymorph = !bind.mutable && bind.polymorph;
            }

            void gen(Ctx ctx) {
                genPreGet(ctx);
                genGet(ctx);
            }

            Code assign(final Code value) {
                return bind.mutable ?
                        new SimpleCode(EvalBind.this, value, null, 0) : null;
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
        ctx.methodInsn(INVOKESTATIC, "yeti/lang/compiler/YetiEval",
                       "getBind", "(I)[Ljava/lang/Object;");
    }

    public void genGet(Ctx ctx) {
        ctx.intConst(bind.index);
        ctx.insn(AALOAD);
    }

    public void genSet(Ctx ctx, Code value) {
        ctx.intConst(bind.index);
        value.gen(ctx);
        ctx.insn(AASTORE);
    }

    public Object captureIdentity() {
        return this;
    }

    public String captureType() {
        return "[Ljava/lang/Object;";
    }
}
