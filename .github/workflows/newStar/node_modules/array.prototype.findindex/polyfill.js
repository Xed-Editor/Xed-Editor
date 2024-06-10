'use strict';

var implementation = require('./implementation');

module.exports = function getPolyfill() {
	// Detect if an implementation exists
	// Detect early implementations which skipped holes in sparse arrays
	// eslint-disable-next-line no-sparse-arrays
	var implemented = Array.prototype.findIndex && ([, 1].findIndex(function (item, idx) {
		return idx === 0;
	}) === 0);

	return implemented ? Array.prototype.findIndex : implementation;
};
