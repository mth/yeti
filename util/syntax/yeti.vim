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
syn match yetiError "}\|\]\|\*/"
syn match yetiError "\<\(done\|esac\|yrt\|then\|elif\|fi\|else\|of\|catch\|finally\)\>"
syn match yetiParenErr ")"


" Enclosing delimiters
syn region yetiParens start="(" end=")" contains=TOP,yetiParenErr
syn region yetiEncl matchgroup=yetiOperator start="{" matchgroup=yetiOperator end="}" contains=TOP
syn region yetiEncl matchgroup=yetiOperator start="\[" matchgroup=yetiOperator end="\]" contains=TOP
syn match yetiOperator "\[:]"

syn region yetiIf matchgroup=yetiConditional start="\<if\>" matchgroup=yetiConditional end="\<\(fi\>\|else:\)" contains=TOP

syn keyword yetiConditional then elif else containedin=yetiIf contained

syn region yetiDo matchgroup=yetiStatement start="\<do\>" matchgroup=yetiStatement end="\<done\>" contains=TOP

syn region yetiCase matchgroup=yetiConditional start="\<case\>" matchgroup=yetiConditional end="\<esac\>" contains=TOP

syn keyword yetiConditional of containedin=yetiCase contained

syn region yetiTry matchgroup=yetiException start="\<try\>" matchgroup=yetiException end="\<yrt\>" contains=TOP

syn keyword yetiException catch containedin=yetiTry contained skipempty skipwhite nextgroup=yetiClassName
syn keyword yetiException finally containedin=yetiTry contained
syn keyword yetiOperator throw with

syn keyword yetiRepeat for forHash forJavaMap loop withExit
syn keyword yetiStatement module program synchronized

syn keyword yetiStorageClass var norec get set

syn keyword yetiAnyVar _
syn keyword yetiBoolean false true undef_bool
syn keyword yetiConstant none undef_str utf8
syn keyword yetiFunction array filter fold id mapHash number head reverse tail
syn keyword yetiFunction any all find index const at on setHashDefault flip sum
syn keyword yetiFunction nullptr? empty? min max maybe abs push sysExit shift
syn keyword yetiFunction defined? wrapArray concat concatMap negate splitBy
syn keyword yetiFunction ln exp cos sin tan acos asin atan sqrt strReplace
syn keyword yetiFunction strSplit substAll strLength strUpper strLower strTrim
syn keyword yetiFunction strSlice strRight strStarts? strEnds? strIndexOf
syn keyword yetiFunction strLastIndexOf strLeft strLeftOf strRightOf length
syn keyword yetiFunction drop sort sortBy same? revAppend list pop swapAt
syn keyword yetiFunction setArrayCapacity catSome map2 withHandle openInFile
syn keyword yetiFunction openOutFile readFile writeFile getLines putLines
syn keyword yetiFunction getContents iterate take splitAt strJoin strPad like
syn keyword yetiFunction delete keys matchAll string apply clearHash strChar
syn keyword yetiFunction failWith lazy int map map' takeWhile collect pair nub
syn keyword yetiFunction strLastIndexOf' copy slice deleteAll hash avoid
syn keyword yetiFunction groupBy insertHash strCapitalize strUncapitalize
syn keyword yetiFunction identityHash deleteFile binReadFile binWriteFile
syn keyword yetiFunction binReadAll trace runThread threadLocal peekObject
syn keyword yetiFunction listDirectory clearArray mapJavaList createPath
syn keyword yetiExternal load deprecated
syn keyword yetiExternal import skipwhite skipempty nextgroup=yetiImport
syn match yetiFunction "\<contains?\>"

syn keyword yetiOperator not and or in or div shl shr b_and b_or xor
syn keyword yetiOperator classOf instanceof skipempty skipwhite nextgroup=yetiClassName
syn match yetiOperator #[:;,=~!+\\\-*%<>^]\+#
syn match yetiOperator #`[a-zA-Z_?]\+`#
syn match yetiOperator #/[^/*]\@=#
syn match yetiOperator #\.\.\.\||>#

syn match yetiConstant "(\s*)"
syn match yetiConstant "\[\s*\]"
syn match yetiConstructor "\<\u\(\w\|'\)*\>"

syn match yetiStringErr "\\." contained
syn region yetiEmbedded contained matchgroup=Delimiter start="\\(" matchgroup=Delimiter end=")" contains=TOP,yetiParenErr
syn match yetiSpecial "\\\([abefnrt0\'"]\|u\x\{4}\)" contained
syn region yetiString start=+"""+ end=+"""+ contains=yetiEmbedded,yetiSpecial,yetiStringErr
syn region yetiString start=+"\(""\)\@!+ end=+"\|\\[ \t\r]*\n+ contains=yetiEmbedded,yetiSpecial,yetiStringErr
syn region yetiString start=+\<'+ skip=+''+ end=+'+

" Numbers: supporting integers and floating point numbers
syn match yetiNumber "-\=\<[+-]\?\d*\.\?\d\+\([eE]\d*\)\?\>"

syn match yetiMemberOp "\(\<\u\(\w\|\$\)*\_\s*\)\?#\_\s*\w\+\_\s*\(()\)\?"

