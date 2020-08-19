// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti core library.
 *
 * Copyright (c) 2020 Madis Janson
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
package yeti;

import java.text.ParseException;
import yeti.lang.Core;
import yeti.lang.Hash;
import yeti.lang.MList;

final class JSONParser {
    private static final int ARRAY_FST = 1;
    private static final int ARRAY     = 2;
    private static final int ARRAY_SEP = 3;
    private static final int OBJ_FST   = 4;
    private static final int OBJ       = 5;
    private static final int PAIR      = 6;
    private static final int OBJ_SEP   = 7;
    private final JSONParser prev;
    private final MList result;
    private final int state;

    private JSONParser(JSONParser prev, MList result, int state) {
        this.prev = prev;
        this.result = result;
        this.state = state;
    }

    static Object parse(char[] data, int pos, int end) throws ParseException {
        JSONParser stack = new JSONParser(null, new MList(), 0);
        stack.result.reserve(1);
        pos = parse(data, pos, end, stack);
        for (; pos >= 0 && pos < end && data[pos] <= ' '; ++pos);
        if (pos != end) {
            pos = Math.abs(pos);
            throw new ParseException("Parse error at " + pos, pos);
        }
        return stack.result.length() <= 0 ? null : stack.result.get(0);
    }

    private static int parse(char[] data, int pos, int end, JSONParser stack) {
        MList array = null;
        int state = 0;
        for (;;) {
            for (; pos < end && data[pos] <= ' '; ++pos);
            if (pos >= end) {
                return -pos;
            }
            char c = data[pos];
            ++pos;
        pop:
            switch (state) {
            case 0:
                switch (c) {
                case '[':
                    state = ARRAY_FST;
                    array = new MList();
                    stack.result.add(array);
                    continue;
                case '{':
                    state = OBJ_FST;
                    array = new MList();
                    continue;
                case '"':
                    StringBuilder buf = null;
                    for (int ss = pos; pos < end; ++pos) {
                        switch (data[pos]) {
                        case '"':
                            String s = new String(data, ss, pos - ss);
                            stack.result.add(buf == null ? s : buf.append(s).toString());
                            ++pos;
                            break pop;
                        case '\\':
                            if (buf == null) {
                                buf = new StringBuilder();
                            }
                            buf.append(new String(data, ss, pos - ss));
                            if (++pos < end) {
                                ss = pos + 1;
                                switch (data[pos]) {
                                case '/':
                                case '\\':
                                case '"':
                                    ss = pos;
                                    continue;
                                case 'b':
                                    buf.append('\b');
                                    continue;
                                case 'f':
                                    buf.append('\f');
                                    continue;
                                case 'n':
                                    buf.append('\n');
                                    continue;
                                case 'r':
                                    buf.append('\r');
                                    continue;
                                case 't':
                                    buf.append('\t');
                                    continue;
                                case 'u':
                                    try {
                                        buf.append(Character.toChars(Integer.parseInt(
                                                    new String(data, pos + 1, 4), 16)));
                                    } catch (Exception ex) {
                                        return -pos;
                                    }
                                    pos += 4;
                                    ss = pos + 1;
                                    continue;
                                }
                            }
                            return -pos;
                        }
                    }
                    return -pos;
                default:
                    int ss = --pos;
                    for (; pos < end; ++pos) {
                        c = data[pos];
                        if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' 
                                || c >= 'a' && c <= 'z' || c == '.'
                                || c == '+' || c == '-')) {
                            break;
                        }
                    }
                    String s = new String(data, ss, pos - ss);
                    if (s.equals("null")) {
                        stack.result.add(null);
                    } else if (s.equals("false")) {
                        stack.result.add(Boolean.FALSE);
                    } else if (s.equals("true")) {
                        stack.result.add(Boolean.TRUE);
                    } else {
                        try {
                            stack.result.add(Core.parseNum(s));
                        } catch (Exception ex) {
                            return -pos;
                        }
                    }
                }
                break;
            case ARRAY_FST:
                if (c == ']') {
                    break;
                }
            case ARRAY:
                stack = new JSONParser(stack, array, ARRAY_SEP);
                state = 0;
                --pos;
                continue;
            case ARRAY_SEP:
                if (c == ',') {
                    state = ARRAY;
                    continue;
                } else if (c != ']') {
                    return -pos;
                }
                break;
            case OBJ_FST:
                if (c == '}') {
                    stack.result.add(new JSONObj(new Hash()));
                    break;
                }
            case OBJ:
                if (c != '"') {
                    return -pos;
                }
                stack = new JSONParser(stack, array, PAIR);
                state = 0;
                --pos;
                continue;
            case PAIR:
                if (c != ':') {
                    return -pos;
                }
                stack = new JSONParser(stack, array, OBJ_SEP);
                state = 0;
                continue;
            case OBJ_SEP:
                if (c == ',') {
                    state = OBJ;
                    continue;
                } else if (c != '}') {
                    return -pos;
                }
                int len = (int) array.length();
                Hash map = new Hash(len * 3 / 2 + 1);
                for (int i = 0; i < len; i += 2) {
                    map.put(array.get(i), array.get(i + 1));
                }
                stack.result.add(new JSONObj(map));
            }
            if (stack.prev == null) {
                return pos;
            }
            array = stack.result;
            state = stack.state;
            stack = stack.prev;
        }
    }
}
