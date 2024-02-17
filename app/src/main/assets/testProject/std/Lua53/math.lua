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

math = {}

---
--- Returns the absolute value of `x`. (integer/float)
---@param x number
---@return number
function math.abs(x) return 0 end

---
--- Returns the arc cosine of `x` (in radians).
---@param x number
---@return number
function math.acos(x) return 0 end

---
--- Returns the arc sine of `x` (in radians).
---@param x number
---@return number
function math.asin(x) return 0 end

---
--- Returns the arc tangent of `y/x` (in radians), but uses the signs of both
--- parameters to find the quadrant of the result. (It also handles correctly
--- the case of `x` being zero.)
---
--- The default value for `x` is 1, so that the call `math.atan(y)`` returns the
--- arc tangent of `y`.
---@overload fun(y:number):number
---@param y number
---@param x number
---@return number
function math.atan(y, x) return 0 end

---
--- Returns the smallest integer larger than or equal to `x`.
---@param x number
---@return number
function math.ceil(x) return 0 end

---
--- Returns the cosine of `x` (assumed to be in radians).
---@param x number
---@return number
function math.cos(x) return 0 end

---
--- Converts the angle `x` from radians to degrees.
---@param x number
---@return number
function math.deg(x) return 0 end

---
--- Returns the value *e^x* (where e is the base of natural logarithms).
---@param x number
---@return number
function math.exp(x) end

---
--- Returns the largest integer smaller than or equal to `x`.
---@param x number
---@return number
function math.floor(x) end

---
--- Returns the remainder of the division of `x` by `y` that rounds the
--- quotient towards zero. (integer/float)
---@param x number
---@param y number
---@return number
function math.fmod(x, y) end

---
--- The float value `HUGE_VAL`, a value larger than any other numeric value.
---@type number
math.huge = nil

---
--- Returns the logarithm of `x` in the given base. The default for `base` is
--- *e* (so that the function returns the natural logarithm of `x`).
---@overload fun(x:number):number
---@param x number
---@param base number
---@return number
function math.log(x, base) end

---
--- Returns the argument with the maximum value, according to the Lua operator
--- `<`. (integer/float)
---@param x number
---@return number
function math.max(x, ...) end

---
--- An integer with the maximum value for an integer.
---@type number
math.maxinteger = nil

---
--- Returns the argument with the minimum value, according to the Lua operator
--- `<`. (integer/float)
---@param x number
---@return number
function math.min(x, ...) end

---
--- An integer with the minimum value for an integer.
---@type number
math.mininteger = nil

---
--- Returns the integral part of `x` and the fractional part of `x`. Its second
--- result is always a float.
---@param x number
---@return number
function math.modf(x) end

---
--- The value of π.
math.pi = 3.1415

---
--- Converts the angle `x` from degrees to radians.
---@param x number
---@return number
function math.rad(x) end

---
--- When called without arguments, returns a pseudo-random float with uniform
--- distribution in the range *[0,1)*. When called with two integers `m` and
--- `n`, `math.random` returns a pseudo-random integer with uniform distribution
--- in the range *[m, n]*. The call `math.random(n)` is equivalent to `math
--- .random`(1,n).
---@overload fun():number
---@param m number
---@param n number
---@return number
function math.random(m, n) end

---
--- Sets `x` as the "seed" for the pseudo-random generator: equal seeds
--- produce equal sequences of numbers.
---@param x number
function math.randomseed(x) end

---
--- Returns the sine of `x` (assumed to be in radians).
---@param x number
---@return number
function math.sin(x) return 0 end

---
--- Returns the square root of `x`. (You can also use the expression `x^0.5` to
--- compute this value.)
---@param x number
---@return number
function math.sqrt(x) return 0 end

---
--- Returns the tangent of `x` (assumed to be in radians).
---@param x number
---@return number
function math.tan(x) return 0 end

---
--- If the value `x` is convertible to an integer, returns that integer.
--- Otherwise, returns `nil`.
---@param x number
---@return number
function math.tointeger(x) end

---
--- Returns "`integer`" if `x` is an integer, "`float`" if it is a float, or
--- **nil** if `x` is not a number.
---@param x number
---@return number
function math.type(x) end

---
--- Returns a boolean, true if and only if integer `m` is below integer `n` when
--- they are compared as unsigned integers.
---@param m number
---@param n number
---@return boolean
function math.ult(m, n) end

return math
