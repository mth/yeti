// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti language parser.
 *
 * Copyright (c) 2007 Madis Janson
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

/*
Syntax.

binding: foo a b c... = <expr>
expr: <application> | <constant>
application: <expr> <expr>
constant: "blaah" | 1.23 | ()
struct: '{' { binding } '}'
*/

package yeti.lang.compiler;

import java.util.List;
import java.util.ArrayList;
import yeti.lang.Core;
import yeti.lang.Num;

interface YetiParser {
    class Node {
        String show() {
            return str();
        }

        String str() {
            return toString();
        }

        public String toString() {
            return getClass().getName();
        }

        String showList(char open, char close, Node[] list) {
            StringBuffer res = new StringBuffer();
            res.append(open);
            for (int i = 0; i < list.length; ++i) {
                if (i != 0) {
                    res.append("; ");
                }
                res.append(list[i].show());
            }
            res.append(close);
            return res.toString();
        }
    }

    class BindOp extends Node {
    }

    class SeqOp extends Node {
    }

    class VarSym extends Node {
    }

    class Bind extends Node {
        String name;
        Node expr;
        boolean var;

        Bind(List args, Node expr) {
            int first = 1;
            Node nameNode = (Node) args.get(0);
            if (nameNode instanceof VarSym) {
                if (args.size() == 1) {
                    throw new RuntimeException("missing variable name");
                }
                nameNode = (Node) args.get(1);
                first = 2;
                var = true;
            }
            if (!(nameNode instanceof Sym)) {
                throw new RuntimeException("illegal binding name: " + nameNode);
            }
            this.name = ((Sym) nameNode).sym;
            for (int i = args.size(); --i >= first;) {
                expr = new Lambda((Node) args.get(i), expr,
                                  i == first ? name : null);
            }
            this.expr = expr;
        }

        String str() {
            return name + " = " + expr.show();
        }
    }

    class Lambda extends Node {
        Node arg;
        Node expr;
        String bindName;

        Lambda(Node arg, Node expr, String name) {
            this.arg = arg;
            this.expr = expr;
            this.bindName = name;
        }

        String str() {
            return "\\" + arg.show() + " -> " + expr.show();
        }
    }

    class Seq extends Node {
        Node[] st;

        Seq(Node[] st) {
            this.st = st;
        }

        String str() {
            return showList('(', ')', st);
        }
    }

    class Struct extends Node {
        Node[] fields;

        Struct(Node[] fields) {
            this.fields = fields;
        }

        String str() {
            return showList('{', '}', fields);
        }
    }

    class NList extends Node {
        Node[] items;

        NList(Node[] items) {
            this.items = items;
        }

        String str() {
            return showList('[', ']', items);
        }
    }

    class Condition extends Node {
        Node[][] choices;

        Condition(Node[][] choices) {
            this.choices = choices;
        }

        String str() {
            StringBuffer buf = new StringBuffer();
            buf.append("\nif ");
            for (int i = 0; i < choices.length; ++i) {
                Node[] choice = choices[i];
                if (choice.length >= 2) {
                    if (i != 0) {
                        buf.append("\nelif ");
                    }
                    buf.append(choice[1].show());
                    buf.append(" then\n  ");
                } else {
                    buf.append("\nelse\n  ");
                }
                buf.append(choice[0].show());
            }
            buf.append("\nfi\n");
            return buf.toString();
        }
    }

    class Case extends Node {
        Node value;
        Node[] choices;
        
        Case(Node value, Node[] choices) {
            this.value = value;
            this.choices = choices;
        }

        String str() {
            StringBuffer buf = new StringBuffer();
            buf.append("\ncase ");
            buf.append(value.show());
            buf.append(" of");
            for (int i = 0; i < choices.length; ++i) {
                buf.append("\n ");
                buf.append(choices[i].show());
            }
            buf.append("\nesac\n");
            return buf.toString();
        }
    }

    class Sym extends Node {
        String sym;

        Sym(String sym) {
            this.sym = sym;
        }

        String str() {
            return sym;
        }
    }

    class Str extends Node {
        String str;

        Str(String str) {
            this.str = str;
        }

        String show() {
            return '"' + str + '"';
        }
    }

    class NumLit extends Node {
        Num num;

        NumLit(Num num) {
            this.num = num;
        }

        String show() {
            return String.valueOf(num);
        }
    }

    class Eof extends Node {
    }

    class Elif extends Eof {
    }

    class Else extends Eof {
    }

    class Fi extends Eof {
    }

