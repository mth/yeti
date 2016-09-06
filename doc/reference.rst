.. ex: se sw=4 sts=4 expandtab:

================================
Yeti language reference manual
================================

:Author: Madis Janson

.. contents:: Contents
.. _yeti.jar: http://dot.planet.ee/yeti/yeti.jar
.. _home page: http://mth.github.io/yeti/
.. _Mouse parser generator: http://mousepeg.sourceforge.net/Manual.pdf
.. _short introduction: http://dot.planet.ee/yeti/intro.html

About this document
~~~~~~~~~~~~~~~~~~~~~~
This reference manual tries to describe the full Yeti language grammar and
semantics as exactly as possible. It is written in terse form and with
expectation, that the reader has already learned the Yeti language basics
(for example by reading the `short introduction`_). It should be useful
for learning the exact syntax and semantics of some language construct, for
learning advanced language parts not described in the introduction, and
for people wanting to modify the compiler (or to write their own Yeti
language compatible parser or compiler).

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

The keywords cannot be used as identifiers, with the exception of the
``"end"`` keyword. The ``"end"`` can be used as an identifier inside blocks
that doesn't use ``"end"`` as terminator (currently only block terminated
using ``"end"`` is `class definition`_).

.. Note::

    The Mouse PEG grammar uses underscore to mean any character,
    and literal underscores must be escaped with backslash.
    For example the above ``"unsafely\_as"`` means literal
    keyword ``unsafely_as``.

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
(for example field `reference operator <Reference operators_>`_ is
distinguished from `function composition`_ by not having whitespace
on both sides).

Multi-line comments can be nested, and all comments are considered
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

Floating-point runtime representation can be enforced by using exponent
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
.. _Id:
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

Most operators can be used as normal identifiers by placing them in
parenthesis. The type of usable operator binding should be a function
(for binary operators it would be *left-side* → *right-side* → *result*).

Type description
+++++++++++++++++++
.. _Type:
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

