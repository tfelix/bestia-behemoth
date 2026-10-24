import { describe, expect, it } from 'vitest';
import { appendLine, closedReason, lineOf, refusalText, type ChatLine } from './chatLog';

function line(id: string): ChatLine {
	return { id, masterId: 5, channel: 'PARTY', senderName: 'Ayla', text: id, sentAt: 1 };
}

describe('lineOf', () => {
	it('reads a chat line off its event', () => {
		const event = {
			id: '1-0',
			type: 'ChatLine',
			data: { masterId: 5, channel: 'GUILD', senderName: 'Ayla', text: 'hi', sentAtEpochMs: 7 }
		};

		expect(lineOf(event)).toEqual({ id: '1-0', masterId: 5, channel: 'GUILD', senderName: 'Ayla', text: 'hi', sentAt: 7 });
	});

	it('ignores every other event', () => {
		expect(lineOf({ id: '1-0', type: 'StanceChanged', data: { masterId: 5 } })).toBeNull();
	});
});

describe('appendLine', () => {
	it('keeps the newest lines, oldest first', () => {
		const lines = ['a', 'b', 'c'].reduce<ChatLine[]>((all, id) => appendLine(all, line(id), 2), []);

		expect(lines.map((l) => l.id)).toEqual(['b', 'c']);
	});

	/** A reconnect replays what the stream holds, which can overlap with what already arrived. */
	it('does not show a replayed line twice', () => {
		const lines = appendLine([line('a')], line('a'));

		expect(lines).toHaveLength(1);
	});
});

describe('refusalText', () => {
	it('names what is missing for chat', () => {
		expect(refusalText(403)).toBe('Chat needs Basic Skill Lv. 2.');
	});

	it('leaves other failures to the generic message', () => {
		expect(refusalText(503)).toBeNull();
	});
});

describe('closedReason', () => {
	it('closes the channel of a group the master is in none of', () => {
		expect(closedReason('GUILD', { party: true, guild: false })).toBe('You are in no guild.');
		expect(closedReason('PARTY', { party: false, guild: true })).toBe('You are in no party.');
	});

	it('leaves a channel open while it is in a group, or not known yet', () => {
		expect(closedReason('GUILD', { party: false, guild: true })).toBeNull();
		expect(closedReason('GUILD', { party: null, guild: null })).toBeNull();
	});
});
