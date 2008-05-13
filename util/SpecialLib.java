package yeti.lang;

import java.io.FileOutputStream;
import org.objectweb.asm.*;

public class SpecialLib implements Opcodes {
    ClassWriter cw;
    String prefix;

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
        mv.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/Fun2", "apply2",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        storeClass("yeti/lang/Fun2_.class");
    }

    public static void main(String[] args) throws Exception {
        SpecialLib l = new SpecialLib();
        l.prefix = args[0] + '/';
        l.fun2_();
    }
}
