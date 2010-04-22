// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
 * Copyright (c) 2007,2008,2009,2010 Madis Janson
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
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.net.URL;
import java.net.URLClassLoader;
import yeti.lang.Fun;
import yeti.lang.Num;
import yeti.lang.RatNum;
import yeti.lang.IntNum;
import yeti.lang.BigNum;
import yeti.lang.Core;

final class Constants implements Opcodes {
    final Map constants = new HashMap();
    private Ctx sb;
    int anonymousClassCounter;
    String sourceName;
    Ctx ctx;

    void registerConstant(Object key, Code code, Ctx ctx_) {
        String descr = 'L' + Code.javaType(code.type) + ';';
        String name = (String) constants.get(key);
        if (name == null) {
            if (sb == null) {
                sb = ctx.newMethod(ACC_STATIC, "<clinit>", "()V");
            }
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                              name, descr, null, null).visitEnd();
            code.gen(sb);
            sb.visitFieldInsn(PUTSTATIC, ctx.className, name, descr);
            constants.put(key, name);
        }
        ctx_.visitFieldInsn(GETSTATIC, ctx.className, name, descr);
    }

    void close() {
        if (sb != null) {
            sb.visitInsn(RETURN);
            sb.closeMethod();
        }
    }

    // generates [Ljava/lang/String;[Z into stack, using constant cache
    void structInitArg(Ctx ctx_, StructField[] fields, int fieldCount) {
        if (sb == null) {
            sb = ctx.newMethod(ACC_STATIC, "<clinit>", "()V");
        }
        String[] fieldNameArr = new String[fieldCount + 1];
        fieldNameArr[0] = "Struct";
        char[] mutableArr = new char[fieldNameArr.length];
        mutableArr[0] = '@';
        int i, mutableCount = 0;
        for (i = 1; i < fieldNameArr.length; ++i) {
            StructField f = fields[i - 1];
            fieldNameArr[i] = f.name;
            if (f.mutable) {
                mutableArr[i] = '\001';
                ++mutableCount;
            }
        }
        Object key = Arrays.asList(fieldNameArr);
        String name = (String) constants.get(key);
        if (name == null) {
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, name,
                              "[Ljava/lang/String;", null, null).visitEnd();
            sb.intConst(fieldCount);
            sb.visitTypeInsn(ANEWARRAY, "java/lang/String");
            for (i = 0; i < fieldCount; ++i) {
                sb.visitInsn(DUP);
                sb.intConst(i);
                sb.visitLdcInsn(fields[i].name);
                sb.visitInsn(AASTORE);
            }
            sb.visitFieldInsn(PUTSTATIC, ctx.className, name,
                             "[Ljava/lang/String;");
            constants.put(key, name);
        }
        ctx_.visitFieldInsn(GETSTATIC, ctx.className, name,
                            "[Ljava/lang/String;");

        if (mutableCount == 0) {
            ctx_.visitInsn(ACONST_NULL);
            return;
        }
        key = new String(mutableArr);
        name = (String) constants.get(key);
        if (name == null) {
            name = "_".concat(Integer.toString(ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                              name, "[Z", null, null).visitEnd();
            sb.intConst(fieldCount);
            sb.visitIntInsn(NEWARRAY, T_BOOLEAN);
            for (i = 0; i < fieldCount; ++i) {
                sb.visitInsn(DUP);
                sb.intConst(i);
                sb.intConst(fields[i].mutable ? 1 : 0);
                sb.visitInsn(BASTORE);
            }
            sb.visitFieldInsn(PUTSTATIC, ctx.className, name, "[Z");
            constants.put(key, name);
        }
        ctx_.visitFieldInsn(GETSTATIC, ctx.className, name, "[Z");
    }
}

final class CompileCtx implements Opcodes {
    static final ThreadLocal currentCompileCtx = new ThreadLocal();
    private static ClassLoader JAVAC;

    private CodeWriter writer;
    private SourceReader reader;
    private String[] preload;
    private Map compiled = new HashMap();
    private List warnings = new ArrayList();
    private String currentSrc;
    private Map definedClasses = new HashMap();
    private List unstoredClasses;
    List postGen = new ArrayList();
    boolean isGCJ;
    ClassFinder classPath;
    Map types = new HashMap();
    int classWriterFlags = ClassWriter.COMPUTE_FRAMES;
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

    String createClassName(Ctx ctx, String outerClass, String nameBase) {
        boolean anon = nameBase == "" && ctx != null;
        String name = nameBase = outerClass + '$' + nameBase;
        if (anon) {
            do {
                name = nameBase + ctx.constants.anonymousClassCounter++;
            } while (definedClasses.containsKey(name));
        } else {
            for (int i = 0; definedClasses.containsKey(name); ++i)
                name = nameBase + i;
        }
        return name;
    }

    public void enumWarns(Fun f) {
        for (int i = 0, cnt = warnings.size(); i < cnt; ++i) {
            f.apply(warnings.get(i));
        }
    }

