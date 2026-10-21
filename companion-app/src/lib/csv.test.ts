import { describe, expect, it } from 'vitest';
import { parseCsv } from './csv';

describe('parseCsv', () => {
	it('reads quoted fields that span lines and carry doubled quotes', () => {
		const text = 'keys,en\r\nAPPLE,Apple\r\nAPPLE_DESC,"A fresh apple.\n\nSays ""crunch""."\r\n';

		expect(parseCsv(text)).toEqual([
			['keys', 'en'],
			['APPLE', 'Apple'],
			['APPLE_DESC', 'A fresh apple.\n\nSays "crunch".']
		]);
	});

	it('keeps a last row without a newline', () => {
		expect(parseCsv('a,b\nc,d')).toEqual([
			['a', 'b'],
			['c', 'd']
		]);
	});
});
