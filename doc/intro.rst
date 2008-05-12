.. ex: se sw=4 sts=4 expandtab:

===========================
Short introduction to Yeti
===========================

.. contents:: Contents

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
**do** *argument*\ **:** *expression* **done**.
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

It is possible to have multiple arguments in the function definition::

    > sub x y = x - y
    sub is number -> number -> number = <code$sub>
    > sub 5 2
    3 is number

This works also with function literals::

    > subA = do x y: x - y done
    subA is number -> number -> number = <code$>
    > subA 5 2
    3 is number

Actually, both of those previous multi-argument function definitions were
just shorthands for nested function literals::

    > subB = do x: do y: x - y done done
    subB is number -> number -> number = <code$>
    > subB 5 2
    3 is number
    > (subB 5) 2
    3 is number

All of those sub definitions are equivalent, and the last one shows
explicitly, what really happens. The nesting of function literals gives
a function, that returns another function as a result.
When first argument (5 in the example) is applied, the outer function
returns a instance of the inner function with x bound to the applied value
(``do y: 5 - y done``, when 5 was applied).
Actual subtraction is done only when another argument (2 in the example) is
applied to the returned function. The function returned from the first
application can be used as any other function.
::

    > subFrom10 = subB 10
    subFrom10 is number -> number = <yeti.lang.Fun2$1>
    > subFrom2 = subB 2
    subFrom2 is number -> number = <yeti.lang.Fun2$1>
    > subFrom10 3
    7 is number
    > subFrom2 4
    -2 is number

So, technically there are only single argument functions in the Yeti,
that get a single value as an argument and return a single value.
Multiple arguments are just a special way of using single argument
functions, that return another function (this is also called curring).
This explains the type of the multiple-argument functions -
``number -> number -> number`` really means ``number -> (number -> number)``,
a function from number to a function from number to number.

This may sound complicated, but you don't have to think how it really works,
as long as you just need a multiple-argument function - declaring
multiple arguments and appling them in the same order is enough.
Knowing how curring works allows you to use partial application (like
subFrom10 and subFrom2 in the above example).

The definition ``sub x y = x - y`` is by intent similar to the following
Java function::

    double sub(double x, double y) {
        return x - y;
    }

Unit type and functions
+++++++++++++++++++++++++++

What if you don't want to return anything?
::

    > println
    <yeti.lang.io$println> is 'a -> ()
    > println "Hello world"
    Hello world

The println function is an example of action - it is not called for getting
a returned value, but for a side effect (printing message to the console).
Since every function in Yeti must return a value, a special unit value ``()``
is returned by println.

Unit value is also used, when you don't want to give an argument.
::

    > const42 () = 42
    const42 is () -> number = <code$const42>
    > const42 ()
    42 is number
    > const42 "test"
    1:9: Cannot apply string to () -> number
        Type mismatch: () is not string

Here the ``()`` is used as an argument in the function definition. This tells
to the compiler, that only the unit value is allowed as argument (in other
words, that the argument type is unit type). Attempt to apply anything else
results in a type error.

Ignoring the argument
++++++++++++++++++++++++

There is an another way of definining function that do not want to use it's
argument value.
::

    > const13 _ = 13
    const13 is 'a -> number = <code$const13>
    > const13 42
    13 is number
    > const13 "wtf"
    13 is number
    > const13 ()
    13 is number

The ``_`` symbol is a kind of wildcard - it tells to the compiler
that any value may be given and it will be ignored.
The ``'a`` in the argument type is a free type variable - meaning any
argument type is allowed.

There is also a shorthand notation for defining function literals that
ignore the argument::

    > f = \3
    f is 'a -> number = <code$>
    > f "test"
    3 is number
    > \"wtf" ()
    "wtf" is string

Sequences and bind scopes
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Multiple side-effecting expressions can be sequenced using ``;`` operator::

    > println "Hello,"; println "world!"
    Hello,
    world!

The expression ``a; b`` means evaluate expression ``a``, discard its result
and after that evaluate expression ``b``. The result of ``b`` is then used
as a result of the sequence operator. The first expression is required
to have a unit type.
::

    > 1; true
    1:1: Unit type expected here, not a number
    > (); true
    true is boolean

The first expression gets a type error because 1 is number and not a unit.
The ``;`` operator is right-associative, so ``a; b; c`` is parsed like
``a; (b; c)``.
::

    > println "a"; println "b"; println "c"; 42
    a
    b
    c
    42 is number

