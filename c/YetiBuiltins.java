// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
 * Copyright (c) 2007,2008,2009 Madis Janson
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
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class BuiltIn implements Binder {
    int op;

    public BuiltIn(int op) {
        this.op = op;
    }

    static BindRef undef_str(Binder binder, int line) {
        return new StaticRef("yeti/lang/Core", "UNDEF_STR",
                             YetiType.STR_TYPE, binder, true, line);
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
            r = new IsNullPtr(YetiType.A_TO_BOOL, "nullptr$q", line);
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
            r = undef_str(this, line);
            break;
        case 24:
            r = new Escape(line);
            break;
        case 25:
            r = new Length();
            break;
        }
        r.binder = this;
        return r;
    }
}

final class Argv extends BindRef implements CodeGen {
    void gen(Ctx ctx) {
        ctx.fieldInsn(GETSTATIC, "yeti/lang/Core",
                             "ARGV", "Ljava/lang/ThreadLocal;");
        ctx.methodInsn(INVOKEVIRTUAL,
            "java/lang/ThreadLocal",
            "get", "()Ljava/lang/Object;");
    }

    public void gen2(Ctx ctx, Code value, int line) {
        ctx.fieldInsn(GETSTATIC, "yeti/lang/Core",
                     "ARGV", "Ljava/lang/ThreadLocal;");
        value.gen(ctx);
        ctx.methodInsn(INVOKEVIRTUAL,
            "java/lang/ThreadLocal",
            "get", "(Ljava/lang/Object;)V");
        ctx.insn(ACONST_NULL);
    }

    Code assign(final Code value) {
        return new SimpleCode(this, value, null, 0);
    }

    boolean flagop(int fl) {
        return (fl & (ASSIGN | DIRECT_BIND)) != 0;
    }
}

class IsNullPtr extends StaticRef {
    private String libName;
    boolean normalIf;

    IsNullPtr(YType type, String fun, int line) {
        super("yeti/lang/std$" + fun, "_", type, null, true, line);
    }

    Code apply(final Code arg, final YType res, final int line) {
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
        ctx.jumpInsn(ifTrue ? IFNULL : IFNONNULL, to);
    }
}

final class IsDefined extends IsNullPtr {
    IsDefined(int line) {
        super(YetiType.A_TO_BOOL, "defined$q", line);
    }

