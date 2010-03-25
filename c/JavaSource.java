package yeti.lang.compiler;

import yeti.renamed.asm3.Opcodes;
import java.util.*;

/*
XXX .* imports mean we can't put classes in map, but have to try all packages
instead, when class name is referenced. Which (in turn) means, that we have to
run all the shit in 2 pass - 1 to parse all classes, and 2 to resolve the id's
when we finally know what classes exist in which packages. It isn't all bad,
though - I think that resolve pass can be done lazily on-demand, when class
description is requested.
Finally, we have the weird foopkg.someclass.subclass; import shit.
I think this also couldn't be fully resolved without trying (and having all
classes known) - you have to first guess, that it's foopkg/someclass/subclass,
and if such beast is not found, try foopkg/somclass$subclass, and after that
foopkg$someclass$subclass. If none of those is found, you have to give up and
declare it to be unknown (for our purposes, a wild guess about first variant
could be done here).
In conclusion - we shouldn't try to resolve class-names at all in the first
parse stage, and just add import lists with classes. When class is required
at later stage the names can be resolved (as all classes are known then).
*/

class JavaNode {
    int modifier;
    String type; // extends for classes
    String name; // full name for classes
    JavaNode field;  // field/method list
    String[] argv;   // implements for classes
    JavaSource source;
}

public class JavaSource implements Opcodes {
    private static final String[] CHAR_SPOOL = new String[128];
    private static final Map MODS = new HashMap();
    private int p, e;
    private char[] s;
    private String lookahead;
    private String fn;
    private Map classes;
    private int line = 1;
    private final String packageName;
    private final List imports = new ArrayList();
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
        if (id != null && (modifiers & ACC_PRIVATE) == 0) {
            while (id.startsWith("[")) {
                type = "[".concat(type);
                id = id.substring(1);
            }
            type = type.intern();
            if (target == null)
                return type;
            n = new JavaNode();
            n.modifier = modifiers;
            n.type = type;
            n.name = id != "(" ? id.intern() : "<init>";
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
                if ("...".equals(id = get(0)))
                    type = "[".concat(type);
                if (id != null)
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
        cl.name = type(2);
        if (outer != null)
            cl.name = outer + '$' + cl.name;
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
            while (id != "" && field(modifiers, id, cl) == ",");
        }
        classes.put(packageName.length() == 0 ? cl.name :
                        packageName + '/' + cl.name, cl);
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
            if ((id = type(0)) != null)
                imports.add(id);
        }
        lookahead = id;
        while (readClass(null, modifiers()) != null);
        s = null;
        classes = null;
        fn = null;
    }

    private void prepareResolve() {
        if (importPackages != null)
            return; // already done
        // reuse classes for full-name import map
        classes = new HashMap(JavaType.JAVA_PRIM);
        classes.remove("number");
        List pkgs = new ArrayList();
        pkgs.add(packageName.concat("/"));
        for (int i = 0; i < imports.size(); ++i) {
            String s = (String) imports.get(i);
            int dot = s.lastIndexOf('/');
            if (dot <= 0)
                continue;
            String name = s.substring(dot + 1);
            if ("*".equals(name)) {
                pkgs.add(s.substring(0, dot + 1));
            } else {
                Object o = classes.put(name, 'L'+ s + ';');
                if (o != null) // don't override primitives!
                    classes.put(name, o);
            }
        }
        importPackages = (String[]) pkgs.toArray(new String[pkgs.size()]);
    }

    private String resolve(ClassFinder finder, String type, boolean descr) {
        int array = 0, l = type.length();
        while (array < l && type.charAt(array) == '[')
            ++array;
        String name = type.substring(array);
        String res = (String) classes.get(name);
        if (res == null) {
            for (int i = 0; res == null && i < importPackages.length; ++i) {
                res = importPackages[i];
                if (!finder.exists(res))
                    res = null;
            }
            res = 'L' + (res != null ? res : importPackages[0]) + name + ';';
        }
        return descr ? type.substring(0, array).concat(res)
                     : array != 0 || res.length() <= 1
                        ? name : res.substring(1, res.length() - 1);
    }

    static void loadClass(ClassFinder finder, JavaTypeReader tr, JavaNode n) {
        JavaSource src = n.source;
        src.prepareResolve();
        String cname = n.name;
        String[] interfaces = new String[n.argv.length];
        for (int i = 0; i < interfaces.length; ++i)
            interfaces[i] = src.resolve(finder, n.argv[i], false);
        tr.visit(0, n.modifier, cname, null,
                 src.resolve(finder, n.type, false), interfaces);
        for (JavaNode i = n.field; i != null; i = i.field) {
            int access = i.modifier;
            String name = i.name;
            YetiType.Type t =
                new YetiType.Type(src.resolve(finder, i.type, true));
            if (i.argv == null) { // field
                ((access & ACC_STATIC) == 0 ? tr.fields : tr.staticFields)
                    .put(name, new JavaType.Field(name, access, cname, t));
                continue;
            }
            YetiType.Type[] av = new YetiType.Type[i.argv.length];
            for (int j = 0; j < av.length; ++j)
                av[j] = new YetiType.Type(src.resolve(finder, i.argv[j], true));
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
    }

    // testing
    private static String join(String[] v) {
        String res = "";
        for (int i = 0; i < v.length; ++i) {
            if (i != 0)
                res += ", ";
            res += v[i];
        }
        return res;
    }

    public static void main(String[] argv) throws Exception {
        HashMap m = new HashMap();
        for (int i = 0; i < argv.length; ++i) {
            String[] fn = { argv[i] };
            char[] s = new YetiC(null).getSource(fn);
            new JavaSource(fn[0], s, m);
        }
        for (Iterator i = m.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            JavaNode c = (JavaNode) m.get(name);
            System.out.println("class " + name + " extends " +
                    (c.type == null ? "Object" : c.type) +
                    (c.argv == null ? "" : " implements " + join(c.argv)) + " {");
            while ((c = c.field) != null) {
                System.out.println("   " + c.type + ' ' + c.name +
                    (c.argv == null ? "" : '(' + join(c.argv) + ')') + ';');
            }
            System.out.println("}\n");
        }
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
