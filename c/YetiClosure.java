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

import yeti.renamed.asm3.Label;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.HashMap;

interface Closure {
    // Closures "wrap" references to the outside world.
    BindRef refProxy(BindRef code);
    void addVar(BindExpr binder);
}

interface CaptureWrapper {
    void genPreGet(Ctx ctx);
    void genGet(Ctx ctx);
    void genSet(Ctx ctx, Code value);
    Object captureIdentity();
    String captureType();
}

class Apply extends Code {
    final Code fun, arg;
    final int line;
    int arity = 1;
    BindExpr.Ref ref;

    Apply(YType res, Code fun, Code arg, int line) {
        type = res;
        this.fun = fun;
        this.arg = arg;
        this.line = line;
    }

    void gen(Ctx ctx) {
        Function f;
        int argc = 0;

        // Function sets its methodImpl field, if it has determined that
        // it optimises itself into simple method.
        if (ref != null &&
               (f = (Function) ((BindExpr) ref.binder).st).methodImpl != null
               && arity == (argc = f.methodImpl.argVar)) {
            //System.err.println("A" + arity + " F" + argc);
            // first argument is function value (captures array really)
            StringBuffer sig = new StringBuffer(f.capture1 ? "(" : "([");
            sig.append("Ljava/lang/Object;");
            Apply a = this; // "this" is the last argument applied, so reverse
            Code[] args = new Code[argc];
            for (int i = argc; --i > 0; a = (Apply) a.fun)
                args[i] = a.arg;
            args[0] = a.arg; // out-of-cycle as we need "a" for fun
            a.fun.gen(ctx);
            if (!f.capture1)
                ctx.typeInsn(CHECKCAST, "[Ljava/lang/Object;");
            for (int i = 0; i < argc; ++i) {
                args[i].gen(ctx);
                sig.append("Ljava/lang/Object;");
            }
            sig.append(")Ljava/lang/Object;");
            ctx.visitLine(line);
            ctx.methodInsn(INVOKESTATIC, f.name, f.bindName, sig.toString());
            return;
        }

        if (fun instanceof Function) {
            f = (Function) fun;
            LoadVar arg_ = new LoadVar();
            // inline direct calls
            // TODO: constants don't need a temp variable
            if (f.uncapture(arg_)) {
                arg.gen(ctx);
                arg_.var = ctx.localVarCount++;
                ctx.varInsn(ASTORE, arg_.var);
                f.genClosureInit(ctx);
                f.body.gen(ctx);
                return;
            }
        }

        Apply to = (arity & 1) == 0 && arity - argc > 1 ? (Apply) fun : this;
        to.fun.gen(ctx);
        ctx.visitLine(to.line);
        ctx.typeInsn(CHECKCAST, "yeti/lang/Fun");
        if (to == this) {
            ctx.visitApply(arg, line);
        } else {
            to.arg.gen(ctx);
            arg.gen(ctx);
            ctx.visitLine(line);
            ctx.methodInsn(INVOKEVIRTUAL, "yeti/lang/Fun", "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

    Code apply(Code arg, final YType res, int line) {
        Apply a = new Apply(res, this, arg, line);
        a.arity = arity + 1;
        if (ref != null) {
            ref.arity = a.arity;
            a.ref = ref;
        }
        return a;
    }
}

/*
 * Since the stupid JVM discards local stack when catching exceptions,
 * try-catch blocks have to be converted into fucking closures
 * (at least for the generic case).
 */
final class TryCatch extends CapturingClosure {
    private List catches = new ArrayList();
    private int exVar;
    Code block;
    Code cleanup;

    final class Catch extends BindRef implements Binder {
        Code handler;

        public BindRef getRef(int line) {
            return this;
        }

        void gen(Ctx ctx) {
            ctx.load(exVar);
        }
    }

    void setBlock(Code block) {
        this.type = block.type;
        this.block = block;
    }

    Catch addCatch(YType ex) {
        Catch c = new Catch();
        c.type = ex;
        catches.add(c);
        return c;
    }

    void captureInit(Ctx ctx, Capture c, int n) {
        c.localVar = n;
        c.captureGen(ctx);
    }

    void gen(Ctx ctx) {
        int argc = mergeCaptures(ctx, true);
        StringBuffer sigb = new StringBuffer("(");
        for (Capture c = captures; c != null; c = c.next) {
            sigb.append(c.captureType());
        }
        sigb.append(")Ljava/lang/Object;");
        String sig = sigb.toString();
        String name = "_" + ctx.usedMethodNames.size();
        ctx.usedMethodNames.put(name, null);
        ctx.methodInsn(INVOKESTATIC, ctx.className, name, sig);
        Ctx mc = ctx.newMethod(ACC_PRIVATE | ACC_STATIC, name, sig);
        mc.localVarCount = argc;

        Label codeStart = new Label(), codeEnd = new Label();
        Label cleanupStart = cleanup == null ? null : new Label();
        Label cleanupEntry = cleanup == null ? null : new Label();
        genClosureInit(mc);
        int retVar = -1;
        if (cleanupStart != null) {
            retVar = mc.localVarCount++;
            mc.insn(ACONST_NULL);
            mc.varInsn(ASTORE, retVar); // silence the JVM verifier...
        }
        mc.visitLabel(codeStart);
        block.gen(mc);
        mc.visitLabel(codeEnd);
        exVar = mc.localVarCount++;
        if (cleanupStart != null) {
            Label goThrow = new Label();
            mc.visitLabel(cleanupEntry);
            mc.varInsn(ASTORE, retVar);
            mc.insn(ACONST_NULL);
            mc.visitLabel(cleanupStart);
            mc.varInsn(ASTORE, exVar);
            cleanup.gen(mc);
            mc.insn(POP); // cleanup's null
            mc.load(exVar).jumpInsn(IFNONNULL, goThrow);
            mc.load(retVar).insn(ARETURN);
            mc.visitLabel(goThrow);
            mc.load(exVar).insn(ATHROW);
        } else {
            mc.insn(ARETURN);
        }
        for (int i = 0, cnt = catches.size(); i < cnt; ++i) {
            Catch c = (Catch) catches.get(i);
            Label catchStart = new Label();
            mc.tryCatchBlock(codeStart, codeEnd, catchStart,
                                  c.type.javaType.className());
            Label catchEnd = null;
            if (cleanupStart != null) {
                catchEnd = new Label();
                mc.tryCatchBlock(catchStart, catchEnd, cleanupStart, null);
            }
            mc.visitLabel(catchStart);
            mc.varInsn(ASTORE, exVar);
            c.handler.gen(mc);
            if (catchEnd != null) {
                mc.visitLabel(catchEnd);
                mc.jumpInsn(GOTO, cleanupEntry);
            } else {
                mc.insn(ARETURN);
            }
        }
        if (cleanupStart != null)
            mc.tryCatchBlock(codeStart, codeEnd, cleanupStart, null);
        mc.closeMethod();
    }
}

/*
 * Bind reference that is actually some wrapper created by closure (capturer).
 * This class is mostly useful as a place where tail call optimization happens.
 */
abstract class CaptureRef extends BindRef {
    Function capturer;
    BindRef ref;
    private Binder[] args;
    private Capture[] argCaptures;
    private boolean hasArgCaptures;

    final class SelfApply extends Apply {
        boolean tail;
        int depth;

        SelfApply(YType type, Code f, Code arg, int line, int depth) {
            super(type, f, arg, line);
            this.depth = depth;
            if (origin != null) {
                this.arity = origin.arity = args.length - depth + 1;
                this.ref = origin;
            }
            if (depth == 0 && capturer.argCaptures == null) {
                if (hasArgCaptures)
                    throw new CompileException(line, 0,
                        "Internal error - already has argCaptures");
                hasArgCaptures = true;
                // we have to resolve the captures lazily later,
                // as here all might not yet be referenced
                capturer.argCaptures = CaptureRef.this;
            }
        }

        // evaluates call arguments and pushes values into stack
        void genArg(Ctx ctx, int i) {
            if (i > 0)
                ((SelfApply) fun).genArg(ctx, i - 1);
            arg.gen(ctx);
        }

        void gen(Ctx ctx) {
            if (!tail || depth != 0 || capturer.argCaptures != CaptureRef.this
                      || capturer.restart == null) {
                // regular apply, if tail call optimisation can't be done
                super.gen(ctx);
                return;
            }
            // push all argument values into stack - they must be evaluated
            // BEFORE modifying any of the arguments for tail-"call"-jump.
            genArg(ctx, argCaptures() == null ? 0 : argCaptures.length);
            ctx.varInsn(ASTORE, capturer.argVar);
            // Now assign the call argument values into argument registers.
            if (argCaptures != null) {
                int i = argCaptures.length;
                if (capturer.outer != null && capturer.outer.merged && --i >= 0)
                    ctx.varInsn(ASTORE, 1); // HACK - fixes merged argument
                while (--i >= 0)
                    if (argCaptures[i] != null)
                        ctx.varInsn(ASTORE, argCaptures[i].localVar);
                    else
                        ctx.insn(POP);
            }
            // And just jump into the start of the function...
            ctx.jumpInsn(GOTO, capturer.restart);
        }

        void markTail() {
            tail = true;
        }

        Code apply(Code arg, YType res, int line) {
            if (depth < 0)
                return super.apply(arg, res, line);
            return new SelfApply(res, this, arg, line, depth - 1);
        }
    }

    Capture[] argCaptures() {
        if (hasArgCaptures && argCaptures == null) {
            /*
             * All arguments have been applied, now we have to search
             * their captures in the inner function (by looking for
             * captures matching the function arguments).
             * Resulting list will be also given to the inner function,
             * so it could copy those captures into local registers
             * to allow tail call.
             *
             * NB. To understand this, remember that this is self-apply,
             * so current scope is also the scope of applied function.
             */
            argCaptures = new Capture[args.length];
            for (Capture c = capturer.captures; c != null; c = c.next)
                for (int i = args.length; --i >= 0;)
                    if (c.binder == args[i]) {
                        argCaptures[i] = c;
                        break;
                    }
        }
        return argCaptures;
    }

    Code apply(Code arg, YType res, int line) {
        if (args != null) {
            return new SelfApply(res, this, arg, line, args.length);
        }

        /*
         * We have application with arg x like ((f x) y) z.
         * Now we take the inner function of our scope and travel
         * through its outer functions until there is one.
         *
         * If function that recognizes f as itself is met,
         * we know that this is self-application and how many
         * arguments are needed to do tail-call optimisation.
         * SelfApply with arguments count is given in that case.
         *
         * SelfApply.apply reduces the argument count until final
         * call is reached, in which case tail-call can be done,
         * if the application happens to be in tail position.
         */
        int n = 0;
        for (Function f = capturer; f != null; ++n, f = f.outer)
            if (f.selfBind == ref.binder) {
                if (ref.flagop(ASSIGN))
                    break; // no tail recursion for vars
                args = new Binder[n];
                f = capturer.outer;
                for (int i = n; --i >= 0; f = f.outer)
                    args[i] = f;
                return new SelfApply(res, this, arg, line, n);
            }
        return super.apply(arg, res, line);
    }
}

final class Capture extends CaptureRef implements CaptureWrapper, CodeGen {
    String id;
    Capture next;
    CaptureWrapper wrapper;
    Object identity;
    int localVar = -1; // -1 - use this (TryCatch captures use 0 localVar)
    boolean uncaptured;
    boolean ignoreGet;
    private String refType;

    void gen(Ctx ctx) {
        if (uncaptured) {
            ref.gen(ctx);
            return;
        }
        genPreGet(ctx);
        genGet(ctx);
    }

    String getId(Ctx ctx) {
        if (id == null)
            id = "_".concat(Integer.toString(ctx.fieldCounter++));
        return id;
    }

    boolean flagop(int fl) {
        /*
         * DIRECT_BIND is allowed, because with code like
         * x = 1; try x finally yrt
         * the 1 won't get directly brought into try closure
         * unless the mergeCaptures uncaptures the DIRECT_BIND ones
         * (the variable doesn't (always) know that it will be
         * a direct binding when it's captured, as this determined
         * later using prepareConst())
         * XXX An automatic uncapture is done on DIRECT_BIND, as it allows
         * cascading uncaptures done by mergeCaptures into parent captures,
         * avoiding attempts to generate parent capture wrappings in that case.
         * This fixes 'try-catch class closure' test.
         */
        if (fl == DIRECT_BIND && !uncaptured)
            return uncaptured = ref.flagop(fl);
        return (fl & (PURE | ASSIGN | DIRECT_BIND)) != 0 && ref.flagop(fl);
    }

    public void gen2(Ctx ctx, Code value, int _) {
        if (uncaptured) {
            ref.assign(value).gen(ctx);
        } else {
            genPreGet(ctx);
            wrapper.genSet(ctx, value);
            ctx.insn(ACONST_NULL);
        }
    }

    Code assign(final Code value) {
        if (!ref.flagop(ASSIGN))
            return null;
        return new SimpleCode(this, value, null, 0);
    }

    public void genPreGet(Ctx ctx) {
        if (uncaptured) {
            wrapper.genPreGet(ctx);
        } else if (localVar < 0) {
            ctx.load(0);
            if (localVar < -1) {
                ctx.intConst(-2 - localVar);
                ctx.insn(AALOAD);
                if (wrapper != null) {
                    String cty = wrapper.captureType();
                    if (cty != null)
                        ctx.captureCast(cty);
                }
            } else {
                ctx.fieldInsn(GETFIELD, ctx.className, id, captureType());
            }
        } else {
            ctx.load(localVar);
            // hacky way to forceType on try-catch, but not on method argument
            if (!ignoreGet) {
                ctx.forceType(captureType().charAt(0) == '['
                        ? refType : refType.substring(1, refType.length() - 1));
            }
        }
    }

    public void genGet(Ctx ctx) {
        if (wrapper != null && !ignoreGet) {
            /*
             * The object got from capture might not be the final value.
             * for example captured mutable variables are wrapped into array
             * by the binding, so the wrapper must get correct array index
             * out of the array in that case.
             */
            wrapper.genGet(ctx);
        }
    }

    public void genSet(Ctx ctx, Code value) {
        wrapper.genSet(ctx, value);
    }

    public CaptureWrapper capture() {
        if (uncaptured) {
            return ref.capture();
        }
        return wrapper == null ? null : this;
    }

    public Object captureIdentity() {
        return wrapper == null ? this : wrapper.captureIdentity();
    }

    public String captureType() {
        if (refType == null) {
            if (wrapper != null) {
                refType = wrapper.captureType();
                if (refType == null)
                    throw new IllegalStateException("captureType:" + wrapper);
            } else if (origin != null) {
                refType = ((BindExpr) binder).captureType();
            } else {
                refType = 'L' + javaType(ref.type) + ';';
            }
        }
        return refType;
    }

    void captureGen(Ctx ctx) {
        if (wrapper == null) {
            ref.gen(ctx);
        } else {
            wrapper.genPreGet(ctx);
        }
        // stupid AALOAD in genPreGet returns shit,
        // so have to captureCast for it...
        ctx.captureCast(captureType());
    }

    BindRef unshare() {
        return new BindWrapper(this);
    }
}

abstract class AClosure extends Code implements Closure {
    private List closureVars = new ArrayList();

    public void addVar(BindExpr binder) {
        closureVars.add(binder);
    }

    public final void genClosureInit(Ctx ctx) {
        int id = -1, mvarcount = 0;
        for (int i = closureVars.size(); --i >= 0;) {
            BindExpr bind = (BindExpr) closureVars.get(i);
            if (bind.assigned && bind.captured) {
                if (id == -1) {
                    id = ctx.localVarCount++;
                }
                bind.setMVarId(this, id, mvarcount++);
            }
        }
        if (mvarcount > 0) {
            ctx.intConst(mvarcount);
            ctx.typeInsn(ANEWARRAY, "java/lang/Object");
            ctx.varInsn(ASTORE, id);
        }
    }
}

abstract class CapturingClosure extends AClosure {
    Capture captures;

    Capture captureRef(BindRef code) {
        for (Capture c = captures; c != null; c = c.next)
            if (c.binder == code.binder) {
                // evil hack... ref sharing broke fun-method
                // optimisation accounting of ref usage
                c.origin = code.origin;
                return c;
            }
        Capture c = new Capture();
        c.binder = code.binder;
        c.type = code.type;
        c.polymorph = code.polymorph;
        c.ref = code;
        c.wrapper = code.capture();
        c.origin = code.origin;
        c.next = captures;
        captures = c;
        return c;
    }

    public BindRef refProxy(BindRef code) {
        return code.flagop(DIRECT_BIND) ? code : captureRef(code);
    }

    // Called by mergeCaptures to initialize a capture.
    // It must be ok to copy capture after that.
    abstract void captureInit(Ctx fun, Capture c, int n);

    // mergeCaptures seems to drop only some uncaptured ones
    // (looks like because so is easy to do, currently
    // this seems to cause extra check only in Function.finishGen).
    int mergeCaptures(Ctx ctx, boolean cleanup) {
        int counter = 0;
        Capture prev = null;
    next_capture:
        for (Capture c = captures; c != null; c = c.next) {
            Object identity = c.identity = c.captureIdentity();
            if (cleanup && (c.uncaptured || c.ref.flagop(DIRECT_BIND))) {
                c.uncaptured = true;
                if (prev == null)
                    captures = c.next;
                else
                    prev.next = c.next;
            }
            if (c.uncaptured)
                continue;
            // remove shared captures
            for (Capture i = captures; i != c; i = i.next) {
                if (i.identity == identity) {
                    c.id = i.id; // copy old one's id
                    c.localVar = i.localVar;
                    prev.next = c.next;
                    continue next_capture;
                }
            }
            captureInit(ctx, c, counter++);
            prev = c;
        }
        return counter;
    }
}

final class Function extends CapturingClosure implements Binder {
    private static final Code NEVER = new Code() {
        void gen(Ctx ctx) {
            throw new UnsupportedOperationException();
        }
    };

    String name; // name of the generated function class
    Binder selfBind;
    Code body;
    String bindName; // function (self)binding name, if there is any

    // function body has asked self reference (and the ref is not mutable)
    private CaptureRef selfRef;
    Label restart; // used by tail-call optimizer
    Function outer; // outer function of directly-nested function
    // outer arguments to be saved in local registers (used for tail-call)
    CaptureRef argCaptures;
    // argument value for inlined function
    private Code uncaptureArg;
    // register used by argument (2 for merged inner function)
    int argVar = 1;
    // Marks function optimised as method and points to it's inner-most lambda
    Function methodImpl;
    // Function has been merged with its inner function.
    boolean merged;
    // How many times the argument has been used.
    // This counter is also used by argument nulling to determine
    // when it safe to assume that argument value is no more needed.
    private int argUsed;
    // Function is constant that can be statically shared.
    // Stores function instance in static final _ field and allows
    // direct-ref no-capture optimisations for function binding.
    private boolean shared;
    // Module has asked function to be a public (inner) class.
    // Useful for making Java code happy, if it wants to call the function.
    boolean publish;
    // Function uses local bindings from its module. Published function
    // should ensure module initialisation in this case, when called.
    private boolean moduleInit;
    // methodImpl and only one live capture - carry it directly.
    boolean capture1;
    // not in struct - capture final fields
    private boolean notInStruct;

    final BindRef arg = new BindRef() {
        void gen(Ctx ctx) {
            if (uncaptureArg != null) {
                uncaptureArg.gen(ctx);
            } else {
                int t;
                ctx.load(argVar);
                // inexact nulling...
                if (--argUsed == 0 && ctx.tainted == 0 &&
                        (t = type.deref().type) != YetiType.NUM &&
                        t != YetiType.BOOL) {
                    ctx.insn(ACONST_NULL);
                    ctx.varInsn(ASTORE, argVar);
                }
            }
        }

        boolean flagop(int fl) {
            return (fl & PURE) != 0;
        }
    };

    Function(YType type) {
        this.type = type;
        arg.binder = this;
    }

    public BindRef getRef(int line) {
        ++argUsed;
        return arg;
    }

    // uncaptures captured variables if possible
    // useful for function inlineing, don't work with self-refs
    boolean uncapture(Code arg) {
        if (selfRef != null || merged)
            return false;
        for (Capture c = captures; c != null; c = c.next)
            c.uncaptured = true;
        uncaptureArg = arg;
        return true;
    }

    void setBody(Code body) {
        this.body = body;
        if (body instanceof Function) {
            Function bodyFun = (Function) body;
            bodyFun.outer = this;
            if (argVar == 1 && !bodyFun.merged &&
                bodyFun.selfRef == null && captures == null) {
                merged = true;
                ++bodyFun.argVar;
            }
        }
    }

    /*
     * When function body refers to bindings outside of it,
     * at each closure border on the way out (to the binding),
     * a refProxy (of the ending closure) is called, possibly
     * transforming the BindRef.
     */
    public BindRef refProxy(BindRef code) {
        if (code.flagop(DIRECT_BIND)) {
            if (code.flagop(MODULE_REQUIRED)) {
                moduleInit = true;
            }
            return code;
        }
        if (selfBind == code.binder && !code.flagop(ASSIGN)) {
            if (selfRef == null) {
                selfRef = new CaptureRef() {
                    void gen(Ctx ctx) {
                        ctx.load(0);
                    }
                };
                selfRef.binder = selfBind;
                selfRef.type = code.type;
                selfRef.ref = code;

                // Right place for this should be outside of if (so it would
                // be updated on multiple selfRefs), but allowing method-fun
                // in such case slows some code down (b/c array capture).
                // Having it here means non-first self-refs arity stays zero
                // and so these will be considered to be used as fun-values.
                selfRef.origin = code.origin;

                selfRef.capturer = this;
            }
            // selfRef.origin = code.origin;
            return selfRef;
        }
        if (merged) {
            return code;
        }
        Capture c = captureRef(code);
        c.capturer = this;
        //expecting max 2 merged
        if (outer != null && outer.merged &&
            (code == outer.selfRef || code == outer.arg)) {
            /*
             * It's actually simple - because nested functions are merged,
             * the parent argument is now real argument that can be
             * directly accessed. Therefore capture proxy would only
             * fuck things up - and so that proxy is marked uncaptured.
             * Same goes for the parent-self-ref - it is now our this.
             *
             * Only problem is that tail-rec optimisation generates code,
             * that wants to store into the "captured" variables copy
             * before jumping back into the start of the function.
             * The optimiser sets argCaptures which should be copied
             * into local vars by function class generator, but this
             * coping is skipped as pointless for uncaptured ones.
             *
             * Therefore the captures localVar is simply set here to 1,
             * which happens to be parent args register (and is ignored
             * by selfRefs). Probable alternative would be to set it
             * when the copy code generation is skipped.
             */
            c.localVar = 1; // really evil hack for tail-recursion.
            c.uncaptured = true;
        }
        return c;
    }

    // called by mergeCaptures
    void captureInit(Ctx fun, Capture c, int n) {
        if (methodImpl == null) {
            // c.getId() initialises the captures id as a side effect
            fun.cw.visitField(notInStruct ? ACC_PRIVATE | ACC_FINAL : 0,
                              c.getId(fun), c.captureType(),
                              null, null).visitEnd();
        } else if (capture1) {
            assert (n == 0);
            c.localVar = 0;
            fun.load(0).captureCast(c.captureType());
            fun.varInsn(ASTORE, 0);
        } else {
            c.localVar = -2 - n;
        }
    }

    private void prepareMethod(Ctx ctx) {
        /*
         * The make-a-method trick is actually damn easy I think.
         * The captures of the innermost joined lambda must be set
         * to refer to the method arguments and closure array instead.
         * This is done by mapping our arguments and outer capture set
         * into good vars. After that the inner captures can be scanned
         * and made to point to those values.
         */
        // Map captures using binder as identity.
        Map captureMapping = null;

        /*
         * This has to be done before mergeCaptures to have all binders.
         * NOP for 1/2-arg functions - they don't have argument captures and
         * the outer captures localVar's will be set by mergeCaptures
         * (unless the 2 argument function _has_ argument captures,
         *  which can happen when the inner function is constructed by
         *  some weird kind of lambda!).
         */
        if (methodImpl != this &&
            (methodImpl != body || methodImpl.captures != null)) {
            captureMapping = new IdentityHashMap();

            // Function is binder for it's argument
            int argCounter = 0;
            for (Function f = this; f != methodImpl; f = (Function) f.body) {
                // just to hold localVar
                Capture tmp = new Capture();
                tmp.localVar = ++argCounter;
                f.argVar = argCounter; // merge fucks up the pre-last capture
                captureMapping.put(f, tmp);
            }
            methodImpl.argVar = ++argCounter;

            for (Capture c = captures; c != null; c = c.next)
                captureMapping.put(c.binder, c);
            Capture tmp = new Capture();
            tmp.localVar = 0;
            captureMapping.put(selfBind, tmp);
        }

        // Create method
        Map usedNames = ctx.usedMethodNames;
        bindName = bindName != null ? mangle(bindName) : "_";
        if (usedNames.containsKey(bindName) || bindName.startsWith("_"))
            bindName += usedNames.size();
        usedNames.put(bindName, null);
        StringBuffer sig = new StringBuffer(capture1 ? "(" : "([");
        for (int i = methodImpl.argVar + 2; --i >= 0;) {
            if (i == 0)
                sig.append(')');
            sig.append("Ljava/lang/Object;");
        }
        Ctx m = ctx.newMethod(ACC_STATIC, bindName, sig.toString());
        
        // Removes duplicate captures and calls captureInit
        // (which sets captures localVar for our case).
        int captureCount = mergeCaptures(m, false);

        // Hijack the inner functions capture mapping...
        if (captureMapping != null)
            for (Capture c = methodImpl.captures; c != null; c = c.next) {
                Object mapped = captureMapping.get(c.binder);
                if (mapped != null) {
                    c.localVar = ((Capture) mapped).localVar;
                    c.ignoreGet = c.localVar > 0;
                } else { // Capture was stealed away by direct bind?
                    Capture x = c;
                    while (x.capturer != this && x.ref instanceof Capture)
                        x = (Capture) x.ref;
                    if (x.uncaptured) {
                        c.ref = x.ref;
                        c.uncaptured = true;
                    }
                }
            }

        // Generate method body
        name = ctx.className;
        m.localVarCount = methodImpl.argVar + 1; // capturearray, args
        methodImpl.genClosureInit(m);
        m.visitLabel(methodImpl.restart = new Label());
        methodImpl.body.gen(m);
        methodImpl.restart = null;
        m.insn(ARETURN);
        m.closeMethod();

        if (!shared && !capture1) {
            ctx.intConst(captureCount);
            ctx.typeInsn(ANEWARRAY, "java/lang/Object");
        }
    }

    /*
     * For functions, this generates the function class.
     * An instance is also given, but capture fields are not initialised
     * (the captures are set later in the finishGen).
     */
    boolean prepareGen(Ctx ctx, boolean notStruct) {
        if (methodImpl != null) {
            prepareMethod(ctx);
            return false;
        }

        if (merged) { // 2 nested lambdas have been optimised into 1
            Function inner = (Function) body;
            inner.bindName = bindName;
            boolean res = inner.prepareGen(ctx, notStruct);
            name = inner.name;
            return res;
        }

        notInStruct = notStruct;
        if (bindName == null)
            bindName = "";
        name = ctx.compilation.createClassName(ctx,
                        ctx.className, mangle(bindName));

        publish &= shared;
        String funClass =
            argVar == 2 ? "yeti/lang/Fun2" : "yeti/lang/Fun";
        Ctx fun = ctx.newClass(publish ? ACC_PUBLIC | ACC_SUPER | ACC_FINAL
                                       : ACC_SUPER | ACC_FINAL,
                               name, funClass, null);

        if (publish)
            fun.markInnerClass(ctx, ACC_PUBLIC | ACC_STATIC | ACC_FINAL);
        mergeCaptures(fun, false);
        if (!notStruct)
            fun.createInit(shared ? ACC_PRIVATE : 0, funClass);

        Ctx apply = argVar == 2
            ? fun.newMethod(ACC_PUBLIC + ACC_FINAL, "apply",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
            : fun.newMethod(ACC_PUBLIC + ACC_FINAL, "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
        apply.localVarCount = argVar + 1; // this, arg
        
        if (argCaptures != null) {
            // Tail recursion needs all args to be in local registers
            // - otherwise it couldn't modify them safely before restarting
            Capture[] args = argCaptures.argCaptures();
            for (int i = 0; i < args.length; ++i) {
                Capture c = args[i];
                if (c != null && !c.uncaptured) {
                    c.gen(apply);
                    c.localVar = apply.localVarCount;
                    c.ignoreGet = true;
                    apply.varInsn(ASTORE, apply.localVarCount++);
                }
            }
        }
        if (moduleInit && publish)
            apply.methodInsn(INVOKESTATIC, ctx.className, "init", "()V");
        genClosureInit(apply);
        apply.visitLabel(restart = new Label());
        body.gen(apply);
        restart = null;
        apply.insn(ARETURN);
        apply.closeMethod();

        Ctx valueCtx =
            shared ? fun.newMethod(ACC_STATIC, "<clinit>", "()V") : ctx;
        valueCtx.typeInsn(NEW, name);
        valueCtx.insn(DUP);
        if (notStruct) { // final fields must be initialized in constructor
            StringBuffer sigb = new StringBuffer("(");
            for (Capture c = captures; c != null; c = c.next)
                if (!c.uncaptured)
                    sigb.append(c.captureType());
            String sig = sigb.append(")V").toString();
            Ctx init = fun.newMethod(shared ? ACC_PRIVATE : 0, "<init>", sig);
            init.load(0).methodInsn(INVOKESPECIAL, funClass, "<init>", "()V");
            int counter = 0;
            for (Capture c = captures; c != null; c = c.next)
                if (!c.uncaptured) {
                    c.captureGen(valueCtx);
                    init.load(0).load(++counter)
                        .fieldInsn(PUTFIELD, name, c.id, c.captureType());
                }
            init.insn(RETURN);
            init.closeMethod();
            valueCtx.visitInit(name, sig);
            valueCtx.forceType("yeti/lang/Fun");
        } else {
            valueCtx.visitInit(name, "()V");
        }
        if (shared) {
            fun.cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                              "_", "Lyeti/lang/Fun;", null, null).visitEnd();
            valueCtx.fieldInsn(PUTSTATIC, name, "_", "Lyeti/lang/Fun;");
            valueCtx.insn(RETURN);
            valueCtx.closeMethod();
        }
        return notStruct;
    }

    void finishGen(Ctx ctx) {
        if (merged) {
            ((Function) body).finishGen(ctx);
            return;
        }
        boolean meth = methodImpl != null;
        int counter = -1;
        // Capture a closure
        for (Capture c = captures; c != null; c = c.next) {
            if (c.uncaptured)
                continue;
            if (capture1) {
                c.captureGen(ctx);
                return;
            }
            ctx.insn(DUP);
            if (meth) {
                ctx.intConst(++counter);
                c.captureGen(ctx);
                ctx.insn(AASTORE);
            } else {
                c.captureGen(ctx);
                ctx.fieldInsn(PUTFIELD, name, c.id, c.captureType());
            }
        }
        ctx.forceType(meth ? "[Ljava/lang/Object;" : "yeti/lang/Fun");
    }

    boolean flagop(int fl) {
        return merged ? ((Function) body).flagop(fl) :
                (fl & (PURE | CONST)) != 0 && (shared || captures == null);
    }

    // Check whether all captures are actually static constants.
    // If so, the function value should also be optimised into shared constant.
    boolean prepareConst(Ctx ctx) {
        if (shared) // already optimised into static constant value
            return true;

        BindExpr bindExpr = null;
        // First try determine if we can reduce into method.
        if (selfBind instanceof BindExpr &&
                (bindExpr = (BindExpr) selfBind).evalId == -1 &&
                bindExpr.result != null) {
            int arityLimit = 99999999;
            for (BindExpr.Ref i = bindExpr.refs; i != null; i = i.next) {
                if (arityLimit > i.arity)
                    arityLimit = i.arity;
            }
            int arity = 0;
            Function impl = this;
            while (++arity < arityLimit && impl.body instanceof Function)
                impl = (Function) impl.body;
            /*
             * Merged ones are a bit tricky - their capture set is
             * merged into their inner one, where is also their own
             * argument. Also their inner ones arg is messed up.
             * Easier to not touch them, although it would be good for speed.
             */
            if (arity > 0 && arityLimit > 0 && (arity > 1 || !merged)) {
                //System.err.println("FF " + arity + " " + arityLimit +
                //                   " " + bindName);
                if (merged) { // steal captures and unmerge :)
                    captures = ((Function) body).captures;
                    merged = false;
                }
                methodImpl = impl.merged ? impl.outer : impl;
                bindExpr.setCaptureType("[Ljava/lang/Object;");
            }
        }

        if (merged) {
            // merged functions are hollow, their juice is in the inner function
            Function inner = (Function) body;
            inner.bindName = bindName;
            inner.publish = publish;
            if (inner.prepareConst(ctx)) {
                name = inner.name; // used by gen
                return true;
            }
            return false;
        }

        // this can be optimised into "const x", so don't touch.
        if (argUsed == 0 && argVar == 1 &&
                methodImpl == null && body.flagop(PURE))
            return false; //captures == null;

        // Uncapture the direct bindings.
        Capture prev = null;
        int liveCaptures = 0;
        for (Capture c = captures; c != null; c = c.next)
            if (c.ref.flagop(DIRECT_BIND)) {
                c.uncaptured = true;
                if (prev == null)
                    captures = c.next;
                else
                    prev.next = c.next;
            } else {
                // Why in the hell are existing uncaptured ones preserved?
                // Does some checks them (selfref, args??) after prepareConst?
                if (!c.uncaptured)
                    ++liveCaptures;
                prev = c;
            }
        
        if (methodImpl != null && liveCaptures == 1) {
            capture1 = true;
            bindExpr.setCaptureType("java/lang/Object");
        }

        // If all captures were uncaptured, then the function can
        // (and will) be optimised into shared static constant.
        if (liveCaptures == 0) {
            shared = true;
            prepareGen(ctx, false);
        }
        return liveCaptures == 0;
    }

    void gen(Ctx ctx) {
        if (shared) {
            if (methodImpl == null)
                ctx.fieldInsn(GETSTATIC, name, "_", "Lyeti/lang/Fun;");
            else
                ctx.insn(ACONST_NULL);
        } else if (!merged && argUsed == 0 && body.flagop(PURE) &&
                   uncapture(NEVER)) {
            // This lambda can be optimised into "const x", so do it.
            genClosureInit(ctx);
            ctx.typeInsn(NEW, "yeti/lang/Const");
            ctx.insn(DUP);
            body.gen(ctx);
            ctx.visitInit("yeti/lang/Const", "(Ljava/lang/Object;)V");
            ctx.forceType("yeti/lang/Fun");
        } else if (prepareConst(ctx)) {
            ctx.fieldInsn(GETSTATIC, name, "_", "Lyeti/lang/Fun;");
        } else if (!prepareGen(ctx, true))
            finishGen(ctx);
    }
}

class LoopExpr extends AClosure {
    Code cond, body;

    LoopExpr() {
        this.type = YetiType.UNIT_TYPE;
    }

    public BindRef refProxy(BindRef code) {
        return code;
    }

    void gen(Ctx ctx) {
        Label start = new Label();
        Label end = new Label();
        ctx.visitLabel(start);
        ++ctx.tainted;
        genClosureInit(ctx);
        cond.genIf(ctx, end, false);
        body.gen(ctx);
        --ctx.tainted;
        ctx.insn(POP);
        ctx.jumpInsn(GOTO, start);
        ctx.visitLabel(end);
        ctx.insn(ACONST_NULL);
    }
}

final class RootClosure extends LoopExpr {
    LoadModule[] preload;
    boolean isModule;
    ModuleType moduleType;

    void gen(Ctx ctx) {
        genClosureInit(ctx);
        for (int i = 0; i < preload.length; ++i) {
            if (preload[i] != null) {
                preload[i].gen(ctx);
                ctx.insn(POP);
            }
        }
        body.gen(ctx);
    }
}
