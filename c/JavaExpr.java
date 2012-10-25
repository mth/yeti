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

import yeti.renamed.asm3.*;

class JavaExpr extends Code {
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

    // convert to java
    private static void convert(Ctx ctx, YType given, YType argType) {
        given = given.deref();
        argType = argType.deref();
        String descr = argType.javaType == null
                        ? "" : argType.javaType.description;
        if (argType.type == YetiType.JAVA_ARRAY &&
            given.type == YetiType.JAVA_ARRAY) {
            ctx.typeInsn(CHECKCAST, JavaType.descriptionOf(argType));
            return; // better than thinking, that array was given...
                    // still FIXME for a case of different arrays
        }
        if (given.type != YetiType.JAVA &&
            (argType.type == YetiType.JAVA_ARRAY ||
             argType.type == YetiType.JAVA &&
                argType.javaType.isCollection())) {
            Label retry = new Label(), end = new Label();
            ctx.typeInsn(CHECKCAST, "yeti/lang/AIter"); // i
            String tmpClass = descr != "Ljava/lang/Set;"
                ? "java/util/ArrayList" : "java/util/HashSet";
            ctx.typeInsn(NEW, tmpClass); // ia
            ctx.insn(DUP);               // iaa
            ctx.visitInit(tmpClass, "()V"); // ia
            ctx.insn(SWAP); // ai
            ctx.insn(DUP); // aii
            ctx.jumpInsn(IFNULL, end); // ai
            ctx.insn(DUP); // aii
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "isEmpty", "()Z"); // aiz
            ctx.jumpInsn(IFNE, end); // ai
            ctx.visitLabel(retry);
            ctx.insn(DUP2); // aiai
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "first", "()Ljava/lang/Object;");
            YType t = null;
            if (argType.param.length != 0 &&
                ((t = argType.param[0]).type != YetiType.JAVA ||
                 t.javaType.description.length() > 1)) {
                convert(ctx, given.param[0], argType.param[0]);
            }
            // aiav
            ctx.methodInsn(INVOKEVIRTUAL, tmpClass,
                                "add", "(Ljava/lang/Object;)Z"); // aiz
            ctx.insn(POP); // ai
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "next", "()Lyeti/lang/AIter;"); // ai
            ctx.insn(DUP); // aii
            ctx.jumpInsn(IFNONNULL, retry); // ai
            ctx.visitLabel(end);
            ctx.insn(POP); // a
            if (argType.type != YetiType.JAVA_ARRAY)
                return; // a - List/Set

            String s = "";
            YType argArrayType = argType;
            while ((argType = argType.param[0]).type ==
                        YetiType.JAVA_ARRAY) {
                s += "[";
                argArrayType = argType;
            }
            String arrayPrefix = s;
            if (s == "" && argType.javaType.description.length() != 1) {
                s = argType.javaType.className();
            } else {
                s += argType.javaType.description;
            }
            ctx.insn(DUP); // aa
            ctx.methodInsn(INVOKEVIRTUAL, tmpClass,
                                "size", "()I"); // an

            if (t.type != YetiType.JAVA ||
                (descr = t.javaType.description).length() != 1) {
                ctx.typeInsn(ANEWARRAY, s); // aA
                ctx.methodInsn(INVOKEVIRTUAL, tmpClass, "toArray",
                    "([Ljava/lang/Object;)[Ljava/lang/Object;");
                if (!s.equals("java/lang/Object")) {
                    ctx.typeInsn(CHECKCAST,
                        arrayPrefix + "[" + argType.javaType.description);
                }
                return; // A - object array
            }

            // emulate a fucking for loop to fill primitive array
            int index = ctx.localVarCount++;
            Label next = new Label(), done = new Label();
            ctx.insn(DUP); // ann
            ctx.varInsn(ISTORE, index); // an
            new NewArrayExpr(argArrayType, null, 0).gen(ctx);
            ctx.insn(SWAP); // Aa
            ctx.visitLabel(next);
            ctx.varInsn(ILOAD, index); // Aan
            ctx.jumpInsn(IFEQ, done); // Aa
            ctx.visitIntInsn(IINC, index); // Aa --index
            ctx.insn(DUP2); // AaAa
            ctx.varInsn(ILOAD, index); // AaAan
            ctx.methodInsn(INVOKEVIRTUAL, tmpClass,
                                "get", "(I)Ljava/lang/Object;"); // AaAv
            if (descr == "Z") {
                ctx.typeInsn(CHECKCAST, "java/lang/Boolean");
                ctx.methodInsn(INVOKEVIRTUAL, "java/lang/Boolean",
                                    "booleanValue", "()Z");
            } else {
                ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
                convertNum(ctx, descr);
            }
            ctx.varInsn(ILOAD, index); // AaAvn
            ctx.insn(SWAP); // AaAnv
            int insn = BASTORE;
            switch (argType.javaType.description.charAt(0)) {
                case 'D': insn = DASTORE; break;
                case 'F': insn = FASTORE; break;
                case 'I': insn = IASTORE; break;
                case 'J': insn = LASTORE; break;
                case 'S': insn = SASTORE;
            }
            ctx.insn(insn); // Aa
            ctx.jumpInsn(GOTO, next); // Aa
            ctx.visitLabel(done);
            ctx.insn(POP); // A
            return; // A - primitive array
        }

        if (given.type == YetiType.STR) {
            ctx.typeInsn(CHECKCAST, "java/lang/String");
            ctx.insn(DUP);
            ctx.fieldInsn(GETSTATIC, "yeti/lang/Core", "UNDEF_STR",
                               "Ljava/lang/String;");
            Label defined = new Label();
            ctx.jumpInsn(IF_ACMPNE, defined);
            ctx.insn(POP);
            ctx.insn(ACONST_NULL);
            ctx.visitLabel(defined);
            return;
        }

        if (given.type != YetiType.NUM ||
            descr == "Ljava/lang/Object;" ||
            descr == "Ljava/lang/Number;") {
            if (descr != "Ljava/lang/Object;") {
                ctx.typeInsn(CHECKCAST, argType.javaType.className());
            }
            return;
        }
        // Convert numbers...
        ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
        if (descr == "Ljava/math/BigInteger;") {
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigInteger", "()Ljava/math/BigInteger;");
            return;
        }
        if (descr == "Ljava/math/BigDecimal;") {
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    "toBigDecimal", "()Ljava/math/BigDecimal;");
            return;
        }
        String newInstr = null;
        if (descr.startsWith("Ljava/lang/")) {
            newInstr = argType.javaType.className();
            ctx.typeInsn(NEW, newInstr);
            ctx.insn(DUP_X1);
            ctx.insn(SWAP);
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
            case 'L': if (descr == "Lyeti/lang/Num;")
                          return;
            case 'J': method = "longValue"; break;
            case 'S': method = "shortValue"; break;
        }
        ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                            method, "()" + descr);
    }

    // MethodCall overrides it
    void visitInvoke(Ctx ctx, int invokeInsn) {
        ctx.methodInsn(invokeInsn, method.classType.javaType.className(),
                            method.name, method.descr(null));
    }

    void genCall(Ctx ctx, BindRef[] extraArgs, int invokeInsn) {
        for (int i = 0; i < args.length; ++i) {
            convertedArg(ctx, args[i], method.arguments[i], line);
        }
        if (extraArgs != null) {
            for (int i = 0; i < extraArgs.length; ++i) {
                BindRef arg = extraArgs[i];
                CaptureWrapper cw = arg.capture();
                if (cw == null) {
                    arg.gen(ctx);
                    ctx.captureCast(arg.captureType());
                } else {
                    cw.genPreGet(ctx);
                }
            }
        }
        ctx.visitLine(line);
        visitInvoke(ctx, invokeInsn);
        JavaType jt = method.returnType.javaType;
        if (jt != null && jt.description.charAt(0) == 'L')
            ctx.forceType(jt.className());
    }

    static void convertedArg(Ctx ctx, Code arg, YType argType, int line) {
        String desc;
        argType = argType.deref();
        if (arg instanceof NumericConstant && argType.type == YetiType.JAVA &&
            ((desc = argType.javaType.description) == "I" || desc == "J") &&
            ((NumericConstant) arg).genInt(ctx, desc == "I")) {
            return; // integer arguments can be directly generated
        }
        if (genRawArg(ctx, arg, argType, line))
            convert(ctx, arg.type, argType);
        else if (argType.type == YetiType.STR)
            convertValue(ctx, arg.type.deref()); // for as cast
    }

    private static boolean genRawArg(Ctx ctx, Code arg,
                                     YType argType, int line) {
        YType given = arg.type.deref();
        String descr =
            argType.javaType == null ? null : argType.javaType.description;
        if (descr == "Z") {
            // boolean
            Label end = new Label(), lie = new Label();
            arg.genIf(ctx, lie, false);
            ctx.intConst(1);
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(lie);
            ctx.intConst(0);
            ctx.visitLabel(end);
            return false;
        }
        arg.gen(ctx);
        if (given.type == YetiType.UNIT) {
            if (!(arg instanceof UnitConstant)) {
                ctx.insn(POP);
                ctx.insn(ACONST_NULL);
            }
            return false;
        }
        ctx.visitLine(line);
        if (descr == "C") {
            ctx.typeInsn(CHECKCAST, "java/lang/String");
            ctx.intConst(0);
            ctx.methodInsn(INVOKEVIRTUAL,
                    "java/lang/String", "charAt", "(I)C");
            return false;
        }
        if (argType.type == YetiType.JAVA_ARRAY &&
            given.type == YetiType.STR) {
            ctx.typeInsn(CHECKCAST, "java/lang/String");
            ctx.methodInsn(INVOKEVIRTUAL,
                "java/lang/String", "toCharArray", "()[C");
            return false;
        }
        if (arg instanceof StringConstant || arg instanceof ConcatStrings)
            return false;
        // conversion from array to list
        if (argType.type == YetiType.MAP && given.type == YetiType.JAVA_ARRAY) {
            String javaItem = given.param[0].javaType.description;
            if (javaItem.length() == 1) {
                String arrayType = "[".concat(javaItem);
                ctx.typeInsn(CHECKCAST, arrayType);
                ctx.methodInsn(INVOKESTATIC, "yeti/lang/PArray",
                               "wrap", "(" + arrayType + ")Lyeti/lang/AList;");
                return false;
            }
            Label isNull = new Label(), end = new Label();
            ctx.typeInsn(CHECKCAST, "[Ljava/lang/Object;");
            ctx.insn(DUP);
            ctx.jumpInsn(IFNULL, isNull);
            boolean toList = argType.param[1].deref().type == YetiType.NONE;
            if (toList) {
                ctx.insn(DUP);
                ctx.insn(ARRAYLENGTH);
                ctx.jumpInsn(IFEQ, isNull);
            }
            if (toList && argType.param[0].deref().type == YetiType.STR) {
                // convert null's to undef_str's
                ctx.methodInsn(INVOKESTATIC, "yeti/lang/MList", "ofStrArray",
                               "([Ljava/lang/Object;)Lyeti/lang/MList;");
            } else {
                ctx.typeInsn(NEW, "yeti/lang/MList");
                ctx.insn(DUP_X1);
                ctx.insn(SWAP);
                ctx.visitInit("yeti/lang/MList", "([Ljava/lang/Object;)V");
            }
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(isNull);
            ctx.insn(POP);
            if (toList) {
                ctx.insn(ACONST_NULL);
            } else {
                ctx.typeInsn(NEW, "yeti/lang/MList");
                ctx.insn(DUP);
                ctx.visitInit("yeti/lang/MList", "()V");
            }
            ctx.visitLabel(end);
            return false;
        }
        return argType.type == YetiType.JAVA ||
               argType.type == YetiType.JAVA_ARRAY;
    }

    static void genValue(Ctx ctx, Code arg, YType argType, int line) {
        genRawArg(ctx, arg, argType, line);
        if (arg.type.deref().type == YetiType.NUM &&
            argType.javaType.description.length() == 1) {
            convertNum(ctx, argType.javaType.description);
        }
    }

    static void convertValue(Ctx ctx, YType t) {
        if (t.type != YetiType.JAVA) {
            return; // array, no automatic conversions
        }
        String descr = t.javaType.description;
        if (descr == "V") {
            ctx.insn(ACONST_NULL);
        } else if (descr == "Ljava/lang/String;") {
            Label nonnull = new Label();
            ctx.insn(DUP);
            ctx.jumpInsn(IFNONNULL, nonnull);
            ctx.insn(POP);
            ctx.fieldInsn(GETSTATIC, "yeti/lang/Core", "UNDEF_STR",
                          "Ljava/lang/String;");
            ctx.visitLabel(nonnull);
        } else if (descr == "Z") {
            Label skip = new Label(), end = new Label();
            ctx.jumpInsn(IFEQ, skip);
            ctx.fieldInsn(GETSTATIC, "java/lang/Boolean", "TRUE",
                          "Ljava/lang/Boolean;");
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(skip);
            ctx.fieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE",
                          "Ljava/lang/Boolean;");
            ctx.visitLabel(end);
        } else if (descr == "B" || descr == "S" ||
                   descr == "I" || descr == "J") {
            ctx.typeInsn(NEW, "yeti/lang/IntNum");
            if (descr == "J") {
                ctx.insn(DUP_X2);
                ctx.insn(DUP_X2);
                ctx.insn(POP);
            } else {
                ctx.insn(DUP_X1);
                ctx.insn(SWAP);
            }
            ctx.visitInit("yeti/lang/IntNum",
                          descr == "J" ? "(J)V" : "(I)V");
            ctx.forceType("yeti/lang/Num");
        } else if (descr == "D" || descr == "F") {
            ctx.typeInsn(NEW, "yeti/lang/FloatNum");
            if (descr == "F") {
                ctx.insn(DUP_X1);
                ctx.insn(SWAP);
                ctx.insn(F2D);
            } else {
                ctx.insn(DUP_X2);
                ctx.insn(DUP_X2);
                ctx.insn(POP);
            }
            ctx.visitInit("yeti/lang/FloatNum", "(D)V");
            ctx.forceType("yeti/lang/Num");
        } else if (descr == "C") {
            ctx.methodInsn(INVOKESTATIC, "java/lang/String",
                           "valueOf", "(C)Ljava/lang/String;");
            ctx.forceType("java/lang/String");
        }
    }

    void gen(Ctx ctx) {
        throw new UnsupportedOperationException();
    }
}
