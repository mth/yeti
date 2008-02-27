// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java class type reader.
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

import java.util.*;
import org.objectweb.asm.*;
import java.io.IOException;
import java.io.InputStream;

class JavaTypeReader implements ClassVisitor, Opcodes {
    Map fields = new HashMap();
    Map staticFields = new HashMap();
    List methods = new ArrayList();
    List staticMethods = new ArrayList();
    List constructors = new ArrayList();
    String parent;

    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        parent = superName;
        System.err.println("visit: ver=" + version + " | access=" + access
            + " | name=" + name + " | sig=" + signature + " super="
            + superName);
    }

    public void visitEnd() {
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    public void visitAttribute(Attribute attr) {
    }

    private static int parseSig(List res, int p, char[] s) {
        int arrays = 0;
        for (int l = s.length; p < l && s[p] != '>'; ++p) {
            if (s[p] == '+') {
                continue;
            }
            if (s[p] == '[') {
                ++arrays;
                continue;
            }
            if (s[p] == ')')
                continue;
            YetiType.Type t = null;
            if (s[p] == 'L') {
                int p1 = p;
                while (p < l && s[p] != ';' && s[p] != '<')
                    ++p;
                t = new YetiType.Type(new String(s, p1, p - p1).concat(";"));
                if (p < l && s[p] == '<') {
                    List param = new ArrayList();
                    p = parseSig(param, p + 1, s) + 1;
                    t.param = (YetiType.Type[])
                        param.toArray(new YetiType.Type[param.size()]);
                }
            } else {
                t = new YetiType.Type(new String(s, p, 1));
            }
            for (; arrays > 0; --arrays) {
                t = new YetiType.Type(YetiType.JAVA_ARRAY,
                            new YetiType.Type[] { t });
            }
            res.add(t);
        }
        return p;
    }

    private static List parseSig(int start, String sig) {
        List res = new ArrayList();
        parseSig(res, start, sig.toCharArray());
        return res;
    }

    public FieldVisitor visitField(int access, String name, String desc,
                                   String signature, Object value) {
        if ((access & ACC_PRIVATE) == 0) {
            System.err.println("visitField: name=" + name + " | desc="
                    + desc + " | sig=" + signature + " | val=" + value
                    + " | access=" + access);
            List l = parseSig(0, signature == null ? desc : signature);
            (((access & ACC_STATIC) == 0) ? fields : staticFields).put(name,
                new JavaType.Field(access, (YetiType.Type) l.get(0)));
        }
        return null;
    }

    public void visitInnerClass(String name, String outerName,
                                String innerName, int access) {
        System.err.println("visitInnerClass: name=" +
            name + " | outer=" + outerName + " | inner=" + innerName);
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        if ((access & ACC_PRIVATE) == 0) {
            System.err.println("visitMethod: name=" + name + " | desc=" + desc
                + " | sig=" + signature + " | exc=" +
                (exceptions == null ? "()"
                    : Arrays.asList(exceptions).toString())
                + " | access=" + access);
            List l = parseSig(1, signature == null ? desc : signature);
            JavaType.Method m = new JavaType.Method();
            m.name = name.intern();
            m.access = access;
            int argc = l.size() - 1;
            m.returnType = (YetiType.Type) l.get(argc);
            m.arguments = (YetiType.Type[])
                l.subList(0, argc).toArray(new YetiType.Type[argc]);
            if (m.name == "<init>") {
                constructors.add(m);
            } else if ((access & ACC_STATIC) == 0) {
                methods.add(m);
            } else {
                staticMethods.add(m);
            }
        }
        return null;
    }

    public void visitOuterClass(String owner, String name, String desc) {
        System.err.println("visitOuterClass: owner=" + owner + " | name="
            + name + " | desc=" + desc);
    }

    public void visitSource(String source, String debug) {
        System.err.println("visitSource: src=" + source + " | debug=" + debug);
    }
}

class JavaType {
    static class Field {
        int access;
        YetiType.Type type;

        public Field(int access, YetiType.Type type) {
            this.access = access;
            this.type = type;
        }
    }

    static class Method {
        int access;
        String name;
        YetiType.Type[] arguments;
        YetiType.Type returnType;
        YetiType.Type classType;

