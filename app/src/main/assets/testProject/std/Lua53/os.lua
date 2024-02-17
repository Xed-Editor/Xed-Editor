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

os = {}

---
--- Returns an approximation of the amount in seconds of CPU time used by
--- the program.
---@return number
function os.clock() end

---
--- Returns a string or a table containing date and time, formatted according
--- to the given string `format`.
---
--- If the `time` argument is present, this is the time to be formatted (see
--- the `os.time` function for a description of this value). Otherwise,
--- `date` formats the current time.
---
--- If `format` starts with '`!`', then the date is formatted in Coordinated
--- Universal Time. After this optional character, if `format` is the string
--- "`*t`", then `date` returns a table with the following fields:
---
--- **`year`** (four digits)
--- **`month`** (1–12)
--- **`day`** (1-31)
--- **`hour`** (0-23)
--- **`min`** (0-59)
--- **`sec`** (0-61), due to leap seconds
--- **`wday`** (weekday, 1–7, Sunday is 1)
--- **`yday`** (day of the year, 1–366)
--- **`isdst`** (daylight saving flag, a boolean). This last field may be absent
--- if the information is not available.
---
--- If `format` is not "`*t`", then `date` returns the date as a string,
--- formatted according to the same rules as the ISO C function `strftime`.
---
--- When called without arguments, `date` returns a reasonable date and time
--- representation that depends on the host system and on the current locale.
--- (More specifically, `os.date()` is equivalent to `os.date("%c")`.)
---
--- On non-POSIX systems, this function may be not thread safe because of its
--- reliance on C function `gmtime` and C function `localtime`.
---@overload fun():string|table
---@param format string
---@param time number
---@return string|table
function os.date(format, time) end

---
--- Returns the difference, in seconds, from time `t1` to time `t2`. (where the
--- times are values returned by `os.time`). In POSIX, Windows, and some other
--- systems, this value is exactly `t2`-`t1`.
---@param t2 number
---@param t1 number
---@return number
function os.difftime(t2, t1) end

---
--- This function is equivalent to the C function `system`. It passes `command`
--- to be executed by an operating system shell. Its first result is **true** if
--- the command terminated successfully, or **nil** otherwise. After this first
--- result the function returns a string plus a number, as follows:
---
--- **"exit"**: the command terminated normally; the following number is the
--- exit status of the command.
--- **"signal"**: the command was terminated by a signal; the following number
--- is the signal that terminated the command.
---
--- When called without a command, `os.execute` returns a boolean that is true
--- if a shell is available.
---@overload fun():string|number
---@param command string
---@return string|number
function os.execute(command) end

---
--- Calls the ISO C function `exit` to terminate the host program. If `code` is
--- **true**, the returned status is `EXIT_SUCCESS`; if `code` is **false**, the
--- returned status is `EXIT_FAILURE`; if `code` is a number, the returned
--- status is this number. The default value for `code` is **true**.
---
--- If the optional second argument `close` is true, closes the Lua state before
--- exiting.
---@overload fun():number
---@param code number
---@param close boolean
---@return number
function os.exit(code, close) end

---
--- Returns the value of the process environment variable `varname`, or
--- **nil** if the variable is not defined.
---@param varname string
---@return string
function os.getenv(varname) end

---
--- Deletes the file (or empty directory, on POSIX systems) with the given name.
--- If this function fails, it returns **nil**, plus a string describing the
--- error and the error code. Otherwise, it returns true.
---@param filename string
---@return nil|string
function os.remove(filename) end

---
--- Renames the file or directory named `oldname` to `newname`. If this function
--- fails, it returns **nil**, plus a string describing the error and the error
--- code. Otherwise, it returns true.
---@param oldname string
---@param newname string
---@return nil|string
function os.rename(oldname, newname) end

---
--- Sets the current locale of the program. `locale` is a system-dependent
--- string specifying a locale; `category` is an optional string describing
--- which category to change: `"all"`, `"collate"`, `"ctype"`, `"monetary"`,
--- `"numeric"`, or `"time"`; the default category is `"all"`. The function
--- returns the name of the new locale, or **nil** if the request cannot be
--- honored.
---
--- If `locale` is the empty string, the current locale is set to an
--- implementation-defined native locale. If `locale` is the string "`C`",
--- the current locale is set to the standard C locale.
---
--- When called with **nil** as the first argument, this function only returns
--- the name of the current locale for the given category.
---
--- This function may be not thread safe because of its reliance on C function
--- `setlocale`.
---@overload fun(locale:string):string|nil
---@param locale string
---@param category string
---@return string|nil
function os.setlocale(locale, category) end

---
--- Returns the current time when called without arguments, or a time
--- representing the date and time specified by the given table. This table
--- must have fields `year`, `month`, and `day`, and may have fields `hour`
--- (default is 12), `min` (default is 0), `sec` (default is 0), and `isdst`
--- (default is **nil**). Other fields are ignored. For a description of these
--- fields, see the `os.date` function.
---
--- When the function is called, the values in these fields do not need to be
--- inside their valid ranges. For instance, if `sec` is -10, it means 10 seconds
--- before the time specified by the other fields; if `hour` is 1000, it means
--- 1000 hours after the time specified by the other fields.
---
--- The returned value is a number, whose meaning depends on your system. In
--- POSIX, Windows, and some other systems, this number counts the number of
--- seconds since some given start time (the "epoch"). In other systems, the
--- meaning is not specified, and the number returned by `time` can be used only
--- as an argument to `os.date` and `os.difftime`.
---
--- When called with a table, `os.time` also normalizes all the fields
--- documented in the `os.date` function, so that they represent the same time
--- as before the call but with values inside their valid ranges.
---@overload fun():number
---@param table table
---@return number
function os.time(table) end

---
--- Returns a string with a file name that can be used for a temporary
--- file. The file must be explicitly opened before its use and explicitly
--- removed when no longer needed.
---
--- On some systems (POSIX), this function also creates a file with that
--- name, to avoid security risks. (Someone else might create the file with
--- wrong permissions in the time between getting the name and creating the
--- file.) You still have to open the file to use it and to remove it (even
--- if you do not use it).
---
--- When possible, you may prefer to use `io.tmpfile`, which automatically
--- removes the file when the program ends.
---@return string
function os.tmpname() end

return os
