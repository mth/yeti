// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
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
import yeti.lang.Tag;
import java.io.IOException;
import java.io.InputStream;

/*
 * Encoding:
 *
 * 00 - format identifier
 * Follows type description
 * 00 XX XX - free type variable XXXX
 * XX, where XX is 01..08 -
 *      primitives (same as YType.type UNIT - MAP_MARKER)
 * 09 x.. y.. - Function x -> y
 * 0A e.. i.. t.. - MAP<e,i,t>
 * 0B <partialMembers...> FF <finalMembers...> FF - Struct
 * 0C <partialMembers...> FF <finalMembers...> FF - Variant
 * 0C F9 ... - Variant with FL_ANY_CASE flag
 * 0D XX XX <param...> FF - java type
 * 0E e.. FF - java array e[]
 * FA XX XX <parameters...> FF - opaque type instance (X is "module:name")
 * FB XX XX - non-free type variable XXXX
 * FC ... - mutable field type
 * FD ... - the following type variable is ORDERED
 * FE XX XX - reference to non-primitive type
 * Follows list of type definitions
 *  <typeDef name
 *   typeDef array of type descriptions (FF)
 *   ...>
 *  FF
 * Follows utf8 encoded direct field mapping.
 *  XX XX XX XX - length
 *  'F' fieldName 00 function-class 00
 *  'P' fieldName 00 - property (field mapping as null)
 */
class TypeAttr extends Attribute {
    static final byte END = -1;
    static final byte REF = -2;
    static final byte ORDERED = -3;
    static final byte MUTABLE = -4;
    static final byte TAINTED = -5;
    static final byte OPAQUE  = -6;
    static final byte ANYCASE = -7;

    final ModuleType moduleType;
    private ByteVector encoded;
    final Compiler compiler;

    TypeAttr(ModuleType mt, Compiler ctx) {
        super("YetiModuleType");
        moduleType = mt;
        compiler = ctx;
    }

    private static final class EncodeType {
        ClassWriter cw;
        ByteVector buf = new ByteVector();
        Map refs = new HashMap();
        Map vars = new HashMap();
        Map opaque = new HashMap();

        void writeMap(Map m) {
            if (m != null) {
                for (Iterator i = m.entrySet().iterator(); i.hasNext();) {
                    Map.Entry e = (Map.Entry) i.next();
                    YType t = (YType) e.getValue();
                    if (t.field == YetiType.FIELD_MUTABLE)
                        buf.putByte(MUTABLE);
                    write(t);
                    int name = cw.newUTF8((String) e.getKey());
                    buf.putShort(name);
                }
            }
            buf.putByte(END);
        }

        void writeArray(YType[] param) {
            for (int i = 0; i < param.length; ++i) {
                write(param[i]);
            }
            buf.putByte(END);
        }

