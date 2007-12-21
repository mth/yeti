// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
 * Copyright (c) 2007 Madis Janson
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

class YetiTypeAttr extends Attribute {
    static final byte END = -1;
    static final byte REF = -2;
    static final byte ORDERED = -3;
    static final byte MUTABLE = -4;

    YetiType.Type type;

    YetiTypeAttr(YetiType.Type type) {
        super("YetiModuleType");
        this.type = type;
    }

    private static final class EncodeType {
        ClassWriter cw;
        ByteVector buf = new ByteVector();
        Map refs = new HashMap();
        Map vars = new HashMap();

        void writeMap(Map m) {
            for (Iterator i = m.entrySet().iterator(); i.hasNext();) {
                Map.Entry e = (Map.Entry) i.next();
                YetiType.Type t = (YetiType.Type) e.getValue();
                if (t instanceof YetiType.MutableFieldType) {
                    buf.putByte(MUTABLE);
                }
                write(t);
                buf.putShort(cw.newUTF8((String) e.getKey()));
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
            buf.putByte(type.type);
            if (type.type == YetiType.FUN) {
                write(type.param[0]);
                write(type.param[1]);
            } else if (type.type == YetiType.MAP) {
                write(type.param[0]);
                write(type.param[1]);
                write(type.param[2]);
            } else if (type.type == YetiType.STRUCT ||
                       type.type == YetiType.VARIANT) {
                writeMap(type.finalMembers);
                writeMap(type.partialMembers);
            } else {
                throw new RuntimeException("Unknown type: " + type.type);
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
            HashMap res = new HashMap();
            while (in[p] != END) {
                YetiType.Type t = read();
                res.put(cr.readUTF8(cr.readUnsignedShort(p), buf), t);
                p += 2;
            }
            ++p;
            return res;
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
                return new YetiType.MutableFieldType(1, read());
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
                t.param = new YetiType.Type[3];
                t.param[0] = read();
                t.param[1] = read();
                t.param[2] = read();
            } else if (tv == YetiType.STRUCT || tv == YetiType.VARIANT) {
                t.finalMembers = readMap();
                t.partialMembers = readMap();
                Map param = new HashMap(t.finalMembers);
                param.putAll(t.partialMembers);
                t.param = (YetiType.Type[]) param.values().toArray(
                                              new YetiType.Type[param.size()]);
            } else {
                throw new RuntimeException("Unknown type: " + tv);
            }
            return t;
        }
    }

    protected Attribute read(ClassReader cr, int off, int len, char[] buf,
                             int codeOff, Label[] labels) {
        return new YetiTypeAttr(new DecodeType(cr, off, len, buf).read());
    }

    protected ByteVector write(ClassWriter cw, byte[] code, int len,
                                int maxStack, int maxLocals) {
        EncodeType enc = new EncodeType();
        enc.cw = cw;
        enc.write(type);
        return enc.buf;
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
            if (attr != null) {
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

    static YetiType.Type readType(ClassReader reader) {
        YetiTypeVisitor visitor = new YetiTypeVisitor();
        reader.accept(visitor, new Attribute[] { new YetiTypeAttr(null) },
                      ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return visitor.typeAttr.type;
    }
}
