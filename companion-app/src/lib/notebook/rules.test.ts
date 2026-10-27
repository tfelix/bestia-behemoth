import { describe, expect, it } from 'vitest';
import { applyChange, type Collection } from './rules';

const held: Collection = {
	revision: 4,
	entries: [
		{ key: 'a', revision: 2, value: { name: 'Ore run' } },
		{ key: 'b', revision: 4, value: { name: 'Home' } }
	]
};

describe('following a notebook collection', () => {
	it('applies the next revision in place, keeping the order', () => {
		const next = applyChange(held, { key: 'a', revision: 5, deleted: false, value: { name: 'Iron run' } });

		expect(next).toEqual({
			revision: 5,
			entries: [
				{ key: 'a', revision: 5, value: { name: 'Iron run' } },
				{ key: 'b', revision: 4, value: { name: 'Home' } }
			]
		});
	});

	it('adds a new key at the end and removes a deleted one', () => {
		const added = applyChange(held, { key: 'c', revision: 5, deleted: false, value: {} }) as Collection;
		expect(added.entries.map((e) => e.key)).toEqual(['a', 'b', 'c']);

		const deleted = applyChange(added, { key: 'a', revision: 6, deleted: true, value: null }) as Collection;
		expect(deleted.entries.map((e) => e.key)).toEqual(['b', 'c']);
		expect(deleted.revision).toBe(6);
	});

	it('ignores what it has already seen - its own write coming back, or a replay', () => {
		expect(applyChange(held, { key: 'a', revision: 4, deleted: false, value: {} })).toBe('ignored');
		expect(applyChange(held, { key: 'a', revision: 1, deleted: true, value: null })).toBe('ignored');
	});

	it('says a write was missed when the revision jumps', () => {
		expect(applyChange(held, { key: 'a', revision: 6, deleted: false, value: {} })).toBe('gap');
	});
});
