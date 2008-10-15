// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti language parser.
 *
 * Copyright (c) 2007,2008 Madis Janson
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

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import yeti.lang.Core;
import yeti.lang.Num;
import yeti.lang.AList;
import yeti.lang.LList;

interface YetiParser {
    ThreadLocal currentSrc = new ThreadLocal();

    String FIELD_OP = new String(".");

    class Node {
        int line;
        int col;
        String kind;

        String str() {
            return toString();
        }

        Node pos(int line, int col) {
            this.line = line;
            this.col = col;
            return this;
        }

        public String toString() {
            char[] s = (char[]) currentSrc.get();
            if (s == null)
                return getClass().getName();
            int p = 0, l = line;
            if (--l > 0)
                while (p < s.length && (s[p++] != '\n' || --l > 0));
            p += col - 1;
            if (p < 0)
                p = 0;
            int e = p;
            char c;
            while (++e < s.length && ((c = s[e]) > ' ' && c != ':' &&
                     c != ';' && c != '.' && c != ',' && c != '(' && c != ')' &&
                     c != '[' && c != ']' && c != '{' && c != '}'));
            return '\'' + new String(s, p, Math.min(e, s.length) - p) + '\''; 
        }

        String sym() {
            throw new CompileException(this,
                "Expected symbol here, not " + this);
        }
    }

    class XNode extends Node {
        Node[] expr;

        XNode(String kind) {
            this.kind = kind;
        }

        XNode(String kind, Node[] expr) {
            this.kind = kind;
            this.expr = expr;
        }
        
        XNode(String kind, Node expr) {
            this.kind = kind;
            this.expr = new Node[] { expr };
            line = expr.line;
            col = expr.col;
        }

        String str() {
            if (expr == null)
                return ":".concat(kind);
            StringBuffer buf = new StringBuffer("(:");
            buf.append(kind);
            for (int i = 0; i < expr.length; ++i) {
                buf.append(' ');
                buf.append(expr[i].str());
            }
            buf.append(')');
            return buf.toString();
        }

        static XNode struct(Node[] fields) {
            for (int i = 0; i < fields.length; ++i) {
                if (fields[i] instanceof Sym) {
                    Sym s = (Sym) fields[i];
                    Bind bind = new Bind();
                    bind.name = s.sym;
                    bind.expr = s;
                    bind.col = s.col;
                    bind.line = s.line;
                    bind.noRec = true;
                    fields[i] = bind;
                }
            }
            return new XNode("struct", fields);
        }

        static XNode lambda(Node arg, Node expr, Node name) {
            XNode lambda = new XNode("lambda", name == null
                ? new Node[] { arg, expr } : new Node[] { arg, expr, name });
            lambda.line = arg.line;
            lambda.col = arg.col;
            return lambda;
        }
    }

    final class Bind extends Node {
        String name;
        Node expr;
        TypeNode type;
        boolean var;
        boolean property;
        boolean noRec;

        Bind() {
        }

        Bind(List args, Node expr) {
            int first = 0;
            Node nameNode = null;
            while (first < args.size()) {
                nameNode = (Node) args.get(first);
                ++first;
                if (nameNode.kind == "var") {
                    var = true;
                } else if (nameNode.kind == "norec") {
                    noRec = true;
                } else {
                    break;
                }
            }
            if (!var && nameNode instanceof Sym) {
                String s = ((Sym) nameNode).sym;
                if (s == "get") {
                    property = true;
                    nameNode = (Node) args.get(first++);
                } else if (s == "set") {
                    property = true;
                    var = true;
                    nameNode = (Node) args.get(first++);
                }
            }
            if (first == 0 || first > args.size()) {
                throw new CompileException(nameNode,
                        "Variable name is missing");
            }
            if (!(nameNode instanceof Sym)) {
                throw new CompileException(nameNode,
                        "Illegal binding name: " + nameNode
                         + " (missing ; after expression?)");
            }
            line = nameNode.line;
            col = nameNode.col;
            this.name = ((Sym) nameNode).sym;
            if (first < args.size() && args.get(first) instanceof BinOp &&
                    ((BinOp) args.get(first)).op == FIELD_OP) {
                throw new CompileException((BinOp) args.get(first),
                    "Bad argument on binding (use := for assignment, not =)");
            }
            int i = args.size() - 1;
            if (i >= first && args.get(i) instanceof IsOp) {
                type = ((IsOp) args.get(i)).type;
                --i;
            }
            for (; i >= first; --i) {
                expr = XNode.lambda((Node) args.get(i), expr,
                        i == first ? nameNode : null);
            }
            this.expr = expr;
        }

        String str() {
            return name + " = " + expr.str();
        }
    }

    final class Seq extends Node {
        static final Object EVAL = new Object();

        Node[] st;
        Object seqKind;

        Seq(Node[] st, Object kind) {
            this.st = st;
            this.seqKind = kind;
        }

        String str() {
            StringBuffer res = new StringBuffer();
            res.append('(');
            if (st == null) {
                res.append(':');
            } else {
                for (int i = 0; i < st.length; ++i) {
                    if (i != 0) {
                        res.append(", ");
                    }
                    res.append(st[i].str());
                }
            }
            res.append(')');
            return res.toString();
        }
    }

    final class Sym extends Node {
        String sym;

