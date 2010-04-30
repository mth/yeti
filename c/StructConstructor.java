// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator - structures.
 *
 * Copyright (c) 2010 Madis Janson
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

import yeti.renamed.asm3.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

final class StructField implements Opcodes {
    boolean property;
    boolean mutable;
    boolean inherited; // inherited field in with { ... }
    String name;
    Code value;
    Code setter;
    BindRef binder;
    String javaName;
    StructField nextProperty;
    int index;
    int line;
}

/*
 * Being a closure allows inlining property getters/setters.
 */
final class StructConstructor extends CapturingClosure implements Comparator {
    StructField[] fields;
    int fieldCount;
    StructField properties;
    String impl;
    int arrayVar = -1;
    private boolean mustGen;
    private Code withParent;
    private String[] withFields;

    private class Bind extends BindRef implements Binder, CaptureWrapper {
        StructField field;
        boolean fun;
        boolean direct;
        boolean mutable;
        int var;

        Bind(StructField sf) {
            type = sf.value.type;
            binder = this;
            mutable = sf.mutable;
            fun = sf.value instanceof Function;
            field = sf;
        }

        void initGen(Ctx ctx) {
            if (prepareConst(ctx)) {
                direct = true;
                field.binder = null;
                return;
            }
            Function f;
            if (!mutable && fun) {
                ((Function) field.value).prepareGen(ctx);
                ctx.visitVarInsn(ASTORE, var = ctx.localVarCount++);
            } else {
                if (arrayVar == -1)
                    arrayVar = ctx.localVarCount++;
                var = arrayVar;
                field.binder = null;
            }
        }

        public BindRef getRef(int line) {
            field.binder = this;
            return this;
        }

        public CaptureWrapper capture() {
            return !fun || mutable ? this : null;
        }

        public boolean flagop(int fl) {
            if ((fl & ASSIGN) != 0)
                return mutable;
            if ((fl & PURE) != 0)
                return !mutable;
            if ((fl & DIRECT_BIND) != 0)
                return direct;
            if ((fl & CONST) != 0)
                return !mutable && field.value.flagop(CONST);
            return false;
        }

        boolean prepareConst(Ctx ctx) {
            return !mutable && field.value.prepareConst(ctx);
        }

        void gen(Ctx ctx) {
            if (direct)
                field.value.gen(ctx);
            else
                ctx.load(var);
        }

        public void genPreGet(Ctx ctx) {
            ctx.load(var);
        }

        public void genGet(Ctx ctx) {
            if (impl == null) {
                // GenericStruct
                ctx.visitLdcInsn(field.name);
                ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct", "get",
                                    "(Ljava/lang/String;)Ljava/lang/Object;");
            } else if (field.property) {
                // Property accessor
                ctx.intConst(field.index);
                ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                                    "get", "(I)Ljava/lang/Object;");
            } else {
                ctx.visitFieldInsn(GETFIELD, impl, field.javaName,
                                   "Ljava/lang/Object;");
            }
        }

        public void genSet(Ctx ctx, Code value) {
            if (impl != null && !field.property) {
                value.gen(ctx);
                ctx.visitFieldInsn(PUTFIELD, impl, field.javaName,
                                   "Ljava/lang/Object;");
                return;
            }
            ctx.visitLdcInsn(field.name);
            value.gen(ctx);
            ctx.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct", "set",
                                "(Ljava/lang/String;Ljava/lang/Object;)V");
        }

        public Object captureIdentity() {
            return StructConstructor.this;
        }

        public String captureType() {
            return impl != null ? 'L' + impl + ';' : "Lyeti/lang/Struct;";
        }
    }

    StructConstructor(int maxBinds) {
        fields = new StructField[maxBinds];
    }

    // for some reason, binds are only for non-property fields
    Binder bind(StructField sf) {
        return new Bind(sf);
    }

    void add(StructField field) {
        if (field.name == null)
            throw new IllegalArgumentException();
        fields[fieldCount++] = field;
        if (field.property) {
            field.nextProperty = properties;
            properties = field;
        }
    }

    public int compare(Object a, Object b) {
        return ((StructField) a).name.compareTo(((StructField) b).name);
    }

    void close() {
        // warning - close can be called second time by `with'
        Arrays.sort(fields, 0, fieldCount, this);
        for (int i = 0; i < fieldCount; ++i) {
            StructField field = fields[i];
            field.javaName = "_".concat(Integer.toString(i));
            field.index = i;
            if (field.property)
                mustGen = true;
        }
    }

    void publish() {
        for (int i = 0; i < fieldCount; ++i) {
            if (!fields[i].property) {
                Code v = fields[i].value;
                while (v instanceof BindRef)
                    v = ((BindRef) v).unref(true);
                if (v instanceof Function)
                    ((Function) v).publish = true;
            }
        }
    }

    Map getDirect() {
        Map r = new HashMap();
        for (int i = 0; i < fieldCount; ++i) {
            if (fields[i].mutable || fields[i].property) {
                r.put(fields[i].name, null);
                continue;
            }
            if (fields[i].binder != null)
                continue;
            Code v = fields[i].value;
            while (v instanceof BindRef)
                v = ((BindRef) v).unref(false);
            if (v instanceof Function && v.flagop(CONST))
                r.put(fields[i].name, ((Function) v).name);
        }
        return r;
    }

