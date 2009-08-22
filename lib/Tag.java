// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - Variant tag.
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

import java.io.Serializable;

public final class Tag implements Comparable, Serializable {
    public final String name;
    public final Object value;

    public Tag(Object aValue, String aName) {
        name = aName;
        value = aValue;
    }

    public int hashCode() {
        return name.hashCode() - (value == null ? 0 : value.hashCode() * 17);
    }

    public boolean equals(Object other) {
        Tag o;
        return other instanceof Tag && name == (o = (Tag) other).name &&
               (value == o.value || value != null && value.equals(o.value));
    }

    public int compareTo(Object other) {
        Tag o = (Tag) other;
        return name == o.name ? ((Comparable) value).compareTo(o.value)
                              : name.compareTo(o.name);
    }

    public String toString() {
        return name + ' ' + Core.show(value);
    }
}
