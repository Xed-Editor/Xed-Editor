-- Copyright (c) 2018. tangzx(love.tangzx@qq.com)
--
-- Licensed under the Apache License, Version 2.0 (the "License"); you may not
-- use this file except in compliance with the License. You may obtain a copy of
-- the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
-- WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
-- License for the specific language governing permissions and limitations under
-- the License.

---
--- The type *string* represents immutable sequences of bytes. Lua is 8-bit
--- clean: strings can contain any 8-bit value, including embedded zeros
--- ('`\0`'). Lua is also encoding-agnostic; it makes no assumptions about
--- the contents of a string.
---@class string
string = {}

---
--- Returns the internal numerical codes of the characters `s[i]`, `s[i+1]`,
--- ..., `s[j]`. The default value for `i` is 1; the default value for `j`
--- is `i`. These indices are corrected following the same rules of function
--- `string.sub`.
---
--- Note that numerical codes are not necessarily portable across platforms.
---@overload fun(s:string):number
---@param s string
---@param i number
---@param j number
---@return number
function string.byte(s, i, j) end

---
--- Receives zero or more integers. Returns a string with length equal to
--- the number of arguments, in which each character has the internal numerical
--- code equal to its corresponding argument.
---
--- Note that numerical codes are not necessarily portable across platforms.
---@return string
function string.char(...) end

---
--- Returns a string containing a binary representation (*a binary chunk*) of
--- the given function, so that a later `load` on this string returns a
--- copy of the function (but with new upvalues). If strip is a true value, the
--- binary representation may not include all debug information about the
--- function, to save space.
---
--- Functions with upvalues have only their number of upvalues saved. When (re)
--- loaded, those upvalues receive fresh instances containing **nil**. (You can
--- use the debug library to serialize and reload the upvalues of a function in
--- a way adequate to your needs.)
---@overload fun(func:fun()):string
---@param func fun()
---@param strip boolean
---@return string
function string.dump(func, strip) end

---
--- Looks for the first match of `pattern` in the string `s`. If it finds a
--- match, then `find` returns the indices of `s` where this occurrence starts
--- and ends; otherwise, it returns **nil**. A third, optional numerical
--- argument `init` specifies where to start the search; its default value is 1
--- and can be negative. A value of **true** as a fourth, optional argument
--- `plain` turns off the pattern matching facilities, so the function does a
--- plain "find substring" operation, with no characters in `pattern` being
--- considered "magic". Note that if `plain` is given, then `init` must be given
--- as well.
---
--- If the pattern has captures, then in a successful match the captured values
--- are also returned, after the two indices.
---@overload fun(s:string, pattern:string):number, number, string
---@param s string
---@param pattern string
---@param init number
---@param plain boolean
---@return number, number, string
function string.find(s, pattern, init, plain) end

---
--- Returns a formatted version of its variable number of arguments following
--- the description given in its first argument (which must be a string). The
--- format string follows the same rules as the ISO C function `sprintf`. The
--- only differences are that the options/modifiers `*`, `h`, `L`, `l`, `n`, and
--- `p` are not supported and that there is an extra option, `q`.
---
--- The `q` option formats booleans, nil, numbers, and strings in a way that the
--- result is a valid constant in Lua source code. Booleans and nil are written
--- in the obvious way (`true`, `false`, `nil`). Floats are written in
--- hexadecimal, to preserve full precision. A string is written between double
--- quotes, using escape sequences when necessary to ensure that it can safely
--- be read back by the Lua interpreter. For instance, the call
---
--- string.format('%q', 'a string with "quotes" and \n new line') may produce
--- the string:
---
--- > "a string with \"quotes\" and \
--- > new line"
---
--- The options `A`, `a`, `E`, `e`, `f`, `g`, `G` and `g` all expect a number as
--- argument. Options `c`, `d`, `i`, `o`, `u`, `X`, and `x` expect an integer.
--- When Lua is compiled with a C89 compiler, options `A` and `a` (hexadecimal
--- floats) do not support any modifier (flags, width, length).
---
--- Option `s` expects a string; if its argument is not a string, it is
--- converted to one following the same rules of `tostring`. If the option
--- has any modifier (flags, width, length), the string argument should not
--- contain embedded zeros.
---@param formatstring string
---@return string
function string.format(formatstring, ...) end

