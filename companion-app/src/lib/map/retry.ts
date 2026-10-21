/** Tries per tile, the first included: a world that is still busy after this is left as a blank tile. */
export const MAX_ATTEMPTS = 5;

const MAX_DELAY_MILLIS = 60_000;

/**
 * A failure worth asking again about: the account is over its request budget, or the world is busy, asleep or
 * slow to answer. A 404 is not one, it is the answer "nothing charted here".
 */
export function isTransient(status: number): boolean {
	return status === 429 || status === 502 || status === 503 || status === 504;
}

/**
 * How long to wait before retry number [attempt] (1 is the first). The server's Retry-After wins when it sent
 * one, since asking sooner only spends more of the budget it is refusing; otherwise 1 s, 2 s, 4 s and so on.
 * [jitter], in [0, 1), spreads out the tiles that failed together so they do not all come back in one burst.
 */
export function retryDelayMillis(attempt: number, retryAfter: string | null, jitter = Math.random()): number {
	const seconds = retryAfter === null || retryAfter.trim() === '' ? NaN : Number(retryAfter);
	const base = Number.isFinite(seconds) && seconds >= 0 ? seconds * 1000 : 1000 * 2 ** (attempt - 1);
	return Math.min(base, MAX_DELAY_MILLIS) + Math.floor(jitter * 1000);
}
