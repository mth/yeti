intro.html: intro.rst
	rst2html --stylesheet-path=voidspace.css $< $@

intro.odt: intro.rst
	rst2odt $< $@

intro.pdf: _intro.latex
	pdflatex -halt-on-error -draftmode _intro.latex
	pdflatex -halt-on-error _intro.latex
	mv _intro.pdf intro.pdf

_intro.latex: intro.rst
	#rst2latex $< $@
	#rst2newlatex --no-toc-backlinks $< $@
	rst2newlatex $< $@

clean:
	rm -f _intro.toc _intro.latex _intro.aux _intro.out _intro.log intro.html intro.pdf
