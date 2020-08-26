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

import java.io.OutputStream;

/** Yeti core library - iterable list. */
public abstract class AIter {
    /** Return iterators current first. */
    public abstract Object first();

    /**
     * Return next iterator or null.
     * May well modify itself and return this.
     */
    public abstract AIter next();

    /**
     * Gives iterator instance that can be used independently.
     * Default implementaion returns this, which is fine for immutable iterators,
     * where next doesn't modify the instance state.
     *
     * @return iterator that doesn't share state with this iterator
     */
    public AIter dup() {
        return this;
    }

    /**
     * Returns true if this is empty iterator (no first() value).
     * Empty {@link MList} is an example of empty iterator.
     * NB! The {@link #next()} method should always return null at the end.
     */
    public boolean isEmpty() {
        return false;
    }

    AIter write(OutputStream stream) throws java.io.IOException {
        stream.write(((Number) first()).intValue());
        return next();
    }
}
