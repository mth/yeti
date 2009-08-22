// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2009 Madis Janson
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

class BinBuf extends Fun {
    private final byte[] buf;
    private final IntNum len;

    BinBuf(byte[] _buf, int _len) {
        buf = _buf;
        len = new IntNum(_len);
    }

    public Object apply(Object cb) {
        return ((Fun) cb).apply(buf, len);
    }

    static Fun readAll(int limit, Fun read, Fun close) {
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
        return new BinBuf(buf, l);
    }
}
