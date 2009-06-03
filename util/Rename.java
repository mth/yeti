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
        String repl1S = "org/objectweb/a31/";
        byte[] find1 = find1S.getBytes("UTF-8");
        byte[] repl1 = repl1S.getBytes("UTF-8");
        byte[] find2 = "org.objectweb.asm.".getBytes("UTF-8");
        byte[] repl2 = "org.objectweb.a31.".getBytes("UTF-8");
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
