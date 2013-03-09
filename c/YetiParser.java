// ex: se sts=4 sw=4 expandtab:

/**
 * Yeti language parser.
 *
 * Copyright (c) 2007-2013 Madis Janson
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
            return '`' + new String(s, p, Math.min(e, s.length) - p) + '\''; 
        }

        String sym() {
            throw new CompileException(this,
                "Expected symbol here, not " + this);
        }
    }

    class XNode extends Node {
        Node[] expr;
        String doc;

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
                return "`".concat(kind);
            StringBuffer buf = new StringBuffer("(`");
            buf.append(kind);
            for (int i = 0; i < expr.length; ++i) {
                buf.append(' ');
                if (expr[i] != null)
                    buf.append(expr[i].str());
            }
            buf.append(')');
            return buf.toString();
        }

        static XNode struct(Node[] fields) {
            for (int i = 0; i < fields.length; ++i) {
                IsOp op = null;
                Sym s = null;
                if (fields[i] instanceof Sym) {
                    s = (Sym) fields[i];
                } else if (fields[i] instanceof IsOp) {
                    op = (IsOp) fields[i];
                    op.right.sym();
                    s = (Sym) op.right;
                }
                if (s != null) {
                    Bind bind = new Bind();
                    bind.name = s.sym;
                    bind.expr = s;
                    bind.col = s.col;
                    bind.line = s.line;
                    bind.noRec = true;
                    if (op != null)
                        bind.type = op.type;
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
        String doc;

        Bind() {
        }

        Bind(List args, Node expr, boolean inStruct, String doc) {
            String s;
            this.doc = doc;
            int first = 0;
            Node nameNode = null;
            while (first < args.size()) {
                nameNode = (Node) args.get(first);
                ++first;
                if (nameNode.kind == "var")
                    var = true;
                else if (nameNode.kind == "norec")
                    noRec = true;
                else
                    break;
            }
            if (!var && nameNode instanceof Sym) {
                s = ((Sym) nameNode).sym;
                if (inStruct && args.size() > first) {
                    if (s == "get") {
                        property = true;
                        nameNode = (Node) args.get(first++);
                    } else if (s == "set") {
                        property = true;
                        var = true;
                        nameNode = (Node) args.get(first++);
                    }
                }
            }
            if (first == 0 || first > args.size())
                throw new CompileException(nameNode,
                        "Variable name is missing");
            if (inStruct && nameNode.kind == "``")
                nameNode = ((XNode) nameNode).expr[0];
            if (!(nameNode instanceof Sym))
                throw new CompileException(nameNode, nameNode.kind == "class"
                            ? "Missing ; after class definition"
                            : "Illegal binding name: " + nameNode
                              + " (missing ; after expression?)");
            line = nameNode.line;
            col = nameNode.col;
            this.name = ((Sym) nameNode).sym;
            if (first < args.size() && args.get(first) instanceof BinOp &&
                ((s = ((BinOp) args.get(first)).op) == FIELD_OP || s == "#"))
                throw new CompileException((BinOp) args.get(first),
                    "Bad argument on binding (use := for assignment, not =)");
            int i = args.size() - 1;
            if (i >= first && args.get(i) instanceof IsOp) {
                type = ((IsOp) args.get(i)).type;
                --i;
            }
            for (; i >= first; --i)
                expr = XNode.lambda((Node) args.get(i), expr,
                        i == first ? nameNode : null);
            this.expr = expr;
        }

        String str() {
            StringBuffer s = new StringBuffer("(`let ");
            if (doc != null) {
                s.append("/**");
                s.append(doc);
                s.append(" */ ");
            }
            if (noRec)
                s.append("`norec ");
            if (property)
                s.append(var ? "`set " : "`get ");
            else if (var)
                s.append("`var ");
            s.append(name);
            s.append(' ');
            s.append(expr.str());
            s.append(')');
            return s.toString();
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
            StringBuffer res = new StringBuffer("(`begin");
            if (seqKind != null)
                res.append(':').append(seqKind);
            for (int i = 0; st != null && i < st.length; ++i) {
                res.append(' ').append(st[i].str());
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
            StringBuffer s = new StringBuffer().append('(');
            if (left == null)
                s.append("`flip ");
            if (op != "")
                s.append(op == FIELD_OP ? "`." : op).append(' ');
            if (left != null)
                s.append(left.str()).append(' ');
            if (right != null)
                s.append(right.str());
            return s.append(')').toString();
        }
    }

    final class TypeDef extends Node {
        static final int SHARED = 1;
        static final int OPAQUE = 2;
        String name;
        String[] param;
        String doc;
        TypeNode type;
        boolean exact;
        int kind;

        String str() {
            StringBuffer buf =
                new StringBuffer("(`typedef ").append(name).append(" (");
            for (int i = 0; i < param.length; ++i) {
                if (i != 0)
                    buf.append(' ');
                buf.append(param[i]);
            }
            return buf.append(") ").append(type.str()).append(')').toString();
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
            return "(`" + op + ' ' + (right == null ? "()" : right.str())
                    + ' ' + type.str() + ')';
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
            buf.append('#').append(name);
            if (arguments != null) {
                buf.append('(');
                for (int i = 0; i < arguments.length; ++i) {
                    if (i != 0)
                        buf.append(", ");
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
            super("instanceof", Parser.COMP_OP_LEVEL, true);
            postfix = true;
            this.className = className;
        }
    }

    class TypeNode extends Node {
        String name;
        TypeNode[] param;
        boolean var;
        String doc;

        TypeNode(String name, TypeNode[] param) {
            this.name = name;
            this.param = param;
        }

        String str() {
            if (name == "->")
                return "(" + param[0].str() + " -> " + param[1].str() + ")";
            StringBuffer buf = new StringBuffer();
            if (name == "|") {
                for (int i = 0; i < param.length; ++i)
                    buf.append(" | ").append(param[i].str());
                return buf.toString();
            }
            if (name == "") {
                buf.append('{');
                for (int i = 0; i < param.length; ++i) {
                    if (i != 0)
                        buf.append("; ");
                    buf.append(param[i].name);
                    buf.append(" is ");
                    buf.append(param[i].param[0].str());
                }
                buf.append('}');
                return buf.toString();
            }
            if (param == null || param.length == 0)
                return name;
            if (Character.isUpperCase(name.charAt(0)))
                return "(" + name + " " + param[0].str() + ")";
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
            if (op.op == "-" && lastOp || op.op == "\\"
                    || op.op == "throw" || op.op == "not") {
                if (!lastOp) {
                    apply(op);
                    to = cur;
                }
                if (op.op == "-")
                    op.prio = 1;
                to.left = to.right;
            } else if (lastOp) {
                throw new CompileException(op, "Do not stack operators");
            } else {
                while (to.parent != null && (to.postfix || to.prio < op.prio ||
                            to.prio == op.prio && op.toRight))
                    to = to.parent;
                op.right = to.right;
            }
            op.parent = to;
            to.right = op;
            cur = op;
            lastOp = !op.postfix;
        }

        void add(Node node) {
            if (node instanceof BinOp && ((BinOp) node).parent == null &&
                    (!lastOp || node.kind != "listop")) {
                addOp((BinOp) node);
            } else {
                if (!lastOp)
                    apply(node);
                lastOp = false;
                cur.left = cur.right;
                cur.right = node;
            }
        }

        Node result() {
            if (cur.left == null && cur.prio != -1 && cur.prio != 1 &&
                    cur.prio != Parser.NOT_OP_LEVEL &&
                    !cur.postfix || cur.right == null)
                throw new CompileException(cur, "Expecting some value"+cur);
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
            { "*", "/", "%" },
            { "+", "-" },
            { null }, // non-standard operators
            { "." },
            { "<", ">", "<=", ">=", "==", "!=", "=~", "!~" },
            { null }, // not
            { null }, // and or
            { "^" },
            { "::", ":.", "++" },
            { "|>" },
            { "is" },
            { ":=" },
            { null }, // loop
        };
        private static final int FIRST_OP_LEVEL = 3;
        private static final int COMP_OP_LEVEL = opLevel("<");
        static final int NOT_OP_LEVEL = COMP_OP_LEVEL + 1;
        static final int LIST_OP_LEVEL = NOT_OP_LEVEL + 3;
        static final int IS_OP_LEVEL = opLevel("is");
        private static final Eof EOF = new Eof("EOF");
        private char[] src;
        private int p;
        private Node eofWas;
        private int flags;
        private int line = 1;
        private int lineStart;
        private String yetiDocStr;
        private boolean yetiDocReset;
        XNode loads;
        String sourceName;
        String moduleName;
        int moduleNameLine;
        String topDoc;
        boolean isModule;
        boolean deprecated;

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

        int currentLine() {
            return line;
        }

        private int directive(int from, int to) {
            boolean doc = src[from] != '%';;
            if (doc && (flags & Compiler.GF_DOC) == 0)
                return to;
            ++from;
            String str = new String(src, from, to - from);
            if (doc) {
                yetiDocStr = yetiDocStr == null || yetiDocReset
                                ? str : yetiDocStr + '\n' + str;
                yetiDocReset = false;
            } else if (str.length() < 2) {
            } else if (str.charAt(0) == ':') {
                try {
                    line = Integer.parseInt(str.substring(1)) - 1;
                } catch (NumberFormatException ex) {
                    throw new CompileException(line, from - lineStart,
                                               "Bad line directive");
                }
            } else if (str.startsWith("FILE='")) {
                p = from + 6;
                sourceName = readAStr().str;
                return p > to ? p : to;
            }
            return to;
        }

        private int skipSpace() {
            char[] src = this.src;
            int i = p, sp;
            char c;
            yetiDocReset = true;
            for (;;) {
                while (i < src.length &&
                       ((c = src[i]) >= '\000' && c <= ' ' || c == 0xa0)) {
                    ++i;
                    if (c == '\n') {
                        ++line;
                        lineStart = i;
                    }
                }
                if (i + 1 < src.length && src[i] == '/') {
                    if (src[i + 1] == '/') {
                        sp = i += 2;
                        while (i < src.length && src[i] != '\n'
                                && src[i] != '\r') ++i;
                        if (i > sp && (src[sp] == '/' || src[sp] == '%'))
                            i = directive(sp, i);
                        continue;
                    }
                    if (src[i + 1] == '*') {
                        int l = line, col = i - lineStart + 1;
                        sp = i += 2;
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
                        if (i - 3 > sp && src[sp] == '*')
                            directive(sp, i - 2);
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
                                (i + 1 >= src.length ||
                                 (c = src[i + 1]) < '~' && CHS[c] == ' ')))
                        return new BinOp(".", COMP_OP_LEVEL - 1, true)
                            .pos(line, col);
                    break;
                case ';':
                    return new XNode(";").pos(line, col);
                case ',':
                    return new XNode(",").pos(line, col);
                case '(':
                    return readSeq(')', null);
                case '[':
                    return readList().pos(line, col);
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
                if (s == "=" || s == ":")
                    return new XNode(s).pos(line, col);
                if (s == ".")
                    return new BinOp(FIELD_OP, 0, true).pos(line, col);
                if (s == "#")
                    return readObjectRef().pos(line, col);
                for (i = OPS.length; --i >= 0;)
                    for (int j = OPS[i].length; --j >= 0;)
                        if (OPS[i][j] == s)
                            return new BinOp(s, i + FIRST_OP_LEVEL,
                                             i != LIST_OP_LEVEL - FIRST_OP_LEVEL)
                                         .pos(line, col);
                return new BinOp(s, FIRST_OP_LEVEL + 2, true).pos(line, col);
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
                res = new BinOp(s, NOT_OP_LEVEL + 1, true);
            } else if (s == "not") {
                res = new BinOp(s, NOT_OP_LEVEL, true);
            } else if (s == "then" || s == "elif" || s == "else" ||
                       s == "fi" || s == "of" || s == "esac" || s == "done" ||
                       s == "catch" || s == "finally" || s == "yrt") {
                res = new Eof(s);
            } else if (s == "case") {
                res = readCase();
            } else if (s == "in") {
                res = new BinOp(s, COMP_OP_LEVEL, true);
            } else if (s == "div" || s == "shr" || s == "shl" ||
                       s == "b_and" || s == "with") {
                res = new BinOp(s, FIRST_OP_LEVEL, true);
            } else if (s == "b_or" || s == "xor") {
                res = new BinOp(s, FIRST_OP_LEVEL + 1, true);
            } else if (s == "is" || s == "unsafely_as" || s == "as") {
                TypeNode t = readType(TYPE_NORMAL);
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
                res = new BinOp(s, IS_OP_LEVEL + 2, false);
            } else if (s == "import") {
                res = readImport();
            } else if (s == "load") {
                res = loads = new XNode(s, new Node[] {
                    readDotted("Expected module name after 'load', not a "),
                    loads });
            } else if (s == "classOf") {
                res = new XNode(s,
                            readDottedType("Expected class name, not a "));
            } else if (s == "typedef") {
                res = readTypeDef();
            } else if (s == "try") {
                res = readTry();
            } else if (s == "instanceof") {
                res = new InstanceOf(
                            readDotted("Expected class name, not a ").sym);
            } else if (s == "class") {
                res = readClassDef();
            } else {
                if (s.charAt(0) != '`') {
                    res = new Sym(s);
                } else if (p >= src.length || src[p] != '`') {
                    throw new CompileException(line, col, "Syntax error");
                } else if (s.length() == 1) {
                    do {
                        if (++p >= src.length || src[p] == '\n')
                            throw new CompileException(line, col,
                                        "Unterminated ``identifier");
                    } while (src[p - 1] != '`' || src[p] != '`');
                    s = new String(src, i + 1, p - i - 2).intern();
                    res = new XNode("``", new Sym(s));
                    ++p;
                } else {
                    ++p;
                    res = new BinOp(s.substring(1).intern(),
                                    FIRST_OP_LEVEL + 2, true);
                }
            }
            return res.pos(line, col);
        }

        private Node readList() {
            char c;
            int i = p;
            if (i + 1 < src.length && src[i] == ':' && src[i + 1] == ']') {
                p = i + 2;
                return new XNode("list");
            }
            Node[] elem = readMany(",", ']');
            if (elem.length != 1 || i <= 1 ||
                    (c = src[i - 2]) < '~' && CHS[c] == ' ' && c != ')' ||
                    elem[0] instanceof BinOp && ((BinOp) elem[0]).op == "..")
                return new XNode("list", elem);
            Node res = new ObjectRefOp(null, elem);
            res.kind = "listop";
            return res;
        }

        private Node def(List args, List expr, boolean structDef, String doc) {
            BinOp partial = null;
            String s = null;
            int i = 0, cnt = expr.size();
            if (cnt > 0) {
                Object o = expr.get(0);
                if (o instanceof BinOp
                    && (partial = (BinOp) o).parent == null
                    && partial.op != "\\" && partial.op != "-"
                    && partial.op != "throw" && partial.op != "not"
                    && partial.op != "#") {
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
                if (s == "loop" || s == "with" || s == "throw" ||
                        partial instanceof IsOp)
                    throw new CompileException(partial, "Special operator `" +
                                    s + "` cannot be used as a function");
                if (partial instanceof TypeOp) {
                    partial.right = new Sym(partial.hashCode() + partial.op);
                    partial.right.pos(partial.line, partial.col);
                    return XNode.lambda(partial.right, partial, null);
                }
                return new Sym(s).pos(partial.line, partial.col);
            }
            ParseExpr parseExpr = new ParseExpr();
            while (i < cnt) {
                parseExpr.add((Node) expr.get(i++));
            }
            Node e = parseExpr.result();
            if (s != null) {
                if (cnt < expr.size()) {
                    BinOp r = new BinOp("", 2, true);
                    r.parent = r; // so it would be considered "processed"
                    r.right = e;
                    r.left = e = new Sym(s);
                    e.line = partial.line;
                    e.col = partial.col;
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
                   : (bind = new Bind(args, e, structDef, doc)).name != "_" ? bind
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
            int dimensions = 0;
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
                    char c = p >= src.length ? '\000' : src[p];
                    if (c == '[') {
                        ++p;
                        args = new Node[] { readSeq(']', null) };
                        while (p + 1 < src.length &&
                               src[p] == '[' && src[p + 1] == ']') {
                            p += 2;
                            ++dimensions;
                        }
                        ++dimensions;
                        break;
                    }
                    if (c != '.' && c != '$') {
                        throw new CompileException(line, p - lineStart + 1,
                                    "Expecting constructor argument list");
                    }
                    ++p;
                    name += c == '.' ? '/' : c;
                }
            }
            Node[] ex = new Node[args.length + 1];
            for (int i = 0; i < dimensions; ++i)
                name += "[]";
            ex[0] = new Sym(name.intern());
            System.arraycopy(args, 0, ex, 1, args.length);
            return new XNode(dimensions == 0 ? "new" : "new-array", ex);
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
                                   i < src.length && src[i] == '('
                                        ? readArgs() : null);
        }

        private Node readIf() {
            Node cond = readSeqTo("then");
            Node expr = readSeq(' ', null);
            Node els;
            if (eofWas.kind == "elif") {
                els = readIf();
            } else {
                if (eofWas.kind == "else") {
                    if (src.length > p && src[p] == ':') {
                        ++p;
                        List l = new ArrayList();
                        while (!((els = fetch()) instanceof Eof) && els.kind != ";")
                            l.add(els);
                        if (l.size() == 0)
                            throw new CompileException(els, "Unexpected " + els);
                        if (els.kind == ";" ||
                                els.kind != "EOF" && els.kind.length() > 1)
                            p -= els.kind.length();
                        els = def(null, l, false, null);
                        eofWas = null;
                    } else {
                        els = readSeq(' ', null);
                    }
                } else {
                    els = eofWas;
                }
                if (eofWas != null && eofWas.kind != "fi") {
                    throw new CompileException(eofWas,
                        "Expected fi, found " + eofWas);
                }
            }
            return new XNode("if", new Node[] { cond, expr, els });
        }

        private void addCase(List cases, XNode choice, List expr) {
            if (expr.size() == 0)
                throw new CompileException(choice, "Missing expression");
            Node code;
            if (expr.size() == 1) {
                code = (Node) expr.get(0);
            } else {
                code = new Seq((Node[]) expr.toArray(new Node[expr.size()]),
                               null).pos(choice.line, choice.col);
            }
            choice.expr = new Node[] { choice.expr[0], code };
            cases.add(choice);
        }

        private Node readCase() {
            Node val = readSeqTo("of");
            Node[] statements = readMany(";", ' ');
            if (eofWas.kind != "esac") {
                throw new CompileException(eofWas,
                    "Expected esac, found " + eofWas);
            }
            List cases = new ArrayList(statements.length + 1);
            cases.add(val);
            XNode pattern = null;
            List expr = new ArrayList();
            for (int i = 0; i < statements.length; ++i) {
                if (statements[i].kind == ":") {
                    if (pattern != null) {
                        addCase(cases, pattern, expr);
                        expr.clear();
                    }
                    pattern = (XNode) statements[i];
                } else if (statements[i] instanceof Sym
                            && statements[i].sym() == "...") {
                    if (i == 0 || i != statements.length - 1) {
                        throw new CompileException(statements[i],
                                    "Unexpected ...");
                    }
                    addCase(cases, pattern, expr);
                    pattern = null;
                    cases.add(new XNode("...", statements[i]));
                } else if (pattern != null) {
                    expr.add(statements[i]);
                } else {
                    throw new CompileException(statements[i],
                                "Expecting option, not a " + statements[i]);
                }
            }
            if (pattern != null)
                addCase(cases, pattern, expr);
            return new XNode("case-of",
                             (Node[]) cases.toArray(new Node[cases.size()]));
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
                c.expr[0] = readDotted("Expected exception name, not ");
                Node n = fetch();
                if (n instanceof Sym) {
                    c.expr[1] = n;
                    n = fetch();
                }
                if (n.kind != ":") {
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

        private Sym readDottedType(String what) {
            Sym t = readDotted(what);
            int s = p;
            while (src.length > p + 1 && src[p] == '[' && src[p + 1] == ']')
                p += 2;
            if (s != p)
                t.sym = t.sym.concat(new String(src, s, p - s)).intern();
            return t;
        }

        private Node readArgDefs() {
            int line_ = line, col_ = p++ - lineStart + 1;
            List args = new ArrayList();
            while ((p = skipSpace()) < src.length && src[p] != ')') {
                if (args.size() != 0 && src[p++] != ',') {
                    throw new CompileException(line, p - lineStart,
                                               "Expecting , or )");
                }
                args.add(readDottedType("Expected argument type, found "));
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

        private static final String EXPECT_DEF =
                "Expected field or method definition, found";

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
            yetiDocStr = null;
            List l = new ArrayList();
            node = readDottedType("Expected extends, field or "
                                  + "method definition, found ");
            Node epos = node;
            if (node.sym() == "extends") {
                do {
                    l.add(readDotted("Expected a class name, found "));
                    int line_ = line, col_ = p - lineStart + 1;
                    l.add(new XNode("arguments", readArgs()).pos(line_, col_));
                } while ((p = skipSpace()) < src.length && src[p++] == ',');
                --p;
                node = readDottedType(EXPECT_DEF);
            }
            defs.add(new XNode("extends", (Node[]) l.toArray(
                            new Node[l.size()])).pos(epos.line, epos.col));
            l.clear();
            eofWas = node;
        collect:
            while (!(eofWas instanceof Sym) || ((Sym) eofWas).sym != "end") {
                if (node == null)
                    node = readDottedType(EXPECT_DEF);
                String vsym = node.sym();
                if (vsym == "var" || vsym == "norec") {
                    l.add(new XNode(vsym).pos(node.line, node.col));
                    node = fetch();
                }
                String doc = yetiDocStr;
                String meth = "method";
                Node args = null;
                while (node instanceof Sym) {
                    p = skipSpace();
                    if (p < src.length && src[p] == '(') {
                        if (meth == "error")
                            throw new CompileException(line,
                                        p - lineStart + 1,
                                        "Static method cannot be abstract");
                        if (meth != "method")
                            l.remove(0);
                        if (l.size() == 0)
                            throw new CompileException(line, p - lineStart + 1,
                                            "Expected method name, found (");
                        if (l.size() == 1)
                            args = readArgDefs();
                        break;
                    }
                    if (((Sym) node).sym == "end" && l.size() == 0)
                        break collect;
                    l.add(node);
                    String s;
                    if ((s = node.sym()) == "static" || s == "abstract") {
                        meth = meth != "method" ? "error" :
                            s == "static" ? "static-method" : "abstract-method";
                        node = readDottedType(EXPECT_DEF);
                    } else {
                        node = fetch();
                    }
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
                Node expr;
                if (meth == "abstract-method") {
                    expr = null;
                    eofWas = fetch();
                } else {
                    expr = readSeq('e', null);
                }
                if (eofWas.kind != "," && (!(eofWas instanceof Sym) ||
                                           ((Sym) eofWas).sym != "end"))
                    throw new CompileException(eofWas, "Unexpected " + eofWas);
                if (args == null) {
                    defs.add(new Bind(l, expr, false, doc));
                } else {
                    Node[] m = expr != null
                        ? new Node[] { (Node) l.get(0), node, args, expr }
                        : new Node[] { (Node) l.get(0), node, args };
                    defs.add(new XNode(meth, m).pos(node.line, node.col));
                }
                l.clear();
                node = null;
                yetiDocStr = null;
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
                if (arg.kind == ":") {
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

        private Sym readDotted(String err) {
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
                if (p >= src.length || src[p] != '.')
                    break;
                ++p;
                result += "/";
            }
            Sym sym = new Sym(result.intern());
            sym.pos(first.line, first.col);
            return sym;
        }

        private XNode readImport() {
            Sym s = readDotted("Expected class path after 'import', not a ");
            ArrayList imports = null;
            for (char c = ':'; ((p = skipSpace()) < src.length &&
                                src[p] == c); c = ',') {
                ++p;
                if (imports == null)
                    imports = new ArrayList();
                imports.add(new Sym(s.sym + '/' + fetch().sym()));
            }
            return imports == null ? new XNode("import", s) :
                        new XNode("import", (Node[])
                                    imports.toArray(new Node[imports.size()]));
        }

        private Node[] readMany(String sep, char end) {
            List res = new ArrayList();
            List args = null;
            List l = new ArrayList();
            String doc = null;
            // TODO check for (blaah=) error
            Node sym;
            yetiDocStr = null;
            while (!((sym = fetch()) instanceof Eof)) {
                if (doc == null)
                    doc = yetiDocStr;
                // hack for class end - end is not reserved word
                if (end == 'e' && sym instanceof Sym && sym.sym() == "end")
                    break;
                if (sym.kind == ":" && args == null) {
                    if (l.size() == 0)
                        throw new CompileException(sym, "Unexpected `:'");
                    XNode colon = (XNode) sym;
                    colon.expr = new Node[] { def(null, l, false, null) };
                    colon.doc = doc;
                    doc = null;
                    yetiDocStr = null;
                    l = new ArrayList();
                    res.add(sym);
                    continue;
                }
                if (sym.kind == "=") {
                    args = l;
                    if (end == '}') {
                        l = Collections.singletonList(readSeq(' ', "{}"));
                        if ((sym = eofWas) instanceof Eof)
                            break;
                    } else {
                        l = new ArrayList();
                        continue;
                    }
                }
                if (sym.kind == ";" || sym.kind == ",") {
                    if (sym.kind != sep)
                        break;
                    if (args == null && sep == ";" && l.size() == 0)
                        continue;
                } else {
                    l.add(sym);
                    if (sep != ";" || !(sym instanceof TypeDef))
                        continue; // look for next in line
                }
                if (l.size() == 0)
                    throw new CompileException(sym, "Unexpected " + sym);
                res.add(def(args, l, end == '}', doc));
                if (args != null)
                    doc = null;
                args = null;
                l = new ArrayList();
                yetiDocStr = null;
            }
            eofWas = sym;
            if (end != ' ' && end != 'e' &&
                (p >= src.length || src[p++] != end)) {
                throw new CompileException(line, p - lineStart + 1,
                                           "Expecting " + end);
            }
            if (l.size() != 0)
                res.add(def(args, l, end == '}', doc));
            else if (args != null)
                throw new CompileException(line, p - lineStart,
                                           "Expression missing after `='");
            return (Node[]) res.toArray(new Node[res.size()]);
        }

        private Node readSeq(char end, Object kind) {
            String doc = yetiDocStr;
            Node[] list = readMany(";", end);
            if (list.length == 1 && kind != Seq.EVAL) {
                if (doc != null && list[0] instanceof Sym)
                    yetiDocStr = doc;
                return list[0];
            }
            if (list.length == 0) {
                return new XNode("()", end == ')' ? null : new Node[0])
                            .pos(line, p - lineStart);
            }
            // find last element for line/col position
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
            boolean tripleQuote;
            if (p + 1 < src.length && src[p] == '"' && src[p + 1] == '"') {
                st = p += 2;
                tripleQuote = true;
            } else {
                tripleQuote = false;
            }
            for (; p < src.length &&
                    (src[p] != '"' || tripleQuote && p + 2 < src.length &&
                     src[p + 1] != '"' && src[p + 2] != '"'); ++p) {
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
                        case 'a':
                            res.append('\u0007');
                            break;
                        case 'b':
                            res.append('\b');
                            break;
                        case 'f':
                            res.append('\f');
                            break;
                        case 'n':
                            res.append('\n');
                            break;
                        case 'r':
                            res.append('\r');
                            break;
                        case 't':
                            res.append('\t');
                            break;
                        case 'e':
                            res.append('\u001b');
                            break;
                        case '0':
                            res.append('\000');
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
                        case 'u': {
                            st += 4;
                            if (st > src.length)
                                st = src.length;
                            int n = st - p;
                            String s = new String(src, p + 1, n);
                            if (n == 4) {
                                try {
                                    res.append((char) Integer.parseInt(s, 16));
                                    break;
                                } catch (NumberFormatException ex) {
                                }
                            }
                            throw new CompileException(line, p - lineStart,
                                "Invalid unicode escape code \\u" + s);
                        }
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
                throw new CompileException(sline, scol,
                        tripleQuote ? "Unclosed \"\"\"" : "Unclosed \"");
            }
            res.append(src, st, p++ - st);
            if (tripleQuote) {
                p += 2;
            }
            if (parts == null) {
                return new Str(res.toString());
            }
            if (res.length() != 0) {
                parts.add(new Str(res.toString()));
            }
            return new XNode("concat", (Node[]) parts.toArray(
                                            new Node[parts.size()]));
        }

        private Str readAStr() {
            int i = p, sline = line, scol = i - lineStart;
            String s = "";
            do {
                for (; i < src.length && src[i] != '\''; ++i)
                    if (src[i] == '\n') {
                        lineStart = i + 1;
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
            if (!(node instanceof Sym))
                throw new CompileException(node,
                            "Expected typename, not a " + node);
            String s = ((Sym) node).sym;
            if (!Character.isLowerCase(s.charAt(0)) && s.charAt(0) != '_')
                throw new CompileException(node,
                            "Typename must start with lowercase character");
            return s;
        }

        TypeDef readTypeDef() {
            TypeDef def = new TypeDef();
            def.doc = yetiDocStr;
            yetiDocStr = null;
            def.name = getTypename(fetch());
            List param = new ArrayList();
            Node node = fetch();
            if (def.name == "opaque")
                def.kind = TypeDef.OPAQUE;
            else if (def.name == "shared" && node instanceof Sym)
                def.kind = TypeDef.SHARED;
            if (def.kind != 0) {
                def.name = getTypename(node);
                node = fetch();
            }
            if (def.kind != TypeDef.SHARED && node instanceof BinOp &&
                ((BinOp) node).op == "!") {
                def.exact = true;
                node = fetch();
            }
            if (node instanceof BinOp && ((BinOp) node).op == "<" &&
                def.kind != TypeDef.SHARED) {
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
            if ((def.type = readType(TYPE_NORMAL)) == null)
                throw new CompileException(node,
                            "Missing type in typedef declaration");
            return def;
        }

        // ugly all-in-one bastard type expression parser
        private static final int TYPE_NORMAL = 0;
        private static final int TYPE_FUNRET = 1;
        private static final int TYPE_VARIANT = 2;
        private static final int TYPE_VARIANT_ARG = 3;

        TypeNode readType(int checkVariant) {
            yetiDocStr = null;
            int i = skipSpace();
            if (p >= src.length || src[i] == ')' || src[i] == '>') {
                p = i;
                return null;
            }
            int sline = line, scol = i - lineStart;
            TypeNode res;
            if (src[i] == '(') {
                p = i + 1;
                res = readType(TYPE_NORMAL);
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
                    yetiDocStr = null;
                    boolean isVar = (field = fetch()).kind == "var";
                    if (isVar)
                        field = fetch();
                    String fieldName;
                    if (field instanceof BinOp &&
                        ((BinOp) field).op == FIELD_OP &&
                        (field = fetch()) instanceof Sym) {
                        fieldName = ".".concat(field.sym()).intern();
                    } else if (!(field instanceof Sym)) {
                        if (isVar)
                            throw new CompileException(field,
                                "Exepcting field name after var");
                        break;
                    } else {
                        fieldName = field.sym();
                    }
                    TypeNode f = new TypeNode(fieldName, new TypeNode[1]);
                    f.var = isVar;
                    f.doc = yetiDocStr;
                    if (!((t = fetch()) instanceof IsOp) ||
                            ((BinOp) t).right != null) {
                        throw new CompileException(t,
                            "Expecting 'is' after field name");
                    }
                    f.param[0] = ((IsOp) t).type;
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
            } else do {
                int start = i;
                char c = ' ', dot = '.';
                if (i < src.length && ((c = src[i]) == '~' || c == '^'))
                    ++i;
                boolean maybeArr = c == '~' || c == '\'';
                if (c != '.') {
                    if (Character.isUpperCase(c))
                        dot = '_';
                    while (i < src.length && ((c = src[i]) > '~' ||
                                CHS[c] == 'x' || c == dot || c == '$'))
                        ++i;
                    while (src[i - 1] == '.')
                        --i;
                }
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
                if (dot == '_') {
                    String doc = yetiDocStr;
                    if (i < src.length && src[i] == '.')
                        ++p;
                    else
                        sym = ".".concat(sym);
                    TypeNode node = readType(TYPE_VARIANT_ARG);
                    if (node == null)
                        throw new CompileException(line, p - lineStart,
                                        "Expecting variant argument");
                    node = new TypeNode(sym, new TypeNode[] { node });
                    node.doc = doc;
                    node.pos(sline, scol);
                    if (checkVariant == TYPE_VARIANT)
                        return node;
                    param.add(node);
                    if (checkVariant != TYPE_VARIANT_ARG) {
                        while ((p = skipSpace() + 1) < src.length &&
                               src[p - 1] == '|' &&
                               (node = readType(TYPE_VARIANT)) != null)
                            param.add(node);
                        --p;
                    }
                    res = (TypeNode)
                        new TypeNode("|", (TypeNode[]) param.toArray(
                                new TypeNode[param.size()])).pos(sline, scol);
                    break; // break do...while, go check for ->
                }
                if ((p = skipSpace()) < src.length && src[p] == '<') {
                    ++p;
                    for (TypeNode node; (node = readType(TYPE_NORMAL)) != null;
                            ++p) {
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
            } while (false);
            if (checkVariant == TYPE_VARIANT)
                throw new CompileException(res, "Invalid `| " + res.str() +
                        "' in variant type (expecting Tag after `|')");
            p = i = skipSpace();
            if (checkVariant == TYPE_VARIANT_ARG || i + 1 >= src.length ||
                    src[i] != '\u2192' && (src[i] != '-' || src[++i] != '>'))
                return res;
            sline = line;
            scol = p - lineStart;
            p = i + 1;
            TypeNode arg = readType(TYPE_FUNRET);
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
            topDoc = yetiDocStr;
            while (i < src.length && src[i] < '~' && CHS[src[i]] == 'x')
                ++i;
            String s = new String(src, p, i - p);
            if (s.equals("module") || s.equals("program")) {
                p = i;
                Sym name = readDotted("Expected " + s + " name, not a ");
                moduleName = name.sym;
                moduleNameLine = name.line;
                isModule = s.equals("module");

                if (isModule) {
                    if (p < src.length && src[p] == ':') {
                        ++p;
                        Node node = fetch();
                        if (node.sym() != "deprecated")
                            throw new CompileException(node,
                                        "Unknown module attribute: " + node);
                        deprecated = true;
                        p = skipSpace();
                    }
                }
                if (p >= src.length || src[p++] != ';')
                    throw new CompileException(line, p - lineStart,
                                               "Expected ';' here");
            }
            char first = p < src.length ? src[p] : ' ';
            Node res;
            if ((flags & Compiler.CF_EVAL_STORE) != 0) {
                res = readSeq(' ', Seq.EVAL);
                if (res instanceof Seq) {
                    Seq seq = (Seq) res;
                    Node last = seq.st[seq.st.length - 1];
                    if (last instanceof Bind ||
                        last.kind == "struct-bind" ||
                        last.kind == "import" ||
                        last instanceof TypeDef) {
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
                if (res.kind == "class")
                    res = new Seq(new Node[] { res }, topLevel)
                                .pos(res.line, res.col);
            }
            if (eofWas != EOF) {
                throw new CompileException(eofWas, "Unexpected " + eofWas);
            }
            return res;
        }
    }
}