A combination of binding and sequence, where binding is in the place of the
first (ignored) expression of the sequence operator, gives a bind expression.
::

    > (x = 3; x * 2)
    6 is number
    > (x = 3; y = x - 1; x * y)
    6 is number

The last one is equivalent to ``(x = 3; (y = x - 1; x * y))``.
The binding on the left side of ``;`` will be available in the expression
on the right side of the ``;`` - this is called the scope of the binding.

Because the bind expression of ``y`` is in the scope of ``x``,
the binding of ``y`` is in the scope of ``x`` and the scope of ``y``
is nested in the scope of ``x`` (meaning both ``x`` and ``y`` are available
in the scope of ``y``).

The parenthesis were used only to delimit the expressions in the interactive
environment (otherwise the scope would expand to following expressions).

Rebinding a name in a nested scope will hide the original binding::

    > x = 3; (x = x - 1; x * 2) + x
    7 is number
    x is number = 3

While the ``x`` in the nested scope (bound to value 2) hides the outer ``x``
binding to value 3, the outer binding is not actually affected by this -
the ``+ x`` uses the outer binding. **Binding a value to a name will never
modify any existing binding.**

The above example also somewhat shows, how the scoping works in the interactive
environment - it is like all the lines read were separated by ``;``. Therefore
entering a binding will cause all subsequently entered expressions to be in the
scope of that binding. A consequence of that is, that you can define multiple
bindings in one line entered into the interactive::

    > a = 5; b = a * 7
    a is number = 5
    b is number = 35
    > b / a
    7 is number

Variables
~~~~~~~~~~~~~~

The value bindings shown before were immutable.
Variable bindings are introduced using ``var`` keyword.
::

    > var x = "test"
    var x is string = test
    > x
    "test" is string
    > x := "something else"
    > x
    "something else" is string

The ``:=`` operator is an assignment operator, which changes a value stored
in the variable. Attempt to assign to an unbound name or a immutable
binding will result in an error::

    > y := 3
    1:1: Unknown identifier: y
    > println := \()
    1:9: Non-mutable expression on the left of the assign operator :=

Assigning a new value to the variable will cause a function referencing
to it also return a new value::

    > g = \x
    g is 'a -> string = <code$>
    > g ()
    "something else" is string
    > x := "whatever"
    > g ()
    "whatever" is string

Assigning values could be done inside a function::

    > setX v = x := v
    setX is string -> () = <code$setX>
    > setX "newt"
    > x
    "newt" is string

Here the setX function is used for assigning to the variable. The binding
could be rebound now with the original variable still fully accessible through
the functions defined before.
::

    > x = true
    x is boolean = true
    > g ()
    "newt" is string
    > setX "ghost?"
    > g ()
    "ghost?" is string
    > x
    true is boolean

The g and setX functions retained a reference to the variable defined before
(in the function definitions scope), regardless of the current binding.

Conditional expression
~~~~~~~~~~~~~~~~~~~~~~~~~~

Most general-purpose languages have some form of branching. Yeti is no
different - it has conditional expression marked by keyword ``if``.
The conditional expression syntax has the following general form in ABNF::

    "if" predicate-expression "then"
        expression
    *("elif" predicate-expression "then"
        expression)
    ["else"
        expression]
    "fi"

Where predicate-expression is an expression having a boolean value.
Attempt to use branches with different types will result in a type error::

    > if true then 1 else "kala" fi
    1:21: This if branch has a string type, while another was a number
    > if true then 1 else 2 fi
    1 is number

Omitting the final else will result in an implicit ``else ()`` to be
generated by the compiler::

    > if true then println "kala" fi
    kala
    > if false then println "kala" fi
    > if true then 13 fi
    1:17: This if branch has a () type, while another was a number

First one evaluated the ``println "kala"`` expression, second one the
implicit ``else ()`` and the last one was an error because of the 13 and
the implicit ``else ()`` having different types.

Because the conditional expression is an expression, and not a statement,
it is more similar to the Java ternary operator ``?:`` than the ``if``
statement - it can be used anywhere, where an expression is expected.
::

    > printAbs x = println if x < 0 then -x else x fi
    printAbs is number -> () = <code$printAbs>
    > printAbs 11
    11
    > printAbs (-22)
    22

