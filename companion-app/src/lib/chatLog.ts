import type { LiveEvent } from '$lib/live.svelte';

export type Channel = 'PARTY' | 'GUILD';

/** A line of party or guild chat that reached [masterId], from the game or from an app. */
export type ChatLine = {
	id: string;
	masterId: number;
	channel: Channel;
	senderName: string;
	text: string;
	sentAt: number;
};

/** How many lines a master's log keeps, oldest dropped first. */
export const MAX_LINES = 200;

/** The zone's limit, and the game client's: a longer line is refused. */
export const MAX_TEXT_LENGTH = 200;

/** The chat line [event] carries, or null for every other kind of event. */
export function lineOf(event: LiveEvent): ChatLine | null {
	if (event.type !== 'ChatLine') return null;

	const d = event.data;
	const channel = d.channel === 'GUILD' ? 'GUILD' : 'PARTY';
	return {
		id: event.id,
		masterId: Number(d.masterId),
		channel,
		senderName: String(d.senderName),
		text: String(d.text),
		sentAt: Number(d.sentAtEpochMs)
	};
}

/** [lines] with [line] at the end, unless it is already there; at most [cap] of them. */
export function appendLine(lines: ChatLine[], line: ChatLine, cap: number = MAX_LINES): ChatLine[] {
	if (lines.some((l) => l.id === line.id)) return lines;
	return [...lines, line].slice(-cap);
}

/** What to tell the writer when the api-server refused their line. */
export function refusalText(status: number, channel: Channel): string | null {
	switch (status) {
		case 403:
			return 'Chat needs Basic Skill Lv. 2.';
		case 409:
			return channel === 'PARTY' ? 'This Master is in no party.' : 'This Master is in no guild.';
		case 400:
			return `A line is 1 to ${MAX_TEXT_LENGTH} characters.`;
		default:
			return null;
	}
}
