/*
 * Copyright (c) 2020 Madis Janson
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

import java.io.IOException;
import java.util.logging.Logger;
import yeti.lang.Struct;
import yeti.lang.Core;
import yeti.lang.Fun;
import yeti.lang.LList;
import yeti.lang.MList;
import yeti.lang.Tag;

/**
 * Java-friendly wrapper to evaluateYetiCode function.
 *
 * <pre>
 * Evaluater context = new Evaluator();
 * Fun add2 = (Fun) context.evaluate("(+) 2");
 * System.out.println(add.apply(new IntNum(3)));
 * </pre>
 */
public class Evaluator {
    private final static Logger LOG = Logger.getLogger(Evaluator.class.getName());
    private final Fun ctx;

    /**
     * Constructs new evaluator context using default preloaded modules.
     */
    public Evaluator() {
        this(null);
    }

    /**
     * Constructs new evaluator context.
     *
     * @param preload list of module names to preload,
     *                or null to use the default (std and io)
     */
    public Evaluator(String[] preload) {
        LList opt = new LList(new Tag(new Fun() {
                @Override
                public Object apply(Object param) {
                    Struct st = (Struct) param;
                    String parent = (String) st.get("parent");
                    if (parent == Core.UNDEF_STR) {
                        parent = null;
                    }
                    String source = getSource((String) st.get("name"), parent);
                    return source != null ? source : Core.UNDEF_STR;
                }
            }, "SourceReader"), new LList(new Tag(new Fun() {
                @Override
                public Object apply(Object param) {
                    warn((CompileException) param);
                    return null;
                }
            }, "Warn"), null));
        if (preload != null) {
            opt = new LList(new Tag(new MList(preload), "Preload"), opt);
        }
        ctx = (Fun) eval.evaluateYetiCode().apply(opt);
    }

    /**
     * Override to provide custom source reader.
     *
     * @param name of the source file to read (may include directories).
     * @param parent directory from source path entry
     *               (can be null, when not searched from the source path)
     * @return source file contents as string
     * @throws RuntimeException on error
     */
    protected String getSource(String name, String parent) {
        return null;
    }

    /**
     * Override to provide custom warning logger.
     * Default implementation uses java.util.logging.
     *
     * @param warning to log
     */
    protected void warn(CompileException warning) {
        LOG.warning(warning.getMessage());
    }

    /**
     * Evaluates given Yeti expression and returns the evaluation result.
     *
     * @param expression to evaluate
     * @return evaluation result as Java object (may be null)
     * @throws Exception from evaluated code
     * @throws CompileException on compilation errors
     */
    public Object evaluate(String expression) throws Exception {
        return evaluate(expression, null, false, null);
    }

    /**
     * Evaluates given Yeti expression and returns the evaluation result.
     * Retains top-level bindings from evaluated code in the evaluators scope.
     *
     * @param expression to evaluate
     * @return evaluation result as Java object (may be null)
     * @throws Exception from evaluated code
     * @throws CompileException on compilation errors
     */
    public Object bindingEvaluate(String expression) throws Exception {
        return evaluate(expression, null, true, null);
    }

    /**
     * Evaluates given Yeti expression and returns the evaluation result.
     *
     * @param expression to evaluate
     * @param sourceName to assign to the evaluated code (may be null)
     * @param bind whether to retain top-level bindings from evaluated code
     *             in the evaluators scope (to be visible for later invocations)
     * @param arguments to be made available as yeti.lang.io._argv (may be null)
     * @return evaluation result as Java object (may be null)
     * @throws Exception from evaluated code
     * @throws CompileException on compilation errors
     */
    public Object evaluate(String expression, String sourceName,
                           boolean bind, String[] arguments) throws Exception {
        LList opt = bind ? new LList(new Tag(null, "Bind"), null) : null;
        if (sourceName != null) {
            opt = new LList(new Tag(sourceName, "Source"), opt);
        }
        if (arguments != null) {
            opt = new LList(new Tag(new MList(arguments), "Exec"), opt);
        }
        Struct st = (Struct) ctx.apply(opt, expression);
        Tag result = (Tag) st.get("result");
        if (result.name != "Result") {
            throw (Exception) result.value;
        }
        return result.value;
    }

    /**
     * Typechecks the given Yeti expression and returns its type.
     *
     * @param expression to evaluate
     * @param sourceName to assign to the evaluated code (may be null)
     * @return string describing the expression type
     * @throws CompileException on compilation errors
     */
    public String check(String expression, String sourceName) {
        LList opt = new LList(new Tag(null, "NoExec"), sourceName == null
            ? null : new LList(new Tag(sourceName, "Source"), null));
        Struct st = (Struct) ctx.apply(opt, expression);
        Tag result = (Tag) st.get("result");
        if (result.name != "Result") {
            if (result.value instanceof RuntimeException) {
                throw (RuntimeException) result.value;
            }
            throw new RuntimeException((Throwable) result.value);
        }
        return ((Fun) showtype.showType().apply(null, ""))
                .apply(st.get("type")).toString();
    }
}