    void genIf(Ctx ctx, Code arg, Label to, boolean ifTrue, int line) {
        Label isNull = new Label(), end = new Label();
        arg.gen(ctx);
        ctx.insn(DUP);
        ctx.jumpInsn(IFNULL, isNull);
        ctx.fieldInsn(GETSTATIC, "yeti/lang/Core",
                             "UNDEF_STR", "Ljava/lang/String;");
        ctx.jumpInsn(IF_ACMPEQ, ifTrue ? end : to);
        ctx.jumpInsn(GOTO, ifTrue ? to : end);
        ctx.visitLabel(isNull);
        ctx.insn(POP);
        if (!ifTrue) {
            ctx.jumpInsn(GOTO, to);
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
        ctx.insn(DUP);
        ctx.jumpInsn(IFNULL, isNull);
        if (ctx.compilation.isGCJ)
            ctx.typeInsn(CHECKCAST, "yeti/lang/Coll");
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/Coll", "isEmpty", "()Z");
        ctx.jumpInsn(IFNE, ifTrue ? to : end);
        ctx.jumpInsn(GOTO, ifTrue ? end : to);
        ctx.visitLabel(isNull);
        ctx.insn(POP);
        if (ifTrue) {
            ctx.jumpInsn(GOTO, to);
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
        ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
        ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
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
        ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
        ctx.insn(DUP);
        Label end = new Label();
        ctx.jumpInsn(IFNULL, end);
        ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
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
        ctx.typeInsn(CHECKCAST, "yeti/lang/Fun");
        ctx.methodInsn(INVOKESTATIC, "yeti/lang/EscapeFun", "with",
                            "(Lyeti/lang/Fun;)Ljava/lang/Object;");
    }
}

final class Negate extends StaticRef implements CodeGen {
    Negate() {
        super("yeti/lang/std$negate", "_", YetiType.NUM_TO_NUM,
              null, false, 0);
    }

    public void gen2(Ctx ctx, Code arg, int line) {
        arg.gen(ctx);
        ctx.visitLine(line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
        ctx.ldcInsn(new Long(0));
        ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                            "subFrom", "(J)Lyeti/lang/Num;");
        ctx.forceType("yeti/lang/Num");
    }

    Code apply(final Code arg1, final YType res1, final int line) {
        if (arg1 instanceof NumericConstant) {
            return new NumericConstant(((NumericConstant) arg1)
                        .num.subFrom(0));
        }
        return new SimpleCode(this, arg1, YetiType.NUM_TYPE, line);
    }
}

final class Length extends StaticRef {
    Length() {
        super("yeti/lang/std$length", "_", YetiType.MAP_TO_NUM,
              null, true, 0);
    }

    void genLong(Ctx ctx, Code arg, int line, boolean toint) {
        Label nonnull = new Label(), end = new Label();
        arg.gen(ctx);
        ctx.visitLine(line);
        // arrays can't be null, other can
        if (arg.type.deref().param[1].deref() != YetiType.NUM_TYPE) {
            ctx.insn(DUP);
            ctx.jumpInsn(IFNONNULL, nonnull);
            ctx.insn(POP);
            if (toint)
                ctx.intConst(0);
            else
                ctx.ldcInsn(new Long(0));
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(nonnull);
        }
        if (ctx.compilation.isGCJ)
            ctx.typeInsn(CHECKCAST, "yeti/lang/Coll");
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/Coll", "length", "()J");
        if (toint)
            ctx.insn(L2I);
        ctx.visitLabel(end);
    }

    Code apply(final Code arg, final YType res, final int line) {
        return new Code() {
            { type = res; }

            void gen(Ctx ctx) {
                ctx.typeInsn(NEW, "yeti/lang/IntNum");
                ctx.insn(DUP);
                genLong(ctx, arg, line, false);
                ctx.visitInit("yeti/lang/IntNum", "(J)V");
            }

            void genInt(Ctx ctx, int line_, boolean longValue) {
                genLong(ctx, arg, line, !longValue);
            }

            boolean flagop(int fl) {
                return (fl & INT_NUM) != 0;
            }
        };
    }
}

abstract class Core2 extends StaticRef {
    boolean derivePolymorph;

    Core2(String coreFun, YType type, int line) {
        super("yeti/lang/std$" + coreFun, "_", type, null, true, line);
    }

    Code apply(final Code arg1, YType res, int line1) {
        return new Apply(res, this, arg1, line1) {
            Code apply(final Code arg2, final YType res,
                       final int line2) {
                class A extends Code implements CodeGen {
                    public void gen2(Ctx ctx, Code param, int line) {
                        genApply2(ctx, arg1, arg2, line2);
                    }

                    void gen(Ctx ctx) {
                        if (prepareConst(ctx)) {
                            Object[] key = { Core2.this.getClass(),
                                             arg1.valueKey(), arg2.valueKey() };
                            ctx.constant(Arrays.asList(key),
                                    new SimpleCode(this, null, type, 0));
                        } else {
                            genApply2(ctx, arg1, arg2, line2);
                        }
                    }

                    boolean flagop(int fl) {
                        return derivePolymorph && (fl & (CONST | PURE)) != 0 &&
                                arg1.flagop(fl) && arg2.flagop(fl);
                    }

                    boolean prepareConst(Ctx ctx) {
                        return derivePolymorph && arg1.prepareConst(ctx) &&
                                arg2.prepareConst(ctx);
                    }
                };
                A r = new A();
                r.type = res;
                r.polymorph = derivePolymorph && arg1.polymorph
                                              && arg2.polymorph;
                return r;
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
        if (!list.flagop(LIST_RANGE) && fun instanceof Function &&
                    (f = (Function) fun).uncapture(arg)) {
            Label retry = new Label(), end = new Label();
            list.gen(ctx);
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.insn(DUP);
            ctx.jumpInsn(IFNULL, end);
            ctx.insn(DUP);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList", "isEmpty", "()Z");
            ctx.jumpInsn(IFNE, end);
            // start of loop
            ctx.visitLabel(retry);
            ctx.insn(DUP);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "first", "()Ljava/lang/Object;");
            // invoke body block
            ctx.varInsn(ASTORE, arg.var = ctx.localVarCount++);
            ++ctx.tainted; // disable argument-nulling - we're in cycle
            // new closure has to be created on each cycle
            // as closure vars could be captured
            f.genClosureInit(ctx);
            f.body.gen(ctx);
            --ctx.tainted;
            ctx.visitLine(line);
            ctx.insn(POP); // ignore return value
            // next
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "next", "()Lyeti/lang/AIter;");
            ctx.insn(DUP);
            ctx.jumpInsn(IFNONNULL, retry);
            ctx.visitLabel(end);
        } else {
            Label nop = new Label(), end = new Label();
            list.gen(ctx);
            fun.gen(ctx);
            ctx.visitLine(line);
            ctx.insn(SWAP);
            ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
            ctx.insn(DUP_X1);
            ctx.jumpInsn(IFNULL, nop);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "forEach", "(Ljava/lang/Object;)V");
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(nop);
            ctx.insn(POP2);
            ctx.visitLabel(end);
            ctx.insn(ACONST_NULL);
        }
    }
}

final class Compose extends Core2 {
    Compose(int line) {
        super("$d", YetiType.COMPOSE_TYPE, line);
        derivePolymorph = true;
    }

