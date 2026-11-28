/**
 * Bestia orders as the Fine-tune form edits them, mirroring what the zone accepts: which jobs take a fallback, the
 * limits of each number, and which rule actions each condition allows. The zone checks all of it again.
 */
import type { Bestia, BestiaOrders, OrderCareItems, OrderGatherFilter, OrderNumbers, OrderRuleLine, OrderSheet } from '#lib/api/model.ts';

/** The jobs that can run out of work, and so hand over to a fallback. */
export const TAKES_FALLBACK = ['GATHER', 'CRAFT', 'MINE'];

/** Whether a species may take a job; a roster that names no jobs leaves the check to the zone. */
export function mayTake(allowedJobs: string[] | undefined, job: string): boolean {
	return !allowedJobs?.length || allowedJobs.includes(job);
}

/** What a job may fall back to: any other job but rest. */
export function fallbacksFor(job: string): string[] {
	if (!TAKES_FALLBACK.includes(job)) return [];
	return ['GUARD', 'GATHER', 'CRAFT', 'MINE'].filter((other) => other !== job);
}

export type NumberKey = keyof OrderNumbers;

export const NUMBERS: { key: NumberKey; name: string; min: number; max: number; unit: string }[] = [
	{ key: 'retreatBelowHealthPct', name: 'Retreat below', min: 0, max: 90, unit: '% HP' },
	{ key: 'eatBelowStaminaPct', name: 'Eat below', min: 10, max: 90, unit: '% stamina' },
	{ key: 'healBelowHealthPct', name: 'Heal below', min: 10, max: 90, unit: '% HP' },
	{ key: 'haulAtLoadPct', name: 'Haul at', min: 30, max: 100, unit: '% load' },
	{ key: 'areaRadius', name: 'Area radius', min: 4, max: 64, unit: 'tiles' }
];

export const MAX_RULES = 8;

/** Items a bestia may use to look after itself, beyond common food and bandages, and those it must leave alone. */
export const CARE_LISTS: { key: keyof OrderCareItems; name: string }[] = [
	{ key: 'neverUse', name: 'Never use' },
	{ key: 'alsoUse', name: 'May also use' }
];

export const MAX_ITEMS = 8;

/** What a gatherer can be told to leave alone, in the zone's order. */
export const GATHER_CATEGORIES: { key: string; name: string; text: string }[] = [
	{ key: 'LOOSE', name: 'Loose things', text: 'Sticks, resin, loose stones, reeds' },
	{ key: 'PLANT', name: 'Plants', text: 'Herbs and shrubs' },
	{ key: 'TREE', name: 'Trees', text: 'Felled for logs and resin' },
	{ key: 'CRYSTAL', name: 'Mana crystals', text: 'Mana and aetherite crystals' }
];

/** The items gathering yields, as zone-server's prop-kinds.yml says: what is picked up, and a felled tree's loot. */
export const GATHERED_ITEMS = [6, 7, 30, 31, 32, 59, 108, 116, 118, 131, 142, 171, 172, 173, 174, 175, 230, 612];

/** Whether the orders make it gather, as its job or while it has nothing else to do. */
export function gathers(draft: OrderDraft): boolean {
	return draft.job === 'GATHER' || (draft.fallbackJob === 'GATHER' && fallbacksFor(draft.job).includes('GATHER'));
}

/** [skip] with [category] skipped or not, in the zone's order so that an unchanged filter compares equal. */
export function withSkip(skip: string[], category: string, skipped: boolean): string[] {
	return GATHER_CATEGORIES.map((each) => each.key).filter((key) => (key === category ? skipped : skip.includes(key)));
}

/** [list] with [itemId] at its end, unless it holds it already or is full. */
export function withItem(list: number[], itemId: number): number[] {
	return list.includes(itemId) || list.length >= MAX_ITEMS ? list : [...list, itemId];
}

export const CONDITIONS: Record<string, { name: string; takesThreshold: boolean; actions: string[] }> = {
	STAMINA_BELOW: { name: 'Stamina below', takesThreshold: true, actions: ['EAT', 'REST_AT_HOME'] },
	HEALTH_BELOW: { name: 'HP below', takesThreshold: true, actions: ['HEAL', 'RETREAT'] },
	BAG_ABOVE: { name: 'Bag fuller than', takesThreshold: true, actions: ['HAUL_NOW'] },
	NIGHT: { name: 'It is night', takesThreshold: false, actions: ['REST_AT_HOME', 'KEEP_WORKING'] },
	DAY: { name: 'It is day', takesThreshold: false, actions: ['REST_AT_HOME', 'KEEP_WORKING'] },
	STRONGER_FOE_IN_SIGHT: { name: 'A stronger foe is in sight', takesThreshold: false, actions: ['RETREAT', 'HOLD_GROUND'] }
};

