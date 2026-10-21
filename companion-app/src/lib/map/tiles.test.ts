import { describe, expect, it } from 'vitest';
import { inWorld, serverTileOf, spanMetres } from './tiles';

describe('tile addressing', () => {
	it('maps a Leaflet tile to the server tile at the same place', () => {
		// Leaflet's row -1 is the band just north of the equator, which is the server's row 0.
		expect(serverTileOf({ x: 3, y: -1, z: -4 })).toEqual({ level: 4, x: 3, y: 0 });
		expect(serverTileOf({ x: 0, y: -8, z: 0 })).toEqual({ level: 0, x: 0, y: 7 });
	});

	it('spans tileSize times two to the level', () => {
		expect(spanMetres(0, 256)).toBe(256);
		expect(spanMetres(3, 256)).toBe(2048);
	});

	it('asks for nothing outside the world', () => {
		expect(inWorld({ level: 9, x: 0, y: 0 }, 256, 128_000, 128_000)).toBe(true);
		expect(inWorld({ level: 9, x: 1, y: 0 }, 256, 128_000, 128_000)).toBe(false);
		expect(inWorld({ level: 0, x: -1, y: 0 }, 256, 128_000, 128_000)).toBe(false);
	});
});
