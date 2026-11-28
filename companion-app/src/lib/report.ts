/**
 * The away report in words: a few short sentences per Bestia, from the facts the zone sends. Short sentences and
 * plain words, since many readers do not speak English as their first language.
 */
import type { BestiaReport } from '#lib/api/model.ts';
import { activityText, stationName } from '#lib/activity.ts';
import { STOPPED } from '#lib/diary.ts';
import { itemName, JOB_TEXT, speciesName } from '#lib/names.ts';

type Incident = BestiaReport['incidents'][number];

/** Whether a Bestia did or met anything worth a line: time alone is not news. */
export function eventful(report: BestiaReport): boolean {
	return (
		Object.keys(report.brought).length > 0 || Object.keys(report.madeAt).length > 0 || report.incidents.length > 0 || report.blocker != null
	);
}

/** "Pip worked for 7 hours. It brought 214 Log to the Camp chest. … To go on: put food in the Camp chest." */
export function reportText(report: BestiaReport, clock: (epochMs: number) => string = timeOfDay): string[] {
	const name = report.name ?? speciesName(report.species);
	return [
		timeSpent(name, report.timeIn),
		brought(report),
		made(report.madeAt),
		...happened(report.incidents, clock),
		now(report),
		toGoOn(report)
	].filter((sentence): sentence is string => sentence != null);
}

const DID: Record<string, string> = {
	WORKING: 'worked',
	RESTING: 'rested',
	IDLE: 'had nothing to do',
	PAUSED: 'waited for you',
	FIGHTING: 'fought',
	SHELTERING: 'took shelter',
	TRAVELLING: 'travelled'
};

/** Less than this is not worth telling. */
const NOTABLE_SECONDS = 10 * 60;

function timeSpent(name: string, timeIn: Record<string, number>): string | null {
	const spent = Object.entries(timeIn)
		.filter(([, seconds]) => seconds >= NOTABLE_SECONDS)
		.sort(([, a], [, b]) => b - a)
		.slice(0, 2)
		.map(([state, seconds]) => `${DID[state] ?? state.toLowerCase()} for ${duration(seconds)}`);
	return spent.length === 0 ? null : `${name} ${spent.join(' and ')}.`;
}

function brought(report: BestiaReport): string | null {
	const goods = Object.entries(report.brought).sort(([, a], [, b]) => b - a);
	if (goods.length === 0) return null;
	const shown = goods.slice(0, 3).map(([item, amount]) => `${amount} ${itemName(item)}`);
	if (goods.length > 3) shown.push('more');
	return `It brought ${list(shown)} to ${chestOf(report)}.`;
}

function made(madeAt: Record<string, number>): string | null {
	const queues = Object.entries(madeAt).map(([station, count]) =>
		count === 1 ? `the queue at the ${stationName(station)}` : `${count} queues at the ${stationName(station)}`
	);
	return queues.length === 0 ? null : `It finished ${list(queues)}.`;
}

/** One sentence per kind of incident, in the order each first happened. */
function happened(incidents: Incident[], clock: (epochMs: number) => string): (string | null)[] {
	const byKind = new Map<string, Incident[]>();
	for (const incident of incidents) byKind.set(incident.kind, [...(byKind.get(incident.kind) ?? []), incident]);

	return [...byKind.values()].map((same) => {
		const what = clause(same, clock);
		return what ? `It ${what}.` : null;
	});
}

function clause(same: Incident[], clock: (epochMs: number) => string): string | null {
	const first = same[0];
	const when = clock(first.atEpochMs);
	switch (first.kind) {
		case 'ATTACKED':
			return same.length === 1 ? `was attacked ${when}` : `was attacked ${times(same.length)}, first ${when}`;
		case 'RETREATED':
			return `retreated home ${when}`;
		case 'HELPED':
			return `helped in a fight ${when}`;
		case 'OUT_OF_FOOD':
			return `ran out of food ${when}`;
		case 'LOW_FOOD':
			return `ran low on food ${when}`;
		case 'WORK_PAUSED':
			return `stopped working ${when}, because ${STOPPED[first.reason ?? ''] ?? first.reason?.toLowerCase() ?? 'something was wrong'}`;
		case 'FIELD_EMPTY':
			return `found nothing more to gather ${when}`;
		case 'FALLBACK_STARTED':
			return `turned to its other job, ${(JOB_TEXT[first.reason ?? '']?.name ?? first.reason ?? '').toLowerCase()}, ${when}`;
		case 'DIED':
			return `was killed ${when}`;
		case 'LOST':
			return `was killed and lost for good ${when}`;
		default:
			return null;
	}
}

function now(report: BestiaReport): string {
	if (report.now === 'DEAD') return 'It lies where it fell.';
	if (report.now === 'NOT_IN_WORLD') return 'It is not in the world now.';
	const doing = activityText({ kind: report.now, station: report.nowAt });
	return `It is ${doing.charAt(0).toLowerCase()}${doing.slice(1)} now.`;
}

function toGoOn(report: BestiaReport): string | null {
	const chest = chestOf(report);
	const HINTS: Record<string, string> = {
		EMPTY_CHEST: `make room in ${chest}`,
		BUILD_SHAFT: 'build a new mining shaft in its area',
		FEED_FIRE: 'put fuel in the fire',
		ADD_INGREDIENTS: 'put what the next item needs in the chest by its station',
		BRING_FOOD: `put food in ${chest}`,
		REVIVE: 'go to it and bring it back to life'
	};
	const hint = report.hint ? HINTS[report.hint] : undefined;
	return hint ? `To go on: ${hint}.` : null;
}

function chestOf(report: BestiaReport): string {
	return report.broughtTo.length === 1 ? `the ${report.broughtTo[0]}` : 'its chest';
}

function duration(seconds: number): string {
	if (seconds >= 3600) {
		const hours = Math.round(seconds / 3600);
		return hours === 1 ? 'an hour' : `${hours} hours`;
	}
	const minutes = Math.round(seconds / 60);
	return `${minutes} minutes`;
}

function times(count: number): string {
	return count === 2 ? 'twice' : `${count} times`;
}

/** "a", "a and b", "a, b and c". */
function list(parts: string[]): string {
	return parts.length <= 1 ? (parts[0] ?? '') : `${parts.slice(0, -1).join(', ')} and ${parts.at(-1)}`;
}

/** "just after midnight", "at 5 am", "at 2:40 pm", in the reader's own time zone. */
export function timeOfDay(epochMs: number): string {
	const at = new Date(epochMs);
	const hours = at.getHours();
	const minutes = at.getMinutes();
	if (hours === 0 && minutes < 15) return 'just after midnight';
	if (hours === 12 && minutes < 15) return 'just after noon';
	const hour = hours % 12 || 12;
	const half = hours < 12 ? 'am' : 'pm';
	return minutes === 0 ? `at ${hour} ${half}` : `at ${hour}:${String(minutes).padStart(2, '0')} ${half}`;
}
