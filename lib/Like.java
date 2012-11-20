// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - function interface.
 *
 * Copyright (c) 2008 Madis Janson
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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

final class LikeMatcher extends Fun {
    private final Matcher m;

    LikeMatcher(Matcher m) {
        this.m = m;
    }

    public Object apply(Object _) {
        if (!m.find()) {
            return new MList();
        }
        Object[] r = new Object[m.groupCount() + 1];
        for (int i = r.length; --i >= 0;) {
            String s;
            if ((s = m.group(i)) == null)
                s = Core.UNDEF_STR;
            r[i] = s;
        }
        return new MList(r);
    }
}

public final class Like extends Fun {
    private Pattern p;

    public Like(Object pattern) {
        p = Pattern.compile((String) pattern, Pattern.DOTALL);
    }

    public Object apply(Object v) {
        return new LikeMatcher(p.matcher((CharSequence) v));
    }

    public String toString() {
        return "<like " + Core.show(p.pattern()) + ">";
    }
}
