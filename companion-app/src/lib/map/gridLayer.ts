import L from 'leaflet';
import type { MapMeta } from '#lib/api/model.ts';
import { columnName, SQUARE_METRES } from './gridRef';
import { MAX_ZOOM } from './chartLayer';
import { serverTileOf, spanMetres } from './tiles';

/** Kilometre lines are drawn once they are at least this many pixels apart. */
const KM_MIN_PIXELS = 48;

/**
 * The grid squares of `gridRef.ts` over the chart: a firm line round each square, a faint one per kilometre once
 * they are far enough apart, and each square's name in its south-west corner. Drawn tile by tile on canvases, so
 * it costs nothing to pan and asks the server for nothing.
 */
class GridLayer extends L.GridLayer {
	constructor(private readonly meta: MapMeta) {
		super({ tileSize: meta.tileSize, minZoom: -meta.maxLevel, maxZoom: MAX_ZOOM, pane: 'overlayPane' });
	}

	protected createTile(coords: L.Coords): HTMLElement {
		const size = this.getTileSize();
		const canvas = document.createElement('canvas');
		canvas.width = size.x;
		canvas.height = size.y;
		const context = canvas.getContext('2d');
		if (context) this.draw(context, coords, size.x);
		return canvas;
	}

	private draw(context: CanvasRenderingContext2D, coords: L.Coords, pixels: number) {
		const tile = serverTileOf(coords);
		const span = spanMetres(tile.level, this.meta.tileSize);
		const metresPerPixel = span / pixels;
		const west = tile.x * span;
		const south = tile.y * span;
		const width = this.meta.worldWidthMetres;
		const height = this.meta.worldHeightMetres;
		if (west >= width || south >= height || west + span <= 0 || south + span <= 0) return;

		const step = 1000 / metresPerPixel >= KM_MIN_PIXELS ? 1000 : SQUARE_METRES;
		const toX = (east: number) => (east - west) / metresPerPixel;
		const toY = (north: number) => pixels - (north - south) / metresPerPixel;

		for (let east = Math.ceil(west / step) * step; east <= Math.min(west + span, width); east += step) {
			line(context, east % SQUARE_METRES === 0, toX(east), 0, toX(east), pixels);
		}
		for (let north = Math.ceil(south / step) * step; north <= Math.min(south + span, height); north += step) {
			line(context, north % SQUARE_METRES === 0, 0, toY(north), pixels, toY(north));
		}

		if (SQUARE_METRES / metresPerPixel < 40) return;
		context.font = '600 12px sans-serif';
		context.lineWidth = 3;
		context.strokeStyle = 'rgb(244 230 200 / 85%)';
		context.fillStyle = '#2a1d10';
		for (let east = Math.ceil(west / SQUARE_METRES) * SQUARE_METRES; east < west + span && east < width; east += SQUARE_METRES) {
			for (let north = Math.ceil(south / SQUARE_METRES) * SQUARE_METRES; north < south + span && north < height; north += SQUARE_METRES) {
				const name = `${columnName(east / SQUARE_METRES)}${north / SQUARE_METRES + 1}`;
				context.strokeText(name, toX(east) + 4, toY(north) - 4);
				context.fillText(name, toX(east) + 4, toY(north) - 4);
			}
		}
	}
}

function line(context: CanvasRenderingContext2D, square: boolean, x1: number, y1: number, x2: number, y2: number) {
	context.strokeStyle = square ? 'rgb(38 28 20 / 55%)' : 'rgb(38 28 20 / 22%)';
	context.lineWidth = square ? 1.5 : 1;
	context.beginPath();
	context.moveTo(x1, y1);
	context.lineTo(x2, y2);
	context.stroke();
}

export function gridLayer(meta: MapMeta): L.GridLayer {
	return new GridLayer(meta);
}
