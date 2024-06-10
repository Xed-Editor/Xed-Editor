'use strict';

var define = require('define-properties');
var RequireObjectCoercible = require('es-object-atoms/RequireObjectCoercible');
var callBind = require('call-bind');
var callBound = require('call-bind/callBound');

var implementation = require('./implementation');
var getPolyfill = require('./polyfill');
var shim = require('./shim');

var $slice = callBound('Array.prototype.slice');

var polyfill = callBind.apply(getPolyfill());

var boundShim = function findIndex(array, predicate) { // eslint-disable-line no-unused-vars
	RequireObjectCoercible(array);
	var args = $slice(arguments, 1);
	return polyfill(array, args);
};

define(boundShim, {
	getPolyfill: getPolyfill,
	implementation: implementation,
	shim: shim
});

module.exports = boundShim;
