import { describe, expect, it } from 'vitest';
import { bestiaColor } from './slotColors';

describe('bestiaColor', () => {
	it('matches the game client for the first bestia and cycles past the last', () => {
		expect(bestiaColor(0)).toBe('#ff7340');
		expect(bestiaColor(8)).toBe(bestiaColor(0));
	});
});
