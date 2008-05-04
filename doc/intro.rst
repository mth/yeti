===========================
Short introduction to Yeti
===========================

What is Yeti?
~~~~~~~~~~~~~~~~~~
Yeti is a ML-derived strict, statically typed functional language,
that runs on JVM. Following tutorial is mostly meant for C/Java programmers
(most of it will be trivial, if you happen to know any ML family language).
Strict means that function arguments will be evaluated before function call
(most imperative languages like Java are strict). Static typing means
that consistency of expression types is checked at compile time.
Yeti compiler infers the types automatically from the code, without needing
explicit type annotations. Functional means that functions are first-class
values (like objects are in OO languages).

Interactive evaluation
~~~~~~~~~~~~~~~~~~~~~~~~~
Yeti comes with interactive evaluation environment. This can be started
by simply running ``java -jar yeti.jar``::

        ~/yeti$ java -jar yeti.jar
        Yeti REPL.

        >

At least J2SE 1.4 compatible JVM is required.
REPL means Read-Eval-Print-Loop - a short description of the interactive
environment, which reads expressions from user, evaluates them and prints
the resulting values. Yeti REPL can be terminated by sending End-Of-File
mark (Ctrl-D on Unix systems or Ctrl-Z on Windows systems).
It is useful to use some readline wrapper for more confortable editing,
when some is available (rlwrap can be installed on Debian or Ubuntu linux
systems using ``aptitude install rlwrap`` or ``sudo aptitude install rlwrap``).
::

        ~/yeti$ alias yc='rlwrap java -jar yeti.jar'
        ~/yeti$ yc
        Yeti REPL.

        > 42
        42 is number
        >

Here expression 42 is typed. REPL answers by telling 42 is number.
Most expression values are replied in the form ``value is sometype`` -
here ``42`` is the value of the expression and ``number`` is the type.

Primitive types
~~~~~~~~~~~~~~~~~~

Yeti has string, number, boolean and () as primitive types.
String literals can be quoted by single or double quotes::

        > "some text"
        "some text" is string
        > 'some text'
        "some text" is string
        > "test\n"
        "test\n" is string
        > 'test\n'
        "test\\n" is string
        > 'i''m'
        "i'm" is string

The difference is that double-quoted strings may contain escaped sequences
and expressions, like "\n" while single-quoted string literal will interpret
everything expect the apostrophe as a literal.

Double-quoted strings may contain embedded expressions::

        > "1 + 2 = \(1 + 2)"
        "1 + 2 = 3" is string

Booleans have just two possible values::
        > true
        true is boolean
        > false
        false is boolean

While all numbers have statically a number type, there is runtime
distinction between integers, rational numbers and floating-point numbers.
::

        > 0.4
        0.4 is number
        > 2/5
        0.4 is number
        > 4/2
        2 is number
        > 4e2
        400.0 is number
        > 4e / 2
        2.0 is number
        > 2
        2 is number

Here 0.4 and integer divisions will result in rational numbers,
4e2 and 4e are floating point numbers (e - exponent) and 2 is integer.
Floating-point arithmetic will also result in floating-point numbers
and so 2.0 is printed.

Unit type (also called () type) has just one possible value - (),
but REPL won't print it.
::

        > ()
        >

Value bindings
~~~~~~~~~~~~~~~~~~
Values can be named - this is called binding value to a name.
In Java terms a value binding is a final variable - those bindings are
by default immutable.
::

        > a = 40
        a is number = 40
        > a
        40 is number
        > b
        1:1: Unknown identifier: b
        > a + 2
        42 is number

Attempt to use an unbound name will result in error.

Functions
~~~~~~~~~~~~~
Functions are values and can be defined using function literal syntax
**do** argument\ **:** expression **done**.
::

        > do x: x + 1 done
        <code$> is number -> number

The function value is printed as <classname>, where classname is the name
of the Java class generated for implementing the function. Function type
is written down as argument-type -> result-type. Here compiler inferred
that both argument and result types are numbers, because the function
adds number 1 to the argument value. Using the function is called application
(or a function call).
::

        > inc = do x: x + 1 done
        inc is number -> number = <code$>
        > inc 2
        3 is number

Here the same function literal is bound to a name ``inc`` and then value
2 is applied to it. Since application syntax is simply function value
followed by argument value, a value can be applied directly to
a function value::

        > do x: x + 1 done 2
        3 is number

Defining function value and giving it a name is a common operation, so Yeti
has a shorthand syntax for it.
::

        > dec x = x - 1
        dec is number -> number = <code$dec>
        > dec 3
        2 is number

It's almost exactly like a value binding, but function argument is placed
after the binding name. The last code example is similar to the following
Java code::

        int dec(int x) {
            return x;
        }
        
        ...
            dec(3)

Multiple arguments
++++++++++++++++++++++++

The function definition can have multiple arguments::

        > add x y = x + y
        add is number -> number -> number = <code$add>
        > add 2 4
        6 is number

As expected, it also works with function literal::

        > sub = do x y: x - y done
        sub is number -> number -> number = <code$>
        > sub 2 4
        -2 is number

This multiple-arguments function definition is actually just
a shorthand for a nested function literals::

        > sub_ = do x: do y: x - y done done
        sub_ is number -> number -> number = <code$>
        > sub_ 2 4
        -2 is number
        > (sub_ 2) 4
        -2 is number


        > sub_from_2 = sub_ 2
        sub_from_2 is number -> number = <yeti.lang.Fun2$1>
        > sub_from_2 4
        -2 is number
        > sub_from_2 4
        -2 is number
        > add_to_3 = add 3
        add_to_3 is number -> number = <yeti.lang.Fun2$1>
        > add_to_3 2
        5 is number
        > 

