import { describe, expect, it } from 'vitest';
import { age } from './age';

describe('age', () => {
	const now = 1_800_000_000_000;

	it('words the age of a report', () => {
		expect(age(now - 10_000, now)).toBe('just now');
		expect(age(now - 4 * 60_000, now)).toBe('4 min ago');
		expect(age(now - 3 * 3_600_000, now)).toBe('3 h ago');
		expect(age(now - 3 * 86_400_000, now)).toBe('3 days ago');
	});

	it('does not invent an age for a row that was never saved', () => {
		expect(age(0, now)).toBe('not yet reported');
	});
});
