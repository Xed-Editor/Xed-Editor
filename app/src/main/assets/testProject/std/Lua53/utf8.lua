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

utf8 = {}

---
--- Receives zero or more integers, converts each one to its corresponding
--- UTF-8 byte sequence and returns a string with the concatenation of all
--- these sequences.
---@return string
function utf8.char(...) end

---
--- The pattern (a string, not a function) "`[\0-\x7F\xC2-\xF4][\x80-\xBF]*`",
--- which matches exactly one UTF-8 byte sequence, assuming that the subject
--- is a valid UTF-8 string.
---@type string
utf8.charpattern = ""

---
--- Returns values so that the construction
--- > `for p, c in utf8.codes(s) do` *body* `end`
--- will iterate over all characters in string `s`, with `p` being the position
--- (in bytes) and `c` the code point of each character. It raises an error if
--- it meets any invalid byte sequence.
---@param s string
---@return string
function utf8.codes(s) end

---
--- Returns the codepoints (as integers) from all characters in `s` that start
--- between byte position `i` and `j` (both included). The default for `i` is
--- 1  and for `j` is `i`. It raises an error if it meets any invalid byte
--- sequence.
---@overload fun(s:string):number
---@param s string
---@param i number
---@param j number
---@return number
function utf8.codepoint (s, i, j) end

---
--- Returns the number of UTF-8 characters in string `s` that start between
--- positions `i` and `j` (both inclusive). The default for `i` is 1 and for
--- `j` is -1. If it finds any invalid byte sequence, returns a false value
--- plus the position of the first invalid byte.
---@overload fun(s:string):number
---@param s string
---@param i number
---@param j number
---@return number
function utf8.len(s, i, j) end

---
--- Returns the position (in bytes) where the encoding of the `n`-th character
--- of `s` (counting from position `i`) starts. A negative `n` gets
--- characters before position `i`. The default for `i` is 1 when `n` is
--- non-negative and `#s + 1` otherwise, so that `utf8.offset(s, -n)` gets the
--- offset of the `n`-th character from the end of the string. If the
--- specified character is neither in the subject nor right after its end,
--- the function returns nil. As a special case, when `n` is 0 the function
--- returns the start of the encoding of the character that contains the `i`-th
--- byte of `s`.
---
--- This function assumes that `s` is a valid UTF-8 string.
---@overload fun(s:string):number
---@param s string
---@param n number
---@param i number
---@return number
function utf8.offset (s, n, i) end
