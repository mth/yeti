class TreeMap extends AStruct {
    private static final String[] NAMES =
        {"add", "contains", "find", "iter", "remove"};
    private final int height;
    private final TreeMap left;
    private final TreeMap right;
    private final Object key;
    private final Object value;
    private final Fun less;

    private TreeMap(TreeMap l, TreeMap r, Object k, Object v, Fun cmp) {
        super(NAMES, null);
        left = l;
        right = r;
        key = k;
        value = v;
        height = l == null ? 0 :
            (l.height > r.height ? l.height : r.height) + 1;
        less = cmp;
    }

    private static TreeMap balance(TreeMap left, TreeMap right,
                                   Object k, Object v, Fun cmp) {
        if (left.height > right.height + 2) {
            TreeMap l = left.left, r = left.right;
            if (l.height >= r.height)
                return new TreeMap(l, new TreeMap(r, right, k, v, cmp),
                                   left.key, left.value, cmp);
            return new TreeMap(
                    new TreeMap(l, r.left, left.key, left.value, cmp),
                    new TreeMap(r.right, right, k, v, cmp),
                    r.key, r.value, cmp);
        }
        if (right.height > left.height + 2) {
            TreeMap l = right.left, r = right.right;
            if (r.height >= l.height)
                return new TreeMap(new TreeMap(left, l, k, v, cmp),
                                   r, right.key, right.value, cmp);
            return new TreeMap(
                    new TreeMap(left, l.left, k, v, cmp),
                    new TreeMap(l.right, r, right.key, right.value, cmp),
                    l.key, l.value, cmp);
        }
        return new TreeMap(left, right, k, v, cmp);
    }

    private static TreeMap add(TreeMap t, Object k, Object v) {
        if (t.height <= 0)
            return new TreeMap(t, t, k, v);
        int c = ((Number) t.cmp.apply(k, t.key)).intValue();
        if (c == 0)
            return new TreeMap(t.left, t.right, k, v, t.lt);
        if (c < 0)
            return balance(add(t.left, k, v), t.right, t.key, t.value, t.lt);
        return balance(t.left, add(t.right, k, v), t.key, t.value, t.lt);
    }

    Object get(String field) {
        
    }

    Object get(int field) {
        switch (field) {
        case 0: // add
            return new Fun2() {
                Object apply(Object k, Object v) {
                    return add(TreeMap.this, k, v);
                }
            };
        }
        return null;
    }
}
