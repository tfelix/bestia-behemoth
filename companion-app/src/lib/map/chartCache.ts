import type { Place } from '#lib/api/model.ts';

/** How long a tile or a place list is trusted without asking again. Charting also clears it at once, see [ChartCache.clear]. */
const TTL_MILLIS = 120_000;

/** Entries, not bytes: a tile is tens of kilobytes, so this stays in the low tens of megabytes. */
const CAPACITY = 400;

/**
 * A map whose entries expire [ttlMillis] after they were stored and that, past [capacity], drops the least
 * recently used one first.
 */
export class TtlMap<V> {
	private readonly entries = new Map<string, { value: V; storedAt: number }>();

	constructor(
		private readonly ttlMillis: number,
		private readonly capacity: number,
		private readonly now: () => number = Date.now
	) {}

	get size(): number {
		return this.entries.size;
	}

	get(key: string): V | undefined {
		const entry = this.entries.get(key);
		if (!entry) return undefined;
		if (this.now() - entry.storedAt >= this.ttlMillis) {
			this.entries.delete(key);
			return undefined;
		}
		// Re-inserted at the back: a Map iterates in insertion order, so its front is the least recently used.
		this.entries.delete(key);
		this.entries.set(key, entry);
		return entry.value;
	}

	set(key: string, value: V): void {
		this.entries.delete(key);
		this.entries.set(key, { value, storedAt: this.now() });
		if (this.entries.size > this.capacity) {
			this.entries.delete(this.entries.keys().next().value!);
		}
	}

	clear(): void {
		this.entries.clear();
	}
}

/**
 * What the map has already asked the api-server this visit, so that zooming out and back in, or panning over
 * ground just left, does not spend the account's request budget again. A tile is the bytes, or null for ground
 * that is not charted (a 404); a failure is never stored.
 *
 * Charts change as the Master walks, and the world says so with a ChartsChanged event: that clears the cache and
 * the subscribers redraw. The lifetime only bounds how stale a view can get if an event is missed.
 */
export class ChartCache {
	readonly tiles: TtlMap<Blob | null>;
	readonly places: TtlMap<Place[]>;
	private readonly listeners = new Set<() => void>();

	constructor(ttlMillis = TTL_MILLIS, capacity = CAPACITY, now: () => number = Date.now) {
		this.tiles = new TtlMap(ttlMillis, capacity, now);
		this.places = new TtlMap(ttlMillis, capacity, now);
	}

	/** Forgets everything and tells the subscribers, which draw again from the server. */
	clear(): void {
		this.tiles.clear();
		this.places.clear();
		for (const listener of [...this.listeners]) listener();
	}

	/** Calls [listener] on every [clear]; returns the unsubscribe. */
	onClear(listener: () => void): () => void {
		this.listeners.add(listener);
		return () => this.listeners.delete(listener);
	}
}
