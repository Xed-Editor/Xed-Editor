# ES6 `Array.prototype.findIndex` polyfill

Simple ES6 [Array.prototype.findIndex](http://people.mozilla.org/%7Ejorendorff/es6-draft.html#sec-array.prototype.findindex) polyfill for older environments taken from [es6-shim](https://github.com/paulmillr/es6-shim).

For browsers and node.js.

## Installation
* Just include repo before your scripts.
* `npm install array.prototype.findindex` if you’re using node.js.
* `component install paulmillr/Array.prototype.findIndex` if you’re using [component(1)](https://github.com/component/component).
* `bower install Array.prototype.findIndex` if you’re using [Twitter Bower](http://bower.io).


## Usage

* `Array.prototype.findIndex(predicate[, thisArg])` returns first item index that matches `predicate` function.
* `predicate(value, index, collection)`: takes three arguments
    * `value`: current collection element
    * `index`: current collection element index
    * `collection`: the collection

```javascript
var findIndex = require('array.prototype.findindex');

findIndex.shim(); // if you want to install it on the global environment
```

Code example:

```javascript
// Default:
[1, 5, 10, 15].findIndex(function(a) {return a > 9;}) // 2
```

## License

The MIT License (MIT)

Copyright (c) 2013 Paul Miller <http://paulmillr.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the “Software”), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
