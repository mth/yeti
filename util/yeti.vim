" Vim syntax file
" Language: Yeti

" For version 5.x: Clear all syntax items
" For version 6.x: Quit when a syntax file was already loaded
if version < 600
  syntax clear
elseif exists("b:current_syntax") && b:current_syntax == "yeti"
  finish
endif

if version >= 600
 setlocal iskeyword=39,48-57,A-Z,a-z,_,?
else
 set iskeyword=39,48-57,A-Z,a-z,_,?
endif

" Yeti is case sensitive.
syn case match

syn match yetiExternal "^#!/[/a-yz]\+/yeti$"
syn case ignore
syn keyword yetiTodo contained TODO FIXME XXX NOTE
syn case match
syn match yetiComment "//.*$" contains=yetiTodo
syn region yetiComment start="/\*" end="\*/" contains=yetiTodo,yetiComment

" Errors
syn match yetiErr "}\|\]\|\*/"
syn match yetiErr "\<\(done\|esac\|yrt\|then\|elif\|fi\|else\|of\|catch\|finally\)\>"
syn match yetiParenErr ")"


" Enclosing delimiters
syn region yetiEncl start="(" end=")" contains=TOP,yetiParenErr
syn region yetiEncl matchgroup=yetiKW start="{" matchgroup=yetiKW end="}" contains=TOP
syn region yetiEncl matchgroup=yetiKW start="\[" matchgroup=yetiKW end="\]" contains=TOP

syn region yetiIf matchgroup=yetiKW start="\<if\>" matchgroup=yetiKW end="\<fi\>" matchgroup=yetiKW contains=TOP

syn keyword yetiKW then elif else containedin=yetiIf contained

syn region yetiDo matchgroup=yetiKW start="\<do\>" matchgroup=yetiKW end="\<done\>" matchgroup=yetiKW contains=TOP

syn region yetiCase matchgroup=yetiKW start="\<case\>" matchgroup=yetiKW end="\<esac\>" matchgroup=yetiKW contains=TOP

syn keyword yetiKW of containedin=yetiCase contained

syn region yetiTry matchgroup=yetiKW start="\<try\>" matchgroup=yetiKW end="\<yrt\>" matchgroup=yetiKW contains=TOP

syn keyword yetiKW catch containedin=yetiTry contained skipwhite nextgroup=yetiClassName
syn keyword yetiKW finally containedin=yetiTry contained

" Classes
syn keyword yetiType class nextgroup=yetiClassDef
syn region yetiClassDef matchgroup=yetiClassDef start="\_\s\+\w\+\>" matchgroup=yetiType end="\<end\>" keepend contains=yetiExtends,yetiClassType,yetiMethodArgs,yetiFieldDef,yetiComment contained
syn keyword yetiClassType void boolean byte short int long float double number var contained
syn keyword yetiExtends extends contained nextgroup=yetiExtendClass
syn match yetiExtendClass "\_\s\+[A-Za-z_$]\+\((\(\w\|\s\|\n\|,\))\)\?" contained
syn region yetiMethodArgs matchgroup=yetiClassDef start="\w\+\s*(" end=")\@=" nextgroup=yetiMethodDef contains=yetiComment,yetiClassType contained
syn region yetiMethodDef matchgroup=yetiClassDef start=")" end=",\|\<end\>" contains=TOP contained
syn region yetiFieldDef matchgroup=yetiOperator start="=" matchgroup=yetiClassDef end=",\|\<end\>" contains=TOP contained

syn keyword yetiKW for forHash synchronized
syn keyword yetiKW module program throw

syn keyword yetiType var norec get set

syn keyword yetiAnyVar _
syn keyword yetiBoolean false true none
syn keyword yetiFunc array filter fold id mapHash number head reverse tail
syn keyword yetiFunc any all find index const at on setHashDefault flip sum
syn keyword yetiFunc nullptr? empty? min max maybe abs push exit shift
syn keyword yetiFunc defined? wrapArray concat concatMap negate splitBy
syn keyword yetiFunc ln exp cos sin tan acos asin atan sqrt strReplace
syn keyword yetiFunc strSplit substAll strLength strUpper strLower strTrim
syn keyword yetiFunc strSlice strRight strStarts strEnds strIndexOf
syn keyword yetiFunc strLastIndexOf strLeft strLeftOf strRightOf length
syn keyword yetiFunc drop sort sortBy same? revAppend list pop swapAt
syn keyword yetiFunc setArrayCapacity catSome map2 withHandle openInFile
syn keyword yetiFunc openOutFile readFile writeFile getLines putLines
syn keyword yetiFunc getContents iterate take splitAt strJoin strPad like
syn keyword yetiFunc delete keys matchAll string apply clearHash strChar
syn keyword yetiFunc failWith lazy int map map' takeWhile collect pair nub
syn keyword yetiFunc strLastIndexOf' copyHash copyArray deleteAll
syn keyword yetiExternal load
syn keyword yetiExternal import skipwhite nextgroup=yetiClassName

