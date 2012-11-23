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

/** Yeti core library - takeWhile list. */
final class TakeWhile extends LList {
    private AList rest;
    private AIter src;
    private Fun pred;

    private TakeWhile(Object first, AIter src, Fun pred) {
        super(first, null);
        this.src = src;
        this.pred = pred;
    }

    static AList take(AIter src, Fun f) {
        Object v = src.first();
        return f.apply(v) == Boolean.TRUE ? new TakeWhile(v, src, f) : null;
    }

    public synchronized AList rest() {
        if (pred != null) {
            AIter i = src.next();
            src = null;
            if (i != null)
                rest = take(i, pred);
            pred = null;
        }
        return rest;
    }
}
