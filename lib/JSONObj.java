package yeti;

import yeti.lang.Core;
import yeti.lang.Hash;
import java.util.Iterator;
import java.util.Map.Entry;

class JSONObj {
    Hash map;

    JSONObj(Hash map) {
        this.map = map;
    }

    public int hashCode() {
        return map.hashCode();
    }

    public boolean equals(Object o) {
        return o instanceof JSONObj && map.equals(((JSONObj) o).map);
    }

    public String toString() {
        int n;
        String[] s = new String[map.size() * 4 + 1];
        Iterator i = map.entrySet().iterator();
        for (n = 0; i.hasNext(); n += 4) {
            Entry e = (Entry) i.next();
            s[n] = n == 0 ? "{" : ",";
            s[n + 1] = Core.show(e.getKey());
            s[n + 2] = ":";
            Object v = e.getValue();
            s[n + 3] = v == null ? "null" : Core.show(v);
        }
        if (n == 0) {
            return "{}";
        }
        s[n] = "}";
        return Core.concat(s);
    }
}
