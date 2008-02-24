.PHONY: compile lib

compile: asm-3.1.jar
	if [ ! -d yeti ]; then cd lib && $(MAKE) lib; fi
	cd c && $(MAKE) compile

lib:
	cd lib && $(MAKE) lib

yeti.jar: clean lib compile
	jar xf asm-3.1.jar
	jar cmf manifest $@ yeti/lang org/objectweb/asm
	rm -r org

clean:
	-rm -f yeti.jar
	-rm -r yeti

pub:
	-rm -rf /tmp/yeti
	git-clone --bare . /tmp/yeti
	cd /tmp/yeti && git-update-server-info
	rsync -rlpz -e ssh /tmp/yeti linux.ee:/home/mzz/public_html/git/
	rm -rf /tmp/yeti