        public String toString() {
            StringBuffer s = new StringBuffer(returnType + " ");
            s.append(name);
            s.append('(');
            for (int i = 0; i < arguments.length; ++i) {
                if (i != 0)
                    s.append(", ");
                s.append(arguments[i]);
            }
            s.append(")\n");
            return s.toString();
        }

        YetiType.Type convertedReturnType() {
            return convertValueType(returnType);
        }

        String argDescr(int arg) {
            return descriptionOf(arguments[arg]);
        }

        String descr() {
            StringBuffer result = new StringBuffer("(");
            for (int i = 0; i < arguments.length; ++i) {
                result.append(argDescr(i));
            }
            result.append(')');
            result.append(returnType.javaType.description);
            return result.toString();
        }
    }

    final String description;
    private boolean resolved;
    private Map fields;
    private Map staticFields;
    private Method[] methods;
    private Method[] staticMethods;
    private Method[] constructors;
    private JavaType parent;
    private static HashMap CACHE = new HashMap();

    static String descriptionOf(YetiType.Type t) {
        String r = "";
        while (t.type == YetiType.JAVA_ARRAY) {
            r = r.concat("[");
            t = t.param[0];
        }
        return r.concat(t.javaType.description);
    }

    static YetiType.Type convertValueType(YetiType.Type t) {
        if (t.type != YetiType.JAVA) {
            return t;
        }
        String descr = t.javaType.description;
        if (descr == "Ljava/lang/String;") {
            return YetiType.STR_TYPE;
        }
        if (descr == "Ljava/lang/Boolean;" || descr == "Z") {
            return YetiType.BOOL_TYPE;
        }
        if (descr == "Lyeti/lang/Num;" ||
            descr.length() == 1 && "BDFIJS".indexOf(descr.charAt(0)) >= 0) {
            return YetiType.NUM_TYPE;
        }
        if (descr == "V") {
            return YetiType.UNIT_TYPE;
        }
        return t;
    }

    private JavaType(String description) {
        this.description = description.intern();
    }

    boolean isCollection() {
        return description == "Ljava/util/List;" ||
               description == "Ljava/util/Collection;" ||
               description == "Ljava/util/Set;";
    }
/*
    static String simpleJavaSig(YetiType.Type t) {
        switch (t.type) {
        case YetiType.JAVA:
            if (t.javaType.isCollection()) {
                if (t.javaType.description == "Ljava/lang/Set;") {
                    return "s" + (t.param.length == 0
                            ? "" : simpleJavaSig(t.param[0]));
                }
                return "l" + (t.param.length == 0
                        ? "" : simpleJavaSig(t.param[0]));
            }
            for (int i = NUMBER_TYPES.length; --i >= 0;) {
                if (t.javaType.description == NUMBER_TYPES[i]) {
                    return NUMBER_X[i];
                }
            }
            return t.javaType.description;
        case YetiType.JAVA_ARRAY:
            return "[" + simpleJavaSig(t.param[0]);
        }
        throw new IllegalArgumentException("simpleJavaSig(" + t.type + ")");
    }
*/
    String className() {
        if (!description.startsWith("L")) {
            throw new RuntimeException("No className for " + description);
        }
        return description.substring(1, description.length() - 1);
    }

    String dottedName() {
        return className().replace('/', '.');
    }

