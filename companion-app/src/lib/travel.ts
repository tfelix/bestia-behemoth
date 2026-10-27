import { sendJson } from '$lib/api/client';
import type { Bestia, Journey, JourneyOrder, Position } from '$lib/api/model';
import type { NotebookEntry } from '$lib/notebook/rules';

/** The notebook collection the routes are kept in, shared with the game. */
export const ROUTES = 'routes';

/** A route as the notebook shows it: [customName] is false while [name] is the one made from where it starts and ends. */
export type RouteValue = { name: string; customName: boolean; waypoints: { east: number; north: number }[] };

export type Route = { key: string; revision: number; name: string; customName: boolean; waypoints: Position[] };

export function routeOf(entry: NotebookEntry<RouteValue>): Route {
	const waypoints = entry.value.waypoints.map((point) => ({ eastMetres: point.east, northMetres: point.north }));
	return { key: entry.key, revision: entry.revision, name: entry.value.name, customName: entry.value.customName, waypoints };
}

/** What a route is kept as; a blank [name] lets the world name it from where it starts and ends. */
export function routeBody(waypoints: Position[], name: string) {
	return { name, waypoints: waypoints.map((point) => ({ east: point.eastMetres, north: point.northMetres })) };
}

/**
 * Journeys as the app orders them. Every write carries a fresh Idempotency-Key: sending again
 * after a timeout cannot give the order twice, because the zone answers a repeated key with its first reply.
 */
export function sendJourney(masterId: number, bestiaId: number, order: JourneyOrder, key: string): Promise<void> {
	return sendJson<void>('PUT', `/v1/companion/masters/${masterId}/bestias/${bestiaId}/journey`, order, { 'Idempotency-Key': key });
}

export function stopJourney(masterId: number, bestiaId: number, key: string): Promise<void> {
	return sendJson<void>('DELETE', `/v1/companion/masters/${masterId}/bestias/${bestiaId}/journey`, undefined, { 'Idempotency-Key': key });
}

/** The stops a journey still has to visit. */
export function stopsAhead(journey: Journey): Position[] {
	return journey.waypoints.slice(journey.nextIndex);
}

/** Where a bestia is and what it is doing, in a few words. */
export function travelStatus(bestia: Bestia): string {
	if (!bestia.inWorld) return 'Resting until you return';
	const journey = bestia.journey;
	if (!journey) return 'Standing where it is';

	const left = stopsAhead(journey).length;
	const where = journey.name ?? 'a place on the map';
	return left > 1 ? `On the way to ${where}, ${left} stops left` : `On the way to ${where}`;
}

/** The way a bestia still has to go, from where it stands, as map points. */
export function wayAhead(bestia: Bestia): Position[] {
	return bestia.journey ? [bestia.position, ...bestia.journey.route] : [];
}

/** Metres between two map points, for telling a leg's length. */
export function distance(a: Position, b: Position): number {
	return Math.hypot(a.eastMetres - b.eastMetres, a.northMetres - b.northMetres);
}

export function distanceText(metres: number): string {
	return metres >= 1000 ? `${(metres / 1000).toFixed(1)} km` : `${Math.round(metres)} m`;
}

/** The straight-line length of the way from [from] through every one of [stops]; the world finds the real way round. */
export function routeLength(from: Position, stops: Position[]): number {
	return stops.reduce((sum, stop, i) => sum + distance(i === 0 ? from : stops[i - 1], stop), 0);
}

/** How long [metres] take at [metresPerSecond], in the words of a trip: "3 min", "1 h 5 min". */
export function travelTimeText(metres: number, metresPerSecond: number): string {
	if (metresPerSecond <= 0) return 'unknown';

	const minutes = Math.round(metres / metresPerSecond / 60);
	if (minutes < 1) return 'under a minute';
	if (minutes < 60) return `${minutes} min`;

	const rest = minutes % 60;
	return rest === 0 ? `${Math.floor(minutes / 60)} h` : `${Math.floor(minutes / 60)} h ${rest} min`;
}
