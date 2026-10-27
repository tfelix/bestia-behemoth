import { appendLine, lineOf, type ChatLine } from '#lib/chatLog.ts';
import { live } from '#lib/live.svelte.ts';

const STORE = (masterId: number) => `bestia.chat.${masterId}`;

/**
 * Party and guild chat per master, as the socket brings it. It listens from the moment the app loads, so a line that
 * arrives while another tab is open is waiting on the chat tab, and keeps each master's log in this browser, so a
 * reload does not empty it. The world keeps no chat history: what this device never heard, it cannot show.
 */
class ChatLog {
	private byMaster = $state<Record<number, ChatLine[]>>({});

	constructor() {
		live.on((event) => {
			const line = lineOf(event);
			if (line) this.add(line);
		});
	}

	/** [masterId]'s lines, oldest first. Call [open] first. */
	lines(masterId: number): ChatLine[] {
		return this.byMaster[masterId] ?? [];
	}

	/** Loads [masterId]'s stored log, once. */
	open(masterId: number): void {
		if (this.byMaster[masterId]) return;
		this.byMaster[masterId] = load(masterId);
	}

	private add(line: ChatLine): void {
		this.open(line.masterId);
		const lines = appendLine(this.byMaster[line.masterId], line);
		this.byMaster[line.masterId] = lines;
		save(line.masterId, lines);
	}
}

function load(masterId: number): ChatLine[] {
	try {
		const stored = localStorage.getItem(STORE(masterId));
		return stored ? (JSON.parse(stored) as ChatLine[]) : [];
	} catch {
		return [];
	}
}

function save(masterId: number, lines: ChatLine[]): void {
	try {
		localStorage.setItem(STORE(masterId), JSON.stringify(lines));
	} catch {
		// Storage full or blocked: the log lives on in memory until the page closes.
	}
}

export const chat = new ChatLog();
