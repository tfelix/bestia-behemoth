import { goto } from '$app/navigation';
import { ApiError, SignedOut } from '#lib/api/client.ts';
import { isRebirth } from '#lib/world.ts';

/**
 * Runs [work]; a session that has ended sends the app to the login page instead of showing an error. A world that
 * cannot answer because a new one is being born sends it to the Interregnum.
 */
export async function guarded<T>(work: () => Promise<T>): Promise<T> {
	try {
		return await work();
	} catch (e) {
		if (e instanceof SignedOut) {
			await goto('/login');
			return new Promise<T>(() => {});
		}
		if (e instanceof ApiError && isZoneDown(e.status) && (await isRebirth())) {
			await goto('/interregnum');
			return new Promise<T>(() => {});
		}
		throw e;
	}
}

function isZoneDown(status: number): boolean {
	return status === 503 || status === 504;
}
