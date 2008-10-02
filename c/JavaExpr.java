// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator for java foreign interface.
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

abstract class JavaExpr extends Code {
    Code object;
    JavaType.Method method;
    Code[] args;
    int line;

    JavaExpr(Code object, JavaType.Method method, Code[] args, int line) {
        this.object = object;
        this.method = method;
        this.args = args;
        this.line = line;
    }

    private static void convert(Ctx ctx, YetiType.Type given,
                         YetiType.Type argType) {
        given = given.deref();
        argType = argType.deref();
        String descr = argType.javaType == null
                        ? "" : argType.javaType.description;
        if (argType.type == YetiType.JAVA_ARRAY &&
            given.type == YetiType.JAVA_ARRAY) {
            ctx.visitTypeInsn(CHECKCAST, JavaType.descriptionOf(argType));
            return; // better than thinking, that array was given...
                    // still FIXME for a case of different arrays
        }
        if (given.type != YetiType.JAVA &&
            (argType.type == YetiType.JAVA_ARRAY ||
             argType.type == YetiType.JAVA &&
                argType.javaType.isCollection())) {
            Label retry = new Label(), end = new Label();
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AIter"); // i
            String tmpClass = descr != "Ljava/lang/Set;"
                ? "java/util/ArrayList" : "java/util/HashSet";
            ctx.visitTypeInsn(NEW, tmpClass); // ia
            ctx.visitInsn(DUP);               // iaa
            ctx.visitInit(tmpClass, "()V"); // ia
            ctx.visitInsn(SWAP); // ai
            ctx.visitInsn(DUP); // aii
            ctx.visitJumpInsn(IFNULL, end); // ai
            ctx.visitInsn(DUP); // aii
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "isEmpty", "()Z"); // aiz
            ctx.visitJumpInsn(IFNE, end); // ai
            ctx.visitLabel(retry);
            ctx.visitInsn(DUP2); // aiai
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "first", "()Ljava/lang/Object;");
            YetiType.Type t = null;
            if (argType.param.length != 0 &&
                ((t = argType.param[0]).type != YetiType.JAVA ||
                 t.javaType.description.length() > 1)) {
                convert(ctx, given.param[0], argType.param[0]);
            }
            // aiav
            ctx.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                "add", "(Ljava/lang/Object;)Z"); // aiz
            ctx.visitInsn(POP); // ai
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "next", "()Lyeti/lang/AIter;"); // ai
            ctx.visitInsn(DUP); // aii
            ctx.visitJumpInsn(IFNONNULL, retry); // ai
            ctx.visitLabel(end);
            ctx.visitInsn(POP); // a
            if (argType.type != YetiType.JAVA_ARRAY)
                return; // a - List/Set

            String s = "";
            while ((argType = argType.param[0]).type ==
                        YetiType.JAVA_ARRAY) {
                s += "[";
            }
            String arrayPrefix = s;
            if (s == "") {
                s = argType.javaType.className();
            } else {
                s += argType.javaType.description;
            }
            ctx.visitInsn(DUP); // aa
            ctx.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                "size", "()I"); // an

            if (t.type != YetiType.JAVA ||
                (descr = t.javaType.description).length() != 1) {
                ctx.visitTypeInsn(ANEWARRAY, s); // aA
                ctx.visitMethodInsn(INVOKEVIRTUAL, tmpClass, "toArray",
                    "([Ljava/lang/Object;)[Ljava/lang/Object;");
                if (!s.equals("java/lang/Object")) {
                    ctx.visitTypeInsn(CHECKCAST,
                        arrayPrefix + "[" + argType.javaType.description);
                }
                return; // A - object array
            }

            // emulate a fucking for loop to fill primitive array
            int index = ctx.localVarCount++;
            Label next = new Label(), done = new Label();
            ctx.visitInsn(DUP); // ann
            ctx.visitVarInsn(ISTORE, index); // an
            ctx.visitTypeInsn(ANEWARRAY, s); // aA
            ctx.visitInsn(SWAP); // Aa
            ctx.visitLabel(next);
            ctx.visitVarInsn(ILOAD, index); // Aan
            ctx.visitJumpInsn(IFEQ, done); // Aa
            ctx.intConst(-1); // Aa1
            ctx.visitVarInsn(IINC, index); // Aa
            ctx.visitInsn(DUP2); // AaAa
            ctx.visitVarInsn(ILOAD, index); // AaAan
            ctx.visitMethodInsn(INVOKEVIRTUAL, tmpClass,
                                "get", "(I)Ljava/lang/Object;"); // AaAv
            if (descr == "Z") {
                ctx.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                ctx.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean",
                                    "booleanValue", "()Z");
            } else {
                ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                convertNum(ctx, descr);
            }
            ctx.visitVarInsn(ILOAD, index); // AaAvn
            ctx.visitInsn(SWAP); // AaAnv
            int insn = BASTORE;
            switch (argType.javaType.description.charAt(0)) {
                case 'D': insn = DASTORE; break;
                case 'F': insn = FASTORE; break;
                case 'I': insn = IASTORE; break;
                case 'J': insn = LASTORE; break;
                case 'S': insn = SASTORE;
            }
            ctx.visitInsn(insn); // Aa
            ctx.visitJumpInsn(GOTO, next); // Aa
            ctx.visitLabel(done);
            ctx.visitInsn(POP); // A
            return; // A - primitive array
        }

        if (given.type != YetiType.NUM ||
            descr == "Ljava/lang/Object;" ||
            descr == "Ljava/lang/Number;") {
            if (descr != "Ljava/lang/Object;") {
                ctx.visitTypeInsn(CHECKCAST, argType.javaType.className());
            }
            return;
        }
        // Convert numbers...
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
        if (descr == "Ljava/math/BigInteger;") {
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigInteger", "()Ljava/math/BigInteger;");
            return;
        }
        if (descr == "Ljava/math/BigDecimal;") {
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigDecimal", "()Ljava/math/BigDecimal;");
            return;
        }
        String newInstr = null;
        if (descr.startsWith("Ljava/lang/")) {
            newInstr = argType.javaType.className();
            ctx.visitTypeInsn(NEW, newInstr);
            ctx.visitInsn(DUP);
            descr = descr.substring(11, 12);
        }
        convertNum(ctx, descr);
        if (newInstr != null) {
            ctx.visitInit(newInstr, "(" + descr + ")V");
        }
    }

    private static void convertNum(Ctx ctx, String descr) {
        String method = null;
        switch (descr.charAt(0)) {
            case 'B': method = "byteValue"; break;
            case 'D': method = "doubleValue"; break;
            case 'F': method = "floatValue"; break;
            case 'I': method = "intValue"; break;
            case 'L':
            case 'J': method = "longValue"; break;
            case 'S': method = "shortValue"; break;
        }
        ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                            method, "()" + descr);
    }

    void genCall(Ctx ctx, int invokeInsn) {
        for (int i = 0; i < args.length; ++i) {
            convertedArg(ctx, args[i], method.arguments[i], line);
        }
        ctx.visitLine(line);
        ctx.visitMethodInsn(invokeInsn, method.classType.javaType.className(),
                            method.name, method.descr());
    }

    static void convertedArg(Ctx ctx, Code arg, YetiType.Type argType,
                             int line) {
        if (genRawArg(ctx, arg, argType, line))
            convert(ctx, arg.type, argType);
    }

    private static boolean genRawArg(Ctx ctx, Code arg,
                                     YetiType.Type argType, int line) {
        YetiType.Type given = arg.type.deref();
        String descr =
            argType.javaType == null ? null : argType.javaType.description;
        if (descr == "Z") {
            // boolean
            Label end = new Label(), lie = new Label();
            arg.genIf(ctx, lie, false);
            ctx.intConst(1);
            ctx.visitJumpInsn(GOTO, end);
            ctx.visitLabel(lie);
            ctx.intConst(0);
            ctx.visitLabel(end);
            return false;
        }
        arg.gen(ctx);
        if (given.type == YetiType.UNIT) {
            if (!(arg instanceof UnitConstant)) {
                ctx.visitInsn(POP);
                ctx.visitInsn(ACONST_NULL);
            }
            return false;
        }
        if (given.type == YetiType.JAVA) {
            return true;
        }
        ctx.visitLine(line);
        if (descr == "C") {
            ctx.visitTypeInsn(CHECKCAST, "java/lang/String");
            ctx.intConst(0);
            ctx.visitMethodInsn(INVOKEVIRTUAL,
                    "java/lang/String", "charAt", "(I)C");
            return false;
        }
        if (argType.type == YetiType.JAVA_ARRAY &&
            given.type == YetiType.STR) {
            ctx.visitTypeInsn(CHECKCAST, "java/lang/String");
            ctx.visitMethodInsn(INVOKEVIRTUAL,
                "java/lang/String", "toCharArray", "()[C");
            return false;
        }
        return true;
    }

    static void genValue(Ctx ctx, Code arg, YetiType.Type argType, int line) {
        genRawArg(ctx, arg, argType, line);
        if (arg.type.type == YetiType.NUM &&
            argType.javaType.description.length() == 1) {
            convertNum(ctx, argType.javaType.description);
        }
    }

    static void convertValue(Ctx ctx, YetiType.Type t) {
        if (t.type != YetiType.JAVA) {
            return; // array, no automatic conversions
        }
        String descr = t.javaType.description;
        if (descr == "V") {
            ctx.visitInsn(ACONST_NULL);
        } else if (descr == "Ljava/lang/String;") {
            Label nonnull = new Label();
            ctx.visitInsn(DUP);
            ctx.visitJumpInsn(IFNONNULL, nonnull);
            ctx.visitInsn(POP);
            ctx.visitFieldInsn(GETSTATIC, "yeti/lang/Core", "UNDEF_STR",
                               "Ljava/lang/String;");
            ctx.visitLabel(nonnull);
        } else if (descr == "Z") {
            Label skip = new Label(), end = new Label();
            ctx.visitJumpInsn(IFEQ, skip);
            ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TRUE",
                                 "Ljava/lang/Boolean;");
            ctx.visitJumpInsn(GOTO, end);
            ctx.visitLabel(skip);
            ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE",
                               "Ljava/lang/Boolean;");
            ctx.visitLabel(end);
        } else if (descr == "B" || descr == "S" ||
                   descr == "I" || descr == "J") {
            ctx.visitTypeInsn(NEW, "yeti/lang/IntNum");
            if (descr == "J") {
                ctx.visitInsn(DUP_X2);
                ctx.visitInsn(DUP_X2);
                ctx.visitInsn(POP);
            } else {
                ctx.visitInsn(DUP_X1);
                ctx.visitInsn(SWAP);
            }
            ctx.visitInit("yeti/lang/IntNum",
                          descr == "J" ? "(J)V" : "(I)V");
            ctx.forceType("yeti/lang/Num");
        } else if (descr == "D" || descr == "F") {
            ctx.visitTypeInsn(NEW, "yeti/lang/FloatNum");
            if (descr == "F") {
                ctx.visitInsn(DUP_X1);
                ctx.visitInsn(SWAP);
                ctx.visitInsn(F2D);
            } else {
                ctx.visitInsn(DUP_X2);
                ctx.visitInsn(DUP_X2);
                ctx.visitInsn(POP);
            }
            ctx.visitInit("yeti/lang/FloatNum", "(D)V");
            ctx.forceType("yeti/lang/Num");
        } else if (descr == "C") {
            ctx.visitMethodInsn(INVOKESTATIC, "java/lang/String",
                                "valueOf", "(C)Ljava/lang/String;");
        }
    }
}
