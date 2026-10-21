/**
 * How old a report is, in words. Every figure the app shows is a report with an age - "as of 4 min ago" - and
 * never pretends to be the present.
 */
export function age(asOf: number, now: number = Date.now()): string {
	if (asOf <= 0) return 'not yet reported';

	const seconds = Math.max(0, Math.round((now - asOf) / 1000));
	if (seconds < 45) return 'just now';

	const minutes = Math.round(seconds / 60);
	if (minutes < 60) return `${minutes} min ago`;

	const hours = Math.round(minutes / 60);
	if (hours < 36) return `${hours} h ago`;

	return `${Math.round(hours / 24)} days ago`;
}
