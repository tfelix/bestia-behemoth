import type { LiveEvent } from '#lib/live.svelte.ts';

export type Channel = 'PARTY' | 'GUILD' | 'WHISPER';

/**
 * A line of chat that reached [masterId], from the game or from an app. [targetName] is set only on the master's own
 * copy of a whisper it wrote: who it went to.
 */
export type ChatLine = {
	id: string;
	masterId: number;
	channel: Channel;
	senderName: string;
	text: string;
	sentAt: number;
	targetName?: string | null;
};

/** How many lines a master's log keeps, oldest dropped first. */
export const MAX_LINES = 200;

/** The zone's limit, and the game client's: a longer line is refused. */
export const MAX_TEXT_LENGTH = 200;

/** The chat line [event] carries, or null for every other kind of event. */
export function lineOf(event: LiveEvent): ChatLine | null {
	if (event.type !== 'ChatLine') return null;

	const d = event.data;
	return {
		id: event.id,
		masterId: Number(d.masterId),
		channel: channelOf(d.channel),
		senderName: String(d.senderName),
		text: String(d.text),
		sentAt: Number(d.sentAtEpochMs),
		targetName: d.targetName ? String(d.targetName) : null
	};
}

function channelOf(channel: unknown): Channel {
	if (channel === 'GUILD' || channel === 'WHISPER') return channel;
	return 'PARTY';
}

/** Who to whisper to answer [line]: the writer of a whisper heard, the target of one written. Null for group lines. */
export function whisperPartner(line: ChatLine): string | null {
	if (line.channel !== 'WHISPER') return null;
	return line.targetName ?? line.senderName;
}

/** The tag a line starts with, the game client's own: [Party], [Guild], [From Name] or [To Name]. */
export function tagOf(line: ChatLine): string {
	switch (line.channel) {
		case 'PARTY':
			return '[Party]';
		case 'GUILD':
			return '[Guild]';
		case 'WHISPER':
			return line.targetName ? `[To ${line.targetName}]` : `[From ${line.senderName}]`;
	}
}

/** [lines] with [line] at the end, unless it is already there; at most [cap] of them. */
export function appendLine(lines: ChatLine[], line: ChatLine, cap: number = MAX_LINES): ChatLine[] {
	if (lines.some((l) => l.id === line.id)) return lines;
	return [...lines, line].slice(-cap);
}

/** What to tell the writer when the api-server refused their line; null for what the generic message says better. */
export function refusalText(status: number): string | null {
	switch (status) {
		case 403:
			return 'Chat needs Basic Skill Lv. 2.';
		case 400:
			return `A line is 1 to ${MAX_TEXT_LENGTH} characters.`;
		default:
			return null;
	}
}

/** Whether the master is in a party and in a guild; null for what is not known yet. */
export type Membership = { party: boolean | null; guild: boolean | null };

/**
 * Why [channel] cannot be spoken on, for a tooltip; null while it can, or while nothing is known to say it cannot. A
 * whisper needs no group, so it is always open.
 */
export function closedReason(channel: Channel, membership: Membership): string | null {
	switch (channel) {
		case 'PARTY':
			return membership.party === false ? 'You are in no party.' : null;
		case 'GUILD':
			return membership.guild === false ? 'You are in no guild.' : null;
		case 'WHISPER':
			return null;
	}
}

/** The game client's wording: one answer for a name nobody has and for a player who is away. */
export function unavailableText(targetName: string): string {
	return `No player named ${targetName} is online.`;
}
