import keys from 'array.prototype.keys';
import * as keysModule from 'array.prototype.keys';
import test from 'tape';
import runTests from './tests.js';

test('as a function', (t) => {
	t.test('bad array/this value', (st) => {
		st.throws(() => keys(undefined), TypeError, 'undefined is not an object');
		st.throws(() => keys(null), TypeError, 'null is not an object');
		st.end();
	});

	runTests(keys, t);

	t.end();
});

test('named exports', async (t) => {
	t.deepEqual(
		Object.keys(keysModule).sort(),
		['default', 'shim', 'getPolyfill', 'implementation'].sort(),
		'has expected named exports',
	);

	const { shim, getPolyfill, implementation } = keysModule;
	t.equal((await import('array.prototype.keys/shim')).default, shim, 'shim named export matches deep export');
	t.equal((await import('array.prototype.keys/implementation')).default, implementation, 'implementation named export matches deep export');
	t.equal((await import('array.prototype.keys/polyfill')).default, getPolyfill, 'getPolyfill named export matches deep export');

	t.end();
});
