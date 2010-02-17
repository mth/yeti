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

import java.util.*;
import org.objectweb.asm.*;
import java.io.IOException;
import java.io.InputStream;

/*
 * Encoding:
 *
 * 00 - format identifier
 * Follows type description
 * 00 XX XX - free type variable XXXX
 * XX, where XX is 00..08 -
 *      primitives (same as YetiType.Type.type UNIT - MAP_MARKER)
 * 09 x.. y.. - Function x -> y
 * 0A e.. i.. t.. - MAP<e,i,t>
 * 0B <partialMembers...> FF <finalMembers...> FF - Struct
 * 0C <partialMembers...> FF <finalMembers...> FF - Variant
 * 0D XX XX <param...> FF - java type
 * 0E e.. FF - java array e[]
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
class YetiTypeAttr extends Attribute {
    static final byte END = -1;
    static final byte REF = -2;
    static final byte ORDERED = -3;
    static final byte MUTABLE = -4;

    YetiType.Type type;
    Map typeDefs;
    Map directFields;
    private ByteVector encoded;

    YetiTypeAttr(YetiType.Type type, Map typeDefs, Map directFields) {
        super("YetiModuleType");
        this.type = type;
        this.typeDefs = typeDefs;
        this.directFields = directFields;
    }

    YetiTypeAttr(ModuleType mt) {
        this(mt.type, mt.typeDefs, mt.directFields);
    }

    private static final class EncodeType {
        ClassWriter cw;
        ByteVector buf = new ByteVector();
        Map refs = new HashMap();
        Map vars = new HashMap();

        void writeMap(Map m) {
            if (m != null) {
                for (Iterator i = m.entrySet().iterator(); i.hasNext();) {
                    Map.Entry e = (Map.Entry) i.next();
                    YetiType.Type t = (YetiType.Type) e.getValue();
                    if (t.field == YetiType.FIELD_MUTABLE) {
                        buf.putByte(MUTABLE);
                    }
                    write(t);
                    int name = cw.newUTF8((String) e.getKey());
                    buf.putShort(name);
                }
            }
            buf.putByte(END);
        }

        void writeArray(YetiType.Type[] param) {
            for (int i = 0; i < param.length; ++i) {
                write(param[i]);
            }
            buf.putByte(END);
        }

        void write(YetiType.Type type) {
            type = type.deref();
            if (type.type == YetiType.VAR) {
                Integer id = (Integer) vars.get(type);
                if (id == null) {
                    vars.put(type, id = new Integer(vars.size()));
                    if (id.intValue() > 0x7fff) {
                        throw new RuntimeException("Too many type parameters");
                    }
                    if ((type.flags & YetiType.FL_ORDERED_REQUIRED) != 0) {
                        buf.putByte(ORDERED);
                    }
                }
                buf.putByte(YetiType.VAR);
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
            buf.putByte(type.type);
            if (type.type == YetiType.FUN) {
                write(type.param[0]);
                write(type.param[1]);
            } else if (type.type == YetiType.MAP) {
                writeArray(type.param);
            } else if (type.type == YetiType.STRUCT ||
                       type.type == YetiType.VARIANT) {
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
                writeArray((YetiType.Type[]) e.getValue());
            }
            buf.putByte(END);
        }

        void writeDirectFields(Map fields) {
            StringBuffer r = new StringBuffer();
            Iterator i = fields.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                Object v = e.getValue();
                r.append(v == null ? 'P' : 'F');
                r.append(e.getKey());
                r.append('\000');
                if (v != null) {
                    r.append(v);
                    r.append('\000');
                }
            }
            try {
                byte[] v = r.toString().getBytes("UTF-8");
                buf.putInt(v.length);
                buf.putByteArray(v, 0, v.length);
            } catch (java.io.UnsupportedEncodingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final class DecodeType {
        ClassReader cr;
        byte[] in;
        char[] buf;
        int p, end;
        Map vars = new HashMap();
        List refs = new ArrayList();

        DecodeType(ClassReader cr, int off, int len, char[] buf) {
            this.cr = cr;
            in = cr.b;
            p = off;
            end = p + len;
            this.buf = buf;
        }

        Map readMap() {
            if (in[p] == END) {
                ++p;
                return null;
            }
            HashMap res = new HashMap();
            while (in[p] != END) {
                YetiType.Type t = read();
                res.put(cr.readUTF8(p, buf), t);
                p += 2;
            }
            ++p;
            return res;
        }

        YetiType.Type[] readArray() {
            List param = new ArrayList();
            while (in[p] != END) {
                param.add(read());
            }
            ++p;
            return (YetiType.Type[])
                        param.toArray(new YetiType.Type[param.size()]);
        }

        YetiType.Type read() {
            YetiType.Type t;
            int tv;

            if (p >= end) {
                throw new RuntimeException("Invalid type description");
            }
            switch (tv = in[p++]) {
            case YetiType.VAR: {
                Integer var = new Integer(cr.readUnsignedShort(p));
                p += 2;
                if ((t = (YetiType.Type) vars.get(var)) == null) {
                    vars.put(var, t = new YetiType.Type(1));
                }
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
                return (YetiType.Type) refs.get(v);
            }
            case MUTABLE:
                return YetiType.fieldRef(1, read(), YetiType.FIELD_MUTABLE);
            }
            if (tv < YetiType.PRIMITIVES.length &&
                (t = YetiType.PRIMITIVES[tv]) != null) {
                return t;
            }
            t = new YetiType.Type(tv, null);
            refs.add(t);
            if (tv == YetiType.FUN) {
                t.param = new YetiType.Type[2];
                t.param[0] = read();
                t.param[1] = read();
            } else if (tv == YetiType.MAP) {
                t.param = readArray();
            } else if (tv == YetiType.STRUCT || tv == YetiType.VARIANT) {
                t.finalMembers = readMap();
                t.partialMembers = readMap();
                Map param;
                if (t.finalMembers == null) {
                    param = t.partialMembers;
                } else if (t.partialMembers == null) {
                    param = t.finalMembers;
                } else {
                    param = new HashMap(t.finalMembers);
                    param.putAll(t.partialMembers);
                }
                t.param = param == null ? YetiType.NO_PARAM :
                           (YetiType.Type[]) param.values().toArray(
                                              new YetiType.Type[param.size()]);
            } else if (tv == YetiType.JAVA) {
                t.javaType = JavaType.fromDescription(cr.readUTF8(p, buf));
                p += 2;
                t.param = readArray();
            } else if (tv == YetiType.JAVA_ARRAY) {
                t.param = new YetiType.Type[] { read() };
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

        Map readDirectFields() {
            int len = cr.readInt(p);
            String code;
            try {
                code = new String(in, p + 4, len, "UTF-8");
            } catch (java.io.UnsupportedEncodingException ex) {
                throw new RuntimeException(ex);
            }
            p += len + 4;
            Map result = new HashMap();
            for (int e, n = 0;;) {
                if ((e = code.indexOf('\000', n)) < 0)
                    break;
                String name = code.substring(n + 1, e);
                if (code.charAt(n) == 'P') {
                    n = e + 1;
                    result.put(name, null);
                    continue;
                }
                if ((n = code.indexOf('\000', ++e)) < 0)
                    break;
                result.put(name, code.substring(e, n++));
            }
            return result;
        }
    }

    protected Attribute read(ClassReader cr, int off, int len, char[] buf,
                             int codeOff, Label[] labels) {
        if (cr.b[off] != 0) {
            throw new RuntimeException("Unknown type encoding: " + cr.b[off]);
        }
        DecodeType decoder = new DecodeType(cr, off + 1, len - 1, buf);
        YetiType.Type t = decoder.read();
        Map typeDefs = decoder.readTypeDefs();
        return new YetiTypeAttr(t, typeDefs, decoder.readDirectFields());
    }

    protected ByteVector write(ClassWriter cw, byte[] code, int len,
                               int maxStack, int maxLocals) {
        if (encoded != null) {
            return encoded;
        }
        EncodeType enc = new EncodeType();
        enc.cw = cw;
        enc.buf.putByte(0); // encoding version
        enc.write(type);
        enc.writeTypeDefs(typeDefs);
        enc.writeDirectFields(directFields);
        return encoded = enc.buf;
    }

}

class ModuleType {
    YetiType.Type type;
    Map typeDefs;
    Map directFields;
    String topDoc;

    ModuleType(YetiType.Type type, Map typeDefs, Map directFields) {
        this.type = type;
        this.typeDefs = typeDefs;
        this.directFields = directFields;
    }
}

class YetiTypeVisitor implements ClassVisitor {
    YetiTypeAttr typeAttr;

    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
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
            typeAttr = (YetiTypeAttr) attr;
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

    static ModuleType readType(ClassReader reader) {
        YetiTypeVisitor visitor = new YetiTypeVisitor();
        reader.accept(visitor,
                      new Attribute[] { new YetiTypeAttr(null, null, null) },
                      ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return visitor.typeAttr == null ? null
            : new ModuleType(visitor.typeAttr.type,
                             visitor.typeAttr.typeDefs,
                             visitor.typeAttr.directFields);
    }

    static ModuleType getType(YetiParser.Node node, String name,
                              boolean bySourcePath) {
        CompileCtx ctx = CompileCtx.current();
        ModuleType t = (ModuleType) ctx.types.get(name);
        if (t != null) {
            return t;
        }
        String source = name;
        InputStream in = null;
        if (!bySourcePath) {
            source += ".yeti";
            in = ClassFinder.find(name + ".class");
        }
        try {
            if (in == null) {
                t = (ModuleType) ctx.types.get(ctx.compile(source, 0));
                if (t == null) {
                    throw new Exception("Could not compile `" + name
                                      + "' to a module");
                }
            } else {
                t = readType(new ClassReader(in));
                in.close();
                if (t == null) {
                    throw new Exception("`" + name + "' is not a yeti module");
                }
            }
            ctx.types.put(name, t);
            return t;
        } catch (CompileException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CompileException(node, ex.getMessage());
        }
    }
}
