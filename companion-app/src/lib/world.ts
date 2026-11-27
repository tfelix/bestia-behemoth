import names from 'virtual:bestia-names';
import { getJson } from '#lib/api/client.ts';
import type { CurrentWorld, LastWorld, WorldState } from '#lib/api/model.ts';

export function worldState(): Promise<WorldState> {
	return getJson<WorldState>('/v1/companion/world');
}

/** True while a new world is born. Any failure to tell counts as no: the page shows its own error then. */
export async function isRebirth(): Promise<boolean> {
	try {
		return (await worldState()).rebirth;
	} catch {
		return false;
	}
}

/** A line of the game's own text, its `%s` filled in order, as Godot's `%` does it. */
export function gameText(key: string, ...args: (string | number)[]): string {
	let next = 0;
	return (names[key] ?? key).replace(/%%|%s/g, (token) => (token === '%%' ? '%' : String(args[next++])));
}

/** A doomsday's name as a sentence uses it: "the Void Tear". */
export function threatName(threat: string): string {
	return names[`DOOM_${threat}`] ?? threat.toLowerCase().replace(/_/g, ' ');
}

export function capitalised(text: string): string {
	return text.charAt(0).toUpperCase() + text.slice(1);
}

/** The stages as the docs name them, each with what a player sees of it. */
const STAGES: Record<string, { name: string; line: string }> = {
	OMENS: { name: 'Omens', line: 'The first signs are seen.' },
	ONSET: { name: 'Onset', line: 'The doom has come to one place.' },
	ESCALATION: { name: 'Escalation', line: 'The doom spreads across the land.' },
	CATACLYSM: { name: 'Cataclysm', line: 'The world stands at the brink.' }
};

/** What the world shows of its doomsday, or null while it shows nothing yet. */
export function doomOf(world: CurrentWorld): { threat: string; stage: string; line: string } | null {
	if (!world.threat || !world.stage) return null;

	const stage = STAGES[world.stage] ?? { name: capitalised(world.stage.toLowerCase()), line: '' };
	return { threat: capitalised(threatName(world.threat)), stage: stage.name, line: stage.line };
}

export function endingTitle(last: LastWorld): string {
	return gameText(last.ending === 'SAVED' ? 'WORLD_END_SAVED_TITLE' : 'WORLD_END_LOST_TITLE');
}

/** The summary the game's final sequence showed, line by line. */
export function summaryLines(last: LastWorld): string[] {
	return [
		gameText('WORLD_END_LIVED', last.name, last.daysLived),
		last.threat ? gameText('WORLD_END_THREAT', threatName(last.threat)) : gameText('WORLD_END_NO_THREAT'),
		gameText('WORLD_END_T', last.lifeSpanPercent),
		townsLine(last.townsLost)
	];
}

function townsLine(townsLost: number): string {
	if (townsLost === 0) return gameText('WORLD_END_NO_TOWN_LOST');
	if (townsLost === 1) return gameText('WORLD_END_ONE_TOWN_LOST');
	return gameText('WORLD_END_TOWNS_LOST', townsLost);
}
