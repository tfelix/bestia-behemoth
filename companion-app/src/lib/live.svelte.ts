import { accessToken, refresh } from '#lib/auth.ts';

/** An event from the world, as the api-server relays it. */
export type LiveEvent = { id: string; type: string; data: Record<string, unknown> };

type Frame = { type: 'ready'; lastEventId: string } | { type: 'event'; event: LiveEvent };

const LAST_EVENT = 'bestia.lastEventId';
const TOKEN_EXPIRED = 4001;
const UNAUTHORIZED = 4000;

/**
 * The socket to the api-server: connected while signed in, reconnecting with backoff, and resuming from the
 * last event it saw, so a phone that slept through an event still hears about it on waking.
 */
class Live {
	connected = $state(false);
	/** The newest events, newest first, for the toasts and the feed. */
	recent = $state<LiveEvent[]>([]);

	private socket: WebSocket | null = null;
	private listeners = new Set<(event: LiveEvent) => void>();
	private seen = new Set<string>();
	private retryMs = 1_000;
	private stopped = true;

	start(): void {
		if (!this.stopped) return;
		this.stopped = false;
		void this.open();
	}

	stop(): void {
		this.stopped = true;
		this.socket?.close();
		this.socket = null;
		this.connected = false;
	}

	/** Calls [listener] for every event from now on; returns the unsubscribe. */
	on(listener: (event: LiveEvent) => void): () => void {
		this.listeners.add(listener);
		return () => this.listeners.delete(listener);
	}

	private async open(): Promise<void> {
		const token = await accessToken();
		if (!token || this.stopped) return;

		const url = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/v1/companion/ws`;
		const socket = new WebSocket(url);
		this.socket = socket;

		socket.onopen = () => {
			socket.send(JSON.stringify({ type: 'auth', token, lastEventId: localStorage.getItem(LAST_EVENT) }));
		};
		socket.onmessage = (message) => this.receive(JSON.parse(message.data) as Frame);
		socket.onclose = (closed) => {
			this.connected = false;
			if (this.stopped || this.socket !== socket) return;
			if (closed.code === UNAUTHORIZED) {
				void refresh().then((session) => session && this.retry());
			} else {
				this.retry(closed.code === TOKEN_EXPIRED ? 0 : undefined);
			}
		};
	}

	private receive(frame: Frame): void {
		if (frame.type === 'ready') {
			this.connected = true;
			this.retryMs = 1_000;
			localStorage.setItem(LAST_EVENT, frame.lastEventId);
			return;
		}

		const event = frame.event;
		// A reconnect replays what the stream holds since the last id, and can overlap with what arrives live.
		if (this.seen.has(event.id)) return;
		this.seen.add(event.id);
		localStorage.setItem(LAST_EVENT, event.id);

		this.recent = [event, ...this.recent].slice(0, 50);
		for (const listener of this.listeners) listener(event);
	}

	private retry(delayMs: number = this.retryMs): void {
		this.retryMs = Math.min(this.retryMs * 2, 30_000);
		setTimeout(() => void this.open(), delayMs);
	}
}

export const live = new Live();
