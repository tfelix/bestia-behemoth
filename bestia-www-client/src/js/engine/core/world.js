/**
 * The game world is represented by this class. It will provide a lot of helper
 * methods to do orientation, path finding and general display and management of
 * the tilemaps for the bestia game.
 * 
 * @constructor
 * @class Bestia.Engine.World
 */
Bestia.Engine.World = function(game, astar) {

	/**
	 * Reference to a phaser game.
	 * 
	 * @property Phaser.Game
	 * @private
	 */
	this._game = game;

	this._astar = astar;

	/**
	 * Current properties of the loaded map.
	 * 
	 * @property {Object}
	 */
	this.properties = null;

	/**
	 * Ground layer of a map. Must be present in all tiled maps.
	 * 
	 * @property {Phaser.TilemapLayer}
	 * @private
	 */
	this._groundLayer = null;
};

/**
 * Holds static configuration of the engine world.
 * 
 * @static
 * @property {Object}
 */
Bestia.Engine.World.config = {};

/**
 * Displays the map name of the game to the user. Should be called after a map
 * change has occured.
 * <p>
 * It uses the mapDbName property of the map itself in order to translate it to
 * the user locale.
 * </p>
 * 
 * @method Bestia.Engine.World#displayMapName
 */
Bestia.Engine.World.prototype.displayMapName = function() {
	// Translates the map db name.
	var mapName = i18n.t('map.' + this.properties.mapDbName);

	// Spawn a centered text.
	var text = this._game.add.text(this._game._width / 2, this._game._height / 2 - 100, mapName);
	text.align = 'center';
	text.anchor.setTo(0.5);

	// Font style
	text.font = 'Arial';
	text.fontSize = 50;
	text.fontWeight = 'bold';

	// Stroke color and thickness
	text.stroke = '#525252';
	text.strokeThickness = 4;

	if (this.properties.isPVP) {
		text.fill = '#D9B525';
	} else {
		text.fill = '#2ED925';
	}
	text.alpha = 0;

	this._game.add.tween(text).to({
		alpha : 1
	}, 2000, Phaser.Easing.Linear.None, false, 1000).to({
		alpha : 0
	}, 2000, Phaser.Easing.Linear.None, false, 2500).start();
};

/**
 * Initializes a new map (all map data like tilesets and must have been already
 * loaded!) for the engine to display.
 * 
 * @method Bestia.Engine.World#loadMap
 * @param {String}
 *            mapDbName - The mapDbName of the map to load into the engine.
 */
Bestia.Engine.World.prototype.loadMap = function(mapDbName) {

	this.map = this._game.add.tilemap(mapDbName);

	// Do some sanity checks.
	if (this.map.tileHeight !== this.map.tileWidth) {
		throw "Engine does not support maps with different width and heights. Tiles must be square.";
	}

	// Extract map properties, and typecast them since they are all strings!
	var props = this.map.properties;
	props.isPVP = (props.isPVP === "true");

	this.properties = props;

	// Set tile size.
	this.properties.tileSize = this.map.tileHeight;

	this.map.addTilesetImage('Berge', 'tiles-' + mapDbName);
	// Namen der layer und tilesets der map einfügen.
	this._astar.setAStarMap(this.map, 'Berge');

	// Ground layer MUST be present.
	this._groundLayer = this.map.createLayer('layer_0');
	this._groundLayer.resizeWorld();

	// Now check how many layer there are and then create them.
	// Get the names of all layer.
	var layerNames = this.map.layers.map(function(x) {
		return x.name;
	});
	for (var i = 1; i < this.map.layers.length; i++) {
		var curLayer = 'layer_' + i;
		if (layerNames.indexOf(curLayer) === -1) {
			continue;
		}
		this.map.createLayer(curLayer);
	}

	this.displayMapName();
};

/**
 * Finds a path between a start and a goal coordinate. The coordiantes must be
 * given in tile space coordiantes.
 * 
 * @method Bestia.Engine.World#findPath
 * @public
 */
Bestia.Engine.World.prototype.findPath = function(start, goal) {
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
Bestia.Engine.World.prototype.getPxXY = function(tileX, tileY, obj) {
	if (obj === undefined) {
		obj = {
			x : 0,
			y : 0
		};
	}

	obj.x = tileX * this.properties.tileSize;
	obj.y = tileY * this.properties.tileSize;

	return obj;
};

/**
 * Returns the tile coordinates if world pixel are given.
 * 
 * @method Bestia.Engine.World#getTileXY
 * @param {Object}
 *            obj - Optional: An object with gets updated with the new
 *            coordiantes.
 * @return {Object} - X and Y tile coordiantes in px in the form
 * @{code {x: INT, y: INT}}.
 */
Bestia.Engine.World.prototype.getTileXY = function(pxX, pxY, obj) {
	if (obj === undefined) {
		obj = {
			x : 0,
			y : 0
		};
	}

	this._groundLayer.getTileXY(pxX, pxY, obj);
	return obj;
};