    class Esac extends Eof {
    }

    class Done extends Eof {
    }

    class BinOp extends Node {
        int prio;
        String op;
        boolean toRight;
        Node left;
        Node right;
        BinOp parent;

        BinOp(String op, int prio, boolean toRight) {
            this.op = op;
            this.prio = prio;
            this.toRight = toRight;
        }

        String str() {
            return '(' + (left == null ? "<>" : left.show()) + ' ' + op + ' '
                       + (right == null ? "<>" : right.show()) + ')';
        }
    }

    class ParseExpr {
        private boolean lastOp = true;
        private BinOp root = new BinOp(null, -1, false);
        private BinOp cur = root;

        private void addOp(BinOp op) {
            BinOp to = cur;
            if (op.op == "-" && lastOp || op.op == "\\") {
                if (!lastOp) {
                    addOp(new BinOp("", 2, true));
                    to = cur;
                }
                op.prio = 1;
                to.left = to.right;
            } else if (lastOp) {
                throw new RuntimeException("Do not stack operators");
            } else {
                while (to.parent != null && (to.prio < op.prio
                                        || to.prio == op.prio && op.toRight)) {
                    to = to.parent;
                }
                op.right = to.right;
            }
            op.parent = to;
            to.right = op;
            cur = op;
            lastOp = true;
        }

        void add(Node node) {
            if (node instanceof BinOp && ((BinOp) node).parent == null) {
                addOp((BinOp) node);
            } else {
                if (!lastOp) {
                    addOp(new BinOp("", 2, true));
                }
                lastOp = false;
                cur.left = cur.right;
                cur.right = node;
            }
        }

        Node result() {
            if (cur.left == null && cur.prio != -1 && cur.prio != 1 ||
                cur.right == null) {
                throw new RuntimeException("Expecting some value");
            }
            return root.right;
        }
    }

    class Parser {
        private static final String[][] OPS = {
            { "*", "/" },
            { "+", "-" },
            { "::" },
            { "<", ">", "<=", ">=", "==", "!=" },
            { "and", "or" },
            { ":=" },
            { ":" }
        };
        private static final Eof EOF = new Eof();
        private char[] src;
        private int p;
        private Node eofWas;
        private int flags;
        private String sourceName;

        Parser(String sourceName, char[] src, int flags) {
            this.sourceName = sourceName;
            this.src = src;
            this.flags = flags;
        }

        private Node fetch() {
            char[] src = this.src;
            int i = p;
            while (i < src.length && src[i] >= '\000' && src[i] <= ' ') {
                ++i;
            }
            if (i >= src.length || src[i] == ')'
                || src[i] == ']' || src[i] == '}') {
                p = i;
                return EOF;
            }
            p = i + 1;
            switch (src[i]) {
                case '.':
                    return new BinOp(".", 0, true);
                case '=':
                    if (p < src.length && src[p] > ' ') {
                        break;
                    }
                    return new BindOp();
                case ';':
                    return new SeqOp();
                case '(':
                    return readSeq(')');
                case '[':
                    return new NList(readMany(']'));
                case '{':
                    return new Struct(readMany('}'));
                case '"':
                    return readStr();
                case '\\':
                    return new BinOp("\\", 1, false);
            }
            p = i;
            char c;
            if ((c = src[i]) >= '0' && c <= '9') {
                while (++i < src.length && (c = src[i]) != '(' && c != ')' &&
                       c != '[' && c != ']' && c != '{' && c != '}' &&
                       c != ':' && c != ';' && c > ' ');
                String s = new String(src, p, i - p);
                p = i;
                return new NumLit(Core.parseNum(s));
            }
            while (++i < src.length && (c = src[i]) != '(' && c != ')' &&
                   c != ';' && c > ' ' && c != '[' && c != ']' &&
                   c != '{' && c != '}' && c != '.' &&
                   (c != ':' || i + 1 < src.length && src[i + 1] > ' '
                             || i > 0 && src[i - 1] == ':'));
            String s = new String(src, p, i - p);
            p = i;
            s = s.intern(); // Sym's are expected to have interned strings
            if (s == "if") {
                return readIf();
            }
            if (s == "elif") {
                return new Elif();
            }
            if (s == "else") {
                return new Else();
            }
            if (s == "fi") {
                return new Fi();
            }
            if (s == "case") {
                return readCase();
            }
            if (s == "esac") {
                return new Esac();
            }
            if (s == "do") {
                return readDo();
            }
            if (s == "done") {
                return new Done();
            }
            if (s == "var") {
                return new VarSym();
            }
            for (i = OPS.length; --i >= 0;) {
                for (int j = OPS[i].length; --j >= 0;) {
                    if (OPS[i][j] == s) {
                        return new BinOp(s, i + 3, s != "::");
                    }
                }
            }
            return new Sym(s);
        }

