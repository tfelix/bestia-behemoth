import { describe, expect, it, vi } from 'vitest';
import { ChartCache, TtlMap } from './chartCache';

describe('TtlMap', () => {
	it('keeps an entry until its lifetime is over', () => {
		let now = 1000;
		const map = new TtlMap<string>(500, 10, () => now);
		map.set('a', 'tile');

		now = 1499;
		expect(map.get('a')).toBe('tile');
		now = 1500;
		expect(map.get('a')).toBeUndefined();
		expect(map.size).toBe(0);
	});

	it('starts the lifetime again when an entry is stored again', () => {
		let now = 0;
		const map = new TtlMap<string>(500, 10, () => now);
		map.set('a', 'old');
		now = 400;
		map.set('a', 'new');
		now = 800;
		expect(map.get('a')).toBe('new');
	});

	it('drops the least recently used entry past its capacity', () => {
		const map = new TtlMap<number>(1000, 2, () => 0);
		map.set('a', 1);
		map.set('b', 2);
		map.get('a');
		map.set('c', 3);

		expect(map.get('b')).toBeUndefined();
		expect(map.get('a')).toBe(1);
		expect(map.get('c')).toBe(3);
	});

	it('holds a null, which is uncharted ground, as an entry', () => {
		const map = new TtlMap<Blob | null>(1000, 2, () => 0);
		map.set('fog', null);
		expect(map.get('fog')).toBeNull();
	});
});

describe('ChartCache', () => {
	it('forgets tiles and places on clear and tells the subscribers', () => {
		const cache = new ChartCache();
		const listener = vi.fn();
		cache.onClear(listener);
		cache.tiles.set('t/3/1/1', null);
		cache.places.set('3/1/1', []);

		cache.clear();

		expect(cache.tiles.get('t/3/1/1')).toBeUndefined();
		expect(cache.places.get('3/1/1')).toBeUndefined();
		expect(listener).toHaveBeenCalledTimes(1);
	});

	it('stops calling a subscriber that unsubscribed', () => {
		const cache = new ChartCache();
		const listener = vi.fn();
		const unsubscribe = cache.onClear(listener);
		unsubscribe();

		cache.clear();

		expect(listener).not.toHaveBeenCalled();
	});
});
