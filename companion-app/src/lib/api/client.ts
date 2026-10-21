import { accessToken, refresh } from '$lib/auth';

export class ApiError extends Error {
	constructor(
		readonly status: number,
		message: string
	) {
		super(message);
	}
}

/** Signed out: the app should go to the login page. */
export class SignedOut extends ApiError {
	constructor() {
		super(401, 'Signed out');
	}
}

/**
 * A call to the api-service as the signed-in account. A 401 is answered once with a refresh and a retry, since
 * the token may have expired on the way; a second 401 means the session itself is over.
 */
export async function call(path: string, init: RequestInit = {}): Promise<Response> {
	const token = await accessToken();
	if (!token) throw new SignedOut();

	let response = await fetch(path, withToken(init, token));
	if (response.status === 401) {
		const renewed = await refresh();
		if (!renewed) throw new SignedOut();
		response = await fetch(path, withToken(init, renewed.token));
		if (response.status === 401) throw new SignedOut();
	}
	return response;
}

export async function getJson<T>(path: string): Promise<T> {
	const response = await call(path);
	if (!response.ok) throw new ApiError(response.status, messageFor(response.status));
	return (await response.json()) as T;
}

export async function sendJson<T>(method: string, path: string, body: unknown, headers: Record<string, string> = {}): Promise<T> {
	const response = await call(path, {
		method,
		headers: { 'Content-Type': 'application/json', ...headers },
		body: JSON.stringify(body)
	});
	if (!response.ok) throw new ApiError(response.status, messageFor(response.status));
	const text = await response.text();
	return (text ? JSON.parse(text) : undefined) as T;
}

function withToken(init: RequestInit, token: string): RequestInit {
	const headers = new Headers(init.headers);
	headers.set('Authorization', `Bearer ${token}`);
	return { ...init, headers };
}

export function messageFor(status: number): string {
	switch (status) {
		case 404:
			return 'Not found - or not yours.';
		case 429:
			return 'Too many requests. Wait a moment.';
		case 503:
			return 'The world is busy or asleep. Try again shortly.';
		case 504:
			return 'The world did not answer in time.';
		default:
			return `Something went wrong (${status}).`;
	}
}