The conditional expression is normally written on multiple lines (the above
examples were one-liners because of the interactive environment).
::

    signStr x =
        if x < 0 then
            "Negative"
        elif x > 0 then
            "Positive"
        else
            "Zero"
        fi;

    println (signStr 23);

Looping and recursion
~~~~~~~~~~~~~~~~~~~~~~~~~

Loops can be written in the form *condition-expression* **loop**
*body-expression*. The *body-expression* is evaluated only when the *condition*
is true, and after evaluating *body-expression* the loop will be retried.
::

    > var n = 1
    var n is number = 1
    > n <= 5 loop (println n; n := n + 1)
    1
    2
    3
    4
    5

Condition must have a boolean type and the *body-expression* must
have a unit type. The loop expression itself also has a unit type.

Loop could be used to define a factorial function::

    fac x =
       (var n = x;
        var accum = 1;
        n > 1 loop
           (accum := accum * n;
            n := n - 1);
        accum)

This doesn't look like a definition of factorial. More declarative factorial
function can be written using recursion::

    fac x =
        if x <= 1 then
            1
        else
            x * fac (x - 1)
        fi

There is a special case of scoping rules for function bindings, which tells
that when a value bound is a function literal, then the function literal will
be also in the binding scope (in other words, the *self*-binding can be
used inside the function). Therefore the fac function can use its own binding.

This resulting function tells basically that factorial of 0 or 1 is 1 and
factorial of larger numbers is the ``x * fac (x - 1)``. When tried in the
interactive, it will work as expected::

    > fac x = if x <= 1 then 1 else x * fac (x - 1) fi
    fac is number -> number = <code$fac>
    > fac 5
    120 is number

There is one problem with this implementation - it is less efficient because
of the nesting of the expressions. Because the value returned is a result
of the multiplication of x and value of the inner call, the outer functions
frame must remain active while calling the inner one. The evaluation
will go on like that::

    fac 5 = 5 * fac 4
          = 5 * (4 * fac 3)
          = 5 * (4 * (3 * fac 2))
          = 5 * (4 * (3 * (2 * fac 1)))
          = 5 * (4 * (3 * (2 * 1)))
          = 5 * (4 * (3 * 2))
          = 5 * (4 * 6)
          = 5 * 24
          = 120

The intermediate expression ``5 * (4 * (3 * (2 * fac 1)))`` basically means,
that all those nested applications of fac 5, fac 4, fac 3, fac 2 are suspended
(in their stack frames) while evaluating the final fac 1 - producing the
long unevaluated expression. This consumes extra memory (O(n) stack memory
usage in this case) and makes the implementation noticeably less efficient.

Solution to this is to rewrite the recursive function to use a *tail recursion*,
which means that the function return value is directly the result of the 
recursive application. In this case the storing of the functions intermediate
states (frames) is not necessary, since the function does nothing after the
recursive tail call.

Tail-recursive factorial function can be written like that::

    tailFac accum x =
        if x <= 1 then
            accum
        else
            tailFac (accum * x) (x - 1)
        fi;

    fac' x = tailFac 1 x;

Additional argument ``accum`` (accumulator) is introduced for storing the
intermediate result of computation of the factorial. The accumulator is
initialized to 1 (since the factorial <= 1 is 1) in the one-argument ``fac'``
factorial definition. Using accumulator is a standard technique for
transforming non-tail-recursive algorithms to tail-recursive ones.

The resulting ``fac'`` gives same result as the previous non-tail-recursive
``fac``, when tried in the interactive environment::

    > tailFac accum x = if x <= 1 then accum else tailFac (accum * x) (x - 1) fi
    tailFac is number -> number -> number = <code$tailFac>
    > fac' x = tailFac 1 x
    fac' is number -> number = <code$fac$z>
    > fac' 5
    120 is number

But the evaluation process is different::

    fac' 5 =
        tailFac 1 5 = tailFac (1 * 5) (5 - 1) =
        tailFac 5 4  = tailFac (5 * 4) (4 - 1) =
        tailFac 20 3 = tailFac (20 * 3) (3 - 1) =
        tailFac 60 2 = tailFac (60 * 2) (2 - 1) =
        tailFac 120 1 = 120

As it can be seen, the nesting of the expressions and suspension of the
intermediate function applications won't happen here. The compiler actually
converts the tail call of the ``tailFac`` into changing the argument values
and a jump instruction to the start of the function - resulting in a
code very similar to that of the first factorial example using explicit
loop.

