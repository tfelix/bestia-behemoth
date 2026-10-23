import { sendJson } from '$lib/api/client';
import type { Bestia, Journey, JourneyOrder, Position, Route } from '$lib/api/model';

/**
 * Journeys and saved routes as the app orders them. Every write carries a fresh Idempotency-Key: sending again
 * after a timeout cannot give the order twice, because the zone answers a repeated key with its first reply.
 */
export function sendJourney(masterId: number, bestiaId: number, order: JourneyOrder, key: string): Promise<void> {
	return sendJson<void>('PUT', `/v1/companion/masters/${masterId}/bestias/${bestiaId}/journey`, order, { 'Idempotency-Key': key });
}

export function stopJourney(masterId: number, bestiaId: number, key: string): Promise<void> {
	return sendJson<void>('DELETE', `/v1/companion/masters/${masterId}/bestias/${bestiaId}/journey`, undefined, { 'Idempotency-Key': key });
}

/** A blank [name] lets the world name it from where it starts and ends. */
export function saveRoute(masterId: number, waypoints: Position[], name: string, key: string): Promise<Route[]> {
	return sendJson<Route[]>('POST', `/v1/companion/masters/${masterId}/routes`, { waypoints, name }, { 'Idempotency-Key': key });
}

/** A blank [name] gives the route its made name back. */
export function renameRoute(masterId: number, routeId: number, name: string, key: string): Promise<Route[]> {
	return sendJson<Route[]>('PATCH', `/v1/companion/masters/${masterId}/routes/${routeId}`, { name }, { 'Idempotency-Key': key });
}

export function deleteRoute(masterId: number, routeId: number, key: string): Promise<Route[]> {
	return sendJson<Route[]>('DELETE', `/v1/companion/masters/${masterId}/routes/${routeId}`, undefined, { 'Idempotency-Key': key });
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
