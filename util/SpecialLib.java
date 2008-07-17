// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti special library utility.
 *
 * Copyright (c) 2008 Madis Janson
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
package yeti.lang;

import java.io.*;
import org.objectweb.asm.*;

class LListAdapter extends ClassAdapter implements Opcodes {
    LListAdapter(ClassVisitor cv) {
        super(cv);
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
                                     String signature, String[] exceptions) {
        return "length".equals(name) || "forEach".equals(name) ||
               "fold".equals(name) || "smap".equals(name) ? null
                : cv.visitMethod(access, name, desc, signature, exceptions);
    }

    public void visitEnd() {
        MethodVisitor mv =
            cv.visitMethod(ACC_PUBLIC, "length", "()J", null, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, 0);
        Label retry = new Label(), end = new Label();
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, end);
        mv.visitLabel(retry);
        mv.visitIincInsn(0, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "next", "()Lyeti/lang/AIter;");
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, retry);
        mv.visitLabel(end);
        mv.visitVarInsn(ILOAD, 0);
        mv.visitInsn(I2L);
        mv.visitInsn(LRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cv.visitMethod(ACC_PUBLIC, "forEach",
                            "(Ljava/lang/Object;)V", null, null);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ASTORE, 0);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, end = new Label());
        mv.visitLabel(retry = new Label());
        mv.visitInsn(DUP2); // fun iter fun iter
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "first", "()Ljava/lang/Object;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun",
                           "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(POP);
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "next", "()Lyeti/lang/AIter;");
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, retry);
        mv.visitLabel(end);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC, "fold",
                    "(Lyeti/lang/Fun;Ljava/lang/Object;)Ljava/lang/Object;",
                    null, null);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ASTORE, 2);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitJumpInsn(IFNULL, end = new Label());
        mv.visitLabel(retry = new Label());
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(SWAP);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "first", "()Ljava/lang/Object;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun", "apply",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitVarInsn(ALOAD, 0); 
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "next", "()Lyeti/lang/AIter;");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, 0);
        mv.visitJumpInsn(IFNONNULL, retry);
        mv.visitLabel(end);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cv.visitMethod(ACC_PUBLIC, "smap",
                            "(Lyeti/lang/Fun;)Lyeti/lang/AList;", null, null);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, "yeti/lang/Fun");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, end = new Label());
        mv.visitTypeInsn(NEW, "yeti/lang/MList");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "yeti/lang/MList",
                           "<init>", "()V");
        mv.visitVarInsn(ASTORE, 0);
        mv.visitLabel(retry = new Label());
        mv.visitInsn(DUP2); // fun iter fun iter
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "first", "()Ljava/lang/Object;"); // i -> v
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun", // f v -> v'
                           "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/MList",
                           "add", "(Ljava/lang/Object;)V"); // l v' -> ()
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/AIter",
                           "next", "()Lyeti/lang/AIter;"); // i -> i'
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNONNULL, retry);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ARETURN);
        mv.visitLabel(end);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        
        cv.visitEnd();
    }
}

public class SpecialLib implements Opcodes {
    ClassWriter cw;
    String prefix;

    void transformLList() throws Exception {
        InputStream in = new FileInputStream(prefix + "yeti/lang/LList.class");
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        LListAdapter la = new LListAdapter(cw);
        new ClassReader(in).accept(la, 0);
        in.close();
        storeClass("yeti/lang/LList.class");
    }

    void storeClass(String name) throws Exception {
        cw.visitEnd();
        name = prefix + name;
        int p = name.lastIndexOf('/');
        new java.io.File(name.substring(0, p)).mkdirs();
        FileOutputStream os = new FileOutputStream(name);
        os.write(cw.toByteArray());
        os.close();
    }

    void fun2_() throws Exception {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_2, 0, "yeti/lang/Fun2_", null, "yeti/lang/Fun", null);
        cw.visitField(0, "fun", "Lyeti/lang/Fun2;", null, null).visitEnd();
        cw.visitField(0, "arg", "Ljava/lang/Object;", null, null).visitEnd();
        MethodVisitor mv = cw.visitMethod(0, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "yeti/lang/Fun", "<init>", "()V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "apply",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, "yeti/lang/Fun2_",
                          "fun", "Lyeti/lang/Fun2;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ASTORE, 0);
        mv.visitFieldInsn(GETFIELD, "yeti/lang/Fun2_",
                          "arg", "Ljava/lang/Object;");
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun2", "apply",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        storeClass("yeti/lang/Fun2_.class");
    }

    void unsafe() throws Exception {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_2, 0, "yeti/lang/Unsafe", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "unsafeThrow", "(Ljava/lang/Throwable;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        storeClass("yeti/lang/Unsafe.class");
    }

    public static void main(String[] args) throws Exception {
        SpecialLib l = new SpecialLib();
        l.prefix = args[1] + '/';
        if (args[0].equals("tr")) {
            l.transformLList();
        } else if (args[0].equals("pre")) {
            l.fun2_();
            l.unsafe();
        } else {
            System.err.println(args[0] + ": WTF?");
        }
    }
}
