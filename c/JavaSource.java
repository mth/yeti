// ex: se sts=4 sw=4 expandtab:

/*
 * Partial Java source code parser.
 *
 * This file is part of Yeti language compiler.
 *
 * Copyright (c) 2010 Madis Janson
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

import yeti.renamed.asm3.Opcodes;
import java.util.*;

// Java source tree node, can be class or method or field.
class JavaNode {
    int modifier;
    String type; // extends for classes
    String name; // full name for classes
    JavaNode field;  // field/method list
    String[] argv;   // implements for classes
    JavaSource source;
}

/*
 * Represents Java source file.
 *
 * It is also partial Java parser that parses class fields and
 * method signatures, and skips method bodies.
 */
class JavaSource implements Opcodes {
    private static final String[] CHAR_SPOOL = new String[128];
    private static final Map MODS = new HashMap();
    private int p, e;
    private char[] s;
    private String lookahead;
    private String fn;
    private Map classes;
    private int line = 1;
    private final String packageName;
    private List imports = new ArrayList();
    private String[] importPackages; // list of packages to search on resolve

    private String get(int level) {
        if (lookahead != null) {
            String id = lookahead;
            lookahead = null;
            return id;
        }
        char c, s[] = this.s;
        int p = this.p - 1, e = this.e;
        for (;;) {
            // skip whitespace and comments
            while (++p < e && (c = s[p]) >= '\000' && c <= ' ')
                if (c == '\n' || c == '\r' && p + 1 < e && s[p + 1] != '\n')
                    ++line;
            if (p + 1 < e && s[p] == '/') {
                if ((c = s[++p]) == '/') {
                    while (++p < e && (c = s[p]) != '\r' && c != '\n');
                    --p;
                    continue;
                }
                if (c == '*') {
                    for (++p; ++p < e && ((c = s[p-1]) != '*' || s[p] != '/');)
                        if (c == '\n' || c == '\r' && s[p] != '\n')
                            ++line;
                    continue;
                }
                --p;
            }
            if (p >= e) {
                this.p = p;
                return null;
            }
            // string, we really don't care about it's contents
            if ((c = s[p]) == '"' || c == '\'') {
                while (++p < e && s[p] != c && s[p] != '\n')
                    if (s[p] == '\\')
                        ++p;
            }
            // skipping block
            if (level > 0 && (c != '}' || --level > 0)) {
                if (c == '{')
                    ++level;
                continue;
            }
            if (level == 0 || level == -1 && c != '(') {
                if (c != '@')
                    break;
                while (++p < e && s[p] >= '0'); // skip name
                level = -1;
            } else if (c == '(') {
                --level;
            } else if (c == ')') {
                ++level;
            }
        }
        int f = p, l;
        // get token
        while (p < e && ((c = s[p]) == '_' || c >= 'a' && c <= 'z' ||
              c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c > '~')) ++p;
        if (f == p)
            for (c = s[p]; ++p < e && c == '.' && s[p] == c;);
        this.p = p;
        // faster and ensures all operators to be interned
        if ((l = p - f) == 1 && (c = s[f]) >= '\000' && c < CHAR_SPOOL.length)
            return CHAR_SPOOL[c];
        return new String(s, f, p - f);
    }

    void expect(String expect, String id) {
        if (!expect.equals(id)) {
            CompileException e = new CompileException(line, 0, "Expected `" +
                    expect + (id == null ? "EOF" : "', not `" + id + '\''));
            e.fn = fn;
            throw e;
        }
    }

    private int modifiers() {
        String id;
        Object mod;
        int result = 0;
        while ((mod = MODS.get(id = get(0))) != null) {
            result |= ((Integer) mod).intValue();
        }
        lookahead = id;
        return result;
    }

    // mode 0 - classname (dotted identifier)
    // mode 1 - variable name (identifier with [])
    // mode 2 - parametric classname (dotted identifier, <>)
    // mode 3 - full type (dotted identifier <> [])
    private String type(int mode) {
        StringBuffer result = null;
        String id = get(0), sep = null;
        if (id == "{")
            return id;
        while (id != null && (sep = get(0)) == "." && mode != 1) {
            if (result == null)
                result = new StringBuffer(id);
            result.append('/');
            if ((id = get(0)) != null)
                result.append(id);
        }
        String type = result == null ? id : result.toString();
        if (mode != 0) {
            if (sep == "<") {
                int level = 1;
                while ((id = get(0)) != null && (id != ">" || --level > 0))
                    if (id == "<")
                        ++level;
                sep = get(0);
            }
            while (sep == "[" && mode != 2) {
                expect("]", get(0));
                type = "[".concat(type);
                sep = get(0);
            }
        }
        lookahead = sep;
        return type;
    }

