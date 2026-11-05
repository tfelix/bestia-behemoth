import { describe, expect, it } from 'vitest';
import { keptFor, letterRefusal, MAX_SUBJECT_LENGTH, replySubject } from './mail';

describe('letterRefusal', () => {
	it('names each refusal the api-server answers with', () => {
		expect(letterRefusal(422)).toContain('Nobody is called that');
		expect(letterRefusal(402)).toContain('postage');
		expect(letterRefusal(500)).toBeNull();
	});
});

describe('keptFor', () => {
	const now = 1_000_000_000_000;

	it('counts days while there are some, then hours', () => {
		expect(keptFor(now + 29 * 86_400_000 + 5, now)).toBe('29 days');
		expect(keptFor(now + 5 * 3_600_000, now)).toBe('5 h');
		expect(keptFor(now + 60_000, now)).toBe('under an hour');
	});
});

describe('replySubject', () => {
	it('answers once, never Re: Re:', () => {
		expect(replySubject('Bridge')).toBe('Re: Bridge');
		expect(replySubject('Re: Bridge')).toBe('Re: Bridge');
	});

	it('stays within the subject limit', () => {
		expect(replySubject('x'.repeat(MAX_SUBJECT_LENGTH)).length).toBe(MAX_SUBJECT_LENGTH);
	});
});