        Sym(String sym) {
            this.sym = sym;
        }

        String sym() {
            return sym;
        }

        String str() {
            return sym;
        }

        public String toString() {
            return sym;
        }
    }

    final class Str extends Node {
        String str;

        Str(String str) {
            this.str = str;
        }

        String str() {
            return Core.show(str);
        }
    }

    final class NumLit extends Node {
        Num num;

        NumLit(Num num) {
            this.num = num;
        }

        String str() {
            return String.valueOf(num);
        }
    }

    final class Eof extends XNode {
        Eof(String kind) {
            super(kind);
        }

        public String toString() {
            return kind;
        }
    }

    class BinOp extends Node {
        int prio;
        String op;
        boolean toRight;
        boolean postfix;
        Node left;
        Node right;
        BinOp parent;

        BinOp(String op, int prio, boolean toRight) {
            this.op = op;
            this.prio = prio;
            this.toRight = toRight;
        }

        String str() {
            return '(' + (left == null ? "<>" : left.str()) + ' ' + op + ' '
                       + (right == null ? "<>" : right.str()) + ')';
        }
    }

    final class TypeDef extends Node {
        String name;
        String[] param;
        TypeNode type;

        String str() {
            StringBuffer buf = new StringBuffer("type ");
            buf.append(name);
            if (param.length > 0) {
                buf.append('<');
                for (int i = 0; i < param.length; ++i) {
                    if (i != 0)
                        buf.append(", ");
                    buf.append(param[i]);
                }
                buf.append('>');
            }
            buf.append(" = ");
            buf.append(type.str());
            return buf.toString();
        }
    }

    class TypeOp extends BinOp {
        TypeNode type;

        TypeOp(String what, TypeNode type) {
            super(what, Parser.IS_OP_LEVEL, true);
            postfix = true;
            this.type = type;
        }

        String str() {
            return (right == null ? "<>" : right.str())
                        + ' ' + op + ' ' + type.str();
        }
    }

    final class IsOp extends TypeOp {
        IsOp(TypeNode type) {
            super("is", type);
        }
    }

    final class ObjectRefOp extends BinOp {
        String name;
        Node[] arguments;

        ObjectRefOp(String name, Node[] arguments) {
            super("#", 0, true);
            postfix = true;
            this.name = name;
            this.arguments = arguments;
        }

        String str() {
            StringBuffer buf =
                new StringBuffer(right == null ? "<>" : right.str());
            buf.append('#');
            buf.append(name);
            if (arguments != null) {
                buf.append('(');
                for (int i = 0; i < arguments.length; ++i) {
                    if (i != 0) {
                        buf.append(", ");
                    }
                    buf.append(arguments[i].str());
                }
                buf.append(')');
            }
            return buf.toString();
        }
    }

    final class InstanceOf extends BinOp {
        String className;

        InstanceOf(String className) {
            super("instanceof", Parser.IS_OP_LEVEL, true);
            postfix = true;
            this.className = className;
        }
    }

    class TypeNode extends Node {
        String name;
        TypeNode[] param;
        boolean var;

        TypeNode(String name, TypeNode[] param) {
            this.name = name;
            this.param = param;
        }

        String str() {
            if (name == "->")
                return "(" + param[0].str() + " -> " + param[1].str() + ")";
            StringBuffer buf = new StringBuffer();
            if (name == "|") {
                for (int i = 0; i < param.length; ++i) {
                    if (i != 0)
                        buf.append(" | ");
                    buf.append(param[i].str());
                }
                return buf.toString();
            }
            if (name == "") {
                buf.append('{');
                for (int i = 0; i < param.length; ++i) {
                    if (i != 0) {
                        buf.append("; ");
                    }
                    buf.append(param[i].name);
                    buf.append(" is ");
                    buf.append(param[i].param[0].str());
                }
                buf.append('}');
                return buf.toString();
            }
            if (param == null || param.length == 0)
                return name;
            if (Character.isUpperCase(name.charAt(0))) {
                return "(" + name + " " + param[0].str() + ")";
            }
            buf.append(name);
            buf.append('<');
            for (int i = 0; i < param.length; ++i) {
                if (i != 0)
                    buf.append(", ");
                buf.append(param[i].str());
            }
            buf.append('>');
            return buf.toString();
        }
    }

    final class ParseExpr {
        private boolean lastOp = true;
        private BinOp root = new BinOp(null, -1, false);
        private BinOp cur = root;

        private void apply(Node node) {
            BinOp apply = new BinOp("", 2, true);
            apply.line = node.line;
            apply.col = node.col;
            addOp(apply);
        }

        private void addOp(BinOp op) {
            BinOp to = cur;
            if (op.op == "-" && lastOp || op.op == "\\" || op.op == "throw") {
                if (!lastOp) {
                    apply(op);
                    to = cur;
                }
                op.prio = 1;
                to.left = to.right;
            } else if (lastOp) {
                throw new CompileException(op, "Do not stack operators");
            } else {
                while (to.parent != null && (to.postfix || to.prio < op.prio ||
                            to.prio == op.prio && op.toRight)) {
                    to = to.parent;
                }
                op.right = to.right;
            }
            op.parent = to;
            to.right = op;
            cur = op;
            lastOp = !op.postfix;
        }

