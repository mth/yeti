.PHONY: compile lib

compile:
	ant compiler

lib:
	ant lib

yeti.jar:
	ant rebuild

gcj:
	ant clean noant jar
	gcj -o yetic -O2 --main=yeti.lang.compiler.yeti yeti-noant.jar

clean:
	-rm .build

pub:
	git push
	yeti-to-hub

wc:
	wc lib/*.java c/*.java c/*.yeti modules/*.yeti util/*.java
