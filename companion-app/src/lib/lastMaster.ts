import { getJson } from '#lib/api/client.ts';
import type { MasterSummary } from '#lib/api/model.ts';

const KEY = 'bestia.lastMaster';

/** The Master the player last had open, so the next start can go straight back to it. */
export function rememberMaster(id: number) {
	try {
		localStorage.setItem(KEY, String(id));
	} catch {
		// Storage can be blocked; the app then simply starts on the list of Masters.
	}
}

function remembered(): number | null {
	try {
		return Number(localStorage.getItem(KEY)) || null;
	} catch {
		return null;
	}
}

/** [masters] has the remembered Master, which it does not for another account's or a deleted one. */
export function pickRemembered(masters: MasterSummary[], id: number | null = remembered()): MasterSummary | null {
	return masters.find((m) => m.id === id) ?? null;
}

/** Where a start of the app, signed in, should land: the last Master's page, else the list of Masters. */
export async function landingPath(): Promise<string> {
	if (remembered() === null) return '/masters';

	try {
		const master = pickRemembered(await getJson<MasterSummary[]>('/v1/companion/masters'));
		return master ? `/m/${master.id}` : '/masters';
	} catch {
		return '/masters';
	}
}