        void add(Node node) {
            if (node instanceof BinOp && ((BinOp) node).parent == null) {
                addOp((BinOp) node);
            } else {
                if (!lastOp) {
                    apply(node);
                }
                lastOp = false;
                cur.left = cur.right;
                cur.right = node;
            }
        }

        Node result() {
            if (cur.left == null && cur.prio != -1 && cur.prio != 1 &&
                    !cur.postfix || cur.right == null) {
                throw new CompileException(cur, "Expecting some value");
            }
            return root.right;
        }
    }

    final class Parser {
        private static final char[] CHS =
            ("                                " + // 0x
             " .'.x..x  .. ../xxxxxxxxxx. ...x" + // 2x
             ".xxxxxxxxxxxxxxxxxxxxxxxxxx[ ].x" + // 4x
             "`xxxxxxxxxxxxxxxxxxxxxxxxxx . . ").toCharArray();

        private static final String[][] OPS = {
            { "*", "/", "%", "&" },
            { "+", "-", "|" },
            { "::", ":." },
            { "<", ">", "<=", ">=", "==", "!=", "=~" },
            { null }, // and or
            { null }, // non-standard operators
            { "." },
            { ":=" },
            { "is" },
            { ":" }
        };
        private static final int FIRST_OP_LEVEL = 3;
        private static final int COMP_OP_LEVEL = opLevel("<");
        private static final int DOT_OP_LEVEL = opLevel(".");
        static final int IS_OP_LEVEL = opLevel("is");
        private static final Eof EOF = new Eof("EOF");
        private char[] src;
        private int p;
        private Node eofWas;
        private int flags;
        private String sourceName;
        private int line = 1;
        private int lineStart;
        String moduleName;
        boolean isModule;

        private static int opLevel(String op) {
            int i = 0;
            while (OPS[i][0] != op)
                ++i;
            return i + FIRST_OP_LEVEL;
        }

        Parser(String sourceName, char[] src, int flags) {
            this.sourceName = sourceName;
            this.src = src;
            this.flags = flags;
        }

        private int skipSpace() {
            char[] src = this.src;
            int i = p;
            char c;
            for (;;) {
                while (i < src.length && (c = src[i]) >= '\000' && c <= ' ') {
                    ++i;
                    if (c == '\n') {
                        ++line;
                        lineStart = i;
                    }
                }
                if (i + 1 < src.length && src[i] == '/') {
                    if (src[i + 1] == '/') {
                        while (i < src.length && src[i] != '\n'
                                && src[i] != '\r') ++i;
                        continue;
                    }
                    if (src[i + 1] == '*') {
                        int l = line, col = i - lineStart + 1;
                        i += 2;
                        for (int level = 1; level > 0;) {
                            if (++i >= src.length) {
                                throw new CompileException(l, col,
                                        "Unclosed /* comment");
                            }
                            if ((c = src[i - 1]) == '\n') {
                                ++line;
                                lineStart = i - 1;
                            } else if (c == '*' && src[i] == '/') {
                                ++i; --level;
                            } else if (c == '/' && src[i] == '*') {
                                ++i; ++level;
                            }
                        }
                        continue;
                    }
                }
                return i;
            }
        }

