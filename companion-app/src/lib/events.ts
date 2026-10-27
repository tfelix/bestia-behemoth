import type { LiveEvent } from '#lib/live.svelte.ts';
import { speciesName, STANCE_TEXT } from '#lib/names.ts';

/** A toast's words for an event, or null for those that only refresh what is on screen. */
export function describe(event: LiveEvent): { title: string; body: string } | null {
	const d = event.data as Record<string, string | number | boolean | null>;

	switch (event.type) {
		case 'HomeFell':
			return { title: `${d.fallen} has fallen`, body: `${d.masterName} now calls ${d.newHome} home.` };
		case 'BestiaDiedAway':
			return { title: `${d.bestiaName ?? speciesName(String(d.species))} was killed`, body: 'It lies where it fell until you return.' };
		case 'ConstructionDone':
			return { title: 'Construction finished', body: `Your ${String(d.kind).toLowerCase().replace(/_/g, ' ')} is standing.` };
		case 'JourneyEnded':
			return journeyEnded(d);
		case 'StanceChanged':
			return { title: 'Standing order received', body: `${STANCE_TEXT[String(d.stance)]?.name ?? d.stance}, aggression ${d.aggression}.` };
		default:
			return null;
	}
}

/** Whether [event] changes what the pages of [masterId] show, so they should fetch again. Chat changes nothing fetched. */
export function touchesMaster(event: LiveEvent, masterId: number): boolean {
	if (event.type === 'ChatLine') return false;
	return event.type === 'Presence' || event.data.masterId === masterId;
}

function journeyEnded(d: Record<string, string | number | boolean | null>): { title: string; body: string } {
	const who = d.bestiaName ?? speciesName(String(d.species));
	switch (d.failure) {
		case null:
			return { title: `${who} has arrived`, body: d.journeyName ? String(d.journeyName) : 'It is where you sent it.' };
		case 'NO_ROUTE':
			return { title: `${who} knows no way on`, body: 'Every way it knows of is blocked.' };
		case 'DIED':
			return { title: `${who} died on the way`, body: 'It lies where it fell until you return.' };
		default:
			return { title: `${who} is stuck`, body: 'It found no way on and stopped. See the map.' };
	}
}
