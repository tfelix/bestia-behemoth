import { goto } from '$app/navigation';
import { SignedOut } from '$lib/api/client';

/** Runs [work]; a session that has ended sends the app to the login page instead of showing an error. */
export async function guarded<T>(work: () => Promise<T>): Promise<T> {
	try {
		return await work();
	} catch (e) {
		if (e instanceof SignedOut) {
			await goto('/login');
			return new Promise<T>(() => {});
		}
		throw e;
	}
}
