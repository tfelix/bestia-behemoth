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
	 * World object holding all features and functions regarding to the "world".
	 * 
	 * @property {Bestia.Engine.World}
	 * @private
	 */
	this.bestiaWorld = null;

	this._publicChatController = null;
};

Bestia.Engine.States.GameState.prototype = {

	init : function(bestia) {
		this.bestia = bestia;

		// Prepare the AStar plugin.
		var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// Load the tilemap and display it.
		this.bestiaWorld = new Bestia.Engine.World(this.game, astar);
		this.bestiaWorld.loadMap(this.bestia.location());

		this.demandLoader = new Bestia.Engine.DemandLoader(this.game.load, this.game.cache);

		// Workaround: The factory must be created here because only now we have
		// the game instance. This is ugly.
		var entityFactory = new Bestia.Engine.EntityFactory(this.game, this.demandLoader, this.engine.entityCache);
		this.engine.entityUpdater._factory = entityFactory;

		this._publicChatController = new Bestia.Engine.ChatEntityController(this.engine.pubsub,
				this.engine.entityCache, this.game);

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
	
	_getPlayerEntity : function() {
		var pbid = this.engine.bestia.playerBestiaId();
		var entity = this.engine.entityCache.getByPlayerBestiaId(pbid);
		return entity;
	},

	clickHandler : function() {
		
		var player = this._getPlayerEntity();
		
		var start = player.position;
		var goal = Bestia.Engine.World.getTileXY(this.game.input.worldX, this.game.input.worldY);

		var path = this.bestiaWorld.findPath(start, goal).nodes;

		if (path.length === 0) {
			return;
		}

		var path = path.reverse();
		var msg = new Bestia.Message.BestiaMove(this.bestia.playerBestiaId(), path, player.walkspeed);
		this.pubsub.publish('io.sendMessage', msg);

		// Start movement locally aswell.
		player.moveTo(path);
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