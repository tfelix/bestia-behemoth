import { describe, expect, it } from 'vitest';
import { appendLine, closedReason, lineOf, refusalText, tagOf, whisperPartner, type ChatLine } from './chatLog';

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

		expect(lineOf(event)).toEqual({
			id: '1-0',
			masterId: 5,
			channel: 'GUILD',
			senderName: 'Ayla',
			text: 'hi',
			sentAt: 7,
			targetName: null
		});
	});

	it('reads the target off the own copy of a whisper', () => {
		const event = {
			id: '1-0',
			type: 'ChatLine',
			data: { masterId: 5, channel: 'WHISPER', senderName: 'Ayla', text: 'psst', sentAtEpochMs: 7, targetName: 'Bren' }
		};

		expect(lineOf(event)).toMatchObject({ channel: 'WHISPER', targetName: 'Bren' });
	});

	it('ignores every other event', () => {
		expect(lineOf({ id: '1-0', type: 'OrdersChanged', data: { masterId: 5 } })).toBeNull();
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

describe('whispers', () => {
	const heard: ChatLine = { ...line('a'), channel: 'WHISPER', senderName: 'Bren' };
	const written: ChatLine = { ...line('b'), channel: 'WHISPER', senderName: 'Ayla', targetName: 'Bren' };

	it('are tagged as the game tags them', () => {
		expect(tagOf(heard)).toBe('[From Bren]');
		expect(tagOf(written)).toBe('[To Bren]');
		expect(tagOf(line('c'))).toBe('[Party]');
	});

	it('are answered to the other player, either way', () => {
		expect(whisperPartner(heard)).toBe('Bren');
		expect(whisperPartner(written)).toBe('Bren');
		expect(whisperPartner(line('c'))).toBeNull();
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

	it('never closes whispers', () => {
		expect(closedReason('WHISPER', { party: false, guild: false })).toBeNull();
	});
});