syn keyword yetiOperator not and or in or div loop shl shr
syn keyword yetiOperator classOf instanceof
syn match yetiOperator #[:;=~!+\-*%<>]\+\|`[a-zA-Z_?]\+`\|/[^/*]\@=#

syn match yetiConst "(\s*)"
syn match yetiConst "\[\s*\]"
syn match yetiConst "\[:]"
syn match yetiConst "\<\u\(\w\|'\)*\>"

syn region yetiEmbedded contained matchgroup=Delimiter start="\\(" matchgroup=Delimiter end=")" contains=TOP,yetiParenErr
syn region yetiString start=+"+ skip=+\\\\\|\\`\|\\"+ end=+["\n]+ contains=yetiEmbedded
syn region yetiString start=+\<'+ end=+'+

" Numbers: supporting integers and floating point numbers
syn match yetiNumber "-\=\<[+-]\?\d*\.\?\d\+\([eE]\d*\)\?\>"

syn match yetiMemberOp "\(\<\w\+\)\?#\w\+\(()\)\?"

" Yeti type definition syntax
syn region yetiTypeDef matchgroup=yetiType start="\<type\>" end="=" nextgroup=yetiTypeDecl contains=NOTHING
syn keyword yetiType is as unsafely_as nextgroup=@yetiTypeDecls
"syn match yetiTypeDecl contained /\(\l\|_\)\(\w\|'\)*/
syn cluster yetiTypeDecls contains=yetiTypeDecl,yetiTypeVar
syn region yetiTypeDecl transparent start="(" end=")" contained contains=@yetiTypeDecls,yetiComment nextgroup=yetiTypeOp
syn match yetiTypeDecl "\~\(\w\|\.\|\$\)*\(\[\]\)*" contained nextgroup=yetiTypeOp
syn match yetiTypeDecl "\l\(\w\|'\|?\)*" contained nextgroup=yetiTypeOp
syn match yetiTypeVar "['^]\(\w\|'\)*\(\[\]\)*" contained nextgroup=yetiTypeOp
syn match yetiTypeDecl "\<\u\(\w\|'\)*\>" contained nextgroup=@yetiTypeDecls
syn match yetiTypeDecl "()" contained nextgroup=yetiTypeOp
syn match yetiTypeOp "->\||" contained nextgroup=@yetiTypeDecls
syn region yetiTypeOp matchgroup=YetiTypeDelim start="<" matchgroup=YetiTypeDelim end=">" contained contains=@yetiTypeDecls,yetiComment nextgroup=yetiTypeOp
syn match yetiTypeOp "\_\s\+" contained nextgroup=yetiTypeOp
syn match yetiTypeDecl "\_\s\+" contained nextgroup=@yetiTypeDecls

syn match yetiClassName "[A-Za-z]\(\w\|\.\|\$\)*\(\[\]\)*\(()\)\?" contained
syn keyword yetiKW new skipwhite nextgroup=yetiClassName

" Synchronization
syn sync minlines=50
syn sync maxlines=500

syn sync match yetiDoSync grouphere  yetiDo "\<do\>"
syn sync match yetiDoSync groupthere yetiDo "\<done\>"

syn sync match yetiIfSync grouphere  yetiIf "\<if\>"
syn sync match yetiIfSync groupthere yetiIf "\<fi\>"

syn sync match yetiCaseSync grouphere  yetiCase "\<case\>"
syn sync match yetiCaseSync groupthere yetiCase "\<esac\>"

syn sync match yetiTrySync grouphere  yetiTry "\<try\>"
syn sync match yetiTrySync groupthere yetiTry "\<yrt\>"

syn sync match yetiClassDef grouphere  yetiTry "\<class\>"
syn sync match yetiClassDef groupthere yetiTry "\<end\>"

" Define the default highlighting.
" For version 5.7 and earlier: only when not done already
" For version 5.8 and later: only when an item doesn't have highlighting yet
if version >= 508 || !exists("did_yeti_syntax_inits")
  if version < 508
    let did_yeti_syntax_inits = 1
    command -nargs=+ HiLink hi link <args>
  else
    command -nargs=+ HiLink hi def link <args>
  endif

  HiLink yetiParenErr	Error
  HiLink yetiErr	Error

  HiLink yetiComment 	Comment

  HiLink yetiExternal	Include
  HiLink yetiFunc       Function
  HiLink yetiKW 	Keyword

  HiLink yetiConst	Constant

  HiLink yetiOperator	Operator
  HiLink yetiAnyVar	Keyword

  HiLink yetiBoolean	Boolean
  HiLink yetiNumber	Number
  HiLink yetiString	String

  HiLink yetiType	Type
  HiLink yetiTypeDecl	Type
  HiLink yetiTypeOp	Type
  HiLink yetiClassType	Type
  HiLink yetiExtends	Type
  HiLink yetiTypeDelim  Delimiter

  HiLink yetiTodo	Todo

  delcommand HiLink
endif

let b:current_syntax = "yeti"

" vim: ts=8
