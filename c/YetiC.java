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

import java.io.*;
import java.util.*;
import java.security.Permission;

class ToFile implements CodeWriter {
    private String target;

    ToFile(String target) {
        this.target = target;
    }

    public void writeClass(String name, byte[] code) throws Exception {
        name = target + name;
        int sl = name.lastIndexOf('/');
        if (sl > 0) {
            new File(name.substring(0, sl)).mkdirs();
        }
        FileOutputStream out = new FileOutputStream(name);
        out.write(code);
        out.close();
    }
}

class Loader extends ClassLoader implements CodeWriter {
    private Map classes = new HashMap();

    Loader() {
        super(Thread.currentThread().getContextClassLoader());
    }

    public void writeClass(String name, byte[] code) {
        // to a dotted classname used by loadClass
        classes.put(name.substring(0, name.length() - 6).replace('/', '.'),
                    code);
    }

    // override loadClass to ensure loading our own class
    // even when it already exists in current classpath
    protected synchronized Class loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        Class loaded = findLoadedClass(name);
        if (loaded == null) {
            byte[] code = (byte[]) classes.get(name);
            if (code == null) {
                return super.loadClass(name, resolve);
            }
            loaded = defineClass(name, code, 0, code.length);
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    public InputStream getResourceAsStream(String path) {
        if (path.endsWith(".class")) {
            String name =
                path.substring(0, path.length() - 6).replace('.', '/');
            byte[] code = (byte[]) classes.get(name);
            if (code != null)
                return new ByteArrayInputStream(code);
        }
        return super.getResourceAsStream(path);
    }
}

public class YetiC implements SourceReader {
    public static final int CF_COMPILE_MODULE   = 1;
    public static final int CF_PRINT_PARSE_TREE = 2;
    public static final int CF_EVAL             = 4;
    public static final int CF_EVAL_BIND        = 8;
    public static final int CF_NO_IMPORT        = 16;
    static String inCharset = "UTF-8";
    static final String[] PRELOAD =
        { "yeti/lang/std", "yeti/lang/io" };
    private String basedir;

    public YetiC(String basedir) {
        this.basedir = basedir;
    }

    public char[] getSource(String[] name_) throws IOException {
        char[] buf = new char[0x8000];
        int l = 0;
        InputStream stream;
        String name = name_[0];
        try {
            stream = basedir == null ? new FileInputStream(name)
                        : new FileInputStream(new File(basedir, name));
        } catch (IOException ex) {
            int p = name.lastIndexOf('/');
            if (p <= 0) {
                throw ex;
            }
            try {
                name = name.substring(p + 1);
                stream = basedir == null ? new FileInputStream(name)
                            : new FileInputStream(new File(basedir, name));
            } catch (IOException e) {
                throw ex;
            }            
        }
        try {
            Reader reader = new java.io.InputStreamReader(stream, inCharset);
            for (int n; (n = reader.read(buf, l, buf.length - l)) >= 0; ) {
                if (buf.length - (l += n) < 0x1000) {
                    char[] tmp = new char[buf.length << 1];
                    System.arraycopy(buf, 0, tmp, 0, l);
                    buf = tmp;
                }
            }
        } catch (IOException ex) {
            throw new IOException(name + ": " + ex.getMessage());
        } finally {
            stream.close();
        }
        name_[0] = name;
        char[] r = new char[l];
        System.arraycopy(buf, 0, r, 0, l);
        return r;
    }
}
