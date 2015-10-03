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
.. _identifier:
.. _ClassId:
.. _JavaId:
.. _ClassName:
.. _Variant:
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

    * Variant_ tags from the pattern form *allowed* member set in the
      corresponding variant type, unless the type is also matched with
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
subclass of its ClassId_), then first matching ``catch`` section is evaluated,
and the resulting value is used as the value of the ``try`` block.

If ``catch`` section has an exception binding Id_, then catched exceptions
value will be bound to the given identifier in that sections scope.

The expression following the ``"finally"`` keyword will be evaluated regardless
of whether any exception was thrown during the evaluation of ``try`` and
``catch`` sections. If an exception was thrown, then it will be suspended
during the evaluation of the ``finally`` section. If exception was suspended
and the ``finally`` section itself throws an exception, then the suspended
exception will be dropped (as only one exception per thread is allowed
simultaneously), otherwise the suspended exception will be rethrown after
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

The ``as`` and ``unsafely_as`` casts can also be used as sections, that result
in a function value that casts its argument value into the given type.
The argument type is inferred from the context where the cast section is used,
defaulting to free type variable (*'a*).

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

* Identifier_
* Parenthesis (that can contain `any expression`_)
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

Variant constructor is written simply as a Variant_ tag. The type of variant
constuctor is *'a* → *Variant 'a*.

Load operator
----------------
.. peg

::

    Load        = "load" !IdChar ClassName;

Load operator gives value of module determined by the ClassName_,
and the expressions type is the type of the module.

Alternatively ``load`` of module with structure type can be used as
a statement on the left side of the sequence operator. In this use
all fields of the module value will be brought into scope of right-hand
side of the sequence operator as local bindings, and additionally all
top-level typedefs_ from the module will be imported into that scope.

New operator
---------------
.. _ArgList:
.. peg

::

    New         = "new" !IdChar ClassName SP NewParam;
    NewParam    = ArgList / "[" AnyExpression "]" "[]"*;
    ArgList     = "(" SP (Expression SP ("," Expression SP)*)? ")";

New operator constructs an instance of Java class specified by ClassName_,
and the expressions type is the class type *~ClassName*.

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
the JVM class specified by the ClassId_.
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
''''''''''''''''''''''''''''

Reference operators
----------------------
.. _reference operator:
.. peg

::

    Reference   = SP PrefixOp* Primitive RefOp*;
    CReference  = SP PrefixOp* CPrimitive CRefOp*;
    RefOp       = FieldRef / MapRef / (SP (ObjectRef / "->" SP Primitive));
    CRefOp      = FieldRef / MapRef / (SP (ObjectRef / "->" SP CPrimitive));

Reference operators have highest precedence and thereby work
on simple `expressions`_.

The ``->`` operator is a function from standard library that is used
to provide custom reference operator for structure objects.

.. peg

::

    PrefixOp    = "\\" SP / "-" SP !OpChar;

The ``\`` prefix operator is shorthand form of `lambda expression`_.
A expression in form ``\``\ *value* is equivalent to ``do:`` *value* ``done``.
The argument value is ignored. If the *value* is a constant expression, then
the result is a constant function.

The ``-`` prefix operator is arithmetic negatiation. Its type is
*number* → *number*, so the negated expression must be a number, and the
resulting value is also number. Since ``-`` can be also used as binary
operator, the prefix operator cannot be used directly as function,
but the function value is bound in standard library module ``yeti.lang.std``
to ``negate`` identifier.

.. peg

::

    FieldRef    = Dot SP FieldId;

Field reference is a postfix operator that gives value of the given structure
*field*. Its type is ``{``\ *.field* ``is`` *'a*\ ``}`` → *'a*.

.. peg

::

    MapRef      = "[" Sequence SP "]";

Mapping reference takes two arguments - the mapping value preceding it and
the key value expression. The resulting value is the element corresponding
to the given key (or index). The standard library has this operator as ``at``
function with type *map<'key, 'element>* → *'key* → *'element*.
The mapping can be either *hash* map or *array*.

.. peg

::

    ObjectRef   = "#" JavaId SP ArgList?;

When ArgList_ is present, the ``#`` operator means method call, otherwise
it will be a Java class field reference.

The left side expression of the ``#`` operator is expected to have a Java
object type (*~Something*), that must have a field or method named by the
JavaId_. No type inference is done for the left-side object type.

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
(simple values or references) consecutively. First value is the
function value and the second one is the argument given to the function.
Yeti uses strict call-by-sharing evaluation semantics (call-by-sharing
is a type of call-by-value evaluation, where references are passed).

The type of application is the functions return type. If the function
value type is *'a'* → *'b*, then the given value must have the same *'a*
type and the applications resulting value type is the same *'b* type.

The application of multiple expressions is done in left-to-right order,
for example ``a b c`` is identical to ``(a b) c``. 

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

Yeti language has following arithmetic and bitwise logic operators:

+
    Arithmetic addition
-
    Arithmetic subtraction
b_or
    Bitwise logical or
b_xor
    Bitwise logical exclusive or
*
    Arithmetic multiplication
/
    Arithmetic division
%
    Remainder of integer division
div
    Integer division
shr
    Bit shift to right
shl
    Bit shift to left
b_and
    Bitwise logical and

X.

Structure merge operator with
++++++++++++++++++++++++++++++++

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
.. _typedefs:
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
.. _`any expression`:
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

