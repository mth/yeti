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

class ToFile implements CodeWriter {
    public void writeClass(String name, byte[] code) throws Exception {
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
}

public class YetiC implements SourceReader {
    public static final int CF_COMPILE_MODULE   = 1;
    public static final int CF_PRINT_PARSE_TREE = 2;
    static String inCharset = "UTF-8";

    public char[] getSource(String name) throws IOException {
        char[] buf = new char[0x8000];
        int l = 0;
        InputStream stream;
        try {
            stream = new FileInputStream(name);
        } catch (IOException ex) {
            int p = name.lastIndexOf('/');
            if (p <= 0) {
                throw ex;
            }
            try {
                stream = new FileInputStream(name.substring(p + 1));
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
        } finally {
            stream.close();
        }
        char[] r = new char[l];
        System.arraycopy(buf, 0, r, 0, l);
        return r;
    }

    private static void help() {
        System.out.println("yeti -flags... files\n\n" +
            "  -h      Print this help\n" +
            "  -C      Compile to classes\n" +
            "  -e expr Evaluate expr and print result\n");
        System.exit(0);
    }

    public static void main(String[] argv) throws Exception {
        if(argv.length == 0) {
            help();
        }
        boolean eval = false, exec = true, printType = false;
        StringBuffer expect = new StringBuffer();
        int expectCounter = 0;
        char[] src = null;
        List sources = new ArrayList();
        String[] evalArgs = {};
        String mainClass = "Program";
        int flags = 0;

        for (int i = 0; i < argv.length; ++i) {
            if (expectCounter < expect.length()) {
                switch (expect.charAt(expectCounter++)) {
                case 'e':
                    src = argv[i].toCharArray();
                    break;
                }
                continue;
            }
            if ("-h".equals(argv[i]) || "--help".equals(argv[i])) {
                help();
            }
            if (argv[i].startsWith("-")) {
                boolean xflag = false;
                for (int j = 1, cnt = argv[i].length(); j < cnt; ++j) {
                    if (xflag) {
                        switch (argv[i].charAt(j)) {
                        case 'm':
                            printType = true;
                            break;
                        case 'p':
                            flags |= CF_PRINT_PARSE_TREE;
                            break;
                        default:
                            System.err.println("Unexpected option 'v"
                                + argv[i].charAt(j) + "'");
                            System.exit(1);
                        }
                        xflag = false;
                        continue;
                    }
                    switch (argv[i].charAt(j)) {
                    case 'e':
                        eval = true;
                        expect.append('e');
                        break;
                    case 'x':
                        xflag = true;
                        break;
                    case 'C':
                        exec = false;
                        break;
                    default:
                        System.err.println("Unknown option '"
                            + argv[i].charAt(j) + "'");
                        System.exit(1);
                    }
                }
                continue;
            }
            if (eval || exec && !sources.isEmpty()) {
                evalArgs = new String[argv.length - i];
                System.arraycopy(argv, i, evalArgs, 0, evalArgs.length);
                break;
            }
            sources.add(argv[i]);
        }
        if (expectCounter < expect.length()) {
            System.err.println("Expecting arguments for option(s): "
                + expect.substring(expectCounter));
            System.exit(1);
        }
        if (sources.isEmpty() && src  == null) {
            return;
        }
        CodeWriter writer = exec ? (CodeWriter) new Loader() : new ToFile();
        YetiCode.CompileCtx compilation =
            new YetiCode.CompileCtx(new YetiC(), writer);
        try {
            if (eval) {
                flags |= CF_COMPILE_MODULE;
                compilation.compile(null, mainClass, src, flags);
                if (printType) {
                    System.out.println((YetiType.Type)
                        compilation.types.get(mainClass));
                    return;
                }
            } else if (printType) {
                if (exec) {
                    YetiCode.currentCompileCtx.set(compilation);
                }
                for (int i = 0; i < sources.size(); ++i) {
                    System.out.println(YetiTypeVisitor.getType(null,
                        (String) sources.get(i)));
                }
                return;
            } else {
                for (int i = 0, cnt = sources.size(); i < cnt; ++i) {
                    mainClass =
                        compilation.compile((String) sources.get(i), flags);
                }
            }
        } catch (CompileException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
        compilation.write();
        if (exec) {
            Class c = Class.forName(mainClass.replace('/', '.'), true,
                                    (ClassLoader) writer);
            try {
                if (eval) {
                    Object res = c.getMethod("eval", new Class[] {})
                                  .invoke(null, new Object[] {});
                    if (res != null) {
                        System.out.println(res);
                    }
                } else {
                    c.getMethod("main", new Class[] { String[].class })
                     .invoke(null, new Object[] { evalArgs });
                }
            } catch (java.lang.reflect.InvocationTargetException ex) {
                Throwable t = ex.getCause();
                (t == null ? ex : t).printStackTrace();
                System.exit(2);
            }
        }
    }
}
