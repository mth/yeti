.PHONY: compile lib

compile:
	cd c && $(MAKE) compile

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

jtt: compile
	java -cp asm-3.1.jar:. yeti.lang.compiler.JavaType yeti/lang/compiler/JavaTypeReader

lee:
	tar c .git | ssh -T linux.ee 'cd public_html/yeti/git rm -rf .git; tar x'
