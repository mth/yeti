===========================
Yeti Macros
===========================

New syntax.
~~~~~~~~~~~~~~~~~~~

Macro is a compile time function. It is typed.
::

        macrodef <name> <types...> of
                pattern1: value1;
                patterns...: values...;
        end

For example::

        macrodef struct1 symbol value of
                (id) v: { (id) = v }
        end

        (struct1 fish 33).fish == 33

How macro argument will be parsed is determined by it's type.
Macro arguments can generally contain symbol, value, seq, list and macro types.

 value<type>             - normal runtime value of given type
 symbol                  - identifier, parameter may be a list of allowed identifiers
 seq<types...>           - sequence of parameters
 macro<types..., result> - some macro


Variable fields struct?
::

        macrodef struct list<seq<symbol, value<any>>> of
                []: {};
                (id) v :: rest: { (id) = v } with struct rest;
        end

        struct [fish 33, camel 'toe'].fish == 33;

Old syntax.
~~~~~~~~~~~~~~~~~~~
Macro is a compile time code template function.
::

        foo = syntax (2: 3)
                     (`...`: syntax x: x / 2)
                     ((x; y): x + y)
                     (x: x * x);
        
        foo 2;      // 3
        foo ... 4   // 4 / 2
        foo (2; 3)  // 2 + 3
        foo (1 + 2) // (1 + 2) * (1 + 2)
        
        bar = syntax (x: syntax (y: x * y));

        bar 3 4    // 3 * 4

Macros are first-class values at compile time. That means for example, that
it is possible to pass syntax definition as an argument to another syntax
definition or not bind syntax definition to a name::

        (syntax (x: x * x)) 3

Recursive use of macros can be quite powerful, but syntax elements
with iterated content will create problems. This can be solved by having
a way to convert that iteration into recursive representation.

For example, it is hard to construct variable length list (like [1, 2, 4])
using macros, but it will be easy to construct 1 \:: 2 \:: 4 \:: [].

Such concatenative representations have to be given to other syntax elements
also. For example classes::

        class Foo
                void print(String s)
                        println s
        end class ...
                String toString()
                        "kala"
        end

Another problematic syntax elements will be structures, hashes and case.

Implementation.
~~~~~~~~~~~~~~~~~

Implementation should be quite easy.

 1. Parser has to parse the syntax definitions.
 2. Analyser has to recognize and analyze the syntax definitions.
 3. Bind expression must support binding syntax definitions.
 4. Modules have to store top-level syntax definitions
    (similar to type definitions).

Note: Syntax definition can be used only where application/operator is expected.

Application of syntax definition has 2 stages.

 1. Pattern matching against the syntax argument pattern.
    Pattern variables will be bound (the identifiers have to be made unique).
 2. The pattern body will be analyzed like any other AST subtree,
    but the scope has the captured pattern variables.

There is a special case with appling syntax definition in a
sequence - if the pattern body is a sequence, the bindings from
it have to be bound into the outer sequence.

