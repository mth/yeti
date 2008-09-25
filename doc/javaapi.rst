=======================
Yeti Java interface
=======================

Import declation
~~~~~~~~~~~~~~~~~~

::

        import package.subpackage.Class;

Import declaration adds Class into local class namespace.
Import declarations may appear anywhere in the file.


new operator
~~~~~~~~~~~~~~
::

        new package.subpackage.Class(arguments...)

or::

        new Class(arguments...)

Argument list is comma separated. When full classname with package path
is not provided, the Class must be imported into local class namespace.


Method call
~~~~~~~~~~~~~
::

        expr#method(arguments...)
        Class#method(arguments...)

Method calls are recognized by having parenthesized argument list after
#-notated method name. When # is preceded by identifier defined in local
class namespace, it will be considered to be a static method invocation.


Class field reference
~~~~~~~~~~~~~~~~~~~~~~~
::

        expr#field
        Class#field

Class field references are # notated references, where name following # is
not followed by parenthesis. When # is preceded by identifier defined in local
class namespace, it will be considered to be a static field reference.


Automatic conversions
~~~~~~~~~~~~~~~~~~~~~~~

Value given as java method/constructor argument must have fully defined type
(bindings with type variables may not be used).
Java method/constructor argument types are not used on the type inference.

Automatic conversion on arguments, when calling java methods:

-	string --> java.lang.String
-	string --> java.lang.StringBuffer
-	string --> java.lang.StringBuilder
-	string --> char[]
-	list?<'a> --> java.util.Collection<?convertable-of 'a>
-	list?<'a> --> java.util.List<?convertable-of 'a>
-	list?<'a> --> java.util.Set<?convertable-of 'a>
-	list?<'a> --> (convertable-of 'a)[]
-	'a[] --> java.util.Collection<?'a>
-	'a[] --> java.util.List<?'a>
-	number --> byte
-	number --> double
-	number --> float
-	number --> int
-	number --> long
-	number --> short
-	number --> java.lang.Byte
-	number --> java.lang.Short
-	number --> java.lang.Float
-	number --> java.lang.Double
-	number --> java.lang.Integer
-	number --> java.lang.Long
-	number --> java.lang.BigInteger
-	number --> java.lang.BigDecimal
-       number --> java.lang.Number
-       number --> yeti.lang.Num
-	boolean --> !boolean
-	boolean --> java.lang.Boolean
-	'a -> 'b --> yeti.lang.Fun
-	hash<'a, 'b> --> java.util.Map<'a, 'b>
-	{ .\.\. } --> yeti.lang.Struct
-	SomeTag 'a --> yeti.lang.Tag

Note: list?<'a> conversions will be done recursively for the 'a.

Automatic conversion on return values of java methods:

-	java.lang.String --> string
-	java.lang.Boolean --> boolean
-	yeti.lang.Num --> number
-	!boolean --> boolean
-	byte   --> number
-	double --> number
-	float  --> number
-	int    --> number
-	long   --> number
-	short  --> number
-       char   --> string
-       void   --> ()


Class definition
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

::

        class Foo(int x) extends Iterator
            var n = x,
        
            Object next()
                n := n + 1;
                "\(n)",
        
            boolean hasNext()
                n < 10,
        
            void remove()
                (),
        
            void setValue(int v)
                n := v
        end

