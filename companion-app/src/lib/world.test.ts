import { describe, expect, it } from 'vitest';
import { doomOf, endingTitle, summaryLines, threatName } from './world';

describe('world', () => {
	const last = {
		name: 'Genesis',
		threat: 'DESTROYER',
		ending: 'LOST',
		daysLived: 300,
		lifeSpanPercent: 100,
		townsLost: 2,
		endedAtEpochMs: 0
	};

	it('names a doomsday as the game does', () => {
		expect(threatName('VOID_TEAR')).toBe('the Void Tear');
	});

	it('shows nothing of a doomsday the world does not show yet', () => {
		expect(doomOf({ name: 'Arvel', ageDays: 3 })).toBeNull();
		expect(doomOf({ name: 'Arvel', ageDays: 200, threat: 'WITHERING', stage: 'ESCALATION' })).toEqual({
			threat: 'The Withering',
			stage: 'Escalation',
			line: 'The doom spreads across the land.'
		});
	});

	it('sums up the last world as its final sequence did', () => {
		expect(endingTitle(last)).toBe('The world has ended');
		expect(summaryLines(last)).toEqual([
			'Genesis lived for 300 days.',
			'Its doom was the Destroyer.',
			'It lived to 100% of its foretold years.',
			'2 towns fell.'
		]);
		expect(summaryLines({ ...last, threat: undefined, townsLost: 1 })).toContain('One town fell.');
	});
});
