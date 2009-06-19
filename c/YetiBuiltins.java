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

final class BuiltIn implements Binder {
    int op;

    public BuiltIn(int op) {
        this.op = op;
    }

    public BindRef getRef(int line) {
        BindRef r = null;
        switch (op) {
        case 1:
            r = new Argv();
            r.type = YetiType.STRING_ARRAY;
            break;
        case 2:
            r = new InOpFun(line);
            break;
        case 3:
            r = new Cons(line);
            break;
        case 4:
            r = new LazyCons(line);
            break;
        case 5:
            r = new For(line);
            break;
        case 6:
            r = new Compose(line);
            break;
        case 7:
            r = new Synchronized(line);
            break;
        case 8:
            r = new IsNullPtr(YetiType.A_TO_BOOL, "nullptr?", line);
            break;
        case 9:
            r = new IsDefined(line);
            break;
        case 10:
            r = new IsEmpty(line);
            break;
        case 11:
            r = new Head(line);
            break;
        case 12:
            r = new Tail(line);
            break;
        case 13:
            r = new MatchOpFun(line, true);
            break;
        case 14:
            r = new MatchOpFun(line, false);
            break;
        case 15:
            r = new NotOp(line);
            break;
        case 16:
            r = new StrChar(line);
            break;
        case 17:
            r = new UnitConstant(YetiType.BOOL_TYPE);
            break;
        case 18:
            r = new BooleanConstant(false);
            break;
        case 19:
            r = new BooleanConstant(true);
            break;
        case 20:
            r = new Negate();
            break;
        case 21:
            r = new Same();
            break;
        case 22:
            r = new StaticRef("yeti/lang/Core", "RANDINT",
                              YetiType.NUM_TO_NUM, this, true, line);
            break;
        case 23:
            r = new StaticRef("yeti/lang/Core", "UNDEF_STR",
                              YetiType.STR_TYPE, this, true, line);
            break;
        case 24:
            r = new Escape(line);
            break;
        }
        r.binder = this;
        return r;
    }
}

final class Argv extends BindRef {
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

    boolean flagop(int fl) {
        return (fl & (ASSIGN | DIRECT_BIND)) != 0;
    }
}

class IsNullPtr extends StaticRef {
    private String libName;
    boolean normalIf;

    IsNullPtr(YetiType.Type type, String fun, int line) {
        super("yeti/lang/std$" + fun, "_", type, null, true, line);
    }