" Classes
syn keyword yetiClass class skipempty skipwhite nextgroup=yetiClassDef
syn region yetiClassDef matchgroup=yetiClassDef start="\w\+\>" matchgroup=yetiType end="\<end\>" keepend contains=yetiExtends,yetiClassType,yetiClassMod,yetiMethodArgs,yetiFieldDef,yetiComment,yetiAnyVar extend contained
syn keyword yetiClassType void boolean byte short int long float double number contained
syn keyword yetiClassMod var static abstract contained
syn keyword yetiExtends extends contained skipempty skipwhite nextgroup=yetiExtendClass
syn match yetiExtendClass "[A-Za-z_$]\+" contained skipempty skipwhite nextgroup=yetiParens
syn region yetiMethodArgs matchgroup=yetiClassDef start="\w\+\s*(" end=")\@=" nextgroup=yetiMethodDef contains=yetiComment,yetiClassType contained
syn region yetiMethodDef matchgroup=yetiClassDef start=")" end=",\|\<end\>" contains=TOP contained
syn region yetiFieldDef matchgroup=yetiOperator start="=" matchgroup=yetiClassDef end=",\|\<end\>" contains=TOP contained

" Yeti type definition syntax
syn keyword yetiTypeMod shared contained
syn region yetiTypeBind matchgroup=yetiTypeDef start="\<typedef\>" end="=" skipempty skipwhite nextgroup=@yetiTypeDecls contains=yetiTypeMod
syn keyword yetiType is skipempty skipwhite nextgroup=@yetiTypeDecls
syn keyword yetiCast as unsafely_as skipempty skipwhite nextgroup=@yetiTypeDecls
"syn match yetiTypeDecl contained /\(\l\|_\)\(\w\|'\)*/
syn cluster yetiTypeDecls contains=yetiTypeDecl,yetiTypeVar
syn region yetiTypeDecl transparent start="(" end=")" contained contains=@yetiTypeDecls,yetiComment skipempty skipwhite nextgroup=yetiTypeOp
syn region yetiTypeDecl transparent start="{" end="}" contained contains=yetiType,yetiComment skipempty skipwhite nextgroup=yetiTypeOp
syn match yetiTypeDecl "\~\(\w\|\.\|\$\)*\(\[\]\)*" contained skipempty skipwhite nextgroup=yetiTypeOp
syn match yetiTypeDecl "\l\(\w\|'\|?\)*" contained skipempty skipwhite nextgroup=yetiTypeOp
syn match yetiTypeVar "['^]\(\w\|'\)*\(\[\]\)*" contained skipwhite skipempty nextgroup=yetiTypeOp
syn match yetiTypeDecl "\<\u\(\w\|'\)*\>" contained skipwhite skipempty nextgroup=@yetiTypeDecls
syn match yetiTypeDecl "()" contained skipwhite skipempty nextgroup=yetiTypeOp
syn match yetiTypeOp "--\?>\||" contained skipwhite skipempty nextgroup=@yetiTypeDecls
syn region yetiTypeOp matchgroup=yetiTypeDelimiter start="<" matchgroup=yetiTypeDelimiter end=">" contained contains=@yetiTypeDecls,yetiComment skipempty skipwhite nextgroup=yetiTypeOp

syn match yetiClassName "[A-Za-z]\(\w\|\.\|\$\)*\(\[\]\)*\(()\)\?" contained
syn match yetiImport "[A-Za-z]\(\w\|\.\|\$\)*" contained skipwhite skipempty nextgroup=yetiImportMany
syn region yetiImportMany matchgroup=yetiOperator start=":" matchgroup=yetiOperator end=";" contained
syn keyword yetiOperator new skipempty skipwhite nextgroup=yetiClassName

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

  HiLink yetiClassMod	yetiStorageClass
  HiLink yetiTypeDecl	yetiType
  HiLink yetiTypeOp	yetiType
  HiLink yetiCast	yetiOperator
  HiLink yetiParenErr	yetiError
  HiLink yetiStringErr	yetiError

  HiLink yetiError	Error

  HiLink yetiComment 	Comment

  HiLink yetiExternal	Include
  HiLink yetiFunction	Function
 
  " these statements hilight actually keywords - and since vim thinks
  " keyword is kind of statement it is more accurate to link this to Keyword
  HiLink yetiStatement	Keyword

  HiLink yetiConstant	Constant
  HiLink yetiConstructor Identifier

  HiLink yetiOperator	Operator
  HiLink yetiAnyVar	Keyword

  HiLink yetiBoolean	Boolean
  HiLink yetiNumber	Number
  HiLink yetiString	String
  HiLink yetiException	Exception
  HiLink yetiRepeat	Repeat
  HiLink yetiConditional Conditional

  HiLink yetiStorageClass StorageClass
  HiLink yetiTypeDelimiter Delimiter
  HiLink yetiType	Type
  HiLink yetiTypeDef	TypeDef
  HiLink yetiTypeMod	yetiTypeDef
  HiLink yetiClassType	Type
  HiLink yetiClass	Structure
  HiLink yetiExtends	Structure

  HiLink yetiTodo	Todo
  HiLink yetiSpecial	SpecialChar

  delcommand HiLink
endif

let b:current_syntax = "yeti"

" vim: ts=8
