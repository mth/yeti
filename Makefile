compile: YetiParser.java YetiType.java YetiCode.java Core.java Fun.java \
         Tag.java TagCon.java Struct.java
	javac -d . -classpath asm-3.1.jar $+

test: compile
	java -classpath .:asm-3.1.jar YetiCode\$$Test
	java Test

clean:
	rm -r yeti
