/**
 * The server's tile addressing, as the game client's map_view.gd uses it. Level n is 2^n metres per pixel; a
 * tile spans `tileSize * 2^n` metres, and tile (x, y) covers east [x·span, (x+1)·span) and north
 * [y·span, (y+1)·span), drawn north up.
 *
 * The map is shown in Leaflet's CRS.Simple with latitude = north metres and longitude = east metres. Leaflet's
 * zoom z is then 2^z pixels per metre, so level n is zoom -n, and Leaflet counts tile rows downwards from the
 * equator, so its row j is the server's row -j-1.
 */
export type ServerTile = { level: number; x: number; y: number };

export function serverTileOf(leaflet: { x: number; y: number; z: number }): ServerTile {
	// `0 - z` rather than `-z`, which is -0 at zoom 0.
	return { level: 0 - leaflet.z, x: leaflet.x, y: -leaflet.y - 1 };
}

export function spanMetres(level: number, tileSize: number): number {
	return tileSize * 2 ** level;
}

/** Whether [tile] lies inside a world of the given size; outside it there is nothing to ask for. */
export function inWorld(tile: ServerTile, tileSize: number, widthMetres: number, heightMetres: number): boolean {
	const span = spanMetres(tile.level, tileSize);
	return tile.x >= 0 && tile.y >= 0 && tile.x * span < widthMetres && tile.y * span < heightMetres;
}
