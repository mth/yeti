// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library.
 *
 * Copyright (c) 2007-2013 Madis Janson
 * Copyright (c) 2012 Chris Cannam
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;

public final class Core {
    private static final int DEC_SHIFT[] = { 1, 10, 100, 1000, 10000,
        100000, 1000000, 10000000, 100000000, 1000000000 };

    public static final String UNDEF_STR = new String();

    public static String replace(String f, String r, String s) {
        StringBuffer result = new StringBuffer();
        int p = 0, i, l = f.length();
        while ((i = s.indexOf(f, p)) >= 0) {
            result.append(s.substring(p, i));
            result.append(r);
            p = i + l;
        }
        if (p < s.length())
            result.append(s.substring(p));
        return result.toString();
    }

    public static Num parseNum(String str) {
        String s = str.trim();
        int l;
        if ((l = s.length()) == 0)
            throw new IllegalArgumentException("Number expected");
        int radix = 10, st = s.charAt(0) == '-' ? 1 : 0;
        if (l > 2 && s.charAt(st) == '0')
            switch (s.charAt(st + 1)) {
                case 'o': case 'O':
                    radix = 2;
                case 'x': case 'X':
                    s = s.substring(st += 2);
                    if (st != 2)
                        s = "-".concat(s);
                    radix += 6;
            }
        if (radix == 10) {
            if (s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
                char c;
                if ((c = s.charAt(l - 1)) == 'e' || c == 'E')
                    return new FloatNum(Double.parseDouble(
                                s.substring(0, l - 1)));
                return new FloatNum(Double.parseDouble(s));
            }
            int dot = s.indexOf('.');
            if (dot == l - 1) {
                s = s.substring(0, dot);
                dot = -1;
            }
            if (dot > 0) do {
                while (s.charAt(--l) == '0');
                if (s.charAt(l) == '.') {
                    s = s.substring(0, l);
                    break;
                }
                if (l <= 11) {
                    long n = Long.parseLong(s.substring(0, dot).concat(
                                            s.substring(dot + 1, l + 1)));
                    if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE)
                        return new RatNum((int) n, DEC_SHIFT[l - dot]);
                }
                return new FloatNum(Double.parseDouble(s));

            } while (false);
        }
        if ((l - st) < 96 / radix + 10) // 22, 19, 16
            return new IntNum(Long.parseLong(s, radix));
        return new BigNum(s, radix);
    }

    public static String concat(String[] param) {
        int l = 0;
        for (int i = param.length; --i >= 0;)
            l += param[i].length();
        if (l == 0)
            return "";
        char[] res = new char[l];
        int p = 0;
        for (int i = 0, cnt = param.length; i < cnt; ++i) {
            String s = param[i];
            s.getChars(0, l = s.length(), res, p);
            p += l;
        }
        return new String(res);
    }

    public static String show(Object o) {
        StringBuffer r;
        if (o == null)
            return "[]";
        if (o instanceof String) {
            // TODO escaping
            char[] s = ((String) o).toCharArray();
            r = new StringBuffer().append('"');
            int p = 0, i = 0, cnt = s.length;
            for (String c; i < cnt; ++i) {
                if (s[i] == '\\') {
                    c = "\\\\";
                } else if (s[i] == '"') {
                    c = "\\\"";
                } else if (s[i] == '\n') {
                    c = "\\n";
                } else if (s[i] == '\r') {
                    c = "\\r";
                } else if (s[i] == '\t') {
                    c = "\\t";
                } else if (s[i] >= '\u0000' && s[i] < ' ') {
                    c = "000".concat(Integer.toHexString(s[i]));
                    c = "\\u".concat(c.substring(c.length() - 4));
                } else {
                    continue;
                }
                r.append(s, p, i - p).append(c);
                p = i + 1;
            }
            return r.append(s, p, i - p).append('"').toString();
        }
        if (o.getClass().isArray()) {
            r = new StringBuffer().append('[');
            for (int i = 0, len = Array.getLength(o); i < len; ++i) {
                if (i != 0)
                    r.append(',');
                if (i == 50 && len > 110) {
                    r.append("...");
                    i = len - 50;
                }
                r.append(Array.get(o, i));
            }
            return r.append(']').toString();
        }
        return o.toString();
    }

    static String read(java.io.Reader r, int max) throws IOException {
        char[] buf = new char[max];
        int n = r.read(buf, 0, max);
        return n < 0 ? null : new String(buf, 0, n);
    }

    static String readAll(java.io.Reader r) throws IOException {
        StringBuffer result = new StringBuffer();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf, 0, buf.length)) > 0)
            result.append(buf, 0, n);
        return result.toString();
    }

    static AList readAll(int limit, Fun read, Fun close) {
        byte[] buf = new byte[0 < limit && limit <= 65536 ? limit : 8192];
        int l = 0, n;
        try {
            while ((n = ((Number) read.apply(buf, new IntNum(l)))
                        .intValue()) >= 0)
                if (buf.length - (l += n) < 2048) {
                    int reserve = buf.length << 1;
                    if (limit > 0 && reserve > limit) {
                        if (buf.length >= limit)
                            Unsafe.unsafeThrow(new java.io.IOException(
                                "Read limit " + limit + " exceeded"));
                        reserve = limit;
                    }
                    byte[] tmp = new byte[reserve];
                    System.arraycopy(buf, 0, tmp, 0, l);
                    buf = tmp;
                }
        } finally {
            close.apply(null);
        }
        return l > 0 ? new ByteArray(0, l, buf) : null;
    }

    public static final ThreadLocal ARGV = new ThreadLocal() {
        protected Object initialValue() {
            return new MList();
        }
    };

    public static void setArgv(String[] argv) {
        if (argv != null) {
            ARGV.set(new MList(argv));
        }
    }

    public static Object badMatch(Object match) {
        throw new IllegalArgumentException("bad match (" + match + ')');
    }

    public static String capitalize(String s) {
        char[] tmp = s.toCharArray();
        if (tmp.length == 0)
            return s;
        tmp[0] = Character.toUpperCase(tmp[0]);
        return new String(tmp);
    }

    static String uncapitalize(String s) {
        char[] tmp = s.toCharArray();
        if (tmp.length == 0)
            return s;
        tmp[0] = Character.toLowerCase(tmp[0]);
        return new String(tmp);
    }

    private static final char base64[] = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            .toCharArray();

    static String b64enc(byte[] buf, int len) {
        char[] res = new char[(len + 2) / 3 * 4];
        for (int s = 0, d = 0; len > 0; len -= 3) {
            res[d] = base64[buf[s] >>> 2 & 63];
            res[d + 1] = base64[((buf[s] & 3) << 4) |
                                (len > 1 ? buf[s + 1] >>> 4 & 15 : 0)];
            res[d + 2] = len > 1 ? base64[((buf[s + 1] & 15) << 2) |
                                     (len > 2 ? buf[s + 2] >>> 6 & 3: 0)] : '=';
            res[d + 3] = len > 2 ? base64[buf[s + 2] & 63] : '=';
            s += 3;
            d += 4;
        }
        return new String(res);
    }

    static AList b64dec(String src) throws Exception {
        int n = 0, outp = 0;
        byte[] buf = new byte[src.length() * 3 / 4];

        for (int s = 0, len = src.length(); s < len; ++s) {
            char c = src.charAt(s);
            int v = c == '+' ? 0x3e : c == '/' ? 0x3f : c >= 'A' && c <= 'Z'
                        ? c - 'A' : c >= 'a' && c <= 'z'
                        ? c - 'G' : c >= '0' && c <= '9' ? c + 4 : -1;
            if (v == -1) {
                if (c == '=')
                    break;
                continue;
            }
            switch (n) {
            case 0:
                buf[outp] = (byte) (v << 2);
                break;
            case 1:
                buf[outp] |= v >>> 4;
                buf[outp + 1] = (byte) ((v & 15) << 4);
                break;
            case 2:
                buf[outp + 1] |= v >>> 2;
                buf[outp + 2] = (byte) ((v & 3) << 6);
                break;
            case 3:
                buf[outp + 2] |= v;
                outp += 3;
                n = -1;
                break;
            }
            ++n;
        }
        if (n > 0) // 1, 2, 3
            outp += n - 1;
        return outp > 0 ? new ByteArray(0, outp, buf) : null;
    }

    public static byte[] bytes(AList list) {
        if (list == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            AIter i = list;
            list = null; // help gc
            while (i != null)
                i = i.write(buf);
            return buf.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
