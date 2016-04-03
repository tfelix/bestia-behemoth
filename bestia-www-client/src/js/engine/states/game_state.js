Bestia.Engine.States = Bestia.Engine.States || {};

/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 * @param {Bestia.Engine}
 *            engine - Reference to the bestia engine.
 */
Bestia.Engine.States.GameState = function(engine, urlHelper, game) {

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

	/**
	 * World object holding all features and functions regarding to the "world".
	 * 
	 * @property {Bestia.Engine.World}
	 * @public
	 */
	this.bestiaWorld = null;

	/**
	 * Entity updater for managing the adding and removal of entities.
	 * 
	 * @public
	 * @property {Bestia.Engine.EntityUpdater}
	 */
	this._entityUpdater = null;

	this._urlHelper = urlHelper;

	this._cursor = null;

	this._demandLoader = null;

	/**
	 * Holds the central cache for all entities displayed in the game.
	 */
	this._entityCache = null;

	/**
	 * Effects manager will subscribe itself to messages from the server which
	 * trigger a special effect for an entity or a stand alone effect which must
	 * be displayed by whatever means.
	 * 
	 * @public
	 * @property {Bestia.Engine.FX.EffectsManager}
	 */
	this._fxManager = null;
	
	this._spriteGroup = null;

	// ==== Subscriptions ====
	this.pubsub.subscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));
	

	/**
	 * When the connect signal is given this is the sign that the engine as
	 * initialized. I know this is a bit hacky but before we dont have access to
	 * the game instance which we need to further initialize this objects.
	 */
	this.pubsub.subscribe(Bestia.Signal.ENGINE_BOOTED, function() {	
		self._spriteGroup = self.game.add.group();
		self._spriteGroup.name = 'sprites';
		
		self._demandLoader = new Bestia.Engine.DemandLoader(self.game.load, self.game.cache, self._urlHelper);
		self._entityCache = new Bestia.Engine.EntityCacheManager();
		self._fxManager = new Bestia.Engine.FX.EffectsManager(self.pubsub, self.game, self._entityCache);
		self._entityFactory = new Bestia.Engine.EntityFactory(self.game, self._demandLoader, self._entityCache, self._spriteGroup);
		self._entityUpdater = new Bestia.Engine.EntityUpdater(self.pubsub, self._entityCache, self._entityFactory);
	});
	// ==== /Subscriptions ====
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
	this.bestiaWorld = new Bestia.Engine.World(this.game, astar, this._spriteGroup);
	this.bestiaWorld.loadMap(this.bestia.location());


	// @ifdef DEVELOPMENT
	this.game.stage.disableVisibilityChange = true;
	// @endif

	this.pubsub.publish(Bestia.Signal.ENGINE_GAME_STARTED);
	this._entityUpdater.releaseHold();
};

Bestia.Engine.States.GameState.prototype.create = function() {
	//var blur = this.game.add.filter('filter_blur');
};

/**
 * Callback which is called if the user wants to use a castable item from the
 * inventory.
 */
Bestia.Engine.States.GameState.prototype._onCastItem = function(item) {

	console.info("Cast item: " + item.name());

	// Switch the indicator to the cast indicator used by this item.

};

Bestia.Engine.States.GameState.prototype.update = function() {

	// Update the animation frame groups of all multi sprite entities.
	var entities = this._entityCache.getAllEntities();
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
	// TODO Ich weiß nicht ob das hier funktioniert oder ob referenz zu callback
	// benötigt wird.
	this.pubsub.unsubscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));

};

Bestia.Engine.States.GameState.prototype.getPlayerEntity = function() {
	var pbid = this.engine.bestia.playerBestiaId();
	var entity = this.engine.entityCache.getByPlayerBestiaId(pbid);
	return entity;
};
