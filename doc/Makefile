RES = intro.html intro.pdf reference.html reference.pdf

all: $(RES)

%.html: %.rst
	rst2html --stylesheet-path=voidspace.css $< $@

%.odt: %.rst
	rst2odt $< $@

%.pdf: _%.latex
	pdflatex -halt-on-error -draftmode $<
	pdflatex -halt-on-error $<
	mv _$@ $@

_%.latex: %.rst preamble.tex
	#rst2newlatex --no-toc-backlinks $< $@
	rst2latex --stylesheet=preamble.tex $< $@

#reference.pdf: reference.rst
#	rst2pdf $< -o $@

clean:
	rm -f _*.toc _*.latex _*.aux _*.out _*.log $(RES)