    void genApply2(Ctx ctx, Code arg1, Code arg2, int line) {
        ctx.typeInsn(NEW, "yeti/lang/Compose");
        ctx.insn(DUP);
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
        ctx.insn(DUP);
        ctx.varInsn(ASTORE, monitorVar);
        ctx.insn(MONITORENTER);

        Label startBlock = new Label(), endBlock = new Label();
        ctx.visitLabel(startBlock);
        new Apply(type, block, new UnitConstant(null), line).gen(ctx);
        ctx.visitLine(line);
        ctx.load(monitorVar).insn(MONITOREXIT);
        ctx.visitLabel(endBlock);
        Label end = new Label();
        ctx.jumpInsn(GOTO, end);

        Label startCleanup = new Label(), endCleanup = new Label();
        ctx.tryCatchBlock(startBlock, endBlock, startCleanup, null);
        // I have no fucking idea, what this second catch is supposed
        // to be doing. javac generates it, so it has to be good.
        // yeah, sure...
        ctx.tryCatchBlock(startCleanup, endCleanup, startCleanup, null);

        int exceptionVar = ctx.localVarCount++;
        ctx.visitLabel(startCleanup);
        ctx.varInsn(ASTORE, exceptionVar);
        ctx.load(monitorVar).insn(MONITOREXIT);
        ctx.visitLabel(endCleanup);
        ctx.load(exceptionVar).insn(ATHROW);
        ctx.visitLabel(end);
    }
}

abstract class BinOpRef extends BindRef {
    boolean markTail2;
    String coreFun;

    class Result extends Code {
        private Code arg1;
        private Code arg2;

        Result(Code arg1, Code arg2, YType res) {
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

    Code apply(final Code arg1, final YType res1, final int line) {
        return new Code() {
            { type = res1; }

            Code apply(Code arg2, YType res, int line) {
                return new Result(arg1, arg2, res);
            }

            void gen(Ctx ctx) {
                BinOpRef.this.gen(ctx);
                ctx.visitApply(arg1, line);
            }
        };
    }

    void gen(Ctx ctx) {
        ctx.fieldInsn(GETSTATIC, "yeti/lang/std$" + coreFun,
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

    public ArithOpFun(String fun, String method, YType type,
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
        boolean arg2IsInt = arg2.flagop(INT_NUM);
        if (method == "and" && arg2IsInt) {
            ctx.typeInsn(NEW, "yeti/lang/IntNum");
            ctx.insn(DUP);
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                                "longValue", "()J");
            arg2.genInt(ctx, line, true);
            ctx.insn(LAND);
            ctx.visitInit("yeti/lang/IntNum", "(J)V");
            ctx.forceType("yeti/lang/Num");
            return;
        }
        arg1.gen(ctx);
        ctx.visitLine(line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
        if (method == "shl" || method == "shr") {
            arg2.genInt(ctx, line, false);
            if (method == "shr")
                ctx.insn(INEG);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                           "shl", "(I)Lyeti/lang/Num;");
        } else if (arg2IsInt) {
            boolean ii = method == "intDiv" || method == "rem";
            arg2.genInt(ctx, line, !ii);
            ctx.visitLine(line);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                method, ii ? "(I)Lyeti/lang/Num;" : "(J)Lyeti/lang/Num;");
            ctx.forceType("yeti/lang/Num");
        } else {
            arg2.gen(ctx);
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Num",
                    method, "(Lyeti/lang/Num;)Lyeti/lang/Num;");
        }
        ctx.forceType("yeti/lang/Num");
    }
}

final class ArithOp implements Binder {
    private String fun;
    private String method;
    private YType type;

