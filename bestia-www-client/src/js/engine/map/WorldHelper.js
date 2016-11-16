
const TILE_SIZE = 32;
const CHUNK_SIZE = 10;
const SIGHT_RANGE_X = 32;
const SIGHT_RANGE_Y = 32;

/**
 * Contains various helpful methods to do calculations in world space.
 * 
 * @constructor
 */
export default class WorldHelper {
	/**
	 * Size of the tiles in px.
	 */
	static get TILE_SIZE() {
		return TILE_SIZE;
	}
	
	/**
	 * Size of the chunks in tiles.
	 */
	static get CHUNK_SIZE() {
		return CHUNK_SIZE;
	}
	
	/**
	 * Sight range of the player in tiles. X and Y direction.
	 */
	static get SIGHT_RANGE() {
		return {x: SIGHT_RANGE_X, y: SIGHT_RANGE_Y};
	}

	/**
	 * Finds a path between a start and a goal coordinate. The coordiantes must
	 * be given in tile space coordiantes.
	 * 
	 * @method Bestia.Engine.World#findPath
	 * @public
	 */
	static findPath(start, goal) {
		return this._astar.findPath(start, goal);
	}

	/**
	 * Returns the px coordiantes if tiles x and y coordiantes are given.
	 * 
	 * @method Bestia.Engine.World#getPxXY
	 * @param {int}
	 *            tileX - Tile x coordiantes.
	 * @param {int}
	 *            tileY - Tile y coordiantes.
	 * @param {Object}
	 *            obj - Optional: Object to update with the new coordiantes.
	 * @return {Object} - X and Y px coordiantes in the form
	 * @{code {x: INT, y: INT}}.
	 */
	static getPxXY(tileX, tileY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};

		obj.x = tileX * this.TILE_SIZE;
		obj.y = tileY * this.TILE_SIZE;

		return obj;
	}

	/**
	 * Similar to getPxXY, but this time we take in regard the anchor of the
	 * sprites. The sprites are anchored to the bottom center. So the need to
	 * get the pixel coordiantes in the middle of the given tile in order to
	 * place the sprite correctly in the middle of the tile.
	 */
	static getSpritePxXY(tileX, tileY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};
		this.getPxXY(tileX, tileY, obj);
		obj.x = obj.x + this.TILE_SIZE / 2;
		obj.y = obj.y + this.TILE_SIZE - 7;
		return obj;
	}

	/**
	 * Returns the tile coordinates if coordinates in pixel are given.
	 * 
	 * @method Bestia.Engine.World#getTileXY
	 * @param {Object}
	 *            obj - Optional: An object with gets updated with the new
	 *            coordiantes.
	 * @return {Object} - X and Y tile coordiantes in px in the form
	 * @{code {x: INT, y: INT}}.
	 */
	static getTileXY(pxX, pxY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};

		obj.x = Math.floor(pxX / this.TILE_SIZE);
		obj.y = Math.floor(pxY / this.TILE_SIZE);

		return obj;
	}

	static getDistance(c1, c2) {
		var dX = c1.x - c2.x;
		var dY = c1.y - c2.y;

		return Math.floor(Math.sqrt(dX * dX + dY * dY));
	}
}
