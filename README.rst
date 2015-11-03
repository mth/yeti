===================================================
  ML like functional language for the JVM
===================================================

* `Yeti home page <http://mth.github.io/yeti/>`_
* `Learn the language <http://dot.planet.ee/yeti/intro.html>`_
* You need `ant <http://ant.apache.org/>`_ to compile the compiler
  and standard library.

Compile the compiler and standard library, and run tests::

    ant test

Directory structure.

doc
    Various documentation and documentation drafts. Only ``intro.rst`` and
    ``reference.rst`` are currently worth reading, other text there probably
    only confuses the reader.

c
    The compiler source code.

examples
    Examples of Yeti code.

lib
    Runtime library written in Java.

modules
    Standard library written in Yeti.

tests
    Automatic tests for the compiler.

util
    Various utility code used for building the compiler.

util/syntax
    Syntax hilighting files for ViM and Emacs.

util/jedit
    Syntax hilighting for JEdit.
