import type { LiveEvent } from '$lib/live.svelte';
import { speciesName, STANCE_TEXT } from '$lib/names';

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
		case 'StanceChanged':
			return { title: 'Standing order received', body: `${STANCE_TEXT[String(d.stance)]?.name ?? d.stance}, aggression ${d.aggression}.` };
		default:
			return null;
	}
}

/** Whether [event] changes what the pages of [masterId] show, so they should fetch again. */
export function touchesMaster(event: LiveEvent, masterId: number): boolean {
	return event.type === 'Presence' || event.data.masterId === masterId;
}
