import { describe, expect, it } from 'vitest';
import type { MasterSummary } from '$lib/api/model';
import { pickRemembered } from './lastMaster';

function master(id: number): MasterSummary {
	return { id, slot: 0, name: `Master ${id}`, level: 1, home: '', inWorld: false };
}

describe('pickRemembered', () => {
	it('finds the Master the player had open', () => {
		expect(pickRemembered([master(1), master(2)], 2)?.id).toBe(2);
	});

	it('finds none for a Master the account does not have', () => {
		expect(pickRemembered([master(1)], 7)).toBeNull();
		expect(pickRemembered([master(1)], null)).toBeNull();
	});
});
