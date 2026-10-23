import { describe, expect, it } from 'vitest';
import type { Bestia } from '$lib/api/model';
import { distanceText, stopsAhead, travelStatus, wayAhead } from './travel';

const here = { eastMetres: 0, northMetres: 0 };

function bestia(overrides: Partial<Bestia>): Bestia {
	return {
		id: 7,
		name: 'Rex',
		speciesId: 1,
		species: 'wolf',
		level: 3,
		stance: 'PATROL',
		aggression: 50,
		inWorld: true,
		position: here,
		asOf: 0,
		...overrides
	};
}

describe('travel', () => {
	const journey = {
		name: 'Ore run',
		nextIndex: 1,
		waypoints: [here, { eastMetres: 10, northMetres: 0 }, { eastMetres: 20, northMetres: 0 }],
		route: [{ eastMetres: 5, northMetres: 0 }, { eastMetres: 20, northMetres: 0 }]
	};

	it('counts only the stops still ahead', () => {
		expect(stopsAhead(journey)).toHaveLength(2);
		expect(travelStatus(bestia({ journey }))).toBe('On the way to Ore run, 2 stops left');
	});

	it('tells a resting bestia from a standing one', () => {
		expect(travelStatus(bestia({ inWorld: false }))).toBe('Resting until you return');
		expect(travelStatus(bestia({}))).toBe('Standing where it is');
	});

	it('draws the way ahead from where the bestia stands', () => {
		expect(wayAhead(bestia({ journey }))[0]).toEqual(here);
		expect(wayAhead(bestia({}))).toEqual([]);
	});

	it('words a distance in metres, then kilometres', () => {
		expect(distanceText(420)).toBe('420 m');
		expect(distanceText(1530)).toBe('1.5 km');
	});
});