        private Node fetch() {
            int i = skipSpace();
            if (i >= src.length) {
                return EOF;
            }
            char[] src = this.src;
            char c;
            p = i + 1;
            int line = this.line, col = p - lineStart;
            switch (src[i]) {
                case '.':
                    if ((i <= 0 || (c = src[i - 1]) < '~' && CHS[c] == ' ' &&
                                (i >= src.length ||
                                 (c = src[i + 1]) < '~' && CHS[c] == ' ')))
                        return new BinOp(".", DOT_OP_LEVEL, true)
                            .pos(line, col);
                    break;
                case ';':
                    return new XNode(";").pos(line, col);
                case ',':
                    return new XNode(",").pos(line, col);
                case '(':
                    return readSeq(')', null);
                case '[':
                    if (i + 2 < src.length && src[i + 1] == ':' &&
                            src[i + 2] == ']') {
                        p = i + 3;
                        return new XNode("list").pos(line,col);
                    }
                    return new XNode("list", readMany(",", ']')).pos(line, col);
                case '{':
                    return XNode.struct(readMany(",", '}')).pos(line, col);
                case ')':
                    p = i;
                    return new Eof(")").pos(line, col);
                case ']':
                    p = i;
                    return new Eof("]").pos(line, col);
                case '}':
                    p = i;
                    return new Eof("}").pos(line, col);
                case '"':
                    return readStr().pos(line, col);
                case '\'':
                    return readAStr().pos(line, col);
                case '\\':
                    return new BinOp("\\", 1, false).pos(line, col);
            }
            p = i;
            while (i < src.length && (c = src[i]) <= '~' &&
                   (CHS[c] == '.' || c == '$' ||
                    c == '/' && (i + 1 >= src.length ||
                                 (c = src[i + 1]) != '/' && c != '*'))) ++i;
            if (i != p) {
                String s = new String(src, p, i - p).intern();
                p = i;
                if (s == "=")
                    return new XNode("=").pos(line, col);
                if (s == ".")
                    return new BinOp(FIELD_OP, 0, true).pos(line, col);
                if (s == "#")
                    return readObjectRef().pos(line, col);
                for (i = OPS.length; --i >= 0;) {
                    for (int j = OPS[i].length; --j >= 0;) {
                        if (OPS[i][j] == s) {
                            return new BinOp(s, i + FIRST_OP_LEVEL, s != "::")
                                         .pos(line, col);
                        }
                    }
                }
                return new BinOp(s, DOT_OP_LEVEL - 1, true).pos(line, col);
            }
            if ((c = src[i]) >= '0' && c <= '9') {
                while (++i < src.length && ((c = src[i]) <= 'z' &&
                       (CHS[c] == 'x' ||
                        c == '.' && (i + 1 >= src.length || src[i + 1] != '.')
                        || ((c == '+' || c == '-') &&
                         (src[i - 1] == 'e' || src[i - 1] == 'E')))));
                String s = new String(src, p, i - p);
                p = i;
                try {
                    return new NumLit(Core.parseNum(s)).pos(line, col);
                } catch (Exception e) {
                    throw new CompileException(line, col,
                        "Bad number literal '" + s + "'");
                }
            }
            if (src[i] == '`' && i + 1 < src.length)
                ++i;
            while (++i < src.length && ((c = src[i]) > '~' || CHS[c] == 'x'));
            String s = new String(src, p, i - p);
            p = i;
            s = s.intern(); // Sym's are expected to have interned strings
            Node res;
            if (s == "if") {
                res = readIf();
            } else if (s == "do") {
                res = readDo();
            } else if (s == "and" || s == "or") {
                res = new BinOp(s, COMP_OP_LEVEL + 1, true);
            } else if (s == "then" || s == "elif" || s == "else" ||
                       s == "fi" || s == "of" || s == "esac" || s == "done" ||
                       s == "catch" || s == "finally" || s == "yrt") {
                res = new Eof(s);
            } else if (s == "case") {
                res = readCase();
            } else if (s == "in") {
                res = new BinOp(s, COMP_OP_LEVEL, true);
            } else if (s == "div" || s == "shr" || s == "shl") {
                res = new BinOp(s, FIRST_OP_LEVEL, true);
            } else if (s == "xor") {
                res = new BinOp(s, FIRST_OP_LEVEL + 1, true);
            } else if (s == "is" || s == "unsafely_as" || s == "as") {
                TypeNode t = readType(true);
                if (t == null) {
                    throw new CompileException(line, col,
                                "Expecting type expression");
                }
                return (s == "is" ? new IsOp(t) : new TypeOp(s, t))
                            .pos(line, col);
            } else if (s == "new") {
                res = readNew();
            } else if (s == "var" || s == "norec") {
                res = new XNode(s);
            } else if (s == "throw") {
                res = new BinOp("throw", 1, false);
            } else if (s == "loop") {
                res = new BinOp(s, IS_OP_LEVEL, false);
            } else if (s == "load" || s == "import" || s == "classOf") {
                res = new XNode(s, readDotted(false,
                    s == "load" ? "Expected module name after 'load', not a " :
                    s == "import" ? "Expected class path after 'import', not a "
                    : "Expected class name, not a "));
            } else if (s == "type") {
                res = readTypeDef();
            } else if (s == "try") {
                res = readTry();
            } else if (s == "instanceof") {
                res = new InstanceOf(
                        readDotted(false, "Expected class name, not a ").sym);
            } else if (s == "class") {
                res = readClassDef();
            } else {
                if (s.charAt(0) != '`') {
                    res = new Sym(s);
                } else if (s.length() > 1 && p < src.length && src[p] == '`') {
                    ++p;
                    res = new BinOp(s.substring(1).intern(),
                                    DOT_OP_LEVEL - 1, true);
                } else {
                    throw new CompileException(line, col, "Syntax error");
                }
            }
            return res.pos(line, col);
        }

        private Node def(List args, List expr, boolean structDef) {
            BinOp partial = null;
            String s = null;
            int i = 0, cnt = expr.size();
            if (cnt > 0) {
                Object o = expr.get(0);
                if (o instanceof BinOp
                    && (partial = (BinOp) o).parent == null
                    && partial.op != "\\" && partial.op != "-"
                    && partial.op != "throw") {
                    s = partial.op;
                    i = 1;
                } else if ((o = expr.get(cnt - 1)) instanceof BinOp &&
                           (partial = (BinOp) o).parent == null &&
                           !partial.postfix) {
                    if (partial.op == "loop") {
                        partial.postfix = true;
                    } else {
                        s = partial.op;
                        --cnt;
                    }
                    if (s == FIELD_OP) {
                        throw new CompileException(partial,
                                "Unexpected '.' here. Add space before it, " +
                                "if you want a compose section.");
                    }
                }
            }
            if (s != null && i >= cnt) {
                return new Sym(s);
            }
            ParseExpr parseExpr = new ParseExpr();
            while (i < cnt) {
                parseExpr.add((Node) expr.get(i++));
            }
            Node e = parseExpr.result();
            if (s != null) {
                if (cnt < expr.size()) {
                    BinOp r = new BinOp("", 2, true);
                    r.left = new Sym(s);
                    r.right = e;
                    r.parent = r; // so it would be considered "processed"
                    e = r;
                } else {
                    e = new XNode("rsection",
                                new Node[] { new Sym(s), parseExpr.result() });
                }
                e.line = partial.line;
                e.col = partial.col;
            }
            Bind bind;
            return args == null ? e :
                   args.size() == 1 && ((Node) args.get(0)).kind == "struct"
                   ? (Node) new XNode("struct-bind",
                        new Node[] { (XNode) args.get(0), e })
                   : (bind = new Bind(args, e)).name != "_" ? bind
                   : bind.expr.kind == "lambda"
                        ? bind.expr : new XNode("_", bind.expr);
        }

