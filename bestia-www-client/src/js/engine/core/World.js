import I18n from '../../util/I18n.js';

/**
 * The game world is represented by this class. It will provide a lot of helper
 * methods to do orientation, path finding and general display and management of
 * the tilemaps for the bestia game.
 * 
 * @constructor
 * @class Bestia.Engine.World
 */
export default class World {
	
	constructor(game, astar, groups) {
		/**
		 * Reference to a phaser game.
		 * 
		 * @property Phaser.Game
		 * @private
		 */
		this._game = game;

		this._astar = astar;

		/**
		 * Current map properties.
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

		/**
		 * Name of the map.
		 * 
		 * @property {String}
		 * @public
		 */
		this.name = "";

		this._groups = groups;

		/**
		 * The loaded raw tile data.
		 */
		this._map = null;

		// this._collisionMap;
	}
	
	/**
	 * Displays the map name of the game to the user. Should be called after a
	 * map change has occured.
	 * <p>
	 * It uses the mapDbName property of the map itself in order to translate it
	 * to the user locale.
	 * </p>
	 * 
	 * @method Bestia.Engine.World#displayMapName
	 */
	displayMapName() {
		// Translates the map db name.
		var mapName = I18n.t('map.' + this.properties.mapDbName);

		// Spawn a centered text.
		var text = this._game.add.text(this._game._width / 2, this._game._height / 2 - 100, mapName);

		this._groups.gui.add(text);

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
	}

	/**
	 * Initializes a new map (all map data like tilesets and must have been
	 * already loaded and need to be inside the cache) for the engine to
	 * display.
	 * 
	 * @method Bestia.Engine.World#loadMap
	 * @param {String}
	 *            mapDbName - The mapDbName of the map to load into the engine.
	 */
	loadMap(mapDbName) {

		// Reset layers.
		this._layers = [];

		this._map = this._game.add.tilemap(mapDbName);

		this.name = mapDbName;

		// Do some sanity checks.
		if (this._map.tileHeight !== this._map.tileWidth) {
			throw "Engine does not support maps with different width and heights. Tiles must be square.";
		}

		// Extract map properties, and typecast them since they are all strings!
		var props = this._map.properties;
		props.isPVP = (props.isPVP === "true");

		this.properties = props;

		// Set tile size.
		this.properties.tileSize = this._map.tileHeight;

		// Find the first name of the tilemaps specified.
		if (this._map.tilesets.length > 1) {
			console.warn("Map " + mapDbName + " contains more then one tileset. Using the first one.");
		}

		// There should be only one tileset! (specification)
		var tilesetName = this._map.tilesets[0].name;

		this._map.addTilesetImage(tilesetName, 'tiles-' + mapDbName, this.properties.tileSize, this.properties.tileSize);
		this._astar.setAStarMap(this._map, tilesetName);

		// Ground layer MUST be present via definition.
		var layer0 = this._map.createLayer('layer_0');
		layer0.name = 'layer_0';
		layer0.resizeWorld();
		this._groups.mapGround.add(layer0);

		var iLayer = 0;
		var jLayer = 1;

		// Now check how many layer there are and then create them.
		// Get the names of all layer.
		var layerNames = this._map.layers.map(function(x) {
			return x.name;
		});

		while (true) {

			var layerName = 'layer_' + iLayer;

			if (jLayer > 0) {
				layerName += '_' + jLayer;
			}

			// Iterate over all layers of the map if there is such a name.
			// Check if the layers exists.
			if (layerNames.indexOf(layerName) === -1) {
				// Layer does not exist anymore.

				if (jLayer > 0) {
					jLayer = 0;
					iLayer++;
					continue;
				}

				if (jLayer === 0) {
					break;
				}
			}

			// Create the layer.
			var layer = this._map.createLayer(layerName);
			layer.name = layerName;
			this._groups.mapOverlay.add(layer);

			jLayer++;
		}

		// Print the name of the map.
		this.displayMapName();
	}

	/**
	 * Can be used to calculate the static collision data for this loaded map.
	 * This collision map is used to find the A* path.
	 */
	calculateCollisionMap() {
		// Calculate the number of tiles visible.
		// var tilesWidth = this.game.width / this.properties.tileSize + 5;
		// var tilesHeight = this.game.height / this.properties.tileSize + 5;

		// Calculate a static collision map.

	}
}

// Constants.
World.TILE_SIZE = 32;