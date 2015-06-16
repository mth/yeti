package yeti.lang;

import yeti.lang.Hash;
import java.util.Iterator;
import java.util.Map.Entry;

class JSONObj {
    Hash map;

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
        s[n] = "}";
        return Core.concat(s);
    }
}
