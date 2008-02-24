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

pub:
	-mkdir -p /tmp/yeti
	-rm -rf /tmp/yeti/git
	git-clone --bare . /tmp/yeti/git
	cd /tmp/yeti/git && git-update-server-info
	rsync -rlpzv -e ssh /tmp/yeti/git linux.ee:/home/mzz/public_html/yeti/