export const ACTION_NAME: Record<string, string> = {
	EAT: 'eat',
	REST_AT_HOME: 'rest at home',
	HEAL: 'heal',
	RETREAT: 'retreat',
	HAUL_NOW: 'haul to the chest now',
	KEEP_WORKING: 'keep working',
	HOLD_GROUND: 'hold the ground'
};

/** Everything the orders form edits. Numbers are a bestia's own while it follows a preset. */
export type OrderDraft = {
	job: string;
	courage: string;
	fallbackJob: string | null;
	numbers: OrderNumbers;
	rules: OrderRuleLine[];
	areaPlaceId: number | null;
	chestPlaceId: number | null;
	careItems: OrderCareItems;
	gatherFilter: OrderGatherFilter;
	/** The Bestia's own, kept while it follows a preset. */
	staysOutOfFights: boolean;
	presetId: number | null;
};

export function draftOf(b: Bestia): OrderDraft {
	const following = b.presetId != null;
	return {
		job: b.job,
		courage: b.courage,
		fallbackJob: b.fallbackJob ?? null,
		numbers: { ...(following ? b.ownOverrides : b.overrides) },
		rules: (b.rules ?? []).map((rule) => ({ ...rule })),
		areaPlaceId: b.areaPlaceId ?? null,
		chestPlaceId: b.chestPlaceId ?? null,
		careItems: { neverUse: [...(b.careItems?.neverUse ?? [])], alsoUse: [...(b.careItems?.alsoUse ?? [])] },
		gatherFilter: { skip: [...(b.gatherFilter?.skip ?? [])], onlyItems: [...(b.gatherFilter?.onlyItems ?? [])] },
		staysOutOfFights: b.staysOutOfFights ?? false,
		presetId: b.presetId ?? null
	};
}

/** The request that gives [draft]. A bestia on a preset sends only its own numbers; the preset brings the rest. */
export function ordersOf(draft: OrderDraft): BestiaOrders {
	const numbers = withoutBlanks(draft.numbers);
	if (draft.presetId != null) {
		return {
			job: draft.job,
			courage: draft.courage,
			presetId: draft.presetId,
			overrides: numbers,
			rules: [],
			ownOverrides: {},
			careItems: { neverUse: [], alsoUse: [] },
			gatherFilter: { skip: [], onlyItems: [] },
			staysOutOfFights: draft.staysOutOfFights
		};
	}
	const fallbackJob = fallbacksFor(draft.job).includes(draft.fallbackJob ?? '') ? (draft.fallbackJob ?? undefined) : undefined;
	return {
		job: draft.job,
		courage: draft.courage,
		fallbackJob,
		overrides: numbers,
		rules: draft.rules.map(ruleOf),
		areaPlaceId: draft.areaPlaceId ?? undefined,
		chestPlaceId: draft.chestPlaceId ?? undefined,
		ownOverrides: {},
		careItems: { neverUse: [...draft.careItems.neverUse], alsoUse: [...draft.careItems.alsoUse] },
		gatherFilter: { skip: [...draft.gatherFilter.skip], onlyItems: [...draft.gatherFilter.onlyItems] },
		staysOutOfFights: draft.staysOutOfFights
	};
}

export function sheetOf(draft: OrderDraft): OrderSheet {
	const { job, courage, fallbackJob, overrides, rules, areaPlaceId, chestPlaceId, careItems, gatherFilter } = ordersOf({ ...draft, presetId: null });
	return { job, courage, fallbackJob, overrides, rules, areaPlaceId, chestPlaceId, careItems, gatherFilter };
}

/** Whether [draft] differs from what [b] has now. */
export function changed(b: Bestia, draft: OrderDraft): boolean {
	return JSON.stringify(ordersOf(draft)) !== JSON.stringify(ordersOf(draftOf(b)));
}

/** A new rule line, the first condition with its first action. */
export function newRule(): OrderRuleLine {
	return { condition: 'STAMINA_BELOW', threshold: 30, action: 'EAT' };
}

/** A line whose condition changed keeps a fitting action and a number only where one is taken. */
export function ruleOf(line: OrderRuleLine): OrderRuleLine {
	const condition = CONDITIONS[line.condition];
	const action = condition.actions.includes(line.action) ? line.action : condition.actions[0];
	const threshold = condition.takesThreshold ? (line.threshold ?? 30) : undefined;
	return { condition: line.condition, threshold, action };
}

/** An emptied field means "not overridden". */
function withoutBlanks(numbers: OrderNumbers): OrderNumbers {
	return Object.fromEntries(Object.entries(numbers).filter(([, value]) => typeof value === 'number' && !Number.isNaN(value))) as OrderNumbers;
}