Type description is one of the following: function, type parameter (starts
with ``'`` or ``^``), Java class name (prefixed with ``~``), structure,
variant or type name. Type name may be followed by optional parameter list
that is embedded between ``<`` and ``>``. Java class name may be followed
by one or more ``[]`` pairs, indicating that it is JVM array type (in this
case the ClassName might be also Java primitive type name like *char*).

Type parameters starting with ``^`` are considered to have an ordered type.

Function type is in the form *argument-type* → *return-type* (the
above grammar defines it like type list separated by arrows, because the
*return-type* itself can be a function type without any surrounding
parenthesis). Either ``->`` or the unicode symbol \\u2192 (→) can be used
for the function arrow.

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
because it is more familiar to programmers knowing the C family
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
is therefore *argument-type* → *return-type* (a function type).
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

.. _StructArg:

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

List and hash map literals are syntactically both enclosed in square
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
    Field       = SP NoRec? Modifier? FieldId
                  (&(SP [,}]) / BindArg* IsType "=" !OpChar AnyExpression) SP;
    FieldId     = Id / "``" ^[`]+ "``";
    NoRec       = "norec" Space+;
    Modifier    = ("get" / "set" / "var") Space+;

Structure literal creates a structure (aka record) value, which contains a
collection of named fields inside curled braces. Each field is represented as
a binding, where the FieldId is optionally followed by IsType_ clause narrowing
the fields type and/or equals (``=``) symbol and an expression containing
the fields value. The FieldId is either normal identifier or a string enclosed
between ``````.

Multiple fields are separated by commas. If the field value is not specified
by explicit expression, then current scope must contain a binding with same
name as the field, and the value of that binding is assigned to the
corresponding structure field.

If field value expression is a function literal (either implicit one created
by having arguments in the field binding or explicit Lambda_ block), then a
new scope is created inside the structure literal, and used by all field value
expressions as a containing scope. All fields having function literal values
will create a local binding inside that structure scope (unless prefixed
with ``norec`` keyword), and the bindings will be recursively available
for all expressions residing in the structure literal definition. This is
the only form of mutually recursive bindings available in the Yeti language.
The local bindings inside the structure scope are always non-polymorphic.

The field names can be prefixed with ``norec``, ``var``, ``get`` or ``set``
keywords:

``var``
    The field is mutable within structure (by default a field is immutable).

``norec``
    The field won't create a local binding inside the structure scope,
    even when it's value is a function literal.

``get``
    The given value is used as an accessor function that is applied to unit
    value ``()`` to get the actual field value whenever the
    `field value is referenced <FieldRef_>`_. The type of the accessor
    function is *()* → *field-type*.

``set``
    The given value is used as an accessor function that is applied to
    the value to be assigned whenever a new value is
    `assigned <assigning values_>`_ to the `field reference <FieldRef_>`_.
    The ``set`` accessor is allowed only together with the ``get`` accessor.
    The type of the accessor function is *field-type* → *()*.

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
expression before ``"then"`` keyword evaluates as **true** value, then
the AnyExpression_ after the ``"then"`` keyword will be evaluated, and
resulting value will be the value for the conditional expression.

Otherwise the following ``elif`` condition will be examined in the same way.
If there are no (more) ``elif`` branches, then evaluation of the expression
after the ``"else"`` keyword will give the value of the conditional expression.

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

Case expression contains one or more case options separated by semicolons.
Each case option has a value pattern followed by colon and expression to be
evaluated in case the pattern matches the given argument value (resulting
from the evaluation of the AnyExpression_ between initial ``"case"`` and
``"of"`` keywords). Only the expression from first matching case option will
be evaluated, and the resulting value will be the value of the whole case
expression.

The patterns are basically treated as literal values that are compared to
the given case argument value, but identifiers in the pattern (Id_) act
like wildcards that match any value. Each case option has its own scope,
and the identifiers from its pattern will have the matching values bound
to them during the expression evaluation.

The pattern can contain wildcard identifiers, number and string literals,
variant constructor applications, list cell constructor applications (``::``),
list literals, structure literals and static final field references from
Java classes (in the ``Class#field`` form).

The underscore identifier ``_`` is special in that it wouldn't be bound
to real variable (similarly as it's used in function arguments).

The compiler should verify that the case options patterns together provide
exhaustive match for the matched value, so at least one case option is
guaranteed to match at runtime, regardless of the matched value. Compilation
error should be given for non-exhaustive patterns.

The last case option can be ``...`` (but it can't be the only option).
This is shorthand for the following case option code::

   value: throw new IllegalArgumentException("bad match (\(value))"); 

It can be useful for marking the case patterns as non-exhaustive (and since
it will match any value, it will make the exhaustiveness check to pass).

The matching value type is inferred from each case option pattern, and
the resulting types are unified into single type. The pattern type
unification works mostly like regular expression type unification,
with few exceptions:

    * `Variant <Id_>`_ tags from the pattern form *allowed* member set in
      the corresponding variant type, unless the type is also matched with
      wildcard (in this case *required* member set is formed in the type).
    * Structure fields from the pattern form *required* member set in the
      corresponding structure type.
    * List literal pattern gives *list?* type instead of *list*, meaning
      that values of *array* type can be also matched to it.

The case option expression types are also inferred and unified into single
type, which will be the type of the whole case expression.

Try block
------------
.. peg

::

    Try         = "try" !IdChar AnyExpression Catches "yrt" !IdChar;
    Catch       = "catch" !IdChar ClassId (Space Id)? Colon AnyExpression;
    Catches     = Finally / Catch+ Finally?;
    Finally     = "finally" !IdChar AnyExpression;

Try block provides exception handling. The expression following the ``"try"``
keyword is evaluated first, and if it doesn't throw an exception, the value
of it will be used as the value of the ``try``...\ ``yrt`` block.

The exceptions correspond to the JVM exceptions, and therefore the exception
types are directly Java class types.

The types of the ``try`` and ``catch`` section expressions are unified, and
the resulting type is used as the type of the ``try`` block.

The ``finally`` sections expression must have the unit type *()*, as the
value from the evaluation of the ``finally`` section is always ignored.

If exception is thrown that matches some ``catch`` section (by being same or
subclass of its `ClassId <Id_>`_), then first matching ``catch`` section is
evaluated, and the resulting value is used as the value of the ``try`` block.

If ``catch`` section has an exception binding Id_, then caught exceptions
value will be bound to the given identifier in that sections scope.

The expression following the ``"finally"`` keyword will be evaluated regardless
of whether any exception was thrown during the evaluation of ``try`` and
``catch`` sections. If an exception was thrown, then it will be suspended
during the evaluation of the ``finally`` section. If exception was suspended
and the ``finally`` section itself throws an exception, then the suspended
exception will be dropped (as only one exception per thread is allowed
simultaneously), otherwise the suspended exception will be re-thrown after
the ``finally`` block finishes.

Operator sections
++++++++++++++++++++

The operator sections can be only in parenthesis.

.. peg

::

    InParenthesis = FieldRef+ / SP AsIsType / RightSection /
                    LeftSection / AnyExpression;
    RightSection = SP AnyOp Expression;
    LeftSection  = Expression SP AnyOp;

Right section results in a function that applies the operator with argument
value as the implicit left-side value, and the expressions value as
right-side value. Left section results in a function that applies the operator
with expressions value as the left-side value, and the argument value as the
implicit right-side value. The expression is evaluated during the evaluation
of the section. The sections can be viewed as a syntactic sugar for following
partial applications::

    right_section = (`operator` expression);
    right_section_equivalent = flip operator expression;
    left_section = (expression `operator`);
    left_section_equivalent = operator expression;

The ``as`` and ``unsafely_as`` casts can also be used as right sections, that
result in a function value that casts its argument value into the given type.
The argument type is inferred from the context where the cast section is used,
defaulting to free type variable (*'a*). Similarly the ``instanceof`` operator
can be used as a right section, resulting in a function that checks whether
its argument value would pass as instance of the given Java class.

Field references can also be put into parenthesis, giving a function that
retrieves the field value from the argument value. The type of single
field reference is ``{``\ *.field-name* ``is`` *'a*\ ``}`` → *'a*.

Field reference functions can be seen as syntactic sugar for following
lambda expressions::

    foo_bar_reference_function = (.foo.bar);
    foo_bar_reference_equivalent = do v: v.foo.bar done;

Any other expression in parenthesis is the expression itself.

Simple expression
++++++++++++++++++++
.. _expressions:
.. peg

::

    Primitive   = Number / String / "(" SP InParenthesis SP ")" / List /
                  Struct / Lambda / If / CaseOf / Try / New / Load / ClassOf /
                  Variant / Id;
    CPrimitive  = !End Primitive;

Simple expression is an expression that is not composed of subexpressions
separated by operators.

* `Identifier <Id_>`_
* Parenthesis (that can contain `any expression <AnyExpression_>`_)
* Literal constructor (number_, string_, `lambda expression`_,
  `list and hash map literals`_, `structure literal`_ or
  `variant constructor`_)
* Block expression (`conditional expression`_, `case expression`_ or
  `try block`_)
* Special value constructor (`load operator`_, `new operator`_ or
  `classOf operator`_)

The CPrimitive is simple expression that isn't the ``end`` keyword.
This is used inside `class definition`_ block, which is terminated by
``end`` (in other places ``end`` is normal identifier).

Variant constructor
----------------------

Variant constructor is written simply as a `Variant <Id_>`_ tag.
The type of variant constructor is *'a* → *Variant 'a*.

Load operator
----------------
.. peg

::

    Load        = "load" !IdChar ClassName;

Load operator gives value of module determined by the `ClassName <Id_>`_,
and the expressions type is the type of the module.

Alternatively ``load`` of module with structure type can be used as
a statement on the left side of the sequence operator. In this use
all fields of the module value will be brought into scope of right-hand
side of the sequence operator as local bindings, and additionally all
top-level `typedefs <Type definition_>`_ from the module will be imported
into that scope.

New operator
---------------
.. _ArgList:
.. peg

::

    New         = "new" !IdChar ClassName SP NewParam;
    NewParam    = ArgList / "[" AnyExpression "]" "[]"*;
    ArgList     = "(" SP (Expression SP ("," Expression SP)*)? ")";

New operator constructs an instance of Java class specified by
`ClassName <Id_>`_, and the expressions type is the class type *~ClassName*.

Similarly to Java language, the constructor that has nearest match to
the given argument types is selected. Compilation fails, if there is no
suitable constructor.
The exact semantics of class construction come from the underlying JVM used,
and can be looked up from the JVM specification.

ClassOf operator
-------------------
.. peg

::

    ClassOf     = "classOf" !IdChar ClassId SP "[]"*;

The ``classOf`` operator gives Java **Class** instance corresponding to
the JVM class specified by the `ClassId <Id_>`_.
The specified class must exists in the compilation class path.
If the class name is followed by ``[]`` pairs, then an array class is given.
The type of ``classOf`` expression is (obviously) ``~java.lang.Class``.

Rough equivalent to ``classOf`` would be using ``Class#forName`` method::

    stringClass = Class#forName("java.lang.String");
    // gives same result as
    stringClass = classOf java.lang.String;
    // or simply
    stringClass = classOf String;

Expression with operators
++++++++++++++++++++++++++++

Operator precedence
----------------------

+----------------+-----------------+-----------------------+-----------------+
| Precedence and | Operator        | Description           | Type            |
| associativity  |                 |                       |                 |
+================+=================+=======================+=================+
| 1.             | **.**\ *field*  | Field reference       | *{.field is*    |
| suffix         |                 |                       | *'a}* → *'a*    |
|                +-----------------+-----------------------+-----------------+
|                | **#**\ *field*  | Java object reference |                 |
|                +-----------------+-----------------------+-----------------+
|                | **[]**          | Map reference         | *map<'k, 'e>*   |
|                |                 |                       | → *'k* → *'e*   |
+----------------+-----------------+-----------------------+-----------------+
| 1.             | **->**          | Custom reference      | *{.``->`` is*   |
| left           |                 |                       | *'a* → *'b}*    |
|                |                 |                       | → *'a* → *'b*   |
+----------------+-----------------+-----------------------+-----------------+
| 2.             | **-**           | Arithmetic negation   | *number* →      |
| prefix         |                 |                       | *number*        |
|                +-----------------+-----------------------+-----------------+
|                | **\\**          | Lambda                |                 |
+----------------+-----------------+-----------------------+-----------------+
| 3.             |                 | Application           | (*'a* → *'b*)   |
| left           |                 |                       | → *'a* → *'b*   |
+----------------+-----------------+-----------------------+-----------------+
| 4.             | **\***          | Multiplication        | *number* →      |
| left           +-----------------+-----------------------+ *number* →      |
|                | **/**           | Division              | *number*        |
|                +-----------------+-----------------------+                 |
|                | **div**         | Integer division      |                 |
|                +-----------------+-----------------------+                 |
|                | **%**           | Remainder of          |                 |
|                |                 | integer division      |                 |
|                +-----------------+-----------------------+                 |
|                | **b_and**       | Bitwise and           |                 |
|                +-----------------+-----------------------+                 |
|                | **shl**         | Bitwise left shift    |                 |
|                +-----------------+-----------------------+                 |
|                | **shr**         | Bitwise right shift   |                 |
|                +-----------------+-----------------------+-----------------+
|                | **with**        | Structure merge       |                 |
+----------------+-----------------+-----------------------+-----------------+
| 5.             | **+**           | Addition              | *number* →      |
| left           +-----------------+-----------------------+ *number* →      |
|                | **-**           | Subtraction           | *number*        |
|                +-----------------+-----------------------+                 |
|                | **b_or**        | Bitwise or            |                 |
|                +-----------------+-----------------------+                 |
|                | **xor**         | Bitwise exclusive or  |                 |
+----------------+-----------------+-----------------------+-----------------+
| 6.             |                 | Custom operators      |                 |
| left           |                 |                       |                 |
+----------------+-----------------+-----------------------+-----------------+
| 7.             | **.**           | Function              | *('a* → *'b)*   |
| undefined      |                 | composition           | → *('c* → *'a)* |
|                |                 |                       | → *'c* → *'b*   |
+----------------+-----------------+-----------------------+-----------------+
| 8.             | **==**          | Equality              | *'a* → *'b*     |
| left           +-----------------+-----------------------+ → *boolean*     |
|                | **!=**          | Inequality            |                 |
|                +-----------------+-----------------------+-----------------+
|                | **<**           | Less than             | *^a* → *^b*     |
|                +-----------------+-----------------------+ → *boolean*     |
|                | **<=**          | Less than or equal    |                 |
|                +-----------------+-----------------------+                 |
|                | **>**           | Greater than          |                 |
|                +-----------------+-----------------------+                 |
|                | **>=**          | Greater than or equal |                 |
|                +-----------------+-----------------------+-----------------+
|                | **=~**          | Pattern match         | *string*        |
|                |                 |                       | → *string*      |
|                |                 |                       | → *boolean*     |
|                +-----------------+-----------------------+-----------------+
|                | **instanceof**  | Instance of class     | *'a* → *boolean*|
+----------------+-----------------+-----------------------+-----------------+
| 9.             | **not**         | Logical negation      | *boolean*       |
| prefix         |                 |                       | → *boolean*     |
+----------------+-----------------+-----------------------+-----------------+
| 10.            | **and**         | Logical and           | *boolean*       |
| left           +-----------------+-----------------------+ → *boolean*     |
|                | **or**          | Logical or            | → *boolean*     |
+----------------+-----------------+-----------------------+-----------------+
| 11.            | **^**           | String concatenation  | *string*        |
| left           |                 |                       | → *string*      |
|                |                 |                       | → *string*      |
+----------------+-----------------+-----------------------+-----------------+
| 12.            | **::**          | List construction     | *'a*            |
| right          |                 |                       | → *list<'a>*    |
|                |                 |                       | → *list<'a>*    |
|                +-----------------+-----------------------+-----------------+
|                | **:.**          | Lazy list             | *'a* → *(()*    |
|                |                 | construction          | → *list<'a>)*   |
|                |                 |                       | → *list<'a>*    |
|                +-----------------+-----------------------+-----------------+
|                | **++**          | List concatenation    | *list<'a>*      |
|                |                 |                       | → *list<'a>*    |
|                |                 |                       | → *list<'a>*    |
+----------------+-----------------+-----------------------+-----------------+
| 13.            | **is** *type*   | Type unification      | *type* → *type* |
| suffix         +-----------------+-----------------------+-----------------+
|                | **as**          | Type conversion       |                 |
|                +-----------------+-----------------------+-----------------+
|                | **unsafely_as** | Unsafe type coercion  |                 |
+----------------+-----------------+-----------------------+-----------------+
| 14.            | **|>**          | Forward application   | *'a* → *('a*    |
| left           |                 |                       | → *'b)* → *'b*  |
+----------------+-----------------+-----------------------+-----------------+
| 15.            | **:=**          | Assignment            | *'a* → *'a*     |
| left           |                 |                       | → *()*          |
+----------------+-----------------+-----------------------+-----------------+
| 16.            | **loop**        | Loop                  | *boolean*       |
| right          |                 |                       | → *()* → *()*   |
+----------------+-----------------+-----------------------+-----------------+

Reference operators
----------------------
.. peg

::

    Reference   = SP PrefixOp* Primitive RefOp*;
    CReference  = SP PrefixOp* CPrimitive CRefOp*;
    RefOp       = FieldRef / MapRef / (SP (ObjectRef / "->" SP Primitive));
    CRefOp      = FieldRef / MapRef / (SP (ObjectRef / "->" SP CPrimitive));

Reference operators have highest precedence and thereby work
on simple `expressions`_.

Reference operators have left associativity.

The ``->`` operator is a function from standard library that is used
to provide custom reference operator for structure objects.

.. peg

::

    PrefixOp    = "\\" SP / "-" SP !OpChar;

The ``\`` prefix operator is shorthand form of `lambda expression`_.
A expression in form ``\``\ *value* is equivalent to ``do:`` *value* ``done``.
The argument value is ignored. If the *value* is a constant expression, then
the result is a constant function.

The ``-`` prefix operator is arithmetic negation. Its type is
*number* → *number*, so the negated expression must be a number, and the
resulting value is also number. Since ``-`` can be also used as binary
operator, the prefix operator cannot be used directly as function,
but the function value is bound in standard library module ``yeti.lang.std``
to ``negate`` identifier.

.. _FieldRef:
.. peg

::

    FieldRef    = Dot SP FieldId;

Field reference is a postfix operator that gives value of the given structure
*field*. Its type is ``{``\ *.field* ``is`` *'a*\ ``}`` → *'a*.

.. _mapping reference:
.. peg

::

    MapRef      = "[" Sequence SP "]";

Mapping reference takes two arguments - the mapping value preceding it and
the key value expression. The resulting value is the element corresponding
to the given key (or index). No whitespace can be before mapping reference
operator - if there is whitespace, then it is parsed as application of list
literal. The standard library has this operator as ``at`` function with
type *map<'key, 'element>* → *'key* → *'element*.
The mapping can be either *hash* map or *array*.

.. peg

::

    ObjectRef   = "#" JavaId SP ArgList?;

When ArgList_ is present, the ``#`` operator means method call, otherwise
it will be a Java class field reference.

The left side expression of the ``#`` operator is expected to have a Java
object type (*~Something*), that must have a field or method named by the
`JavaId <Id_>`_. No type inference is done for the left-side object type.

Since Java classes can have multiple methods with same name, the exact
method is resolved by finding one that has the correct number of arguments
and best match for the actual argument types. Implicit casting is done
for the arguments, if necessary. The resulting expression type is derived
from the used methods return type for method calls and field type for object
field references.

The ``#`` operator cannot be used as a function.

Application
--------------
.. peg

::

    Apply       = Reference (SP AsIsType* Reference)*;
    CApply      = CReference (SP AsIsType* CReference)*;

Function application is done simply by having two value expressions
(simple values or references) consecutively. Left side value is the
function value and the right side is the argument given to the function.
Yeti uses strict call-by-sharing evaluation semantics (call-by-sharing
is a type of call-by-value evaluation, where references are passed).

The type of application is the functions return type. If the function
value type is *'a* → *'b*, then the given value must have the same *'a*
type and the applications resulting value type is the same *'b* type.

The application operator has left associativity, for example ``a b c`` is
identical to ``(a b) c``. 

The function expression is evaluated before argument expression. This means
also that when multiple arguments are given by curring, then these argument
expressions are evaluated in the application order.

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

Yeti language has the following arithmetic and bitwise logic operators:

+-----------+-------------------------------+
| Operator  | Description                   |
+===========+===============================+
| **\+**    | Arithmetic addition           |
+-----------+-------------------------------+
| **\-**    | Arithmetic subtraction        |
+-----------+-------------------------------+
| **b_or**  | Bitwise logical or            |
+-----------+-------------------------------+
| **b_xor** | Bitwise logical exclusive or  |
+-----------+-------------------------------+
| **\***    | Arithmetic multiplication     |
+-----------+-------------------------------+
| **/**     | Arithmetic division           |
+-----------+-------------------------------+
| **%**     | Remainder of integer division |
+-----------+-------------------------------+
| **div**   | Integer division              |
+-----------+-------------------------------+
| **b_and** | Bitwise logical and           |
+-----------+-------------------------------+
| **shr**   | Bit shift to right (unsigned) |
+-----------+-------------------------------+
| **shl**   | Bit shift to left             |
+-----------+-------------------------------+

All arithmetic and bitwise operators have the type *number* → *number*
→ *number* and left associativity. The bitwise, integer division and
remainder operators truncate fractional part from their arguments,
doing the given operation using only the integer part of the argument.

Structure override and merge operator with
'''''''''''''''''''''''''''''''''''''''''''''
The expression on the right of the ``with`` operator must have a structure
type that has an allowed fields set (a non-extensible structure type).
The left-side expression must have either structure type or undefined
type  *'a* (a free type variable). The ``with`` operator has nothing else
in common with arithmetic operators, than having the same precedence and
left associativity.

The resulting value of the ``with`` expression is a structure consisting of
all fields from the right-side value, that were in its types allowed field
set, and those fields from the left-side structure value, that were not in
the right-side expression types allowed field set.

Mutable fields are shared with their originating structure. This means that
the structure that gave a mutable field to the resulting structure gets
its field updated whenever the field is assigned a new value in the ``with``
operators result structure. The ``get`` and ``set`` field accessor functions
are also passed to the resulting structure, so accessing the result structure
field still goes through the accessor functions.

The ``with`` operator has two distinct use cases, overriding and merging.
If the left-side expression also has a structure type with allowed fields set,
then a merge operation is done, otherwise only a simple override is done.

For overriding operation the left-side expressions type is unified with
structure type that has as a required fields set the right-side types
allowed fields set. The result of unification is used as the type of the
``with`` expression. Due to the type unification the right-side allowed
field set is either same or subset of the left-side values field set, with
matching types, and all the corresponding fields are overridden.

For merging operation, the type of the ``with`` expression is a new structure
type. The result types allowed field set contains all of the right-side types
allowed field set, and those fields from the left-side types allowed field
set that were not present in the right-side type. A required fields set is
not present in the result type, and no unification is done with either left
nor right side expression types. Since no unification is done, for a field
present on both sides of the ``with`` operator the types can be different
(only the type from right side is used in this case).

Custom operators
-------------------
.. peg

::

    CustomOps   = Sum SkipSP (AsIsType* CustomOp Sum)*;
    CCustomOps  = CSum SkipSP (AsIsType* CustomOp CSum)*;
    CustomOp    = !(CompareOp / [*/%+-<=>^:\\\.] !OpChar) OpChar+ / IdOp;

