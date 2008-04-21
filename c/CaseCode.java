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

interface CaseCode extends YetiCode {

    abstract class CasePattern implements Opcodes {
        int preparePattern(Ctx ctx) {
            return 1;
        }

        abstract void tryMatch(Ctx ctx, Label onFail, boolean preserve);

        boolean irrefutable() {
            return false;
        }
    }

    class BindPattern extends CasePattern implements Binder {
        private CaseExpr caseExpr;
        private int nth;

        BindRef param = new BindRef() {
            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, caseExpr.paramStart + nth);
            }
        };

        BindPattern(CaseExpr caseExpr, YetiType.Type type) {
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
                ctx.m.visitInsn(DUP);
            }
            ctx.m.visitVarInsn(ASTORE, caseExpr.paramStart + nth);
        }

        boolean irrefutable() {
            return true;
        }
    }

    CasePattern ANY_PATTERN = new CasePattern() {
        void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
            if (!preserve) {
                ctx.m.visitInsn(POP);
            }
        }

        boolean irrefutable() {
            return true;
        }
    };

    class ConstPattern extends CasePattern {
        Code v;

        ConstPattern(Code value) {
            v = value;
        }

        void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
            if (preserve) {
                ctx.m.visitInsn(DUP);
            }
            v.gen(ctx);
            ctx.m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object",
                                  "equals", "(Ljava/lang/Object;)Z");
            ctx.m.visitJumpInsn(IFEQ, onFail);
        }
    }

    class VariantPattern extends CasePattern {
        String variantTag;
        CasePattern variantArg;

        VariantPattern(String tagName, CasePattern arg) {
            variantTag = tagName;
            variantArg = arg;
        }

        int preparePattern(Ctx ctx) {
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Tag");
            ctx.m.visitInsn(DUP);
            ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "name",
                                 "Ljava/lang/String;");
            return 2; // TN
        }

        void tryMatch(Ctx ctx, Label onFail, boolean preserve) {
            Label jumpTo = onFail;
            if (preserve) {
                ctx.m.visitInsn(DUP); // TNN
                ctx.m.visitLdcInsn(variantTag);
                ctx.m.visitJumpInsn(IF_ACMPNE, onFail); // TN
                ctx.m.visitInsn(SWAP); // NT
                ctx.m.visitInsn(DUP_X1); // TNT
            } else {
                Label cont = new Label(); // TN
                ctx.m.visitLdcInsn(variantTag);
                ctx.m.visitJumpInsn(IF_ACMPEQ, cont); // T
                ctx.m.visitInsn(POP);
                ctx.m.visitJumpInsn(GOTO, onFail);
                ctx.m.visitLabel(cont);
            }
            ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "value",
                                 "Ljava/lang/Object;"); // TNt (t)
            variantArg.preparePattern(ctx); 
            variantArg.tryMatch(ctx, onFail, false); // TN ()
        }
    }

    class CaseExpr extends Code {
        private int totalParams;
        private Code caseValue;
        private List choices = new ArrayList();
        int paramStart;
        int paramCount;

        CaseExpr(Code caseValue) {
            this.caseValue = caseValue;
        }

        private static class Choice {
            CasePattern pattern;
            Code expr;
        }

        void addChoice(CasePattern pattern, Code code) {
            Choice c = new Choice();
            c.pattern = pattern;
            c.expr = code;
            choices.add(c);
            if (totalParams < paramCount) {
                totalParams = paramCount;
            }
            paramCount = 0;
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
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(next);
            }
            ctx.m.visitLabel(next);
            ctx.popn(patternStack);
            ctx.m.visitInsn(ACONST_NULL);
            ctx.m.visitLabel(end);
        }

        void markTail() {
            for (int i = choices.size(); --i >= 0;) {
                ((Choice) choices.get(i)).expr.markTail();
            }
        }
    }
}