        private Node[] readArgs() {
            if ((p = skipSpace()) >= src.length || src[p] != '(') {
                return null;
            }
            ++p;
            return readMany(",", ')');
        }

        // new ClassName(...)
        private Node readNew() {
            Node[] args = null;
            String name = "";
            while (args == null) {
                int nline = line, ncol = p - lineStart + 1;
                Node sym = fetch();
                if (!(sym instanceof Sym)) {
                    throw new CompileException(nline, ncol,
                                "Expecting class name after new");
                }
                name += ((Sym) sym).sym;
                args = readArgs();
                if (args == null) {
                    char c;
                    if (p >= src.length || (c = src[p]) != '.' && c != '$') {
                        throw new CompileException(line, p - lineStart + 1,
                                    "Expecting constructor argument list");
                    }
                    ++p;
                    name += c == '.' ? '/' : c;
                }
            }
            Node[] ex = new Node[args.length + 1];
            ex[0] = new Sym(name.intern());
            System.arraycopy(args, 0, ex, 1, args.length);
            return new XNode("new", ex);
        }

        // #something or #something(...)
        private Node readObjectRef() {
            int nline = line, ncol = p - lineStart + 1;
            int st = skipSpace(), i = st;
            while (i < src.length && Character.isJavaIdentifierPart(src[i]))
                ++i;
            if (i == st) {
                throw new CompileException(nline, ncol,
                            "Expecting java identifier after #");
            }
            p = i;
            return new ObjectRefOp(new String(src, st, i - st).intern(),
                                   readArgs());
        }

        private Node readIf() {
            Node cond = readSeqTo("then");
            Node expr = readSeq(' ', null);
            Node els;
            if (eofWas.kind == "elif") {
                els = readIf();
            } else {
                if (eofWas.kind == "else") {
                    els = readSeq(' ', null);
                } else {
                    els = new XNode("()").pos(eofWas.line, eofWas.col);
                }
                if (eofWas.kind != "fi") {
                    throw new CompileException(eofWas,
                        "Expected fi, found " + eofWas);
                }
            }
            return new XNode("if", new Node[] { cond, expr, els });
        }

        private Node readCase() {
            Node val = readSeqTo("of");
            Node[] choices = readMany(";", ' ');
            if (eofWas.kind != "esac") {
                throw new CompileException(eofWas,
                    "Expected esac, found " + eofWas);
            }
            Node[] expr = new Node[choices.length + 1];
            expr[0] = val;
            System.arraycopy(choices, 0, expr, 1, choices.length);
            return new XNode("case", expr);
        }

        private Node readTry() {
            List catches = new ArrayList();
            catches.add(readSeq(' ', null));
            while (eofWas.kind != "finally" && eofWas.kind != "yrt") {
                if (eofWas.kind != "catch") {
                    throw new CompileException(eofWas,
                        "Expected finally or yrt, found " + eofWas);
                }
                XNode c = (XNode) eofWas;
                catches.add(c);
                c.expr = new Node[3];
                c.expr[0] =
                    readDotted(false, "Expected exception name, not ");
                Node n = fetch();
                if (n instanceof Sym) {
                    c.expr[1] = n;
                    n = fetch();
                }
                if (!(n instanceof BinOp) || ((BinOp) n).op != ":") {
                    throw new CompileException(n, "Expected ':'" +
                        (c.expr[1] == null ? " or identifier" : "") +
                        ", but found " + n);
                }
                if (c.expr[1] == null) {
                    c.expr[1] = new Sym("_").pos(n.line, n.col);
                }
                c.expr[2] = readSeq(' ', null);
            }
            if (eofWas.kind != "yrt") {
                catches.add(readSeqTo("yrt"));
            }
            Node[] expr = (Node[]) catches.toArray(new Node[catches.size()]);
            if (expr.length <= 1) {
                throw new CompileException(eofWas,
                    "try block must contain at least one catch or finally");
            }
            return new XNode("try", expr);
        }

        private Node readArgDefs() {
            int line_ = line, col_ = p++ - lineStart + 1;
            List args = new ArrayList();
            while ((p = skipSpace()) < src.length && src[p] != ')') {
                if (args.size() != 0 && src[p++] != ',') {
                    throw new CompileException(line, p - lineStart,
                                               "Expecting , or )");
                }
                Sym t = readDotted(false, "Expected argument type, found ");
                int s = p;
                while (src.length > p + 1 && src[p] == '[' && src[p + 1] == ']')
                    p += 2;
                if (s != p)
                    t.sym = t.sym.concat(new String(src, s, p - s)).intern();
                args.add(t);
                Node name = fetch();
                if (!(name instanceof Sym)) {
                    throw new CompileException(name,
                        "Expected an argument name, found " + name);
                }
                args.add(name);
            }
            ++p;
            return new XNode("argument-list", (Node[]) args.toArray(
                                new Node[args.size()])).pos(line_, col_);
        }