    ArithOp(String op, String method, YType type) {
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
        YType t = arg1.type.deref();
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
            ctx.insn(DUP); // 2-1-1
            ctx.jumpInsn(IFNONNULL, nonull); // 2-1
            // reach here, when 1 was null
            if (op == COND_GT || op == COND_LE ||
                arg2.flagop(EMPTY_LIST) &&
                    (op == COND_EQ || op == COND_NOT)) {
                // null is never greater and always less or equal
                ctx.insn(POP2);
                ctx.jumpInsn(GOTO,
                    op == COND_LE || op == COND_EQ ? to : nojmp);
            } else {
                ctx.insn(POP); // 2
                ctx.jumpInsn(op == COND_EQ || op == COND_GE
                                    ? IFNULL : IFNONNULL, to);
                ctx.jumpInsn(GOTO, nojmp);
            }
            ctx.visitLabel(nonull);
            if (!eq && ctx.compilation.isGCJ)
                ctx.typeInsn(CHECKCAST, "java/lang/Comparable");
            ctx.insn(SWAP); // 1-2
        } else if (arg2 instanceof StringConstant &&
                   ((StringConstant) arg2).str.length() == 0 &&
                   (op & COND_LT) == 0) {
            arg1.gen(ctx);
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "java/lang/String");
            ctx.methodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
            ctx.jumpInsn((op & COND_NOT) == (op >>> 2) ? IFEQ : IFNE, to);
            return;
        } else {
            arg1.gen(ctx);
            ctx.visitLine(line);
            if (arg2.flagop(INT_NUM)) {
                ctx.typeInsn(CHECKCAST, "yeti/lang/Num");
                arg2.genInt(ctx, line, true);
                ctx.visitLine(line);
                ctx.methodInsn(INVOKEVIRTUAL,
                        "yeti/lang/Num", "rCompare", "(J)I");
                ctx.jumpInsn(ROP[op], to);
                return;
            }
            if (!eq && ctx.compilation.isGCJ)
                ctx.typeInsn(CHECKCAST, "java/lang/Comparable");
            arg2.gen(ctx);
            ctx.visitLine(line);
        }
        if (eq) {
            op ^= COND_NOT;
            ctx.methodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                "equals", "(Ljava/lang/Object;)Z");
        } else {
            ctx.methodInsn(INVOKEINTERFACE, "java/lang/Comparable",
                                "compareTo", "(Ljava/lang/Object;)I");
        }
        ctx.jumpInsn(OPS[op], to);
        if (nojmp != null) {
            ctx.visitLabel(nojmp);
        }
    }
}

final class Compare implements Binder {
    YType type;
    int op;
    String fun;

    public Compare(YType type, int op, String fun) {
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
        ctx.jumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
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
            ctx.typeInsn(CHECKCAST, "yeti/lang/ByKey");
        }
        arg1.gen(ctx);
        ctx.visitLine(line);
        ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/ByKey",
                            "containsKey", "(Ljava/lang/Object;)Z");
        ctx.jumpInsn(ifTrue ? IFNE : IFEQ, to);
    }
}

final class NotOp extends StaticRef {
    NotOp(int line) {
        super("yeti/lang/std$not", "_",
              YetiType.BOOL_TO_BOOL, null, false, line);
    }

