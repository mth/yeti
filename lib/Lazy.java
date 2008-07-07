package yeti.lang;

class Lazy extends Fun {
    private Object value;
    private boolean forced;
    private Fun f;

    Lazy(Fun f) {
        this.f = f;
    }

    public Object apply(Object _) {
        if (!forced) {
            value = ((Fun) f).apply(null);
            forced = true;
        }
        return value;
    }
}
