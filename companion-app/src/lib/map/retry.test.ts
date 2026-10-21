import { describe, expect, it } from 'vitest';
import { isTransient, retryDelayMillis } from './retry';

describe('which failures are asked again', () => {
	it('retries over-budget and busy-world answers', () => {
		for (const status of [429, 502, 503, 504]) expect(isTransient(status)).toBe(true);
	});

	it('does not retry uncharted ground or a refused request', () => {
		for (const status of [400, 401, 403, 404, 500]) expect(isTransient(status)).toBe(false);
	});
});

describe('retry delay', () => {
	it('doubles from one second without a Retry-After', () => {
		expect(retryDelayMillis(1, null, 0)).toBe(1000);
		expect(retryDelayMillis(2, null, 0)).toBe(2000);
		expect(retryDelayMillis(4, null, 0)).toBe(8000);
	});

	it('waits as long as the server asked', () => {
		expect(retryDelayMillis(1, '60', 0)).toBe(60_000);
		expect(retryDelayMillis(3, '5', 0)).toBe(5000);
	});

	it('falls back to the backoff on a Retry-After it cannot read', () => {
		expect(retryDelayMillis(2, 'Wed, 21 Oct 2026 07:28:00 GMT', 0)).toBe(2000);
		expect(retryDelayMillis(2, '', 0)).toBe(2000);
	});

	it('never waits more than a minute, and adds up to a second of jitter', () => {
		expect(retryDelayMillis(1, '3600', 0)).toBe(60_000);
		expect(retryDelayMillis(1, null, 0.5)).toBe(1500);
		expect(retryDelayMillis(1, null, 0.999)).toBe(1999);
	});
});