    Code apply(final Code arg, YType res, int line) {
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
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(label);
            ctx.fieldInsn(GETSTATIC, "java/lang/Boolean",
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
        ctx.visitLine(line);
        ctx.typeInsn(NEW, "yeti/lang/LList");
        ctx.insn(DUP);
        arg1.gen(ctx);
        arg2.gen(ctx);
        ctx.visitLine(line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
        if (arg2.type.deref().param[1].deref() != YetiType.NO_TYPE) {
            Label cons = new Label();
            ctx.insn(DUP);
            ctx.jumpInsn(IFNULL, cons); // null, ok
            ctx.insn(DUP);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList", "isEmpty", "()Z");
            ctx.jumpInsn(IFEQ, cons); // not empty, ok
            ctx.insn(POP); // empty not-null, dump it
            ctx.insn(ACONST_NULL); // and use null instead
            ctx.visitLabel(cons);
        }
        ctx.visitInit("yeti/lang/LList",
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
        ctx.typeInsn(NEW, "yeti/lang/LazyList");
        ctx.insn(DUP);
        arg1.gen(ctx);
        arg2.gen(ctx);
        ctx.visitLine(line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/Fun");
        ctx.visitInit("yeti/lang/LazyList",
                      "(Ljava/lang/Object;Lyeti/lang/Fun;)V");
        ctx.forceType("yeti/lang/AList");
    }
}

final class MatchOpFun extends BinOpRef implements CodeGen {
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

    public void gen2(Ctx ctx, Code arg2, int line) {
        ctx.typeInsn(NEW, "yeti/lang/Match");
        ctx.insn(DUP);
        arg2.gen(ctx);
        ctx.intConst(yes ? 1 : 0);
        ctx.visitLine(line);
        ctx.visitInit("yeti/lang/Match", "(Ljava/lang/Object;Z)V");
    }

    Code apply2nd(final Code arg2, final YType t, final int line) {
        if (line == 0) {
            throw new NullPointerException();
        }
        final Code matcher = new SimpleCode(this, arg2, t, line);
        if (!(arg2 instanceof StringConstant))
            return matcher;
        try {
            Pattern.compile(((StringConstant) arg2).str, Pattern.DOTALL);
        } catch (PatternSyntaxException ex) {
            throw new CompileException(line, 0,
                        "Bad pattern syntax: " + ex.getMessage());
        }
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
        ctx.fieldInsn(GETSTATIC, "java/lang/Boolean",
                      "TRUE", "Ljava/lang/Boolean;");
        ctx.jumpInsn(ifTrue ? IF_ACMPEQ : IF_ACMPNE, to);
    }
}

final class RegexFun extends StaticRef implements CodeGen {
    private String impl;
    private String funName;

    RegexFun(String fun, String impl, YType type,
             Binder binder, int line) {
        super("yeti/lang/std$" + fun, "_", type, null, false, line);
        this.funName = fun;
        this.binder = binder;
        this.impl = impl;
    }

    public void gen2(Ctx ctx, Code arg, int line) {
        ctx.typeInsn(NEW, impl);
        ctx.insn(DUP);
        arg.gen(ctx);
        ctx.visitLine(line);
        ctx.visitInit(impl, "(Ljava/lang/Object;)V");
    }

    Code apply(final Code arg, final YType t, final int line) {
        final Code f = new SimpleCode(this, arg, t, line);
        if (!(arg instanceof StringConstant))
            return f;
        try {
            Pattern.compile(((StringConstant) arg).str, Pattern.DOTALL);
        } catch (PatternSyntaxException ex) {
            throw new CompileException(line, 0,
                        "Bad pattern syntax: " + ex.getMessage());
        }
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
    private YType type;

    Regex(String fun, String impl, YType type) {
        this.fun = fun;
        this.impl = impl;
        this.type = type;
    }

    public BindRef getRef(int line) {
        return new RegexFun(fun, impl, type, this, line);
    }
}

final class ClassOfExpr extends Code implements CodeGen {
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

    public void gen2(Ctx ctx, Code param, int line) {
        ctx.ldcInsn(className);
        ctx.methodInsn(INVOKESTATIC, "java/lang/Class",
            "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
        ctx.forceType("java/lang/Class");
    }

    void gen(Ctx ctx) {
        ctx.constant("CLASS-OF:".concat(className),
                new SimpleCode(this, null, YetiType.CLASS_TYPE, 0));
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
        ctx.typeInsn(INSTANCEOF, className);
        ctx.jumpInsn(ifTrue ? IFNE : IFEQ, to);
    }

    void gen(Ctx ctx) {
        Label label = new Label();
        genIf(ctx, label, false);
        ctx.genBoolean(label);
    }
}

final class JavaArrayRef extends Code implements CodeGen {
    Code value, index;
    YType elementType;
    int line;

    JavaArrayRef(YType _type, Code _value, Code _index, int _line) {
        type = JavaType.convertValueType(elementType = _type);
        value = _value;
        index = _index;
        line = _line;
    }

    private void _gen(Ctx ctx, Code store) {
        value.gen(ctx);
        ctx.typeInsn(CHECKCAST, JavaType.descriptionOf(value.type));
        index.genInt(ctx, line, false);
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
                ctx.typeInsn(CHECKCAST, resDescr);
        }
        ctx.insn(insn);
        if (insn == AALOAD) {
            ctx.forceType(resDescr);
        }
    }

    void gen(Ctx ctx) {
        _gen(ctx, null);
        JavaExpr.convertValue(ctx, elementType);
    }

    public void gen2(Ctx ctx, Code setValue, int line) {
        _gen(ctx, setValue);
        ctx.insn(ACONST_NULL);
    }

    Code assign(final Code setValue) {
        return new SimpleCode(this, setValue, null, 0);
    }
}

final class StrOp extends StaticRef implements Binder {
    final static Code NOP_CODE = new Code() {
        void gen(Ctx ctx) {
        }
    };

    String method;
    String sig;
    YType argTypes[];

    final class StrApply extends Apply {
        StrApply prev;

        StrApply(Code arg, YType type, StrApply prev, int line) {
            super(type, NOP_CODE, arg, line);
            this.prev = prev;
        }

        Code apply(Code arg, YType res, int line) {
            return new StrApply(arg, res, this, line);
        }

        void genApply(Ctx ctx) {
            super.gen(ctx);
        }

        void gen(Ctx ctx) {
            genIf(ctx, null, false);
        }

        void genIf(Ctx ctx, Label to, boolean ifTrue) {
            List argv = new ArrayList();
            for (StrApply a = this; a != null; a = a.prev) {
                argv.add(a);
            }
            if (argv.size() != argTypes.length) {
                StrOp.this.gen(ctx);
                for (int i = argv.size(); --i >= 0;)
                    ((StrApply) argv.get(i)).genApply(ctx);
                return;
            }
            ((StrApply) argv.get(argv.size() - 1)).arg.gen(ctx);
            ctx.visitLine(line);
            ctx.typeInsn(CHECKCAST, "java/lang/String");
            for (int i = 0, last = argv.size() - 2; i <= last; ++i) {
                StrApply a = (StrApply) argv.get(last - i);
                if (a.arg.type.deref().type == YetiType.STR) {
                    a.arg.gen(ctx);
                    ctx.typeInsn(CHECKCAST, "java/lang/String");
                } else {
                    JavaExpr.convertedArg(ctx, a.arg, argTypes[i], a.line);
                }
            }
            ctx.visitLine(line);
            ctx.methodInsn(INVOKEVIRTUAL, "java/lang/String",
                                method, sig);
            if (to != null) { // really genIf
                ctx.jumpInsn(ifTrue ? IFNE : IFEQ, to);
            } else if (type.deref().type == YetiType.STR) {
                ctx.forceType("java/lang/String;");
            } else {
                JavaExpr.convertValue(ctx, argTypes[argTypes.length - 1]);
            }
        }
    }

    StrOp(String fun, String method, String sig, YType type) {
        super("yeti/lang/std$" + mangle(fun), "_", type, null, false, 0);
        this.method = method;
        this.sig = sig;
        binder = this;
        argTypes = JavaTypeReader.parseSig1(1, sig);
    }

    public BindRef getRef(int line) {
        return this;
    }

    Code apply(final Code arg, final YType res, final int line) {
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
        ctx.typeInsn(CHECKCAST, "java/lang/String");
        arg2.genInt(ctx, line, false);
        ctx.insn(DUP);
        ctx.intConst(1);
        ctx.insn(IADD);
        ctx.methodInsn(INVOKEVIRTUAL, "java/lang/String",
                       "substring", "(II)Ljava/lang/String;");
        ctx.forceType("java/lang/String");
    }
}