    private String field(int modifiers, String type, JavaNode target) {
        JavaNode n = null;
        String id = type(1);
        if (id != null && ((modifiers & ACC_PRIVATE) == 0 || id == "(")) {
            if ("...".equals(id)) {
                type = "[".concat(type);
                if ((id = type(1)) == null)
                    return null;
            }
            while (id.startsWith("[")) {
                type = "[".concat(type);
                id = id.substring(1);
            }
            type = type.intern();
            if (target == null)
                return type;
            n = new JavaNode();
            n.modifier = modifiers;
            if (id == "(") {
                type = "void";
                n.name = "<init>";
            } else {
                n.name = id.intern();
            }
            n.type = type;
            n.field = target.field;
            target.field = n;
        }
        int method = 0;
        if (id == "(" || (id = get(0)) == "(") {
            List l = new ArrayList();
            do {
                modifiers();
                if ((id = type(3)) == ")")
                    break;
                type = field(0, id, null);
                if ((id = get(0)) != null)
                    l.add(type);
            } while (id == ",");
            expect(")", id);
            if (n != null)
                n.argv = (String[]) l.toArray(new String[l.size()]);
            ++method;
        } else if (id != "=") {
            return id;
        }
        int level = method;
        while ((id = get(0)) != null && id != ";" && (level > 0 || id != ","))
            if (id == "{") {
                get(1);
                if (method != 0)
                    return ";";
            } else if (id == "(") {
                ++level;
            } else if (id == ")") {
                --level;
            }
        return id;
    }

    private String readClass(String outer, int modifiers) {
        String id = type(3);
        if ("interface".equals(id))
            modifiers |= ACC_INTERFACE;
        else if (!"class".equals(id))
            return id;
        JavaNode cl = new JavaNode();
        cl.source = this;
        cl.modifier = modifiers;
        id = type(2);
        if (outer != null)
            id = outer + '$' + id;
        if (packageName.length() != 0)
            id = packageName + '/' + id;
        cl.name = id;
        id = get(0);
        if ("extends".equals(id)) {
            cl.type = type(2);
            id = get(0);
        }
        if ("implements".equals(id)) {
            List impl = new ArrayList();
            do {
                impl.add(type(2));
            } while ((id = get(0)) == ",");
            cl.argv = (String[]) impl.toArray(new String[impl.size()]);
        }
        expect("{", id);
        while ((id = readClass(cl.name, modifiers = modifiers())) != "}") {
            if (id == null)
                return null;
            if (id == "{")
                get(1);
            else
                while (id != "" && field(modifiers, id, cl) == ",");
        }
        classes.put(cl.name, cl);
        return "";
    }

    JavaSource(String sourceName, char[] source, Map classes) {
        fn = sourceName;
        s = source;
        e = source.length;
        this.classes = classes;
        String id = get(0);
        if ("package".equals(id)) {
            packageName = type(0);
            id = get(0);
        } else {
            packageName = "";
        }
        for (; id != null; id = get(0)) {
            if (id == ";")
                continue; // skip toplevel ;
            if (!"import".equals(id))
                break;
            if ("static".equals(id = type(0)))
                type(0); // ignore import static blaah
            else if (id != null)
                imports.add(id);
        }
        lookahead = id;
        while (readClass(null, modifiers()) != null);
        s = null;
        classes = null;
        fn = null;
    }

    /*
     * Java weird inner class implementation means that
     * import w.x.y.z; doesn't tell whether it means really
     * class x.y.z or x.y$z or x$y$z.
     * Only way to find out is to try, which class exists...
     */
    private static String resolveFull(ClassFinder finder, String t, Map to) {
        String name = null, full = t;
        char[] cs = t.toCharArray();
        for (int i = cs.length; --i >= 0;)
            if (cs[i] == '/') {
                if (name == null)
                    name = t.substring(i + 1, cs.length);
                else
                    full = new String(cs);
                if (finder.exists(full)) {
                    if (to != null) {
                        Object o = to.put(name, 'L'+ (i == 0 ? t : full) + ';');
                        if (o != null) // don't override primitives!
                            to.put(name, o);
                    }
                    return full;
                }
                cs[i] = '$';
            }
        return null;
    }