        private Node readClassDef() {
            List defs = new ArrayList();
            Node node = fetch();
            if (!(node instanceof Sym))
                throw new CompileException(node,
                            "Expected a class name, found " + node);
            p = skipSpace();
            defs.add(node);
            defs.add(p < src.length && src[p] == '(' ? readArgDefs()
                        : new XNode("argument-list", new Node[0]));
            List l = new ArrayList();
            node = readDotted(false, "Expected extends, field or "
                                     + "method definition, found ");
            Node epos = node;
            if (node.sym() == "extends") {
                do {
                    l.add(readDotted(false, "Expected a class name, found "));
                    int line_ = line, col_ = p - lineStart + 1;
                    l.add(new XNode("arguments", readArgs()).pos(line_, col_));
                } while ((p = skipSpace()) < src.length && src[p++] == ',');
                --p;
                node = fetch();
            } else if (node.sym() == "var") {
                node = new XNode("var").pos(node.line, node.col);
            }
            defs.add(new XNode("extends", (Node[]) l.toArray(
                            new Node[l.size()])).pos(epos.line, epos.col));
            l.clear();
            eofWas = node;
            while (!(eofWas instanceof Sym) || ((Sym) eofWas).sym != "end") {
                if (node == null)
                    node = fetch();
                if (node.kind == "var" || node.kind == "norec") {
                    l.add(node);
                    node = fetch();
                }
                String meth = "method";
                Node args = null;
                while (node instanceof Sym) {
                    p = skipSpace();
                    if (p < src.length && src[p] == '(') {
                        if (l.size() != 0 &&
                            ((Node) l.get(0)).sym() == "static") {
                            meth = "static-method";
                            l.remove(0);
                        }
                        if (l.size() == 0) {
                            throw new CompileException(line, p - lineStart + 1,
                                            "Expected method name, found (");
                        }
                        if (l.size() == 1) {
                            args = readArgDefs();
                        }
                        break;
                    }
                    l.add(node);
                    node = fetch();
                }
                if (args == null) {
                    if (node instanceof IsOp) {
                        l.add(node);
                        node = fetch();
                    }
                    if (node.kind != "=")
                        throw new CompileException(node,
                            "Expected '=' or argument list, found " + node);
                }
                Node expr = readSeq('e', null);
                if (eofWas instanceof Eof)
                    throw new CompileException(eofWas, "Unexpected " + eofWas);
                if (args == null) {
                    defs.add(new Bind(l, expr));
                } else {
                    defs.add(new XNode(meth, new Node[] { (Node) l.get(0),
                                node, args, expr }).pos(node.line, node.col));
                }
                l.clear();
                node = null;
            }
            return new XNode("class",
                             (Node[]) defs.toArray(new Node[defs.size()]));
        }

        private Node readDo() {
            for (List args = new ArrayList();;) {
                Node arg = fetch();
                if (arg instanceof Eof) {
                    throw new CompileException(arg, "Unexpected " + arg);
                }
                if (arg instanceof BinOp && ((BinOp) arg).op == ":") {
                    Node expr = readSeqTo("done");
                    if (args.isEmpty()) {
                        return XNode.lambda(new Sym("_").pos(arg.line, arg.col),
                                            expr, null);
                    }
                    for (int i = args.size(); --i >= 0;) {
                        expr = XNode.lambda((Node) args.get(i), expr, null);
                    }
                    return expr;
                }
                args.add(arg);
            }
        }

        private Sym readDotted(boolean decl, String err) {
            Node first = fetch();
            String result = "";
            for (Node n = first;; n = fetch()) {
                if (!(n instanceof Sym)) {
                    if (n.kind != "var")
                        throw new CompileException(n, err + n);
                    result += n.kind;
                } else {
                    result += ((Sym) n).sym;
                }
                p = skipSpace();
                if (p >= src.length)
                    break;
                if (src[p] != '.') {
                    if (!decl)
                        break;
                    if (src[p] == ';') {
                        ++p;
                        break;
                    }
                    throw new CompileException(n, "Expected ';', not a " + n);
                }
                ++p;
                result += "/";
            }
            Sym sym = new Sym(result.intern());
            sym.pos(first.line, first.col);
            return sym;
        }

        private Node[] readMany(String sep, char end) {
            List res = new ArrayList();
            List args = null;
            List l = new ArrayList();
            // TODO check for (blaah=) error
            Node sym;
            while (!((sym = fetch()) instanceof Eof)) {
                // hack for class end - end is not reserved word
                if (end == 'e' && sym instanceof Sym && sym.sym() == "end")
                    break;
                if (sym.kind == "=") {
                    args = l;
                    if (end == '}') {
                        l = Collections.singletonList(readSeq(' ', null));
                        if ((sym = eofWas) instanceof Eof)
                            break;
                    } else {
                        l = new ArrayList();
                        continue;
                    }
                }
                if (sym.kind == ";" || sym.kind == ",") {
                    if (sym.kind != sep) {
                        break;
                    }
                    if (args != null || sep != ";" || l.size() != 0) {
                        res.add(def(args, l, end == '}'));
                        args = null;
                        l = new ArrayList();
                    }
                    continue;
                }
                l.add(sym);
                if (sep == ";" && sym instanceof TypeDef) {
                    res.add(def(args, l, false));
                    args = null;
                    l = new ArrayList();
                }
            }
            eofWas = sym;
            if (end != ' ' && end != 'e' &&
                (p >= src.length || src[p++] != end)) {
                throw new CompileException(line, p - lineStart + 1,
                                           "Expecting " + end);
            }
            if (l.size() != 0) {
                res.add(def(args, l, end == '}'));
            }
            return (Node[]) res.toArray(new Node[res.size()]);
        }

