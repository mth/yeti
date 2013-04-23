// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2007,2008,2009 Madis Janson
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

import yeti.lang.*;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Arrays;
import java.io.Serializable;

class SubMJUList extends AbstractList {
    private final List impl;
    private final Fun toYeti;
    private final Fun toJava;

    public SubMJUList(List impl, Fun toYeti, Fun toJava) {
        this.impl = impl;
        this.toYeti = toYeti;
        this.toJava = toJava;
    }

    public Object get(int index) {
        return toYeti.apply(impl.get(index));
    }

    public int size() {
        return impl.size();
    }

    public Object set(int index, Object value) {
        return toYeti.apply(impl.set(index,toJava.apply(value)));
    }

    public void add(int index, Object value) {
        impl.add(index, toJava.apply(value));
    }

    public Object remove(int index) {
        return toYeti.apply(impl.remove(index));
    }

}


public class MJUList extends MList {
    public static final Fun ID = new Fun() {
        public Object apply(Object arg) {
            return arg;
        }
    };

    private final Fun copyF;
    private final List impl;


    public MJUList(List impl, Fun copy) {
        this.impl = impl;
        this.copyF = copy;
    }
    private List copyImpl() {
        return (List) copyF.apply(impl);
    }

    public final long length() {
        return impl.size();
    }

    public Object copy() {
        return new MJUList(copyImpl(),copyF);
    }

    public int hashCode() {
        return impl.hashCode();
    }

    public boolean equals(Object obj) {
        if(obj == null)
            return impl.size() == 0;
        if(obj instanceof MJUList) 
            return impl.equals(((MJUList) obj).impl);

        if (!(obj instanceof AList))
            return false;
        
        AIter yi = (AList) obj;
        Iterator ji = impl.iterator();
        while(ji.hasNext()) {
            if(yi == null)
                return false;
            Object n = ji.next();
            if(n == null && yi.first() != null)
                return false;
            if(!n.equals(yi.first()))
                return false;
            yi = yi.next();
        }
        return yi == null;
    }

    public String toString() {
        Iterator it = impl.iterator();
        StringBuffer buf = new StringBuffer();
        buf.append("[");
        boolean first = true;
        while(it.hasNext()){
            if(first)
                first = false;
            else
                buf.append(", ");
            buf.append(Core.show(it.next()));
        }
        buf.append("]");
        return buf.toString();    
    }

    public void forEach(Object fun) {
        Iterator it = impl.iterator();
        while(it.hasNext())
            ((Fun) fun).apply(it.next());
    }

    public Object fold(Fun f, Object v) {
        Iterator it = impl.iterator();
        while(it.hasNext()){
            v = f.apply(v,it.next());
        }
        return v;
    }

    public Num index(Object v) {
        int i = impl.indexOf(v);
        if(i < 0)
            return null;
        else
            return new IntNum(i);
    }

    public AList map(Fun f) {
        return smap(f);
    }

    public AList smap(Fun f) {
        return new MJUList(
            new SubMJUList(copyImpl(),f,ID),copyF);
    }

    public int compareTo(Object obj) {
        Iterator ji = impl.iterator();
        if(obj instanceof MJUList){
            MJUList o = (MJUList) obj;
            Iterator i2 = ((MJUList)obj).impl.iterator();
            while(ji.hasNext()) {
                if(!i2.hasNext())
                    return -1;
                Object n = (ji.next());
                Object n2 = (i2.next());
                if(n == null && n2 != null)
                    return -1;
                int r = ((Comparable)n).compareTo(n2);
                if (r != 0)
                    return r;
            }
            if(i2.hasNext())
                return 1;
            else
                return 0;
        }
        if (obj instanceof AIter) {
            AIter yi = (AIter) obj;
            while(ji.hasNext()) {
                if(yi == null)
                    return -1;
                Object n = (ji.next());
                if(n == null && yi.first() != null)
                    return -1;
                int r = ((Comparable) n).compareTo(yi.first()); 
                if (r != 0)
                    return r;
                yi = yi.next();
            }
            if(yi == null) 
                return 0;
            else
                return 1;
        }

        return 0;
    }

    public AList reverse() {
        List ret = copyImpl();
        Collections.reverse(ret);
        return new MJUList(ret,copyF);
    }

    public AList sort() {
        List ret = copyImpl();
        Collections.sort(ret);
        return new MJUList(ret,copyF);
    }

    public AList sort(final Fun isLess) {
        Comparator comp = new Comparator() {
            public int compare(Object f1, Object f2) {
                if (isLess.apply(f1,f2) == Boolean.TRUE)
                    return -1;
                else
                    return 1;
            }
        };

        List ret = copyImpl();
        Collections.sort(ret, comp);
        return new MJUList(ret,copyF);
    }

    public void reserve(int n) {}

    public void add(Object o) {
        impl.add(o);
    }

    public Object shift() {
        return impl.remove(0);
    }

    public Object pop() {
        return impl.remove(impl.size() - 1);
    }

    public void clear() {
        impl.clear();
    }

    public Object first() {
        return impl.get(0);
    }

    private static final class ItFun extends Fun {
        private final Iterator it;
        private int count;
        ItFun(Iterator it, int count) {
            this.it = it;
            this.count = count;
        }

        public Object apply(Object ob) {
            if(it.hasNext()) {
                if(count != 0) {
                    count = count - 1;
                    return new LazyList(it.next(),this);
                }else{
                    return null;
                }
            }else{
                return null;
            }
        }
    }
    public AList rest() {
        if(impl.size() < 2)
            return null;
        return (AList) (new ItFun(impl.listIterator(1), -1)).apply(null);
    }

    private final class Iter extends AIter implements Serializable {
        int i = 1;
        public Object first() {
            return impl.get(i);
        }

        public AIter next() {
            return ++i < impl.size() ? this : null;
        }

        public boolean isEmpty() {
            return i >= impl.size();
        }
    }

    public AIter next() {
        if(impl.size() < 2) 
            return null;
        return new Iter();
    }

    public boolean containsKey(Object index) {
        int i;
        return (i = ((Number) index).intValue()) >= 0 && i < impl.size();
    }

    public Object get(int i) {
        return impl.get(i);
    }

    public Object vget(Object index) {
        return get(((Number)index).intValue());
    }

    public Object put(Object index, Object value) {
        impl.add(((Number)index).intValue(), value);
        return null;
    }

    public Object remove(Object index) {
        impl.remove(((Number) index).intValue());
        return null;
    }
    

    public void removeAll(AList keys) {
        if(keys == null || keys.isEmpty())
            return;
        Set ts = new java.util.TreeSet(new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Number)o2).intValue() - ((Number)o1).intValue();
            }
        });
        while(keys != null) 
            ts.add(keys.first());
        Iterator it = ts.iterator();
        while(it.hasNext()) 
            impl.remove(((Number)it.next()).intValue());
    }

    public MList copy(int from, int to) {
        return new MJUList(impl.subList(from, to),copyF);
    }

    public boolean isEmpty() {
        return impl.isEmpty();
    }

    public AList take(int from, int count) {
        if (from < 0)
            from = 0;
        if((count + from) > impl.size())
            count = -1;
        
        return (AList) (new ItFun(impl.listIterator(from), count)).apply(null);
    }

    public void setDefault(Fun fun) {
        throw new UnsupportedOperationException();
    }



}

