" Vim syntax file
" Language:    Templater

" For version 5.x: Clear all syntax items
" For version 6.x: Quit when a syntax file was already loaded
if version < 600
  syntax clear
elseif exists("b:current_syntax")
  finish
endif

if version >= 600
 setlocal iskeyword=39,48-57,A-Z,a-z,_,?
else
 set iskeyword=39,48-57,A-Z,a-z,_,?
endif

syn cluster yetiExp contains=yetiOperator,yetiStat,yetiFunc,yetiGroup,yetiKW,yetiList,yetiString,yetiChar,yetiNumber,yetiLineComment,yetiComment
syn region yetiGroup contained start="(" end=")" contains=@yetiExp
syn match yetiOperator ?[,:;={}\[\]~!+\-*/%<>]\+\|`[a-zA-Z_?]\+`?
syn match yetiConst /\[\s*\]/
syn region yetiEmbedded contained matchgroup=Delimiter start="\\(" matchgroup=Delimiter end=")" contains=@yetiExp

" Standard Rattrap Keywords
syn keyword yetiOperator not and or in is unsafely_as as or div loop shl shr
syn keyword yetiOperator classOf instanceof
syn keyword yetiStat if then elif else fi for forHash synchronized
syn keyword yetiStat ignore module program try catch finally yrt new throw
syn keyword yetiStat do done case of esac
syn keyword yetiAnyVar _
syn keyword yetiBoolean false true none
syn keyword yetiType var norec get set
syn keyword yetiFunc array filter fold id map mapHash number head reverse tail
syn keyword yetiFunc any all find index const at setHashDefault flip sum
syn keyword yetiFunc nullptr? empty? min max maybe abs push exit shift
syn keyword yetiFunc defined? wrapArray concat concatMap negate splitBy
syn keyword yetiFunc ln exp cos sin tan acos asin atan sqrt strReplace
syn keyword yetiFunc strSplit substAll strLength strUpper strLower strTrim
syn keyword yetiFunc strSlice strRight strStarts strEnds strIndexOf
syn keyword yetiFunc strLastIndexOf strLeft strLeftOf strRightOf length
syn keyword yetiFunc drop sort sortBy same? revAppend asList pop swapAt
syn keyword yetiFunc setArrayCapacity catSome map2 withHandle openInFile
syn keyword yetiFunc openOutFile readFile writeFile getLines putLines
syn keyword yetiFunc getContents iterate take splitAt strJoin strPad like
syn keyword yetiFunc delete keys matchAll asString
syn keyword yetiExternal load import

syn match yetiId /\<\(\l\|_\)\(\w\|'\)*\>/
syn match yetiConst /\u\(\w\|'\)*\>/

"syn keyword yetiErr done esac of

"syn region yetiGroup matchgroup=yetiStat start="\<do\>" matchgroup=yetiStat end="\<done\>" contains=@yetiExp
"syn region yetiGroup matchgroup=yetiStat start="\<case\>" matchgroup=yetiStat end="\<esac\>" contains=@yetiExp,yetiOf
"syn keyword yetiOf contained of

syn case ignore
syn keyword yetiTodo contained TODO XXX FIXME
syn case match

" Strings
syn region yetiString start=+"+ skip=+\\\\\|\\`\|\\"+ end=+["\n]+ contains=yetiEmbedded
syn region yetiString start=+'+ end=+'+

" Numbers: supporting integers and floating point numbers
syn match yetiNumber "-\=\<[+-]\?\d*\.\?\d\+\([eE]\d*\)\?\>"

" Comments
syn match yetiLineComment "//.*$" contains=yetiTodo
syn region yetiComment start="/\*" end="\*/" contains=yetiTodo,yetiComment

" synchronization
syn sync lines=100

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

  HiLink yetiLineComment	Comment
  HiLink yetiComment		Comment
  HiLink yetiNumber		Number
  HiLink yetiString		String
  HiLink yetiChar		Character
  HiLink yetiTodo		Todo
  HiLink yetiOperator		Operator
  HiLink yetiStat		Statement
  HiLink yetiOf			Statement
  HiLink yetiAccess		Statement
  HiLink yetiExceptions		Exception
  HiLink yetiFunc		Function
  HiLink yetiConst		Constant
  HiLink yetiBoolean		Boolean
  HiLink yetiRepeat		Repeat
  HiLink yetiStruct		Structure
  HiLink yetiStorageClass	StorageClass
  HiLink yetiType		Type
  HiLink yetiExternal		Include
  HiLink yetiAnyVar             Keyword
  HiLink yetiErr                Error

  delcommand HiLink
endif

let b:current_syntax = "yeti"

" vim: ts=8 nowrap
