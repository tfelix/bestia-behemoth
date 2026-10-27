/**
 * How the app's copy of a notebook collection follows the server's. Every write to a collection is numbered, so a
 * change one past the revision held is applied as it stands; an older one is a write already seen (this app's own,
 * coming back as an event, or a replay after a reconnect) and is ignored; anything further ahead means one was
 * missed, and the whole collection has to be listed again.
 */
export type NotebookEntry<T = unknown> = { key: string; revision: number; value: T };

export type Collection = { revision: number; entries: NotebookEntry[] };

export type Change = { key: string; revision: number; deleted: boolean; value: unknown };

export function applyChange(held: Collection, change: Change): Collection | 'ignored' | 'gap' {
	if (change.revision <= held.revision) return 'ignored';
	if (change.revision > held.revision + 1) return 'gap';

	const others = held.entries.filter((entry) => entry.key !== change.key);
	if (change.deleted) return { revision: change.revision, entries: others };

	const entry = { key: change.key, revision: change.revision, value: change.value };
	const at = held.entries.findIndex((existing) => existing.key === change.key);
	const entries = at < 0 ? [...others, entry] : held.entries.map((existing, i) => (i === at ? entry : existing));
	return { revision: change.revision, entries };
}