        void write(YType type) {
            type = type.deref();
            if (type.type == YetiType.VAR) {
                Integer id = (Integer) vars.get(type);
                if (id == null) {
                    vars.put(type, id = new Integer(vars.size()));
                    if (id.intValue() > 0x7fff)
                        throw new RuntimeException("Too many type parameters");
                    if ((type.flags & YetiType.FL_ORDERED_REQUIRED) != 0)
                        buf.putByte(ORDERED);
                }
                buf.putByte((type.flags & YetiType.FL_TAINTED_VAR) == 0
                                ? YetiType.VAR : TAINTED);
                buf.putShort(id.intValue());
                return;
            }
            if (type.type < YetiType.PRIMITIVES.length &&
                YetiType.PRIMITIVES[type.type] != null) {
                // primitives
                buf.putByte(type.type);
                return;
            }
            Integer id = (Integer) refs.get(type);
            if (id != null) {
                if (id.intValue() > 0x7fff) {
                    throw new RuntimeException("Too many type parts");
                }
                buf.putByte(REF);
                buf.putShort(id.intValue());
                return;
            }
            refs.put(type, new Integer(refs.size()));
            if (type.type >= YetiType.OPAQUE_TYPES) {
                Object idstr = opaque.get(new Integer(type.type));
                if (idstr == null)
                    idstr = type.partialMembers.keySet().toArray()[0];
                buf.putByte(OPAQUE);
                buf.putShort(cw.newUTF8(idstr.toString()));
                writeArray(type.param);
                return;
            }
            buf.putByte(type.type);
            if (type.type == YetiType.FUN) {
                write(type.param[0]);
                write(type.param[1]);
            } else if (type.type == YetiType.MAP) {
                writeArray(type.param);
            } else if (type.type == YetiType.STRUCT ||
                       type.type == YetiType.VARIANT) {
                if ((type.finalMembers == null || type.finalMembers.isEmpty())
                    && (type.partialMembers == null ||
                        type.partialMembers.isEmpty()))
                    throw new CompileException(0, 0,
                                type.type == YetiType.STRUCT
                                ? "Internal error: empty struct"
                                : "Internal error: empty variant");
                if ((type.flags & YetiType.FL_ANY_CASE) != 0)
                    buf.putByte(ANYCASE);
                writeMap(type.finalMembers);
                writeMap(type.partialMembers);
            } else if (type.type == YetiType.JAVA) {
                buf.putShort(cw.newUTF8(type.javaType.description));
                writeArray(type.param);
            } else if (type.type == YetiType.JAVA_ARRAY) {
                write(type.param[0]);
            } else {
                throw new RuntimeException("Unknown type: " + type.type);
            }
        }

        void writeTypeDefs(Map typeDefs) {
            for (Iterator i = typeDefs.entrySet().iterator(); i.hasNext();) {
                Map.Entry e = (Map.Entry) i.next();
                buf.putShort(cw.newUTF8((String) e.getKey()));
                writeArray((YType[]) e.getValue());
            }
            buf.putByte(END);
        }
    }

    private static final class DecodeType {
        private static final int VAR_DEPTH = 1;
        final ClassReader cr;
        final byte[] in;
        final char[] buf;
        int p;
        final int end;
        final Map vars = new HashMap();
        final List refs = new ArrayList();
        final Map opaqueTypes;

        DecodeType(ClassReader cr, int off, int len, char[] buf,
                   Map opaqueTypes) {
            this.cr = cr;
            in = cr.b;
            p = off;
            end = p + len;
            this.buf = buf;
            this.opaqueTypes = opaqueTypes;
        }

        Map readMap() {
            if (in[p] == END) {
                ++p;
                return null;
            }
            HashMap res = new HashMap();
            while (in[p] != END) {
                YType t = read();
                res.put(cr.readUTF8(p, buf), t);
                p += 2;
            }
            ++p;
            return res;
        }

        YType[] readArray() {
            List param = new ArrayList();
            while (in[p] != END) {
                param.add(read());
            }
            ++p;
            return (YType[]) param.toArray(new YType[param.size()]);
        }

