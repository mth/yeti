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

The grammar can be extracted from manual and compiled into runnable parser
by invoking following ant target in the Yeti source tree root::

    ant grammar

Mouse parser generator is used and the resulting ``yeti-peg.jar`` can be
invoked using ``java -jar`` at command line. The PEG grammar given here
therefore follows the exact syntax used by the `Mouse parser generator`_.

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
Single quote character (``'``) can be escaped by writing it twice,
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
with ``'``), Java class name (prefixed with ``~``), structure, variant or
type name. Type name may be followed by optional parameter list that is
embedded between ``<`` and ``>``. Java class name may be followed by one
or more ``[]`` pairs, indicating that it is JVM array type (in this case
the ClassName might be also Java primitive type name like *char*).

Function type is in the form *argument-type* ``->`` *return-type* (the
above grammar defines it like type list separated by arrows, because the
*return-type* itself can be a function type without any surrounding
parenthesis).

Structure type
----------------
.. peg

::

    StructType  = FieldType ("}" / "," SP "}" / "," StructType);
    FieldType   = SP ("var" !IdChar SP)? "\."? Sym SP "is" !IdChar Type;

Structure type denotes field list surrounded by ``{`` and ``}``.
The field names can be prefixed with dot, denoting required fields
(if any of the fields is without dot, then **all** listed fields
form the allowed fields set in the structure type).

Variant type
--------------
.. peg

::

    VariantType = Variant "\."? !IdChar SP BareType SkipSP;

Single variant type consists of the capitalized variant tag followed
by variants value type. The variant tag can be suffixed with dot,
denoting that it isn't a required variant.

The full variant type consists of single variants separated by ``|``
symbols. If any of the tags in full variant type has the dot prefix,
then **all** listed fields form the allowed variants set).

Composite literals
+++++++++++++++++++++

String
---------
.. peg

::

    String      = SimpleString /
                  "\"\"\"" ("\\" StringEscape / !"\"\"\"" _)* "\"\"\"" /
                  "\"" ("\\" StringEscape / ^["])* "\"";
    StringEscape = ["\\abfnrte0] / "u" Hex Hex Hex Hex /
                   "(" SP InParenthesis SP ")" / [ \t\r\n] SP "\"";

Lambda expression
--------------------
.. peg

::

    Lambda      = "do" !IdChar BindArg* Colon AnyExpression "done" !IdChar;
    BindField   = SP Id IsType ("=" !OpChar SP Id)? SP;
    StructArg   = "{" BindField ("," BindField)* "}";
    BindArg     = SP (Id / "()" / StructArg);

List literals
----------------
.. peg

::

    List        = "[:]" / "[" SP (Items ("," SP)?)? "]";
    Items       = HashItem ("," HashItem)* / ListItem ("," ListItem)*;
    ListItem    = Expression SP ("\.\." !OpChar Expression)? SP;
    HashItem    = Expression Colon Expression SP;

Structure literals
---------------------
.. peg

::

    Struct      = "{" Field ("," Field)* ","? SP "}";
    Field       = SP Modifier? FieldId
                  (&(SP [,}]) / BindArg* IsType "=" !OpChar AnyExpression) SP;
    FieldId     = Id / "``" ^[`]+ "``";
    Modifier    = ("var" / "norec") Space+;


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

    Reference   = SP ("\\" SP / "-" SP !OpChar)* Primitive RefOp*;
    CReference  = SP ("\\" SP / "-" SP !OpChar)* CPrimitive CRefOp*;
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

