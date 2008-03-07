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

interface YetiBuiltins extends YetiCode {
    int COND_EQ  = 0;
    int COND_NOT = 1;
    int COND_LT  = 2;
    int COND_GT  = 4;
    int COND_LE  = COND_NOT | COND_GT;
    int COND_GE  = COND_NOT | COND_LT;

    class Argv implements Binder, DirectBind {
        public BindRef getRef(int line) {
            return new BindRef() {
                { type = YetiType.STRING_ARRAY; }

                void gen(Ctx ctx) {
                    ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                         "ARGV", "Ljava/lang/ThreadLocal;");
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                        "java/lang/ThreadLocal",
                        "get", "()Ljava/lang/Object;");
                }

                Code assign(final Code value) {
                    return new Code() {
                        void gen(Ctx ctx) {
                            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                         "ARGV", "Ljava/lang/ThreadLocal;");
                            value.gen(ctx);
                            ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                                "java/lang/ThreadLocal",
                                "get", "(Ljava/lang/Object;)V");
                            ctx.m.visitInsn(ACONST_NULL);
                        }
                    };
                }

                public boolean assign() {
                    return true;
                }
            };
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

    abstract class Bind1Arg implements Binder, Opcodes {
        private String libName;
        private YetiType.Type type;
        boolean polymorph = true;

        Bind1Arg(YetiType.Type type, String libName) {
            this.type = type;
            this.libName = libName;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/std", libName,
                                 type, this, polymorph, line) {
                Code apply(final Code arg, final YetiType.Type res, int line) {
                    return new Code() {
                        { type = res; }

                        void gen(Ctx ctx) {
                            Bind1Arg.this.gen(ctx, arg);
                        }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            Bind1Arg.this.genIf(ctx, arg, to, ifTrue);
                        }
                    };
                }
            };
        }

        void gen(Ctx ctx, Code arg) {
            arg.gen(ctx);
        }

        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue) {
            throw new UnsupportedOperationException();
        }
    }

    class Ignore extends Bind1Arg {
        Ignore() {
            super(YetiType.A_TO_UNIT, "ignore");
        }
    }

    class IsNullPtr extends Bind1Arg {
        IsNullPtr() {
            super(YetiType.A_TO_BOOL, "raw_nullptr?");
        }

        void gen(Ctx ctx, Code arg) {
            Label label = new Label();
            genIf(ctx, arg, label, false);
            ctx.genBoolean(label);
        }

        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue) {
            arg.gen(ctx);
            ctx.m.visitJumpInsn(ifTrue ? IFNULL : IFNONNULL, to);
        }
    }

    class IsDefined extends IsNullPtr {
        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue) {
            Label isNull = new Label(), end = new Label();
            arg.gen(ctx);
            ctx.m.visitInsn(DUP);
            ctx.m.visitJumpInsn(IFNULL, isNull);
            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                 "UNDEF_STR", "Ljava/lang/String;");
            ctx.m.visitJumpInsn(IF_ACMPEQ, ifTrue ? end : to);
            ctx.m.visitJumpInsn(GOTO, ifTrue ? to : end);
            ctx.m.visitLabel(isNull);
            ctx.m.visitInsn(POP);
            if (!ifTrue) {
                ctx.m.visitJumpInsn(GOTO, to);
            }
            ctx.m.visitLabel(end);
        }
    }

    class Negate extends StaticRef implements Binder {
        Negate() {
            super("yeti/lang/std", "negate", YetiType.NUM_TO_NUM,
                  null, false, 0);
            binder = this;
        }

        public BindRef getRef(int line) {
            return this;
        }

        Code apply(final Code arg1, final YetiType.Type res1, final int line) {
            if (arg1 instanceof NumericConstant) {
                return new NumericConstant(((NumericConstant) arg1)
                            .num.subFrom(0));
            }
            return new Code() {
                { type = YetiType.NUM_TYPE; }

                void gen(Ctx ctx) {
                    arg1.gen(ctx);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                    ctx.m.visitLdcInsn(new Long(0));
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                          "subFrom", "(J)Lyeti/lang/Num;");
                }
            };
        }
    }

    abstract class Bind2Core implements Binder, Opcodes {
        String lib = "yeti/lang/std";
        private String coreFun;
        YetiType.Type type;

        Bind2Core(String fun, YetiType.Type type) {
            coreFun = fun;
            this.type = type;
        }

        public BindRef getRef(int line) {
            return new StaticRef(lib, coreFun, type, this, true, line) {
                Code apply(final Code arg1, YetiType.Type res, int line1) {
                    return new Apply(res, this, arg1, line1) {
                        Code apply(final Code arg2, final YetiType.Type res,
                                   final int line2) {
                            return new Code() {
                                { type = res; }

                                void gen(Ctx ctx) {
                                    genApply2(ctx, arg1, arg2, line2);
                                }
                            };
                        }
                    };
                }
            };
        }

        abstract void genApply2(Ctx ctx, Code arg1, Code arg2, int line);
    }

    class For extends Bind2Core {
        For() {
            super("FOR", YetiType.FOR_TYPE);
            lib = "yeti/lang/Core";
        }

        void genApply2(Ctx ctx, Code list, Code fun, int line) {
            Label nop = new Label(), end = new Label();
            list.gen(ctx);
            fun.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitInsn(SWAP);
            ctx.m.visitInsn(DUP);
            ctx.m.visitJumpInsn(IFNULL, nop);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.m.visitInsn(DUP);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                  "iter", "()Lyeti/lang/ListIter;");
            ctx.m.visitInsn(DUP_X2);
            ctx.m.visitInsn(POP);
            ctx.m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ListIter",
                    "forEach", "(Ljava/lang/Object;Lyeti/lang/AIter;)V");
            ctx.m.visitJumpInsn(GOTO, end);
            ctx.m.visitLabel(nop);
            ctx.m.visitInsn(POP2);
            ctx.m.visitLabel(end);
            ctx.m.visitInsn(ACONST_NULL);
        }
    }

    class Compose extends Bind2Core {
        Compose() {
            super("$d", YetiType.COMPOSE_TYPE);
        }

        void genApply2(Ctx ctx, Code arg1, Code arg2, int line) {
            ctx.m.visitTypeInsn(NEW, "yeti/lang/Compose");
            ctx.m.visitInsn(DUP);
            arg1.gen(ctx);
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Compose",
                    "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        }
    }

    class Synchronized extends Bind2Core {
        Synchronized() {
            super("synchronized", YetiType.SYNCHRONIZED_TYPE);
        }

        void genApply2(Ctx ctx, Code monitor, Code block, int line) {
            Label startBlock = new Label(), endBlock = new Label();
            Label startCleanup = new Label(), endCleanup = new Label();
            Label end = new Label();
            ctx.m.visitTryCatchBlock(startBlock, endBlock, startCleanup, null);
            // I have no fucking idea, what this second catch is supposed
            // to be doing. javac generates it, so it has to be good.
            // yeah, sure...
            ctx.m.visitTryCatchBlock(startCleanup, endCleanup,
                                     startCleanup, null);
            monitor.gen(ctx);
            int monitorVar = ctx.localVarCount++;
            ctx.visitLine(line);
            ctx.m.visitInsn(DUP);
            ctx.m.visitVarInsn(ASTORE, monitorVar);
            ctx.m.visitInsn(MONITORENTER);

            ctx.m.visitLabel(startBlock);
            new Apply(type, block, new UnitConstant(), line).gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitVarInsn(ALOAD, monitorVar);
            ctx.m.visitInsn(MONITOREXIT);
            ctx.m.visitLabel(endBlock);
            ctx.m.visitJumpInsn(GOTO, end);

            int exceptionVar = ctx.localVarCount++;
            ctx.m.visitLabel(startCleanup);
            ctx.m.visitVarInsn(ASTORE, exceptionVar);
            ctx.m.visitVarInsn(ALOAD, monitorVar);
            ctx.m.visitInsn(MONITOREXIT);
            ctx.m.visitLabel(endCleanup);
            ctx.m.visitVarInsn(ALOAD, exceptionVar);
            ctx.m.visitInsn(ATHROW);
            ctx.m.visitLabel(end);
        }
    }

    abstract class BinOpRef extends BindRef implements DirectBind {
        boolean markTail2;
        String coreFun;

        Code apply(final Code arg1, final YetiType.Type res1, final int line) {
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
                    BinOpRef.this.gen(ctx);
                    arg1.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                        "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
                }
            };
        }

        void gen(Ctx ctx) {
            ctx.m.visitFieldInsn(GETSTATIC, "yeti/lang/std",
                                 coreFun, "Lyeti/lang/Fun;");
        }

        abstract void binGen(Ctx ctx, Code arg1, Code arg2);

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            throw new UnsupportedOperationException("binGenIf");
        }
    }

    class ArithOpFun extends BinOpRef implements Binder {
        String method;

        public ArithOpFun(String op, String method, YetiType.Type type) {
            this.type = type;
            this.method = method;
            binder = this;
            coreFun = mangle(op);
        }

        public BindRef getRef(int line) {
            return this; // XXX should copy for type?
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            arg1.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            boolean ii = method == "intDiv" || method == "rem" ||
                         method == "shl" || method == "shr";
            if (arg2 instanceof NumericConstant &&
                ((NumericConstant) arg2).genInt(ctx, ii)) {
                if (method == "shr") {
                    method = "shl";
                    ctx.m.visitInsn(INEG);
                }
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, ii ? "(I)Lyeti/lang/Num;" : "(J)Lyeti/lang/Num;");
                return;
            }
            arg2.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            if (method == "shl" || method == "shr") {
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                      "intValue", "()I");
                if (method == "shr") {
                    ctx.m.visitInsn(INEG);
                }
                ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                      "shl", "(I)Lyeti/lang/Num;");
                return;
            }
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
    }

    abstract class BoolBinOp extends BinOpRef {
        void binGen(Ctx ctx, Code arg1, Code arg2) {
            Label label = new Label();
            binGenIf(ctx, arg1, arg2, label, false);
            ctx.genBoolean(label);
        }
    }

    class CompareFun extends BoolBinOp {
        static final int[] OPS = { IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE };
        static final int[] ROP = { IFEQ, IFNE, IFGT, IFLE, IFLT, IFGE };
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
                if (arg2.isIntNum()) {
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                    ((NumericConstant) arg2).genInt(ctx, false);
                    ctx.visitLine(line);
                    ctx.m.visitMethodInsn(INVOKEVIRTUAL,
                            "yeti/lang/Num", "rCompare", "(J)I");
                    ctx.m.visitJumpInsn(ROP[op], to);
                    return;
                }
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
        String fun;

        public Compare(YetiType.Type type, int op, String fun) {
            this.op = op;
            this.type = type;
            this.fun = Code.mangle(fun);
        }

        public BindRef getRef(int line) {
            CompareFun c = new CompareFun();
            c.binder = this;
            c.type = type;
            c.op = op;
            c.polymorph = true;
            c.line = line;
            c.coreFun = fun;
            return c;
        }
    }

    class InOpFun extends BoolBinOp {
        int line;

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            arg2.gen(ctx);
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Hash");
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash",
                                  "containsKey", "(Ljava/lang/Object;)Z");
            ctx.m.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
        }
    }

    class InOp implements Binder {
        public BindRef getRef(int line) {
            InOpFun f = new InOpFun();
            f.type = YetiType.IN_TYPE;
            f.line = line;
            f.polymorph = true;
            f.coreFun = "in";
            return f;
        }
    }

    class NotOp implements Binder {
        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/std", "not",
                                 YetiType.BOOL_TO_BOOL, this, false, line) {
                Code apply(final Code arg, YetiType.Type res, int line) {
                    return new Code() {
                        { type = YetiType.BOOL_TYPE; }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            arg.genIf(ctx, to, !ifTrue);
                        }

                        void gen(Ctx ctx) {
                            Label label = new Label();
                            arg.genIf(ctx, label, true);
                            ctx.genBoolean(label);
                        }
                    };
                }
            };
        }
    }

    class BoolOpFun extends BoolBinOp implements Binder {
        boolean orOp;

        BoolOpFun(boolean orOp) {
            this.type = YetiType.BOOLOP_TYPE;
            this.orOp = orOp;
            binder = this;
            markTail2 = true;
            coreFun = orOp ? "or" : "and";
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
                    coreFun = "$c$c";
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

    class LazyCons implements Binder {
        public BindRef getRef(final int line) {
            return new BinOpRef() {
                {
                    type = YetiType.LAZYCONS_TYPE;
                    binder = LazyCons.this;
                    coreFun = "$c$d";
                    polymorph = true;
                }

                void binGen(Ctx ctx, Code arg1, Code arg2) {
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/LazyList");
                    ctx.m.visitInsn(DUP);
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitLine(line);
                    ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/LazyList",
                        "<init>", "(Ljava/lang/Object;Lyeti/lang/Fun;)V");
                }
            };
        }
    }

    class MatchOpFun extends BinOpRef implements Binder {
        MatchOpFun() {
            type = YetiType.STR2_PRED_TYPE;
            coreFun = mangle("=~");
        }

        public BindRef getRef(int line) {
            return this;
        }

        void binGen(Ctx ctx, Code arg1, final Code arg2) {
            apply2nd(arg2, YetiType.STR2_PRED_TYPE, 0).gen(ctx);
            arg1.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                    "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        }

        Code apply2nd(final Code arg2, final YetiType.Type t, final int line) {
            final Code matcher = new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.m.visitTypeInsn(NEW, "yeti/lang/Match");
                    ctx.m.visitInsn(DUP);
                    arg2.gen(ctx);
                    ctx.m.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Match",
                                          "<init>", "(Ljava/lang/Object;)V");
                }
            };
            if (!(arg2 instanceof StringConstant)) {
                return matcher;
            }
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.constant("MATCH-FUN:".concat(((StringConstant) arg2)
                                    .str), matcher);
                }
            };
        }

        void binGenIf(Ctx ctx, Code arg1, Code arg2,
                Label to, boolean ifTrue) {
            binGen(ctx, arg1, arg2);
            ctx.m.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "TRUE", "Ljava/lang/Boolean;");
            ctx.m.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
        }
    }

    class ClassOfExpr extends Code implements DirectBind {
        String className;

        ClassOfExpr(JavaType what) {
            type = YetiType.CLASS_TYPE;
            className = what.dottedName();
        }

        void gen(Ctx ctx) {
            ctx.constant("CLASS-OF:".concat(className), new Code() {
                { type = YetiType.CLASS_TYPE; }

                void gen(Ctx ctx) {
                    ctx.m.visitLdcInsn(className);
                    ctx.m.visitMethodInsn(INVOKESTATIC, "java/lang/Class",
                        "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
                }
            });
        }
    }
}
