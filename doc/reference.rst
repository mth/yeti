.. ex: se sw=4 sts=4 expandtab:

================================
Yeti language reference manual
================================

:Author: Madis Janson

.. contents:: Contents
.. _yeti.jar: http://dot.planet.ee/yeti/yeti.jar
.. _home page: http://mth.github.io/yeti/
.. _Mouse parser generator: http://mousepeg.sourceforge.net/Manual.pdf

Grammar
~~~~~~~~~~
The Yeti grammar represented here is written as a parsing expression
grammar.

The choice of PEG representation may seem odd, but the nature of Yeti syntax
meant that it was easiest to use PEG for writing a full grammar that can
be compiled into actual parser without any hacks (might be because the Yeti
compiler uses a hand-written recursive-descent parser, which has quite similar
logic to PEG grammars).

The grammar can be extracted from this manual and compiled into runnable
parser by invoking following ant target in the Yeti source tree root::

    ant grammar

Mouse parser generator is used and the resulting ``yeti-peg.jar`` can be
invoked using ``java -jar`` at command line. The PEG grammar given here
therefore follows the exact syntax used by the `Mouse parser generator`_.

The Yeti source code is always read assuming UTF-8 encoding, regardless
of the locale settings.

.. peg

::

    Source      = SP TopLevel !_;


Tokens
+++++++++

Reserved words
-----------------
.. peg

::

    KeywordOp   = "and" / "b\_and" / "b\_or" / "div" / "in" / "not" / "or" /
                  "shl" / "shr" / "xor";
    Keyword     = "instanceof" / KeywordOp / "as" / "case" / "catch" / "class" /
                  "classOf" / "done" / "do" / "elif" / "else" / "esac" /
                  "fall" / "finally" / "fi" / "if" / "import" / "is" / "load" /
                  "loop" / "new" / "norec" / "of" / "then" / "try" /
                  "typedef" / "unsafely\_as" / "var" / "with" / "yrt";
    End         = "end" !IdChar;

The keywords cannot be used as identifiers, with the exception of the ``"end"``
keyword. The ``"end"`` can be used as an identifier inside blocks that doesn't
use ``"end"`` as terminator (currently only block terminated using ``"end"``
is `class definition`_).

Comments and whitespace
--------------------------
.. peg

::

    LineComment = "//" ^[\r\n]*;
    CommentBody = ("/*" CommentBody / !"*/" _)* "*/";
    Space       = [ \t\r\n\u00A0] / LineComment / "/*" CommentBody;
    SP          = Space*;
    SkipSP      = (Space+ !("\." / "["))?;

Whitespace can appear between most other tokens without changing the
meaning of code, although some operators are whitespace sensitive
(for example field `reference operator`_ is distinguished from
`function composition`_ by not having whitespace on both sides).

Multiline comments can be nested, and all comments are considered
to be equivalent to other whitespace.

Separators
-------------
.. peg

::

    Colon       = SP ":" !OpChar;
    Semicolon   = SP ";";
    Dot         = "\." / SP "\." ![ (),;\\{}];

The separator symbols have a different meaning depending on the context.

Number
---------
.. peg

::

    Hex         = [0-9] / [a-f] / [A-F];
    Number      = ("0" ([xX] Hex+ / [oO] [0-7]+) /
                  [0-9]+ ("\." [0-9]+)? ([eE] ([+-]? [0-9]+)?)?);

Numbers represent numeric literals in expressions, and have always
the *number* type (rational and integer values are not distinguished
by type). Integer literals can be written as hexadecimal or octal
numbers, by using the ``0x`` or ``0o`` prefix respectively.

Floating-point runtime reprentation can be enforced by using exponent
(scientific) notation. As a special case of it, a single letter ``e``
may be added to the end (the exponent is considered to be zero in this
case).

Simple string
-----------------
.. peg

