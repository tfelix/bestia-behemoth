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
	this._entityCache = new Bestia.Engine.EntityCacheManager();

	/**
	 * Effects manager will subscribe itself to messages from the server which
	 * trigger a special effect for an entity or a stand alone effect which must
	 * be displayed by whatever means.
	 * 
	 * @public
	 * @property {Bestia.Engine.FX.EffectsManager}
	 */
	this._fxManager = null;

	this._groups = {};

	// ==== Subscriptions ====
	this.pubsub.subscribe(Bestia.Signal.ENGINE_CAST_ITEM, this._onCastItem.bind(this));

	/**
	 * When the connect signal is given this is the sign that the engine as
	 * initialized. I know this is a bit hacky but before we dont have access to
	 * the game instance which we need to further initialize this objects.
	 */
	this.pubsub.subscribe(Bestia.Signal.ENGINE_BOOTED, function() {

		self._demandLoader = new Bestia.Engine.DemandLoader(self.game.load, self.game.cache, self._urlHelper);
		self._fxManager = new Bestia.Engine.FX.EffectsManager(self.pubsub, self.game, self._entityCache);
		self._entityFactory = new Bestia.Engine.EntityFactory(self.game, self._demandLoader, self._entityCache,
				self._groups);
		self._entityUpdater = new Bestia.Engine.EntityUpdater(self.pubsub, self._entityCache, self._entityFactory);

	});
	// ==== /Subscriptions ====
};

Bestia.Engine.States.GameState.prototype.init = function(bestia) {
	this.bestia = bestia;

	// ==== GROUPS ====
	var self = this;
	self._groups.mapGround = self.game.add.group();
	self._groups.mapGround.name = 'map_ground';
	self._groups.sprites = self.game.add.group();
	self._groups.sprites.name = 'sprites';
	self._groups.mapOverlay = self.game.add.group();
	self._groups.mapOverlay.name = 'map_overlay';
	self._groups.effects = self.game.add.group();
	self._groups.effects.name = 'fx';
	self._groups.overlay = self.game.add.group();
	self._groups.overlay.name = 'overlay';
	self._groups.gui = self.game.add.group();
	self._groups.gui.name = 'gui';
	// ==== /GROUPS ====

	// ==== PLUGINS ====
	// AStar
	var astar = this.game.plugins.add(Phaser.Plugin.AStar);

	// @ifdef DEVELOPMENT
	this.game.plugins.add(Phaser.Plugin.Debug);
	// @endif
	// ==== /PLUGINS ====

	// Load the tilemap and display it.
	this.bestiaWorld = new Bestia.Engine.World(this.game, astar, this._groups);
	this.bestiaWorld.loadMap(this.bestia.location());

	// @ifdef DEVELOPMENT
	this.game.stage.disableVisibilityChange = true;
	// @endif

	this.pubsub.publish(Bestia.Signal.ENGINE_GAME_STARTED);
	this._entityUpdater.releaseHold();
};

Bestia.Engine.States.GameState.prototype.create = function() {

	// Start rain.
	var emitter = this.game.add.emitter(this.game.world.centerX, 0, 600);

	this._groups.effects.add(emitter);

	emitter.width = this.game.world.width;

	emitter.makeParticles('rain');

	emitter.minParticleScale = 0.1;
	emitter.maxParticleScale = 0.4;

	// emitter.angle = 30; // uncomment to set an angle for the rain.

	emitter.setYSpeed(300, 500);
	emitter.setXSpeed(-5, 5);

	emitter.minRotation = 0;
	emitter.maxRotation = 0;

	emitter.start(false, 1600, 1, 0);
	
	// We need two layers. One for shadow, one for color informations. Both must be updated by the objects.
	var shadowMap = this.game.add.bitmapData(this.game.width, this.game.height);
	var shadowImg = this.game.add.image(0, 0, shadowMap);   
	
	shadowImg.blendMode = Phaser.blendModes.MULTIPLY;
	
	shadowMap.ctx.fillStyle = '#000000';
	shadowMap.ctx.beginPath();
	shadowMap.ctx.fillRect(0, 0, this.game.width, this.game.height);
	shadowMap.ctx.closePath();
	
	//shadowMap.ctx.beginFill(0xFFFFFF, 0.5);
	
	var gradient =  shadowMap.ctx.createRadialGradient(700, 260, 100 * 0.75, 700, 260, 200);  
	gradient.addColorStop(0, 'rgba(255, 255, 255, 1.0)');    
	gradient.addColorStop(1, 'rgba(255, 255, 255, 0.0)');  
	
	shadowMap.context.beginPath();    
	shadowMap.context.fillStyle = gradient;    
	shadowMap.context.arc(700, 260, 200, 0, Math.PI*2, false);    
	shadowMap.context.fill(); 
    
	shadowMap.dirty = true;
	
	shadowImg.alpha = 0.8;
	
	/*var blurX = this.game.add.filter('BlurX');
	var blurY = this.game.add.filter('BlurY');
	
	blurX.blur = 50;
	blurY.blur = 50;
	
	//shadowImg.filters = [blurX, blurY];*/
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
	}); // Update the marker.
	if (this.marker !== null) {
		
		this.marker.onUpdate();

		if (this.game.input.activePointer.leftButton.isDown) {
			this.marker.onClick();
		}
	}

};

Bestia.Engine.States.GameState.prototype.render = function() {


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
