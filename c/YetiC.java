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

import java.io.*;
import java.util.*;

class ToFile implements CodeWriter {
    public void writeClass(String name, byte[] code) throws Exception {
        FileOutputStream out = new FileOutputStream(name);
        out.write(code);
        out.close();
    }
}

class Loader extends ClassLoader implements CodeWriter {
    private Map classes = new HashMap();

    public void writeClass(String name, byte[] code) {
        classes.put(name, code);
    }

    public Class findClass(String name) throws ClassNotFoundException {
        byte[] code = (byte[]) classes.get(name + ".class");
        if (code == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, code, 0, code.length);
    }
}

public class YetiC {
    static String inCharset = "UTF-8";

    public static String loadFile(String name) throws IOException {
        StringBuffer buf = new StringBuffer();
        InputStream stream = new FileInputStream(name);
        try {
            Reader reader = new java.io.InputStreamReader(stream, inCharset);
            char[] cbuf = new char[0x8000];
            for (int n; (n = reader.read(cbuf)) >= 0; ) {
                buf.append(cbuf, 0, n);
            }
        } finally {
            stream.close();
        }
        return buf.toString();
    }
    
    public static void main(String[] argv) throws Exception {
        boolean eval = false, exec = true;
        StringBuffer expect = new StringBuffer();
        int expectCounter = 0;
        char[] src = null;
        List sources = new ArrayList();
        String[] evalArgs = {};
        String mainClass = "Program";

        for (int i = 0; i < argv.length; ++i) {
            if (expectCounter < expect.length()) {
                switch (expect.charAt(expectCounter++)) {
                case 'e':
                    src = argv[i].toCharArray();
                    break;
                }
                continue;
            }
            if (argv[i].startsWith("-")) {
                for (int j = 1, cnt = argv[i].length(); j < cnt; ++j) {
                    switch (argv[i].charAt(j)) {
                    case 'e':
                        eval = true;
                        expect.append('e');
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
        CodeWriter writer = exec ? (CodeWriter) new Loader() : new ToFile();
        YetiCode.CompileCtx compilation = new YetiCode.CompileCtx(writer);
        if (eval) {
            compilation.compile(mainClass, src, true);
        } else {
            for (int i = 0, cnt = sources.size(); i < cnt; ++i) {
                String srcName = (String) sources.get(i);
                src = loadFile(srcName).toCharArray();
                int dot = srcName.lastIndexOf('.');
                mainClass = dot < 0 ? srcName : srcName.substring(0, dot);
                compilation.compile(mainClass, src, false);
            }
        }
        compilation.write();
        if (exec) {
            Class c = Class.forName(mainClass, true, (ClassLoader) writer);
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
        }
    }
}