    private synchronized void prepareResolve(ClassFinder finder) {
        if (importPackages != null)
            return; // already done
        // reuse classes for full-name import map
        classes = new HashMap(JavaType.JAVA_PRIM);
        classes.remove("number");
        List pkgs = new ArrayList();
        pkgs.add(packageName.concat("/"));
        pkgs.add("java/lang/");
        for (int i = 0; i < imports.size(); ++i) {
            String s = (String) imports.get(i);
            if (s.endsWith("/*"))
                pkgs.add(s.substring(0, s.length() - 1));
            else
                resolveFull(finder, s, classes);
        }
        classes.put("void", "V");
        importPackages = (String[]) pkgs.toArray(new String[pkgs.size()]);
        imports = null;
    }

    // Java package imports and inner class naming means that it is impossible
    // to resolve Java type name without knowing what classes really exists
    // (so all source classes must be parsed before attempting any resolving).
    private YType resolve(ClassFinder finder, String type, String[] to, int n) {
        int array = 0, l = type.length();
        while (array < l && type.charAt(array) == '[')
            ++array;
        int dot = (type = type.substring(array)).indexOf('/');
        String subclass = type, cname = type;
        if (dot > 0) {
            subclass = type.replace('/', '$');
            cname = type.substring(0, dot);
        }
        String res = (String) classes.get(cname);
        if (res == null && (dot <= 0 || (res = resolveFull(finder, type, null))
                                            == null)) {
            // try to resolve from package imports
            for (int i = 0; res == null && i < importPackages.length; ++i)
                if (finder.exists(importPackages[i].concat(cname)))
                    res = importPackages[i].concat(subclass);
            if (res == null) // couldn't resolve
                res = dot > 0 ? type : importPackages[0].concat(type);
            res = 'L' + res + ';';
        }
        if (to != null) {
            to[n] = array != 0 || res.length() <= 1
                        ? type : res.substring(1, res.length() - 1);
            return null;
        }
        YType t = new YType(res);
        while (--array >= 0)
            t = new YType(YetiType.JAVA_ARRAY, new YType[] { t });
        return t;
    }

    static void loadClass(ClassFinder finder, JavaTypeReader tr, JavaNode n) {
        JavaSource src = n.source;
        src.prepareResolve(finder);
        String cname = n.name;
        String[] superType = { "java/lang/Object" };
        if (n.type != null)
            src.resolve(finder, n.type, superType, 0);
        String[] interfaces = new String[n.argv == null ? 0 : n.argv.length];
        for (int i = 0; i < interfaces.length; ++i)
            src.resolve(finder, n.argv[i], interfaces, i);
        tr.visit(0, n.modifier, cname, null, superType[0], interfaces);
        for (JavaNode i = n.field; i != null; i = i.field) {
            int access = i.modifier;
            String name = i.name;
            YType t = src.resolve(finder, i.type, null, 0);
            if (i.argv == null) { // field
                ((access & ACC_STATIC) == 0 ? tr.fields : tr.staticFields)
                    .put(name, new JavaType.Field(name, access, cname, t));
                continue;
            }
            YType[] av = new YType[i.argv.length];
            for (int j = 0; j < av.length; ++j)
                av[j] = src.resolve(finder, i.argv[j], null, 0);
            JavaType.Method m = new JavaType.Method();
            m.name = name;
            m.access = access;
            m.returnType = t;
            m.arguments = av;
            m.className = cname;
            m.sig = name + m.descr(null);
            if (name == "<init>")
                tr.constructors.add(m);
            else
                ((access & ACC_STATIC) == 0 ? tr.methods : tr.staticMethods)
                    .add(m);
        }
        if (tr.constructors.size() == 0) // default constructor
            tr.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    }

    private static void mod(String name, int value) {
        MODS.put(name, new Integer(value));
    }

    static {
        char[] x = { ' ' };
        for (short i = 0; i < CHAR_SPOOL.length; ++i) {
            x[0] = (char) i;
            CHAR_SPOOL[i] = new String(x).intern();
        }
        mod("abstract", ACC_ABSTRACT);
        mod("final", ACC_FINAL);
        mod("native", ACC_NATIVE);
        mod("private", ACC_PRIVATE);
        mod("protected", ACC_PROTECTED);
        mod("public", ACC_PUBLIC);
        mod("static", ACC_STATIC);
        mod("strictfp", ACC_STRICT);
        mod("synchronized", ACC_SYNCHRONIZED);
        mod("transient", ACC_TRANSIENT);
        mod("volatile", ACC_VOLATILE);
    }
}
