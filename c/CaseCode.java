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
        private List params = new ArrayList();
        private int paramBase;
        private boolean enclosing; // for sanity check
        CaseExpr caseExpr;

        class Param extends BindRef implements Binder {
            int nth = -1;

            public BindRef getRef(int line) {
                binder = this;
                return this;
            }

            void gen(Ctx ctx) {
                ctx.m.visitVarInsn(ALOAD, caseExpr.paramStart + nth);
            }

            void store(Ctx ctx) {
                if (nth < 0) {
                    ctx.m.visitInsn(POP);
                } else {
                    ctx.m.visitVarInsn(ASTORE, caseExpr.paramStart + nth);
                }
            }
        }

        void noParam() {
            params.add(new Param());
        }

        Binder bindParam(YetiType.Type type) {
            if (enclosing) {
                throw new IllegalStateException(
                    "bindParam not allowed after having children");
            }
            Param p = new Param();
            p.type = type;
            p.nth = paramBase + params.size();
            if (caseExpr.paramCount <= p.nth)
                caseExpr.paramCount = p.nth + 1;
            params.add(p);
            return p;
        }

        void enclose(CasePattern parent) {
            if (params.size() != 0) {
                throw new IllegalStateException(
                    "enclose not allowed after bindParam");
            }
            paramBase = parent.paramBase + parent.params.size();
            parent.enclosing = true;
        }

        Param param(int n) {
            return (Param) params.get(n);
        }

        void StoreParam(Ctx ctx, int n) {
            ((Param) params.get(n)).store(ctx);
        }

        abstract void prepareCase(Ctx ctx);
        abstract void caseCleanup(Ctx ctx);
        abstract void jumpUnless(Ctx ctx, Label to);
    }

    class VariantPattern extends CasePattern {
        String variantTag;

        VariantPattern(CaseExpr caseExpr, String tagName) {
            this.caseExpr = caseExpr;
            variantTag = tagName;
        }

        void prepareCase(Ctx ctx) {
            ctx.m.visitTypeInsn(CHECKCAST, "yeti/lang/Tag");
            ctx.m.visitInsn(DUP);
            ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "name",
                                 "Ljava/lang/String;");
        }

        void caseCleanup(Ctx ctx) {
            ctx.m.visitInsn(POP2);
        }

        void jumpUnless(Ctx ctx, Label to) {
            ctx.m.visitInsn(DUP);
            ctx.m.visitLdcInsn(variantTag);
            ctx.m.visitJumpInsn(IF_ACMPNE, to);
            ctx.m.visitInsn(POP);
            Param p = param(0);
            if (p.nth < 0) {
                ctx.m.visitInsn(POP);
            } else {
                ctx.m.visitFieldInsn(GETFIELD, "yeti/lang/Tag", "value",
                                     "Ljava/lang/Object;");
                p.store(ctx);
            }
        }
    }

    class CaseExpr extends Code {
        Code caseValue;
        List choices = new ArrayList();
        int paramStart;
        int paramCount;

        CaseExpr(Code caseValue) {
            this.caseValue = caseValue;
        }

        private class Choice {
            CasePattern pattern;
            Code expr;
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
            ctx.localVarCount += paramCount;
            Label next = null, end = new Label();
            ((Choice) choices.get(0)).pattern.prepareCase(ctx);
            for (int last = choices.size() - 1, i = 0; i <= last; ++i) {
                Choice c = (Choice) choices.get(i);
                next = new Label();
                c.pattern.jumpUnless(ctx, next);
                c.expr.gen(ctx);
                ctx.m.visitJumpInsn(GOTO, end);
                ctx.m.visitLabel(next);
            }
            ctx.m.visitLabel(next);
            ((Choice) choices.get(0)).pattern.caseCleanup(ctx);
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
