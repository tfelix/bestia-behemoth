import { describe, expect, it } from 'vitest';
import { diaryText } from './diary';

const line = { playerBestiaId: 1, atEpochMs: 0, items: {} };

describe('diary', () => {
	it('sums a haul into one line', () => {
		expect(diaryText({ ...line, kind: 'HAULED', items: { unknown_log: 12, unknown_herb: 3 } })).toBe(
			'Brought 12 unknown_log, 3 unknown_herb to the chest'
		);
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
});
