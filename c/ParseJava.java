package yeti.lang.compiler;

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

In-class mode has basically easy life.

Modifiers:
abstract public protected package private static
final synchronized transient volatile strictfp native

modifier sets flag
class at cl1 -> expect name
id at cl1 -> type = id; mode = cl2
< at cl2  -> skip until matching >, ;{ stops
id at cl2 -> name = id; mode = cl3
= at cl3  -> mode = cl4
( at cl3  -> mode = cl5, read arglist

classes are only true nested shit for us - but we can cheat
using linked-list by having outer field in our class
structure and storing current class ptr.

XXX we can exploit MOD TYPE NAME pattern in java syntax to use same
code to parse fields, methods and method arguments.
XXX in fields , separated lists must create multiple class fields
*/

class JavaNode {
    int modifiers;
    String type; // extends for classes
    String name; // full name for classes
    String[][] classInfo; // imports, implements
    int fieldCount;
    JavaNode field;  // field list
    JavaNode method; // method list for classes
    JavaNode outer;
}

class ParseJava {
    private static final int TOPLEVEL = 0;
    private static final int PACKAGE  = 1;
    private static final int IMPORT   = 2;

    void parse(String src, int p, int e) {
        String packageName, id = null;
        // String prevId;
        ArrayList imports = new ArrayList();
        char[] s = src.toCharArray();
        int mode = TOPLEVEL, lineno = 1;
        char c;
        StringBuffer buf = new StringBuffer();
        List collector = new ArrayList();
        JavaNode current;
    next:
        for (--p;;) {
            // skip whitespace and comments
            while (++p < e && (c = s[p]) >= '\000' && c <= ' ');
            if (p + 1 < e && s[p] == '/') {
                if ((c = s[++p]) == '/') {
                    while (++p < e && (c = s[p]) != '\r' && c != '\n');
                    continue next;
                }
                if (c == '*') {
                    for (++p; ++p < e && s[p - 1] != '*' || s[p] != '/';)
                    continue next;
                }
                --p;
            }
            if (p >= e)
                break;
            int f = p;
            // get token
            while (p < e && (c = s[p]) == '_' || c >= 'a' && c <= 'z' ||
                   c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c > '~') ++p;
            //prevId = id;
            id = f == p ? null : src.substring(f, p);
            if (id == null)
                c = s[p++];
            switch (mode) {
            case TOPLEVEL:
                if (s == "package")
                    mode = PACKAGE;
                else if (s == "import")
                    mode = IMPORT;
                break;
            case PACKAGE:
            case IMPORT:
                if (id != null)
                    buf.add(id);
                else if (c == '.')
                    buf.add(c);
                else if (c == ';') {
                    if (mode == PACKAGE)
                        packageName = buf.toString();
                    else
                        imports.add(buf.toString());
                    buf.clear();
                    mode = TOPLEVEL;
                }
            }
        }
    }
}