        private Node readSeq(char end, Object kind) {
            Node[] list = readMany(";", end);
            if (list.length == 1 && kind != Seq.EVAL) {
                return list[0];
            }
            if (list.length == 0) {
                return new XNode("()").pos(line, p - lineStart);
            }
            Node w = list[list.length - 1];
            for (BinOp bo; w instanceof BinOp &&
                           (bo = (BinOp) w).left != null;) {
                w = bo.left;
            }
            return new Seq(list, kind).pos(w.line, w.col);
        }

        private Node readSeqTo(String endKind) {
            Node node = readSeq(' ', null);
            if (eofWas.kind != endKind) {
                throw new CompileException(eofWas,
                    "Expected " + endKind + ", found " + eofWas);
            }
            return node;
        }

        private Node readStr() {
            int st = p;
            List parts = null;
            StringBuffer res = new StringBuffer();
            int sline = line, scol = p - lineStart;
            for (; p < src.length && src[p] != '"'; ++p) {
                if (src[p] == '\n') {
                    lineStart = p + 1;
                    ++line;
                }
                if (src[p] == '\\') {
                    res.append(src, st, p - st);
                    st = ++p;
                    if (p >= src.length) {
                        break;
                    }
                    switch (src[p]) {
                        case '\\':
                        case '"':
                            continue;
                        case 'n':
                            res.append('\n');
                            break;
                        case 'r':
                            res.append('\r');
                            break;
                        case 't':
                            res.append('\t');
                            break;
                        case '(':
                            ++p;
                            if (parts == null) {
                                parts = new ArrayList();
                            }
                            if (res.length() != 0) {
                                parts.add(new Str(res.toString()));
                            }
                            parts.add(readSeq(')', null));
                            res.setLength(0);
                            st = --p;
                            break;
                        default:
                            if (src[p] > ' ') {
                                throw new CompileException(line, p - lineStart,
                                    "Unexpected escape: \\" + src[p]);
                            }
                            p = skipSpace();
                            if (p >= src.length || src[p] != '"') {
                                throw new CompileException(line, p - lineStart,
                                            "Expecting continuation of string");
                            }
                            st = p;
                    }
                    ++st;
                }
            }
            if (p >= src.length) {
                throw new CompileException(sline, scol, "Unclosed \"");
            }
            res.append(src, st, p++ - st);
            if (parts == null) {
                return new Str(res.toString());
            }
            if (res.length() != 0) {
                parts.add(new Str(res.toString()));
            }
            return new XNode("concat", (Node[]) parts.toArray(
                                            new Node[parts.size()]));
        }

        private Node readAStr() {
            int i = p, sline = line, scol = i - lineStart;
            String s = "";
            do {
                for (; i < src.length && src[i] != '\''; ++i)
                    if (src[p] == '\n') {
                        lineStart = p + 1;
                        ++line;
                    }
                if (i >= src.length) {
                    throw new CompileException(sline, scol, "Unclosed '");
                }
                s = s.concat(new String(src, p, i - p));
                p = ++i;
            } while (i < src.length && src[i++] == '\'');
            return new Str(s);
        }

        String getTypename(Node node) {
            if (!(node instanceof Sym)) {
                throw new CompileException(node,
                            "Expected typename, not a " + node);
            }
            return ((Sym) node).sym;
        }

        TypeDef readTypeDef() {
            TypeDef def = new TypeDef();
            def.name = getTypename(fetch());
            List param = new ArrayList();
            Node node = fetch();
            if (node instanceof BinOp && ((BinOp) node).op == "<") {
                do {
                    param.add(getTypename(fetch()));
                } while ((node = fetch()).kind == ",");
                if (!(node instanceof BinOp) || ((BinOp) node).op != ">")
                    throw new CompileException(node,
                                 "Expected '>', not a " + node);
                node = fetch();
            }
            if (node.kind != "=")
                throw new CompileException(node, "Expected '=', not a " + node);
            def.param = (String[]) param.toArray(new String[param.size()]);
            def.type = readType(true);
            return def;
        }

