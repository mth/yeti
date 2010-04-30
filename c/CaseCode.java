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

abstract class CasePattern implements Opcodes {
    static final CasePattern ANY_PATTERN = new CasePattern() {
        void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
            if (!preserve) {
                ctx.insn(POP);
            }
        }

        boolean irrefutable() {
            return true;
        }
    };

    int preparePattern(Ctx ctx) {
        return 1;
    }

    abstract void tryMatch(Ctx ctx, Label onFail, boolean preserve);

    boolean irrefutable() {
        return false;
    }
}

final class BindPattern extends CasePattern implements Binder {
    private CaseExpr caseExpr;
    private int nth;

    BindRef param = new BindRef() {
        void gen(Ctx ctx) {
            ctx.load(caseExpr.paramStart + nth);
        }
    };

    BindPattern(CaseExpr caseExpr, YType type) {
        this.caseExpr = caseExpr;
        param.binder = this;
        param.type = type;
        nth = caseExpr.paramCount++;
    }

    public BindRef getRef(int line) {
        return param;
    }

    void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
        if (preserve) {
            ctx.insn(DUP);
        }
        ctx.varInsn(ASTORE, caseExpr.paramStart + nth);
    }

    boolean irrefutable() {
        return true;
    }
}

final class ConstPattern extends CasePattern {
    Code v;

    ConstPattern(Code value) {
        v = value;
    }

    void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
        if (preserve) {
            ctx.insn(DUP);
        }
        v.gen(ctx);
        ctx.methodInsn(INVOKEVIRTUAL, "java/lang/Object",
                            "equals", "(Ljava/lang/Object;)Z");
        ctx.jumpInsn(IFEQ, onFail);
    }
}

abstract class AListPattern extends CasePattern {
    static final CasePattern EMPTY_PATTERN = new CasePattern() {
        void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
            ctx.insn(DUP);
            Label cont = new Label();
            ctx.jumpInsn(IFNULL, cont);
            if (preserve) {
                ctx.insn(DUP);
            }
            ctx.typeInsn(CHECKCAST, "yeti/lang/AIter");
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "isEmpty", "()Z");
            ctx.jumpInsn(IFEQ, onFail);
            if (preserve) {
                ctx.visitLabel(cont);
            } else {
                Label end = new Label();
                ctx.jumpInsn(GOTO, end);
                ctx.visitLabel(cont);
                ctx.insn(POP);
                ctx.visitLabel(end);
            }
        }
    };

    abstract boolean listMatch(Ctx ctx, Label onFail, Label dropFail);

    void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
        Label dropFail = preserve ? onFail : new Label();
        ctx.insn(DUP);
        ctx.jumpInsn(IFNULL, dropFail);
        ctx.typeInsn(CHECKCAST, "yeti/lang/AList");
        ctx.insn(DUP);
        ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                            "isEmpty", "()Z");
        ctx.jumpInsn(IFNE, dropFail);
        if (preserve) {
            ctx.insn(DUP);
            dropFail = new Label();
        }
        if (listMatch(ctx, onFail, dropFail) || !preserve) {
            Label cont = new Label();
            ctx.jumpInsn(GOTO, cont);
            ctx.visitLabel(dropFail);
            ctx.insn(POP);
            ctx.jumpInsn(GOTO, onFail);
            ctx.visitLabel(cont);
        }
    }
}

final class ConsPattern extends AListPattern {
    private CasePattern hd;
    private CasePattern tl;

    ConsPattern(CasePattern hd, CasePattern tl) {
        this.hd = hd;
        this.tl = tl;
    }

    boolean listMatch(Ctx ctx, Label onFail, Label dropFail) {
        if (hd != ANY_PATTERN) {
            if (tl != ANY_PATTERN) {
                ctx.insn(DUP);
            } else {
                dropFail = onFail;
            }
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "first", "()Ljava/lang/Object;");
            hd.preparePattern(ctx);
            hd.tryMatch(ctx, dropFail, false);
        }
        if (tl != ANY_PATTERN) {
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AList",
                                "rest", "()Lyeti/lang/AList;");
            tl.preparePattern(ctx);
            tl.tryMatch(ctx, onFail, false);
        } else if (hd == ANY_PATTERN) {
            ctx.insn(POP);
        }
        return dropFail != onFail && !hd.irrefutable();
    }
}

final class ListPattern extends AListPattern {
    private CasePattern[] items;

    ListPattern(CasePattern[] items) {
        this.items = items;
    }

    boolean listMatch(Ctx ctx, Label onFail, Label dropFail) {
        boolean dropUsed = false;
        for (int i = 0; i < items.length; ++i) {
            if (i != 0) {
                ctx.insn(DUP);
                ctx.jumpInsn(IFNULL, dropFail);
                dropUsed = true;
            }
            if (items[i] != ANY_PATTERN) {
                ctx.insn(DUP);
                ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                    "first", "()Ljava/lang/Object;");
                items[i].preparePattern(ctx);
                items[i].tryMatch(ctx, dropFail, false);
                dropUsed |= !items[i].irrefutable();
            }
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                                "next", "()Lyeti/lang/AIter;");
        }
        ctx.jumpInsn(IFNONNULL, onFail);
        return dropUsed;
    }
}

