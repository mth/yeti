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

import java.util.Map;
import java.util.Random;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

abstract class Fun2 extends Fun {
    abstract Object apply2(Object a, Object b);

    public Object apply(final Object a) {
        return new Fun() {
            public Object apply(Object b) {
                return apply2(a, b);
            }
        };
    }
}

public final class Core {
    private static final int DEC_SHIFT[] = { 1, 10, 100, 1000, 10000,
        100000, 1000000, 10000000, 100000000, 1000000000 };
    private static Random rnd = null;
    private static BufferedReader stdin = null;

    public static final String UNDEF_STR = new String();

    public static final Fun READLN = new Fun() {
        public synchronized Object apply(Object x) {
            if (stdin == null) {
                stdin = new BufferedReader(new InputStreamReader(System.in));
            }
            try {
                return stdin.readLine();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    public static final Fun REVERSE = new Fun() {
        public Object apply(Object x) {
            return x == null ? null : ((AList) x).reverse();
        }
    };

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

    private static final class Head extends Fun {
        public Object apply(Object x) {
            if (x == null) {
                throw new IllegalArgumentException("Empty list");
            }
            return ((AList) x).first();
        }
    }

    private static final class Tail extends Fun {
        public Object apply(Object x) {
            if (x == null) {
                throw new IllegalArgumentException("Empty list");
            }
            return ((AList) x).rest();
        }
    }

    private static final class For extends Fun {
        public Object apply(Object list) {
            final AList l = (AList) list;
            return new Fun() {
                public Object apply(Object f) {
                    if (l != null) {
                        l.iter().forEach(f, l);
                    }
                    return null;
                }
            };
        }
    }

    private static final class ForHash extends Fun2 {
        Object apply2(Object map, Object fun) {
            Fun f = (Fun) fun;
            java.util.Iterator i = ((Map) map).entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry e = (Map.Entry) i.next();
                ((Fun) f.apply(e.getKey())).apply(e.getValue());
            }
            return null;
        }
    }

    private static final class Map_ extends Fun2 {
        Object apply2(Object f, Object list) {
            if (list == null) {
                return null;
            }
            return ((AList) list).map((Fun) f);
        }
    }

    private static final class Map2 extends Fun {
        public Object apply(final Object f) {
            return new Fun2() {
                Object apply2(Object l1, Object l2) {
                    return l1 == null || l2 == null
                        ? null : new Map2List((Fun) f, (AIter) l1,
                                                       (AIter) l2);
                }
            };
        }
    }

    private static final class MapHash extends Fun2 {
        Object apply2(Object fun, Object map) {
            Map m = (Map) map;
            Object[] a = new Object[m.size()];
            Fun f = (Fun) fun;
            java.util.Iterator i = m.entrySet().iterator();
            for (int n = 0; i.hasNext(); ++n) {
                Map.Entry e = (Map.Entry) i.next();
                a[n] = ((Fun) f.apply(e.getKey())).apply(e.getValue());
            }
            return new MList(a);
        }
    }

    private static final class Fold extends Fun2 {
        Object apply2(final Object f, final Object value) {
            return new FunX() {
                public Object apply(Object list) {
                    if (list == null) {
                        return value;
                    }
                    if (list instanceof LList) {
                        AIter i = (AIter) list;
                        Fun fun = (Fun) f;
                        list = null; // give it free for gc
                        Object v;
                        for (v = value; i != null; i = i.next()) {
                            v = ((Fun) fun.apply(v)).apply(i.first());
                        }
                        return v;
                    }
                    return ((AList) list).fold(this, (Fun) f, value);
                }

                public Object apply(Object a, Object b, Fun f) {
                    return ((Fun) f.apply(a)).apply(b);
                }
            };
        }
    }

    private static final class Sum extends FunX {
        public Object apply(Object list) {
            if (list == null) {
                return IntNum._0;
            }
            if (list instanceof LList) {
                AIter i = (AIter) list;
                list = null; // give it free for gc
                Num v;
                for (v = IntNum._0; i != null; i = i.next()) {
                    v = v.add((Num) i.first());
                }
                return v;
            }
            return ((AList) list).fold(this, null, IntNum._0);
        }

        public Object apply(Object a, Object b, Fun f) {
            return ((Num) a).add((Num) b);
        }
    }

    private static final class Find extends Fun2 {
        Object apply2(Object f, Object list) {
            if (list == null) {
                return null;
            }
            return ((AList) list).find((Fun) f);
        }
    }

    private static final class Index extends Fun2 {
        Object apply2(Object v, Object list) {
            if (list == null) {
                return IntNum.__1;
            }
            Num n = ((AList) list).index(v);
            return n == null ? IntNum.__1 : n;
        }
    }

    private static final class Empty extends Fun {
        public Object apply(Object v) {
            AMList m;
            return v == null || v instanceof AMList &&
                    (m = (AMList) v)._size() <= m.start
                ? Boolean.TRUE : Boolean.FALSE;
        }
    }

    private static final class Replace extends Fun {
        public Object apply(Object find) {
            final String f = (String) find;
            return new Fun2() {
                Object apply2(Object replace, Object text) {
                    StringBuffer result = new StringBuffer();
                    String s = (String) text;
                    String r = (String) replace;
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
            };
        }
    }

    public static final Fun HEAD = new Head();
    public static final Fun TAIL = new Tail();
    public static final Fun FOR  = new For();
    public static final Fun FORHASH = new ForHash();
    public static final Fun MAP  = new Map_();
    public static final Fun MAP2 = new Map2();
    public static final Fun MAPHASH = new MapHash();
    public static final Fun FOLD = new Fold();
    public static final Fun SUM  = new Sum();
    public static final Fun FIND = new Find();
    public static final Fun INDEX = new Index();
    public static final Fun EMPTY = new Empty();
    public static final Fun REPLACE = new Replace();

    private static synchronized void initRandom() {
        if (rnd == null) {
            rnd = new Random();
        }
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
