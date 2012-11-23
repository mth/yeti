// ex: se sts=4 sw=4 expandtab:

/*
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

import java.io.BufferedReader;
import java.io.IOException;

/** Yeti core library - BufferedReader list. */
final class ReaderList extends LList {
    private AList rest;
    private boolean forced;
    private final BufferedReader r;

    private ReaderList(String line, BufferedReader r) {
        super(line, null);
        this.r = r;
    }

    public AList rest() {
        synchronized (r) {
            if (!forced) {
                rest = lines(r);
                forced = true;
            }
            return rest;
        }
    }

    static AList lines(BufferedReader r) {
        try {
            String line = null;
            try {
                if ((line = r.readLine()) == null) {
                    return null;
                }
                return new ReaderList(line, r);
            } finally {
                if (line == null) {
                    r.close();
                }
            }
        } catch (IOException ex) {
            Unsafe.unsafeThrow(ex);
            return null;
        }
    }
}