---
--- Returns an iterator function that, each time it is called, returns the
--- next captures from `pattern` over the string `s`. If `pattern` specifies no
--- captures, then the whole match is produced in each call.
---
--- As an example, the following loop will iterate over all the words from
--- string `s`, printing one per line:
---
--- `s = "hello world from Lua"`
--- `for w in string.gmatch(s, "%a+") do`
---  > `print(w)`
--- `end`
---
--- The next example collects all pairs `key=value` from the given string into a
--- table:
---
--- `t = {}`
---  s = "from=world, to=Lua"`
--- `for k, v in string.gmatch(s, "(%w+)=(%w+)") do`
---  > `t[k] = v`
--- `end`
---
--- For this function, a caret '`^`' at the start of a pattern does not work as
--- an anchor, as this would prevent the iteration.
---@param s string
---@param pattern string
---@return fun():string, table
function string.gmatch(s, pattern) end

---
--- Returns a copy of `s` in which all (or the first `n`, if given)
--- occurrences of the `pattern` have been replaced by a replacement string
--- specified by `repl`, which can be a string, a table, or a function. `gsub`
--- also returns, as its second value, the total number of matches that
--- occurred.
---
--- If `repl` is a string, then its value is used for replacement. The character
--- `%` works as an escape character: any sequence in `repl` of the form `%n`,
--- with *n* between 1 and 9, stands for the value of the *n*-th captured
--- substring (see below). The sequence `%0` stands for the whole match. The
--- sequence `%%` stands for a single `%`.
---
--- If `repl` is a table, then the table is queried for every match, using
--- the first capture as the key; if the pattern specifies no captures, then
--- the whole match is used as the key.
---
--- If `repl` is a function, then this function is called every time a match
--- occurs, with all captured substrings passed as arguments, in order; if
--- the pattern specifies no captures, then the whole match is passed as a
--- sole argument.
---
--- If the value returned by the table query or by the function call is a
--- string or a number, then it is used as the replacement string; otherwise,
--- if it is false or nil, then there is no replacement (that is, the original
--- match is kept in the string).
---
--- Here are some examples:
--- `x = string.gsub("hello world", "(%w+)", "%1 %1")`
--- `-- > x="hello hello world world"`
--- `x = string.gsub("hello world", "%w+", "%0 %0", 1)`
--- `-- > x="hello hello world"`
--- `x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1")`
--- `-- > x="world hello Lua from"`
--- `x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv)`
--- `-- > x="home = /home/roberto, user = roberto"`
--- `x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s)`
---  >> return loadstring(s)()
---  > end)
--- `-- > x="4+5 = 9"`
--- `local t = {name="lua", version="5.3"}`
--- `x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t)`
--- > x="lua-5.3.tar.gz"
---@overload fun(s:string, pattern:string, repl:string|fun()):string, number
---@param s string
---@param pattern string
---@param repl string|fun()
---@param n number
---@return string, number
function string.gsub(s, pattern, repl, n) end

---
--- Receives a string and returns its length. The empty string `""` has
--- length 0. Embedded zeros are counted, so `"a\000bc\000"` has length 5.
---@param s string
---@return number
function string.len(s) end

---
--- Receives a string and returns a copy of this string with all uppercase
--- letters changed to lowercase. All other characters are left unchanged. The
--- definition of what an uppercase letter is depends on the current locale.
---@param s string
---@return string
function string.lower(s) end

---
--- Looks for the first *match* of `pattern` in the string `s`. If it
--- finds one, then `match` returns the captures from the pattern; otherwise
--- it returns **nil**. If `pattern` specifies no captures, then the whole match
--- is returned. A third, optional numerical argument `init` specifies where
--- to start the search; its default value is 1 and can be negative.
---@overload fun(s:string, pattern:string):any
---@param s string
---@param pattern string
---@param init number
---@return any
function string.match(s, pattern, init) end

---
--- Returns a binary string containing the values `v1`, `v2`, etc. packed (that
--- is, serialized in binary form) according to the format string `fmt`.
---@param fmt string
---@param v1 string
---@param v2 string
---@return string
function string.pack(fmt, v1, v2, ...) end

---
--- Returns the size of a string resulting from `string.pack` with the given
--- format. The format string cannot have the variable-length options '`s`' or
--- '`z`'
---@param fmt string
---@return number
function string.packsize(fmt) end

---
--- Returns a string that is the concatenation of `n` copies of the string
--- `s` separated by the string `sep`. The default value for `sep` is the empty
--- string (that is, no separator). Returns the empty string if n is not
--- positive.
---
--- Note that it is very easy to exhaust the memory of your machine with a
--- single call to this function.
---@overload fun(s:string, n:number):string
---@param s string
---@param n number
---@param sep string
---@return string
function string.rep(s, n, sep) end

---
--- Returns a string that is the string `s` reversed.
---@param s string
---@return string
function string.reverse(s) end

---
--- Returns the substring of `s` that starts at `i` and continues until
--- `j`; `i` and `j` can be negative. If `j` is absent, then it is assumed to
--- be equal to -1 (which is the same as the string length). In particular,
--- the call `string.sub(s,1,j)` returns a prefix of `s` with length `j`, and
--- `string.sub(s, -i)` (for a positive i) returns a suffix of `s` with length
--- `i`.
---
--- If, after the translation of negative indices, `i` is less than 1, it is
--- corrected to 1. If `j` is greater than the string length, it is corrected to
--- that length. If, after these corrections, `i` is greater than `j`, the
--- function returns the empty string.
---@overload fun(s:string, i:number):string
---@param s string
---@param i number
---@param j number
---@return string
function string.sub(s, i, j) end

---
--- Returns the values packed in string `s` according to the format string
--- `fmt`. An optional `pos` marks where to start reading in `s` (default is 1).
--- After the read values, this function also returns the index of the first
--- unread byte in `s`.
---@overload fun(fmt:string, s:string):string
---@param fmt string
---@param s string
---@param pos number
---@return string
function string.unpack(fmt, s, pos) end

---
--- Receives a string and returns a copy of this string with all lowercase
--- letters changed to uppercase. All other characters are left unchanged. The
--- definition of what a lowercase letter is depends on the current locale.
---@param s string
---@return string
function string.upper(s) end
