import names from 'virtual:bestia-names';

/** An item's name as the game shows it, from its identifier (`gold_coin` is `GOLD_COIN` in items.csv). */
export function itemName(identifier: string): string {
	return names[identifier.toUpperCase()] ?? identifier;
}

export function itemDescription(identifier: string): string | null {
	return names[`${identifier.toUpperCase()}_DESC`] ?? null;
}

/** A species' name, from bestias.csv's `BESTIA_<IDENTIFIER>`. */
export function speciesName(identifier: string): string {
	return names[`BESTIA_${identifier.toUpperCase()}`] ?? identifier;
}

/** What a stance means to a player, in the words of the game's standing orders. */
export const STANCE_TEXT: Record<string, { name: string; text: string }> = {
	HOLD: { name: 'Hold', text: 'Stays put. Defends itself, but will not wander, forage or pick fights.' },
	PATROL: { name: 'Patrol', text: 'Roams around where it was left and comes back if it strays.' },
	FORAGE: { name: 'Forage', text: 'Looks after itself: eats and sleeps as needed, roaming meanwhile.' },
	DEFEND: { name: 'Defend', text: 'Engages anything hostile that comes near, rather than waiting to be hit.' }
};
