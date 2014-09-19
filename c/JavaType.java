// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java class type reader.
 *
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

import java.util.*;
import yeti.renamed.asm3.*;
import java.io.IOException;
import java.io.InputStream;
import yeti.lang.Core;

class JavaClassNotFoundException extends Exception {
    public JavaClassNotFoundException(String what) {
        super(what);
    }
}

class JavaTypeReader implements ClassVisitor, Opcodes {
    Map vars = new HashMap();
    Map fields = new HashMap();
    Map staticFields = new HashMap();
    List methods = new ArrayList();
    List staticMethods = new ArrayList();
    List constructors = new ArrayList();
    JavaType parent;
    String className;
    String[] interfaces;
    int access;

    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        if (superName != null)
            parent = JavaType.fromDescription('L' + superName + ';');
        this.access = access;
        this.interfaces = interfaces;
/*        System.err.println("visit: ver=" + version + " | access=" + access
            + " | name=" + name + " | sig=" + signature + " super="
            + superName);*/
    }

    public void visitEnd() {
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    public void visitAttribute(Attribute attr) {
    }

    private static int parseSig(Map vars, List res, int p, char[] s) {
        int arrays = 0;
        for (int l = s.length; p < l && s[p] != '>'; ++p) {
            if (s[p] == '+' || s[p] == '*') {
                continue;
            }
            if (s[p] == '[') {
                ++arrays;
                continue;
            }
            if (s[p] == ')')
                continue;
            YType t = null;
            if (s[p] == 'L') {
                int p1 = p;
                while (p < l && s[p] != ';' && s[p] != '<')
                    ++p;
                t = new YType(new String(s, p1, p - p1).concat(";"));
                if (p < l && s[p] == '<') {
                    List param = new ArrayList();
                    p = parseSig(vars, param, p + 1, s) + 1;
                    /* XXX: workaround for broken generics support
                    //      strips free type vars from classes...
                    for (int i = param.size(); --i >= 0;) {
                        if (((YType) param.get(i)).type
                                == YetiType.VAR) {
                            param.remove(i);
                        }
                    }*/
                    t.param = (YType[])
                        param.toArray(new YType[param.size()]);
                }
            } else if (s[p] == 'T') {
                int p1 = p + 1;
                while (++p < l && s[p] != ';' && s[p] != '<');
                /*String varName = new String(s, p1, p - p1);
                t = (YType) vars.get(varName);
                if (t == null) {
                    t = new YType(1000000);
                    vars.put(varName, t);
                }*/
                t = YetiType.OBJECT_TYPE;
            } else {
                t = new YType(new String(s, p, 1));
            }
            for (; arrays > 0; --arrays) {
                t = new YType(YetiType.JAVA_ARRAY,
                            new YType[] { t });
            }
            res.add(t);
        }
        return p;
    }

    private List parseSig(int start, String sig) {
        List res = new ArrayList();
        parseSig(vars, res, start, sig.toCharArray());
        return res;
    }

    static YType[] parseSig1(int start, String sig) {
        List res = new ArrayList();
        parseSig(new HashMap(), res, start, sig.toCharArray());
        return (YType[]) res.toArray(new YType[res.size()]);
    }

    public FieldVisitor visitField(int access, String name, String desc,
                                   String signature, Object value) {
        if ((access & ACC_PRIVATE) == 0) {
/*            System.err.println("visitField: name=" + name + " | desc="
                    + desc + " | sig=" + signature + " | val=" + value
                    + " | access=" + access);*/
            List l = parseSig(0, signature == null ? desc : signature);
            JavaType.Field f =
                new JavaType.Field(name, access, className, (YType) l.get(0));
            if ((access & (ACC_FINAL | ACC_STATIC)) == (ACC_FINAL | ACC_STATIC))
                f.constValue = value;
            (((access & ACC_STATIC) == 0) ? fields : staticFields).put(name, f);
        }
        return null;
    }

    public void visitInnerClass(String name, String outerName,
                                String innerName, int access) {
/*        System.err.println("visitInnerClass: name=" +
            name + " | outer=" + outerName + " | inner=" + innerName);*/
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        if ((access & ACC_PRIVATE) == 0) {
/*            System.err.println("visitMethod: name=" + name + " | desc=" + desc
                + " | sig=" + signature + " | exc=" +
                (exceptions == null ? "()"
                    : Arrays.asList(exceptions).toString())
                + " | access=" + access);*/
            JavaType.Method m = new JavaType.Method();
//            if (signature == null) {
                signature = desc;
  //          }
            List l = parseSig(1, signature);
            m.sig = name + signature;
            m.name = name.intern();
            m.access = access;
            int argc = l.size() - 1;
            m.returnType = (YType) l.get(argc);
            /* hack for broken generic support
            if (m.returnType.type == YetiType.VAR) {
                m.returnType = YetiType.OBJECT_TYPE;
            }*/
            m.arguments = (YType[])
                l.subList(0, argc).toArray(new YType[argc]);
            m.className = className;
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
/*        System.err.println("visitOuterClass: owner=" + owner + " | name="
            + name + " | desc=" + desc);*/
    }

    public void visitSource(String source, String debug) {
//        System.err.println("visitSource: src=" + source + " | debug=" + debug);
    }
}

class JavaType implements Cloneable {
    static class Field {
        int access;
        String name;
        YType type;
        YType classType;
        String className;
        Object constValue;

        public Field(String name, int access,
                     String className, YType type) {
            this.access = access;
            this.type = type;
            this.name = name;
            this.className = className;
        }

        YType convertedType() {
            return convertValueType(type);
        }

        void check(YetiParser.Node where, String packageName) {
            classType.javaType.checkPackage(where, packageName);
            if ((access & classType.javaType.publicMask) == 0)
                checkPackage(where, packageName, className, "field", name);
        }
    }

    static class Method {
        int access;
        String name;
        YType[] arguments;
        YType returnType;
        YType classType;
        String className; // name of the class the method actually belongs to
        String sig;
        String descr;

        Method dup(Method[] arr, int n, YType classType) {
            if (classType == this.classType ||
                className.equals(classType.javaType.className())) {
                this.classType = classType;
                return this;
            }
            Method m = new Method();
            m.access = access;
            m.name = name;
            m.arguments = arguments;
            m.returnType = returnType;
            m.classType = classType;
            m.className = className;
            m.sig = sig;
            m.descr = descr;
            arr[n] = m;
            return m;
        }

        Method check(YetiParser.Node where, String packageName, int extraMask) {
            classType.javaType.checkPackage(where, packageName);
            if ((access & (classType.javaType.publicMask | extraMask)) == 0)
                checkPackage(where, packageName, className, "method", name);
            return this;
        }

        public String toString() {
            StringBuffer s =
                new StringBuffer(returnType.type == YetiType.UNIT
                                    ? "void" : returnType.toString());
            s.append(' ');
            s.append(name);
            s.append('(');
            for (int i = 0; i < arguments.length; ++i) {
                if (i != 0)
                    s.append(", ");
                s.append(arguments[i]);
            }
            s.append(")");
            return s.toString();
        }

        YType convertedReturnType() {
            return convertValueType(returnType);
        }

        String argDescr(int arg) {
            return descriptionOf(arguments[arg]);
        }

        String descr(String extra) {
            if (descr != null) {
                return descr;
            }
            StringBuffer result = new StringBuffer("(");
            for (int i = 0; i < arguments.length; ++i) {
                result.append(argDescr(i));
            }
            if (extra != null)
                result.append(extra);
            result.append(')');
            if (returnType.type == YetiType.UNIT) {
                result.append('V');
            } else {
                result.append(descriptionOf(returnType));
            }
            return descr = result.toString();
        }

        private static final Set BUILTINS = new HashSet(Arrays.asList(
            new Object[] {
                "int hashCode()",
                "void notify()",
                "void notifyAll()",
                "~java.lang.Class getClass()",
                "~java.lang.Object clone()",
                "~java.lang.String toString()",
                "void wait(long)",
                "void wait(long, int)",
                "boolean equals(~java.lang.Object)",
                "void wait()",
                "void finalize()" }));

        boolean isBuiltin() {
            return BUILTINS.contains(this.toString());
        }
    }

    private static final JavaType[] EMPTY_JTARR = {};
    static final Map JAVA_PRIM = new HashMap();

    final String description;
    private boolean resolved;
    private Map fields;
    private Map staticFields;
    private Method[] methods;
    private Method[] staticMethods;
    private Method[] constructors;
    private JavaType parent;
    private HashMap interfaces;
    private static HashMap CACHE = new HashMap();
    int publicMask = Opcodes.ACC_PUBLIC;
    int access;
    JavaClass implementation;

    static void checkPackage(YetiParser.Node where, String packageName,
                             String name, String what, String item) {
        if (!JavaType.packageOfClass(name).equals(packageName))
            throw new CompileException(where,
                "Non-public " + what + ' ' + name.replace('/', '.')
                + (item == null ? "" : "#".concat(item))
                + " cannot be accessed from different package ("
                + packageName.replace('/', '.') + ")");
    }

    void checkPackage(YetiParser.Node where, String packageName) {
        if ((access & Opcodes.ACC_PUBLIC) == 0)
            checkPackage(where, packageName, className(),
                         (access & Opcodes.ACC_INTERFACE) != 0
                            ? "interface" : "class", null);
    }

    boolean isInterface() {
        return (access & Opcodes.ACC_INTERFACE) != 0;
    }

    static String descriptionOf(YType t) {
        if (t.type == YetiType.VAR) {
            if (t.ref != null) {
                return descriptionOf(t.ref);
            }
            return "Ljava/lang/Object;";
        }
        String r = "";
        while (t.type == YetiType.JAVA_ARRAY) {
            r = r.concat("[");
            t = t.param[0];
        }
        if (t.type != YetiType.JAVA) {
            return "Ljava/lang/Object;";
        }
        return r.concat(t.javaType.description);
    }

    static YType convertValueType(YType t) {
        if (t.type != YetiType.JAVA) {
            return t;
        }
        String descr = t.javaType.description;
        if (descr == "Ljava/lang/String;" || descr == "C") {
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

    private static JavaType getClass(YType t) {
        switch (t.type) {
        case YetiType.JAVA:
            return t.javaType;
        case YetiType.NUM:
            return fromDescription("Lyeti/lang/Num;");
        case YetiType.STR:
            return fromDescription("Ljava/lang/String;");
        case YetiType.BOOL:
            return fromDescription("Ljava/lang/Boolean;");
        case YetiType.MAP:
            switch (t.param[2].type) {
            case YetiType.LIST_MARKER:
                return fromDescription(t.param[1].type == YetiType.NUM
                            ? "Lyeti/lang/MList;" : "Lyeti/lang/AList;");
            case YetiType.MAP_MARKER:
                return fromDescription("Ljava/util/Map;");
            }
            return fromDescription("Lyeti/lang/ByKey;");
        case YetiType.FUN:
            return fromDescription("Lyeti/lang/Fun;");
        case YetiType.VARIANT:
            return fromDescription("Lyeti/lang/Tag;");
        case YetiType.STRUCT:
            return fromDescription("Lyeti/lang/Struct;");
        case YetiType.UNIT:
        case YetiType.VAR:
            return fromDescription("Ljava/lang/Object;");
        }
        return null;
    }

    static void checkUnsafeCast(YetiParser.Node cast,
                                YType from, YType to) {
        if (from.type != YetiType.JAVA && to.type != YetiType.JAVA &&
                to.type != YetiType.JAVA_ARRAY) {
            throw new CompileException(cast,
                "Illegal cast from " + from + " to " + to + 
                " (neither side is java object)");
        }
        JavaType src = getClass(from);
        if (src == null)
            throw new CompileException(cast, "Illegal cast from " + from);
        JavaType dst = getClass(to);
        if (from.type == YetiType.VAR && (dst == null ||
                dst.description != "Ljava/lang/Object;")) {
            from.type = YetiType.JAVA;
            from.param = YetiType.NO_PARAM;
            from.javaType = fromDescription("Ljava/lang/Object;");
        }
        if (dst == null) {
            if (src.description == "Ljava/lang/Object;" &&
                    to.type == YetiType.JAVA_ARRAY)
                return;
            throw new CompileException(cast, "Illegal cast to " + to);
        }
        if (to.type == YetiType.JAVA &&
            (src.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }
        try {
            if (dst.isAssignable(src) < 0 &&
                (from.type != YetiType.JAVA || src.isAssignable(dst) < 0)) {
                throw new CompileException(cast,
                    "Illegal cast from " + from + " to " + to);
            }
        } catch (JavaClassNotFoundException ex) {
            throw new CompileException(cast, ex);
        }
    }

    private JavaType(String description) {
        this.description = description.intern();
    }

    static JavaType createNewClass(String className, JavaClass impl) {
        JavaType t = new JavaType('L' + className + ';');
        t.implementation = impl;
        return t;
    }

    boolean isCollection() {
        return description == "Ljava/util/List;" ||
               description == "Ljava/util/Collection;" ||
               description == "Ljava/util/Set;";
    }

    String className() {
        if (!description.startsWith("L")) {
            throw new RuntimeException("No className for " + description);
        }
        return description.substring(1, description.length() - 1);
    }

    String dottedName() {
        return className().replace('/', '.');
    }

    private static void putMethods(Map mm, Method[] methods) {
        for (int i = methods.length; --i >= 0;) {
            Method m = methods[i];
            mm.put(m.sig, m);
        }
    }

    private static void putMethods(Map mm, List methods) {
        for (int i = methods.size(); --i >= 0;) {
            Method m = (Method) methods.get(i);
            mm.put(m.sig, m);
        }
    }

    private static Method[] methodArray(Collection c) {
        return (Method[]) c.toArray(new Method[c.size()]);
    }

    private synchronized void resolve() throws JavaClassNotFoundException {
        if (resolved)
            return;
        if (!description.startsWith("L")) {
            resolved = true;
            return;
        }
        JavaTypeReader t = ((Compiler) Compiler.currentCompiler.get())
            .classPath.readClass(className());
        if (t == null) {
            throw new JavaClassNotFoundException(dottedName());
        }
        resolve(t);
    }

    void resolve(JavaTypeReader t) throws JavaClassNotFoundException {
        access = t.access;
        interfaces = new HashMap();
        if (t.interfaces != null) {
            for (int i = t.interfaces.length; --i >= 0;) {
                JavaType it = fromDescription('L' + t.interfaces[i] + ';');
                it.resolve();
                interfaces.putAll(it.interfaces);
                interfaces.put(it.description, it);
            }
        }
        fields = new HashMap();
        staticFields = new HashMap();
        HashMap mm = new HashMap();
        HashMap smm = new HashMap();
        if (t.parent != null) {
            parent = t.parent;
            parent.resolve();
        }
        for (Iterator i = interfaces.values().iterator(); i.hasNext();) {
            JavaType ii = (JavaType) i.next();
            staticFields.putAll(ii.staticFields);
            putMethods(mm, ii.methods);
        }
        if (parent != null) {
            interfaces.putAll(parent.interfaces);
            fields.putAll(parent.fields);
            staticFields.putAll(parent.staticFields);
            putMethods(mm, parent.methods);
            putMethods(smm, parent.staticMethods);
        }
        fields.putAll(t.fields);
        staticFields.putAll(t.staticFields);
        putMethods(mm, t.methods);
        putMethods(smm, t.staticMethods);
        constructors = methodArray(t.constructors);
        methods = methodArray(mm.values());
        staticMethods = methodArray(smm.values());
        resolved = true;
    }

    void checkAbstract() {
        if ((access & Opcodes.ACC_ABSTRACT) != 0)
            return;
        for (int i = methods.length; --i >= 0;) {
            if ((methods[i].access & Opcodes.ACC_ABSTRACT) != 0) {
                access |= Opcodes.ACC_ABSTRACT;
                return;
            }
        }
    }

    static final int SAM_MASK = Opcodes.ACC_ABSTRACT | Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    static final int SAM_BITS = Opcodes.ACC_ABSTRACT | Opcodes.ACC_PUBLIC;

    // single abstract method, if available
    Method getSAM() {
        //TODO: test that we fail for 1 constructor with nonempty args
        //TODO: test that we succeed for 1 constructor with empty args list
        if (constructors.length > 1)
            return null;
        if (constructors.length == 1 && !"<init>()".equals(constructors[0]))
            return null;
        Method sam = null;
        //FIXME: verify we're handling "final" classes correctly
        for (int i = 0; i < methods.length; ++i) {
            if (methods[i].isBuiltin() ||
                (methods[i].access & SAM_MASK) != SAM_BITS)
                continue;
            if (sam != null)
                return null; // must be exactly 1 such method to be SAM
            sam = methods[i];
        }
        return sam;
    }

    static final String[] NUMBER_TYPES = {
        "Ljava/lang/Byte;",
        "Ljava/lang/Short;",
        "Ljava/lang/Float;",
        "Ljava/lang/Integer;",
        "Ljava/lang/Long;",
        "Ljava/math/BigInteger;",
        "Ljava/math/BigDecimal;"
    };

    int isAssignable(JavaType from) throws JavaClassNotFoundException {
        from.resolve();
        if (this == from) {
            return 0;
        }
        if (from.description.length() == 1) {
            return -1;
        }
        if (from.interfaces.containsKey(description)) {
            return 1; // I'm an interface implemented by from
        }
        from = from.parent;
        // I'm from or one of from's parents?
        for (int i = 1; from != null; ++i) {
            if (this == from) {
                return i;
            }
            from.resolve();
            from = from.parent;
        }
        return -1;
    }

    YType[] TRY_SMART =
        { YetiType.BOOL_TYPE, YetiType.STR_TYPE, YetiType.NUM_TYPE };

    // -1 not assignable. 0 - perfect match. > 0 convertable.
    private int isAssignableJT(YType to, YType from, boolean smart)
            throws JavaClassNotFoundException, TypeException {
        int ass;
        if (from.type != YetiType.JAVA && description == "Ljava/lang/Object;") {
            return from.type == YetiType.VAR ? 1 : 10;
        }
        switch (from.type) {
        case YetiType.STR:
            return "Ljava/lang/String;" == description ? 0 :
                   "Ljava/lang/CharSequence;" == description ? 1 :
                   "Ljava/lang/StringBuffer;" == description ||
                   "Ljava/lang/StringBuilder;" == description ? 2 :
                   "C" == description ? 3 : -1;
        case YetiType.NUM:
            if (description == "D" || description == "Ljava/lang/Double;")
                return 3;
            if (description.length() == 1)
                return "BFIJS".indexOf(description.charAt(0)) < 0 ? -1 : 4;
            for (int i = NUMBER_TYPES.length; --i >= 0;)
                if (NUMBER_TYPES[i] == description)
                    return 4;
            return description == "Ljava/lang/Number;" ? 1 :
                   description == "Lyeti/lang/Num;" ? 0 : -1;
        case YetiType.BOOL:
            return description == "Z" || description == "Ljava/lang/Boolean;"
                    ? 0 : -1;
        case YetiType.FUN:
            if (description == "Lyeti/lang/Fun;")
                return 0;
            resolve();
            Method sam = getSAM();
            if (sam != null) {
                //TODO: add unit tests to verify that retval assignability checking Yeti->Java is correct
                //TODO: when testing retval assignability, also check num. of arguments too big/small
                //FIXME: add some protection to be sure we won't get into infinite recursion
                YType margs[] = sam.arguments;
                YType yarg = from;
                for (int i = 0; i < margs.length; ++i) {
                    if (yarg.type != YetiType.FUN)
                        return -1;
                    YType funarg[] = from.param;
                    if (funarg == null || funarg == YetiType.NO_PARAM ||
                        funarg.length != 2)
                        return -1;
                    if (isAssignable(funarg[0], margs[i], true) < 0) //FIXME: true here, or false?
                        return -1;
                    yarg = funarg[1];
                }
                if (isAssignable(sam.returnType, yarg, true) < 0) //FIXME: true here, or false?
                    return 9; //FIXME: what value here? would be nice to add args assignability
            }
            return -1;
        case YetiType.MAP: {
            switch (from.param[2].deref().type) {
            case YetiType.MAP_MARKER:
                return "Ljava/util/Map;" == description &&
                       (to.param.length == 0 ||
                        isAssignable(to.param[1], from.param[0], smart) == 0 &&
                        isAssignable(from.param[1], to.param[0], smart) >= 0)
                       ? 0 : -1;
            case YetiType.LIST_MARKER:
                if ("Ljava/util/List;" == description ||
                    "Ljava/util/Collection;" == description ||
                    "Ljava/util/Set;" == description ||
                    "Lyeti/lang/AList;" == description ||
                    "Lyeti/lang/AIter;" == description)
                    break;
            default:
                    return -1;
            }
            return to.param.length == 0 ||
                   (ass = isAssignable(to.param[0], from.param[0], smart)) == 0
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
                    isAssignable(to.param[0], from.param[0], smart) == 0)
                   ? 1 : -1;
        case YetiType.VAR:
            if (smart) {
                for (int i = 0; i < TRY_SMART.length; ++i) {
                    int r = isAssignableJT(to, TRY_SMART[i], false);
                    if (r >= 0) {
                        YetiType.unify(from, TRY_SMART[i]);
                        return r;
                    }
                }
                YetiType.unify(from, to);
                return 1;
            }
        }
        return description == "Ljava/lang/Object;" ? 10 : -1;
    }

    private static int isAssignable(YType to, YType from, boolean smart)
            throws JavaClassNotFoundException, TypeException {
        int ass;
//        System.err.println(" --> isAssignable(" + to + ", " + from + ")");
        to = to.deref();
        from = from.deref();
        if (to.type == YetiType.JAVA) {
            return to.javaType.isAssignableJT(to, from, smart);
        }
        if (to.type == YetiType.JAVA_ARRAY) {
            YType of = to.param[0];
            switch (from.type) {
            case YetiType.STR:
                return of.type == YetiType.JAVA &&
                       of.javaType.description == "C" ? 1 : -1;
            case YetiType.MAP: {
                return from.param[2].type == YetiType.LIST_MARKER &&
                       (ass = isAssignable(to.param[0], from.param[0], smart))
                            >= 0 ? 1 : -1;
            }
            case YetiType.JAVA_ARRAY:
                return isAssignable(to.param[0], from.param[0], smart);
            }
        }
        if (to.type == YetiType.STR &&
            from.type == YetiType.JAVA &&
            from.javaType.description == "Ljava/lang/String;")
            return 0;
        return -1;
    }

    static int isAssignable(YetiParser.Node where, YType to,
                            YType from, boolean smart) {
        from = from.deref();
        if (smart && from.type == YetiType.UNIT) {
            return 0;
        }
        try {
            return isAssignable(to, from, smart);
        } catch (JavaClassNotFoundException ex) {
            throw new CompileException(where, ex);
        } catch (TypeException ex) {
            throw new CompileException(where, ex.getMessage());
        }
    }

    static boolean isSafeCast(Scope scope, YetiParser.Node where,
                              YType to, YType from, boolean explicit) {
        to = to.deref();
        from = from.deref();
        // automatic array wrapping
        YType mapKind;
        if (from.type == YetiType.JAVA_ARRAY && to.type == YetiType.MAP &&
            ((mapKind = to.param[2].deref()).type == YetiType.LIST_MARKER ||
             mapKind.type == YetiType.VAR)) {
            YType fp = from.param[0].deref();
            YType tp = to.param[0].deref();
            try {
                if (fp.javaType != null &&
                    fp.javaType.description.length() == 1) {
                    char fromPrimitive = fp.javaType.description.charAt(0);
                    YetiType.unify(to.param[1], YetiType.NO_TYPE);
                    YetiType.unify(to.param[0],
                        fromPrimitive == 'Z' ? YetiType.BOOL_TYPE :
                        fromPrimitive == 'C' ? YetiType.STR_TYPE :
                        YetiType.NUM_TYPE);
                } else if (tp.type == YetiType.VAR) {
                    if (fp != tp)
                        YetiType.unifyToVar(tp, fp);
                } else if (isAssignable(where, tp, fp, false) < 0) {
                    return false;
                }
            } catch (TypeException ex) {
                return false;
            }
            mapKind.type = YetiType.LIST_MARKER;
            mapKind.param = YetiType.NO_PARAM;
            YType index = to.param[1].deref();
            if (index.type == YetiType.VAR) {
                index.type = tp.type == YetiType.STR
                                ? YetiType.NONE : YetiType.NUM;
                index.param = YetiType.NO_PARAM;
            }
            if (index.type == YetiType.NUM && tp.type == YetiType.STR) {
                scope.ctx.compiler.warn(new CompileException(where,
                    "Cast `as array<string>' is dangerous and deprecated." +
                    "\n    Please use either `as list<string>' or" +
                    " `as array<~String>'"));
            }
            return true;
        }
        if (to.type == YetiType.JAVA && to.javaType.description.length() == 1)
            return false;
        if (explicit)
            return isAssignable(where, to, from, true) >= 0;
        boolean smart = true;
        boolean mayExact = false;
        while (from.type == YetiType.MAP &&
               from.param[2].type == YetiType.LIST_MARKER &&
               (to.type == YetiType.MAP &&
                from.param[1].type == YetiType.NONE &&
                to.param[2].type == YetiType.LIST_MARKER &&
                to.param[1].type != YetiType.NUM ||
                to.type == YetiType.JAVA_ARRAY)) {
            if (to.type == YetiType.JAVA_ARRAY)
                mayExact = true;
            from = from.param[0].deref();
            to = to.param[0].deref();
            smart = false;
        }
        if (to.type == YetiType.STR && smart &&
                (from.type == YetiType.JAVA &&
                    from.javaType.description == "Ljava/lang/String;"/* ||
                 from.type == YetiType.JAVA_ARRAY &&
                    from.param[0].deref().javaType.description == "C"*/))
            return true;
        if (from.type != YetiType.JAVA)
            return false;
        try {
            return to.type == YetiType.JAVA &&
                    (to.javaType != from.javaType || mayExact) &&
                   (smart ? isAssignable(where, to, from, true)
                          : to.javaType.isAssignable(from.javaType)) >= 0;
        } catch (JavaClassNotFoundException ex) {
            throw new CompileException(where, ex);
        }
    }

    private Method resolveByArgs(YetiParser.Node n, Method[] ma,
                                 String name, Code[] args,
                                 YType objType) {
        name = name.intern();
        int rAss = Integer.MAX_VALUE;
        int res = -1;
        int suitable[] = new int[ma.length];
        int suitableCounter = 0;
        for (int i = ma.length; --i >= 0;) {
            Method m = ma[i];
            if (m.name == name && m.arguments.length == args.length) {
                suitable[suitableCounter++] = i;
            }
        }
        boolean single = suitableCounter == 1;
    find_match:
        while (--suitableCounter >= 0) {
            int index = suitable[suitableCounter];
            Method m = ma[index];
            int mAss = 0;
            for (int j = 0; j < args.length; ++j) {
                int ass = isAssignable(n, m.arguments[j], args[j].type, single);
                if (ass < 0) {
                    continue find_match;
                }
                if (ass != 0) {
                    mAss += ass + 1;
                }
            }
            if (m.returnType.javaType != null &&
                (m.returnType.javaType.resolve(n).access &
                    Opcodes.ACC_PUBLIC) == 0)
                mAss += 10;
            if (mAss == 0) {
                res = index;
                break;
            }
            if (mAss < rAss) {
                res = index;
                rAss = mAss;
            }
        }
        if (res != -1) {
            return ma[res].dup(ma, res, objType);
        }
        StringBuffer err = new StringBuffer("No suitable method ")
                                .append(name).append('(');
        for (int i = 0; i < args.length; ++i) {
            if (i != 0)
                err.append(", ");
            err.append(args[i].type);
        }
        err.append(") found in ").append(dottedName());
        boolean fst = true;
        for (int i = ma.length; --i >= 0;) {
            if (ma[i].name != name)
                continue;
            if (fst) {
                err.append("\nMethods named ").append(name).append(':');
                fst = false;
            }
            err.append("\n    ").append(ma[i]);
        }
        throw new CompileException(n, err.toString());
    }

    JavaType resolve(YetiParser.Node where) {
        try {
            resolve();
        } catch (JavaClassNotFoundException ex) {
            throw new CompileException(where, ex);
        }
        return this;
    }

    private static JavaType javaTypeOf(YetiParser.Node where,
                                       YType objType, String err) {
        if (objType.type != YetiType.JAVA) {
            throw new CompileException(where,
                        err + objType + ", java object expected");
        }
        return objType.javaType.resolve(where);
    }

    static Method resolveConstructor(YetiParser.Node call,
                                     YType t, Code[] args,
                                     boolean noAbstract) {
        JavaType jt = t.javaType.resolve(call);
        if ((jt.access & Opcodes.ACC_INTERFACE) != 0)
            throw new CompileException(call, "Cannot instantiate interface "
                                             + jt.dottedName());
        if (noAbstract && (jt.access & Opcodes.ACC_ABSTRACT) != 0) {
            StringBuffer msg =
                new StringBuffer("Cannot construct abstract class ");
            msg.append(jt.dottedName());
            int n = 0;
            for (int i = 0; i < jt.methods.length; ++i)
                if ((jt.methods[i].access & Opcodes.ACC_ABSTRACT) != 0) {
                    if (++n == 1) {
                        msg.append("\nAbstract methods found in ");
                        msg.append(jt.dottedName());
                        msg.append(':');
                    } else if (n > 2) {
                        msg.append("\n    ...");
                        break;
                    }
                    msg.append("\n    ");
                    msg.append(jt.methods[i]);
                }
            throw new CompileException(call, msg.toString());
        }
        return jt.resolveByArgs(call, jt.constructors, "<init>", args, t);
    }

    static Method resolveMethod(YetiParser.ObjectRefOp ref,
                                YType objType, Code[] args,
                                boolean isStatic) {
        objType = objType.deref();
        JavaType jt = javaTypeOf(ref, objType, "Cannot call method on ");
        return jt.resolveByArgs(ref, isStatic ? jt.staticMethods : jt.methods,
                                ref.name, args, objType);
    }

    static Field resolveField(YetiParser.ObjectRefOp ref,
                              YType objType,
                              boolean isStatic) {
        objType = objType.deref();
        JavaType jt = javaTypeOf(ref, objType, "Cannot access field on ");
        Map fm = isStatic ? jt.staticFields : jt.fields;
        Field field = (Field) fm.get(ref.name);
        if (field == null) {
            throw new CompileException(ref,
                        (isStatic ? "Static field " : "Field ") +
                        ref.name + " not found in " + jt.dottedName());
        }
        if (field.classType != objType) {
            if (!field.className.equals(objType.javaType.className())) {
                field = new Field(field.name, field.access,
                                  field.className, field.type);
                fm.put(field.name, field);
            }
            field.classType = objType;
        }
        return field;
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

    JavaType dup() {
        if (!resolved)
            throw new IllegalStateException("Cannot clone unresolved class");
        try {
            return (JavaType) clone();
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static YType typeOfClass(String packageName, String className) {
        if (packageName != null && packageName.length() != 0) {
            className = packageName + '/' + className;
        }
        return new YType("L" + className + ';');
    }

    public String str() {
        switch (description.charAt(0)) {
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'V': return "void";
            case 'L': return "~".concat(dottedName());
        }
        return "~".concat(description);
    }

    static String packageOfClass(String className) {
        if (className == null || className.length() == 0) {
            return "";
        }
        int p = className.lastIndexOf('/');
        return p < 0 ? "" : className.substring(0, p);
    }

/*    static YType toStructType(YType object) {
        return null;   
    }*/

    private static List parentList(JavaType t) {
        List a = new ArrayList();
        while (t != null) {
            a.add(t);
            t = t.parent;
        }
        return a;
    }

    static YType mergeTypes(YType a, YType b) {
        a = a.deref();
        b = b.deref();
        if (a.type != YetiType.JAVA || b.type != YetiType.JAVA) {
            // immutable lists can be recursively merged
            if (a.type == YetiType.MAP && b.type == YetiType.MAP &&
                a.param[1].type == YetiType.NONE &&
                a.param[2].type == YetiType.LIST_MARKER &&
                b.param[1].type == YetiType.NONE &&
                b.param[2].type == YetiType.LIST_MARKER) {
                YType t = mergeTypes(a.param[0], b.param[0]);
                if (t != null) {
                    return new YType(YetiType.MAP, new YType[] {
                                    t, YetiType.NO_TYPE, YetiType.LIST_TYPE });
                }
            }
            if (a.type == YetiType.UNIT &&
                (b.type == YetiType.JAVA &&
                    b.javaType.description.length() != 1 ||
                 b.type == YetiType.JAVA_ARRAY))
                return b;
            if (b.type == YetiType.UNIT &&
                (a.type == YetiType.JAVA &&
                    a.javaType.description.length() != 1 ||
                 a.type == YetiType.JAVA_ARRAY))
                return a;
            return null;
        }
        if (a.javaType == b.javaType) {
            return a;
        }
        List aa = parentList(a.javaType), ba = parentList(b.javaType);
        JavaType common = null;
        for (int i = aa.size(), j = ba.size();
             --i >= 0 && --j >= 0 && aa.get(i) == ba.get(j);) {
            common = (JavaType) aa.get(i);
        }
        if (common == null) {
            return null;
        }
        JavaType aj = a.javaType, bj = b.javaType;
        if (common.description == "Ljava/lang/Object;") {
            int mc = -1;
            if (bj.interfaces.containsKey(aj.description)) {
                return a;
            }
            if (aj.interfaces.containsKey(bj.description)) {
                return b;
            }
            Map m = bj.interfaces;
            Iterator i = aj.interfaces.keySet().iterator();
            while (i.hasNext()) {
                Object o;
                if ((o = m.get(i.next())) != null) {
                    JavaType jt = (JavaType) o;
                    int n = jt.methods.length;
                    if (n > mc) {
                        common = jt;
                    }
                }
            }
        }
        YType t = new YType(YetiType.JAVA, YetiType.NO_PARAM);
        t.javaType = common;
        return t;
    }

    static YType typeOfName(String name, Scope scope) {
        int arrays = 0;
        while (name.endsWith("[]")) {
            ++arrays;
            name = name.substring(0, name.length() - 2);
        }
        String descr = (String) JAVA_PRIM.get(name);
        YType t = descr != null ? new YType(descr) :
                   YetiType.resolveFullClass(arrays == 0 ? name : name.intern(),
                                             scope);
        while (--arrays >= 0)
            t = new YType(YetiType.JAVA_ARRAY, new YType[] { t });
        return t;
    }

    static {
        Map p = JAVA_PRIM;
        p.put("int",     "I");
        p.put("long",    "J");
        p.put("boolean", "Z");
        p.put("byte",    "B");
        p.put("char",    "C");
        p.put("double",  "D");
        p.put("float",   "F");
        p.put("short",   "S");
        p.put("number",  "Lyeti/lang/Num;");
    }
}
