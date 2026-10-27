/**
 * Signing in, the game's way: PKCE, the login server's own passkey page, a single-use code back on
 * /auth/callback. The access token lives in memory only; the refresh token is an httpOnly cookie this script
 * never sees, scoped to the login endpoints below.
 */
const AUTH = '/api/v1/auth/companion';
const PENDING = 'bestia.pendingLogin';
const REFRESH_LOCK = 'bestia.refresh';
/** Refreshed this long before it actually expires, so a request never leaves with a token about to die. */
const EARLY_MS = 30_000;

type Session = { token: string; accountId: number; expiresAt: number };

let session: Session | null = null;
let refreshing: Promise<Session | null> | null = null;

export class LoginError extends Error {}

export function base64Url(bytes: Uint8Array): string {
	let binary = '';
	for (const b of bytes) binary += String.fromCharCode(b);
	return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomToken(bytes: number): string {
	return base64Url(crypto.getRandomValues(new Uint8Array(bytes)));
}

export async function challengeOf(verifier: string): Promise<string> {
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
	return base64Url(new Uint8Array(digest));
}

/** Leaves the app for the passkey page; it comes back on /auth/callback. */
export async function startLogin(intent: 'LOGIN' | 'REGISTER' = 'LOGIN'): Promise<never> {
	const verifier = randomToken(32);
	const state = randomToken(16);
	sessionStorage.setItem(PENDING, JSON.stringify({ verifier, state }));

	const response = await fetch(`${AUTH}/start`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({
			redirect_uri: `${location.origin}/auth/callback`,
			code_challenge: await challengeOf(verifier),
			code_challenge_method: 'S256',
			state,
			intent
		})
	});
	if (!response.ok) throw new LoginError(`The login server refused to start (${response.status})`);

	const { login_url } = await response.json();
	location.assign(login_url);
	return new Promise(() => {});
}

/** Trades the code on the callback URL for a session. */
export async function finishLogin(callback: { readonly searchParams: { get(name: string): string | null } }): Promise<void> {
	const pending = sessionStorage.getItem(PENDING);
	sessionStorage.removeItem(PENDING);
	const code = callback.searchParams.get('code');
	if (!pending || !code) throw new LoginError('This sign-in was not started here, or has already been used.');

	const { verifier, state } = JSON.parse(pending) as { verifier: string; state: string };
	if (callback.searchParams.get('state') !== state) throw new LoginError('The sign-in came back for another request.');

	const response = await fetch(`${AUTH}/exchange`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ code, code_verifier: verifier })
	});
	if (!response.ok) throw new LoginError(`The sign-in could not be completed (${response.status}).`);
	session = sessionOf(await response.json());
}

/** A usable access token, refreshing it first when it is about to expire; null when signed out. */
export async function accessToken(): Promise<string | null> {
	if (session && session.expiresAt - EARLY_MS > Date.now()) return session.token;
	return (await refresh())?.token ?? null;
}

export function currentAccountId(): number | null {
	return session?.accountId ?? null;
}

/**
 * One refresh at a time, across every tab and window of the app: the cookie is shared by all of them, and the
 * login server reads a second use of a spent cookie as theft and ends the session. Callers in one page share
 * the same promise; other pages wait on the lock and then send the cookie the first one received.
 */
export function refresh(): Promise<Session | null> {
	refreshing ??= withRefreshLock(async () => {
		try {
			const response = await fetch(`${AUTH}/refresh`, { method: 'POST', credentials: 'same-origin' });
			session = response.ok ? sessionOf(await response.json()) : null;
			return session;
		} catch {
			// Offline: keep what we have, so cached pages still render.
			return session;
		}
	}).finally(() => {
		refreshing = null;
	});
	return refreshing;
}

function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
	if (!('locks' in navigator)) return work();
	// Typed as a promise of the callback's promise; at run time it is already the value.
	return navigator.locks.request(REFRESH_LOCK, work).then((value) => value);
}

export async function logout(): Promise<void> {
	session = null;
	await fetch(`${AUTH}/logout`, { method: 'POST', credentials: 'same-origin' }).catch(() => {});
}

function sessionOf(body: { access_token: string; account_id: number; expires_in: number }): Session {
	return { token: body.access_token, accountId: body.account_id, expiresAt: Date.now() + body.expires_in * 1000 };
}
