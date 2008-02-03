compile: CodeWriter.java SourceReader.java YetiParser.java YetiType.java\
         YetiCode.java YetiC.java CompileException.java YetiTypeAttr.java\
         YetiBuiltins.java
	javac -d . -classpath asm-3.1.jar:. $+

jt: JavaType.java
	javac -d . -classpath asm-3.1.jar:. $+

.PHONY: lib

lib:
	cd lib && $(MAKE) lib

test: compile
	java -classpath .:asm-3.1.jar YetiCode\$$Test
	java -classpath .:lib Test

yeti.jar: clean lib compile
	jar xf asm-3.1.jar
	jar cmf manifest $@ yeti/lang org/objectweb/asm
	rm -r org

clean:
	-rm -f yeti.jar
	-rm -r yeti

g:
	./yc guess.yeti

jtt: jt
	java -cp asm-3.1.jar:. yeti.lang.compiler.JavaType yeti/lang/compiler/JavaType
