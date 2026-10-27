import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { columnIndex, columnName, formatGridRef, parseGridRef } from './gridRef';

const WORLD = 128_000;
const CASES = new URL('../../../../shared/src/test/resources/gridref-cases.tsv', import.meta.url);

function cases(): string[][] {
	return readFileSync(CASES, 'utf8')
		.split(/\r?\n/)
		.filter((line) => line !== '' && !line.startsWith('#'))
		.map((line) => line.split('\t'));
}

describe('grid references', () => {
	it('holds every case the server holds', () => {
		const all = cases();
		expect(all.length).toBeGreaterThan(20);

		for (const [kind, a, b, c, d] of all) {
			const label = [kind, a, b, c, d].join(' | ');
			switch (kind) {
				case 'format':
					expect(formatGridRef(Number(a), Number(b), WORLD, WORLD, Number(c)), label).toBe(d);
					break;
				case 'parse':
					expect(parseGridRef(a, WORLD, WORLD), label).toEqual({
						eastMetres: Number(b),
						northMetres: Number(c),
						precisionMetres: Number(d)
					});
					break;
				case 'invalid':
					expect(parseGridRef(a ?? '', WORLD, WORLD), label).toBeNull();
					break;
				case 'column':
					expect(columnName(Number(a)), label).toBe(b);
					expect(columnIndex(b), label).toBe(Number(a));
					break;
				default:
					throw new Error(`Unknown case kind: ${label}`);
			}
		}
	});
});
