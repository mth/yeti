// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
 * Copyright (c) 2007,2008,2009 Madis Janson
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

import yeti.lang.Core;

public class CompileException extends RuntimeException {
    String fn;
    int line;
    int col;
    String what;

    static String format(YType param1, YType param2,
                         String s, TypeException ex, Scope scope) {
        StringBuffer result = new StringBuffer();
        int p = 0, i;
        boolean msg = false;
        while ((i = s.indexOf('#', p)) >= 0 && i < s.length() - 1) {
            result.append(s.substring(p, i));
            p = i + 2;
            switch (s.charAt(i + 1)) {
                case '0':
                    result.append(ex.getMessage(scope));
                    msg = true;
                    break;
                case '1': result.append(param1.toString(scope, ex)); break;
                case '2': result.append(param2.toString(scope, ex)); break;
                case '~':
                    result.append(param1.toString(scope, ex));
                    result.append(" is not ");
                    result.append(param2.toString(scope, ex));
                    break;
                default:
                    result.append('#');
                    --p;
            }
        }
        result.append(s.substring(p));
        if (!msg && ex != null && ex.special) {
            result.append(" (");
            result.append(ex.getMessage(scope));
            result.append(")");
        }
        return result.toString();
    }

    public CompileException(int line, int col, String what) {
        this.line = line;
        this.col = col;
        this.what = what;
    }

    public CompileException(YetiParser.Node pos,
                            JavaClassNotFoundException ex) {
        this(ex, pos, "Unknown class: " + ex.getMessage());
    }

    public CompileException(YetiParser.Node pos, String what) {
        this(null, pos, what);
    }

    private CompileException(Throwable ex, YetiParser.Node pos, String what) {
        super(ex);
        if (pos != null) {
            line = pos.line;
            col = pos.col;
        }
        this.what = what;
    }

    public CompileException(YetiParser.Node pos, Scope scope,
                            YType param1, YType param2, String what,
                            TypeException ex) {
        this(ex, pos, format(param1, param2, what, ex, scope));
    }

    public String getMessage() {
        return (fn == null ? "" : fn + ":") +
               (line == 0 ? "" : line + (col > 0 ? ":" + col + ": " : ": ")) +
               what;
    }
}
