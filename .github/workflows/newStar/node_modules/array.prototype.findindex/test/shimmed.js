'use strict';

require('../auto');

var test = require('tape');
var defineProperties = require('define-properties');
var callBind = require('call-bind');

var isEnumerable = Object.prototype.propertyIsEnumerable;
var functionsHaveNames = require('functions-have-names')();
var hasStrictMode = require('has-strict-mode')();

var runTests = require('./tests');

test('shimmed', function (t) {
	t.equal(Array.prototype.findIndex.length, 1, 'Array#findIndex has a length of 1');
	t.test('Function name', { skip: !functionsHaveNames }, function (st) {
		st.equal(Array.prototype.findIndex.name, 'findIndex', 'Array#findIndex has name "findIndex"');
		st.end();
	});

	t.test('enumerability', { skip: !defineProperties.supportsDescriptors }, function (et) {
		et.equal(false, isEnumerable.call(Array.prototype, 'findIndex'), 'Array#findIndex is not enumerable');
		et.end();
	});

	t.test('bad array/this value', { skip: !hasStrictMode }, function (st) {
		st['throws'](function () { return Array.prototype.findIndex.call(undefined); }, TypeError, 'undefined is not an object');
		st['throws'](function () { return Array.prototype.findIndex.call(null); }, TypeError, 'null is not an object');
		st.end();
	});

	runTests(callBind(Array.prototype.findIndex), t);

	t.end();
});
