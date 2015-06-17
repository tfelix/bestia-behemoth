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
};

Bestia.Engine.Entity = function(game, sprite, world) {
	this.walkspeed = 1;
	this.sprite = sprite;

	this.game = game;
	this.world = world;

	this.path = [];
	this.tween = game.add.tween(sprite);
};

/**
 * Calculates the duration in ms of the total walk of the given path. Depends
 * upon the relative walkspeed of the entity.
 * 
 * @private
 * @method Bestia.Engine.Entity.#_getWalkDuration
 * @returns Total walkspeed in ms.
 */
Bestia.Engine.Entity.prototype._getWalkDuration = function(length, walkspeed) {
	// Usual walkspeed is 3 tiles / s -> 1/3 s/tile.
	return Math.round((1 / 3) * length / walkspeed * 1000);

};

Bestia.Engine.Entity.prototype.moveTo = function(path, world) {

	this.tween = this.game.add.tween(this.sprite);

	var pathX = new Array(path.length);
	var pathY = new Array(path.length);

	var self = this;
	self.world = world;

	// Calculate coordinate arrays from path.
	path.forEach(function(ele, i) {
		var cords = self.world.getPxXY(ele.x, ele.y);
		pathX[i] = cords.x;
		pathY[i] = cords.y;
	});

	// Calculate total amount of speed.
	this.tween.to({
		x : pathX,
		y : pathY,
	}, this._getWalkDuration(path.length, 1), Phaser.Easing.Linear.None, true);
};

/**
 * Stops a current movement.
 * 
 * @method Bestia.Engine.Entit#stopMove
 */
Bestia.Engine.Entity.prototype.stopMove = function() {

	this.tween.stop();

};

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

		// Prepare the AStar plugin.
		// TODO dieser call evtl in die world rein.
		this.astar = this.game.plugins.add(Phaser.Plugin.AStar);
		
		this._bestiaWorld = new Bestia.Engine.World(game, this.astar);
		this._bestiaWorld.loadMap();
		

		this.gfxCollision = this.add.graphics(0, 0);
		this.gfxCollision.beginFill(0xFF0000, 0.5);

		// Draw our player.
		this.player = this.game.add.sprite(0, 0, 'player');
		this.player.anchor.setTo(0, 0);
		this.movePlayer(3, 3);
		this._playerEntity = new Bestia.Engine.Entity(this.game, this.player, this._api);

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

		game.input.onDown.add(function(pointer, event) {

			var start = this._bestiaWorld.getTileXY(this.player.x, this.player.y);
			var goal = this._bestiaWorld.getTileXY(this.game.input.worldX, this.game.input.worldY);

			var path = this._bestiaWorld.findPath(start, goal);
			// Start movement.
			this._playerEntity.moveTo(path.nodes.reverse(), this._bestiaWorld);

		}, this);
	},

	update : function() {

		var game = this.game;

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

		// Check if we have to render the debug display.
		if (this.engine.config.debug()) {
			this._updateDebug();
		}
	},

	render : function() {
		var game = this.game;

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
		// Das hier noch irgendwie anpassen.
		BG.engine.info.fps(this.game.time.fps);
	},

	/**
	 * Renders all the debug information.
	 * 
	 * @method Bestia.Engine.States.GameState#renderDebug
	 * @private
	 */
	_renderDebug : function() {
		var game = this.game;

		// Show the path.
		game.debug.AStar(this.astar, 20, 20, '#ff0000');

	},

	movePlayer : function(x, y) {
		this.player.x = x * this.config.tileSize;
		this.player.y = y * this.config.tileSize;
	},

	updateMarker : function() {
		
		var cords = this._bestiaWorld.getTileXY(this.game.input.activePointer.worldX, this.game.input.activePointer.worldY);
		this._bestiaWorld.getPxXY(cords.x, cords.y, cords);

		this.marker.x = cords.x;
		this.marker.y = cords.y;

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