Custom operators are any operators that are not built into the language.
These operators are defined by simply having a function value bound with name
consisting of operator characters, or by using regular identifier between
backticks. The operator type is the binding type, and resulting value/type
is the result of applying the function value to the given arguments. 

Custom operators have left associativity.

Function composition
-----------------------
.. peg

::

    Compose     = CustomOps (AsIsType* ComposeOp CustomOps)*;
    CCompose    = CCustomOps (AsIsType* ComposeOp CCustomOps)*;
    ComposeOp   = "\." Space+ / Space+ "\." SP;

Function composition operator composes two functions given as it's arguments.
Canonical implementation of the function composition is the following
definition::

    (.) f g a = f (g a);

The type of the composition operator is
*('a* → *'b)* → *('c* → *'a)* → *'c* → *'b*.

Dot is considered to be composition operator only when it doesn't have
identifier neither directly before or after it (without whitespace).
Otherwise the dot denotes reference operator.

Function composition is associative, therefore the operators associativity
is undefined.

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
    CompareOp   = ("<" / ">" / "<=" / ">=" / "==" / "!=" / "=~" / "!=")
                  !OpChar / "in" !IdChar;

Comparison operators compare two values of same type and give boolean
result. Comparison operators have left associativity.

Yeti language has the following comparison operators:

