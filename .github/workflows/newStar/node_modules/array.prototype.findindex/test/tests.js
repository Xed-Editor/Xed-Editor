'use strict';

var canDistinguishSparseFromUndefined = 0 in [undefined]; // IE 6 - 8 have a bug where this returns false.

var thrower = function () {
	throw new Error('should not reach here');
};

module.exports = function (findIndex, t) {
	var list = [5, 10, 15, 20];

	t.equal(
		findIndex(list, function (item) { return item === 15; }),
		2,
		'find index by predicate'
	);
	t.equal(
		findIndex(list, function (item) { return item === 'a'; }),
		-1,
		'returns -1 when nothing matches'
	);
	t['throws'](
		function () { findIndex(list); },
		TypeError,
		'throws without callback'
	);

	var context = {};
	var foundIndex = findIndex(list, function (value, index, arr) {
		t.equal(list[index], value);
		t.deepEqual(list, arr);
		t.equal(this, context, 'receiver is as expected');
		return false;
	}, context);
	t.equal(foundIndex, -1, 'receives all three arguments');

	var arraylike = { 0: 1, 1: 2, 2: 3, length: 3 };
	t.equal(
		findIndex(arraylike, function (item) {
			return item === 2;
		}),
		1,
		'works with an array-like object'
	);

	t.equal(
		findIndex({ 0: 1, 1: 2, 2: 3, length: -3 }, thrower),
		-1,
		'works with an array-like object with negative length'
	);

	t.test('sparse arrays', { skip: !canDistinguishSparseFromUndefined }, function (st) {
		st.test('works with a sparse array', function (s2t) {
			var obj = [1, , undefined]; // eslint-disable-line no-sparse-arrays
			s2t.notOk(1 in obj);
			var seen = [];
			var foundSparse = findIndex(obj, function (item, idx) {
				seen.push([idx, item]);
				return false;
			});
			s2t.equal(foundSparse, -1);
			s2t.deepEqual(seen, [[0, 1], [1, undefined], [2, undefined]]);

			s2t.end();
		});

		st.test('works with a sparse array-like object', function (s2t) {
			var obj = { 0: 1, 2: undefined, length: 3.2 };
			var seen = [];
			var foundSparse = findIndex(obj, function (item, idx) {
				seen.push([idx, item]);
				return false;
			});
			s2t.equal(foundSparse, -1);
			s2t.deepEqual(seen, [[0, 1], [1, undefined], [2, undefined]]);

			s2t.end();
		});

		st.end();
	});
};
