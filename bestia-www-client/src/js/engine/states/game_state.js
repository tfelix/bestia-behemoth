Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
Bestia.Engine.States.GameState = function(engine, urlHelper) {

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
	 * @public
	 */
	this.bestiaWorld = null;

	/**
	 * Effects manager will subscribe itself to messages from the server which
	 * trigger a special effect for an entity or a stand alone effect which must
	 * be displayed by whatever means.
	 * 
	 * @public
	 * @property {Bestia.Engine.FX.EffectsManager}
	 */
	this._fxManager = null;
	
	/**
	 * Holds the central cache for all entities displayed in the game.
	 */
	this._entityCache = null;
	
	/**
	 * Entity updater for managing the adding and removal of entities.
	 * 
	 * @public
	 * @property {Bestia.Engine.EntityUpdater}
	 */
	this._entityUpdater = null;

	this._urlHelper = urlHelper;

	this._cursor = null;
};

Bestia.Engine.States.GameState.prototype.init = function(bestia) {
	this.bestia = bestia;

	// ==== PLUGINS ====
	// AStar
	var astar = this.game.plugins.add(Phaser.Plugin.AStar);

	// @ifdef DEVELOPMENT
	this.game.plugins.add(Phaser.Plugin.Debug);
	// @endif
	// ==== /PLUGINS ====

	// Load the tilemap and display it.
	this.bestiaWorld = new Bestia.Engine.World(this.game, astar);
	this.bestiaWorld.loadMap(this.bestia.location());

	this._demandLoader = new Bestia.Engine.DemandLoader(this.game.load, this.game.cache, this._urlHelper);
	this._entityCache = new Bestia.Engine.EntityCacheManager();
	this._fxManager = new Bestia.Engine.FX.EffectsManager(this.pubsub, this.game, this._entityCache);
	this._entityUpdater = new Bestia.Engine.EntityUpdater(this.pubsub, this._entityCache);

	// @ifdef DEVELOPMENT
	this.game.stage.disableVisibilityChange = true;
	// @endif
	
	// ==== Subscriptions ====
	this.pubsub.subscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));
	// ==== /Subscriptions ====

	this.pubsub.publish(Bestia.Signal.ENGINE_GAME_STARTED);
};

/**
 * Callback which is called if the user wants to use a castable item from the inventory.
 */
Bestia.Engine.States.GameState.prototype._onCastItem = function(item) {
	
	console.info("Cast item: " + item.name());
	
};

Bestia.Engine.States.GameState.prototype.update = function() {

	// Update the animation frame groups of all multi sprite entities.
	var entities = this.engine.entityCache.getAllEntities();
	entities.forEach(function(entity) {
		entity.tickAnimation();
	});

	// Update the marker.
	if (this.marker !== null) {
		this.marker.onUpdate();

		if (this.game.input.activePointer.leftButton.isDown) {
			this.marker.onClick();
		}
	}
};

Bestia.Engine.States.GameState.prototype.shutdown = function() {

	// We need to UNSUBSCRIBE from all subscriptions to avoid leakage.
	// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu callback benötigt wird.
	this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));
	
};

Bestia.Engine.States.GameState.prototype.getPlayerEntity = function() {
	var pbid = this.engine.bestia.playerBestiaId();
	var entity = this.engine.entityCache.getByPlayerBestiaId(pbid);
	return entity;
};