    Code apply(final Code arg, final YetiType.Type res,
               final int line) {
        return new Code() {
            { type = res; }

            void gen(Ctx ctx) {
                IsNullPtr.this.gen(ctx, arg, line);
            }

            void genIf(Ctx ctx, Label to, boolean ifTrue) {
                if (normalIf) {
                    super.genIf(ctx, to, ifTrue);
                } else {
                    IsNullPtr.this.genIf(ctx, arg, to, ifTrue, line);
                }
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

final class IsDefined extends IsNullPtr {
    IsDefined(int line) {
        super(YetiType.A_TO_BOOL, "defined$q", line);
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

final class IsEmpty extends IsNullPtr {
    IsEmpty(int line) {
        super(YetiType.MAP_TO_BOOL, "empty$q", line);
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

final class Head extends IsNullPtr {
    Head(int line) {
        super(YetiType.LIST_TO_A, "head", line);
    }

    void gen(Ctx ctx, Code arg, int line) {
        arg.gen(ctx);
        ctx.visitLine(line);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
        ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                            "first", "()Ljava/lang/Object;");
    }
}

final class Tail extends IsNullPtr {
    Tail(int line) {
        super(YetiType.LIST_TO_LIST, "tail", line);
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

final class Escape extends IsNullPtr {
    Escape(int line) {
        super(YetiType.WITH_EXIT_TYPE, "withExit", line);
        normalIf = true;
    }

    void gen(Ctx ctx, Code block, int line) {
        block.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
        ctx.visitMethodInsn(INVOKESTATIC, "yeti/lang/EscapeFun", "with",
                            "(Lyeti/lang/Fun;)Ljava/lang/Object;");
    }
}

final class Negate extends StaticRef {
    Negate() {
        super("yeti/lang/std$negate", "_", YetiType.NUM_TO_NUM,
              null, false, 0);
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
                ctx.forceType("yeti/lang/Num");
            }
        };
    }
}

abstract class Core2 extends StaticRef {
    boolean derivePolymorph;

    Core2(String coreFun, YetiType.Type type, int line) {
        super("yeti/lang/std$" + coreFun, "_", type, null, true, line);
    }

    Code apply(final Code arg1, YetiType.Type res, int line1) {
        return new Apply(res, this, arg1, line1) {
            Code apply(final Code arg2, final YetiType.Type res,
                       final int line2) {
                return new Code() {
                    {
                        type = res;
                        polymorph = derivePolymorph && arg1.polymorph
                                                    && arg2.polymorph;
                    }

                    void gen(Ctx ctx) {
                        genApply2(ctx, arg1, arg2, line2);
                    }
                };
            }
        };
    }

    abstract void genApply2(Ctx ctx, Code arg1, Code arg2, int line);
}

final class For extends Core2 {
    For(int line) {
        super("for", YetiType.FOR_TYPE, line);
    }

    void genApply2(Ctx ctx, Code list, Code fun, int line) {
        Function f;
        LoadVar arg = new LoadVar();
        if (fun instanceof Function &&
                (f = (Function) fun).uncapture(arg)) {
            Label retry = new Label(), end = new Label();
            list.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.visitInsn(DUP);
            ctx.visitJumpInsn(IFNULL, end);
            ctx.visitInsn(DUP);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "isEmpty", "()Z");
            ctx.visitJumpInsn(IFNE, end);
            // start of loop
            ctx.visitLabel(retry);
            ctx.visitInsn(DUP);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "first", "()Ljava/lang/Object;");
            // invoke body block
            ctx.visitVarInsn(ASTORE, arg.var = ctx.localVarCount++);
            f.body.gen(ctx);
            ctx.visitLine(line);
            ctx.visitInsn(POP); // ignore return value
            // next
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "next", "()Lyeti/lang/AIter;");
            ctx.visitInsn(DUP);
            ctx.visitJumpInsn(IFNONNULL, retry);
            ctx.visitLabel(end);
        } else {
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
}

final class Compose extends Core2 {
    Compose(int line) {
        super("$d", YetiType.COMPOSE_TYPE, line);
        derivePolymorph = true;
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

final class Synchronized extends Core2 {
    Synchronized(int line) {
        super("synchronized", YetiType.SYNCHRONIZED_TYPE, line);
    }

    void genApply2(Ctx ctx, Code monitor, Code block, int line) {
        monitor.gen(ctx);
        int monitorVar = ctx.localVarCount++;
        ctx.visitLine(line);
        ctx.visitInsn(DUP);
        ctx.visitVarInsn(ASTORE, monitorVar);
        ctx.visitInsn(MONITORENTER);

        Label startBlock = new Label(), endBlock = new Label();
        ctx.visitLabel(startBlock);
        new Apply(type, block, new UnitConstant(null), line).gen(ctx);
        ctx.visitLine(line);
        ctx.visitVarInsn(ALOAD, monitorVar);
        ctx.visitInsn(MONITOREXIT);
        ctx.visitLabel(endBlock);
        Label end = new Label();
        ctx.visitJumpInsn(GOTO, end);

        Label startCleanup = new Label(), endCleanup = new Label();
        ctx.visitTryCatchBlock(startBlock, endBlock, startCleanup, null);
        // I have no fucking idea, what this second catch is supposed
        // to be doing. javac generates it, so it has to be good.
        // yeah, sure...
        ctx.visitTryCatchBlock(startCleanup, endCleanup, startCleanup, null);

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

abstract class BinOpRef extends BindRef {
    boolean markTail2;
    String coreFun;

    class Result extends Code {
        private Code arg1;
        private Code arg2;

        Result(Code arg1, Code arg2, YetiType.Type res) {
            type = res;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

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
    }

    Code apply(final Code arg1, final YetiType.Type res1, final int line) {
        return new Code() {
            { type = res1; }

            Code apply(Code arg2, YetiType.Type res, int line) {
                return new Result(arg1, arg2, res);
            }

            void gen(Ctx ctx) {
                BinOpRef.this.gen(ctx);
                ctx.visitApply(arg1, line);
            }
        };
    }

    void gen(Ctx ctx) {
        ctx.visitFieldInsn(GETSTATIC, "yeti/lang/std$" + coreFun,
                           "_", "Lyeti/lang/Fun;");
    }

    abstract void binGen(Ctx ctx, Code arg1, Code arg2);

    void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
        throw new UnsupportedOperationException("binGenIf");
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }
}

final class ArithOpFun extends BinOpRef {
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
        boolean ii = method == "intDiv" || method == "rem";
        if (method == "shl" || method == "shr") {
            ctx.genInt(arg2, line);
            if (method == "shr")
                ctx.visitInsn(INEG);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                "shl", "(I)Lyeti/lang/Num;");
        } else if (arg2 instanceof NumericConstant &&
                 ((NumericConstant) arg2).genInt(ctx, ii)) {
            ctx.visitLine(line);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                method, ii ? "(I)Lyeti/lang/Num;" : "(J)Lyeti/lang/Num;");
            ctx.forceType("yeti/lang/Num");
        } else {
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Num");
            ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
        ctx.forceType("yeti/lang/Num");
    }
}

final class ArithOp implements Binder {
    private String fun;
    private String method;
    private YetiType.Type type;

    ArithOp(String op, String method, YetiType.Type type) {
        fun = op == "+" ? "plus" : Code.mangle(op);
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

final class CompareFun extends BoolBinOp {
    static final int COND_EQ  = 0;
    static final int COND_NOT = 1;
    static final int COND_LT  = 2;
    static final int COND_GT  = 4;
    static final int COND_LE  = COND_NOT | COND_GT;
    static final int COND_GE  = COND_NOT | COND_LT;
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

final class Compare implements Binder {
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


final class Same extends BoolBinOp {
    Same() {
        type = YetiType.EQ_TYPE;
        polymorph = true;
        coreFun = "same$q";
    }

    void binGenIf(Ctx ctx, Code arg1, Code arg2,
                  Label to, boolean ifTrue) {
        arg1.gen(ctx);
        arg2.gen(ctx);
        ctx.visitJumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
    }
}

final class InOpFun extends BoolBinOp {
    int line;

    InOpFun(int line) {
        type = YetiType.IN_TYPE;
        this.line = line;
        polymorph = true;
        coreFun = "in";
    }

    void binGenIf(Ctx ctx, Code arg1, Code arg2, Label to, boolean ifTrue) {
        arg2.gen(ctx);
        ctx.visitLine(line);
        if (ctx.compilation.isGCJ) {
            ctx.visitTypeInsn(CHECKCAST, "yeti/lang/ByKey");
        }
        arg1.gen(ctx);
        ctx.visitLine(line);
        ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                            "containsKey", "(Ljava/lang/Object;)Z");
        ctx.visitJumpInsn(ifTrue ? IFNE : IFEQ, to);
    }
}

final class NotOp extends StaticRef {
    NotOp(int line) {
        super("yeti/lang/std$not", "_",
              YetiType.BOOL_TO_BOOL, null, false, line);
    }

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
}

final class BoolOpFun extends BoolBinOp implements Binder {
    boolean orOp;

    BoolOpFun(boolean orOp) {
        this.type = YetiType.BOOLOP_TYPE;
        this.orOp = orOp;
        this.binder = this;
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

final class Cons extends BinOpRef {
    private int line;

    Cons(int line) {
        type = YetiType.CONS_TYPE;
        coreFun = "$c$c";
        polymorph = true;
        this.line = line;
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
}

final class LazyCons extends BinOpRef {
    private int line;

    LazyCons(int line) {
        type = YetiType.LAZYCONS_TYPE;
        coreFun = "$c$d";
        polymorph = true;
        this.line = line;
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
}

final class MatchOpFun extends BinOpRef {
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

final class RegexFun extends StaticRef {
    private String impl;
    private String funName;

    RegexFun(String fun, String impl, YetiType.Type type,
             Binder binder, int line) {
        super("yeti/lang/std$" + fun, "_", type, null, false, line);
        this.funName = fun;
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
                ctx.constant(funName + ":regex:" +
                    ((StringConstant) arg).str, f);
            }
        };
    }
}

final class Regex implements Binder {
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

final class ClassOfExpr extends Code {
    String className;

    ClassOfExpr(JavaType what, int array) {
        type = YetiType.CLASS_TYPE;
        String cn = what.dottedName();
        if (array != 0) {
            cn = 'L' + cn + ';';
            do {
                cn = "[".concat(cn);
            } while (--array > 0);
        }
        className = cn;
    }

    void gen(Ctx ctx) {
        ctx.constant("CLASS-OF:".concat(className), new Code() {
            { type = YetiType.CLASS_TYPE; }

            void gen(Ctx ctx) {
                ctx.visitLdcInsn(className);
                ctx.visitMethodInsn(INVOKESTATIC, "java/lang/Class",
                    "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
                ctx.forceType("java/lang/Class");
            }
        });
    }

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }
}

final class InstanceOfExpr extends Code {
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

final class JavaArrayRef extends Code {
    Code value, index;
    YetiType.Type elementType;
    int line;

    JavaArrayRef(YetiType.Type _type, Code _value, Code _index, int _line) {
        type = JavaType.convertValueType(elementType = _type);
        value = _value;
        index = _index;
        line = _line;
    }

    private void _gen(Ctx ctx, Code store) {
        value.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, JavaType.descriptionOf(value.type));
        ctx.genInt(index, line);
        String resDescr = elementType.javaType == null
                            ? JavaType.descriptionOf(elementType)
                            : elementType.javaType.description;
        int insn = BALOAD;
        switch (resDescr.charAt(0)) {
        case 'C':
            insn = CALOAD;
            break;
        case 'D':
            insn = DALOAD;
            break;
        case 'F':
            insn = FALOAD;
            break;
        case 'I':
            insn = IALOAD;
            break;
        case 'J':
            insn = LALOAD;
            break;
        case 'S':
            insn = SALOAD;
            break;
        case 'L':
            resDescr = resDescr.substring(1, resDescr.length() - 1);
        case '[':
            insn = AALOAD;
            break;
        }
        if (store != null) {
            insn += 33;
            JavaExpr.genValue(ctx, store, elementType, line);
            if (insn == AASTORE)
                ctx.visitTypeInsn(CHECKCAST, resDescr);
        }
        ctx.visitInsn(insn);
        if (insn == AALOAD) {
            ctx.forceType(resDescr);
        }
    }

    void gen(Ctx ctx) {
        _gen(ctx, null);
        JavaExpr.convertValue(ctx, elementType);
    }

    Code assign(final Code setValue) {
        return new Code() {
            void gen(Ctx ctx) {
                _gen(ctx, setValue);
                ctx.visitInsn(ACONST_NULL);
            }
        };
    }
}

final class StrOp extends StaticRef implements Binder {
    final static Code NOP_CODE = new Code() {
        void gen(Ctx ctx) {
        }
    };

    String method;
    String sig;
    YetiType.Type argTypes[];

    final class StrApply extends Apply {
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
                if (a.arg.type.deref().type == YetiType.STR) {
                    a.arg.gen(ctx);
                    ctx.visitTypeInsn(CHECKCAST, "java/lang/String");
                } else {
                    JavaExpr.convertedArg(ctx, a.arg, argTypes[i], a.line);
                }
            }
            ctx.visitLine(line);
            ctx.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String",
                                method, sig);
            if (type.deref().type == YetiType.STR) {
                ctx.forceType("java/lang/String;");
            } else {
                JavaExpr.convertValue(ctx, argTypes[argTypes.length - 1]);
            }
        }
    }

    StrOp(String fun, String method, String sig, YetiType.Type type) {
        super("yeti/lang/std$" + mangle(fun), "_", type, null, false, 0);
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

    boolean flagop(int fl) {
        return (fl & STD_CONST) != 0;
    }
}

final class StrChar extends BinOpRef {
    private int line;

    StrChar(int line) {
        coreFun = "strChar";
        type = YetiType.STR_TO_NUM_TO_STR;
        this.line = line;
    }

    void binGen(Ctx ctx, Code arg1, Code arg2) {
        arg1.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, "java/lang/String");
        ctx.genInt(arg2, line);
        ctx.visitInsn(DUP);
        ctx.intConst(1);
        ctx.visitInsn(IADD);
        ctx.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String",
                            "substring", "(II)Ljava/lang/String;");
        ctx.forceType("java/lang/String");
    }
}
