import { describe, expect, it } from 'vitest';
import { activityText } from './activity';

describe('activity', () => {
	it('tells what a crafter makes, how far it is and where', () => {
		expect(activityText({ kind: 'CRAFTING', item: 'unknown_plank', done: 4, total: 20, station: 'WORKBENCH' })).toBe(
			'Crafting unknown_plank 4/20 at Workbench'
		);
	});

	it('puts a pause first', () => {
		expect(activityText({ kind: 'CRAFTING', station: 'WORKBENCH', pause: 'BAG_FULL' })).toBe('Paused: bag full');
	});

	it('names a miner at its shaft and a resting bestia plainly', () => {
		expect(activityText({ kind: 'MINING', station: 'MINING_SHAFT' })).toBe('Mining at Mining shaft');
		expect(activityText({ kind: 'RESTING_AT_HOME' })).toBe('Resting at home');
	});
});