/*    public BindRef refProxy(BindRef code) {
        return code;
//        return code.flagop(DIRECT_BIND) ? code : captureRef(code);
    }*/

    void captureInit(Ctx st, Capture c, int n) {
        // c.getId() initialises the captures id as a side effect
        st.cw.visitField(ACC_SYNTHETIC, c.getId(st), c.captureType(),
                         null, null).visitEnd();
    }

    void gen(Ctx ctx) {
        boolean generated = false;
        // default: null - GenericStruct
        if (mustGen || fieldCount > 6 && fieldCount <= 15) {
            impl = genStruct(ctx);
            generated = true;
        } else if (fieldCount <= 3) {
            impl = "yeti/lang/Struct3";
        } else if (fieldCount <= 6) {
            impl = "yeti/lang/Struct6";
        }
        for (int i = 0; i < fieldCount; ++i)
            if (fields[i].binder != null)
                ((Bind) fields[i].binder).initGen(ctx);
        String implClass = impl != null ? impl : "yeti/lang/GenericStruct";
        ctx.visitTypeInsn(NEW, implClass);
        ctx.visitInsn(DUP);
        if (withParent != null) {
            withParent.gen(ctx);
            ctx.visitInit(implClass, "(Lyeti/lang/Struct;)V");
        } else if (generated) {
            ctx.visitInit(implClass, "()V");
        } else {
            ctx.constants.structInitArg(ctx, fields, fieldCount, false);
            ctx.visitInit(implClass, "([Ljava/lang/String;[Z)V");
        }
        if (arrayVar != -1)
            ctx.visitVarInsn(ASTORE, arrayVar);
        for (int i = 0, cnt = fieldCount; i < cnt; ++i) {
            if (fields[i].property || fields[i].inherited)
                continue;
            if (arrayVar != -1) {
                ctx.load(arrayVar);
            } else {
                ctx.visitInsn(DUP);
            }
            if (impl == null)
                ctx.visitLdcInsn(fields[i].name);
            if (fields[i].binder != null) {
                fields[i].binder.gen(ctx);
                ((Function) fields[i].value).finishGen(ctx);
            } else {
                fields[i].value.gen(ctx);
            }
            if (impl != null) {
                ctx.visitFieldInsn(PUTFIELD, impl, fields[i].javaName,
                                   "Ljava/lang/Object;");
            } else {
                ctx.visitMethodInsn(INVOKEVIRTUAL, "yeti/lang/GenericStruct",
                        "set", "(Ljava/lang/String;Ljava/lang/Object;)V");
            }
        }
        if (arrayVar != -1)
            ctx.load(arrayVar);
        for (Capture c = captures; c != null; c = c.next) {
            ctx.visitInsn(DUP);
            if (c.wrapper == null)
                c.ref.gen(ctx);
            else
                c.wrapper.genPreGet(ctx);
            String type = c.captureType();
            ctx.captureCast(type);
            ctx.visitFieldInsn(PUTFIELD, impl, c.id, type);
        }
    }

    String genStruct(Ctx ctx) {
        String cn, structKey = null;
        StructField field;
        Label next, dflt = null, jumps[];
        int i;

        if (!mustGen) {
            StringBuffer buf = new StringBuffer();
            for (i = 0; i < fields.length; ++i) {
                buf.append(fields[i].mutable ? ';' : ',')
                   .append(fields[i].name);
            }
            structKey = buf.toString();
            cn = (String) ctx.constants.structClasses.get(structKey);
            if (cn != null)
                return cn;
        }

        // TODO: share generated non-property structure classes
        cn = ctx.compilation.createClassName(ctx, ctx.className, "");
        if (structKey != null) {
            ctx.constants.structClasses.put(structKey, cn);
        }
        Ctx st = ctx.newClass(ACC_SUPER | ACC_FINAL, cn,
                              "yeti/lang/AStruct", null);
        st.fieldCounter = fieldCount;
        mergeCaptures(st);
        Ctx m = st.newMethod(ACC_PUBLIC, "<init>",
                    withParent == null ? "()V" : "(Lyeti/lang/Struct;)V");
        m.load(0).constants
                 .structInitArg(m, fields, fieldCount, withParent != null);
        m.visitInit("yeti/lang/AStruct", "([Ljava/lang/String;[Z)V");
        if (withParent != null) {
            // generates code for joining super fields
            m.intConst(1);
            m.visitIntInsn(NEWARRAY, T_INT);
            m.visitVarInsn(ASTORE, 4); // index - int[]
            m.intConst(withFields.length - 2);
            m.visitVarInsn(ISTORE, 3); // j = NAMES.length - 1
            // ext (extended struct)
            m.load(1).visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                                      "count", "()I");
            m.visitVarInsn(ISTORE, 2); // i - field counter
            Label retry = new Label(), cont = new Label(), exit = new Label();
            next = new Label();
            m.visitLabel(retry);
            m.visitIntInsn(IINC, 2); // --i

            // if (ext.name(i) != NAMES[j]) goto next;
            m.load(1).visitVarInsn(ILOAD, 2);
            m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                              "name", "(I)Ljava/lang/String;");
            m.constants.stringArray(m, withFields);
            m.visitVarInsn(ILOAD, 3);
            m.visitInsn(AALOAD); // NAMES[j]
            m.visitJumpInsn(IF_ACMPNE, cont);

            // this ext.ref(i, index, 0)
            m.load(0).load(1).visitVarInsn(ILOAD, 2);
            m.load(4).intConst(0);
            m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                              "ref", "(I[II)Ljava/lang/Object;");

            jumps = new Label[withFields.length - 1];
            for (i = 0; i < jumps.length; ++i)
                jumps[i] = new Label();
            m.load(0).load(4).intConst(0);
            m.visitInsn(IALOAD); // index[0]
            if (jumps.length > 1) {
                dflt = new Label();
                m.visitVarInsn(ILOAD, 3); // switch (j)
                m.visitSwitchInsn(0, jumps.length - 1, dflt, null, jumps);
            }
            i = 0;
            for (int j = 0;; ++i)
                if (fields[i].inherited) {
                    m.visitLabel(jumps[j]);
                    m.visitFieldInsn(PUTFIELD, cn, "i" + i, "I");
                    m.visitFieldInsn(PUTFIELD, cn, fields[i].javaName,
                                     "Ljava/lang/Object;");
                    if (++j >= jumps.length)
                        break;
                    m.visitJumpInsn(GOTO, next);
                }
            if (jumps.length > 1) {
                m.visitLabel(dflt);
                m.popn(4); // this ref this index
            }
            m.visitLabel(next);
            m.visitIntInsn(IINC, 3); // --j
            m.visitVarInsn(ILOAD, 3);
            m.visitJumpInsn(IFLT, exit);

            m.visitLabel(cont);
            m.visitVarInsn(ILOAD, 2);
            m.visitJumpInsn(IFGT, retry);
            m.visitLabel(exit);
        }
        m.visitInsn(RETURN);
        m.closeMethod();

        // fields
        for (i = 0; i < fieldCount; ++i) {
            if (!fields[i].property)
                st.cw.visitField(ACC_SYNTHETIC, fields[i].javaName,
                        "Ljava/lang/Object;", null, null).visitEnd();
            if (fields[i].inherited)
                st.cw.visitField(ACC_SYNTHETIC | ACC_PRIVATE,
                        "i" + i, "I", null, null).visitEnd();
        }

        // get(String)
        m = st.newMethod(ACC_PUBLIC, "get",
                         "(Ljava/lang/String;)Ljava/lang/Object;");
        m.load(0);
        Label withMutable = null;
        for (i = 0; i < fieldCount; ++i) {
            next = new Label();
            m.load(1).visitLdcInsn(fields[i].name);
            m.visitJumpInsn(IF_ACMPNE, next);
            if (fields[i].property) {
                m.intConst(i);
                m.visitMethodInsn(INVOKEVIRTUAL, cn, "get",
                                  "(I)Ljava/lang/Object;");
            } else {
                m.visitFieldInsn(GETFIELD, cn, fields[i].javaName,
                                 "Ljava/lang/Object;");
            }
            if (fields[i].inherited) {
                if (withMutable == null)
                    withMutable = new Label();
                m.load(0).visitFieldInsn(GETFIELD, cn, "i" + i, "I");
                m.visitInsn(DUP);
                m.visitJumpInsn(IFGE, withMutable);
                m.visitInsn(POP);
            }
            m.visitInsn(ARETURN);
            m.visitLabel(next);
        }
        m.visitTypeInsn(NEW, "java/lang/NoSuchFieldException");
        m.visitInsn(DUP);
        m.load(1).visitInit("java/lang/NoSuchFieldException",
                            "(Ljava/lang/String;)V");
        m.visitInsn(ATHROW);
        if (withMutable != null) {
            m.visitLabel(withMutable);
            m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                              "get", "(I)Ljava/lang/Object;");
            m.visitInsn(ARETURN);
        }
        m.closeMethod();

        // get(int)
        m = st.newMethod(ACC_PUBLIC, "get", "(I)Ljava/lang/Object;");
        m.localVarCount = 2;
        m.load(0).visitVarInsn(ILOAD, 1);
        jumps = new Label[fieldCount];
        int mutableCount = 0;
        for (i = 0; i < fieldCount; ++i) {
            jumps[i] = new Label();
            if (fields[i].mutable)
                ++mutableCount;
        }
        dflt = new Label();
        m.visitSwitchInsn(0, fieldCount - 1, dflt, null, jumps);
        if (withMutable != null)
            withMutable = new Label();
        for (i = 0; i < fieldCount; ++i) {
            field = fields[i];
            m.visitLabel(jumps[i]);
            if (field.property) {
                new Apply(null, field.value,
                          new UnitConstant(null), field.line).gen(m);
            } else {
                m.visitFieldInsn(GETFIELD, cn, field.javaName,
                                 "Ljava/lang/Object;");
            }
            if (field.inherited) {
                m.load(0).visitFieldInsn(GETFIELD, cn, "i" + i, "I");
                m.visitInsn(DUP);
                m.visitJumpInsn(IFGE, withMutable);
                m.visitInsn(POP);
            }
            m.visitInsn(ARETURN);
        }
        m.visitLabel(dflt);
        m.visitInsn(ACONST_NULL);
        m.visitInsn(ARETURN);
        if (withMutable != null) {
            m.visitLabel(withMutable);
            m.visitMethodInsn(INVOKEINTERFACE, "yeti/lang/Struct",
                              "get", "(I)Ljava/lang/Object;");
            m.visitInsn(ARETURN);
        }
        m.closeMethod();

        if (withParent != null) {
            m = st.newMethod(ACC_PUBLIC, "ref",
                             "(I[II)Ljava/lang/Object;");
            Label isConst = null;
            Label isVar = null;
            jumps = new Label[fieldCount];
            for (i = 0; i < fieldCount; ++i) {
                if (fields[i].inherited) {
                    jumps[i] = new Label();
                } else if (fields[i].mutable || fields[i].property) {
                    if (isVar == null)
                        isVar = new Label();
                    jumps[i] = isVar;
                } else {
                    if (isConst == null)
                        isConst = new Label();
                    jumps[i] = isConst;
                }
            }
            dflt = new Label();
            m.load(2).visitVarInsn(ILOAD, 3);
            m.visitVarInsn(ILOAD, 1);
            m.visitSwitchInsn(0, fieldCount - 1, dflt, null, jumps);
            if (isConst != null) {
                m.visitLabel(isConst);
                m.intConst(-1);
                m.visitInsn(IASTORE);
                m.load(0).visitVarInsn(ILOAD, 1);
                m.visitMethodInsn(INVOKEVIRTUAL, cn, "get",
                                  "(I)Ljava/lang/Object;");
                m.visitInsn(ARETURN);
            }
            if (isVar != null) {
                m.visitLabel(isVar);
                m.visitVarInsn(ILOAD, 1);
                m.visitInsn(IASTORE);
                m.load(0).visitInsn(ARETURN);
            }
            for (i = 0; i < fieldCount; ++i) {
                if (!fields[i].inherited)
                    continue;
                m.visitLabel(jumps[i]);
                m.load(0).visitFieldInsn(GETFIELD, cn, "i" + i, "I");
                m.visitInsn(IASTORE);
                m.load(0).visitFieldInsn(GETFIELD, cn, fields[i].javaName,
                                         "Ljava/lang/Object;");
                m.visitInsn(ARETURN);
            }
            m.visitLabel(dflt);
            m.visitInsn(POP2);
            m.visitInsn(ACONST_NULL);
            m.visitInsn(ARETURN);
            m.closeMethod();
        }

        if (mutableCount == 0)
            return cn;
        // set(String, Object)
        m = st.newMethod(ACC_PUBLIC, "set",
                         "(Ljava/lang/String;Ljava/lang/Object;)V");
        m.localVarCount = 3;
        m.load(0);
        for (i = 0; i < fieldCount; ++i) {
            field = fields[i];
            if (!field.mutable)
                continue;
            next = new Label();
            m.load(1).visitLdcInsn(field.name);
            m.visitJumpInsn(IF_ACMPNE, next);
            if (field.property) {
                LoadVar var = new LoadVar();
                var.var = 2;
                new Apply(null, field.setter, var, field.line).gen(m);
                m.visitInsn(POP2);
            } else if (field.inherited) {
                m.visitFieldInsn(GETFIELD, cn, field.javaName,
                                 "Ljava/lang/Object;");
                m.load(1).load(2)
                 .visitMethodInsn(INVOKEINTERFACE,  "yeti/lang/Struct", "set",
                                  "(Ljava/lang/String;Ljava/lang/Object;)V");
            } else {
                m.load(2).visitFieldInsn(PUTFIELD, cn, field.javaName,
                                         "Ljava/lang/Object;");
            }
            m.visitInsn(RETURN);
            m.visitLabel(next);
        }
        m.visitInsn(POP);
        m.visitInsn(RETURN);
        m.closeMethod();
        return cn;
    }

    void genWith(Ctx ctx, Code src, Map srcFields) {
        srcFields = new HashMap(srcFields);
        for (int i = 0; i < fieldCount; ++i)
            srcFields.remove(fields[i].name);
        if (srcFields.isEmpty()) { // everything has been overrided
            gen(ctx);
            return;
        }
        mustGen = true;
        withParent = src;
        StructField[] fields = new StructField[fieldCount + srcFields.size()];
        withFields = new String[srcFields.size() + 1];
        Iterator j = srcFields.entrySet().iterator();
        for (int i = 1; i < withFields.length; ++i) {
            Map.Entry e = (Map.Entry) j.next();
            StructField sf = new StructField();
            withFields[i] = sf.name = (String) e.getKey();
            sf.inherited = true;
            // whether to generate the setter code
            sf.mutable = ((YType) e.getValue()).field == YetiType.FIELD_MUTABLE;
            fields[i - 1] = sf;
        }
        System.arraycopy(this.fields, 0, fields, srcFields.size(), fieldCount);
        this.fields = fields;
        fieldCount = fields.length;
        close();
        gen(ctx);
    }
}

final class WithStruct extends Code {
    private Code src;
    private Code override;
    private String[] names;

    WithStruct(YType type, Code src, Code override,
               String[] names) {
        this.type = type;
        this.src = src;
        this.override = override;
        this.names = names;
    }

    void gen(Ctx ctx) {
        Map srcFields = src.type.deref().finalMembers;
        if (srcFields != null && override instanceof StructConstructor) {
            ((StructConstructor) override).genWith(ctx, src, srcFields);
            return;
        }

        ctx.visitTypeInsn(NEW, "yeti/lang/WithStruct");
        ctx.visitInsn(DUP);
        src.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        override.gen(ctx);
        ctx.visitTypeInsn(CHECKCAST, "yeti/lang/Struct");
        String[] a = new String[names.length + 1];
        System.arraycopy(names, 0, a, 1, names.length);
        ctx.constants.stringArray(ctx, a);
        ctx.intConst(srcFields != null ? 1 : 0);
        ctx.visitInit("yeti/lang/WithStruct",
               "(Lyeti/lang/Struct;Lyeti/lang/Struct;[Ljava/lang/String;Z)V");
        ctx.forceType("yeti/lang/Struct");
    }
}
