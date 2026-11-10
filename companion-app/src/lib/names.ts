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

export function speciesDescription(identifier: string): string | null {
	return names[`BESTIA_${identifier.toUpperCase()}_DESC`] ?? null;
}

/** A biome's name, from general.csv's `BIOME_<NAME>`. */
export function biomeName(biome: string): string {
	return names[`BIOME_${biome}`] ?? biome.toLowerCase().replace(/_/g, ' ');
}

/** What a job means to a player, in the words of the game's Bestia orders. */
export const JOB_TEXT: Record<string, { name: string; text: string }> = {
	GUARD: { name: 'Guard', text: 'Roams around where it was left and drives off anything hostile that comes near.' },
	GATHER: {
		name: 'Gather',
		text: 'Collects sticks, plants, crystals and logs around where it was left and carries them to your nearest chest.'
	},
	CRAFT: {
		name: 'Craft',
		text: 'Works the queue of any of your stations around where it was left that takes any free crafter.'
	},
	REST: { name: 'Rest', text: 'Stays around where it was left, eats and sleeps as needed.' }
};

export const COURAGE_NAME: Record<string, string> = {
	TIMID: 'Timid',
	CAREFUL: 'Careful',
	STEADY: 'Steady',
	BOLD: 'Bold',
	BRAVE: 'Brave'
};