final class StructPattern extends CasePattern {
    private String[] names;
    private CasePattern[] patterns;

    StructPattern(String[] names, CasePattern[] patterns) {
        this.names = names;
        this.patterns = patterns;
    }

    int preparePattern(Ctx ctx) {
        return 1;
    }

    void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
        boolean dropped = false;
        Label failed = preserve ? onFail : new Label();
        for (int i = 0; i < names.length; ++i) {
            if (patterns[i] == ANY_PATTERN)
                continue;
            if (preserve || i != names.length - 1)
                ctx.insn(DUP);
            else dropped = true;
            ctx.ldcInsn(names[i]);
            ctx.methodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                      "get", "(Ljava/lang/String;)Ljava/lang/Object;");
            patterns[i].preparePattern(ctx);
            patterns[i].tryMatch(ctx, i < names.length - 1
                                        ? failed : onFail, false);
        }
        if (!preserve && names.length > 1) {
            Label ok = new Label();
            ctx.jumpInsn(GOTO, ok);
            ctx.visitLabel(failed);
            ctx.insn(POP);
            ctx.jumpInsn(GOTO, onFail);
            ctx.visitLabel(ok);
            if (!dropped)
                ctx.insn(POP);
        }
    }
}

final class VariantPattern extends CasePattern {
    String variantTag;
    CasePattern variantArg;

    VariantPattern(String tagName, CasePattern arg) {
        variantTag = tagName;
        variantArg = arg;
    }

    int preparePattern(Ctx ctx) {
        ctx.typeInsn(CHECKCAST, "yeti/lang/Tag");
        ctx.insn(DUP);
        ctx.fieldInsn(GETFIELD, "yeti/lang/Tag", "name",
                           "Ljava/lang/String;");
        return 2; // TN
    }

    void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
        Label jumpTo = onFail;
        if (preserve) {
            ctx.insn(DUP); // TNN
            ctx.ldcInsn(variantTag);
            ctx.jumpInsn(IF_ACMPNE, onFail); // TN
            if (variantArg == ANY_PATTERN) {
                return;
            }
            ctx.insn(SWAP); // NT
            ctx.insn(DUP_X1); // TNT
        } else if (variantArg == ANY_PATTERN) {
            ctx.insn(SWAP); // NT
            ctx.insn(POP); // N
            ctx.ldcInsn(variantTag);
            ctx.jumpInsn(IF_ACMPNE, onFail);
            return;
        } else {
            Label cont = new Label(); // TN
            ctx.ldcInsn(variantTag);
            ctx.jumpInsn(IF_ACMPEQ, cont); // T
            ctx.insn(POP);
            ctx.jumpInsn(GOTO, onFail);
            ctx.visitLabel(cont);
        }
        ctx.fieldInsn(GETFIELD, "yeti/lang/Tag", "value",
                             "Ljava/lang/Object;"); // TNt (t)
        variantArg.preparePattern(ctx); 
        variantArg.tryMatch(ctx, onFail, false); // TN ()
    }
}

final class CaseExpr extends Code {
    private int totalParams;
    private Code caseValue;
    private List choices = new ArrayList();
    int paramStart;
    int paramCount;

    CaseExpr(Code caseValue) {
        this.caseValue = caseValue;
    }

    private static final class Choice {
        CasePattern pattern;
        Code expr;
    }

    void resetParams() {
        if (totalParams < paramCount) {
            totalParams = paramCount;
        }
        paramCount = 0;
    }

    void addChoice(CasePattern pattern, Code code) {
        Choice c = new Choice();
        c.pattern = pattern;
        c.expr = code;
        choices.add(c);
    }

    void gen(Ctx ctx) {
        caseValue.gen(ctx);
        paramStart = ctx.localVarCount;
        ctx.localVarCount += totalParams;
        Label next = null, end = new Label();
        CasePattern lastPattern = ((Choice) choices.get(0)).pattern;
        int patternStack = lastPattern.preparePattern(ctx);

        for (int last = choices.size() - 1, i = 0; i <= last; ++i) {
            Choice c = (Choice) choices.get(i);
            if (lastPattern.getClass() != c.pattern.getClass()) {
                ctx.popn(patternStack - 1);
                patternStack = c.pattern.preparePattern(ctx);
            }
            lastPattern = c.pattern;
            next = new Label();
            c.pattern.tryMatch(ctx, next, true);
            ctx.popn(patternStack);
            c.expr.gen(ctx);
            ctx.jumpInsn(GOTO, end);
            ctx.visitLabel(next);
        }
        ctx.visitLabel(next);
        ctx.popn(patternStack - 1);
        ctx.methodInsn(INVOKESTATIC, "yeti/lang/Core",
                       "badMatch", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ctx.visitLabel(end);
    }

    void markTail() {
        for (int i = choices.size(); --i >= 0;) {
            ((Choice) choices.get(i)).expr.markTail();
        }
    }
}
