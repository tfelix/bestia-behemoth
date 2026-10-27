import { call } from '#lib/api/client.ts';

/**
 * Guild emblems as object URLs, by the hash that names them. A hash names one picture forever, so an URL is made
 * once per page load and never revoked; there are as many as guilds the player looks at.
 */
const urls = new Map<string, Promise<string | null>>();

/** Null when the emblem cannot be had; asked again only after a reload. */
export function emblemUrl(hash: string): Promise<string | null> {
	let url = urls.get(hash);
	if (!url) {
		url = fetchEmblem(hash);
		urls.set(hash, url);
	}
	return url;
}

async function fetchEmblem(hash: string): Promise<string | null> {
	try {
		const response = await call(`/v1/companion/emblems/${hash}.png`);
		return response.ok ? URL.createObjectURL(await response.blob()) : null;
	} catch {
		return null;
	}
}