        YType read() {
            YType t;
            int tv;

            if (p >= end) {
                throw new RuntimeException("Invalid type description");
            }
            switch (tv = in[p++]) {
            case YetiType.VAR:
            case TAINTED: {
                Integer var = new Integer(cr.readUnsignedShort(p));
                p += 2;
                if ((t = (YType) vars.get(var)) == null)
                    vars.put(var, t = new YType(VAR_DEPTH));
                if (tv == TAINTED)
                    t.flags |= YetiType.FL_TAINTED_VAR;
                return t;
            }
            case ORDERED:
                t = read();
                t.flags |= YetiType.FL_ORDERED_REQUIRED;
                return t;
            case REF: {
                int v = cr.readUnsignedShort(p);
                p += 2;
                if (refs.size() <= v) {
                    throw new RuntimeException("Illegal type reference");
                }
                return (YType) refs.get(v);
            }
            case MUTABLE:
                return YetiType.fieldRef(1, read(), YetiType.FIELD_MUTABLE);
            }
            if (tv < YetiType.PRIMITIVES.length && tv > 0)
                return YetiType.PRIMITIVES[tv];
            t = new YType(tv, null);
            refs.add(t);
            if (t.type == YetiType.FUN) {
                t.param = new YType[2];
                t.param[0] = read();
                t.param[1] = read();
            } else if (tv == YetiType.MAP) {
                t.param = readArray();
            } else if (tv == YetiType.STRUCT || tv == YetiType.VARIANT) {
                if (in[p] == ANYCASE) {
                    t.flags |= YetiType.FL_ANY_CASE;
                    ++p;
                }
                t.finalMembers = readMap();
                t.partialMembers = readMap();
                Map param;
                if (t.finalMembers == null) {
                    if ((param = t.partialMembers) == null)
                        param = new HashMap();
                } else if (t.partialMembers == null) {
                    param = t.finalMembers;
                } else {
                    param = new HashMap(t.finalMembers);
                    param.putAll(t.partialMembers);
                }
                t.param = new YType[param.size() + 1];
                t.param[0] = new YType(VAR_DEPTH);
                Iterator i = param.values().iterator();
                for (int n = 1; i.hasNext(); ++n)
                    t.param[n] = (YType) i.next();
            } else if (tv == YetiType.JAVA) {
                t.javaType = JavaType.fromDescription(cr.readUTF8(p, buf));
                p += 2;
                t.param = readArray();
            } else if (tv == YetiType.JAVA_ARRAY) {
                t.param = new YType[] { read() };
            } else if (tv == OPAQUE) {
                String idstr = cr.readUTF8(p, buf);
                p += 2;
                synchronized (opaqueTypes) {
                    YType old = (YType) opaqueTypes.get(idstr);
                    if (old != null) {
                        t.type = old.type;
                    } else {
                        t.type = opaqueTypes.size() + YetiType.OPAQUE_TYPES;
                        opaqueTypes.put(idstr, t);
                    }
                }
                t.partialMembers =
                    Collections.singletonMap(idstr, YetiType.NO_TYPE);
                t.param = readArray();
            } else {
                throw new RuntimeException("Unknown type id: " + tv);
            }
            return t;
        }

        Map readTypeDefs() {
            Map result = new HashMap();
            while (in[p] != END) {
                String name = cr.readUTF8(p, buf);
                p += 2;
                result.put(name.intern(), readArray());
            }
            ++p;
            return result;
        }
    }

    protected Attribute read(ClassReader cr, int off, int len, char[] buf,
                             int codeOff, Label[] labels) {
        int hdr = 3;
        switch (cr.b[off]) {
        case 0: hdr = 1; // version 0 has only version in header
        case 1: break;
        default:
            throw new RuntimeException("Unknown type encoding: " + cr.b[off]);
        }
        DecodeType decoder =
            new DecodeType(cr, off + hdr, len - hdr, buf, compiler.opaqueTypes);
        YType t = decoder.read();
        Map typeDefs = decoder.readTypeDefs();
        return new TypeAttr(new ModuleType(t, typeDefs, hdr != 1, -1),
                            compiler);
    }

    protected ByteVector write(ClassWriter cw, byte[] code, int len,
                               int maxStack, int maxLocals) {
        if (encoded != null) {
            return encoded;
        }
        EncodeType enc = new EncodeType();
        Iterator i = moduleType.typeDefs.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            YType[] def = (YType[]) e.getValue();
            YType t = def[def.length - 1];
            if (t.type >= YetiType.OPAQUE_TYPES && t.partialMembers == null)
                enc.opaque.put(new Integer(t.type),
                               moduleType.name + ':' + e.getKey());
        }
        enc.cw = cw;
        enc.buf.putByte(1); // encoding version
        enc.buf.putShort(0);
        enc.write(moduleType.type);
        enc.writeTypeDefs(moduleType.typeDefs);
        return encoded = enc.buf;
    }
}

class ModuleType extends YetiParser.Node {
    final YType type;
    final Map typeDefs;
    final boolean directFields;
    Scope typeScope;
    String topDoc;
    String name;
    boolean deprecated;
    long lastModified;
    private YType[] free;

    ModuleType(YType type, Map typeDefs, boolean directFields, int depth) {
        this.typeDefs = typeDefs;
        this.directFields = directFields;
        this.type = copy(depth, type);
    }

