import { describe, expect, it } from 'vitest';
import { matches, numberLabel, type Page } from './compendium';

const blob: Page = { id: 1, name: 'Blob', caught: false };
const unseen: Page = { id: 2, name: null, caught: false };

describe('numberLabel', () => {
	it('pads to three digits', () => {
		expect(numberLabel(3)).toBe('#003');
		expect(numberLabel(120)).toBe('#120');
	});
});

describe('matches', () => {
	it('shows everything on the all tab without a query', () => {
		expect(matches(unseen, 'all', ' ')).toBe(true);
	});

	it('finds a number with or without its hash', () => {
		expect(matches(unseen, 'all', '2')).toBe(true);
		expect(matches(unseen, 'all', '#002')).toBe(true);
	});

	it('finds a seen name in any case, never an unseen one', () => {
		expect(matches(blob, 'all', 'bL')).toBe(true);
		expect(matches({ ...unseen, id: 7 }, 'all', 'blob')).toBe(false);
	});

	it('filters by seen and caught', () => {
		expect(matches(unseen, 'seen', '')).toBe(false);
		expect(matches(blob, 'seen', '')).toBe(true);
		expect(matches(blob, 'caught', '')).toBe(false);
		expect(matches({ ...blob, caught: true }, 'caught', '')).toBe(true);
	});
});