        private Node def(List args, List expr) {
            ParseExpr parseExpr = new ParseExpr();
            for (int i = 0, cnt = expr.size(); i < cnt; ++i) {
                parseExpr.add((Node) expr.get(i));
            }
            Node e = parseExpr.result();
            if (args == null) {
                return e;
            }
            return new Bind(args, e);
        }

        private Node readExpr(String to) {
            for (List res = new ArrayList();;) {
                Node node = fetch();
                if (node instanceof Eof) {
                    throw new RuntimeException(to + " expected, not " + node);
                }
                if (node instanceof Sym && to == ((Sym) node).sym) {
                    if (res.isEmpty()) {
                        throw new RuntimeException("no condition?");
                    }
                    return def(null, res);
                }
                res.add(node);
            }
        }

        private Node readIf() {
            List branches = new ArrayList();
            do {
                Node cond = readExpr("then");
                branches.add(new Node[] { readSeq(' '), cond });
            } while (eofWas instanceof Elif);
            branches.add(new Node[] {
                eofWas instanceof Else ? readSeq(' ')
                                       : new Seq(new Node[] {}) });
            if (!(eofWas instanceof Fi)) {
                throw new RuntimeException("Unexpected " + eofWas.show());
            }
            return new Condition((Node[][]) branches.toArray(
                            new Node[branches.size()][]));
        }

        private Node readCase() {
            Node val = readExpr("of");
            Node[] choices = readMany(' ');
            if (!(eofWas instanceof Esac)) {
                throw new RuntimeException("Unexpected " + eofWas.show());
            }
            return new Case(val, choices);
        }

        private Node readDo() {
            for (List args = new ArrayList();;) {
                Node arg = fetch();
                if (arg instanceof Eof) {
                    throw new RuntimeException("Unexpected " + arg.show());
                }
                if (arg instanceof BinOp && ((BinOp) arg).op == ":") {
                    Node expr = readSeq(' ');
                    if (!(eofWas instanceof Done)) {
                        throw new RuntimeException("Unexpected "
                                                 + eofWas.show());
                    }
                    if (args.isEmpty()) {
                        return new Lambda(new Sym("_"), expr, null);
                    }
                    for (int i = args.size(); --i >= 0;) {
                        expr = new Lambda((Node) args.get(i), expr, null);
                    }
                    return expr;
                }
                args.add(arg);
            }
        }

        private Node[] readMany(char end) {
            List res = new ArrayList();
            List args = null;
            List l = new ArrayList();
            // TODO check for (blaah=) error
            Node sym;
            while (!((sym = fetch()) instanceof Eof)) {
                if (sym instanceof BindOp) {
                    args = l;
                    l = new ArrayList();
                    continue;
                }
                if (sym instanceof SeqOp) {
                    res.add(def(args, l));
                    args = null;
                    l = new ArrayList();
                    continue;
                }
                l.add(sym);
            }
            eofWas = sym;
            if (end != ' ' && (p >= src.length || src[p++] != end)) {
                throw new RuntimeException("Expecting " + end);
            }
            if (!l.isEmpty()) {
                res.add(def(args, l));
            }
            return (Node[]) res.toArray(new Node[res.size()]);
        }

        Node readSeq(char end) {
            Node[] list = readMany(end);
            if (list.length == 1) {
                return list[0];
            }
            return new Seq(list);
        }

        private Node readStr() {
            int st = p;
            String res = "";
            for (; p < src.length && src[p] != '"'; ++p) {
                if (src[p] == '\\') {
                    res = res.concat(new String(src, st, p));
                    st = p;
                    if (++p >= src.length) {
                        break;
                    }
                    switch (src[p]) {
                        case '\\':
                        case '"':
                            continue;
                        case 'n':
                            res = res.concat("\n");
                            break;
                        case 'r':
                            res = res.concat("\r");
                            break;
                        case 't':
                            res = res.concat("\t");
                            break;
                        default:
                            throw new RuntimeException("WTF");
                    }
                    ++st;
                }
            }
            if (p >= src.length) {
                throw new RuntimeException("Unclosed \"");
            }
            return new Str(res.concat(new String(src, st, p++ - st)));
        }
    }
}
