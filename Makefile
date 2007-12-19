compile: CodeWriter.java YetiParser.java YetiType.java YetiCode.java YetiC.java\
         CompileException.java
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
