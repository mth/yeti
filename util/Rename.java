/*
 * Package renamer for .class files in jar.
 *
 * Copyright (c) 2009 Madis Janson
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

import java.util.jar.*;
import java.util.zip.*;
import java.util.*;
import java.io.*;

public class Rename {
    static void replace(byte[] data, int len, byte[] find, byte[] repl) {
        len -= find.length - 1;
        byte b = find[0];
    main:
        for (int i = 0; i < len; ++i) {
            if (data[i] == b) {
                for (int j = 1; j < find.length; ++j)
                    if (data[i + j] != find[j])
                        continue main;
                System.arraycopy(repl, 0, data, i, repl.length);
                i += repl.length - 1;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String find1S = "org/objectweb/asm/";
        String repl1S = "yeti/renamed/asm3/";
        byte[] find1 = find1S.getBytes("UTF-8");
        byte[] repl1 = repl1S.getBytes("UTF-8");
        byte[] find2 = find1S.replace('/', '.').getBytes("UTF-8");
        byte[] repl2 = repl1S.replace('/', '.').getBytes("UTF-8");
        JarFile in = new JarFile(args[0]);
        JarOutputStream out =
            new JarOutputStream(new FileOutputStream(args[1]));
        byte[] buf = new byte[65536];
        for (Enumeration e = in.entries(); e.hasMoreElements();) {
            ZipEntry z = (ZipEntry) e.nextElement();
            if (z.isDirectory())
                continue;
            String name = z.getName();
            if (name.startsWith(find1S))
                name = repl1S.concat(name.substring(find1S.length()));
            ZipEntry z2 = new ZipEntry(name);
            z2.setComment(z.getComment());
            z2.setCrc(z.getCrc());
            z2.setExtra(z.getExtra());
            z2.setSize(z.getSize());
            z2.setTime(z.getTime());
            if (buf.length < z.getSize())
                buf = new byte[(int) z.getSize()];
            InputStream is = in.getInputStream(z);
            int n, count = 0;
            while ((n = is.read(buf, count, buf.length - count)) >= 0)
                count += n;
            if (count != z.getSize())
                throw new Exception(name + ": " + count + " != " + z.getSize());
            is.close();
            replace(buf, count, find1, repl1);
            replace(buf, count, find2, repl2);
            out.putNextEntry(z2);
            out.write(buf, 0, count);
        }
        out.close();
    }
}