    private void generateModuleFields(Map fields, Ctx ctx, Map ignore) {
        //ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        for (Iterator i = fields.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            String name = (String) entry.getKey();
            if (ignore.containsKey(name))
                continue;
            String jname = Code.mangle(name);
            String type = Code.javaType((YType) entry.getValue());
            String descr = 'L' + type + ';';
            ctx.cw.visitField(ACC_PUBLIC | ACC_STATIC, jname,
                    descr, null, null).visitEnd();
            ctx.visitInsn(DUP);
            ctx.visitLdcInsn(name);
            ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                "get", "(Ljava/lang/String;)Ljava/lang/Object;");
            ctx.visitTypeInsn(CHECKCAST, type);
            ctx.visitFieldInsn(PUTSTATIC, ctx.className, jname, descr);
        }
    }

    String compileAll(String[] sources, int flags, String[] javaArg)
            throws Exception {
        String[] fn = new String[1];
        List java = null;
        int i, yetiCount = 0;
        for (i = 0; i < sources.length; ++i)
            if (sources[i].endsWith(".java")) {
                fn[0] = sources[i];
                char[] s = reader.getSource(fn, true);
                new JavaSource(fn[0], s, classPath.parsed);
                if (java == null) {
                    java = new ArrayList();
                    boolean debug = true;
                    for (int j = 0; j < javaArg.length; ++j) {
                        if (javaArg[j].startsWith("-g"))
                            debug = false;
                        java.add(javaArg[j]);
                    }
                    if (!java.contains("-encoding")) {
                        java.add("-encoding");
                        java.add("utf-8");
                    }
                    if (debug)
                        java.add("-g");
                    if (classPath.pathStr.length() != 0) {
                        java.add("-cp");
                        java.add(classPath.pathStr);
                    }
                }
                java.add(sources[i]);
            } else {
                sources[yetiCount++] = sources[i];
            }
        String mainClass = null;
        for (i = 0; i < yetiCount; ++i)
            mainClass = compile(sources[i], flags);
        if (java != null) {
            javaArg = (String[]) java.toArray(new String[javaArg.length]);
            Class javac = null;
            try {
                javac = Class.forName("com.sun.tools.javac.Main", true,
                                      getClass().getClassLoader());
            } catch (Exception ex) {
            }
            java.lang.reflect.Method m;
            try {
                if (javac == null) { // find javac...
                    synchronized (currentCompileCtx) {
                        if (JAVAC == null)
                            JAVAC = new URLClassLoader(new URL[] {
                                new URL("file://" + new File(System.getProperty(
                                             "java.home"), "/../lib/tools.jar")
                                    .getAbsolutePath().replace('\\', '/')) });
                    }
                    javac =
                        Class.forName("com.sun.tools.javac.Main", true, JAVAC);
                }
                m = javac.getMethod("compile", new Class[] { String[].class });
            } catch (Exception ex) {
                throw new CompileException(null, "Couldn't find Java compiler");
            }
            Object o = javac.newInstance();
            if (((Integer) m.invoke(o, new Object[] {javaArg})).intValue() != 0)
                throw new CompileException(null,
                            "Error while compiling Java sources");
        }
        return mainClass;
    }

    String compile(String sourceName, int flags) throws Exception {
        String className = (String) compiled.get(sourceName);
        if (className != null) {
            return className;
        }
        String[] srcName = { sourceName };
        char[] src;
        try {
            src = reader.getSource(srcName, false);
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

    YType compile(String sourceName, String name,
                          char[] code, int flags) throws Exception {
        if (definedClasses.containsKey(name)) {
            throw new RuntimeException(definedClasses.get(name) == null
                ? "Circular module dependency: " + name
                : "Duplicate module name: " + name);
        }
        boolean module = (flags & YetiC.CF_COMPILE_MODULE) != 0;
        RootClosure codeTree;
        Object oldCompileCtx = currentCompileCtx.get();
        currentCompileCtx.set(this);
        currentSrc = sourceName;
        this.flags = flags;
        List oldUnstoredClasses = unstoredClasses;
        unstoredClasses = new ArrayList();
        try {
            try {
                codeTree = YetiAnalyzer.toCode(sourceName, name, code,
                                               this, preload);
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
                .newClass(ACC_PUBLIC | ACC_SUPER, name,
                          (flags & YetiC.CF_EVAL) != 0 ? "yeti/lang/Fun" : null,
                          null);
            constants.ctx = ctx;
            if (module) {
                ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "$",
                                  "Ljava/lang/Object;", null, null).visitEnd();
                ctx.cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
                                  "_$", "Z", null, Boolean.FALSE);
                ctx = ctx.newMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED,
                                    "eval", "()Ljava/lang/Object;");
                ctx.visitFieldInsn(GETSTATIC, name, "_$", "Z");
                Label eval = new Label();
                ctx.visitJumpInsn(IFEQ, eval);
                ctx.visitFieldInsn(GETSTATIC, name, "$",
                                     "Ljava/lang/Object;");
                ctx.visitInsn(ARETURN);
                ctx.visitLabel(eval);
                Code codeTail = codeTree.code;
                while (codeTail instanceof SeqExpr) {
                    codeTail = ((SeqExpr) codeTail).result;
                }
                if (codeTail instanceof StructConstructor) {
                    ((StructConstructor) codeTail).publish();
                    codeTree.gen(ctx);
                    codeTree.moduleType.directFields =
                        ((StructConstructor) codeTail).getDirect();
                } else {
                    codeTree.gen(ctx);
                }
                ctx.cw.visitAttribute(new YetiTypeAttr(codeTree.moduleType));
                if (codeTree.type.type == YetiType.STRUCT) {
                    generateModuleFields(codeTree.type.finalMembers, ctx,
                                         codeTree.moduleType.directFields);
                }
                ctx.visitInsn(DUP);
                ctx.visitFieldInsn(PUTSTATIC, name, "$",
                                     "Ljava/lang/Object;");
                ctx.intConst(1);
                ctx.visitFieldInsn(PUTSTATIC, name, "_$", "Z");
                ctx.visitInsn(ARETURN);
                types.put(name, codeTree.moduleType);
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
                Label codeStart = new Label();
                ctx.visitLabel(codeStart);
                codeTree.gen(ctx);
                ctx.visitInsn(POP);
                ctx.visitInsn(RETURN);
                Label exitStart = new Label();
                ctx.visitTryCatchBlock(codeStart, exitStart, exitStart,
                                       "yeti/lang/ExitError");
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
            write();
            unstoredClasses = oldUnstoredClasses;
            classPath.existsCache.clear();
            return codeTree.type;
        } catch (CompileException ex) {
            if (ex.fn == null) {
                ex.fn = sourceName;
            }
            throw ex;
        }
    }

    void addClass(String name, Ctx ctx) {
        if (definedClasses.put(name, ctx) != null) {
            throw new IllegalStateException("Duplicate class: "
                                            + name.replace('/', '.'));
        }
        if (ctx != null) {
            unstoredClasses.add(ctx);
        }
    }

    private void write() throws Exception {
        if (writer == null)
            return;
        int i, cnt = postGen.size();
        for (i = 0; i < cnt; ++i)
            ((Runnable) postGen.get(i)).run();
        postGen.clear();
        cnt = unstoredClasses.size();
        for (i = 0; i < cnt; ++i) {
            Ctx c = (Ctx) unstoredClasses.get(i);
            definedClasses.put(c.className, "");
            String name = c.className + ".class";
            byte[] content = c.cw.toByteArray();
            writer.writeClass(name, content);
            classPath.define(name, content);
        }
        unstoredClasses = null;
    }
}

