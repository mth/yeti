// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library.
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

package yeti.lang;

public final class Core {
    private static final int DEC_SHIFT[] = { 1, 10, 100, 1000, 10000,
        100000, 1000000, 10000000, 100000000, 1000000000 };

    public static final Fun PRINTLN = new Fun() {
        public Object apply(Object x) {
            System.out.println(x);
            return null;
        }
    };

    public static final Fun ARRAY = new Fun() {
        public Object apply(Object x) {
            return new MList((AIter) x);
        }
    };

    public static Num parseNum(String str) {
        String s = str.trim();
        int l;
        if ((l = str.length()) == 0) {
            throw new IllegalArgumentException("Number expected");
        }
        if (s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
            return new FloatNum(Double.parseDouble(s));
        }
        char c;
        if ((c = s.charAt(l - 1)) == 'f' || c == 'F') {
            return new FloatNum(Double.parseDouble(s.substring(0, l - 1)));
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
}