        // ugly all-in-one bastard type expression parser
        TypeNode readType(boolean checkVariant) {
            int i = skipSpace();
            if (p >= src.length || src[i] == ')' || src[i] == '>') {
                p = i;
                return null;
            }
            int sline = line, scol = i - lineStart;
            TypeNode res;
            if (src[i] == '(') {
                p = i + 1;
                res = readType(true);
                if (p >= src.length || src[p] != ')') {
                    if (res == null)
                        throw new CompileException(sline, scol, "Unclosed (");
                    throw new CompileException(line, p - lineStart,
                                               "Expecting ) here");
                }
                ++p;
                if (res == null) {
                    res = new TypeNode("()", null);
                    res.pos(sline, scol);
                }
            } else if (src[i] == '{') {
                p = i + 1;
                Node t, field = null;
                ArrayList param = new ArrayList();
                String expect = "Expecting field name or '}' here, not ";
                for (;;) {
                    boolean isVar = (field = fetch()).kind == "var";
                    if (isVar && !((field = fetch()) instanceof Sym))
                        throw new CompileException(field,
                            "Exepcting field name after var");
                    if (!(field instanceof Sym))
                        break;
                    if (!((t = fetch()) instanceof IsOp) ||
                            ((BinOp) t).right != null) {
                        throw new CompileException(t,
                            "Expecting 'is' after field name");
                    }
                    TypeNode f = new TypeNode(((Sym) field).sym,
                                    new TypeNode[] { ((IsOp) t).type });
                    f.var = isVar;
                    param.add(f);
                    if ((field = fetch()).kind != ",") {
                        expect = "Expecting ',' or '}' here, not ";
                        break;
                    }
                }
                if (field.kind != "}") {
                    throw new CompileException(field, expect + field);
                }
                ++p;
                res = new TypeNode("",
                        (TypeNode[]) param.toArray(new TypeNode[param.size()]));
                res.pos(sline, scol);
            } else {
                int start = i;
                char c = ' ', dot = '_';
                if (i < src.length) {
                    if ((c = src[i]) == '~') {
                        ++i;
                        dot = '.';
                    } else if (c == '^') {
                        ++i;
                    }
                }
                boolean maybeArr = c == '~' || c == '\'';
                while (i < src.length && ((c = src[i]) > '~' || CHS[c] == 'x'
                                          || c == dot || c == '$'))
                    ++i;
                while (maybeArr && i + 1 < src.length && // java arrays
                       src[i] == '[' && src[i + 1] == ']')
                    i += 2;
                if (i == start)
                    throw new CompileException(sline, scol,
                                "Expected type identifier, not '" +
                                src[i] + "' in the type expression");
                p = i;
                String sym = new String(src, start, i - start).intern();
                ArrayList param = new ArrayList();
                if (Character.isUpperCase(src[start])) {
                    TypeNode node = readType(false);
                    if (node == null)
                        throw new CompileException(line, p - lineStart,
                                        "Expecting variant argument");
                    node =  new TypeNode(sym, new TypeNode[] { node });
                    node.pos(sline, scol);
                    if (!checkVariant) {
                        return node;
                    }
                    param.add(node);
                    while ((p = skipSpace() + 1) < src.length &&
                           src[p - 1] == '|' &&
                           (node = readType(false)) != null)
                        param.add(node);
                    --p;
                    return (TypeNode)
                        new TypeNode("|", (TypeNode[]) param.toArray(
                                new TypeNode[param.size()])).pos(sline, scol);
                }
                if ((p = skipSpace()) < src.length && src[p] == '<') {
                    ++p;
                    for (TypeNode node; (node = readType(true)) != null; ++p) {
                        param.add(node);
                        if ((p = skipSpace()) >= src.length || src[p] != ',')
                            break;
                    }
                    if (p >= src.length || src[p] != '>')
                        throw new CompileException(line, p - lineStart,
                                                   "Expecting > here");
                    ++p;
                }
                res = new TypeNode(sym,
                        (TypeNode[]) param.toArray(new TypeNode[param.size()]));
                res.pos(sline, scol);
            }
            if ((p = skipSpace()) + 1 >= src.length ||
                    src[p] != '-' || src[p + 1] != '>')
                return res;
            sline = line;
            scol = p - lineStart;
            p += 2;
            TypeNode arg = readType(false);
            if (arg == null)
                throw new CompileException(sline, scol,
                                "Expecting return type after ->");
            return (TypeNode) new TypeNode("->", new TypeNode[] { res, arg })
                            .pos(sline, scol);
        }

        Node parse(Object topLevel) {
            if (src.length > 2 && src[0] == '#' && src[1] == '!')
                for (p = 2; p < src.length && src[p] != '\n'; ++p);
            int i = p = skipSpace();
            while (i < src.length && src[i] < '~' && CHS[src[i]] == 'x')
                ++i;
            String s = new String(src, p, i - p);
            if (s.equals("module") || s.equals("program")) {
                p = i;
                moduleName = readDotted(true,
                    "Expected " + s + " name, not a ").sym;
                isModule = s.equals("module");
            }
            char first = p < src.length ? src[p] : ' ';
            Node res;
            if ((flags & YetiC.CF_EVAL_BIND) != 0) {
                res = readSeq(' ', Seq.EVAL);
                if (res instanceof Seq) {
                    Seq seq = (Seq) res;
                    if (seq.st[seq.st.length - 1] instanceof Bind ||
                        seq.st[seq.st.length - 1].kind == "struct-bind" ||
                        seq.st[seq.st.length - 1].kind == "import") {
                        Node[] tmp = new Node[seq.st.length + 1];
                        System.arraycopy(seq.st, 0, tmp, 0, seq.st.length);
                        tmp[tmp.length - 1] =
                            new XNode("()").pos(seq.line, seq.col);
                        seq.st = tmp;
                    } else if (seq.st.length == 1) {
                        res = seq.st[0];
                    }
                }
            } else {
                res = readSeq(' ', topLevel);
            }
            if (eofWas != EOF) {
                throw new CompileException(eofWas, "Unexpected " + eofWas);
            }
            return res;
        }
    }
}
