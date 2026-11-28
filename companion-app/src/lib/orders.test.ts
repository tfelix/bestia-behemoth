import { describe, expect, it } from 'vitest';
import type { Bestia } from '#lib/api/model.ts';
import { MAX_ITEMS, changed, draftOf, fallbacksFor, gathers, mayTake, ordersOf, ruleOf, sheetOf, withItem, withSkip } from './orders';

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
		allowedJobs: [],
		careItems: { neverUse: [], alsoUse: [] },
		gatherFilter: { skip: [], onlyItems: [] },
		staysOutOfFights: false,
		...changes
	};
}

describe('orders', () => {
	it('lets a species take only the jobs it may, and leaves an unknown species to the zone', () => {
		expect(mayTake(['GATHER', 'REST', 'FOLLOW'], 'GATHER')).toBe(true);
		expect(mayTake(['GATHER', 'REST', 'FOLLOW'], 'MINE')).toBe(false);
		expect(mayTake([], 'MINE')).toBe(true);
	});

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
			rules: [{ condition: 'DAY', threshold: undefined, action: 'KEEP_WORKING' }],
			areaPlaceId: undefined,
			chestPlaceId: undefined,
			careItems: { neverUse: [], alsoUse: [] },
			gatherFilter: { skip: [], onlyItems: [] }
		});
	});

	it('sends its care items, and none while it follows a preset', () => {
		const careItems = { neverUse: [46], alsoUse: [3] };
		const own = draftOf(bestia({ careItems }));
		expect(ordersOf(own).careItems).toEqual(careItems);
		expect(changed(bestia({ careItems }), own)).toBe(false);

		own.careItems.alsoUse.push(4);
		expect(changed(bestia({ careItems }), own)).toBe(true);
		expect(ordersOf({ ...own, presetId: 3 }).careItems).toEqual({ neverUse: [], alsoUse: [] });
	});

	it('keeps staying out of fights as the Bestia\'s own, also on a preset, and never in a preset\'s sheet', () => {
		const frail = bestia({ staysOutOfFights: true, presetId: 3 });
		const draft = draftOf(frail);

		expect(ordersOf(draft).staysOutOfFights).toBe(true);
		expect(changed(frail, draft)).toBe(false);
		expect(sheetOf(draft)).not.toHaveProperty('staysOutOfFights');
		draft.staysOutOfFights = false;
		expect(changed(frail, draft)).toBe(true);
	});

	it('keeps skipped categories in the zone order, so an unchanged filter is no change', () => {
		const gatherer = bestia({ gatherFilter: { skip: ['LOOSE', 'TREE'], onlyItems: [] } });
		const draft = draftOf(gatherer);
		draft.gatherFilter.skip = withSkip(withSkip(draft.gatherFilter.skip, 'LOOSE', false), 'LOOSE', true);

		expect(draft.gatherFilter.skip).toEqual(['LOOSE', 'TREE']);
		expect(changed(gatherer, draft)).toBe(false);
	});

	it('shows the gather filter to a gatherer and to one that gathers when idle', () => {
		expect(gathers(draftOf(bestia({ job: 'GATHER' })))).toBe(true);
		expect(gathers(draftOf(bestia({ job: 'MINE', fallbackJob: 'GATHER' })))).toBe(true);
		expect(gathers(draftOf(bestia({ job: 'GUARD', fallbackJob: 'GATHER' })))).toBe(false);
	});

	it('adds an item once, and no more than fit', () => {
		expect(withItem([3], 3)).toEqual([3]);
		expect(withItem([3], 4)).toEqual([3, 4]);
		const full = Array.from({ length: MAX_ITEMS }, (_, i) => i + 1);
		expect(withItem(full, 99)).toBe(full);
	});
});
