.PHONY: compile lib

compile:
	ant compiler

lib:
	ant lib

yeti.jar:
	ant clean yeti

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
	wc */*.java modules/*.yeti
