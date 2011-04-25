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
import yeti.renamed.asm3.*;
import java.io.IOException;
import java.io.InputStream;

/*
 * Encoding:
 *
 * 00 - format identifier
 * Follows type description
 * 00 XX XX - free type variable XXXX
 * XX, where XX is 00..08 -
 *      primitives (same as YType.type UNIT - MAP_MARKER)
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

    ModuleType moduleType;
    private ByteVector encoded;

    YetiTypeAttr(ModuleType mt) {
        super("YetiModuleType");
        this.moduleType = mt;
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
                    YType t = (YType) e.getValue();
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
                writeArray((YType[]) e.getValue());
            }
            buf.putByte(END);
        }

        void writeDirectFields(Map fields) {
            buf.putShort(fields.size());
            Iterator i = fields.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                Object v = e.getValue();
                buf.putShort(cw.newUTF8((String) e.getKey()));
                buf.putShort(cw.newUTF8(v == null ? "" : (String) v));
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
            return (YType[])
                        param.toArray(new YType[param.size()]);
        }

        YType read() {
            YType t;
            int tv;

            if (p >= end) {
                throw new RuntimeException("Invalid type description");
            }
            switch (tv = in[p++]) {
            case YetiType.VAR: {
                Integer var = new Integer(cr.readUnsignedShort(p));
                p += 2;
                if ((t = (YType) vars.get(var)) == null) {
                    vars.put(var, t = new YType(1));
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
                return (YType) refs.get(v);
            }
            case MUTABLE:
                return YetiType.fieldRef(1, read(), YetiType.FIELD_MUTABLE);
            }
            if (tv < YetiType.PRIMITIVES.length &&
                (t = YetiType.PRIMITIVES[tv]) != null) {
                return t;
            }
            t = new YType(tv, null);
            refs.add(t);
            if (tv == YetiType.FUN) {
                t.param = new YType[2];
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
                           (YType[]) param.values().toArray(
                                              new YType[param.size()]);
            } else if (tv == YetiType.JAVA) {
                t.javaType = JavaType.fromDescription(cr.readUTF8(p, buf));
                p += 2;
                t.param = readArray();
            } else if (tv == YetiType.JAVA_ARRAY) {
                t.param = new YType[] { read() };
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
            int n = cr.readUnsignedShort(p);
            Map result = new HashMap(n);
            for (;;) {
                p += 2;
                if (--n < 0)
                    return result;
                String name = cr.readUTF8(p, buf);
                String fun = cr.readUTF8(p += 2, buf);
                result.put(name, fun.length() == 0 ? null : fun);
            }
        }
    }

    protected Attribute read(ClassReader cr, int off, int len, char[] buf,
                             int codeOff, Label[] labels) {
        if (cr.b[off] != 0) {
            throw new RuntimeException("Unknown type encoding: " + cr.b[off]);
        }
        DecodeType decoder = new DecodeType(cr, off + 1, len - 1, buf);
        YType t = decoder.read();
        Map typeDefs = decoder.readTypeDefs();
        return new YetiTypeAttr(new ModuleType(t, typeDefs,
                                               decoder.readDirectFields()));
    }

    protected ByteVector write(ClassWriter cw, byte[] code, int len,
                               int maxStack, int maxLocals) {
        if (encoded != null) {
            return encoded;
        }
        EncodeType enc = new EncodeType();
        enc.cw = cw;
        enc.buf.putByte(0); // encoding version
        enc.write(moduleType.type);
        enc.writeTypeDefs(moduleType.typeDefs);
        enc.writeDirectFields(moduleType.directFields);
        return encoded = enc.buf;
    }

}

class ModuleType {
    YType type;
    Map typeDefs;
    Map directFields;
    String topDoc;
    boolean deprecated;

    ModuleType(YType type, Map typeDefs, Map directFields) {
        this.type = type;
        this.typeDefs = typeDefs;
        this.directFields = directFields;
    }
}

class YetiTypeVisitor implements ClassVisitor {
    YetiTypeAttr typeAttr;
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
        reader.accept(visitor, new Attribute[] { new YetiTypeAttr(null) },
                      ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        if (visitor.typeAttr == null)
            return null;
        ModuleType mt = visitor.typeAttr.moduleType;
        if (mt != null)
            mt.deprecated = visitor.deprecated;
        return mt;
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
            in = ClassFinder.get().findClass(name + ".class");
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
