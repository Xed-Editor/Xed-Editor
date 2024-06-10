'use strict';

var define = require('define-properties');
var shimUnscopables = require('es-shim-unscopables');

var getPolyfill = require('./polyfill');

module.exports = function shimFindIndex() {
	var polyfill = getPolyfill();

	define(
		Array.prototype,
		{ findIndex: polyfill },
		{
			findIndex: function () {
				return Array.prototype.findIndex !== polyfill;
			}
		}
	);

	shimUnscopables('findIndex');

	return polyfill;
};
