import { describe, expect, it } from 'vitest';
import { diaryText, shownInDiary } from './diary';

const line = { playerBestiaId: 1, atEpochMs: 0, items: {} };

describe('diary', () => {
	it('sums a haul into one line', () => {
		expect(diaryText({ ...line, kind: 'HAULED', items: { unknown_log: 12, unknown_herb: 3 } })).toBe(
			'Brought 12 unknown_log, 3 unknown_herb to the chest'
		);
	});

	it('names the chest a haul went to', () => {
		expect(diaryText({ ...line, kind: 'HAULED', items: { unknown_log: 2 }, place: 'Camp chest' })).toBe(
			'Brought 2 unknown_log to Camp chest'
		);
	});

	it('says when the field is empty and the other job takes over', () => {
		expect(diaryText({ ...line, kind: 'FIELD_EMPTY' })).toBe('Found nothing more to gather in its area');
		expect(diaryText({ ...line, kind: 'FALLBACK_STARTED', reason: 'CRAFT' })).toBe(
			'Had no more work and turned to its other job: Craft'
		);
	});

	it('keeps state lines out of the list', () => {
		expect(shownInDiary({ ...line, kind: 'STATE', reason: 'WORKING' })).toBe(false);
		expect(shownInDiary({ ...line, kind: 'HAULED' })).toBe(true);
	});

	it('says where and why work stopped', () => {
		expect(diaryText({ ...line, kind: 'WORK_PAUSED', place: 'CAMPFIRE', reason: 'STATION_OUT' })).toBe(
			'Stopped working at Campfire: its fire went out'
		);
		expect(diaryText({ ...line, kind: 'WORK_PAUSED', reason: 'CHEST_FULL' })).toBe('Stopped working: the chest is full');
	});

	it('names new orders by their job', () => {
		expect(diaryText({ ...line, kind: 'NEW_ORDERS', reason: 'MINE' })).toBe('Got new orders: Mine');
	});

	it('names the kind of event a watching bestia felt', () => {
		expect(diaryText({ ...line, kind: 'EVENT_SENSED', reason: 'mana' })).toBe(
			'Felt something of the mana stir near the outpost'
		);
	});

	it('says when food runs low and when it runs out', () => {
		expect(diaryText({ ...line, kind: 'LOW_FOOD' })).toBe('Has food for about two more hours of work');
		expect(diaryText({ ...line, kind: 'OUT_OF_FOOD' })).toBe('Ran out of food and waits at home');
	});
});
