intro.html: intro.rst
	rst2html $< $@

intro.pdf: _intro.latex
	pdflatex _intro.latex
	mv _intro.pdf intro.pdf

_intro.latex: intro.rst
	rst2latex $< $@
