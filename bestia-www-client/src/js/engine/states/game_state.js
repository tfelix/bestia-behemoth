/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
Bestia.Engine.States.GameState = function(engine) {

	var self = this;

	this.marker = null;
	/**
	 * @property {Bestia.Engine} Reference to the central bestia engine object.
	 */
	this.engine = engine;

	/**
	 * @property {Bestia.PubSub} Shortcut to the publish subscriber interface.
	 */
	this.pubsub = this.engine.pubsub;

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
	 * Can load asset packs on demand and fires events if it has done so.
	 * 
	 * @property {Bestia.Engine.DemandLoader}
	 * @private
	 */
	this._demandLoader = null;

	/**
	 * Holds the player bestia which should be used as the current player
	 * object. Some information like the current position will be extracted from
	 * it.
	 * 
	 * @private
	 * @property {Bestia.BestiaViewModel}
	 */
	this.bestia = null;

	// Setup the callbacks.
	var entityAppearCallback = function(obj) {

		// Prepare callback function, since we might need it.
		var completeEntityInsert = function() {
			var entity = new Bestia.Engine.Entity(obj, self.game,
					self._bestiaWorld);

			// Check if we just created the player bestia. if so hold reference
			// to it for the engine.
			if (entity.pbid === self.bestia.playerBestiaId()) {
				self.player = entity;
				// Center the camera on the spot of the soon to be selected
				// bestia.
				self.game.camera.follow(self.player.sprite);
			}

			return entity;
		};

		// Check if the spritepack for this new entity has been loaded.
		// If not delay the call and preload the pack first.
		for (var i = 0; i < obj.s.length; i++) {
			var name = obj.s[i];
			var key = name + '_desc';
			if (!self.game.cache.checkJSONKey(key)) {
				// Preload the missing stuff and wait for completion.
				self._demandLoader.loadMobSprite(name, function() {

					var entity = completeEntityInsert();

					self.engine.entityUpdater.registerEntity(obj, entity);
				});
				return;
			}
		}
		return completeEntityInsert();
	};
	this.engine.entityUpdater.addHandler('onAppear', entityAppearCallback);

	/**
	 * Updates an entity if a change is incoming.
	 */
	var entityUpdateCallback = function(entity, obj) {
		entity.moveTo([ {
			x : obj.x,
			y : obj.y
		} ]);
	};
	this.engine.entityUpdater.addHandler('onUpdate', entityUpdateCallback);
};

Bestia.Engine.States.GameState.prototype = {

	init : function(bestia) {
		this.bestia = bestia;

		// Prepare the demandloader.
		this._demandLoader = new Bestia.Engine.DemandLoader(this.load,
				this.cache);

		// Prepare the AStar plugin.
		var astar = this.game.plugins.add(Phaser.Plugin.AStar);

		// Load the tilemap and display it.
		this._bestiaWorld = new Bestia.Engine.World(this.game, astar);
		this._bestiaWorld.loadMap(this.bestia.location());

		// Timing for FPS.
		this.game.time.advancedTiming = true;
		
		this.game.stage.disableVisibilityChange = true;
	},

	create : function() {

		var game = this.game;

		this.cursors = this.game.input.keyboard.createCursorKeys();

		// Our painting marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);

		// Music.
		// this.game.add.audio('bg_theme').play();

		game.input.onDown.add(function() {

			var start = this.player.pos;
			var goal = this._bestiaWorld.getTileXY(this.game.input.worldX,
					this.game.input.worldY);

			var path = this._bestiaWorld.findPath(start, goal).nodes;

			if (path.length === 0) {
				return;
			}

			var path = path.reverse();
			var msg = new Bestia.Message.BestiaMove(this.player.pbid, path,
					this.player.walkspeed);
			this.pubsub.publish('io.sendMessage', msg);

			// Start movement locally aswell.
			this.player.moveTo(path);

		}, this);

		// Activate the selected bestia which triggered the mapload.
		var msg = new Bestia.Message.BestiaActivate(this.bestia
				.playerBestiaId());
		this.pubsub.publish('io.sendMessage', msg);

		// After we have created everything release the hold of the update
		// messages.
		this.engine.entityUpdater.releaseHold();
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
		// var game = this.game;

		// Show the path.
		// game.debug.AStar(this.astar, 20, 20, '#ff0000');

	},

	/**
	 * Performs an on demand load of an asset which must be displayed by the
	 * engine.
	 */
	loadAsset : function() {

	},

	updateMarker : function() {

		var cords = this._bestiaWorld.getTileXY(
				this.game.input.activePointer.worldX,
				this.game.input.activePointer.worldY);
		this._bestiaWorld.getPxXY(cords.x, cords.y, cords);

		this.marker.x = cords.x;
		this.marker.y = cords.y;

	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;