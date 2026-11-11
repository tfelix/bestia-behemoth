/** A bestia's status line, from what the zone says it is doing. */
import type { Activity } from '#lib/api/model.ts';
import { itemName } from '#lib/names.ts';

const DOING: Record<string, string> = {
	CRAFTING: 'Crafting',
	MINING: 'Mining',
	GATHERING: 'Gathering',
	HAULING: 'Carrying its bag to the chest',
	FETCHING_FOOD: 'Fetching food from the chest',
	EATING: 'Eating',
	HEALING: 'Tending its wounds',
	FIGHTING: 'Fighting',
	RETREATING: 'Retreating home',
	RESTING_AT_HOME: 'Resting at home',
	SLEEPING: 'Sleeping',
	GUARDING: 'Guarding its area',
	IDLE: 'Idle'
};

/** Why a station waits, as its pause names it. */
export const PAUSE_TEXT: Record<string, string> = {
	WORKER_AWAY: 'the worker is away',
	WORKER_OFFLINE: 'the worker is not in the world',
	BAG_FULL: 'bag full',
	STATION_OUT: 'the fire is out',
	CANNOT_CRAFT: 'cannot make the next one',
	NO_FREE_CRAFTER: 'waiting for a free crafter'
};

/** `WORKBENCH` as Workbench, `MINING_SHAFT` as Mining shaft. */
export function stationName(kind: string): string {
	const words = kind.toLowerCase().replaceAll('_', ' ');
	return words.charAt(0).toUpperCase() + words.slice(1);
}

export function activityText(activity: Activity): string {
	if (activity.pause) return `Paused: ${PAUSE_TEXT[activity.pause] ?? activity.pause.toLowerCase()}`;

	const doing = DOING[activity.kind] ?? activity.kind;
	const what = activity.item ? ` ${itemName(activity.item)}` : '';
	const count = activity.total != null ? ` ${activity.done ?? 0}/${activity.total}` : '';
	const where = activity.station ? ` at ${stationName(activity.station)}` : '';
	return `${doing}${what}${count}${where}`;
}
