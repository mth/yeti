package yeti.lang;

class Lazy extends Fun {
    private Object value;
    private Fun f;

    Lazy(Fun f) {
        this.f = f;
    }

    public Object apply(Object _) {
        if (f != null) {
            value = ((Fun) f).apply(null);
            f = null;
        }
        return value;
    }
}
