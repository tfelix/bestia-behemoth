/** The Bestia Compendium's list rules, the same as the game window's (`CompendiumSearch.cs`). */

export type Tab = 'all' | 'seen' | 'caught';

export type Page = {
	id: number;
	/** The species' name, or null while it is unseen: an unseen name is not the player's to read. */
	name: string | null;
	caught: boolean;
};

/** `#003`: the number every entry shows, seen or not. */
export function numberLabel(id: number): string {
	return `#${String(id).padStart(3, '0')}`;
}

/** A query matches the number, with or without its `#`, or a seen species' name. */
export function matches(page: Page, tab: Tab, query: string): boolean {
	if (tab === 'seen' && page.name === null) return false;
	if (tab === 'caught' && !page.caught) return false;

	const wanted = query.trim().toLowerCase();
	if (wanted === '') return true;
	if (numberLabel(page.id).includes(wanted.replace(/^#/, ''))) return true;

	return page.name !== null && page.name.toLowerCase().includes(wanted);
}
