
/**
 * Contains various helpful methods to do calculations in world space.
 * 
 * @constructor
 */
let WorldHelper = Object.freeze({

	// =================
	// Constants
	// =================
	
	/**
	 * Size of the tiles in px.
	 */
	TILE_SIZE : 32,
	/**
	 * Size of the chunks in tiles.
	 */
	CHUNK_SIZE: 10,
	
	/**
	 * Sight range of the player in tiles. X direction.
	 */
	SIGHT_RANGE_X: 32,
	
	/**
	 * Sight range of the player in tiles. y direction.
	 */
	SIGHT_RANGE_Y: 32,

	/**
	 * Finds a path between a start and a goal coordinate. The coordiantes must
	 * be given in tile space coordiantes.
	 * 
	 * @method Bestia.Engine.World#findPath
	 * @public
	 */
	findPath : function(start, goal) {
		return this._astar.findPath(start, goal);
	},

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
	getPxXY : function(tileX, tileY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};

		obj.x = tileX * this.TILE_SIZE;
		obj.y = tileY * this.TILE_SIZE;

		return obj;
	},

	/**
	 * Similar to getPxXY, but this time we take in regard the anchor of the
	 * sprites. The sprites are anchored to the bottom center. So the need to
	 * get the pixel coordiantes in the middle of the given tile in order to
	 * place the sprite correctly in the middle of the tile.
	 */
	getSpritePxXY : function(tileX, tileY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};
		this.getPxXY(tileX, tileY, obj);
		obj.x = obj.x + this.TILE_SIZE / 2;
		obj.y = obj.y + this.TILE_SIZE - 7;
		return obj;
	},

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
	getTileXY : function(pxX, pxY, obj) {
		obj = obj || {
			x : 0,
			y : 0
		};

		obj.x = Math.floor(pxX / this.TILE_SIZE);
		obj.y = Math.floor(pxY / this.TILE_SIZE);

		return obj;
	},

	getDistance : function(c1, c2) {
		var dX = c1.x - c2.x;
		var dY = c1.y - c2.y;

		return Math.floor(Math.sqrt(dX * dX + dY * dY));
	}
});

export default { WorldHelper };