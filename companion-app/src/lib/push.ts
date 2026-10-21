import { sendJson } from '$lib/api/client';

/** Why notifications cannot be turned on here, or null when they can. */
export function pushUnavailableReason(): string | null {
	if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
		return isIos() && !isInstalled()
			? 'On iPhone and iPad, notifications need the app on your Home Screen: Share, then "Add to Home Screen".'
			: 'This browser cannot receive notifications.';
	}
	if (Notification.permission === 'denied') return 'Notifications are blocked for this site in the browser settings.';
	return null;
}

export function isInstalled(): boolean {
	return matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;
}

export function isIos(): boolean {
	return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

export async function currentSubscription(): Promise<PushSubscription | null> {
	const registration = await navigator.serviceWorker.ready;
	return registration.pushManager.getSubscription();
}

/** Asks for permission (from a tap, as browsers require), subscribes, and tells the api-service where to send. */
export async function enablePush(): Promise<void> {
	if ((await Notification.requestPermission()) !== 'granted') throw new Error('Notifications were not allowed.');

	const { publicKey } = await fetch('/v1/companion/push/key').then((r) => r.json() as Promise<{ publicKey: string }>);
	const registration = await navigator.serviceWorker.ready;
	const subscription =
		(await registration.pushManager.getSubscription()) ??
		(await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: fromBase64Url(publicKey) }));

	await sendJson('POST', '/v1/companion/push/subscriptions', subscription.toJSON());
}

export async function disablePush(): Promise<void> {
	const subscription = await currentSubscription();
	if (!subscription) return;

	await sendJson('DELETE', '/v1/companion/push/subscriptions', { endpoint: subscription.endpoint });
	await subscription.unsubscribe();
}

function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
	const base64 = text.replace(/-/g, '+').replace(/_/g, '/');
	const binary = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4));
	return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}
