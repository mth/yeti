// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2007-2013 Madis Janson
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

import java.io.IOException;
import java.io.OutputStream;

/** Yeti core library - Concat list. */
final class ConcatLists extends LList {
    private AList rest;
    private AIter src;  // current list?<'a>
    private AIter tail; // list<list?<'a>>

    public ConcatLists(AIter src, AIter rest) {
        super(src.first(), null);
        this.src = src;
        this.tail = rest;
    }

    public synchronized AList rest() {
        if (src != null) {
            AIter i = src.next();
            src = null;
            // current done? -> rest is concatenation of tail list of lists
            //  more current -> rest contains the current
            rest = i == null ? concat(tail) : new ConcatLists(i, tail);
            tail = null;
        }
        return rest;
    }

    synchronized AIter write(OutputStream out) throws IOException {
        if (src == null)
            return super.write(out);
        AIter i = src.dup();
        while (i != null)
            i = i.write(out);
        if (tail != null) {
            AIter lists = tail.dup();
            do {
                i = (AIter) lists.first();
                while (i != null)
                    i = i.write(out);
                lists = lists.next();
            } while (lists != null);
        }
        return null;
    }

    // src is list<list?<'a>>
    public static AList concat(AIter src) {
        // find first non-empty list in the src list of lists
        while (src != null) {
            AList h = (AList) src.first();
            src = src.next();
            // If found make concat-list mirroring it,
            // with tail src to use when it's finished.
            if (h != null && !h.isEmpty())
                return src == null ? h : new ConcatLists(h, src);
        }
        return null; // no, all empty
    }
}
