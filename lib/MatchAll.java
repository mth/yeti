// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti core library - matchAll.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MatchAllFun extends Fun {
    final Pattern pattern;
    final Fun matchFun;
    final Fun skipFun;

    final class Match extends LList {
        private AList rest;
        private final int last;
        private final String str;
        private Matcher m;

        Match(Object v, int last_, String str_, Matcher m_) {
            super(v, null);
            last = last_;
            str = str_;
            m = m_;
        }

        public synchronized AList rest() {
            if (m != null) {
                rest = get(str, m, last);
                m = null;
            }
            return rest;
        }
    }

    MatchAllFun(Pattern pattern_, Fun matchFun_, Fun skipFun_) {
        pattern = pattern_;
        matchFun = matchFun_;
        skipFun = skipFun_;
    }

    AList get(String s, Matcher m, int last) {
        if (!m.find()) {
            return (s = s.substring(last)).length() == 0
                    ? null : new LList(skipFun.apply(s), null);
        }
        int st = m.start();
        Object skip = last >= st ? null :
            skipFun.apply(s.substring(last, st));
        Object[] r = new Object[m.groupCount() + 1];
        for (int i = r.length; --i >= 0;) {
            String g;
            if ((g = m.group(i)) == null)
                g = Core.UNDEF_STR;
            r[i] = g;
        }
        Match l = new Match(matchFun.apply(new MList(r)), m.end(), s, m);
        return last < st ? new LList(skip, l) : l;
    }

    public Object apply(Object str) {
        String s = (String) str;
        return get(s, pattern.matcher(s), 0);
    }
}

final public class MatchAll extends Fun2 {
    private final Pattern p;

    public MatchAll(Object pattern) {
        p = Pattern.compile((String) pattern, Pattern.DOTALL);
    }

    public Object apply(Object matchFun, Object skipFun) {
        return new MatchAllFun(p, (Fun) matchFun, (Fun) skipFun);
    }
}
