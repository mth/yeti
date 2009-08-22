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
	git repack -d
	-rm -rf /tmp/yeti
	git-clone --bare . /tmp/yeti
	cd /tmp/yeti && git-update-server-info
	rsync -rlpz -e ssh /tmp/yeti linux.ee:/home/mzz/public_html/git/
	rm -rf /tmp/yeti

wc:
	wc lib/*.java c/*.java c/*.yeti modules/*.yeti util/*.java util/*.yeti