    YType copy(int depth, YType t) {
        if (t == null)
            t = type;
        if (depth == -1)
            return t;
        if (free == null) {
            List freeVars = new ArrayList();
            YetiType.getFreeVar(freeVars, freeVars, t,
                                YetiType.RESTRICT_POLY, -1);
            free = (YType[]) freeVars.toArray(new YType[freeVars.size()]);
        }
        return YetiType.copyType(t, YetiType.createFreeVars(free, depth),
                                 new HashMap());
    }

    Tag yetiType() {
        return TypeDescr.yetiType(type, typeScope != null
                ? TypePattern.toPattern(typeScope)
                : TypePattern.toPattern(typeDefs), null);
    }
}

class YetiTypeVisitor implements ClassVisitor {
    TypeAttr typeAttr;
    private boolean deprecated;

    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        deprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
    }

    public void visitEnd() {
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    public void visitAttribute(Attribute attr) {
        if (attr.type == "YetiModuleType") {
            if (typeAttr != null) {
                throw new RuntimeException(
                    "Multiple YetiModuleType attributes are forbidden");
            }
            typeAttr = (TypeAttr) attr;
        }
    }

    public FieldVisitor visitField(int access, String name, String desc,
                                   String signature, Object value) {
        return null;
    }

    public void visitInnerClass(String name, String outerName,
                                String innerName, int access) {
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        return null;
    }

    public void visitOuterClass(String owner, String name, String desc) {
    }

    public void visitSource(String source, String debug) {
    }

    static ModuleType readType(Compiler compiler, InputStream in)
            throws IOException {
        YetiTypeVisitor visitor = new YetiTypeVisitor();
        ClassReader reader = new ClassReader(in);
        reader.accept(visitor, new Attribute[] { new TypeAttr(null, compiler) },
                      ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        in.close();
        if (visitor.typeAttr == null)
            return null;
        ModuleType mt = visitor.typeAttr.moduleType;
        if (mt != null)
            mt.deprecated = visitor.deprecated;
        mt.name = reader.getClassName();
        return mt;
    }

    static ModuleType getType(Compiler ctx, YetiParser.Node node,
                              String name, boolean byPath) {
        final boolean bySourcePath = false;
        final String cname = name.toLowerCase();
        ModuleType t = (ModuleType) ctx.types.get(cname);
        if (t != null)
            return t;
        InputStream in = null;
        int old_flags = ctx.flags;
        long[] lastModified = new long[1];
        if (!byPath) {
            in = ctx.classPath.findClass(cname + ".class", lastModified);
            ctx.flags |= Compiler.CF_RESOLVE_MODULE;
        } else {
            ctx.flags |= Compiler.CF_FORCE_COMPILE;
        }
        try {
            if (in == null) {
                ctx.flags |= Compiler.CF_EXPECT_MODULE;
                ctx.flags &= ~Compiler.CF_EVAL_BIND; // clear the eval flags
                t = (ModuleType) ctx.types.get(ctx.compile(name, null).name);
                if (t == null)
                    throw new CompileException(node,
                                "Could not compile `" + name + "' to a module");
                if (!byPath && !cname.equals(t.name))
                    throw new CompileException(node, "Found " +
                                t.name.replace('/', '.') +
                                " instead of " + name.replace('/', '.'));
            } else {
                t = readType(ctx, in);
                if (t == null)
                    throw new CompileException(node,
                                "`" + name + "' is not a yeti module");
                t.name = cname;
                t.lastModified = lastModified[0];
                ctx.types.put(cname, t);
            }
            if (!t.directFields)
                ctx.warn(new CompileException(node, "The `" +
                    t.name.replace('/', '.') + "' module is compiled " +
                    "with pre-0.9.8 version\n    of Yeti compiler and " +
                    "might not work with newer standard library."));
            return t;
        } catch (CompileException ex) {
            if (ex.line == 0) {
                if (node != null) {
                    ex.line = node.line;
                    ex.col = node.col;
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CompileException(node, ex.getMessage());
        } finally {
            ctx.flags = old_flags;
        }
    }
}
