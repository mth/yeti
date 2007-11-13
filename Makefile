compile: YetiParser.java YetiType.java YetiCode.java
	javac -d . -classpath asm-3.1.jar $+

.PHONY: lib

lib:
	cd lib && $(MAKE) lib

test: compile
	java -classpath .:asm-3.1.jar YetiCode\$$Test
	java -classpath .:lib Test

clean:
	-rm -r yeti