    private synchronized void resolve() {
        if (resolved)
            return;
        if (!description.startsWith("L")) {
            resolved = true;
            return;
        }
        InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(className() + ".class");
        JavaTypeReader t = new JavaTypeReader();
        try {
            new ClassReader(in).accept(t, null,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (IOException ex) {
            throw new RuntimeException("Not found: " + dottedName(), ex);
        }
//        System.err.println(t.constructors);
        fields = t.fields;
        staticFields = t.staticFields;
        constructors = (Method[]) t.constructors.toArray(
                            new Method[t.constructors.size()]);
        methods = (Method[]) t.methods.toArray(new Method[t.methods.size()]);
        staticMethods = (Method[]) t.staticMethods.toArray(
                            new Method[t.staticMethods.size()]);
        parent = t.parent == null
                    ? null : fromDescription('L' + t.parent + ';');
        resolved = true;
    }

    static final String[] NUMBER_TYPES = {
        "Ljava/lang/Byte;",
        "Ljava/lang/Short;",
        "Ljava/lang/Float;",
        "Ljava/lang/Double;",
        "Ljava/lang/Integer;",
        "Ljava/lang/Long;",
        "Ljava/math/BigInteger;",
        "Ljava/math/BigDecimal;"
    };

    static final String[] NUMBER_X = { "B", "S", "F", "D", "I", "J", "i", "d" };

    int isAssignable(JavaType from) {
        // I'm from or one of from's parents?
        for (int i = 0; from != null; ++i) {
            if (this == from) {
                return i;
            }
            from.resolve();
            from = from.parent;
        }
        return -1;
    }

    // -1 not assignable. 0 - perfect match. > 0 convertable.
    int isAssignableJT(YetiType.Type to, YetiType.Type from) {
        int ass;
        if (from.type != YetiType.JAVA && description == "Ljava/lang/Object;") {
            return from.type == YetiType.VAR ? 1 : 10;
        }
        switch (from.type) {
        case YetiType.STR:
            return "Ljava/lang/String;" == description ? 0 :
                   "Ljava/lang/StringBuffer;" == description ||
                   "Ljava/lang/StringBuilder;" == description ? 2 :
                   "C" == description ? 3 : -1;
        case YetiType.NUM:
            if (description.length() == 1)
                return "BDFIJS".indexOf(description.charAt(0)) < 0 ? -1 : 2;
            for (int i = NUMBER_TYPES.length; --i >= 0;)
                if (NUMBER_TYPES[i] == description)
                    return 2;
            return description == "Ljava/lang/Number;" ? 1 :
                   description == "Lyeti/lang/Num;" ? 0 : -1;
        case YetiType.BOOL:
            return description == "Z" || description == "Ljava/lang/Boolean;"
                    ? 0 : -1;
        case YetiType.FUN:
            return description == "Lyeti/lang/Fun;" ? 0 : -1;
        case YetiType.MAP: {
            switch (from.param[2].deref().type) {
            case YetiType.MAP_MARKER:
                return "Ljava/util/Map;" == description &&
                       (to.param.length == 0 ||
                        isAssignable(to.param[1], from.param[0]) == 0 &&
                        isAssignable(from.param[1], to.param[0]) >= 0)
                       ? 0 : -1;
            case YetiType.LIST_MARKER:
                if ("Ljava/util/List;" == description ||
                    "Ljava/util/Collection;" == description ||
                    "Ljava/util/Set;" == description)
                    break;
            default:
                    return -1;
            }
            return to.param.length == 0 ||
                   (ass = isAssignable(to.param[0], from.param[0])) == 0
                   || ass > 0 && from.param[1].type == YetiType.NONE ? 1 : -1;
        }
        case YetiType.STRUCT:
            return description == "Lyeti/lang/Struct;" ? 0 : -1;
        case YetiType.VARIANT:
            return description == "Lyeti/lang/Tag;" ? 0 : -1;
        case YetiType.JAVA:
            return isAssignable(from.javaType);
        case YetiType.JAVA_ARRAY:
            return ("Ljava/util/Collection;" == description ||
                    "Ljava/util/List;" == description) &&
                   (to.param.length == 0 ||
                    isAssignable(to.param[0], from.param[0]) == 0)
                   ? 1 : -1;
        }
        return description == "Ljava/lang/Object;" ? 10 : -1;
    }

    static int isAssignable(YetiType.Type to, YetiType.Type from) {
        int ass;
        System.err.println(" --> isAssignable(" + to + ", " + from + ")");
        to = to.deref();
        from = from.deref();
        if (to.type == YetiType.JAVA) {
            return to.javaType.isAssignableJT(to, from);
        }
        if (to.type == YetiType.JAVA_ARRAY) {
            YetiType.Type of = to.param[0];
            switch (from.type) {
            case YetiType.STR:
                return of.type == YetiType.JAVA &&
                       of.javaType.description == "C" ? 1 : -1;
            case YetiType.MAP:
                return from.param[2].type == YetiType.LIST_MARKER &&
                       ((ass = isAssignable(to.param[0], from.param[0])) == 0
                        || ass > 0 && from.param[1].type == YetiType.NONE)
                       ? 1 : -1;
            case YetiType.JAVA_ARRAY:
                return isAssignable(to.param[0], from.param[0]);
            }
        }
        return -1;
    }

    private Method resolveByArgs(YetiParser.Node n, Method[] ma,
                                 String name, YetiCode.Code[] args) {
        name = name.intern();
        int rAss = Integer.MAX_VALUE;
        Method res = null;
    find_match:
        for (int i = ma.length; --i >= 0;) {
            Method m = ma[i];
            if (m.name != name || m.arguments.length != args.length) {
                continue;
            }
            int mAss = 0;
            for (int j = 0; j < args.length; ++j) {
                int ass = isAssignable(m.arguments[j], args[j].type);
//                System.err.println("isAssignable(" + m.arguments[j] +
//                    ", " + args[j].type + ") = " + ass);
                if (ass < 0) {
                    continue find_match;
                }
                if (ass != 0) {
                    mAss += ass + 1;
                }
            }
            if (mAss == 0)
                return m;
            if (mAss < rAss) {
                res = m;
                rAss = mAss;
            }
        }
        if (res != null) {
            return res;
        }
        throw new CompileException(n, "No suitable method " + name
                                   + " found in " + dottedName());
    }

    private static JavaType javaTypeOf(YetiParser.Node where,
                                       YetiType.Type objType, String err) {
        if (objType.type != YetiType.JAVA) {
            throw new CompileException(where,
                        err + objType + ", java object expected");
        }
        JavaType jt = objType.javaType;
        jt.resolve();
        return jt;
    }

    static Method resolveConstructor(YetiParser.NewOp call,
                                     YetiType.Type t,
                                     YetiCode.Code[] args) {
        JavaType jt = t.javaType;
        jt.resolve();
        Method m = jt.resolveByArgs(call, jt.constructors, "<init>", args);
        m.classType = t;
        return m;
    }

    static Method resolveMethod(YetiParser.ObjectRefOp ref,
                                YetiType.Type objType,
                                YetiCode.Code[] args,
                                boolean isStatic) {
        objType = objType.deref();
        JavaType jt = javaTypeOf(ref, objType, "Cannot call method on");
        Method m = jt.resolveByArgs(ref,
                        isStatic ? jt.staticMethods : jt.methods,
                        ref.name, args);
        m.classType = objType;
        return m;
    }

    static YetiType.Type resolveField(YetiParser.ObjectRefOp ref,
                                      YetiType.Type objType,
                                      boolean isStatic) {
        objType = objType.deref();
        JavaType jt = javaTypeOf(ref, objType, "Cannot access field on ");
        YetiType.Type t =
            (YetiType.Type) (isStatic ? jt.staticFields : jt.methods).get(ref.name);
        if (t == null) {
            throw new CompileException(ref,
                        (isStatic ? "Static field" : "Field") +
                        " not found in " + jt.dottedName());
        }
        return t;
    }

    static JavaType fromDescription(String sig) {
        synchronized (CACHE) {
            JavaType t = (JavaType) CACHE.get(sig);
            if (t == null) {
                t = new JavaType(sig);
                CACHE.put(sig, t);
            }
            return t;
        }
    }

    public String str(Map vars, Map refs, YetiType.Type[] param) {
        switch (description.charAt(0)) {
            case 'Z': return "!boolean";
            case 'B': return "!byte";
            case 'C': return "!char";
            case 'D': return "!double";
            case 'F': return "!float";
            case 'I': return "!int";
            case 'J': return "!long";
            case 'S': return "!short";
            case 'V': return "void";
            case 'L': break;
            default : return "!" + description;
        }
        StringBuffer s = new StringBuffer("!");
        s.append(description.substring(1, description.length() - 1)
                            .replace('/', '.'));
        if (param != null && param.length > 0) {
            s.append('<');
            for (int i = 0; i < param.length; ++i) {
                if (i != 0) {
                    s.append(", ");
                }
                String ps = param[i].str(vars, refs);
                s.append(ps.charAt(0) == '!' ? ps.substring(1) : ps);
            }
            s.append('>');
        }
        return s.toString();
    }

    public static void main(String[] arg) throws Exception{
        InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(arg[0] + ".class");
        JavaTypeReader t = new JavaTypeReader();
        new ClassReader(in).accept(t, null,
                ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        System.err.println(t.fields);
        System.err.println(t.methods);
    }
}

