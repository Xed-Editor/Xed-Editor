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

package = {}

---
--- A string describing some compile-time configurations for packages. This
--- string is a sequence of lines:
---
--- The first line is the directory separator string. Default is '\' for Windows
--- and '/' for all other systems.
--- The second line is the character that separates templates in a path. Default
--- is ';'.
--- The third line is the string that marks the substitution points in a
--- template. Default is '?'.
--- The fourth line is a string that, in a path in Windows, is replaced by the
--- executable's directory. Default is '!'.
--- The fifth line is a mark to ignore all text after it when building the
--- luaopen_ function name. Default is '-'.
package.config = ""

---
--- The path used by `require` to search for a C loader.
---
--- Lua initializes the C path `package.cpath` in the same way it initializes
--- the Lua path `package.path`, using the environment variable `LUA_CPATH_5_4`
--- or the environment variable `LUA_CPATH`, or a default path defined in
--- `luaconf.h`.
package.cpath = ""


---
--- A table used by `require` to control which modules are already
--- loaded. When you require a module `modname` and `package.loaded[modname]``
--- is not false, `require` simply returns the value stored there.
---
--- This variable is only a reference to the real table; assignments to this
--- variable do not change the table used by `require`.
package.loaded = {}

---
--- Dynamically links the host program with the C library `libname`.
---
--- If `funcname` is "*", then it only links with the library, making the
--- symbols exported by the library available to other dynamically linked
--- libraries. Otherwise, it looks for a function `funcname` inside the library
--- and returns this function as a C function. So, `funcname` must follow the
--- `lua_CFunction` prototype (see `lua_CFunction`).
---
--- This is a low-level function. It completely bypasses the package and module
--- system. Unlike `require`, it does not perform any path searching and does
--- not automatically adds extensions. `libname` must be the complete file name
--- of the C library, including if necessary a path and an extension. `funcname`
--- must be the exact name exported by the C library (which may depend on the C
--- compiler and linker used).
---
--- This function is not supported by Standard C. As such, it is only available
--- on some platforms (Windows, Linux, Mac OS X, Solaris, BSD, plus other Unix
--- systems that support the `dlfcn` standard).
---@param libname string
---@param funcname string
---@return fun():nil
function package.loadlib(libname, funcname) end

---
--- The path used by `require` to search for a Lua loader.
---
--- At start-up, Lua initializes this variable with the value of the environment
--- variable `LUA_PATH_5_4` or the environment variable `LUA_PATH` or with a
--- default path defined in `luaconf.h`, if those environment variables are not
--- defined. Any ";;" in the value of the environment variable is replaced by
--- the default path.
package.path = ""

---
--- A table to store loaders for specific modules (see `require`).
---
--- This variable is only a reference to the real table; assignments to this
--- variable do not change the table used by `require`.
package.preload = {}

---
--- A table used by require to control how to load modules.
---
--- Each entry in this table is a *searcher function*. When looking for a
--- module, *require* calls each of these searchers in ascending order, with the
--- module name (the argument given to `require`) as its sole parameter. The
--- function can return another function (the module *loader*) plus an extra
--- value that will be passed to that loader, or a string explaining why it did
--- not find that module (or **nil** if it has nothing to say).
---
--- Lua initializes this table with four searcher functions.
---
--- The first searcher simply looks for a loader in the `package.preload` table.
---
--- The second searcher looks for a loader as a Lua library, using the path
--- stored at `package.path`. The search is done as described in function
--- `package.searchpath`.
---
--- The third searcher looks for a loader as a C library, using the path given
--- by the variable  package.cpath`. Again, the search is done as described in
--- function `package.searchpath`. For instance, if the C path is the string
--- > "`./?.so;./?.dll;/usr/local/?/init.so`"
--- the searcher for module foo will try to open the files ``./foo.so, ./foo
--- .dll`, and ``/usr/local/foo/init.so`, in that order. Once it finds a C
--- library, this searcher first uses a dynamic link facility to link the
--- application with the library. Then it tries to find a C function inside the
--- library to be used as the loader. The name of this C function is the string
--- "`luaopen_`" concatenated with a copy of the module name where each dot is
--- replaced by an underscore. Moreover, if the module name has a hyphen, its
--- suffix after (and including) the first hyphen is removed. For instance, if
--- the module name is `a.b.c-v2.1`, the function name will be `luaopen_a_b_c`.
---
--- The fourth searcher tries an *all-in-one loader*. It searches the C path for
--- a library for the root name of the given module. For instance, when
--- requiring `a.b.c`, it will search for a C library for `a`. If found, it
--- looks into it for an open function for the submodule; in our example, that
--- would be `luaopen_a_b_c`. With this facility, a package can pack several C
--- submodules into one single library, with each submodule keeping its original
--- open function.
---
--- All searchers except the first one (preload) return as the extra value the
--- file name where the module was found, as returned by `package.searchpath`.
--- The first searcher returns no extra value.
package.searchers = {}

---
--- Searches for the given name in the given path.
---
--- A path is a string containing a sequence of *templates* separated by
--- semicolons. For each template, the function replaces each interrogation mark
--- (if any) in the template with a copy of name wherein all occurrences of
--- `sep` (a dot, by default) were replaced by `rep` (the system's directory
--- separator, by default), and then tries to open the resulting file name.
---
--- For instance, if the path is the string
--- > "`./?.lua;./?.lc;/usr/local/?/init.lua`"
--- the search for the name `foo.a` will try to open the files `./foo/a.lua`,
--- `./foo/a.lc`, and `/usr/local/foo/a/init.lua`, in that order.
---
--- Returns the resulting name of the first file that it can open in read mode
--- (after closing the file), or **nil** plus an error message if none succeeds.
--- (This error message lists all file names it tried to open.)
---@overload fun(name:string, path:string):string
---@param name string
---@param path string
---@param sep string
---@param rep string
---@return string
function package.searchpath(name, path, sep, rep) end

return package
