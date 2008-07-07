package yeti.lang;

class Lazy extends Fun {
    private Object value;
    private boolean undef;
    private Fun f;

    Lazy(Fun f) {
        this.f = f;
    }

    public Object apply(Object _) {
        if (undef) {
            value = ((Fun) f).apply(null);
            undef = false;
        }
        return value;
    }
}
