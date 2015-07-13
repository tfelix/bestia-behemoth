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
	 * World object holding all features and functions regarding to the "world".
	 * 
	 * @property {Bestia.Engine.World}
	 * @private
	 */
	this._bestiaWorld = null;

	/**
	 * Holds the player bestia which should be used as the current player
	 * object. Some information like the current position will be extracted from
	 * it.
	 * 
	 * @private
	 * @property {Bestia.BestiaViewModel}
	 */
	this.bestia = null;
};

Bestia.Engine.States.GameState.prototype = {

	init : function(bestia) {
		this.bestia = bestia;
	},

	preload : function() {
		// Timing for FPS.
		this.game.time.advancedTiming = true;
	},

	create : function() {

		var game = this.game;

		// Prepare the AStar plugin.
		var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		this._bestiaWorld = new Bestia.Engine.World(game, astar);
		this._bestiaWorld.loadMap(this.bestia.location());

		this.gfxCollision = this.add.graphics(0, 0);
		this.gfxCollision.beginFill(0xFF0000, 0.5);

		// Draw our player.
		this.player = new Bestia.Engine.Entity(this.game, this._bestiaWorld);
		this.player.setPos(this.bestia.posX(), this.bestia.posY());

		this.cursors = this.game.input.keyboard.createCursorKeys();
		this.game.camera.follow(this.player.sprite);

		// Our painting marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);

		// Music.
		// this.game.add.audio('bg_theme').play();

		game.input.onDown.add(function() {

			var start = this.player.pos;
			var goal = this._bestiaWorld.getTileXY(this.game.input.worldX, this.game.input.worldY);

			var path = this._bestiaWorld.findPath(start, goal).nodes;

			var path = path.reverse();
			var msg = new Bestia.Message.BestiaMove(this.player.pbid, path, this.player.walkspeed);
			Bestia.publish('io.sendMessage', msg);

			// Start movement locally aswell.
			this.player.moveTo(path);

		}, this);
	},

	update : function() {

		// Check if we have to render the debug display.
		if (this.engine.config.debug()) {
			this._updateDebug();
		}
	},

	render : function() {
		
		// Check if we have to render the debug display.
		if (this.engine.config.debug()) {
			this._renderDebug();
		}
	},

	/**
	 * Updates all needed debug information. Only called if debug is enabled.
	 * 
	 * @method Bestia.Engine.States.GameState#updateDebug
	 * @private
	 */
	_updateDebug : function() {
		this.engine.info.fps(this.game.time.fps);
	},

	/**
	 * Renders all the debug information.
	 * 
	 * @method Bestia.Engine.States.GameState#renderDebug
	 * @private
	 */
	_renderDebug : function() {
		//var game = this.game;

		// Show the path.
		//game.debug.AStar(this.astar, 20, 20, '#ff0000');

	},

	updateMarker : function() {

		var cords = this._bestiaWorld.getTileXY(this.game.input.activePointer.worldX,
				this.game.input.activePointer.worldY);
		this._bestiaWorld.getPxXY(cords.x, cords.y, cords);

		this.marker.x = cords.x;
		this.marker.y = cords.y;

	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;