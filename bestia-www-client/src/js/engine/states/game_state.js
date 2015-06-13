Bestia.Engine.ClickDebounce = function(callback) {
	// TODO Error check.

	this.btnDown = false;
	this.onClick = callback;
};

Bestia.Engine.ClickDebounce.prototype.clicked = function() {
	if (!this.btnDown) {
		this.btnDown = true;
		// Trigger callback.
		this.onClick();
	}
};

Bestia.Engine.ClickDebounce.prototype.released = function() {
	this.btnDown = false;
}

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
Bestia.Engine.States.GameState = function(engine) {
	this.marker = null;
	/**
	 * @property {Bestia.Engine} engine - Reference to the central bestia engine
	 *           object.
	 */
	this.engine = engine;

	this.map = null;

	/**
	 * Ground layer of the map. Can be used for various purpose like path
	 * calculation and tile location.
	 */
	this.groundLayer = null;

	/**
	 * Sprite of the player.
	 */
	this.player = null;

	/**
	 * Holds AStar plugin reference to calculate paths of the bestias when
	 * clicked by the user. The other bestias are controlled by the server. But
	 * user movement will be controlled by the client.
	 * 
	 * @private
	 * @property
	 */
	this.astar = null;

	this.config = {
		tileSize : 0,
		mapNameStyle : {
			font : "65px Arial",
			fill : "#ff0044",
			align : "center"
		}
	};
};

Bestia.Engine.States.GameState.prototype = {

	preload : function() {
		// Timing for FPS.
		this.game.time.advancedTiming = true;
	},

	create : function() {

		var game = this.game;

		this.gfxCollision = this.add.graphics(0, 0);
		this.gfxCollision.beginFill(0xFF0000, 0.5);

		var map = this.game.add.tilemap('map');
		
		// Do some sanity checks.
		if(map.tileHeight !== map.tileWidth) {
			throw "Engine does not support maps with different width and heights. Tiles must be square.";
		}
		this.config.tileSize = map.tileHeight;
		
		this.map = map;

		// Extract map properties.
		var props = map.properties;
		props.isPVP = (props.isPVP === "true");

		map.addTilesetImage('Berge', 'tiles');
		// Ground layer MUST be present.
		this.groundLayer = map.createLayer('layer_0');
		this.groundLayer.resizeWorld();

		// Now check how many layer there are and then create them.
		// Get the names of all layer.
		var layerNames = map.layers.map(function(x) {
			return x.name;
		});
		for (var i = 1; i < map.layers.length; i++) {
			var curLayer = 'layer_' + i;
			if (layerNames.indexOf(curLayer) === -1) {
				continue;
			}
			map.createLayer(curLayer);
		}

		// Iterate over all layers and pull down the collision attributes for
		// the a* plugin.

		// Prepare the AStar plugin.
		this.astar = this.game.plugins.add(Phaser.Plugin.AStar);
		// Namen der layer und tilesets der map einfügen.
		this.astar.setAStarMap(map, 'Berge');

		// Draw our player.
		this.player = this.game.add.sprite(0, 0, 'player');
		this.player.anchor.setTo(0, 1);
		this.player.x = 64;
		this.player.y = 128;

		this.cursors = this.game.input.keyboard.createCursorKeys();
		this.game.camera.follow(this.player);

		// Our painting marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);

		// Music.
		// this.game.add.audio('bg_theme').play();

		// Single Sprites
		this.game.add.sprite(160, 320, '1_F_ORIENT_01');
		this.game.add.sprite(320, 320, '1_M_BARD');

		// ========== DISPLAY MAP NAME ==============
		var mapName = i18n.t('map.' + props.mapDbName);
		text = this.game.add.text(this.game._width / 2, this.game._height / 2 - 100, mapName);
		text.align = 'center';
		text.anchor.setTo(0.5);
		// Font style
		text.font = 'Arial';
		text.fontSize = 50;
		text.fontWeight = 'bold';

		// Stroke color and thickness
		text.stroke = '#525252';
		text.strokeThickness = 4;
		if (props.isPVP) {
			text.fill = '#D9B525';
		} else {
			text.fill = '#2ED925';
		}
		text.alpha = 0;

		this.game.add.tween(text).to({
			alpha : 1
		}, 2000, Phaser.Easing.Linear.None, false, 1000).to({
			alpha : 0
		}, 2000, Phaser.Easing.Linear.None, false, 2500).start();

		game.input.onDown.add(function(pointer, event) {

			var start = this.groundLayer.getTileXY(this.player.x, this.player.y, {});
			var goal = this.groundLayer.getTileXY(this.game.input.x, this.game.input.y, {});
			console.log("Tile: " + goal.x + " " + goal.y);

			var path = this.astar.findPath(start, goal);
			var self = this;
			// Start movement.
			path.nodes.reverse().forEach(function(ele){
				self.movePlayer(ele.x, ele.y);
			});

			console.log(path);

		}, this);
	},

	update : function() {

		var game = this.game;

		BG.engine.info.fps(this.game.time.fps);

		var cursors = this.cursors;
		// For example this checks if the up or down keys are pressed and moves
		// the camera accordingly.
		if (cursors.up.isDown) {
			this.player.y -= 32;
		} else if (cursors.down.isDown) {
			this.player.y += 32;
		} else if (cursors.left.isDown) {
			this.player.x -= 32;
		} else if (cursors.right.isDown) {
			this.player.x += 32;
		}
	},

	render : function() {
		var game = this.game;

		// Check if we have to render the debug display.
		if (this.engine.config.debug()) {
			this.renderDebug();
		}
	},

	/**
	 * Renders all the debug information.
	 * 
	 * @method Bestia.Engine.States.GameState#renderDebug
	 */
	renderDebug : function() {
		var game = this.game;

		// Show the path.
		game.debug.AStar(this.astar, 20, 20, '#ff0000');
		
		
	},

	movePlayer : function(x, y) {
		this.player.x = x * this.config.tileSize;
		this.player.y = y * this.config.tileSize;
	},

	updateMarker : function() {

		this.marker.x = this.groundLayer.getTileX(this.game.input.activePointer.worldX) * 32;
		this.marker.y = this.groundLayer.getTileY(this.game.input.activePointer.worldY) * 32;

	},

	getTilePos : function(x) {
		return Math.floor(x / this.config.tileSize);
	},

	renderCollisions : function() {
		// Loop over all visible tiles and check if they are walkable. if not
		// render a block.
		var x = 0;
		var y = 0;
		this.gfxCollision.drawRect(x, y, this.config.tileSize, this.config.tileSize);
	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;