import { describe, expect, it } from 'vitest';
import type { BestiaReport } from '#lib/api/model.ts';
import { eventful, reportText, timeOfDay } from './report';

const HOUR = 3600;

/** Local times, so the words do not depend on the machine's time zone. */
function at(hours: number, minutes = 0): number {
	return new Date(2026, 10, 28, hours, minutes).getTime();
}

const quiet: BestiaReport = {
	playerBestiaId: 1,
	name: 'Pip',
	species: 'fox',
	brought: {},
	broughtTo: [],
	madeAt: {},
	timeIn: {},
	incidents: [],
	now: 'RESTING_AT_HOME'
};

describe('away report', () => {
	it('tells a night of gathering that ended without food', () => {
		const pip: BestiaReport = {
			...quiet,
			brought: { unknown_resin: 36, unknown_log: 214 },
			broughtTo: ['Camp chest'],
			timeIn: { WORKING: 7 * HOUR + 60, RESTING: 3 * HOUR - 60, FIGHTING: 120 },
			incidents: [
				{ kind: 'ATTACKED', atEpochMs: at(0, 5) },
				{ kind: 'ATTACKED', atEpochMs: at(2, 30) },
				{ kind: 'OUT_OF_FOOD', atEpochMs: at(5) }
			],
			blocker: 'OUT_OF_FOOD',
			hint: 'BRING_FOOD'
		};

		expect(reportText(pip, timeOfDay)).toEqual([
			'Pip worked for 7 hours and rested for 3 hours.',
			'It brought 214 unknown_log and 36 unknown_resin to the Camp chest.',
			'It was attacked twice, first just after midnight.',
			'It ran out of food at 5 am.',
			'It is resting at home now.',
			'To go on: put food in the Camp chest.'
		]);
	});

	it('counts finished queues and says why work stopped', () => {
		const crafter: BestiaReport = {
			...quiet,
			name: undefined,
			species: 'unknown_beaver',
			madeAt: { WORKBENCH: 2, CAMPFIRE: 1 },
			timeIn: { WORKING: 40 * 60 },
			incidents: [{ kind: 'WORK_PAUSED', atEpochMs: at(14, 40), place: 'CAMPFIRE', reason: 'STATION_OUT' }],
			now: 'CRAFTING',
			nowAt: 'CAMPFIRE',
			blocker: 'STATION_OUT',
			hint: 'FEED_FIRE'
		};

		expect(reportText(crafter, timeOfDay)).toEqual([
			'unknown_beaver worked for 40 minutes.',
			'It finished 2 queues at the Workbench and the queue at the Campfire.',
			'It stopped working at 2:40 pm, because its fire went out.',
			'It is crafting at Campfire now.',
			'To go on: put fuel in the fire.'
		]);
	});

	it('tells a death and what to do about it', () => {
		const fallen: BestiaReport = {
			...quiet,
			incidents: [{ kind: 'DIED', atEpochMs: at(12, 5) }],
			now: 'DEAD',
			blocker: 'DIED',
			hint: 'REVIVE'
		};

		expect(reportText(fallen, timeOfDay)).toEqual([
			'It was killed just after noon.',
			'It lies where it fell.',
			'To go on: go to it and bring it back to life.'
		]);
	});

	it('lists three goods at most and an unnamed chest as its chest', () => {
		const hauler: BestiaReport = { ...quiet, brought: { a: 4, b: 3, c: 2, d: 1 }, now: 'NOT_IN_WORLD' };

		expect(reportText(hauler, timeOfDay)).toEqual(['It brought 4 a, 3 b, 2 c and more to its chest.', 'It is not in the world now.']);
	});

	it('leaves out a Bestia that only spent time', () => {
		expect(eventful({ ...quiet, timeIn: { RESTING: 10 * HOUR } })).toBe(false);
		expect(eventful({ ...quiet, blocker: 'CHEST_FULL' })).toBe(true);
	});

	it('names the time of day plainly', () => {
		expect(timeOfDay(at(0, 10))).toBe('just after midnight');
		expect(timeOfDay(at(5))).toBe('at 5 am');
		expect(timeOfDay(at(23, 7))).toBe('at 11:07 pm');
	});
});
