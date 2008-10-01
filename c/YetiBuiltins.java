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
import java.util.*;

interface YetiBuiltins extends CaseCode {
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
                    ctx.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                         "ARGV", "Ljava/lang/ThreadLocal;");
                    ctx.visitMethodInsn(INVOKEVIRTUAL,
                        "java/lang/ThreadLocal",
                        "get", "()Ljava/lang/Object;");
                }

                Code assign(final Code value) {
                    return new Code() {
                        void gen(Ctx ctx) {
                            ctx.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                         "ARGV", "Ljava/lang/ThreadLocal;");
                            value.gen(ctx);
                            ctx.visitMethodInsn(INVOKEVIRTUAL,
                                "java/lang/ThreadLocal",
                                "get", "(Ljava/lang/Object;)V");
                            ctx.visitInsn(ACONST_NULL);
                        }
                    };
                }

                public boolean flagop(int fl) {
                    return (fl & ASSIGN) != 0;
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

    class IsNullPtr implements Binder, Opcodes {
        private String libName;
        private YetiType.Type type;

        IsNullPtr(YetiType.Type type, String fun) {
            this.type = type;
            libName = fun;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/std", libName,
                                 type, this, true, line) {
                Code apply(final Code arg, final YetiType.Type res,
                           final int line) {
                    return new Code() {
                        { type = res; }

                        void gen(Ctx ctx) {
                            IsNullPtr.this.gen(ctx, arg, line);
                        }

                        void genIf(Ctx ctx, Label to, boolean ifTrue) {
                            IsNullPtr.this.genIf(ctx, arg, to, ifTrue, line);
                        }
                    };
                }
            };
        }

        void gen(Ctx ctx, Code arg, int line) {
            Label label = new Label();
            genIf(ctx, arg, label, false, line);
            ctx.genBoolean(label);
        }

        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue, int line) {
            arg.gen(ctx);
            ctx.visitJumpInsn(ifTrue ? IFNULL : IFNONNULL, to);
        }
    }

    class IsDefined extends IsNullPtr {
        IsDefined() {
            super(YetiType.A_TO_BOOL, "defined?");
        }

        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue, int line) {
            Label isNull = new Label(), end = new Label();
            arg.gen(ctx);
            ctx.visitInsn(DUP);
            ctx.visitJumpInsn(IFNULL, isNull);
            ctx.visitFieldInsn(GETSTATIC, "yeti/lang/Core",
                                 "UNDEF_STR", "Ljava/lang/String;");
            ctx.visitJumpInsn(IF_ACMPEQ, ifTrue ? end : to);
            ctx.visitJumpInsn(GOTO, ifTrue ? to : end);
            ctx.visitLabel(isNull);
            ctx.visitInsn(POP);
            if (!ifTrue) {
                ctx.visitJumpInsn(GOTO, to);
            }
            ctx.visitLabel(end);
        }
    }

    class IsEmpty extends IsNullPtr {
        IsEmpty() {
            super(YetiType.MAP_TO_BOOL, "empty$q");
        }

        void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue, int line) {
            Label isNull = new Label(), end = new Label();
            arg.gen(ctx);
            ctx.visitLine(line);
            ctx.visitInsn(DUP);
            ctx.visitJumpInsn(IFNULL, isNull);
            if (ctx.compilation.isGCJ)
                ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Coll");
            ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Coll",
                                "isEmpty", "()Z"); 
            ctx.visitJumpInsn(IFNE, ifTrue ? to : end);
            ctx.visitJumpInsn(GOTO, ifTrue ? end : to);
            ctx.visitLabel(isNull);
            ctx.visitInsn(POP);
            if (ifTrue) {
                ctx.visitJumpInsn(GOTO, to);
            }
            ctx.visitLabel(end);
        }
    }

    class Head extends IsNullPtr {
        Head() {
            super(YetiType.LIST_TO_A, "head");
        }

        void gen(Ctx ctx, Code arg, int line) {
            arg.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "first", "()Ljava/lang/Object;");
        }
    }

    class Tail extends IsNullPtr {
        Tail() {
            super(YetiType.LIST_TO_LIST, "tail");
        }

        void gen(Ctx ctx, Code arg, int line) {
            arg.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.visitInsn(DUP);
            Label end = new Label();
            ctx.visitJumpInsn(IFNULL, end);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "rest", "()Lyeti/lang/AList;");
            ctx.visitLabel(end);
            ctx.forceType("yeti/lang/AList");
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
                    ctx.visitLine(line);
                    ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                    ctx.visitLdcInsn(new Long(0));
                    ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                        "subFrom", "(J)Lyeti/lang/Num;");
                }
            };
        }
    }

    abstract class Bind2Core implements Binder, Opcodes {
        private String coreFun;
        YetiType.Type type;

        Bind2Core(String fun, YetiType.Type type) {
            coreFun = fun;
            this.type = type;
        }

        public BindRef getRef(int line) {
            return new StaticRef("yeti/lang/std", coreFun,
                                 type, this, true, line) {
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
            super("for", YetiType.FOR_TYPE);
        }

        void genApply2(Ctx ctx, Code list, Code fun, int line) {
            Label nop = new Label(), end = new Label();
            list.gen(ctx);
            fun.gen(ctx);
            ctx.visitLine(line);
            ctx.visitInsn(SWAP);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.visitInsn(DUP_X1);
            ctx.visitJumpInsn(IFNULL, nop);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "forEach", "(Ljava/lang/Object;)V");
            ctx.visitJumpInsn(GOTO, end);
            ctx.visitLabel(nop);
            ctx.visitInsn(POP2);
            ctx.visitLabel(end);
            ctx.visitInsn(ACONST_NULL);
        }
    }

    class Compose extends Bind2Core {
        Compose() {
            super("$d", YetiType.COMPOSE_TYPE);
        }

        void genApply2(Ctx ctx, Code arg1, Code arg2, int line) {
            ctx.visitTypeInsn(NEW, "yeti/lang/Compose");
            ctx.visitInsn(DUP);
            arg1.gen(ctx);
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.visitInit("yeti/lang/Compose",
                          "(Ljava/lang/Object;Ljava/lang/Object;)V");
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
            ctx.visitTryCatchBlock(startBlock, endBlock, startCleanup, null);
            // I have no fucking idea, what this second catch is supposed
            // to be doing. javac generates it, so it has to be good.
            // yeah, sure...
            ctx.visitTryCatchBlock(startCleanup, endCleanup,
                                     startCleanup, null);
            monitor.gen(ctx);
            int monitorVar = ctx.localVarCount++;
            ctx.visitLine(line);
            ctx.visitInsn(DUP);
            ctx.visitVarInsn(ASTORE, monitorVar);
            ctx.visitInsn(MONITORENTER);

            ctx.visitLabel(startBlock);
            new Apply(type, block, new UnitConstant(), line).gen(ctx);
            ctx.visitLine(line);
            ctx.visitVarInsn(ALOAD, monitorVar);
            ctx.visitInsn(MONITOREXIT);
            ctx.visitLabel(endBlock);
            ctx.visitJumpInsn(GOTO, end);

            int exceptionVar = ctx.localVarCount++;
            ctx.visitLabel(startCleanup);
            ctx.visitVarInsn(ASTORE, exceptionVar);
            ctx.visitVarInsn(ALOAD, monitorVar);
            ctx.visitInsn(MONITOREXIT);
            ctx.visitLabel(endCleanup);
            ctx.visitVarInsn(ALOAD, exceptionVar);
            ctx.visitInsn(ATHROW);
            ctx.visitLabel(end);
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
                    ctx.visitApply(arg1, line);
                }
            };
        }

        void gen(Ctx ctx) {
            ctx.visitFieldInsn(GETSTATIC, "yeti/lang/std",
                                 coreFun, "Lyeti/lang/Fun;");
        }

        abstract void binGen(Ctx ctx, Code arg1, Code arg2);

        void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
            throw new UnsupportedOperationException("binGenIf");
        }
    }

    class ArithOpFun extends BinOpRef {
        private String method;
        private int line;

        public ArithOpFun(String fun, String method, YetiType.Type type,
                          Binder binder, int line) {
            this.type = type;
            this.method = method;
            coreFun = fun;
            this.binder = binder;
            this.line = line;
        }

        public BindRef getRef(int line) {
            return this; // XXX should copy for type?
        }

        void binGen(Ctx ctx, Code arg1, Code arg2) {
            if (method == "and" && arg2 instanceof NumericConstant &&
                ((NumericConstant) arg2).flagop(INT_NUM)) {
                ctx.visitTypeInsn(NEW, "yeti/lang/IntNum");
                ctx.visitInsn(DUP);
                arg1.gen(ctx);
                ctx.visitLine(line);
                ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                    "longValue", "()J");
                ((NumericConstant) arg2).genInt(ctx, false);
                ctx.visitInsn(LAND);
                ctx.visitInit("yeti/lang/IntNum", "(J)V");
                ctx.forceType("yeti/lang/Num");
                return;
            }
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            boolean ii = method == "intDiv" || method == "rem" ||
                         method == "shl" || method == "shr";
            if (arg2 instanceof NumericConstant &&
                ((NumericConstant) arg2).genInt(ctx, ii)) {
                ctx.visitLine(line);
                if (method == "shr") {
                    method = "shl";
                    ctx.visitInsn(INEG);
                }
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, ii ? "(I)Lyeti/lang/Num;" : "(J)Lyeti/lang/Num;");
                return;
            }
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            if (method == "shl" || method == "shr") {
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                    "intValue", "()I");
                if (method == "shr") {
                    ctx.visitInsn(INEG);
                }
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                    "shl", "(I)Lyeti/lang/Num;");
                return;
            }
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
    }

    class ArithOp implements Binder {
        private String fun;
        private String method;
        private YetiType.Type type;

        ArithOp(String op, String method, YetiType.Type type) {
            fun = Code.mangle(op);
            this.method = method;
            this.type = type;
        }

        public BindRef getRef(int line) {
            return new ArithOpFun(fun, method, type, this, line);
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

        void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
            YetiType.Type t = arg1.type.deref();
            int op = this.op;
            boolean eq = (op & (COND_LT | COND_GT)) == 0;
            if (!ifTrue) {
                op ^= COND_NOT;
            }
            Label nojmp = null;
            if (t.type == YetiType.VAR || t.type == YetiType.MAP &&
                    t.param[2] == YetiType.LIST_TYPE &&
                    t.param[1].type != YetiType.NUM) {
                Label nonull = new Label();
                nojmp = new Label();
                arg2.gen(ctx);
                arg1.gen(ctx); // 2-1
                ctx.visitLine(line);
                ctx.visitInsn(DUP); // 2-1-1
                ctx.visitJumpInsn(IFNONNULL, nonull); // 2-1
                // reach here, when 1 was null
                if (op == COND_GT || op == COND_LE ||
                    arg2.flagop(EMPTY_LIST) &&
                        (op == COND_EQ || op == COND_NOT)) {
                    // null is never greater and always less or equal
                    ctx.visitInsn(POP2);
                    ctx.visitJumpInsn(GOTO,
                        op == COND_LE || op == COND_EQ ? to : nojmp);
                } else {
                    ctx.visitInsn(POP); // 2
                    ctx.visitJumpInsn(op == COND_EQ || op == COND_GE
                                        ? IFNULL : IFNONNULL, to);
                    ctx.visitJumpInsn(GOTO, nojmp);
                }
                ctx.visitLabel(nonull);
                if (!eq && ctx.compilation.isGCJ)
                    ctx.visitTypeInsn(CHECKCAST, "java/lang/Comparable");
                ctx.visitInsn(SWAP); // 1-2
            } else {
                arg1.gen(ctx);
                ctx.visitLine(line);
                if (arg2.flagop(INT_NUM)) {
                    ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
                    ((NumericConstant) arg2).genInt(ctx, false);
                    ctx.visitLine(line);
                    ctx.visitMethodInsn(INVOKEVIRTUAL,
                            "yeti/lang/Num", "rCompare", "(J)I");
                    ctx.visitJumpInsn(ROP[op], to);
                    return;
                }
                if (!eq && ctx.compilation.isGCJ)
                    ctx.visitTypeInsn(CHECKCAST, "java/lang/Comparable");
                arg2.gen(ctx);
                ctx.visitLine(line);
            }
            if (eq) {
                op ^= COND_NOT;
                ctx.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                    "equals", "(Ljava/lang/Object;)Z");
            } else {
                ctx.visitMethodInsn(INVOKEINTERFACE, "java/lang/Comparable",
                                    "compareTo", "(Ljava/lang/Object;)I");
            }
            ctx.visitJumpInsn(OPS[op], to);
            if (nojmp != null) {
                ctx.visitLabel(nojmp);
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


    class Same implements Binder {
        public BindRef getRef(int line) {
            return new BoolBinOp() {
                {
                    type = YetiType.EQ_TYPE;
                    binder = Same.this;
                    polymorph = true;
                    coreFun = "same$q";
                }

                void binGenIf(Ctx ctx, Code arg1, Code arg2,
                              Label to, boolean ifTrue) {
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
                }
            };
        }
    }


    class InOpFun extends BoolBinOp {
        int line;

        void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Hash");
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Hash",
                                "containsKey", "(Ljava/lang/Object;)Z");
            ctx.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
        }
    }

    class InOp implements Binder {
        public BindRef getRef(int line) {
            InOpFun f = new InOpFun();
            f.type = YetiType.IN_TYPE;
            f.line = line;
            f.polymorph = true;
            f.coreFun = "in";
            f.binder = this;
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
                ctx.visitJumpInsn(GOTO, end);
                ctx.visitLabel(label);
                ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                        orOp ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
                ctx.visitLabel(end);
            }
        }

        void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
            if (orOp == ifTrue) {
                arg1.genIf(ctx, to, orOp);
                arg2.genIf(ctx, to, orOp);
            } else {
                Label noJmp = new Label();
                arg1.genIf(ctx, noJmp, orOp);
                arg2.genIf(ctx, to, !orOp);
                ctx.visitLabel(noJmp);
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
                    String lclass = "yeti/lang/LList";
                    if (arg2.type.deref().param[1].deref()
                            != YetiType.NO_TYPE) {
                        lclass = "yeti/lang/LMList";
                    }
                    ctx.visitLine(line);
                    ctx.visitTypeInsn(NEW, lclass);
                    ctx.visitInsn(DUP);
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitLine(line);
                    ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
                    ctx.visitInit(lclass,
                                  "(Ljava/lang/Object;Lyeti/lang/AList;)V");
                    ctx.forceType("yeti/lang/AList");
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
                    ctx.visitTypeInsn(NEW, "yeti/lang/LazyList");
                    ctx.visitInsn(DUP);
                    arg1.gen(ctx);
                    arg2.gen(ctx);
                    ctx.visitLine(line);
                    ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
                    ctx.visitInit("yeti/lang/LazyList",
                                  "(Ljava/lang/Object;Lyeti/lang/Fun;)V");
                    ctx.forceType("yeti/lang/AList");
                }
            };
        }
    }

    class MatchOpFun extends BinOpRef {
        private int line;
        private boolean yes;

        MatchOpFun(int line, boolean yes) {
            type = YetiType.STR2_PRED_TYPE;
            coreFun = mangle(yes ? "=~" : "!~");
            this.line = line;
            this.yes = yes;
        }

        void binGen(Ctx ctx, Code arg1, final Code arg2) {
            apply2nd(arg2, YetiType.STR2_PRED_TYPE, line).gen(ctx);
            ctx.visitApply(arg1, line);
        }

        Code apply2nd(final Code arg2, final YetiType.Type t, final int line) {
            if (line == 0) {
                throw new NullPointerException();
            }
            final Code matcher = new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.visitTypeInsn(NEW, "yeti/lang/Match");
                    ctx.visitInsn(DUP);
                    arg2.gen(ctx);
                    ctx.intConst(yes ? 1 : 0);
                    ctx.visitLine(line);
                    ctx.visitInit("yeti/lang/Match",
                                  "(Ljava/lang/Object;Z)V");
                }
            };
            if (!(arg2 instanceof StringConstant))
                return matcher;
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.constant((yes ? "MATCH-FUN:" : "MATCH!FUN:")
                        .concat(((StringConstant) arg2).str), matcher);
                }
            };
        }

        void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
            binGen(ctx, arg1, arg2);
            ctx.visitFieldInsn(GETSTATIC, "java/lang/Boolean",
                    "TRUE", "Ljava/lang/Boolean;");
            ctx.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
        }
    }

    class MatchOp implements Binder {
        boolean yes;

        MatchOp(boolean yes) {
            this.yes = yes;
        }

        public BindRef getRef(int line) {
            return new MatchOpFun(line, yes);
        }
    }

    class RegexFun extends StaticRef {
        private String impl;

        RegexFun(String fun, String impl, YetiType.Type type,
                 Binder binder, int line) {
            super("yeti/lang/std", fun, type, null, false, line);
            this.binder = binder;
            this.impl = impl;
        }

        Code apply(final Code arg, final YetiType.Type t, final int line) {
            final Code f = new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.visitTypeInsn(NEW, impl);
                    ctx.visitInsn(DUP);
                    arg.gen(ctx);
                    ctx.visitLine(line);
                    ctx.visitInit(impl, "(Ljava/lang/Object;)V");
                }
            };
            if (!(arg instanceof StringConstant))
                return f;
            return new Code() {
                { type = t; }

                void gen(Ctx ctx) {
                    ctx.constant(funFieldName + ':' +
                        ((StringConstant) arg).str, f);
                }
            };
        }
    }

    class Regex implements Binder {
        private String fun, impl;
        private YetiType.Type type;

        Regex(String fun, String impl, YetiType.Type type) {
            this.fun = fun;
            this.impl = impl;
            this.type = type;
        }

        public BindRef getRef(int line) {
            return new RegexFun(fun, impl, type, this, line);
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
                    ctx.visitLdcInsn(className);
                    ctx.visitMethodInsn(INVOKESTATIC, "java/lang/Class",
                        "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
                }
            });
        }
    }

    class InstanceOfExpr extends Code {
        Code expr;
        String className;

        InstanceOfExpr(Code expr, JavaType what) {
            type = YetiType.BOOL_TYPE;
            this.expr = expr;
            className = what.className();
        }

        void genIf(Ctx ctx, Label to, boolean ifTrue) {
            expr.gen(ctx);
            ctx.visitTypeInsn(INSTANCEOF, className);
            ctx.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
        }

        void gen(Ctx ctx) {
            Label label = new Label();
            genIf(ctx, label, false);
            ctx.genBoolean(label);
        }
    }

    Code NOP_CODE = new Code() {
        void gen(Ctx ctx) {
        }
    };

    class StrOp extends StaticRef implements DirectBind, Binder {
        String method;
        String sig;
        YetiType.Type argTypes[];

        class StrApply extends Apply {
            StrApply prev;

            StrApply(Code arg, YetiType.Type type, StrApply prev, int line) {
                super(type, NOP_CODE, arg, line);
                this.prev = prev;
            }

            Code apply(Code arg, YetiType.Type res, int line) {
                return new StrApply(arg, res, this, line);
            }

            void genApply(Ctx ctx) {
                super.gen(ctx);
            }

            void gen(Ctx ctx) {
                List argv = new ArrayList();
                for (StrApply a = this; a != null; a = a.prev) {
                    argv.add(a);
                }
                if (argv.size() != argTypes.length) {
                    StrOp.this.gen(ctx);
                    for (int i = argv.size() - 1; --i >= 0;)
                        ((StrApply) argv.get(i)).genApply(ctx);
                    return;
                }
                ((StrApply) argv.get(argv.size() - 1)).arg.gen(ctx);
                ctx.visitLine(line);
                ctx.visitTypeInsn(CHECKCAST, "java/lang/String");
                for (int i = 0, last = argv.size() - 2; i <= last; ++i) {
                    StrApply a = (StrApply) argv.get(last - i);
                    JavaExpr.convertedArg(ctx, a.arg, argTypes[i], a.line);
                }
                ctx.visitLine(line);
                ctx.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String",
                                    method, sig);
                JavaExpr.convertValue(ctx, argTypes[argTypes.length - 1]);
            }
        }

        StrOp(String fun, String method, String sig, YetiType.Type type) {
            super("yeti/lang/std", fun, type, null, false, 0);
            this.method = method;
            this.sig = sig;
            binder = this;
            argTypes = JavaTypeReader.parseSig1(1, sig);
        }

        public BindRef getRef(int line) {
            return this;
        }

        Code apply(final Code arg, final YetiType.Type res, final int line) {
            return new StrApply(arg, res, null, line);
        }
    }
}
