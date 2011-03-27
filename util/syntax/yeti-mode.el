;;; yeti-mode.el --- Major mode for editing Yeti files

;; Copyright (C) 2011 Brian McKenna

;; Author: Brian McKenna
;; URL: http://github.com/mth/yeti
;; Created: 2011-03-26
;; Keywords: languages yeti
;; Version: 1.0

;; Provides font-locking for Yeti code.
;;
;; If you're installing manually, you should add this to your .emacs
;; file after putting it on your load path:
;;
;;    (autoload 'yeti-mode "yeti-mode" nil t)
;;    (add-to-list 'auto-mode-alist '("\\.yeti$" . yeti-mode))

;; TODO
;; * Fix escaping when whitespace between \ and "
;; * Add support for """strings"""
;; * Fix f a is ~java.lang.Throwable -> int = 10

;;; Code:

(defconst yeti-font-lock-keywords
  `(,(rx symbol-start
         (or "then" "elif" "else"
	     "of"
	     "catch"
	     "finally"
	     "throw" "with"
	     "for" "forHash" "loop" "withExit"
	     "module" "program" "synchronized"
	     "var" "norec" "get" "set"
	     "load"
	     "import"
	     "not" "and" "or" "in" "div" "shl" "shr" "b_and" "b_or" "xor"
	     "classOf" "instanceof"
	     "var" "static" "abstract"
	     "extends"
	     "is"
	     "as"
	     "new"
	     "do" "done"
	     "if" "fi"
	     "case" "esac"
	     "try" "yrt"
	     "end")
         symbol-end)

    ;; Class definition
    (,(rx symbol-start (group "class") (1+ space) (group (1+ (or word ?_))))
     (1 font-lock-keyword-face) (2 font-lock-type-face))

    ;; Function definition
    (,(rx symbol-start (group (1+ (or word ?_))) (1+ space) (group (1+ (or word ?_ space))) (? (1+ space) "is" (1+ (not-char ?=))) (1+ space) ?=)
     (1 font-lock-function-name-face) (2 font-lock-variable-name-face))

    ;; Method definition
    (,(rx symbol-start (group (1+ (or word ?_))) (1+ space) (group (1+ (or word ?_))) "(")
     (1 font-lock-type-face) (2 font-lock-function-name-face))

    ;; Variable definition
    (,(rx symbol-start (group (1+ (or word ?_ ?'))) (* space) ?=)
     (1 font-lock-variable-name-face))

    ;; Buil-in types
    (,(rx symbol-start
	  (group (or "void" "boolean" "byte" "short" "int" "long" "float" "double" "number"))
	  (1+ space)
	  (group (1+ (or word ?_ ?'))))
     (1 font-lock-type-face) (2 font-lock-variable-name-face))

    ;; Built-ins functions
    (,(rx symbol-start
	  (group
	   (or
	    "array" "filter" "fold" "id" "mapHash" "number" "head" "reverse" "tail"
	    "any" "all" "find" "index" "const" "at" "on" "setHashDefault" "flip" "sum"
	    "nullptr?" "empty?" "min" "max" "maybe" "abs" "push" "sysExit" "shift"
	    "defined?" "wrapArray" "concat" "concatMap" "negate" "splitBy"
	    "ln" "exp" "cos" "sin" "tan" "acos" "asin" "atan" "sqrt" "strReplace"
	    "strSplit" "substAll" "strLength" "strUpper" "strLower" "strTrim"
	    "strSlice" "strRight" "strStarts?" "strEnds?" "strIndexOf"
	    "strLastIndexOf" "strLeft" "strLeftOf" "strRightOf" "length"
	    "drop" "sort" "sortBy" "same?" "revAppend" "list" "pop" "swapAt"
	    "setArrayCapacity" "catSome" "map2" "withHandle" "openInFile"
	    "openOutFile" "readFile" "writeFile" "getLines" "putLines"
	    "getContents" "iterate" "take" "splitAt" "strJoin" "strPad" "like"
	    "delete" "keys" "matchAll" "string" "apply" "clearHash" "strChar"
	    "failWith" "lazy" "int" "map" "map'" "takeWhile" "collect" "pair" "nub"
	    "strLastIndexOf'" "copy" "slice" "deleteAll" "hash" "avoid"
	    "groupBy" "insertHash" "strCapitalize" "strUncapitalize"
	    "identityHash" "deleteFile" "binReadFile" "binWriteFile"
	    "binReadAll" "trace" "runThread" "threadLocal" "peekObject"
	    "listDirectory" "clearArray"))
	  symbol-end)
     (1 font-lock-builtin-face))))

;;;###autoload
(defun yeti-mode ()
  "Major mode for editing Yeti files."
  (interactive)
  (kill-all-local-variables)

  (let ((table (make-syntax-table)))
    (modify-syntax-entry ?\' "\"" table) ; 'c'
    (modify-syntax-entry ?# "_" table) ; Include # in symbols for Java#classes
    (modify-syntax-entry ?/ ". 124b" table) ; // quotes
    (modify-syntax-entry ?\n "> b" table)
    (modify-syntax-entry ?* ". 23" table) ; /* quotes */
    (set-syntax-table table))

  (setq major-mode 'yeti-mode)
  (set (make-local-variable 'font-lock-defaults)
       '((yeti-font-lock-keywords) nil nil))
  (set (make-local-variable 'font-lock-keywords)
       yeti-font-lock-keywords)

  (set (make-local-variable 'comment-start) "//")
  (set (make-local-variable 'comment-end) ""))

;;;###autoload
(add-to-list 'auto-mode-alist (cons (purecopy "\\.yeti\\'") 'yeti-mode))