::

    SimpleString = ("'" ^[']* "'")+;

Simple string literals have *string* type in expressions.
Single apostrophe character (``'``) can be escaped by writing it twice,
but other escaping mechanisms are not available in simple string literals.
This makes it suitable for writing strings that contain many backslash
symbols (for example Perl compatible regular expressions).

Identifiers
--------------
.. peg

::

    IdChar      = [a-z] / [A-Z] / [0-9] / "\_" / "'" / "?" / "$";
    OpChar      = [!#%&*+-.:<=>@^|~] / "/" ![*/];
    Sym         = !(Keyword !IdChar) ([a-z] / "\_") IdChar*;
    IdOp        = "`" Sym "`";
    AnyOp       = !([=:] !OpChar) OpChar+ / IdOp / KeywordOp !IdChar;
    Id          = Sym / "(" SP AnyOp SP ")";
    JavaId      = SP ([a-z] / [A-Z] / "\_") ([a-z] / [A-Z] / [0-9] / "\_" / "$")*;
    ClassName   = JavaId (Dot JavaId)*;
    ClassId     = SP "~"? ClassName;
    Variant     = [A-Z] IdChar*;

Identifiers are used for naming definitions/bindings and their references,
the exact syntax and meaning depends on the context (most common are the
value bindings used within expressions).

Type description
+++++++++++++++++++
.. _IsType:
.. peg

::

    Type        = SP BareType SkipSP FuncType*;
    IsType      = SP ("is" !IdChar Type)?;
    BareType    = ['^] IdChar+ / "~" JavaType / "{" StructType / "(" SP ")" /
                  "(" Type ")" / VariantType ("|" !OpChar SP VariantType)* /
                  Sym "!"? SkipSP TypeParam?;
    TypeParam   = "<" SP (Type ("," Type)*)? ">";
    FuncType    = ("->" / "\u2192") !OpChar SP BareType SkipSP;
    JavaType    = ClassName "[]"*;

Type description is one of the following: function, type paramater (starts
with ``'`` or ``^``), Java class name (prefixed with ``~``), structure,
variant or type name. Type name may be followed by optional parameter list
that is embedded between ``<`` and ``>``. Java class name may be followed
by one or more ``[]`` pairs, indicating that it is JVM array type (in this
case the ClassName might be also Java primitive type name like *char*).

Type parameters starting with ``^`` are considered to have an ordered type.

Function type is in the form *argument-type* ``->`` *return-type* (the
above grammar defines it like type list separated by arrows, because the
*return-type* itself can be a function type without any surrounding
parenthesis).

The IsType clause using ``"is"`` keyword is used after binding or expression
to narrow it's type by unifying it with the given type.

Structure type
-----------------
.. peg

::

    StructType  = FieldType ("}" / "," SP "}" / "," StructType);
    FieldType   = SP ("var" !IdChar SP)? "\."? Sym SP "is" !IdChar Type;

Structure type is denoted by field list surrounded by ``{`` and ``}``.
The field names can be prefixed with dot, denoting required fields
(if any of the fields is without dot, then **all** listed fields
form the allowed fields set in the structure type).

Structure type in Yeti is more commonly called an extensible record
type in the ML family languages (the name structure is chosen in Yeti
because it is more familiar to programmers knowning the C family
languages).

Variant type
---------------
.. peg

::

    VariantType = Variant "\."? !IdChar SP BareType SkipSP;

Single variant type consists of the capitalized variant tag followed
by variants value type. The variant tag can be suffixed with dot,
denoting that it isn't a required variant.

The full variant type consists of single variants separated by ``|``
symbols. If any of the tags in full variant type has the dot prefix,
then **all** listed fields form the allowed variants set).

Composite literal constructors
+++++++++++++++++++++++++++++++++

Composite literals are literal expressions that can contain other expressions.
These expressions generally construct a new instance of the value on each
evaluation, with the exception of constant list literals, and string literals
that doesn't have any embedded expressions.

String
---------
.. peg

::

    String      = SimpleString /
                  "\"\"\"" ("\\" StringEscape / !"\"\"\"" _)* "\"\"\"" /
                  "\"" ("\\" StringEscape / ^["])* "\"";
    StringEscape = ["\\abfnrte0] / "u" Hex Hex Hex Hex /
                   "(" SP InParenthesis SP ")" / [ \t\r\n] SP "\"";

String literals have *string* type in expressions.
Strings can contain following escape sequences:

+-------------------+--------------------------------------------------------+
| Escape sequence   | Meaning in the string                                  |
+===================+========================================================+
| \\"               | Quotation mark ``"`` (ASCII code 34)                   |
+-------------------+--------------------------------------------------------+
| \\\ \\            | Backslash ``\`` (ASCII code 92)                        |
+-------------------+--------------------------------------------------------+
| \\(*expression*)  | Embedded expression. The value of the expression       |
|                   | is converted into string in the same way as standard   |
|                   | libraries string function would do.                    |
+-------------------+--------------------------------------------------------+
| \\\ *whitespace*" | This escape is simply omitted. The whitespace can      |
|                   | contain line breaks and comments, so this is useful    |
|                   | for breaking long strings into multiple lines.         |
+-------------------+--------------------------------------------------------+
| \\0               | NUL (ASCII code 0, null character)                     |
+-------------------+--------------------------------------------------------+
| \\a               | BEL (ASCII code 7, bell)                               |
+-------------------+--------------------------------------------------------+
| \\b               | BS  (ASCII code 8, backspace)                          |
+-------------------+--------------------------------------------------------+
| \\t               | HT  (ASCII code 9, horizontal tab)                     |
+-------------------+--------------------------------------------------------+
| \\n               | LF  (ASCII code 10, new line)                          |
+-------------------+--------------------------------------------------------+
| \\f               | FF  (ASCII code 12, form feed)                         |
+-------------------+--------------------------------------------------------+
| \\r               | CR  (ASCII code 13, carriage return)                   |
+-------------------+--------------------------------------------------------+
| \\e               | ESC (ASCII code 27, escape)                            |
+-------------------+--------------------------------------------------------+
| \\u\ *####*       | UTF-16 code point with the given hexadecimal           |
|                   | code *####*.                                           |
+-------------------+--------------------------------------------------------+

Stray backslash characters are not allowed, and all other sequences of symbols
represent themselves inside the string literal.

Strings are composite literals, because it is possible to embed arbitrary
expressions_ in the string using \\(...). The value of the whole
string literal is the result of concatenation of literal and embedded
expression value parts as strings.

Strings can be triple-quoted (in the start and end), the meaning is exactly
same as with strings between single ``"`` symbols. Triple-quoted strings
can be useful for larger string literals that contain ``"`` symbols by
themselves.

Lambda expression
--------------------
.. _Lambda:
.. peg

::

    Lambda      = "do" !IdChar BindArg* Colon AnyExpression "done" !IdChar;
    BindField   = FieldId IsType "=" !OpChar SP Id SP / Id IsType;
    StructArg   = "{" SP BindField ("," SP BindField)* "}";
    BindArg     = SP (Id / "()" / StructArg);

Lambda expression (aka function literal) constructs a function value containing
the given block of code (AnyExpression_) as body. The type of lambda expression
is therefore *argument-type* ``->`` *return-type* (a function type).
The argument type is inferred from the function body and the return type is
the type of the body expression.

The bindings from outer scopes are accessible for the function literals
body expression, and when used create a closure. Mutable bindings will
be stored in the closure as implicit references to the bindings.

Multiple arguments (BindArg) can be declared, this creates implicit nested
lambda expression for each of the arguments. The following lambda definitions
are therefore strictly equivalent::

    implicit_inner_lambda = do a b: a + b done;
    explicit_inner_lambda = do a: do b: a + b done;

Some special argument forms are accepted:

Unit value literal: ``()``
    The argument type is unit type and no actual argument binding is done.

Single underscore: ``_``
    The argument type is a free type variable and no actual argument
    binding is done (essentially a wildcard pattern match).

Structure literal: StructArg
    A destructuring binding of the argument is done. This means that the
    identifiers (Id) used as values for structure fields (FieldId) are bound
    inside the function body to the actual field values (taken from
    the structure value given as argument).

List and hash map literals
-----------------------------
.. peg

::

    List        = "[:]" / "[" SP (Items ("," SP)?)? "]";
    Items       = HashItem ("," HashItem)* / ListItem ("," ListItem)*;
    ListItem    = Expression SP ("\.\." !OpChar Expression)? SP;
    HashItem    = Expression Colon Expression SP;

List and and hash map literals are syntactically both enclosed in square
brackets. The difference is that hash map items have the key expression
and colon prepended to the value expression, while list items have only
the value expression. Empty hash map constructor is written as ``[:]`` to
differentiate it from the empty list literal ``[]``.

The list literal constructs a immutable single-linked list of its item
values (elements). The hash map literal constructs a mutable hash table
containing the given key-value associations.

Value expression types of all items are unified, resulting in a single
*value-type*. Hash map literals also unify all items key expression
types, resulting in a single *key-type*. The type of the list literal
itself is *list<value-type>*, and the type of the hash map literal is
*hash<key-type, value-type>*. Empty list and hash map constructors
assign free type variables to the *value-type* and *key-type*.

List literals can contain value ranges, where the lower and higher bound
of the range are separated by two consecutive dots (*lower-bound* ``..``
*higher-bound*). The items corresponding to the range are created lazily
when the list is traversed by incrementing the lower bound by one as long
as it doesn't exceed the higher bound. The bound and item types for a list
containing range are always *number* (which means that the *value-type*
is also a *number*).

Structure literal
--------------------
.. peg

::

    Struct      = "{" Field ("," Field)* ","? SP "}";
    Field       = SP Modifier? FieldId
                  (&(SP [,}]) / BindArg* IsType "=" !OpChar AnyExpression) SP;
    FieldId     = Id / "``" ^[`]+ "``";
    Modifier    = ("var" / "norec") Space+;

Structure literal creates a structure (aka record) value, which contains a
collection of named fields inside curled braces. Each field is represented as
a binding, where the FieldId is optionally followed by IsType_ clause narrowing
the fields type and/or equals (``=``) symbol and an expression containing
the fields value.

Multiple fields are separated by commas. If the field value is not specified
by explicit expression, then current scope must contain a binding with same
name as the field, and the value of that binding is assigned to the
corresponding structure field.

If field value expression is a function literal (either implicit one created
by having arguments in the field binding or explicit Lambda_ block), then a
new scope is created inside the structure literal, and used by all field
value expressions as a containing scope. All fields having function literal
values will create a local binding inside that structure scope (unless prefixed
with ``norec`` keyword), and the bindings will be recursively available
for all expressions residing in the structure literal definition. This is
the only form of mutually recursive bindings avaible in the Yeti language.
The local bindings inside the structure scope are always non-polymorphic.

The field names can be prefixed with ``var`` and/or ``norec`` keywords.
The ``var`` keyword means that the field is mutable within structure (by
default a field is immutable). The ``norec`` keyword means that the field
won't create a local binding inside the structure scope, even when it's
value is a function literal.

The type of structure literal is a structure type. The types of fields are
inferred from the values assigned to the fields and produce an allowed fields
set for the literals type. The required fields set in the type will be empty.

Block expressions
+++++++++++++++++++++

Conditional expression
-------------------------
.. peg

::

    If          = "if" !IdChar IfCond ("elif" !IdChar IfCond)* EndIf;
    EndIf       = ("else" !IdChar AnyExpression)? "fi" !IdChar /
                  "else:" !OpChar Expression;
    IfCond      = AnyExpression "then" !IdChar AnyExpression;

Conditional expression provides branched evaluation. When the condition
before ``"then"`` keyword evaluates as **true** value, then the AnyExpression_
after the ``"then"`` keyword will be evaluated, and resulting value will
be the value for the conditional expression. Otherwise the following
``elif`` condition will be examined in the same way. If there are no
(more) ``elif`` branches, then evaluation of the expression after the
``"else"`` keyword will give the value of the conditional expression.

The type of conditions (which precede the ``"then"`` keywords) is *boolean*.
The types of branch expressions are unified, and the unified type is used as
the type of the whole conditional expression. The unification uses implicit
casting rules for ``elif`` and ``else`` branches.

The final ``else`` branch might be omitted, in this case an implicit
``else`` branch is created by the compiler. If the unified type of the
explicit branches were *string*, then the value of the implicit ``else``
branch will be **undef_str**, otherwise the implicit ``else`` branch will
give the unit value ``()`` (that has the unit type *()*).

Case expression
------------------
.. peg

::

    CaseOf      = "case" !IdChar AnyExpression "of" !IdChar
                  Case (Semicolon CaseStmt?)* SP Esac;
    Case        = SP Pattern Colon Statement;
    CaseStmt    = Case / Statement / SP "\.\.\." Semicolon* SP &Esac;
    Esac        = "esac" !IdChar;
    Pattern     = Match SP ("::" !OpChar SP Match SP)*;
    Match       = Number / String / JavaId SP "#" SP JavaId /
                  Variant SP Match / Id /
                  "[" SP (Pattern ("," SP Pattern)* ("," SP)?)? "]" /
                  "{" FieldPattern ("," FieldPattern)* ("," SP)? "}" /
                  "(" SP Pattern? ")";
    FieldPattern = SP Id IsType ("=" !OpChar SP Pattern)? SP;

Try block
------------
.. peg

::

    Try         = "try" !IdChar AnyExpression Catches "yrt" !IdChar;
    Catch       = "catch" !IdChar ClassId (Space Id)? Colon AnyExpression;
    Catches     = Finally / Catch+ Finally?;
    Finally     = "finally" !IdChar AnyExpression;


Simple expression
++++++++++++++++++++
.. _expressions:

.. peg

::

    Primitive   = Number / String / "(" SP InParenthesis SP ")" / List /
                  Struct / Lambda / If / CaseOf / Try / New / Load / ClassOf /
                  Variant / Id;
    CPrimitive  = !End Primitive;
    InParenthesis = FieldRef+ / SP AsIsType / SP AnyOp Expression /
                    Expression SP AnyOp / AnyExpression;

Load operator
----------------
.. peg

::

    Load        = "load" !IdChar ClassName;

New operator
---------------
.. peg

::

    New         = "new" !IdChar ClassName SP NewParam;
    NewParam    = ArgList / "[" AnyExpression "]" "[]"*;
    ArgList     = "(" SP (Expression SP ("," Expression SP)*)? ")";

ClassOf operator
-------------------
.. peg

::

    ClassOf     = "classOf" !IdChar ClassId SP "[]"*;


Expression with operators
++++++++++++++++++++++++++++

Reference operators
----------------------
.. _reference operator:

.. peg

::

    Reference   = SP PrefixOp* Primitive RefOp*;
    CReference  = SP PrefixOp* CPrimitive CRefOp*;
    PrefixOp    = "\\" SP / "-" SP !OpChar;
    RefOp       = FieldRef / MapRef / (SP (ObjectRef / "->" SP Primitive));
    CRefOp      = FieldRef / MapRef / (SP (ObjectRef / "->" SP CPrimitive));
    FieldRef    = Dot SP FieldId;
    MapRef      = "[" Sequence SP "]";
    ObjectRef   = "#" JavaId SP ArgList?;

Application
--------------
.. peg

::

    Apply       = Reference (SP AsIsType* Reference)*;
    CApply      = CReference (SP AsIsType* CReference)*;

Arithmetic operators
-----------------------
.. peg

::

    Sum         = Multiple SkipSP (SumOp Multiple)*;
    CSum        = CMultiple SkipSP (SumOp CMultiple)*;
    SumOp       = AsIsType* ("+" / "-") !OpChar / ("b\_or" / "xor") !IdChar;
    Multiple    = Apply SkipSP (AsIsType* FactorOp Apply SkipSP)*;
    CMultiple   = CApply SkipSP (AsIsType* FactorOp CApply SkipSP)*;
    FactorOp    = ("*" / "/" / "%") !OpChar /
                  ("div" / "shr" / "shl" / "b\_and" / "with") !IdChar;

Custom operators
-------------------
.. peg

::

    CustomOps   = Sum SkipSP (AsIsType* CustomOp Sum)*;
    CCustomOps  = CSum SkipSP (AsIsType* CustomOp CSum)*;
    CustomOp    = !(CompareOp / [*/%+-<=>^:\\\.] !OpChar) OpChar+ / IdOp;

Function composition
-----------------------
.. peg

::

    Compose     = CustomOps (AsIsType* ComposeOp CustomOps)*;
    CCompose    = CCustomOps (AsIsType* ComposeOp CCustomOps)*;
    ComposeOp   = "\." Space+ / Space+ "\." SP;

Comparison operators
-----------------------
.. peg

::

    Compare     = SP Not* Compose SP (AsIsType* CompareOp Compose)*
                  SP InstanceOf*;
    CCompare    = SP Not* CCompose SP (AsIsType* CompareOp CCompose)*
                  SP InstanceOf*;
    InstanceOf  = "instanceof" !IdChar ClassId SP;
    Not         = "not" !IdChar SP;
    CompareOp   = ("<" / ">" / "<=" / ">=" / "==" / "!=" / "=~" / "!=") !OpChar /
                  "in" !IdChar;

Logical operators
--------------------
.. peg

::

    Logical     = Compare SP (AsIsType* ("and" / "or") !IdChar Compare)*;
    CLogical    = CCompare SP (AsIsType* ("and" / "or") !IdChar CCompare)*;

String concatenation
-----------------------
.. peg

::

    StrConcat   = Logical SP (AsIsType* "^" !OpChar Logical)*;
    CStrConcat  = CLogical SP (AsIsType* "^" !OpChar CLogical)*;

List construction and concatenation
--------------------------------------
.. peg

::

    Cons        = StrConcat SP (AsIsType* ConsOp !OpChar StrConcat)*;
    CCons       = CStrConcat SP (AsIsType* ConsOp !OpChar CStrConcat)*;
    ConsOp      = "::" / ":." / "++";

Casts
--------
.. peg

::

    AsIsType    = ("is" / "as" / "unsafely\_as") !IdChar Type;

Forward application
----------------------
.. peg

::

    ApplyPipe   = Cons SP ("|>" !OpChar Cons)* AsIsType*;
    CApplyPipe  = CCons SP ("|>" !OpChar CCons)* AsIsType*;

Assigning values
-------------------
.. peg

::

    Assign      = ApplyPipe SP (":=" !OpChar ApplyPipe)?;
    CAssign     = CApplyPipe SP (":=" !OpChar CApplyPipe)?;

Loop
-------
.. peg

::

    Expression  = Assign SP ("loop" (!IdChar Assign)?)?;
    CExpression = CAssign SP ("loop" (!IdChar CAssign)?)?;


Value and function bindings
++++++++++++++++++++++++++++++
.. peg

::

    Binding     = (StructArg / Modifier? !Any Id BindArg* IsType)
                  SP "=" !OpChar Expression Semicolon+ SP;
    CBinding    = (StructArg / Modifier? !(Any / End) Id (!End BindArg)* IsType)
                  SP "=" !OpChar CExpression Semicolon+ SP;
    Any         = "\_" !IdChar;

Self-binding lambda expression
---------------------------------
.. peg

::

    SelfBind    = (Modifier? Id BindArg+ / Any) IsType "=" !OpChar;
    CSelfBind   = (Modifier? !End Id (!End BindArg)+ / Any) IsType "=" !OpChar;


Class definition
+++++++++++++++++++
.. peg

::

    Class       = "class" !IdChar JavaId SP MethodArgs? Extends?
                  (End / Member ("," Member)* ","? SP End);
    Extends     = "extends" !IdChar ClassName SP ArgList? SP ("," ClassName SP)*;
    Member      = SP (Method / ClassField) SP;

Class field
--------------
.. peg

::

    ClassField  = ("var" Space+)? !End Id SP (!End BindArg SP)*
                  "=" !OpChar CExpression;

Class method
---------------
.. peg

::

    Method      = (("abstract" / "static") Space)? MethodType JavaId
                  MethodArgs Semicolon* MethodBody?;
    MethodArgs  = "(" SP (")" / MethodArg ("," MethodArg)* ")") SP;
    MethodType  = SP ClassName SP "[]"* SP;
    MethodArg   = MethodType Id SP;
    MethodBody  = CStatement (Semicolon CStatement?)*;


Declarations
+++++++++++++++
.. peg

::

    Declaration  = ClassDecl / Binding;
    CDeclaration = ClassDecl / CBinding;
    MDeclaration = TypeOrImport / Binding;
    ClassDecl    = Class Semicolon+ SP / TypeOrImport; 
    TypeOrImport = Import Semicolon+ SP / Typedef Semicolon* SP;

Java class imports
--------------------
.. peg

::

    Import      = "import" !IdChar Space+ ClassName
                  (Colon JavaId SP ("," JavaId SP)*)?;

Type definition
------------------
.. peg

::

    Typedef     = "typedef" !IdChar SP TypedefOf Semicolon*;
    TypedefOf   = "unshare" !IdChar SP Id /
                  (("opaque" / "shared") !IdChar SP)?
                  Id SP TypedefParam? "=" !OpChar Type;
    TypedefParam = "<" !OpChar SP Id SP ("," SP Id SP)* ">" !OpChar SP;


Sequence expression
+++++++++++++++++++++++
.. _AnyExpression:
.. peg

::

    AnyExpression = Semicolon* Sequence? SP;
    Sequence   = Statement (Semicolon Statement?)*; 
    Statement  = SP ClassDecl* (CSelfBind / Declaration* CSelfBind?) Expression;
    CStatement = SP ClassDecl* (SelfBind / CDeclaration* SelfBind?) CExpression;
    MStatement = SP TypeOrImport* (SelfBind Expression /
                                  MDeclaration* (Class / SelfBind? Expression));


Top level of the source file
+++++++++++++++++++++++++++++++
.. peg

::

    TopLevel    = Module / Program? AnyExpression;
    Program     = "program" !IdChar Space+ ClassName Semicolon;
    Module      = "module" !IdChar Space+ ClassName
                  (Colon SP "deprecated")? Semicolon+ ModuleMain? SP;
    ModuleMain  = MStatement (Semicolon MStatement?)*;