+----------+--------------------------------------------+----------------------+
| Operator | Proposition                                | Type                 |
+==========+============================================+======================+
| **==**   | Left side value is equal to right side     | *'a* → *'a*          |
+----------+--------------------------------------------+ → *boolean*          |
| **!=**   | Left side value is not equal to right side |                      |
+----------+--------------------------------------------+----------------------+
| **<**    | Left side value is less than right side    | *^a* → *^a*          |
+----------+--------------------------------------------+ → *boolean*          |
| **<=**   | Left side value is less than or equal      |                      |
|          | to right side                              |                      |
+----------+--------------------------------------------+                      |
| **>**    | Left side value is greater than right side |                      |
+----------+--------------------------------------------+                      |
| **>=**   | Left side value is greater than            |                      |
|          | or equal to right side                     |                      |
+----------+--------------------------------------------+----------------------+
| **=~**   | Left side string matches regex             | *string* → *string*  |
|          | pattern on the right side                  | → *boolean*          |
+----------+--------------------------------------------+----------------------+

instanceof operator
''''''''''''''''''''''

The ``instanceof`` operator gives ``true`` value when the left-side value
would pass as an instance of the Java class named on the right of the operator,
by being instance of it or its subclass. Otherwise the application of the
``instanceof`` operator results in ``false`` value. Only the left-side values
runtime (JVM) type is considered, the compile-time static type doesn't matter
at all, and therefore can be any type, including native Yeti types.

Since the type name is de-facto part of the operator, it can be considered
to be suffix operator similarly to the cast operators, and has the type
*`a* → *boolean*.

Logical operators
--------------------
.. peg

::

    Logical     = Compare SP (AsIsType* ("and" / "or") !IdChar Compare)*;
    CLogical    = CCompare SP (AsIsType* ("and" / "or") !IdChar CCompare)*;

Logical **and** expression results in **true** only, if both arguments are
**true** (otherwise the result is **false**). The right side argument
expression is not evaluated, if the left side had a **false** value.

Logical **or** expression results in **true**, if either of arguments 
**true** (otherwise the result is **false**). The right side argument
expression is not evaluated, if the left side had a **true** value.

The type of logical operators is *boolean* → *boolean* → *boolean* (the
expression results in *boolean* value and the arguments are *boolean* as well).

Logical operators have left associativity. Yeti is different from many other
programming languages by having same precedence for **and** and **or** -
this is to encourage using parenthesis to make the grouping explicit.

String concatenation
-----------------------
.. peg

::

    StrConcat   = Logical SP (AsIsType* "^" !OpChar Logical)*;
    CStrConcat  = CLogical SP (AsIsType* "^" !OpChar CLogical)*;

String concatenation operator takes two *string* values and results in
a *string* value that represents character sequence, that is concatenation
of the character sequences from the left side and right side arguments.

The type of the **^** operator is *string* → *string* → *string*.

String concatenation is associative.

List construction and concatenation
--------------------------------------
.. peg

::

    Cons        = StrConcat SP (AsIsType* ConsOp !OpChar StrConcat)*;
    CCons       = CStrConcat SP (AsIsType* ConsOp !OpChar CStrConcat)*;
    ConsOp      = "::" / ":." / "++";

List construction operator **::** takes head value from left side and
tail list from right side, and constructs a new list starting with the
head value. The type of **::** operator is *'a* → *list<'a>* → *list<'a>*.

Lazy list construction operator **:.** is similar, but takes on the right
side a function that returns the tail list when applied to unit value **()**.
The type of **:.** operator is *'a* → *(()* → *list<'a>)* → *list<'a>*.

List concatenation operator **++** takes two lists and results in 
a list that has elements from the left side list followed by the elements
from right side list, preserving the order of elements. The resulting
list is constructed lazily.
The type of **++** operator is *list<'a>* → *list<'a>* → *list<'a>*.

List construction and concatenation operators have right associativity.

Casts
--------
.. peg

::

    AsIsType    = ("is" / "as" / "unsafely\_as") !IdChar Type;

Cast operators are in reality suffix operators, as the type description
on their right side that can be considered to be part of the operator.

The **is** operator unifies the left side expressions type with the type
on the right side. The resulting value type is the unified type. It passes
the value unmodified, and due to the unification process the argument
expressions type is same as the resulting type. Its only effect is
compile-time narrowing of expression type and unification error on unexpected
type.

The **as** cast operator does a safe conversion of the argument value
into a value with given result type. The compiler verifies that the conversion
is guaranteed to be possible, and if needed, generates code to convert the
value into representation required by the given type. It's typically used
for conversions between Yeti native types and Java object types, and for
upcasting the Java types. A special case of **as** cast is casting
into opaque types.

The **unsafely_as** cast operator does a unsafe type coercion into a value
with the given result type. Unlike **as** cast, no value conversion will be
done, only JVM primitive checkcast opcode is used to change the underlying JVM
object type.
The compiler allows any coercion between Java object types that have subclass
relation (both downcasts and upcasts are possible, although it is more
reasonable to use **as** for upcasting). One of the argument or result types
can also be a Yeti native type that is represented by JVM type having a
subclass relation to the other type. Using **unsafely_as** with native
Yeti type makes the typesystem unsound, as the compiler cannot be sure anymore
that the runtime value matches the expected static type.

Both **as** and **unsafely_as** casts decouple the argument and result types,
limiting type inference.

Forward application
----------------------
.. peg

::

    ApplyPipe   = Cons SP ("|>" !OpChar Cons)* AsIsType*;
    CApplyPipe  = CCons SP ("|>" !OpChar CCons)* AsIsType*;

Forward application applies the right side function value to the left side
value. Its essentially equivalent to normal application (function value
followed by value given as argument), providing just better readability
in some cases.

The type of forward application operator is *'a* → *('a* → *'b)* → *'b*
and it has a left associativity (``x |> f |> g`` is same as ``(x |> f) |> g``
or ``g (f x)``).

Assigning values
-------------------
.. peg

::

    Assign      = ApplyPipe SP (":=" !OpChar ApplyPipe)?;
    CAssign     = CApplyPipe SP (":=" !OpChar CApplyPipe)?;

The left-side expression must provide a mutable box - either mutable
variable, mutable `structure field <FieldRef_>`_ or a `mapping reference`_
(having the form ``expression[key]``).

Assign operator stores into the mutable box a value from evaluation of the
right-side expression. The mutable boxes always store only a value reference,
which means that actual copy of the value is never done by assignment
(giving a sharing semantics for values that contain mutable boxes by
themselves, exactly as it is with the call-by-sharing function application
arguments).

The types of left-side and right-side expressions are unified. The result of
assignment expression is a normal ``()`` value, not a mutable box.

The evaluation order between left and right side of assignment is unspecified.

Loop
-------
.. _Expression:
.. peg

::

    Expression  = Assign SP ("loop" !IdChar Assign)* ("loop" !IdChar)?;
    CExpression = CAssign SP ("loop" !IdChar CAssign)* ("loop" !IdChar)?;

The expression left of ``loop`` operator must have a *boolean* type and
the right-side expression must have a *()* type. The right-side expression
may be omitted, in this case implicit ``()`` value is used in its place.
The whole ``loop`` expression has *()* type.

First the left-side expression is evaluated. The evaluation of ``loop``
expression terminates only when the left-side evaluation results in ``false``
value or exception is thrown. Otherwise the right-side expression is evaluated,
and if no exception was thrown, the ``loop`` expression evaluation is restarted
(repeating the loop while left-side is ``true``).

The ``loop`` operator has right associativity and cannot be used as a section or
function.

Value and function bindings
++++++++++++++++++++++++++++++
.. _binding:
.. peg

::

    Binding     = (StructArg / Var? !Any Id BindArg* IsType)
                  SP "=" !OpChar Expression Semicolon+ SP;
    CBinding    = (StructArg / Var? !(Any / End) Id (!End BindArg)* IsType)
                  SP "=" !OpChar CExpression Semicolon+ SP;
    Var         = "var" Space+;
    Any         = "\_" !IdChar;

Binding expression creates a new scope with a value from evaluation of the
Expression_ bound to the given identifier (Id). The binding is part of
`sequence expression`_, and the new scope is used for the following
expressions in the sequence (the part of sequence expression following
the binding can be considered to be part of the binding expression).
The type of the expression is used as the binding type.

A mutable variable binding is created, if the ``var`` keyword precedes
the binding name (Id_). The mutable variable acts as a mutable box where new
values can be `assigned <Assigning values_>`_. When a closure is created
over a mutable variable, a reference to the mutable box is stored in the
closure, without making a copy of the variable.

When underscore ``_`` is used as binding name, no binding or new scope is
created - the expression is still evaluated, but its value is discarded after
the evaluation. This can be useful when the evaluation is performed only for
its side effects.

Function arguments (`BindArg <Lambda_>`_) may be present after the binding
name (Id). This is treated as a syntactic sugar for binding a lambda_
expression - the compiler replaces the Expression with a ``do`` .. ``done``
block containing the Expression, and the function arguments are used as
the lambda expressions arguments.

If the bound value is a `function literal <Lambda_>`_ (either explicitly
written or implicit as described in the previous paragraph), then the
binding is available in the lambda expressions body scope, where it is
not polymorphic. Otherwise the bound expressions scope does not include
the binding itself (therefore an outer scopes binding with the same name
can be accessed, if one exists).

If a binding type is given (IsType_ before the ``=`` symbol), it will be
unified with the bound expression type. This is equivalent to using ``is``
operator unless the binding type is flexible.

Destructuring binding is done, if a structure literal StructArg_ is used
instead of binding name (no function arguments may follow it). In this case
the evaluation of the Expression must result in a structure value, and for
each structure field in the StructArg the identifier used as a value is bound
to the actual corresponding field value in the evaluation result.
The bound expression type is unified with a structure type where required
member set contains each field from the StructArg, with the field types
used for the corresponding created bindings.

Self-binding lambda expression
---------------------------------
.. peg

::

    SelfBind    = (Id BindArg+ / Any) IsType "=" !OpChar;
    CSelfBind   = (!End Id (!End BindArg)+ / Any) IsType "=" !OpChar;

This is another syntax for writing function literals, that comes from
generalizing the function binding_. If the binding has arguments and is
either last statement in the `sequence expression`_, or not part of sequence,
then it is considered to be a standalone lambda expression. For example,
an expression ``(_ x = x)`` is equivalent to ``do x: x done``.

Just like with normal function bindings, if the binding name (Id_) is not
an underscore ``_``, then a recursive non-polymorphic binding is created,
that is available in the lambda expressions scope.

Class definition
+++++++++++++++++++
.. peg

::

    Class       = "class" !IdChar JavaId SP MethodArgs? Extends?
                  (End / Member ("," Member)* ","? SP End);
    Extends     = "extends" !IdChar ClassName SP ArgList? SP ("," ClassName SP)*;
    Member      = SP (Method / ClassField) SP;

Class definition creates a Java class with a given name (`JavaId <Id_>`_)
inside the same JVM package where the containing module or program resides.
The class will be **public**, if the definition is part of modules top-level
`sequence expression`_, otherwise it has **package** access. **Public**
classes must be generated by the compiler to be usable outside of Yeti code,
for example by normal Java code, without any explicit initialization of
the containing Yeti module.

Class definition in sequence expression also introduces a new scope with
new class name binding (the class name bindings have separate namespace).
Non-public classes can be constructed or extended only using that binding
in Yeti code. Attempts to instantiate them outside of their scope will
result in undefined behavior.

The extends list can contain a single class name (that will be the
superclass) and any number of interface names (that will be implemented).
The ``java.lang.Object`` will be used as default superclass,
if superclass is not given in the extends list.

The class will be automatically marked as **abstract**, if it contains
abstract methods, either defined by the class itself, or derived from
superclass or any of the implemented interfaces without being overridden
with concrete implementation by the class itself. The words **abstract**,
**public** and **package** are used here with the meaning these words
have in the Java language.

The *class scope* is the scope inside the class definition, that initially
contains constructor arguments and special bindings ``this`` and ``super``.
The ``this`` binding denotes instance of the class. The ``super`` binding
also denotes instance of the class, but can be used only for calling method
on it, and any overridden method called on super binding will invoke the
parent classes corresponding method. The JVM *invokespecial* instruction is
used for that effect. Any other use of ``super`` binding (like passing the
instance value) is forbidden.

The class name may be followed by constructor argument list in parenthesis.
The constructor arguments will be bound in the classes scope and stored
in implicit private fields. The constructor argument type declaration
and value conversion is done in the same way as with the method arguments.
Only single constructor is created for the class.

Bindings from outside scope are accessible inside the class, and those used
may be stored in implicit private fields.

Class field
--------------
.. peg

::

    ClassField  = ("var" Space+)? !End Id SP (!End BindArg SP)*
                  "=" !OpChar CExpression;

A class field is a binding inside the class scope, that redefines the class
scope for all class methods and subsequent fields. A value from evaluation
of the `CExpression <Expression_>`_ is bound to the given identifier (Id),
and a new scope containing the field binding will be the new class scope.
Consequently, the scope of class field expression contains previous
(but not following) class field bindings, and all method expression scopes
contain all field bindings.

The class field is similar to binding_\ s in the `sequence expression`_:

* The **var** keyword can be used to define mutable field binding.
* Using underscore (``_``) as field name omits the actual binding and
  new scope, but still forces the evaluation of expression at class instance
  construction time.
* Lambda_ expression can be created by including arguments
  (`BindArg <Lambda_>`_) after the field name.

If a field named **serialVersionUID** is defined with value being numeric
constant, then the compiler must generate a **private static final long
serialVersionUID** field with the given initialization value into generated
JVM class.

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

The method definition creates a new method into the containing Java class.

The ``abstract`` modifier marks method to be declared without actual
implementation in the same way as in the Java language.

The ``static`` modifier marks the generated JVM method as **static**
and is allowed only in public classes (those are defined in the modules
top-level `sequence expression`_).

The method signature after the optional modifier starts with return
type and method name, followed by argument list in parenthesis.
The return value and argument types in the signature are Java types
(not Yeti types), and can denote either primitive Java types (byte, short,
char, int, long, float, double, boolean) or a Java classname.

Non-abstract methods have a method body expression after the method signature,
which is evaluated when the method is invoked.

The body expression for non-static methods is in the final class scope, thus
all field bindings are visible to the method body, along with constructor
arguments, and ``this`` and ``super`` instance bindings.
The static methods use for body expression the scope containing the class,
so no class-specific value bindings are visible there.

No exception declarations are supported for the Java class methods defined
in the Yeti code, and any method can throw any exception (it violates the
Java language semantics, but is valid for the underlaying JVM).

While the method argument types are declared as Java types, the argument
bindings have Yeti types and implicit argument casting rules are used to
convert the values having Java primitive types (as there are no Yeti
primitive types). The body expression type is inferred in the same way from
the declared Java return type. A reverse conversion is done for the returned
value, when the return type is a Java primitive type.

Declarations
+++++++++++++++
.. peg

::

    Declaration  = ClassDecl / Binding;
    CDeclaration = ClassDecl / CBinding;
    MDeclaration = TypeOrImport / Binding;
    ClassDecl    = Class Semicolon+ SP / TypeOrImport; 
    TypeOrImport = Import Semicolon+ SP / Typedef Semicolon* SP;

Declarations are parts of `sequence expression`_ used to define new bindings
that can be by the following expression parts.

Java class imports
--------------------
.. peg

::

    Import      = "import" !IdChar Space+ ClassName
                  (Colon JavaId SP ("," JavaId SP)*)?;

Class import creates a new scope for the following parts of the
`sequence expression`_, that contains imported class name bindings (in
separate namespace from other bindings). Class name bindings associate
the short class name in the local scope with full name containing the
package path (as given in the import).

The actual existence of the class is not verified by the compiler
(errors are given only when the binding is actually used and
for example class method or field signature cannot be be resolved). 

The **import** declaration has two possible forms. The simple form has
single full class name (dot-separated package path and class name) after
the ``import`` keyword. The package import has package path without
class after the ``import`` keyword, followed be colon and comma separated
list of class names to be imported from that package.

Unlike Java, the Yeti language doesn't support importing entire package
with asterisk.

Type definition
------------------
.. peg

::

    Typedef     = "typedef" !IdChar SP TypedefOf Semicolon*;
    TypedefOf   = "unshare" !IdChar SP Id /
                  (("opaque" / "shared") !IdChar SP)?
                  Id SP TypedefParam? "=" !OpChar Type;
    TypedefParam = "<" !OpChar SP Id SP ("," SP Id SP)* ">" !OpChar SP;

Type definition creates a new scope for the following parts of the
`sequence expression`_, that contains the given Type_ bound to the given
identifier (Id).

A copy is made of the bound type on every reference to preserve polymorphism,
if it contains any free type variables. The ``shared`` modifier disables this
behaviour, so the bound type itself will get unified with every reference of
the shared binding (this can be used to infer typedefs from code).

The ``unshare`` declaration can be later used to transform the former shared
typedef into normal polymorphic typedef (that will bind a copy of the shared
type).

Type definitions can have parameter list between ``<>`` symbols (when not
provided, it is same as having empty list). These will create free type
variables bound in the scope of definition of the Type itself.
The parameters must also be provided when the bound definition is used.
The given parameters will be unified to the corresponding ones in
the copy of the bound type.

The binding of type to Id is also available for the definition of the Type
itself, so recursive types can be defined, but the binding has no parameters
there. It is created by first creating a type variable bound to the Id in the
type definition scope, which is thereafter unified with the defined type.

TODO opaque types and module exports

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

