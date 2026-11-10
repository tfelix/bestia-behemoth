/**
 * The survey needle game at a theodolite, as the zone defines it. Angles are degrees from straight up: -90 is left,
 * +90 right. The swing is a pure function of time, so the zone judges a stop by when it came, as it was drawn here.
 */

/** Survey sizes in km, and the Cartography level each needs. */
export const SURVEY_SIZES = [
	{ km: 1, level: 1 },
	{ km: 3, level: 3 },
	{ km: 5, level: 5 }
] as const;

/** Must match the zone's TheodoliteSurveys.WORK_SECONDS. */
export const WORK_MINUTES = 10;

const MAX_SWING_DEGREES = 90;

export function needleAt(elapsedMs: number, swingPeriodMs: number): number {
	return MAX_SWING_DEGREES * Math.sin((2 * Math.PI * elapsedMs) / swingPeriodMs);
}

/** Whether a needle at [degrees] lies in the target arc. Only a hint: the zone has the last word. */
export function isInArc(degrees: number, centreDegrees: number, widthDegrees: number): boolean {
	return Math.abs(degrees - centreDegrees) <= widthDegrees / 2;
}

/** A point on the dial at [degrees], for a pivot at (cx, cy). */
export function dialPoint(cx: number, cy: number, radius: number, degrees: number): { x: number; y: number } {
	const radians = (degrees * Math.PI) / 180;
	return { x: cx + radius * Math.sin(radians), y: cy - radius * Math.cos(radians) };
}

/** The SVG path of the target arc, kept on the half circle. */
export function arcPath(cx: number, cy: number, radius: number, centreDegrees: number, widthDegrees: number): string {
	const from = dialPoint(cx, cy, radius, Math.max(-MAX_SWING_DEGREES, centreDegrees - widthDegrees / 2));
	const to = dialPoint(cx, cy, radius, Math.min(MAX_SWING_DEGREES, centreDegrees + widthDegrees / 2));
	return `M ${from.x.toFixed(2)} ${from.y.toFixed(2)} A ${radius} ${radius} 0 0 1 ${to.x.toFixed(2)} ${to.y.toFixed(2)}`;
}

/** What a refused survey tells the player, by the status the api-server answered with. */
export function surveyRefusal(status: number): string | null {
	switch (status) {
		case 403:
			return 'Your Cartography level does not open this size yet.';
		case 409:
			return 'This theodolite is already surveying.';
		case 422:
			return 'A survey needs one empty paper and one ink in your bag.';
		default:
			return null;
	}
}

/** "7 min" of work left, rounded up so it never reads 0 while some is left. */
export function workLeft(seconds: number): string {
	return `${Math.max(1, Math.ceil(seconds / 60))} min`;
}