final class YClassWriter extends ClassWriter {
    YClassWriter(int flags) {
        super(COMPUTE_MAXS | flags);
    }

    // Overload to avoid using reflection on non-standard-library classes
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if (type1.startsWith("java/lang/") && type2.startsWith("java/lang/") ||
            type1.startsWith("yeti/lang/") && type2.startsWith("yeti/lang/")) {
            return super.getCommonSuperClass(type1, type2);
        }
        return "java/lang/Object";
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
    Map usedMethodNames;
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

    Ctx newClass(int flags, String name, String extend, String[] interfaces) {
        Ctx ctx = new Ctx(compilation, constants,
                          new YClassWriter(compilation.classWriterFlags), name);
        ctx.usedMethodNames = new HashMap();
        ctx.cw.visit(V1_4, flags, name, null,
                extend == null ? "java/lang/Object" : extend, interfaces);
        ctx.cw.visitSource(constants.sourceName, null);
        compilation.addClass(name, ctx);
        return ctx;
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
            if (n >= -32768 && n <= 32767) {
                m.visitIntInsn(n >= -128 && n <= 127 ? BIPUSH : SIPUSH, n);
            } else {
                m.visitLdcInsn(new Integer(n));
            }
        }
    }

    void genInt(Code arg, int line) {
        if (arg instanceof NumericConstant) {
            intConst(((NumericConstant) arg).num.intValue());
        } else {
            arg.gen(this);
            visitLine(line);
            visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num", "intValue", "()I");
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

    void visitIntInsn(int opcode, int param) {
        visitInsn(-1);
        m.visitIntInsn(opcode, param);
    }

    void visitTypeInsn(int opcode, String type) {
        if (opcode == CHECKCAST &&
            (lastInsn == -2 && type.equals(lastType) ||
             lastInsn == ACONST_NULL)) {
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
        if (owner == null || name == null || desc == null)
            throw new IllegalArgumentException("visitFieldInsn(" + opcode +
                        ", " + owner + ", " + name + ", " + desc + ")");
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
        if (lastInsn != -2)
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

    void visitSwitchInsn(int min, int max, Label dflt,
                         int[] keys, Label[] labels) {
        visitInsn(-1);
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
    static final int EMPTY_LIST = 0x10;

    // no capturing
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
    protected String funFieldName;
    int line;
   
    StaticRef(String className, String fieldName, YType type,
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

    boolean flagop(int fl) {
        return (fl & DIRECT_BIND) != 0;
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

    public void gen2(Ctx ctx, Code param, int line) {
        ctx.visitTypeInsn(NEW, "yeti/lang/RatNum");
        ctx.visitInsn(DUP);
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

    void gen(Ctx ctx) {
        ctx.visitLdcInsn(str);
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }
}

final class UnitConstant extends BindRef {
    UnitConstant(YType type) {
        this.type = type == null ? YetiType.UNIT_TYPE : type;
    }

    void gen(Ctx ctx) {
        ctx.visitInsn(ACONST_NULL);
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
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
    private YetiType.ClassBinding extraArgs;

    NewExpr(JavaType.Method init, Code[] args,
            YetiType.ClassBinding extraArgs, int line) {
        super(null, init, args, line);
        type = init.classType;
        this.extraArgs = extraArgs;
    }

    void gen(Ctx ctx) {
        String name = method.classType.javaType.className();
        ctx.visitTypeInsn(NEW, name);
        ctx.visitInsn(DUP);
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
        ctx.genInt(count, line);
        ctx.visitLine(line);
        if (type.param[0].type != YetiType.JAVA) { // array of arrays
            ctx.visitTypeInsn(ANEWARRAY, JavaType.descriptionOf(type.param[0]));
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
            ctx.visitTypeInsn(ANEWARRAY, jt.className());
            return;
        default:
            throw new IllegalStateException("ARRAY<" + jt.description + '>');
        }
        ctx.visitIntInsn(NEWARRAY, t);
    }
}

final class MethodCall extends JavaExpr {
    private JavaType classType;

    MethodCall(Code object, JavaType.Method method, Code[] args, int line) {
        super(object, method, args, line);
        type = method.convertedReturnType();
    }

    void visitInvoke(Ctx ctx, int invokeInsn) {
        String className = classType.className();
        String descr = method.descr(null);
        String name = method.name;
        // XXX: not checking for package access. shouldn't matter.
        if ((method.access & ACC_PROTECTED) != 0 &&
                classType.implementation != null &&
                !object.flagop(DIRECT_THIS)) {
            if (invokeInsn != INVOKESTATIC) {
                descr = '(' + classType.description + descr.substring(1);
                invokeInsn = INVOKESTATIC;
            }
            name = classType.implementation.getAccessor(method, descr);
        }
        ctx.visitMethodInsn(invokeInsn, className, name, descr);
    }

    private void _gen(Ctx ctx) {
        classType = method.classType.javaType;
        int ins = object == null ? INVOKESTATIC : classType.isInterface()
                                 ? INVOKEINTERFACE : INVOKEVIRTUAL;
        if (object != null) {
            object.gen(ctx);
            if (ins != INVOKEINTERFACE)
                classType = object.type.deref().javaType;
            if (ctx.compilation.isGCJ || ins != INVOKEINTERFACE) {
                ctx.visitTypeInsn(CHECKCAST, classType.className());
            }
        }
        genCall(ctx, null, ins);
    }

    void gen(Ctx ctx) {
        _gen(ctx);
        if (method.returnType == YetiType.UNIT_TYPE) {
            ctx.visitInsn(ACONST_NULL);
        } else {
            convertValue(ctx, method.returnType);
        }
    }

    void genIf(Ctx ctx, Label to, boolean ifTrue) {
        if (method.returnType.javaType != null &&
                method.returnType.javaType.description == "Z") {
            _gen(ctx);
            ctx.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
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
        ctx.visitInsn(ATHROW);
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
            ctx.visitTypeInsn(CHECKCAST, className);
        // XXX: not checking for package access. shouldn't matter.
        if ((field.access & ACC_PROTECTED) != 0
                && classType.implementation != null
                && !object.flagop(DIRECT_THIS)) {
            descr = (object == null ? "()" : '(' + classType.description + ')')
                    + descr;
            String name = classType.implementation
                                   .getAccessor(field, descr, false);
            ctx.visitMethodInsn(INVOKESTATIC, className, name, descr);
        } else {
            ctx.visitFieldInsn(object == null ? GETSTATIC : GETFIELD,
                                 className, field.name, descr);
        }
        convertValue(ctx, field.type);
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
        JavaType classType = field.classType.javaType;
        String className = classType.className();
        if (object != null) {
            object.gen(ctx);
            ctx.visitTypeInsn(CHECKCAST, className);
            classType = object.type.deref().javaType;
        }
        genValue(ctx, setValue, field.type, line);
        String descr = JavaType.descriptionOf(field.type);
        if (descr.length() > 1) {
            ctx.visitTypeInsn(CHECKCAST,
                field.type.type == YetiType.JAVA
                    ? field.type.javaType.className() : descr);
        }
        
        if ((field.access & ACC_PROTECTED) != 0
                && classType.implementation != null
                && !object.flagop(DIRECT_THIS)) {
            descr = (object != null ? "(".concat(classType.description)
                                    : "(") + descr + ")V";
            String name = classType.implementation
                                   .getAccessor(field, descr, true);
            ctx.visitMethodInsn(INVOKESTATIC, className, name, descr);
        } else {
            ctx.visitFieldInsn(object == null ? PUTSTATIC : PUTFIELD,
                                 className, field.name, descr);
        }
        ctx.visitInsn(ACONST_NULL);
    }

    Code assign(final Code setValue) {
        if ((field.access & ACC_FINAL) != 0)
            return null;
        return new SimpleCode(this, setValue, null, 0);
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
        } else {
            object.gen(ctx);
        }
    }
}

final class LoadVar extends Code {
    int var;

    void gen(Ctx ctx) {
        ctx.visitVarInsn(ALOAD, var);
    }
}

final class VariantConstructor extends Code implements CodeGen {
    String name;

    VariantConstructor(YType type, String name) {
        this.type = type;
        this.name = name;
    }

    public void gen2(Ctx ctx, Code param, int line) {
        ctx.visitTypeInsn(NEW, "yeti/lang/TagCon");
        ctx.visitInsn(DUP);
        ctx.visitLdcInsn(name);
        ctx.visitInit("yeti/lang/TagCon", "(Ljava/lang/String;)V");
    }

    void gen(Ctx ctx) {
        ctx.constant("TAG:".concat(name), new SimpleCode(this, null, type, 0));
    }

    Code apply(Code arg, YType res, int line) {
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
        //ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        ctx.visitLdcInsn(name);
        ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                "get", "(Ljava/lang/String;)Ljava/lang/Object;");
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
        st.gen(ctx);
        ctx.visitLine(line);
        //ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        ctx.visitLdcInsn(name);
        setValue.gen(ctx);
        ctx.visitLine(line);
        ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
        ctx.visitInsn(ACONST_NULL);
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
            ctx.visitTypeInsn(NEW, "yeti/lang/Compose");
            ctx.visitInsn(DUP);
        }
        for (int i = names.length; --i >= 0;) {
            ctx.visitTypeInsn(NEW, "yeti/lang/Selector");
            ctx.visitInsn(DUP);
            ctx.visitLdcInsn(names[i]);
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
        if (ctx.compilation.isGCJ) {
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/ByKey");
        }
        key.gen(ctx);
        ctx.visitLine(line);
        ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey", "vget",
                              "(Ljava/lang/Object;)Ljava/lang/Object;");
    }

    public void gen2(Ctx ctx, Code setValue, int _) {
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
                ctx.visitJumpInsn(GOTO, end);
            } else {
                choices[i][0].gen(ctx);
            }
            ctx.visitLabel(jmpNext);
        }
        ctx.visitInsn(-1); // reset type
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

    class Ref extends BindRef {
        int arity;
        Ref next;

        void gen(Ctx ctx) {
            if (directBind) {
                st.gen(ctx);
            } else {
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
                    ctx.visitInsn(ACONST_NULL);
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
            return var ? BindExpr.this : null;
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
        return refs = res;
    }

    public Object captureIdentity() {
        return mvar == -1 ? (Object) this : closure;
    }

    public String captureType() {
        return mvar == -1 ? javaDescr : "[Ljava/lang/Object;";
    }

    public void genPreGet(Ctx ctx) {
        if (mvar == -1) {
            if (directField == null) {
                ctx.visitVarInsn(ALOAD, id);
                ctx.forceType(javaType);
            } else {
                ctx.visitFieldInsn(GETSTATIC, myClass, directField, javaDescr);
            }
        } else {
            ctx.visitVarInsn(ALOAD, mvar);
            ctx.forceType("[Ljava/lang/Object;");
        }
    }

    public void genGet(Ctx ctx) {
        if (mvar != -1) {
            ctx.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
            ctx.intConst(id);
            ctx.visitInsn(AALOAD);
        }
    }

    public void genSet(Ctx ctx, Code value) {
        if (directField == null) {
            ctx.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
            ctx.intConst(id);
            value.gen(ctx);
            ctx.visitInsn(AASTORE);
        } else {
            value.gen(ctx);
            ctx.visitFieldInsn(PUTSTATIC, myClass, directField, javaDescr);
        }
    }

    private void genLocalSet(Ctx ctx, Code value) {
        if (mvar == -1) {
            value.gen(ctx);
            if (!javaType.equals("java/lang/Object"))
                ctx.visitTypeInsn(CHECKCAST, javaType);
            if (directField == null) {
                ctx.visitVarInsn(ASTORE, id);
            } else {
                ctx.visitFieldInsn(PUTSTATIC, myClass, directField, javaDescr);
            }
        } else {
            ctx.visitVarInsn(ALOAD, mvar);
            ctx.intConst(id);
            value.gen(ctx);
            ctx.visitInsn(AASTORE);
        }
    }
    
    // called by Function.prepareConst when this bastard mutates into method
    void setArrayType() {
        javaDescr = javaType = "[Ljava/lang/Object;";
    }

    private void genBind(Ctx ctx) {
        javaType = javaType(st.type);
        javaDescr = 'L' + javaType + ';';
        if (!var && st.prepareConst(ctx) && evalId == -1) {
            directBind = true;
            return;
        }
        if (directField == "") {
            myClass = ctx.className;
            directField =
                "$".concat(Integer.toString(ctx.constants.ctx.fieldCounter++));
            ctx.cw.visitField(ACC_STATIC | ACC_SYNTHETIC, directField,
                              javaDescr, null, null).visitEnd();
        } else if (mvar == -1) {
            id = ctx.localVarCount++;
        }
        genLocalSet(ctx, st);
        if (evalId != -1) {
            ctx.intConst(evalId);
            genPreGet(ctx);
            if (mvar != -1)
                ctx.intConst(id);
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
    boolean checkUsed;
    private boolean used;

    LoadModule(String moduleName, ModuleType type) {
        this.type = type.type;
        this.moduleName = moduleName;
        moduleType = type;
        polymorph = true;
    }

    void gen(Ctx ctx) {
        if (checkUsed && !used)
            ctx.visitInsn(ACONST_NULL);
        else
            ctx.visitMethodInsn(INVOKESTATIC, moduleName,
                "eval", "()Ljava/lang/Object;");
    }

    Binder bindField(final String name, final YType type) {
        return new Binder() {
            public BindRef getRef(final int line) {
                if (!moduleType.directFields.containsKey(name)) {
                    used = true;
                    return new StaticRef(moduleName, mangle(name), type,
                                         this, true, line);
                }
                String directRef = (String) moduleType.directFields.get(name);
                if (directRef == null) { // property or mutable field
                    used = true;
                    final boolean mutable =
                        type.field == YetiType.FIELD_MUTABLE;
                    return new SelectMember(type, LoadModule.this,
                                            name, line, false) {
                        boolean mayAssign() {
                            return mutable;
                        }

                        boolean flagop(int fl) {
                            return (fl & DIRECT_BIND) != 0 ||
                                   (fl & ASSIGN) != 0 && mutable;
                        }
                    };
                }
                return new StaticRef(directRef, "_", type, this, true, line);
            }
        };
    }
}

final class StructField implements Opcodes {
    boolean property;
    boolean mutable;
    String name;
    Code value;
    Code setter;
    BindRef binder;
    String javaName;
    StructField nextProperty;

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object o) {
        StructField st;
        return o instanceof StructField &&
            name.equals((st = (StructField) o).name) &&
            mutable == st.mutable;
    }
}

/*
 * Being a closure allows inlining property getters/setters.
 */
final class StructConstructor extends CapturingClosure implements Comparator {
    StructField[] fields;
    int fieldCount;
    StructField properties;
    String impl;
    int arrayVar = -1;

    private class Bind extends BindRef implements Binder, CaptureWrapper {
        StructField field;
        boolean fun;
        boolean direct;
        boolean mutable;
        int var;

        Bind(StructField sf) {
            type = sf.value.type;
            binder = this;
            mutable = sf.mutable;
            fun = sf.value instanceof Function;
            field = sf;
        }

        void initGen(Ctx ctx) {
            if (prepareConst(ctx)) {
                direct = true;
                field.binder = null;
                return;
            }
            Function f;
            if (!mutable && fun) {
                ((Function) field.value).prepareGen(ctx);
                ctx.visitVarInsn(ASTORE, var = ctx.localVarCount++);
            } else {
                if (arrayVar == -1)
                    arrayVar = ctx.localVarCount++;
                var = arrayVar;
                field.binder = null;
            }
        }

        public BindRef getRef(int line) {
            field.binder = this;
            return this;
        }

        public CaptureWrapper capture() {
            return !fun || mutable ? this : null;
        }

        public boolean flagop(int fl) {
            if ((fl & ASSIGN) != 0)
                return mutable;
            if ((fl & PURE) != 0)
                return !mutable;
            if ((fl & DIRECT_BIND) != 0)
                return direct;
            if ((fl & CONST) != 0)
                return !mutable && field.value.flagop(CONST);
            return false;
        }

        boolean prepareConst(Ctx ctx) {
            return !mutable && field.value.prepareConst(ctx);
        }

        void gen(Ctx ctx) {
            if (direct)
                field.value.gen(ctx);
            else
                ctx.visitVarInsn(ALOAD, var);
        }

        public void genPreGet(Ctx ctx) {
            ctx.visitVarInsn(ALOAD, var);
        }

        public void genGet(Ctx ctx) {
            if (impl != null) {
                ctx.visitFieldInsn(GETFIELD, impl, field.javaName,
                                   "Ljava/lang/Object;");
                return;
            }
            ctx.visitLdcInsn(field.name);
            ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct", "get",
                                "(Ljava/lang/String;)Ljava/lang/Object;");
        }

        public void genSet(Ctx ctx, Code value) {
            if (impl != null) {
                value.gen(ctx);
                ctx.visitFieldInsn(PUTFIELD, impl, field.javaName,
                                   "Ljava/lang/Object;");
                return;
            }
            ctx.visitLdcInsn(field.name);
            value.gen(ctx);
            ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct", "set",
                                "(Ljava/lang/String;Ljava/lang/Object;)V");
        }

        public Object captureIdentity() {
            return StructConstructor.this;
        }

        public String captureType() {
            return impl != null ? 'L' + impl + ';' : "Lyeti/lang/Struct;";
        }
    }

    StructConstructor(int maxBinds) {
        fields = new StructField[maxBinds];
    }

    // for some reason, binds are only for non-property fields
    Binder bind(StructField sf) {
        return new Bind(sf);
    }

    void add(StructField field) {
        if (field.name == null)
            throw new IllegalArgumentException();
        fields[fieldCount++] = field;
        if (field.property) {
            field.nextProperty = properties;
            properties = field;
        }
    }

    public int compare(Object a, Object b) {
        return ((StructField) a).name.compareTo(((StructField) b).name);
    }

    void close() {
        Arrays.sort(fields, 0, fieldCount, this);
        for (int i = 0; i < fieldCount; ++i)
            fields[i].javaName = "_".concat(Integer.toString(i));
    }

    void publish() {
        for (int i = 0; i < fieldCount; ++i) {
            if (!fields[i].property) {
                Code v = fields[i].value;
                while (v instanceof BindRef)
                    v = ((BindRef) v).unref(true);
                if (v instanceof Function)
                    ((Function) v).publish = true;
            }
        }
    }

    Map getDirect() {
        Map r = new HashMap();
        for (int i = 0; i < fieldCount; ++i) {
            if (fields[i].mutable || fields[i].property) {
                r.put(fields[i].name, null);
                continue;
            }
            if (fields[i].binder != null)
                continue;
            Code v = fields[i].value;
            while (v instanceof BindRef)
                v = ((BindRef) v).unref(false);
            if (v instanceof Function && v.flagop(CONST))
                r.put(fields[i].name, ((Function) v).name);
        }
        return r;
    }

/*    public BindRef refProxy(BindRef code) {
        return code;
//        return code.flagop(DIRECT_BIND) ? code : captureRef(code);
    }*/

    void captureInit(Ctx st, Capture c, int n) {
        // c.getId() initialises the captures id as a side effect
        st.cw.visitField(ACC_SYNTHETIC, c.getId(st), c.captureType(),
                         null, null).visitEnd();
    }

    void gen(Ctx ctx) {
        boolean generated = false;
        // default: null - GenericStruct
        if (fieldCount <= 3) {
            impl = "yeti/lang/Struct3";
        } else if (fieldCount <= 6) {
            impl = "yeti/lang/Struct6";
        } else if (fieldCount <= 15) {
            impl = genStruct(ctx);
            generated = true;
        }
        for (int i = 0; i < fieldCount; ++i)
            if (fields[i].binder != null)
                ((Bind) fields[i].binder).initGen(ctx);
        String implClass = impl != null ? impl : "yeti/lang/GenericStruct";
        ctx.visitTypeInsn(NEW, implClass);
        ctx.visitInsn(DUP);
        if (generated) {
            ctx.visitInit(implClass, "()V");
        } else {
            ctx.constants.structInitArg(ctx, fields, fieldCount);
            ctx.visitInit(implClass, "([Ljava/lang/String;[Z)V");
        }
        if (arrayVar != -1)
            ctx.visitVarInsn(ASTORE, arrayVar);
        for (int i = 0, cnt = fieldCount; i < cnt; ++i) {
            if (fields[i].property)
                continue; // TODO
            if (arrayVar != -1) {
                ctx.visitVarInsn(ALOAD, arrayVar);
            } else {
                ctx.visitInsn(DUP);
            }
            if (impl == null)
                ctx.visitLdcInsn(fields[i].name);
            if (fields[i].binder != null) {
                fields[i].binder.gen(ctx);
                ((Function) fields[i].value).finishGen(ctx);
            } else {
                fields[i].value.gen(ctx);
            }
            if (impl != null) {
                ctx.visitFieldInsn(PUTFIELD, impl, fields[i].javaName,
                                   "Ljava/lang/Object;");
            } else {
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/GenericStruct",
                        "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
            }
        }
        if (arrayVar != -1)
            ctx.visitVarInsn(ALOAD, arrayVar);
        /*if (properties == null) {
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
                      "([Ljava/lang/Object;[Ljava/lang/Object;)V");*/
    }

    String genStruct(Ctx ctx) {
        Label next;
        int i;
        String cn = ctx.compilation.createClassName(ctx, ctx.className, "");
        Ctx st = ctx.newClass(ACC_SUPER | ACC_FINAL, cn,
                              "yeti/lang/AStruct", null);
        Ctx m = st.newMethod(ACC_PUBLIC, "<init>", "()V");
        m.visitVarInsn(ALOAD, 0); // this.
        m.constants.structInitArg(m, fields, fieldCount);
        m.visitInit("yeti/lang/AStruct", "([Ljava/lang/String;[Z)V");
        m.visitInsn(RETURN);
        m.closeMethod();

        // fields
        for (i = 0; i < fieldCount; ++i) {
            st.cw.visitField(ACC_SYNTHETIC, fields[i].javaName,
                    "Ljava/lang/Object;", null, null).visitEnd();
        }

        // get(String)
        m = st.newMethod(ACC_PUBLIC, "get",
                         "(Ljava/lang/String;)Ljava/lang/Object;");
        m.visitVarInsn(ALOAD, 0);
        for (i = 0; i < fieldCount; ++i) {
            next = new Label();
            m.visitVarInsn(ALOAD, 1);
            m.visitLdcInsn(fields[i].name);
            m.visitJumpInsn(IF_ACMPNE, next);
            m.visitFieldInsn(GETFIELD, cn, fields[i].javaName,
                             "Ljava/lang/Object;");
            m.visitInsn(ARETURN);
            m.visitLabel(next);
        }
        m.visitTypeInsn(NEW, "java/lang/NoSuchFieldException");
        m.visitInsn(DUP);
        m.visitVarInsn(ALOAD, 1);
        m.visitInit("java/lang/NoSuchFieldException", "(Ljava/lang/String;)V");
        m.visitInsn(ATHROW);
        m.closeMethod();

        // get(int)
        m = st.newMethod(ACC_PUBLIC, "get", "(I)Ljava/lang/Object;");
        m.visitVarInsn(ALOAD, 0);
        m.visitVarInsn(ILOAD, 1);
        Label[] jumps = new Label[fieldCount];
        int mutableCount = 0;
        for (i = 0; i < fieldCount; ++i) {
            jumps[i] = new Label();
            if (fields[i].mutable)
                ++mutableCount;
        }
        Label dflt = new Label();
        m.visitSwitchInsn(0, fieldCount - 1, dflt, null, jumps);
        for (i = 0; i < fieldCount; ++i) {
            m.visitLabel(jumps[i]);
            m.visitFieldInsn(GETFIELD, cn, fields[i].javaName,
                             "Ljava/lang/Object;");
            m.visitInsn(ARETURN);
        }
        m.visitLabel(dflt);
        m.visitInsn(ACONST_NULL);
        m.visitInsn(ARETURN);
        m.closeMethod();

        if (mutableCount != 0) {
            // set(String, Object)
            m = st.newMethod(ACC_PUBLIC, "set",
                             "(Ljava/lang/String;Ljava/lang/Object;)V");
            m.visitVarInsn(ALOAD, 0);
            m.visitVarInsn(ALOAD, 2);
            for (i = 0; i < fieldCount; ++i) {
                if (!fields[i].mutable)
                    continue;
                next = new Label();
                m.visitVarInsn(ALOAD, 1);
                m.visitLdcInsn(fields[i].name);
                m.visitJumpInsn(IF_ACMPNE, next);
                m.visitFieldInsn(PUTFIELD, cn, fields[i].javaName,
                                 "Ljava/lang/Object;");
                m.visitInsn(RETURN);
                m.visitLabel(next);
            }
            m.visitInsn(POP2);
            m.visitInsn(RETURN);
            m.closeMethod();
        }
        return cn;
    }
}

final class WithStruct extends Code {
    private Code src;
    private Code override;
    private String[] names;

    WithStruct(YType type, Code src, Code override, String[] names) {
        this.type = type;
        this.src = src;
        this.override = override;
        this.names = names;
    }

    void gen(Ctx ctx) {
        override.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct3");
        src.gen(ctx);
        if (override instanceof StructConstructor) {
            ctx.visitInsn(ACONST_NULL);
        } else {
            ctx.intConst(names.length);
            ctx.visitTypeInsn(ANEWARRAY, "java/lang/String");
            for (int i = 0; i < names.length; ++i) {
                ctx.visitInsn(DUP);
                ctx.intConst(i);
                ctx.visitLdcInsn(names[i]);
                ctx.visitInsn(AASTORE);
            }
        }
        ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Struct", "with",
                "(Ljava/lang/Object;[Ljava/lang/String;)Lyeti/lang/Struct;");
        ctx.forceType("yeti/lang/Struct");
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
        return //(fl & (CONST | PURE)) != 0 ||
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
        ctx.visitTypeInsn(NEW, "yeti/lang/Hash");
        ctx.visitInsn(DUP);
        if (keyItems.length > 16) {
            ctx.intConst(keyItems.length);
            ctx.visitInit("yeti/lang/Hash", "(I)V");
        } else {
            ctx.visitInit("yeti/lang/Hash", "()V");
        }
        for (int i = 0; i < keyItems.length; ++i) {
            ctx.visitInsn(DUP);
            keyItems[i].gen(ctx);
            items[i].gen(ctx);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            ctx.visitInsn(POP);
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
        ctx.visitInsn(ACONST_NULL);
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
