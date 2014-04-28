all: intro.html intro.rst

%.html: %.rst
	rst2html --stylesheet-path=voidspace.css $< $@

%.odt: %.rst
	rst2odt $< $@

%.pdf: _%.latex
	pdflatex -halt-on-error -draftmode $<
	pdflatex -halt-on-error $<
	mv _$@ $@

_intro.latex: intro.rst
	#rst2latex $< $@
	#rst2newlatex --no-toc-backlinks $< $@
	rst2latex $< $@

clean:
	rm -f _intro.toc _intro.latex _intro.aux _intro.out _intro.log intro.html intro.pdf
