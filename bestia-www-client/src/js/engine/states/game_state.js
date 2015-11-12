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
	 * @property {Bestia.Engine} Reference to the central bestia engine object.
	 */
	this.engine = engine;

	/**
	 * @property {Bestia.PubSub} Shortcut to the publish subscriber interface.
	 */
	this.pubsub = this.engine.pubsub;

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

		// Prepare the AStar plugin.
		var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// Load the tilemap and display it.
		this._bestiaWorld = new Bestia.Engine.World(this.game, astar);
		this._bestiaWorld.loadMap(this.bestia.location());
		
		this.demandLoader = new Bestia.Engine.DemandLoader(this.game.load, this.game.cache);
		var entityFactory = new Bestia.Engine.EntityFactory(this.game, this.demandLoader, this.engine.entityCache);
		
		// Set the factory to the updater.
		this.engine.entityUpdater._factory = entityFactory;

		// DEBUG
		this.game.stage.disableVisibilityChange = true;
	},

	create : function() {

		this.cursors = this.game.input.keyboard.createCursorKeys();

		// Our cursor marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);
		this.game.input.onDown.add(this.clickHandler, this);

		// Activate the selected bestia which triggered the mapload.
		var msg = new Bestia.Message.BestiaActivate(this.bestia.playerBestiaId());
		this.pubsub.publish('io.sendMessage', msg);

		// After we have created everything release the hold of the update
		// messages.
		this.engine.entityUpdater.releaseHold();
	},

	update : function() {

		// no op.

	},

	render : function() {

		// no op.

	},

	clickHandler : function() {
		var start = this.player.pos;
		var goal = this._bestiaWorld.getTileXY(this.game.input.worldX, this.game.input.worldY);

		var path = this._bestiaWorld.findPath(start, goal).nodes;

		if (path.length === 0) {
			return;
		}

		var path = path.reverse();
		var msg = new Bestia.Message.BestiaMove(this.player.pbid, path, this.player.walkspeed);
		this.pubsub.publish('io.sendMessage', msg);

		// Start movement locally aswell.
		this.player.moveTo(path);
	},

	updateMarker : function() {

		var pointer = this.game.input.activePointer;

		var cords = Bestia.Engine.World.getTileXY(pointer.worldX, pointer.worldY);
		Bestia.Engine.World.getPxXY(cords.x, cords.y, cords);

		this.marker.x = cords.x;
		this.marker.y = cords.y;

	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;