// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library.
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

package yeti.lang;

import java.util.Random;

public final class Core {
    private static final int DEC_SHIFT[] = { 1, 10, 100, 1000, 10000,
        100000, 1000000, 10000000, 100000000, 1000000000 };
    private static Random rnd = null;

    public static final String UNDEF_STR = new String();

    public static final Fun RANDINT = new Fun() {
        public Object apply(Object x) {
            if (rnd == null) {
                initRandom();
            }
            Num n = (Num) x;
            if (n.rCompare(0x7fffffffL) > 0) {
                return new IntNum(rnd.nextInt(n.intValue()));
            }
            if (n.rCompare(Long.MAX_VALUE) > 0) {
                return new IntNum((long) (n.doubleValue() * rnd.nextDouble()));
            }
            // XXX
            return new FloatNum(Math.floor(n.doubleValue() * rnd.nextDouble()));
        }
    };

    private static synchronized void initRandom() {
        if (rnd == null) {
            rnd = new Random();
        }
    }

    public static String replace(String f, String r, String s) {
        StringBuffer result = new StringBuffer();
        int p = 0, i, l = f.length();
        while ((i = s.indexOf(f, p)) >= 0) {
            result.append(s.substring(p, i));
            result.append(r);
            p = i + l;
        }
        if (p < s.length()) {
            result.append(s.substring(p));
        }
        return result.toString();
    }

    public static Num parseNum(String str) {
        String s = str.trim();
        int l;
        if ((l = str.length()) == 0) {
            throw new IllegalArgumentException("Number expected");
        }
        if (s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
            char c;
            if ((c = s.charAt(l - 1)) == 'e' || c == 'E') {
                return new FloatNum(Double.parseDouble(s.substring(0, l - 1)));
            }
            return new FloatNum(Double.parseDouble(s));
        }
        int dot = s.indexOf('.');
        if (dot == l - 1) {
            s = s.substring(0, dot);
            dot = -1;
        }
        int st = s.charAt(0) == '-' ? 1 : 0, n;
        if (dot > 0) do {
            while (s.charAt(--l) == '0');
            if (s.charAt(l) == '.') {
                s = s.substring(0, l);
                break;
            }
            if ((n = l - st) > 10 || n == 10 && s.charAt(st) > '2') {
                return new FloatNum(Double.parseDouble(s));
            }
            int shift = DEC_SHIFT[l - dot];
            s = s.substring(0, dot) + s.substring(dot + 1, l + 1);
            return new RatNum(Integer.parseInt(s), shift);
        } while (false);
        if ((l - st) <= 19) {
            return new IntNum(Long.parseLong(s));
        }
        return new BigNum(s);
    }

    public static String concat(String[] param) {
        int l = 0;
        for (int i = param.length; --i >= 0;) {
            l += param[i].length();
        }
        if (l == 0) {
            return "";
        }
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
        if (o == null) {
            return "[]";
        }
        if (o instanceof String) {
            // TODO escaping
            char[] s = ((String) o).toCharArray();
            StringBuffer r = new StringBuffer("\"");
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
                } else {
                    continue;
                }
                r.append(s, p, i - p);
                r.append(c);
                p = i + 1;
            }
            r.append(s, p, i - p);
            r.append('"');
            return r.toString();
        }
        return o.toString();
    }

    static String read(java.io.Reader r, int max) throws java.io.IOException {
        char[] buf = new char[max];
        int n = r.read(buf, 0, max);
        return n < 0 ? null : new String(buf, 0, n);
    }

    static String readAll(java.io.Reader r) throws java.io.IOException {
        StringBuffer result = new StringBuffer();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf, 0, buf.length)) > 0) {
            result.append(buf, 0, n);
        }
        return result.toString();
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

    public static Object badMatch() {
        throw new IllegalArgumentException("bad match");
    }

    static String capitalize(String s) {
        char[] tmp = s.toCharArray();
        tmp[0] = Character.toUpperCase(tmp[0]);
        return new String(tmp);
    }

    static String uncapitalize(String s) {
        char[] tmp = s.toCharArray();
        tmp[0] = Character.toLowerCase(tmp[0]);
        return new String(tmp);
    }

/*
    public static Object convertList(Object value, String type) {
        if (type == "") {
            return value;
        }
        switch (type.charAt(0)) {
        case 'l': return ((AList) value).toList(type.substring(1));
        case 's': return ((AList) value).toSet(type.substring(1));
        case '[': return ((AList) value).toArray(type.substring(1));
        case 'B': return new Byte(((Num) value).byteValue());
        case 'S': return new Short(((Num) value).shortValue());
        case 'F': return new Float(((Num) value).floatValue());
        case 'D': return new Double(((Num) value).doubleValue());
        case 'I': return new Integer(((Num) value).intValue());
        case 'J': return new Long(((Num) value).longValue());
        case 'i': return ((Num) value).toBigInteger();
        case 'd': return ((Num) value).toBigDecimal();
        }
        return value;
    }
*/
}
