/** A diary line in words, as the master reads it. */
import type { DiaryEntry } from '#lib/api/model.ts';
import { PAUSE_TEXT, stationName } from '#lib/activity.ts';
import { itemName, JOB_TEXT } from '#lib/names.ts';

const STOPPED: Record<string, string> = {
	...PAUSE_TEXT,
	STATION_OUT: 'its fire went out',
	CANNOT_CRAFT: 'it could not make the next one',
	CHEST_FULL: 'the chest is full',
	NO_ORE: 'there is no ore left in reach'
};

/** "Brought 12 Log, 3 Herb to the chest", without the bestia's name, which the page shows beside it. */
export function diaryText(line: DiaryEntry): string {
	const at = line.place ? ` at ${stationName(line.place)}` : '';
	switch (line.kind) {
		case 'NEW_ORDERS':
			return `Got new orders: ${JOB_TEXT[line.reason ?? '']?.name ?? line.reason}`;
		case 'HAULED':
			return `Brought ${goods(line.items)} to the chest`;
		case 'WORK_PAUSED':
			return `Stopped working${at}: ${STOPPED[line.reason ?? ''] ?? line.reason?.toLowerCase()}`;
		case 'QUEUE_FINISHED':
			return `Finished the queue${at}`;
		case 'ATTACKED':
			return 'Was attacked';
		case 'RETREATED':
			return 'Retreated home';
		case 'DIED':
			return 'Was killed';
		default:
			return line.kind;
	}
}

function goods(items: Record<string, number>): string {
	return Object.entries(items)
		.map(([item, amount]) => `${amount} ${itemName(item)}`)
		.join(', ');
}
