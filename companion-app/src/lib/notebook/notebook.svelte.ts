import { ApiError, getJson, sendJson } from '#lib/api/client.ts';
import type { LiveEvent } from '#lib/live.svelte.ts';
import { applyChange, type Collection, type NotebookEntry } from './rules';

type Page = { revision: number; entries: NotebookEntry[] };
type Written = { revision: number; key: string; value: unknown | null };

/** The key of a collection that holds one document, such as preferences. */
export const SINGLE_KEY = '_';

/**
 * One master's notebook - routes, map marks, map preferences - as the game keeps it too. Collections are listed on
 * first use and then followed: a write made here is applied from the server's answer, and one made in the game
 * arrives as a `NotebookChanged` event, both by the rules in `rules.ts`. A write refused as stale (409) lists the
 * collection again, so what is shown is the newer version.
 */
export class Notebook {
	collections = $state<Record<string, Collection>>({});

	constructor(private readonly masterId: number) {}

	entries<T>(collection: string): NotebookEntry<T>[] {
		return (this.collections[collection]?.entries ?? []) as NotebookEntry<T>[];
	}

	document<T>(collection: string): T | null {
		return (this.entries<T>(collection).find((entry) => entry.key === SINGLE_KEY)?.value ?? null) as T | null;
	}

	async load(collection: string): Promise<void> {
		const page = await getJson<Page>(this.path(collection));
		this.collections = { ...this.collections, [collection]: { revision: page.revision, entries: page.entries } };
	}

	/** A new entry under a key the server picks. */
	add(collection: string, value: unknown): Promise<void> {
		return this.write(collection, 'POST', this.path(collection), { value, baseRevision: 0 });
	}

	/** With [baseRevision] the write lands only while the entry is still at it. */
	put(collection: string, key: string, value: unknown, baseRevision?: number): Promise<void> {
		return this.write(collection, 'PUT', `${this.path(collection)}/${encodeURIComponent(key)}`, { value, baseRevision });
	}

	remove(collection: string, key: string, baseRevision?: number): Promise<void> {
		const query = baseRevision === undefined ? '' : `?baseRevision=${baseRevision}`;
		return this.write(collection, 'DELETE', `${this.path(collection)}/${encodeURIComponent(key)}${query}`, undefined);
	}

	/** Applies a `NotebookChanged` event of this master's; false for any other event. */
	receive(event: LiveEvent): boolean {
		if (event.type !== 'NotebookChanged' || event.data.masterId !== this.masterId) return false;

		const collection = String(event.data.collection);
		const json = event.data.json;
		this.follow(collection, {
			key: String(event.data.key),
			revision: Number(event.data.revision),
			deleted: Boolean(event.data.deleted),
			value: typeof json === 'string' ? JSON.parse(json) : null
		});
		return true;
	}

	private async write(collection: string, method: string, path: string, body: unknown): Promise<void> {
		try {
			const written = await sendJson<Written>(method, path, body, { 'Idempotency-Key': crypto.randomUUID() });
			this.follow(collection, { key: written.key, revision: written.revision, deleted: written.value === null, value: written.value });
		} catch (e) {
			if (e instanceof ApiError && e.status === 409) {
				await this.load(collection);
				throw new Error('That changed in the game meanwhile; this is the newer version.');
			}
			throw e;
		}
	}

	private follow(collection: string, change: Parameters<typeof applyChange>[1]): void {
		const held = this.collections[collection];
		if (!held) return;

		const next = applyChange(held, change);
		if (next === 'gap') void this.load(collection);
		else if (next !== 'ignored') this.collections = { ...this.collections, [collection]: next };
	}

	private path(collection: string): string {
		return `/v1/companion/masters/${this.masterId}/notebook/${encodeURIComponent(collection)}`;
	}
}
