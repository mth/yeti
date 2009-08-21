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

public class CompileException extends RuntimeException {
    String fn;
    int line;
    int col;
    String what;

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

    public CompileException(YetiParser.Node pos, String what,
                            YetiType.TypeException ex) {
        this(ex, pos, ex.special ? what + " (" + ex.getMessage() + ")" : what);
    }

    public String getMessage() {
        return (fn == null ? "" : fn + ":") +
               (line == 0 ? "" : line + (col > 0 ? ":" + col + ": " : ": ")) +
               what;
    }
}
