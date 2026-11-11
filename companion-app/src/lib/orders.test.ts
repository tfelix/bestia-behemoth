import { describe, expect, it } from 'vitest';
import type { Bestia } from '#lib/api/model.ts';
import { changed, draftOf, fallbacksFor, ordersOf, ruleOf, sheetOf } from './orders';

function bestia(changes: Partial<Bestia>): Bestia {
	return {
		id: 7,
		name: 'Rex',
		speciesId: 1,
		species: 'wolf',
		level: 3,
		job: 'GATHER',
		courage: 'STEADY',
		inWorld: true,
		position: { eastMetres: 0, northMetres: 0 },
		asOf: 0,
		metresPerSecond: 4,
		overrides: {},
		rules: [],
		ownOverrides: {},
		...changes
	};
}

describe('orders', () => {
	it('offers a fallback only behind a job that runs out of work, never the job itself or rest', () => {
		expect(fallbacksFor('GATHER')).toEqual(['GUARD', 'CRAFT', 'MINE']);
		expect(fallbacksFor('GUARD')).toEqual([]);
		expect(fallbacksFor('REST')).toEqual([]);
	});

	it('sends a fallback the job can no longer take as none', () => {
		const draft = { ...draftOf(bestia({ fallbackJob: 'CRAFT' })), job: 'GUARD' };
		expect(ordersOf(draft).fallbackJob).toBeUndefined();
	});

	it('leaves out emptied numbers', () => {
		const draft = { ...draftOf(bestia({})), numbers: { haulAtLoadPct: 60, eatBelowStaminaPct: Number.NaN } };
		expect(ordersOf(draft).overrides).toEqual({ haulAtLoadPct: 60 });
	});

	it('edits the own numbers of a bestia on a preset and sends only those', () => {
		const following = bestia({ presetId: 3, overrides: { haulAtLoadPct: 50, eatBelowStaminaPct: 70 }, ownOverrides: { eatBelowStaminaPct: 70 } });
		const draft = draftOf(following);

		expect(draft.numbers).toEqual({ eatBelowStaminaPct: 70 });
		expect(ordersOf(draft)).toMatchObject({ presetId: 3, overrides: { eatBelowStaminaPct: 70 }, rules: [] });
		expect(changed(following, draft)).toBe(false);
	});

	it('gives a changed condition a fitting action and a number only where one is taken', () => {
		expect(ruleOf({ condition: 'NIGHT', threshold: 30, action: 'EAT' })).toEqual({ condition: 'NIGHT', threshold: undefined, action: 'REST_AT_HOME' });
		expect(ruleOf({ condition: 'BAG_ABOVE', threshold: undefined, action: 'EAT' })).toEqual({ condition: 'BAG_ABOVE', threshold: 30, action: 'HAUL_NOW' });
	});

	it('saves a preset from whole orders, never a link', () => {
		const draft = { ...draftOf(bestia({ rules: [{ condition: 'DAY', action: 'KEEP_WORKING' }] })), presetId: 3 };
		expect(sheetOf(draft)).toEqual({
			job: 'GATHER',
			courage: 'STEADY',
			fallbackJob: undefined,
			overrides: {},
			rules: [{ condition: 'DAY', threshold: undefined, action: 'KEEP_WORKING' }]
		});
	});
});
