// ex: set sts=4 sw=4 expandtab:

/**
 * Yeti case compiler.
 * Copyright (c) 2007-2013 Madis Janson
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

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

final class CaseCompiler extends YetiType {
    CaseExpr exp;
    Scope scope;
    int depth;
    List variants = new ArrayList();
    List listVars;
    int submatch; // hack for variants

    CaseCompiler(Code val, int depth) {
        exp = new CaseExpr(val);
        exp.polymorph = true;
        this.depth = depth;
    }

    CasePattern toPattern(Node node, YType t, String doc) {
        if ((t.flags & FL_ANY_PATTERN) != 0)
            throw new CompileException(node,
                "Useless case " + node + " (any value already matched)");
        if (t.type == VAR && t.ref == null && listVars != null &&
                !listVars.contains(t))
            listVars.add(t);
        if (node instanceof Sym) {
            t.flags |= FL_ANY_PATTERN;
            String name = node.sym();
            if (name == "_" || name == "...")
                return CasePattern.ANY_PATTERN;
            BindPattern binding = new BindPattern(exp, t);
            scope = new Scope(scope, name, binding);
            t = t.deref();
            if (t.type == VARIANT)
                t.flags |= FL_ANY_PATTERN;
            return binding;
        }
        if (node.kind == "()") {
            unify(t, UNIT_TYPE, node, scope, "#0");
            return CasePattern.ANY_PATTERN;
        }
        if (node instanceof NumLit || node instanceof Str ||
                node instanceof ObjectRefOp) {
            Code c = YetiAnalyzer.analyze(node, scope, depth);
            if (!(node instanceof ObjectRefOp) || c.flagop(Code.CONST)) {
                t = t.deref();
                if (t.type == VAR) {
                    t.type = c.type.type;
                    t.param = NO_PARAM;
                    t.flags = FL_PARTIAL_PATTERN;
                } else if (t.type != c.type.type) {
                    throw new CompileException(node, scope, c.type, t,
                                        "Pattern type mismatch: #~", null);
                }
                return new ConstPattern(c);
            }
        }
        if (node.kind == "list") {
            XNode list = (XNode) node;
            YType itemt = new YType(depth);
            YType lt = new YType(MAP,
                    new YType[] { itemt, new YType(depth), LIST_TYPE });
            lt.flags |= FL_PARTIAL_PATTERN;
            if (list.expr == null || list.expr.length == 0) {
                unify(t, lt, node, scope, "#0");
                return AListPattern.EMPTY_PATTERN;
            }
            CasePattern[] items = new CasePattern[list.expr.length];
            int anyitem = FL_ANY_PATTERN;
            ++submatch;
            List oldListVars = listVars;
            listVars = new ArrayList();
            for (int i = 0; i < items.length; ++i) {
                itemt.flags &= ~FL_ANY_PATTERN;
                for (int j = listVars.size(); --j >= 0; )
                    ((YType) listVars.get(j)).flags &= ~FL_ANY_PATTERN;
                listVars.clear();
                items[i] = toPattern(list.expr[i], itemt, null);
                anyitem &= itemt.flags;
            }
            listVars = oldListVars;
            --submatch;
            itemt.flags &= anyitem;
            unify(t, lt, node, scope, "#0");
            return new ListPattern(items);
        }
        if (node instanceof BinOp) {
            BinOp pat = (BinOp) node;
            if (pat.op == "" && pat.left instanceof Sym) {
                String variant = pat.left.sym();
                if (!Character.isUpperCase(variant.charAt(0)))
                    throw new CompileException(pat.left, variant +
                        ": Variant constructor must start with upper case");
                t = t.deref();
                if (t.type != VAR && t.type != VARIANT)
                    throw new CompileException(node, "Variant " + variant +
                                 " ... is not " + t.toString(scope, null));
                t.type = VARIANT;
                if (t.requiredMembers == null) {
                    t.requiredMembers = new IdentityHashMap();
                    t.flags |= FL_ANY_CASE;
                    if (submatch == 0) // XXX hack!!!
                        variants.add(t);
                }
                YType argt = new YType(depth);
                argt.doc = doc;
                YType old = (YType) t.requiredMembers.put(variant, argt);
                if (old != null) {
                    argt = withDoc(old, doc);
                    t.requiredMembers.put(variant, argt);
                }
                CasePattern arg = toPattern(pat.right, argt, null);
                structParam(t, t.requiredMembers, new YType(depth));
                return new VariantPattern(variant, arg);
            }
            if (pat.op == "::") {
                YType itemt = new YType(depth);
                // It must must have the NO_TYPE constraint,
                // because tail has the same type as the matched
                // (this could be probably solved by giving tail
                //  and pattern separate list types, but then
                //  correct use of pattern flags must be considered)
                YType lt = new YType(MAP,
                            new YType[] { itemt, NO_TYPE, LIST_TYPE });
                int flags = t.flags; 
                unify(t, lt, node, scope, "#0");
                ++submatch;
                CasePattern hd = toPattern(pat.left, itemt, null);
                CasePattern tl = toPattern(pat.right, t, null);
                --submatch;
                lt.flags = FL_PARTIAL_PATTERN;
                t.flags = flags;
                return new ConsPattern(hd, tl);
            }
        }
        if (node.kind == "struct") {
            Node[] fields = ((XNode) node).expr;
            if (fields.length == 0)
                throw new CompileException(node, YetiAnalyzer.NONSENSE_STRUCT);
            String[] names = new String[fields.length];
            CasePattern[] patterns = new CasePattern[fields.length];
            HashMap uniq = new HashMap(fields.length);
            int allAny = FL_ANY_PATTERN;
            //++submatch;
            for (int i = 0; i < fields.length; ++i) {
                Bind field = YetiAnalyzer.getField(fields[i]);
                if (uniq.containsKey(field.name))
                    YetiAnalyzer.duplicateField(field);
                uniq.put(field.name, null);
                YType ft = new YType(depth);
                YType part = new YType(STRUCT,
                        new YType[] { new YType(depth), ft });
                IdentityHashMap tm = new IdentityHashMap();
                tm.put(field.name, ft);
                part.requiredMembers = tm;
                unify(t, part, field, scope, "#0");
                names[i] = field.name;
                ft.flags &= ~FL_ANY_PATTERN;
                patterns[i] = toPattern(field.expr, ft, null);
                allAny &= ft.flags;
            }
            //--submatch;
            Map tm = t.deref().requiredMembers;
            // The submatch hack was broken by allowing non-matcing matches
            // to be given. So, force ANY for missing structure fields - it
            // seems at least a sensible thing to do. This might be alsa
            // broken, but i can't fix it better with this design - the
            // case compilation should be rewritten to DFA generation.
            if (tm != null)
                for (Iterator j = tm.values().iterator(); j.hasNext(); ) {
                    YType ft = ((YType) j.next()).deref();
                    if (allAny == 0) // may not much, don't give any
                        ft.flags &= ~FL_ANY_PATTERN;
                    else // all are any, force it
                        ft.flags |= FL_ANY_PATTERN;
                }
            return new StructPattern(names, patterns);
        }
        throw new CompileException(node, "Bad case pattern: " + node);
    }

    void finalizeVariants() {
        for (int i = variants.size(); --i >= 0;) {
            YType t = (YType) variants.get(i);
            if (t.type == VARIANT && t.allowedMembers == null &&
                (t.flags & FL_ANY_PATTERN) == 0) {
                t.allowedMembers = t.requiredMembers;
                t.requiredMembers = null;
                t.flags &= ~FL_ANY_CASE;
            }
        }
    }

    void mergeChoice(CasePattern pat, Node node, Scope scope) {
        Code opt = YetiAnalyzer.analyze(node, scope, depth);
        exp.polymorph &= opt.polymorph;
        if (exp.type == null)
            exp.type = opt.type;
        else
            try {
                exp.type = mergeOrUnify(exp.type, opt.type);
            } catch (TypeException e) {
                throw new CompileException(node, scope, opt.type, exp.type,
                    "This choice has a #1 type, while another was a #2", e);
            }
        exp.addChoice(pat, opt);
    }

    static String checkPartialMatch(YType t) {
        if (t.seen || (t.flags & FL_ANY_PATTERN) != 0)
            return null;
        if ((t.flags & FL_PARTIAL_PATTERN) != 0)
            return t.type == MAP ? "[]" : t.toString();
        if (t.type != VAR) {
            t.seen = true;
            for (int i = t.param.length; --i >= 0;) {
                String s = checkPartialMatch(t.param[i]);
                if (s != null) {
                    t.seen = false;
                    if (t.type == MAP)
                        return "(" + s + ")::_";
                    if (t.type == VARIANT || t.type == STRUCT) {
                        Iterator j = t.requiredMembers.entrySet().iterator();
                        while (j.hasNext()) {
                            Map.Entry e = (Map.Entry) j.next();
                            if (e.getValue() == t.param[i])
                                return (t.type == STRUCT ? "." : "") +
                                            e.getKey() + " (" + s + ")";
                        }
                    }
                    return s;
                }
            }
            t.seen = false;
        } else if (t.ref != null) {
            return checkPartialMatch(t.ref);
        }
        return null;
    }

    static Code caseType(XNode ex, Scope scope, int depth) {
        Node[] choices = ex.expr;
        if (choices.length <= 1)
            throw new CompileException(ex, "case expects some option!");
        Code val = YetiAnalyzer.analyze(choices[0], scope, depth);
        CaseCompiler cc = new CaseCompiler(val, depth);
        CasePattern[] pats = new CasePattern[choices.length];
        Scope[] scopes = new Scope[choices.length];
        YType argType = new YType(depth);
        for (int i = 1; i < choices.length; ++i) {
            cc.scope = scope;
            XNode choice = (XNode) choices[i];
            pats[i] = cc.toPattern(choice.expr[0], argType, choice.doc);
            scopes[i] = cc.scope;
            cc.exp.resetParams();
        }
        String partialError = checkPartialMatch(argType);
        if (partialError != null)
            throw new CompileException(ex, "Partial match: " + partialError);
        cc.finalizeVariants();
        for (int i = 1; i < choices.length; ++i)
            if (choices[i].kind != "...")
                cc.mergeChoice(pats[i], ((XNode) choices[i]).expr[1], scopes[i]);
        unify(val.type, argType, choices[0], scope,
          "Inferred type for case argument is #2, but a #1 is given\n    (#0)");
        return cc.exp;
    }
}
