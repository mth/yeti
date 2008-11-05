===========================
Yeti Macros
===========================

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
using macros, but it will be easy to construct 1 \:: 2 \:: 4.

Such recursive representations have to be given to other language constructs
also. For example classes could have concatenation::

        class Foo
                void print(String s)
                        println s
        end class ...
                String toString()
                        "kala"
        end

Another problematic constructs will be structures, hashes and case.

