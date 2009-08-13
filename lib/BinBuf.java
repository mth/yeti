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
        byte[] buf = new byte[limit > 0 && 8192 > limit ? limit : 8192];
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
