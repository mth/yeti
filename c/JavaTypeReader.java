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

class JavaTypeReader implements ClassVisitor {
    ArrayList fields;

    public YetiType.Type parseTypeSignature

    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
    }

    public void visitEnd() {
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    public void visitAttribute(Attribute attr) {
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
        return visitor.typeAttr == null ? null : visitor.typeAttr.type;
    }

    static YetiType.Type getType(YetiParser.Node node, String name) {
        YetiCode.CompileCtx ctx =
            (YetiCode.CompileCtx) YetiCode.currentCompileCtx.get();
        YetiType.Type t = (YetiType.Type) ctx.types.get(name);
        if (t != null) {
            return t;
        }
        InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(name + ".class");
        try {
            if (in == null) {
                ctx.compile(name + ".yeti", 0);
                t = (YetiType.Type) ctx.types.get(name);
                if (t == null) {
                    throw new Exception("Could compile to `" + name
                                      + "' module");
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
        } catch (Exception ex) {
            if (node == null) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            throw new CompileException(node, ex.getMessage());
        }
    }
}
