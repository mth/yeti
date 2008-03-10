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

        String show() {
            return str();
        }

        String str() {
            return toString();
        }

        String showList(char open, char close, Node[] list) {
            StringBuffer res = new StringBuffer();
            res.append(open);
            if (list == null) {
                res.append(':');
            } else {
                for (int i = 0; i < list.length; ++i) {
                    if (i != 0) {
                        res.append(", ");
                    }
                    res.append(list[i].show());
                }
            }
            res.append(close);
            return res.toString();
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
    }

    class BindOp extends Node {
    }

    class SepOp extends Node {
        char sep;

        SepOp(char sep) {
            this.sep = sep;
        }
    }

    class VarSym extends Node {
    }

    class NoRecSym extends Node {
    }

    class ThrowSym extends Node {
    }

    class UnitLiteral extends Node {
        String str() {
            return "()";
        }
    }

    class Bind extends Node {
        String name;
        Node expr;
        TypeNode type;
        boolean var;
        boolean noRec;

        Bind(List args, Node expr) {
            int first = 0;
            Node nameNode = null;
            while (first < args.size()) {
                nameNode = (Node) args.get(first);
                ++first;
                if (nameNode instanceof VarSym) {
                    var = true;
                } else if (nameNode instanceof NoRecSym) {
                    noRec = true;
                } else {
                    break;
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
            line = arg.line;
            col = arg.col;
        }

        String str() {
            return "\\" + arg.show() + " -> " + expr.show();
        }
    }

    class Load extends Node {
        String moduleName;

        Load(String moduleName) {
            this.moduleName = moduleName;
        }

        String str() {
            return "load " + moduleName.replace('/', '.');
        }
    }

    class Import extends Node {
        String className;

        Import(String className) {
            this.className = className;
        }

        String str() {
            return "import " + className.replace('/', '.');
        }
    }

    class Seq extends Node {
        boolean isEvalSeq;
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

        public String toString() {
            return sym;
        }
    }

    class Str extends Node {
        String str;

        Str(String str) {
            this.str = str;
        }

        String str() {
            return '"' + str + '"';
        }
    }

    class ConcatStr extends Node {
        Node[] param;

        ConcatStr(Node[] param) {
            this.param = param;
        }

        String str() {
            StringBuffer buf = new StringBuffer("\"");
            for (int i = 0; i < param.length; ++i) {
                if (param[i] instanceof Str) {
                    buf.append(((Str) param[i]).str);
                } else {
                    buf.append("\\(");
                    buf.append(param[i].show());
                    buf.append(")");
                }
            }
            buf.append('"');
            return buf.toString();
        }
    }

    class NumLit extends Node {
        Num num;

        NumLit(Num num) {
            this.num = num;
        }

        String str() {
            return String.valueOf(num);
        }
    }

    class Try extends Node {
        Node block;
        Catch[] catches;
        Node cleanup;

        String str() {
            StringBuffer buf = new StringBuffer("try\n");
            for (int i = 0; i < catches.length; ++i)
                buf.append(catches[i].str());
            if (cleanup != null) {
                buf.append("\nfinally\n");
                buf.append(cleanup.str());
            }
            buf.append("\nyrt\n");
            return buf.toString();
        }
    }

    class Catch extends Eof {
        String exception;
        String bind;
        Node handler;

        String str() {
            return "\ncatch " + exception + ' ' + bind + ":\n" + handler.str();
        }
    }

    class Finally extends Eof {
    }

    class Yrt extends Finally {
    }

    class Eof extends Node {
    }

    class CloseBracket extends Eof {
        char c;

        CloseBracket(char c) {
            this.c = c;
        }

        String show() {
            return new String(new char[] { c });
        }
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
            return '(' + (left == null ? "<>" : left.show()) + ' ' + op + ' '
                       + (right == null ? "<>" : right.show()) + ')';
        }
    }

    class RSection extends Node {
        String sym;
        Node arg;

        RSection(String sym, Node arg) {
            this.sym = sym;
            this.arg = arg;
        }

        String str() {
            return "(" + sym + " " + arg.str() + ")";
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
            return (right == null ? "<>" : right.show())
                        + ' ' + op + ' ' + type.str();
        }
    }

    class IsOp extends TypeOp {
        IsOp(TypeNode type) {
            super("is", type);
        }
    }

    class ObjectRefOp extends BinOp {
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
                new StringBuffer(right == null ? "<>" : right.show());
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

    class NewOp extends Node {
        String name;
        Node[] arguments;

        NewOp(String name, Node[] arguments) {
            this.name = name.intern();
            this.arguments = arguments;
        }

        String str() {
            StringBuffer buf = new StringBuffer("new ");
            buf.append(name);
            buf.append('(');
            for (int i = 0; i < arguments.length; ++i) {
                if (i != 0)
                    buf.append(", ");
                buf.append(arguments[i].str());
            }
            return buf + ")";
        }
    }

    class ClassOf extends Node {
        String className;

        ClassOf(String className) {
            this.className = className.intern();
        }

        String str() {
            return "classOf " + className;
        }
    }

    class TypeNode extends Node {
        String name;
        TypeNode[] param;

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

    class ParseExpr {
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
            if (op.op == "-" && lastOp || op.op == "\\") {
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

    class Parser {
        private static final char[] CHS =
           ("                                " + // 0x
            " .'....x  .. ../xxxxxxxxxx. ...x" + // 2x
            ".xxxxxxxxxxxxxxxxxxxxxxxxxx[ ].x" + // 4x
            "`xxxxxxxxxxxxxxxxxxxxxxxxxx . . ").toCharArray();

        private static final String[][] OPS = {
            { "*", "/", "%" },
            { "+", "-" },
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
        private static final Eof EOF = new Eof() {
            public String toString() {
                return "EOF";
            }
        };
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
                    return new SepOp(';').pos(line, col);
                case ',':
                    return new SepOp(',').pos(line, col);
                case '(':
                    return readSeq(')');
                case '[':
                    if (i + 2 < src.length && src[i + 1] == ':' &&
                        src[i + 2] == ']') {
                        p = i + 3;
                        return new NList(null).pos(line,col);
                    }
                    return new NList(readMany(',', ']')).pos(line, col);
                case '{':
                    return new Struct(readMany(';', '}')).pos(line, col);
                case ')': case ']': case '}':
                    p = i;
                    return new CloseBracket(src[i]).pos(line, col);
                case '"':
                    return readStr().pos(line, col);
                case '\'':
                    return readAStr().pos(line, col);
                case '\\':
                    return new BinOp("\\", 1, false).pos(line, col);
            }
            p = i;
            while (i < src.length && (c = src[i]) <= '~' && (CHS[c] == '.' ||
                   c == '/' && (i + 1 >= src.length ||
                                (c = src[i + 1]) != '/' && c != '*'))) ++i;
            if (i != p) {
                String s = new String(src, p, i - p).intern();
                p = i;
                if (s == "=")
                    return new BindOp().pos(line, col);
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
            } else if (s == "elif") {
                res = new Elif();
            } else if (s == "else") {
                res = new Else();
            } else if (s == "fi") {
                res = new Fi();
            } else if (s == "case") {
                res = readCase();
            } else if (s == "esac") {
                res = new Esac();
            } else if (s == "do") {
                res = readDo();
            } else if (s == "done") {
                res = new Done();
            } else if (s == "and" || s == "or") {
                res = new BinOp(s, COMP_OP_LEVEL + 1, true);
            } else if (s == "in") {
                res = new BinOp(s, COMP_OP_LEVEL, true);
            } else if (s == "div" || s == "shr" || s == "shl") {
                res = new BinOp(s, FIRST_OP_LEVEL, true);
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
            } else if (s == "var") {
                res = new VarSym();
            } else if (s == "norec") {
                res = new NoRecSym();
            } else if (s == "loop") {
                res = new BinOp(s, IS_OP_LEVEL, false);
            } else if (s == "load") {
                res = new Load(readDotted(false,
                                "Expected module name after 'load', not a "));
            } else if (s == "try") {
                res = readTry();
            } else if (s == "catch") {
                res = new Catch();
            } else if (s == "finally") {
                res = new Finally();
            } else if (s == "yrt") {
                res = new Yrt();
            } else if (s == "import") {
                res = new Import(readDotted(false,
                                 "Expected class path after 'import', not a "));
            } else if (s == "throw") {
                res = new ThrowSym();
            } else if (s == "classOf") {
                res = new ClassOf(
                            readDotted(false, "Expected class name, not a "));
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

        private Node def(List args, List expr) {
            BinOp partial = null;
            String s = null;
            int i = 0, cnt = expr.size();
            if (cnt > 0) {
                Object o = expr.get(0);
                if (o instanceof BinOp
                    && (partial = (BinOp) o).parent == null
                    && partial.op != "\\" && partial.op != "-") {
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
                    e = new RSection(s, parseExpr.result());
                }
                e.line = partial.line;
                e.col = partial.col;
            }
            return args == null ? e : new Bind(args, e);
        }

        private Node readExpr(String to) {
            for (List res = new ArrayList();;) {
                Node node = fetch();
                if (node instanceof Eof) {
                    throw new CompileException(node,
                                    to + " expected, not " + node);
                }
                if (node instanceof Sym && to == ((Sym) node).sym) {
                    if (res.isEmpty()) {
                        throw new CompileException(node, "no condition?");
                    }
                    return def(null, res);
                }
                res.add(node);
            }
        }

        private Node[] readArgs() {
            if ((p = skipSpace()) >= src.length || src[p] != '(') {
                return null;
            }
            ++p;
            return readMany(',', ')');
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
            return new NewOp(name, args);
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
            List branches = new ArrayList();
            do {
                Node cond = readExpr("then");
                branches.add(new Node[] { readSeq(' '), cond });
            } while (eofWas instanceof Elif);
            branches.add(new Node[] {
                eofWas instanceof Else ? readSeq(' ')
                    : new UnitLiteral().pos(eofWas.line, eofWas.col) });
            if (!(eofWas instanceof Fi)) {
                throw new CompileException(eofWas,
                    "Expected fi, found " + eofWas);
            }
            return new Condition((Node[][]) branches.toArray(
                            new Node[branches.size()][]));
        }

        private Node readCase() {
            Node val = readExpr("of");
            Node[] choices = readMany(';', ' ');
            if (!(eofWas instanceof Esac)) {
                throw new CompileException(eofWas,
                    "Expected esac, found " + eofWas);
            }
            return new Case(val, choices);
        }

        private Node readTry() {
            List catches = new ArrayList();
            Try t = new Try();
            t.block = readSeq(' ');
            while (!(eofWas instanceof Finally)) {
                if (!(eofWas instanceof Catch)) {
                    throw new CompileException(eofWas,
                        "Expected finally or yrt, found " + eofWas);
                }
                Catch c = (Catch) eofWas;
                catches.add(c);
                c.exception =
                    readDotted(false, "Expected exception name, not ").intern();
                Node n = fetch();
                if (n instanceof Sym) {
                    c.bind = ((Sym) n).sym;
                    n = fetch();
                }
                if (!(n instanceof BinOp) || ((BinOp) n).op != ":") {
                    throw new CompileException(n, "Expected ':'" +
                        (c.bind == null ? " or identifier" : "") +
                        ", but found " + n);
                }
                c.handler = readSeq(' ');
            }
            t.catches = (Catch[]) catches.toArray(new Catch[catches.size()]);
            if (!(eofWas instanceof Yrt)) {
                t.cleanup = readSeq(' ');
                if (!(eofWas instanceof Yrt)) {
                    throw new CompileException(eofWas,
                        "Expected yrt, found " + eofWas);
                }
            } else if (t.catches.length == 0) {
                throw new CompileException(eofWas,
                    "try block must contain at least one catch or finally");
            }
            return t;
        }

        private Node readDo() {
            for (List args = new ArrayList();;) {
                Node arg = fetch();
                if (arg instanceof Eof) {
                    throw new CompileException(arg, "Unexpected " + arg);
                }
                if (arg instanceof BinOp && ((BinOp) arg).op == ":") {
                    Node expr = readSeq(' ');
                    if (!(eofWas instanceof Done)) {
                        throw new CompileException(eofWas,
                                    "Expected done, found " + eofWas);
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

        private String readDotted(boolean decl, String err) {
            String result = "";
            for (;;) {
                Node n = fetch();
                if (!(n instanceof Sym)) {
                    throw new CompileException(n, err + n);
                }
                result += ((Sym) n).sym;
                p = skipSpace();
                if (p >= src.length)
                    return result;
                if (src[p] != '.') {
                    if (!decl)
                        return result;
                    if (src[p] == ';') {
                        ++p;
                        return result;
                    }
                    throw new CompileException(n, "Expected ';', not a " + n);
                }
                ++p;
                result += "/";
            }
        }

        private Node[] readMany(char sep, char end) {
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
                if (sym instanceof SepOp && ((SepOp) sym).sep == sep) {
                    res.add(def(args, l));
                    args = null;
                    l = new ArrayList();
                    continue;
                }
                l.add(sym);
            }
            eofWas = sym;
            if (end != ' ' && (p >= src.length || src[p++] != end)) {
                throw new CompileException(line, p - lineStart + 1,
                                           "Expecting " + end);
            }
            if (!l.isEmpty()) {
                res.add(def(args, l));
            }
            return (Node[]) res.toArray(new Node[res.size()]);
        }

        private Node readSeq(char end) {
            Node[] list = readMany(';', end);
            if (list.length == 1) {
                return list[0];
            }
            if (list.length == 0) {
                return new UnitLiteral().pos(line, p - lineStart);
            }
            Node w = list[list.length - 1];
            for (BinOp bo; w instanceof BinOp &&
                           (bo = (BinOp) w).left != null;) {
                w = bo.left;
            }
            return new Seq(list).pos(w.line, w.col);
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
                            parts.add(readSeq(')'));
                            res.setLength(0);
                            st = --p;
                            break;
                        default:
                            throw new CompileException(line, p - lineStart,
                                "Unexpected escape: \\" + src[p]);
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
            return new ConcatStr((Node[]) parts.toArray(
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
                while ((field = fetch()) instanceof Sym) {
                    if (!((t = fetch()) instanceof IsOp) ||
                        ((BinOp) t).right != null) {
                        throw new CompileException(t,
                            "Expecting 'is' after field name");
                    }
                    param.add(new TypeNode(((Sym) field).sym,
                                new TypeNode[] { ((IsOp) t).type }));
                    if (!((field = fetch()) instanceof SepOp) ||
                        ((SepOp) field).sep != ';') {
                        expect = "Expecting ';' or '}' here, not ";
                        break;
                    }
                }
                if (!(field instanceof CloseBracket) || src[p++] != '}') {
                    throw new CompileException(field, expect + field);
                }
                res = new TypeNode("",
                        (TypeNode[]) param.toArray(new TypeNode[param.size()]));
                res.pos(sline, scol);
            } else {
                int start = i;
                char c, dot = '_';
                if (i < src.length && src[i] == '~') {
                    ++i;
                    dot = '.';
                }
                while (i < src.length && ((c = src[i]) > '~' || CHS[c] == 'x'
                                          || c == dot || c == '$'))
                    ++i;
                while (dot != '_' && i + 1 < src.length && // java arrays
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

        Node parse() {
            int i = p = skipSpace();
            while (i < src.length && CHS[src[i]] == 'x')
                ++i;
            String s = new String(src, p, i - p);
            if (s.equals("module") || s.equals("program")) {
                p = i;
                moduleName = readDotted(true,
                    "Expected " + s + " name, not a ");
                isModule = s.equals("module");
            }
            Node res = readSeq(' ');
            if ((flags & YetiC.CF_EVAL) != 0) {
                if (res instanceof Bind) {
                    res = new Seq(new Node[] { res }).pos(res.line, res.col);
                }
                if (res instanceof Seq) {
                    Seq seq = (Seq) res;
                    seq.isEvalSeq = true;
                    if (seq.st[seq.st.length - 1] instanceof Bind) {
                        Node[] tmp = new Node[seq.st.length + 1];
                        System.arraycopy(seq.st, 0, tmp, 0, seq.st.length);
                        tmp[tmp.length - 1] =
                            new UnitLiteral().pos(seq.line, seq.col);
                        seq.st = tmp;
                    }
                }
            }
            if (eofWas != EOF) {
                throw new CompileException(eofWas, "Unexpected " + eofWas);
            }
            return res;
        }
    }
}
